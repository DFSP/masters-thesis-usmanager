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

package pt.unl.fct.miei.usmanagement.manager.service.management.hosts.cloud.aws;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pt.unl.fct.miei.usmanagement.manager.database.hosts.HostAddress;
import pt.unl.fct.miei.usmanagement.manager.service.exceptions.ManagerException;
import pt.unl.fct.miei.usmanagement.manager.service.management.remote.ssh.SshService;
import pt.unl.fct.miei.usmanagement.manager.service.util.Timing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AwsService {

	private final SshService sshService;
	private final AmazonEC2 ec2;
	private final String awsInstanceAmi;
	private final String awsInstanceSecurityGroup;
	private final String awsInstanceKeyPair;
	private final String awsInstanceType;
	private final String awsInstanceTag;
	private final int awsMaxRetries;
	private final int awsDelayBetweenRetries;
	private final int awsConnectionTimeout;

	public AwsService(SshService sshService, AwsProperties awsProperties) {
		this.sshService = sshService;
		String awsAccessKey = awsProperties.getAccess().getKey();
		String awsSecretAccessKey = awsProperties.getAccess().getSecretKey();
		BasicAWSCredentials awsCredentials = new BasicAWSCredentials(awsAccessKey, awsSecretAccessKey);
		AWSStaticCredentialsProvider awsCredentialsProvider = new AWSStaticCredentialsProvider(awsCredentials);
		this.ec2 = AmazonEC2ClientBuilder
			.standard()
			.withRegion(Regions.US_EAST_2)
			.withCredentials(awsCredentialsProvider)
			.build();
		this.awsInstanceAmi = awsProperties.getInstance().getAmi();
		this.awsInstanceSecurityGroup = awsProperties.getInstance().getSecurityGroup();
		this.awsInstanceKeyPair = awsProperties.getInstance().getKeyPair();
		this.awsInstanceType = awsProperties.getInstance().getType();
		this.awsInstanceTag = awsProperties.getInstance().getTag();
		this.awsMaxRetries = awsProperties.getMaxRetries();
		this.awsDelayBetweenRetries = awsProperties.getDelayBetweenRetries();
		this.awsConnectionTimeout = awsProperties.getConnectionTimeout();
	}

	public List<Instance> getInstances() {
		List<Instance> instances = new ArrayList<>();
		DescribeInstancesRequest request = new DescribeInstancesRequest();
		DescribeInstancesResult result;
		do {
			result = ec2.describeInstances(request);
			result.getReservations().stream().map(Reservation::getInstances).flatMap(List::stream)
				.filter(this::isManagerInstance).forEach(instances::add);
			request.setNextToken(result.getNextToken());
		} while (result.getNextToken() != null);
		return instances;
	}

	public Instance getInstance(String id) {
		DescribeInstancesRequest request = new DescribeInstancesRequest().withInstanceIds(id);
		DescribeInstancesResult result;
		do {
			result = ec2.describeInstances(request);
			Optional<Instance> instance = result.getReservations().stream().map(Reservation::getInstances)
				.flatMap(List::stream).filter(this::isManagerInstance).findFirst();
			if (instance.isPresent()) {
				return instance.get();
			}
			request.setNextToken(result.getNextToken());
		} while (result.getNextToken() != null);
		throw new ManagerException("Instance with id %s not found", id);
	}

	public List<AwsSimpleInstance> getSimpleInstances() {
		return getInstances().stream().map(AwsSimpleInstance::new).collect(Collectors.toList());
	}

	public Instance createInstance() {
		log.info("Creating new aws instance...");
		String instanceId = createEC2();
		Instance instance = waitInstanceState(instanceId, AwsInstanceState.RUNNING);
		String publicIpAddress = instance.getPublicIpAddress();
		log.info("New aws instance created: instanceId = {}, publicIpAddress = {}", instanceId, publicIpAddress);
		try {
			waitToBoot(instance);
		}
		catch (TimeoutException e) {
			e.printStackTrace();
		}
		return instance;
	}

	private String createEC2() {
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
			.withImageId(awsInstanceAmi)
			.withInstanceType(awsInstanceType)
			.withMinCount(1)
			.withMaxCount(1)
			.withSecurityGroups(awsInstanceSecurityGroup)
			.withKeyName(awsInstanceKeyPair);
		RunInstancesResult result = ec2.runInstances(runInstancesRequest);
		Instance instance = result.getReservation().getInstances().get(0);
		String instanceId = instance.getInstanceId();
		String instanceName = String.format("ubuntu-%d", System.currentTimeMillis());
		CreateTagsRequest createTagsRequest = new CreateTagsRequest().withResources(instanceId)
			.withTags(new Tag("Name", instanceName), new Tag(awsInstanceTag, "true"));
		ec2.createTags(createTagsRequest);
		return instanceId;
	}

	public Instance startInstance(String instanceId) {
		log.info("Starting instance {}", instanceId);
		Instance instance = setInstanceState(instanceId, AwsInstanceState.RUNNING);
		try {
			waitToBoot(instance);
		}
		catch (TimeoutException e) {
			e.printStackTrace();
		}
		return instance;
	}

	private void startInstanceById(String instanceId) {
		DryRunSupportedRequest<StartInstancesRequest> dryRequest = () ->
			new StartInstancesRequest().withInstanceIds(instanceId).getDryRunRequest();
		DryRunResult<StartInstancesRequest> dryResponse = ec2.dryRun(dryRequest);
		if (!dryResponse.isSuccessful()) {
			throw new ManagerException(dryResponse.getDryRunResponse().getErrorMessage());
		}
		StartInstancesRequest request = new StartInstancesRequest().withInstanceIds(instanceId);
		ec2.startInstances(request);
	}

	public Instance stopInstance(String instanceId) {
		log.info("Stopping instance {}", instanceId);
		return setInstanceState(instanceId, AwsInstanceState.STOPPED);
	}

	private void stopInstanceById(String instanceId) {
		DryRunSupportedRequest<StopInstancesRequest> dryRequest = () ->
			new StopInstancesRequest().withInstanceIds(instanceId).getDryRunRequest();
		DryRunResult<StopInstancesRequest> dryResponse = ec2.dryRun(dryRequest);
		if (!dryResponse.isSuccessful()) {
			throw new ManagerException(dryResponse.getDryRunResponse().getErrorMessage());
		}
		StopInstancesRequest request = new StopInstancesRequest().withInstanceIds(instanceId);
		ec2.stopInstances(request);
	}

	public Instance terminateInstance(String instanceId) {
		log.info("Terminating instance {}", instanceId);
		return setInstanceState(instanceId, AwsInstanceState.TERMINATED);
	}

	private void terminateInstanceById(String instanceId) {
		DryRunSupportedRequest<TerminateInstancesRequest> dryRequest = () ->
			new TerminateInstancesRequest().withInstanceIds(instanceId).getDryRunRequest();
		DryRunResult<TerminateInstancesRequest> dryResponse = ec2.dryRun(dryRequest);
		if (!dryResponse.isSuccessful()) {
			throw new ManagerException(dryResponse.getDryRunResponse().getErrorMessage());
		}
		TerminateInstancesRequest request = new TerminateInstancesRequest().withInstanceIds(instanceId);
		ec2.terminateInstances(request);
	}

	private Instance setInstanceState(String hostname, AwsInstanceState state) {
		for (int tries = 0; tries < awsMaxRetries; tries++) {
			Instance instance = getInstance(hostname);
			int instanceState = instance.getState().getCode();
			if (instanceState == state.getCode()) {
				log.info("Instance {} is already on state {}", hostname, state.getState());
				return instance;
			}
			try {
				switch (state) {
					case RUNNING:
						startInstanceById(hostname);
						break;
					case STOPPED:
						stopInstanceById(hostname);
						break;
					case TERMINATED:
						terminateInstanceById(hostname);
						break;
					default:
						throw new UnsupportedOperationException();
				}
				instance = waitInstanceState(hostname, state);
				log.info("Setting instance {} to {} state", hostname, state.getState());
				return instance;
			}
			catch (ManagerException e) {
				log.info("Failed to set instance {} to {} state: {}", hostname, state.getState(), e.getMessage());
			}
			Timing.sleep(awsDelayBetweenRetries, TimeUnit.MILLISECONDS);
		}
		throw new ManagerException("Unable to set instance state %d within %d tries",
			state.getState(), awsMaxRetries);
	}

	private Instance waitInstanceState(String instanceId, AwsInstanceState state) {
		Instance[] instance = new Instance[1];
		try {
			Timing.wait(() -> {
				instance[0] = getInstance(instanceId);
				return instance[0].getState().getCode() == state.getCode();
			}, awsConnectionTimeout);
		}
		catch (TimeoutException e) {
			log.info("Unknown status of instance {} {} operation: Timed out", instanceId, state.getState());
			throw new ManagerException(e.getMessage());
		}
		return instance[0];
	}

	private void waitToBoot(Instance instance) throws TimeoutException {
		HostAddress hostAddress = new HostAddress(instance.getPublicIpAddress(), instance.getPrivateIpAddress());
		log.info("Waiting for instance {} to boot...", hostAddress.getPublicIpAddress());
		Timing.wait(() -> sshService.hasConnection(hostAddress), 1000, awsConnectionTimeout);
	}

	private boolean isManagerInstance(Instance instance) {
		return instance.getTags().stream().anyMatch(tag ->
			Objects.equals(tag.getKey(), awsInstanceTag) && Objects.equals(tag.getValue(), "true"));
	}

}
