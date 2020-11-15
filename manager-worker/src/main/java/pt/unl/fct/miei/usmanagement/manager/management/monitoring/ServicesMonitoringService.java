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

package pt.unl.fct.miei.usmanagement.manager.management.monitoring;

import lombok.extern.slf4j.Slf4j;
import pt.unl.fct.miei.usmanagement.manager.apps.App;
import pt.unl.fct.miei.usmanagement.manager.containers.Container;
import pt.unl.fct.miei.usmanagement.manager.containers.ContainerConstants;
import pt.unl.fct.miei.usmanagement.manager.hosts.Coordinates;
import pt.unl.fct.miei.usmanagement.manager.hosts.HostAddress;
import pt.unl.fct.miei.usmanagement.manager.management.containers.ContainerProperties;
import pt.unl.fct.miei.usmanagement.manager.management.containers.ContainersService;
import pt.unl.fct.miei.usmanagement.manager.management.docker.containers.DockerContainer;
import pt.unl.fct.miei.usmanagement.manager.management.docker.containers.DockerContainersService;
import pt.unl.fct.miei.usmanagement.manager.management.hosts.HostsService;
import pt.unl.fct.miei.usmanagement.manager.management.location.LocationRequestsService;
import pt.unl.fct.miei.usmanagement.manager.management.monitoring.events.ContainerEvent;
import pt.unl.fct.miei.usmanagement.manager.management.monitoring.events.ServicesEventsService;
import pt.unl.fct.miei.usmanagement.manager.management.monitoring.metrics.ServiceMetricsService;
import pt.unl.fct.miei.usmanagement.manager.management.monitoring.metrics.simulated.AppSimulatedMetricsService;
import pt.unl.fct.miei.usmanagement.manager.management.monitoring.metrics.simulated.ContainerSimulatedMetricsService;
import pt.unl.fct.miei.usmanagement.manager.management.monitoring.metrics.simulated.ServiceSimulatedMetricsService;
import pt.unl.fct.miei.usmanagement.manager.management.rulesystem.decision.DecisionsService;
import pt.unl.fct.miei.usmanagement.manager.management.rulesystem.decision.ServiceDecisionResult;
import pt.unl.fct.miei.usmanagement.manager.management.rulesystem.rules.ServiceRulesService;
import pt.unl.fct.miei.usmanagement.manager.management.services.ServicesService;
import pt.unl.fct.miei.usmanagement.manager.management.workermanagers.WorkerManagerProperties;
import pt.unl.fct.miei.usmanagement.manager.monitoring.ContainerFieldAverage;
import pt.unl.fct.miei.usmanagement.manager.monitoring.ServiceEvent;
import pt.unl.fct.miei.usmanagement.manager.monitoring.ServiceFieldAverage;
import pt.unl.fct.miei.usmanagement.manager.monitoring.ServiceMonitoring;
import pt.unl.fct.miei.usmanagement.manager.monitoring.ServiceMonitoringLog;
import pt.unl.fct.miei.usmanagement.manager.monitoring.ServiceMonitoringLogs;
import pt.unl.fct.miei.usmanagement.manager.monitoring.ServiceMonitorings;
import pt.unl.fct.miei.usmanagement.manager.rulesystem.decision.ServiceDecision;
import pt.unl.fct.miei.usmanagement.manager.rulesystem.rules.RuleDecisionEnum;
import pt.unl.fct.miei.usmanagement.manager.sync.SyncService;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@Slf4j
public class ServicesMonitoringService {

	// Container minimum logs to start applying rules
	private static final int CONTAINER_MINIMUM_LOGS_COUNT = 1;

	private final ServiceMonitorings servicesMonitoring;
	private final ServiceMonitoringLogs serviceMonitoringLogs;

	private final DockerContainersService dockerContainersService;
	private final ContainersService containersService;
	private final ServicesService servicesService;
	private final ServiceRulesService serviceRulesService;
	private final ServicesEventsService servicesEventsService;
	private final HostsService hostsService;
	private final LocationRequestsService requestLocationMonitoringService;
	private final DecisionsService decisionsService;
	private final ServiceMetricsService serviceMetricsService;
	private final AppSimulatedMetricsService appSimulatedMetricsService;
	private final ServiceSimulatedMetricsService serviceSimulatedMetricsService;
	private final ContainerSimulatedMetricsService containerSimulatedMetricsService;
	private final ContainersRecoveryService containersRecoveryService;
	private final SyncService syncService;

	private final long monitorPeriod;
	private final int stopContainerOnEventCount;
	private final int replicateContainerOnEventCount;
	private final int migrateContainerOnEventCount;
	private final boolean isTestEnable;
	private Timer serviceMonitoringTimer;

