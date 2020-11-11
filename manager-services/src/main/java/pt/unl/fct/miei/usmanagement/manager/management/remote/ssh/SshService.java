/*
 * MIT License
 *
 * Copyright (c) 2020 manager
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package pt.unl.fct.miei.usmanagement.manager.management.remote.ssh;

import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile;
import net.schmizz.sshj.xfer.FileSystemFile;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import pt.unl.fct.miei.usmanagement.manager.exceptions.EntityNotFoundException;
import pt.unl.fct.miei.usmanagement.manager.exceptions.ManagerException;
import pt.unl.fct.miei.usmanagement.manager.hosts.HostAddress;
import pt.unl.fct.miei.usmanagement.manager.hosts.cloud.CloudHost;
import pt.unl.fct.miei.usmanagement.manager.hosts.edge.EdgeHost;
import pt.unl.fct.miei.usmanagement.manager.management.hosts.HostsService;
import pt.unl.fct.miei.usmanagement.manager.management.monitoring.prometheus.PrometheusProperties;
import pt.unl.fct.miei.usmanagement.manager.management.docker.DockerProperties;
import pt.unl.fct.miei.usmanagement.manager.management.hosts.cloud.CloudHostsService;
import pt.unl.fct.miei.usmanagement.manager.management.hosts.cloud.aws.AwsProperties;
import pt.unl.fct.miei.usmanagement.manager.management.hosts.edge.EdgeHostsService;

import java.io.File;
import java.io.IOException;
import java.security.Security;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SshService {

	private static final int EXECUTE_COMMAND_TIMEOUT = 120000;

	private final EdgeHostsService edgeHostsService;
	private final CloudHostsService cloudHostsService;
	private final HostsService hostsService;

	private final int connectionTimeout;
	private final String awsKeyFilePath;
	private final Map<String, String> scriptPaths;

	public SshService(EdgeHostsService edgeHostsService, CloudHostsService cloudHostsService,
					  @Lazy HostsService hostsService,
					  SshProperties sshProperties, AwsProperties awsProperties, DockerProperties dockerProperties,
					  PrometheusProperties prometheusProperties) {
		this.edgeHostsService = edgeHostsService;
		this.cloudHostsService = cloudHostsService;
		this.connectionTimeout = sshProperties.getConnectionTimeout();
		this.awsKeyFilePath = awsProperties.getAccess().getKeyFilePath();
		this.hostsService = hostsService;
		PrometheusProperties.NodeExporter nodeExporterProperties = prometheusProperties.getNodeExporter();
		this.scriptPaths = Map.of(
			dockerProperties.getInstallScript(), dockerProperties.getInstallScriptPath(),
			dockerProperties.getUninstallScript(), dockerProperties.getUninstallScriptPath(),
			dockerProperties.getInstallApiScript(), dockerProperties.getInstallApiScriptPath(),
			nodeExporterProperties.getInstallScript(), nodeExporterProperties.getInstallScriptPath()
		);
		Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
	}

	private SSHClient initClient(HostAddress hostAddress, int timeout) throws IOException {
		if (!hostAddress.hasConnectionInfo()) {
			hostAddress = hostsService.completeConnectionInfo(hostAddress);
		}
		String publicKeyFile;
		try {
			EdgeHost edgeHost = edgeHostsService.getEdgeHostByAddress(hostAddress);
			hostAddress = edgeHost.getAddress();
			publicKeyFile = edgeHostsService.getPrivateKeyFilePath(edgeHost);
		}
		catch (EntityNotFoundException e) {
			try {
				CloudHost cloudHost = cloudHostsService.getCloudHostByAddress(hostAddress);
				hostAddress = cloudHost.getAddress();
			}
			catch (EntityNotFoundException ignored) {
			}
			publicKeyFile = String.format("%s/%s", System.getProperty("user.dir"), awsKeyFilePath);
		}
		return initClient(hostAddress.getUsername(), hostAddress.getPublicIpAddress(), new File(publicKeyFile), timeout);
	}

	private SSHClient initClient(HostAddress hostAddress) throws IOException {
		return initClient(hostAddress, connectionTimeout);
	}

	private SSHClient initClient(String username, String hostname, File publicKeyFile, int timeout) throws IOException {
		SSHClient sshClient = new SSHClient();
		sshClient.setConnectTimeout(timeout);
		sshClient.addHostKeyVerifier(new PromiscuousVerifier());
		log.info("Logging in to host {}@{} using key {}", username, hostname, publicKeyFile);
		sshClient.connect(hostname);
		PKCS8KeyFile keyFile = new PKCS8KeyFile();
		keyFile.init(publicKeyFile);
		sshClient.authPublickey(username, keyFile);
		log.info("Successfully logged in to host {}@{}", username, hostname);
		return sshClient;
	}

	private SSHClient initClient(HostAddress hostAddress, char[] password) throws IOException {
		String hostname = hostAddress.getPublicIpAddress();
		String username = hostAddress.getUsername();
		SSHClient sshClient = new SSHClient();
		sshClient.setConnectTimeout(connectionTimeout);
		sshClient.addHostKeyVerifier(new PromiscuousVerifier());
		log.info("Logging in to host {} using password", hostAddress);
		sshClient.connect(hostname);
		sshClient.authPassword(username, password);
		log.info("Successfully logged in to host {}", hostAddress);
		return sshClient;
	}

	public void uploadFile(HostAddress hostAddress, String filename) {
		try (SSHClient sshClient = initClient(hostAddress);
			 SFTPClient sftpClient = sshClient.newSFTPClient()) {
			String scriptPath = scriptPaths.get(filename);
			if (scriptPath == null) {
				throw new EntityNotFoundException(File.class, "name", filename);
			}
			File file = new File(scriptPath);
			log.info("Transferring file {} to host {}", filename, hostAddress);
			sftpClient.put(new FileSystemFile(file), filename);
		}
		catch (IOException e) {
			throw new ManagerException("Failed to transfer file %s to %s: %s", filename, hostAddress.toSimpleString(), e.getMessage());
		}
	}

	public SshCommandResult executeCommandSync(String command, HostAddress hostAddress) {
		return executeCommand(command, hostAddress, EXECUTE_COMMAND_TIMEOUT);
	}

	public void executeCommandAsync(String command, HostAddress hostAddress) {
		executeCommand(command, hostAddress, 0);
	}

	public SshCommandResult executeCommand(String command, HostAddress hostAddress, SSHClient client, long timeout) {
		try (Session session = client.startSession()) {
			return executeCommand(session, command, hostAddress, timeout);
		}
		catch (IOException e) {
			log.error("Failed to execute command {} on host {}: {}", command, hostAddress.toSimpleString(), e.getMessage());
			return new SshCommandResult(hostAddress, command, -1, List.of(), List.of(e.getMessage()));
		}
	}

	public SshCommandResult executeCommand(String command, HostAddress hostAddress, long timeout) {
		try (SSHClient sshClient = initClient(hostAddress)) {
			return executeCommand(command, hostAddress, sshClient, timeout);
		}
		catch (IOException e) {
			log.error("Failed to execute command {} on host {}: {}", command, hostAddress.toSimpleString(), e.getMessage());
			return new SshCommandResult(hostAddress, command, -1, List.of(), List.of(e.getMessage()));
		}
	}

	public SshCommandResult executeCommandSync(String command, HostAddress hostAddress, char[] password) {
		return executeCommand(command, hostAddress, password, EXECUTE_COMMAND_TIMEOUT);
	}

	public SshCommandResult executeCommandAsync(String command, HostAddress hostAddress, char[] password) {
		return executeCommand(command, hostAddress, password, 0);
	}

	private SshCommandResult executeCommand(String command, HostAddress hostAddress, char[] password, long timeout) {
		try (SSHClient sshClient = initClient(hostAddress, password);
			 Session session = sshClient.startSession()) {
			return executeCommand(session, command, hostAddress, timeout);
		}
		catch (IOException e) {
			log.error("Unable to execute command: {}", e.getMessage());
			return new SshCommandResult(hostAddress, command, -1, List.of(), List.of(e.getMessage()));
		}
	}

	private SshCommandResult executeCommand(Session session, String command, HostAddress hostAddress, long timeout) throws IOException {
		if (!command.contains("sshpass")) {
			// to avoid logging passwords
			log.info("Executing: {}, at host {}", command, hostAddress);
		}
		Session.Command cmd = session.exec(command);
		if (timeout == 0) {
			return null;
		}
		cmd.join(timeout, TimeUnit.MILLISECONDS);
		int exitStatus = cmd.getExitStatus();
		List<String> output = Arrays.asList(IOUtils.readFully(cmd.getInputStream()).toString().strip().split("\n"));
		List<String> error = Arrays.asList(IOUtils.readFully(cmd.getErrorStream()).toString().strip().split("\n"));
		log.info("Command exited with\nstatus: {}\noutput: {}\nerror: {}", exitStatus, output, error);
		return new SshCommandResult(hostAddress, command, exitStatus, output, error);
	}

	public void executeCommandInBackground(String command, HostAddress hostAddress) {
		String executeCommand = String.format("nohup %s 1>/dev/null 2>/dev/null &", command);
		executeCommandSync(executeCommand, hostAddress);
	}

	public boolean hasConnection(HostAddress hostAddress) {
		log.info("Checking connectivity to {}", hostAddress);
		try (SSHClient client = initClient(hostAddress);
			 Session ignored = client.startSession()) {
			log.info("Successfully connected to {}", hostAddress);
			return true;
		}
		catch (IOException e) {
			log.info("Failed to connect to {}: {}", hostAddress, e.getMessage());
		}
		return false;
	}

	public SSHClient waitAvailability(HostAddress hostAddress, int timeout) throws IOException {
		log.info("Waiting for host {} to become available", hostAddress);
		return initClient(hostAddress, timeout);
	}

	public Set<String> getScripts() {
		return scriptPaths.keySet();
	}

}
