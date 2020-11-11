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

package pt.unl.fct.miei.usmanagement.manager.management.services.discovery.registration;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import pt.unl.fct.miei.usmanagement.manager.containers.Container;
import pt.unl.fct.miei.usmanagement.manager.containers.ContainerConstants;
import pt.unl.fct.miei.usmanagement.manager.exceptions.ManagerException;
import pt.unl.fct.miei.usmanagement.manager.hosts.HostAddress;
import pt.unl.fct.miei.usmanagement.manager.hosts.cloud.AwsRegion;
import pt.unl.fct.miei.usmanagement.manager.management.containers.ContainersService;
import pt.unl.fct.miei.usmanagement.manager.management.hosts.HostsService;
import pt.unl.fct.miei.usmanagement.manager.management.hosts.cloud.CloudHostsService;
import pt.unl.fct.miei.usmanagement.manager.management.hosts.cloud.aws.AwsService;
import pt.unl.fct.miei.usmanagement.manager.management.regions.RegionsService;
import pt.unl.fct.miei.usmanagement.manager.management.services.ServicesService;
import pt.unl.fct.miei.usmanagement.manager.regions.RegionEnum;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RegistrationServerService {

	public static final String REGISTRATION_SERVER = "registration-server";

	private final HostsService hostsService;
	private final ServicesService servicesService;
	private final ContainersService containersService;
	private final AwsService awsService;
	private final RegionsService regionsService;
	private final CloudHostsService cloudHostsService;

	private final int port;

	public RegistrationServerService(HostsService hostsService, ServicesService servicesService,
									 @Lazy ContainersService containersService, AwsService awsService,
									 RegionsService regionsService, CloudHostsService cloudHostsService,
									 RegistrationProperties registrationProperties) {
		this.hostsService = hostsService;
		this.servicesService = servicesService;
		this.containersService = containersService;
		this.awsService = awsService;
		this.regionsService = regionsService;
		this.cloudHostsService = cloudHostsService;
		this.port = registrationProperties.getPort();
	}

	public Container launchRegistrationServer(RegionEnum region) {
		return launchRegistrationServers(List.of(region)).get(0);
	}

	public Container launchRegistrationServer(HostAddress hostAddress) {
		List<String> customEnvs = Collections.emptyList();

		Map<String, String> customLabels = Collections.emptyMap();

		String registrationServers = getRegistrationServerAddresses();
		Map<String, String> dynamicLaunchParams = Map.of("${zone}", registrationServers);

		Container container = containersService.launchContainer(hostAddress, REGISTRATION_SERVER, customEnvs, customLabels, dynamicLaunchParams);

		AwsRegion awsRegion = regionsService.getAwsRegion(hostAddress.getRegion());
		String instanceId = cloudHostsService.getCloudHostByAddress(hostAddress).getInstanceId();

		String allocationId = awsService.getAllocatedElasticIps().get(awsRegion).getAllocationId();
		try {
			awsService.associateElasticIpAddress(allocationId, instanceId).get();
		}
		catch (InterruptedException | ExecutionException e) {
			throw new ManagerException("Failed to associate elastic ip address to the registration server: %s", e.getMessage());
		}

		return container;
	}

	public List<Container> launchRegistrationServers(List<RegionEnum> regions) {
		log.info("Launching registration servers at regions {}", regions);

		double expectedMemoryConsumption = servicesService.getExpectedMemoryConsumption(REGISTRATION_SERVER);

		List<HostAddress> availableHosts = regions.parallelStream()
			.map(region ->
				hostsService.getCapableNode(expectedMemoryConsumption, region, node -> cloudHostsService.hasCloudHost(node.getPublicIpAddress())))
			.distinct()
			.collect(Collectors.toList());

		List<String> customEnvs = Collections.emptyList();

		Map<String, String> customLabels = Collections.emptyMap();

		String registrationServers = getRegistrationServerAddresses();
		Map<String, String> dynamicLaunchParams = Map.of("${zone}", registrationServers);

		Gson gson = new Gson();
		return availableHosts.stream()
			.map(hostAddress -> {
				// avoid launching another registration server on the same region
				List<Container> containers = containersService.getContainersWithLabels(Set.of(
					Pair.of(ContainerConstants.Label.SERVICE_NAME, REGISTRATION_SERVER),
					Pair.of(ContainerConstants.Label.REGION, gson.toJson(hostAddress.getRegion()))
				));
				return !containers.isEmpty() ?
					containers.get(0)
					: containersService.launchContainer(hostAddress, REGISTRATION_SERVER, customEnvs, customLabels, dynamicLaunchParams);
			}).collect(Collectors.toList());
	}

	private List<HostAddress> getRegistrationServers() {
		return containersService.getContainersWithLabels(Set.of(
			Pair.of(ContainerConstants.Label.SERVICE_NAME, REGISTRATION_SERVER))
		).stream().map(Container::getHostAddress).collect(Collectors.toList());
	}

	private List<HostAddress> getRegistrationServerHosts(RegionEnum region) {
		return containersService.getContainersWithLabels(Set.of(
			Pair.of(ContainerConstants.Label.SERVICE_NAME, REGISTRATION_SERVER),
			Pair.of(ContainerConstants.Label.REGION, region.name()))
		).stream().map(Container::getHostAddress).collect(Collectors.toList());
	}

	private String getRegistrationServerAddresses() {
		return awsService.getAllocatedElasticIps().values().stream()
			.map(address -> String.format("http://%s:%s/eureka/", address.getPublicIp(), port))
			.collect(Collectors.joining(","));
	}

	public Optional<String> getRegistrationServerAddress(RegionEnum region) {
		return containersService.getContainersWithLabels(Set.of(
			Pair.of(ContainerConstants.Label.SERVICE_NAME, REGISTRATION_SERVER),
			Pair.of(ContainerConstants.Label.REGION, region.name())))
			.stream()
			.map(container -> container.getLabels().get(ContainerConstants.Label.SERVICE_ADDRESS))
			.findFirst();
	}

}