	public ServicesMonitoringService(ServiceMonitorings servicesMonitoring,
									 ServiceMonitoringLogs serviceMonitoringLogs,
									 DockerContainersService dockerContainersService,
									 ContainersService containersService, ServicesService servicesService, ServiceRulesService serviceRulesService,
									 ServicesEventsService servicesEventsService, HostsService hostsService,
									 LocationRequestsService requestLocationMonitoringService,
									 DecisionsService decisionsService, ServiceMetricsService serviceMetricsService,
									 AppSimulatedMetricsService appSimulatedMetricsService,
									 ServiceSimulatedMetricsService serviceSimulatedMetricsService,
									 ContainerSimulatedMetricsService containerSimulatedMetricsService,
									 ContainersRecoveryService containersRecoveryService, SyncService syncService, ContainerProperties containerProperties,
									 WorkerManagerProperties workerManagerProperties) {
		this.serviceMonitoringLogs = serviceMonitoringLogs;
		this.servicesMonitoring = servicesMonitoring;
		this.dockerContainersService = dockerContainersService;
		this.containersService = containersService;
		this.servicesService = servicesService;
		this.serviceRulesService = serviceRulesService;
		this.servicesEventsService = servicesEventsService;
		this.hostsService = hostsService;
		this.requestLocationMonitoringService = requestLocationMonitoringService;
		this.decisionsService = decisionsService;
		this.serviceMetricsService = serviceMetricsService;
		this.appSimulatedMetricsService = appSimulatedMetricsService;
		this.serviceSimulatedMetricsService = serviceSimulatedMetricsService;
		this.containerSimulatedMetricsService = containerSimulatedMetricsService;
		this.containersRecoveryService = containersRecoveryService;
		this.syncService = syncService;
		this.monitorPeriod = containerProperties.getMonitorPeriod();
		this.stopContainerOnEventCount = containerProperties.getStopContainerOnEventCount();
		this.replicateContainerOnEventCount = containerProperties.getReplicateContainerOnEventCount();
		this.migrateContainerOnEventCount = containerProperties.getMigrateContainerOnEventCount();
		this.isTestEnable = workerManagerProperties.getTests().isEnabled();
	}

	public List<ServiceMonitoring> getServicesMonitoring() {
		return servicesMonitoring.findAll();
	}

	public List<ServiceMonitoring> getServiceMonitoring(String serviceName) {
		return servicesMonitoring.getByServiceNameIgnoreCase(serviceName);
	}

	public List<ServiceMonitoring> getContainerMonitoring(String containerId) {
		return servicesMonitoring.getByContainerId(containerId);
	}

	public ServiceMonitoring getContainerMonitoring(String containerId, String field) {
		return servicesMonitoring.getByContainerIdAndFieldIgnoreCase(containerId, field);
	}

	public void saveServiceMonitoring(String containerId, String serviceName, String field, double value) {
		ServiceMonitoring serviceMonitoring = getContainerMonitoring(containerId, field);
		Timestamp updateTime = Timestamp.from(Instant.now());
		if (serviceMonitoring == null) {
			serviceMonitoring = ServiceMonitoring.builder()
				.containerId(containerId)
				.serviceName(serviceName)
				.field(field)
				.minValue(value).maxValue(value).sumValue(value).lastValue(value)
				.count(1)
				.lastUpdate(updateTime)
				.build();
		}
		else {
			serviceMonitoring.update(value, updateTime);
		}
		servicesMonitoring.save(serviceMonitoring);
		if (isTestEnable) {
			saveServiceMonitoringLog(containerId, serviceName, field, value);
		}
	}

	public List<ServiceFieldAverage> getServiceFieldsAvg(String serviceName) {
		return servicesMonitoring.getServiceFieldsAvg(serviceName);
	}

	public ServiceFieldAverage getServiceFieldAverage(String serviceName, String field) {
		return servicesMonitoring.getServiceFieldAverage(serviceName, field);
	}

	public List<ContainerFieldAverage> getContainerFieldsAvg(String containerId) {
		return servicesMonitoring.getContainerFieldsAvg(containerId);
	}

	public ContainerFieldAverage getContainerFieldAverage(String containerId, String field) {
		return servicesMonitoring.getContainerFieldAverage(containerId, field);
	}

	public List<ServiceMonitoring> getTopContainersByField(List<String> containerIds, String field) {
		return servicesMonitoring.getTopContainersByField(containerIds, field);
	}

	public void saveServiceMonitoringLog(String containerId, String serviceName, String field, double effectiveValue) {
		ServiceMonitoringLog serviceMonitoringLog = ServiceMonitoringLog.builder()
			.containerId(containerId)
			.serviceName(serviceName)
			.field(field)
			.timestamp(LocalDateTime.now())
			.value(effectiveValue)
			.build();
		serviceMonitoringLogs.save(serviceMonitoringLog);
	}

	public List<ServiceMonitoringLog> getServiceMonitoringLogs() {
		return serviceMonitoringLogs.findAll();
	}

	public List<ServiceMonitoringLog> getServiceMonitoringLogsByServiceName(String serviceName) {
		return serviceMonitoringLogs.findByServiceName(serviceName);
	}

	public List<ServiceMonitoringLog> getServiceMonitoringLogsByContainerId(String containerId) {
		return serviceMonitoringLogs.findByContainerIdStartingWith(containerId);
	}

	public void initServiceMonitorTimer() {
		serviceMonitoringTimer = new Timer("services-monitoring", true);
		serviceMonitoringTimer.schedule(new TimerTask() {
			private long previousTime = System.currentTimeMillis();

			@Override
			public void run() {
				long currentTime = System.currentTimeMillis();
				int interval = (int) (currentTime - previousTime);
				previousTime = currentTime;
				try {
					monitorServicesTask(interval);
				}
				catch (Exception e) {
					log.error("Failed to execute monitor services task: {}", e.getMessage());
				}
			}
		}, monitorPeriod, monitorPeriod);
	}

	private void monitorServicesTask(int interval) {
		List<DockerContainer> monitoringContainers = dockerContainersService.getAppContainers();
		List<DockerContainer> systemContainers = dockerContainersService.getSystemContainers();
		List<Container> synchronizedContainers = syncService.synchronizeContainersDatabase();

		/*containersRecoveryService.restoreCrashedContainers(monitoringContainers, synchronizedContainers);*/

		/*systemContainers.parallelStream()
			.filter(container -> synchronizedContainers.stream().noneMatch(c -> Objects.equals(c.getContainerId(), container.getContainerId())))
			.distinct()
			.forEach(containersRecoveryService::restartContainer);*/

		Map<String, List<ServiceDecisionResult>> containersDecisions = new HashMap<>();

		monitoringContainers.forEach(container -> {
			/*if (synchronizedContainers.stream().noneMatch(c ->
				Objects.equals(c.getContainerId(), container.getContainerId()) && Objects.equals(c.getHostAddress(), container.getHostAddress()))) {
				containersService.launchContainer(container.getHostAddress(), container.getServiceName(), ContainerTypeEnum.SINGLETON);
			}
			else {*/
			HostAddress hostAddress = container.getHostAddress();
			String containerId = container.getId();
			String serviceName = container.getLabels().get(ContainerConstants.Label.SERVICE_NAME);

			// Metrics from docker
			Map<String, Double> stats = serviceMetricsService.getContainerStats(hostAddress, containerId);

			// Simulated app metrics
			for (App app : servicesService.getApps(serviceName)) {
				String appName = app.getName();
				Map<String, Double> appSimulatedFields = appSimulatedMetricsService.getAppSimulatedMetricByApp(appName)
					.stream().filter(metric -> metric.isActive() && (!stats.containsKey(metric.getName()) || metric.isOverride()))
					.collect(Collectors.toMap(metric -> metric.getField().getName(), appSimulatedMetricsService::randomizeFieldValue));
				stats.putAll(appSimulatedFields);
			}

			// Simulated service metrics
			Map<String, Double> serviceSimulatedFields = serviceSimulatedMetricsService.getServiceSimulatedMetricByService(serviceName)
				.stream().filter(metric -> metric.isActive() && (!stats.containsKey(metric.getName()) || metric.isOverride()))
				.collect(Collectors.toMap(metric -> metric.getField().getName(), serviceSimulatedMetricsService::randomizeFieldValue));
			stats.putAll(serviceSimulatedFields);

			// Simulated container metrics
			Map<String, Double> containerSimulatedFields = containerSimulatedMetricsService.getServiceSimulatedMetricByContainer(containerId)
				.stream().filter(metric -> metric.isActive() && (!stats.containsKey(metric.getName()) || metric.isOverride()))
				.collect(Collectors.toMap(metric -> metric.getField().getName(), containerSimulatedMetricsService::randomizeFieldValue));
			stats.putAll(containerSimulatedFields);

			// Calculated metrics
			Map<String, Double> calculatedMetrics = new HashMap<>(2);
			if (stats.containsKey("rx-bytes")
				&& !serviceSimulatedFields.containsKey("rx-bytes-per-sec")
				&& !containerSimulatedFields.containsKey("rx-bytes-per-sec")) {
				calculatedMetrics.put("rx-bytes", stats.get("rx-bytes"));
			}
			if (stats.containsKey("tx-bytes")
				&& !serviceSimulatedFields.containsKey("tx-bytes-per-sec")
				&& !containerSimulatedFields.containsKey("tx-bytes-per-sec")) {
				calculatedMetrics.put("tx-bytes", stats.get("tx-bytes"));
			}
			calculatedMetrics.forEach((field, value) -> {
				ServiceMonitoring monitoring = getContainerMonitoring(containerId, field);
				double lastValue = monitoring == null ? 0 : monitoring.getLastValue();
				double bytesPerSec = Math.max(0, (value - lastValue) / interval);
				stats.put(field + "-per-sec", bytesPerSec);
			});

			stats.forEach((stat, value) -> saveServiceMonitoring(containerId, serviceName, stat, value));

			ServiceDecisionResult containerDecisionResult = runRules(hostAddress, containerId, serviceName, stats);
			List<ServiceDecisionResult> containerDecisions = containersDecisions.get(serviceName);
			if (containerDecisions != null) {
				containerDecisions.add(containerDecisionResult);
			}
			else {
				containerDecisions = new LinkedList<>();
				containerDecisions.add(containerDecisionResult);
				containersDecisions.put(serviceName, containerDecisions);
			}
			/*}*/

		});

		if (!containersDecisions.isEmpty()) {
			processContainerDecisions(containersDecisions);
		}
		else {
			log.info("No service decisions to process");
		}
	}

	private ServiceDecisionResult runRules(HostAddress hostAddress, String containerId, String serviceName, Map<String, Double> newFields) {

		ContainerEvent containerEvent = new ContainerEvent(containerId, serviceName);
		Map<String, Double> containerEventFields = containerEvent.getFields();

		getContainerMonitoring(containerId)
			.stream()
			.filter(loggedField -> loggedField.getCount() >= CONTAINER_MINIMUM_LOGS_COUNT && newFields.get(loggedField.getField()) != null)
			.forEach(loggedField -> {
				String field = loggedField.getField();
				Double newValue = newFields.get(field);
				containerEventFields.put(field + "-effective-val", newValue);
				double average = loggedField.getSumValue() / loggedField.getCount();
				containerEventFields.put(field + "-avg-val", average);
				double deviationFromAverageValue = ((newValue - average) / average) * 100;
				containerEventFields.put(field + "-deviation-%-on-avg-val", deviationFromAverageValue);
				double lastValue = loggedField.getLastValue();
				double deviationFromLastValue = ((newValue - lastValue) / lastValue) * 100;
				containerEventFields.put(field + "-deviation-%-on-last-val", deviationFromLastValue);
			});

		return serviceRulesService.processServiceEvent(hostAddress, containerEvent);
	}

	private void processContainerDecisions(Map<String, List<ServiceDecisionResult>> servicesDecisions) {
		log.info("Processing container decisions...");
		Map<String, List<ServiceDecisionResult>> decisions = new HashMap<>();
		for (List<ServiceDecisionResult> serviceDecisions : servicesDecisions.values()) {
			for (ServiceDecisionResult containerDecision : serviceDecisions) {
				String serviceName = containerDecision.getServiceName();
				String containerId = containerDecision.getContainerId();
				RuleDecisionEnum decision = containerDecision.getDecision();
				log.info("Service {} on container {} had decision {}", serviceName, containerId, decision);
				ServiceEvent serviceEvent =
					servicesEventsService.saveServiceEvent(containerId, serviceName, decision.toString());
				int serviceEventCount = serviceEvent.getCount();
				if (decision == RuleDecisionEnum.STOP && serviceEventCount >= stopContainerOnEventCount
					|| decision == RuleDecisionEnum.REPLICATE && serviceEventCount >= replicateContainerOnEventCount
					|| decision == RuleDecisionEnum.MIGRATE && serviceEventCount >= migrateContainerOnEventCount) {
					List<ServiceDecisionResult> decisionsList = decisions.get(serviceName);
					if (decisionsList != null) {
						decisionsList.add(containerDecision);
					}
					else {
						decisionsList = new ArrayList<>(List.of(containerDecision));
						decisions.put(serviceName, decisionsList);
					}
				}
			}
		}
		if (!decisions.isEmpty()) {
			Map<String, Integer> replicasCount = servicesDecisions.entrySet().stream()
				.collect(Collectors.toMap(Entry::getKey, e -> e.getValue().size()));
			executeDecisions(decisions, replicasCount);
		}
	}

	private void executeDecisions(Map<String, List<ServiceDecisionResult>> decisions, Map<String, Integer> replicasCount) {
		Map<String, Coordinates> serviceWeightedMiddlePoint = requestLocationMonitoringService.getServicesWeightedMiddlePoint();
		for (Entry<String, List<ServiceDecisionResult>> servicesDecisions : decisions.entrySet()) {
			String serviceName = servicesDecisions.getKey();
			List<ServiceDecisionResult> containerDecisions = servicesDecisions.getValue();
			Collections.sort(containerDecisions);
			ServiceDecisionResult topPriorityDecisionResult = containerDecisions.get(0);
			int currentReplicas = replicasCount.get(serviceName);
			int minimumReplicas = servicesService.getMinimumReplicasByServiceName(serviceName);
			int maximumReplicas = servicesService.getMaximumReplicasByServiceName(serviceName);
			if (currentReplicas < minimumReplicas) {
				// start a new container to meet the requirements. The location is based on the data collected from the
				// location-request-monitor component
				Coordinates coordinates = serviceWeightedMiddlePoint.get(serviceName);
				if (coordinates == null) {
					coordinates = topPriorityDecisionResult.getHostAddress().getCoordinates();
				}
				double expectedMemoryConsumption = servicesService.getExpectedMemoryConsumption(serviceName);
				HostAddress hostAddress = hostsService.getClosestCapableHost(expectedMemoryConsumption, coordinates);
				log.info("Service {} has too few replicas ({}/{}). Starting another container close to {}",
					serviceName, currentReplicas, minimumReplicas, hostAddress);
				containersService.launchContainer(hostAddress, serviceName);
			}
			else {
				RuleDecisionEnum topPriorityDecision = topPriorityDecisionResult.getDecision();
				if (topPriorityDecision == RuleDecisionEnum.REPLICATE) {
					if (maximumReplicas == 0 || currentReplicas < maximumReplicas) {
						String containerId = topPriorityDecisionResult.getContainerId();
						String decision = topPriorityDecisionResult.getDecision().name();
						long ruleId = topPriorityDecisionResult.getRuleId();
						Map<String, Double> fields = topPriorityDecisionResult.getFields();
						Coordinates coordinates = serviceWeightedMiddlePoint.get(serviceName);
						if (coordinates == null) {
							coordinates = topPriorityDecisionResult.getHostAddress().getCoordinates();
						}
						double expectedMemoryConsumption = servicesService.getExpectedMemoryConsumption(serviceName);
						HostAddress toHostAddress = hostsService.getClosestCapableHost(expectedMemoryConsumption, coordinates);
						String replicatedContainerId = containersService.replicateContainer(containerId, toHostAddress).getContainerId();
						String result = String.format("replicated container %s of service %s to container %s on %s",
							containerId, serviceName, replicatedContainerId, toHostAddress);
						saveServiceDecision(containerId, serviceName, decision, ruleId, fields, result);
					}
				}
				else if (topPriorityDecision == RuleDecisionEnum.STOP) {
					if (currentReplicas > minimumReplicas) {
						ServiceDecisionResult leastPriorityContainer = containerDecisions.get(containerDecisions.size() - 1);
						String containerId = leastPriorityContainer.getContainerId();
						String decision = leastPriorityContainer.getDecision().name();
						long ruleId = leastPriorityContainer.getRuleId();
						HostAddress hostAddress = leastPriorityContainer.getHostAddress();
						Map<String, Double> fields = leastPriorityContainer.getFields();
						containersService.stopContainer(containerId);
						String result = String.format("stopped container %s of service %s on host %s", containerId, serviceName, hostAddress);
						saveServiceDecision(containerId, serviceName, decision, ruleId, fields, result);
					}
				}
			}
		}
	}

	private void saveServiceDecision(String containerId, String serviceName, String decision, long ruleId, Map<String, Double> fields, String result) {
		log.info("Executed decision: {}", result);
		servicesEventsService.resetServiceEvent(serviceName);
		ServiceDecision serviceDecision = decisionsService.addServiceDecision(containerId, serviceName, decision, ruleId, result);
		decisionsService.addServiceDecisionValueFromFields(serviceDecision, fields);
	}

	public void stopServiceMonitoring() {
		if (serviceMonitoringTimer != null) {
			serviceMonitoringTimer.cancel();
			log.info("Stopped service monitoring");
		}
	}

	public void reset() {
		log.info("Clearing all service monitoring");
		servicesMonitoring.deleteAll();
	}
}