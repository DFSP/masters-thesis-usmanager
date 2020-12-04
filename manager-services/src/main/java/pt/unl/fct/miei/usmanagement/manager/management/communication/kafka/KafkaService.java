package pt.unl.fct.miei.usmanagement.manager.management.communication.kafka;

import com.google.common.base.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import pt.unl.fct.miei.usmanagement.manager.apps.App;
import pt.unl.fct.miei.usmanagement.manager.componenttypes.ComponentType;
import pt.unl.fct.miei.usmanagement.manager.containers.Container;
import pt.unl.fct.miei.usmanagement.manager.containers.ContainerConstants;
import pt.unl.fct.miei.usmanagement.manager.eips.ElasticIp;
import pt.unl.fct.miei.usmanagement.manager.exceptions.EntityNotFoundException;
import pt.unl.fct.miei.usmanagement.manager.fields.Field;
import pt.unl.fct.miei.usmanagement.manager.hosts.HostAddress;
import pt.unl.fct.miei.usmanagement.manager.hosts.cloud.CloudHost;
import pt.unl.fct.miei.usmanagement.manager.hosts.edge.EdgeHost;
import pt.unl.fct.miei.usmanagement.manager.kafka.KafkaBroker;
import pt.unl.fct.miei.usmanagement.manager.kafka.KafkaBrokers;
import pt.unl.fct.miei.usmanagement.manager.management.apps.AppsService;
import pt.unl.fct.miei.usmanagement.manager.management.communication.zookeeper.ZookeeperService;
import pt.unl.fct.miei.usmanagement.manager.management.componenttypes.ComponentTypesService;
import pt.unl.fct.miei.usmanagement.manager.management.containers.ContainersService;
import pt.unl.fct.miei.usmanagement.manager.management.docker.nodes.NodesService;
import pt.unl.fct.miei.usmanagement.manager.management.eips.ElasticIpsService;
import pt.unl.fct.miei.usmanagement.manager.management.fields.FieldsService;
import pt.unl.fct.miei.usmanagement.manager.management.hosts.cloud.CloudHostsService;
import pt.unl.fct.miei.usmanagement.manager.management.hosts.edge.EdgeHostsService;
import pt.unl.fct.miei.usmanagement.manager.management.monitoring.events.HostsEventsService;
import pt.unl.fct.miei.usmanagement.manager.management.monitoring.events.ServicesEventsService;
import pt.unl.fct.miei.usmanagement.manager.management.monitoring.metrics.simulated.AppSimulatedMetricsService;
import pt.unl.fct.miei.usmanagement.manager.management.monitoring.metrics.simulated.ContainerSimulatedMetricsService;
import pt.unl.fct.miei.usmanagement.manager.management.monitoring.metrics.simulated.HostSimulatedMetricsService;
import pt.unl.fct.miei.usmanagement.manager.management.monitoring.metrics.simulated.ServiceSimulatedMetricsService;
import pt.unl.fct.miei.usmanagement.manager.management.operators.OperatorsService;
import pt.unl.fct.miei.usmanagement.manager.management.rulesystem.condition.ConditionsService;
import pt.unl.fct.miei.usmanagement.manager.management.rulesystem.decision.DecisionsService;
import pt.unl.fct.miei.usmanagement.manager.management.rulesystem.rules.AppRulesService;
import pt.unl.fct.miei.usmanagement.manager.management.rulesystem.rules.ContainerRulesService;
import pt.unl.fct.miei.usmanagement.manager.management.rulesystem.rules.HostRulesService;
import pt.unl.fct.miei.usmanagement.manager.management.rulesystem.rules.ServiceRulesService;
import pt.unl.fct.miei.usmanagement.manager.management.services.ServicesService;
import pt.unl.fct.miei.usmanagement.manager.management.valuemodes.ValueModesService;
import pt.unl.fct.miei.usmanagement.manager.metrics.simulated.AppSimulatedMetric;
import pt.unl.fct.miei.usmanagement.manager.metrics.simulated.ContainerSimulatedMetric;
import pt.unl.fct.miei.usmanagement.manager.metrics.simulated.HostSimulatedMetric;
import pt.unl.fct.miei.usmanagement.manager.metrics.simulated.ServiceSimulatedMetric;
import pt.unl.fct.miei.usmanagement.manager.monitoring.ServiceMonitoringLog;
import pt.unl.fct.miei.usmanagement.manager.nodes.Node;
import pt.unl.fct.miei.usmanagement.manager.operators.Operator;
import pt.unl.fct.miei.usmanagement.manager.regions.RegionEnum;
import pt.unl.fct.miei.usmanagement.manager.rulesystem.condition.Condition;
import pt.unl.fct.miei.usmanagement.manager.rulesystem.decision.Decision;
import pt.unl.fct.miei.usmanagement.manager.rulesystem.decision.HostDecision;
import pt.unl.fct.miei.usmanagement.manager.rulesystem.decision.ServiceDecision;
import pt.unl.fct.miei.usmanagement.manager.rulesystem.rules.AppRule;
import pt.unl.fct.miei.usmanagement.manager.rulesystem.rules.ContainerRule;
import pt.unl.fct.miei.usmanagement.manager.rulesystem.rules.HostRule;
import pt.unl.fct.miei.usmanagement.manager.rulesystem.rules.ServiceRule;
import pt.unl.fct.miei.usmanagement.manager.services.ServiceConstants;
import pt.unl.fct.miei.usmanagement.manager.util.Timing;
import pt.unl.fct.miei.usmanagement.manager.valuemodes.ValueMode;
import pt.unl.fct.miei.usmanagement.manager.zookeeper.Zookeeper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KafkaService {

	private static final int PORT = 9092;
	private static final int MIN_POPULATE_SLEEP = 5000;
	private static final int MAX_POPULATE_SLEEP = 15000;

	private final AppsService appsService;
	private final CloudHostsService cloudHostsService;
	private final ComponentTypesService componentTypesService;
	private final ConditionsService conditionsService;
	private final ContainersService containersService;
	private final DecisionsService decisionsService;
	private final EdgeHostsService edgeHostsService;
	private final ElasticIpsService elasticIpsService;
	private final FieldsService fieldsService;
	private final NodesService nodesService;
	private final OperatorsService operatorsService;
	private final ServicesService servicesService;
	private final HostSimulatedMetricsService hostSimulatedMetricsService;
	private final AppSimulatedMetricsService appSimulatedMetricsService;
	private final ServiceSimulatedMetricsService serviceSimulatedMetricsService;
	private final ContainerSimulatedMetricsService containerSimulatedMetricsService;
	private final HostRulesService hostRulesService;
	private final AppRulesService appRulesService;
	private final ServiceRulesService serviceRulesService;
	private final ContainerRulesService containerRulesService;
	private final ValueModesService valueModesService;

	private final ZookeeperService zookeeperService;
	private final ProducerFactory<String, Object> producerFactory;
	private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

	private final KafkaBrokers kafkaBrokers;

	private final KafkaTemplate<String, Object> kafkaTemplate;
	private final AtomicLong increment;
	private boolean populated;

	public KafkaService(@Lazy ContainersService containersService,
						@Lazy AppsService appsService,
						@Lazy CloudHostsService cloudHostsService,
						@Lazy ComponentTypesService componentTypesService,
						@Lazy ConditionsService conditionsService,
						@Lazy DecisionsService decisionsService,
						@Lazy EdgeHostsService edgeHostsService,
						@Lazy ElasticIpsService elasticIpsService,
						@Lazy FieldsService fieldsService,
						@Lazy NodesService nodesService,
						@Lazy OperatorsService operatorsService,
						@Lazy ServicesService servicesService,
						@Lazy ServiceRulesService serviceRulesService,
						@Lazy HostSimulatedMetricsService hostSimulatedMetricsService,
						@Lazy AppSimulatedMetricsService appSimulatedMetricsService,
						@Lazy ServiceSimulatedMetricsService serviceSimulatedMetricsService,
						@Lazy ContainerSimulatedMetricsService containerSimulatedMetricsService,
						@Lazy HostRulesService hostRulesService,
						@Lazy AppRulesService appRulesService,
						@Lazy ContainerRulesService containerRulesService,
						@Lazy ValueModesService valueModesService,
						HostsEventsService hostsEventsService, ServicesEventsService servicesEventsService, ZookeeperService zookeeperService,
						@Lazy ProducerFactory<String, Object> producerFactory,
						KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry,
						KafkaBrokers kafkaBrokers,
						@Lazy KafkaTemplate<String, Object> kafkaTemplate) {
		this.appsService = appsService;
		this.cloudHostsService = cloudHostsService;
		this.componentTypesService = componentTypesService;
		this.conditionsService = conditionsService;
		this.decisionsService = decisionsService;
		this.edgeHostsService = edgeHostsService;
		this.fieldsService = fieldsService;
		this.nodesService = nodesService;
		this.operatorsService = operatorsService;
		this.serviceRulesService = serviceRulesService;
		this.hostSimulatedMetricsService = hostSimulatedMetricsService;
		this.appSimulatedMetricsService = appSimulatedMetricsService;
		this.serviceSimulatedMetricsService = serviceSimulatedMetricsService;
		this.containerSimulatedMetricsService = containerSimulatedMetricsService;
		this.hostRulesService = hostRulesService;
		this.appRulesService = appRulesService;
		this.containerRulesService = containerRulesService;
		this.valueModesService = valueModesService;
		this.containersService = containersService;
		this.elasticIpsService = elasticIpsService;
		this.servicesService = servicesService;
		this.zookeeperService = zookeeperService;
		this.producerFactory = producerFactory;
		this.kafkaBrokers = kafkaBrokers;
		this.kafkaTemplate = kafkaTemplate;
		this.kafkaListenerEndpointRegistry = kafkaListenerEndpointRegistry;
		this.increment = new AtomicLong();
		this.populated = false;
	}

	public KafkaBroker launchKafkaBroker(RegionEnum region) {
		return launchKafkaBrokers(List.of(region)).get(0);
	}

	public List<KafkaBroker> launchKafkaBrokers(List<RegionEnum> regions) {
		log.info("Launching kafka brokers at regions {}", regions);

		int previousKafkaBrokersCount = getKafkaBrokers().size();

		List<CompletableFuture<KafkaBroker>> futureKafkaBrokers = regions.stream().map(region -> {
			List<KafkaBroker> regionKafkaBrokers = getKafkaBroker(region);
			if (regionKafkaBrokers.size() > 0) {
				return CompletableFuture.completedFuture(regionKafkaBrokers.get(0));
			}
			else {
				HostAddress hostAddress = elasticIpsService.getHost(region);
				return launchKafkaBroker(hostAddress);
			}
		}).collect(Collectors.toList());

		CompletableFuture.allOf(futureKafkaBrokers.toArray(new CompletableFuture[0])).join();

		List<KafkaBroker> kafkaBrokers = new ArrayList<>();
		for (CompletableFuture<KafkaBroker> futureKafkaBroker : futureKafkaBrokers) {
			KafkaBroker kafkaBroker = futureKafkaBroker.join();
			kafkaBrokers.add(kafkaBroker);
		}

		if (previousKafkaBrokersCount == 0) {
			startConsumers();
			populateTopics();
		}

		return kafkaBrokers;
	}

	@Async
	public CompletableFuture<KafkaBroker> launchKafkaBroker(HostAddress hostAddress) {
		RegionEnum region = hostAddress.getRegion();
		List<Zookeeper> zookeepers = zookeeperService.getZookeepers(region);
		if (zookeepers.size() == 0) {
			zookeepers.add(zookeeperService.launchZookeeper(hostAddress));
		}
		else if (!Objects.equal(zookeepers.get(0).getContainer().getHostAddress(), hostAddress)) {
			zookeeperService.stopZookeeper(zookeepers.get(0).getId());
			zookeepers.add(zookeeperService.launchZookeeper(hostAddress));
		}
		long brokerId = increment.getAndIncrement() + 1;
		String zookeeperConnect = String.format("%s:%d", zookeepers.get(0).getContainer().getPrivateIpAddress(),
			servicesService.getService(ServiceConstants.Name.ZOOKEEPER).getDefaultExternalPort());
		String listeners = String.format("PLAINTEXT://:%d", PORT);
		String advertisedListeners = String.format("PLAINTEXT://%s:%d", hostAddress.getPublicIpAddress(), PORT);
		List<String> environment = List.of(
			String.format("%s=%s", ContainerConstants.Environment.Kafka.KAFKA_BROKER_ID, brokerId),
			String.format("%s=%s", ContainerConstants.Environment.Kafka.KAFKA_ZOOKEEPER_CONNECT, zookeeperConnect),
			String.format("%s=%s", ContainerConstants.Environment.Kafka.KAFKA_LISTENERS, listeners),
			String.format("%s=%s", ContainerConstants.Environment.Kafka.KAFKA_ADVERTISED_LISTENERS, advertisedListeners),
			String.format("%s=%s", ContainerConstants.Environment.Kafka.KAFKA_CREATE_TOPICS, topics())
		);
		Map<String, String> labels = Map.of(
			ContainerConstants.Label.KAFKA_BROKER_ID, String.valueOf(brokerId)
		);
		Container container = containersService.launchContainer(hostAddress, ServiceConstants.Name.KAFKA, environment, labels);
		return CompletableFuture.completedFuture(saveKafkaBroker(container));
	}

	public List<KafkaBroker> getKafkaBrokers() {
		return kafkaBrokers.findAll();
	}

	public String getKafkaBrokersHosts() {
		return elasticIpsService.getElasticIps().stream()
			.filter(elasticIp -> elasticIp.getAssociationId() != null)
			.map(elasticIp -> String.format("%s:%d", elasticIp.getPublicIp(), PORT))
			.collect(Collectors.joining(","));
	}

	private List<KafkaBroker> getKafkaBroker(RegionEnum region) {
		return kafkaBrokers.getByRegion(region);
	}

	public KafkaBroker getKafkaBroker(Long id) {
		return kafkaBrokers.findByBrokerId(id).orElseThrow(() ->
			new EntityNotFoundException(KafkaBroker.class, "id", String.valueOf(id)));
	}

	public KafkaBroker getKafkaBrokerByContainer(Container container) {
		return kafkaBrokers.getByContainer(container).orElseThrow(() ->
			new EntityNotFoundException(KafkaBroker.class, "container", container.getId()));
	}

	public void stopKafkaBroker(Long id) {
		KafkaBroker kafkaBroker = getKafkaBroker(id);
		String containerId = kafkaBroker.getContainer().getId();
		kafkaBrokers.delete(kafkaBroker);
		containersService.stopContainer(containerId);
		if (!hasKafkaBrokers()) {
			this.stop();
		}
	}

	public void deleteKafkaBrokerByContainer(Container container) {
		KafkaBroker kafkaBroker = getKafkaBrokerByContainer(container);
		kafkaBrokers.delete(kafkaBroker);
		if (!hasKafkaBrokers()) {
			this.stop();
		}
	}

	public void reset() {
		kafkaBrokers.deleteAll();
	}

	public KafkaBroker saveKafkaBroker(Container container) {
		long brokerId = Long.parseLong(container.getLabels().get(ContainerConstants.Label.KAFKA_BROKER_ID));
		return kafkaBrokers.save(KafkaBroker.builder().brokerId(brokerId).container(container).region(container.getRegion()).build());
	}

	public boolean hasKafkaBroker(Container container) {
		return kafkaBrokers.hasKafkaBrokerByContainer(container.getId());
	}

	public boolean hasKafkaBrokers() {
		return kafkaBrokers.hasKafkaBrokers();
	}

	private void startConsumers() {
		kafkaListenerEndpointRegistry.start();
	}

	private void populateTopics() {
		Random random = new Random();
		do {
			try {
				for (Map.Entry<String, Supplier<?>> topicKeyValue : topicsValues().entrySet()) {
					String topic = topicKeyValue.getKey();
					List<?> values = (List<?>) topicKeyValue.getValue().get();
					values.forEach(value -> kafkaTemplate.send(topic, value));
				}
				populated = true;
			}
			catch (KafkaException e) {
				String message = e.getMessage();
				int randomSleep = random.nextInt(MAX_POPULATE_SLEEP - MIN_POPULATE_SLEEP) + MIN_POPULATE_SLEEP;
				log.error("Failed to populate kafka: {}... Retrying in {} ms", message, randomSleep);
				Timing.sleep(randomSleep, TimeUnit.MILLISECONDS);
			}
		} while (!populated);
	}

	private Map<String, Supplier<?>> topicsValues() {
		Map<String, Supplier<?>> topicsValues = new HashMap<>();

		topicsValues.put("apps", () -> appsService.getApps().stream().map(AppMessage::new).collect(Collectors.toList()));
		topicsValues.put("cloud-hosts", () -> cloudHostsService.getCloudHosts().stream().map(CloudHostMessage::new).collect(Collectors.toList()));
		topicsValues.put("component-types", () -> componentTypesService.getComponentTypes().stream().map(ComponentTypeMessage::new).collect(Collectors.toList()));
		topicsValues.put("conditions", () -> conditionsService.getConditions().stream().map(ConditionMessage::new).collect(Collectors.toList()));
		topicsValues.put("containers", () -> containersService.getContainers().stream().map(ContainerMessage::new).collect(Collectors.toList()));
		topicsValues.put("decisions", () -> decisionsService.getDecisions().stream().map(DecisionMessage::new).collect(Collectors.toList()));
		topicsValues.put("edge-hosts", () -> edgeHostsService.getEdgeHosts().stream().map(EdgeHostMessage::new).collect(Collectors.toList()));
		topicsValues.put("eips", () -> elasticIpsService.getElasticIps().stream().map(ElasticIpMessage::new).collect(Collectors.toList()));
		topicsValues.put("fields", () -> fieldsService.getFields().stream().map(FieldMessage::new).collect(Collectors.toList()));
		topicsValues.put("nodes", () -> nodesService.getNodes().stream().map(NodeMessage::new).collect(Collectors.toList()));
		topicsValues.put("operators", () -> operatorsService.getOperators().stream().map(OperatorMessage::new).collect(Collectors.toList()));
		topicsValues.put("services", () -> servicesService.getServices().stream().map(ServiceMessage::new).collect(Collectors.toList()));
		topicsValues.put("simulated-host-metrics", () -> hostSimulatedMetricsService.getHostSimulatedMetrics().stream().map(HostSimulatedMetricMessage::new).collect(Collectors.toList()));
		topicsValues.put("simulated-app-metrics", () -> appSimulatedMetricsService.getAppSimulatedMetrics().stream().map(AppSimulatedMetricMessage::new).collect(Collectors.toList()));
		topicsValues.put("simulated-service-metrics", () -> serviceSimulatedMetricsService.getServiceSimulatedMetrics().stream().map(ServiceSimulatedMetricMessage::new).collect(Collectors.toList()));
		topicsValues.put("simulated-container-metrics", () -> containerSimulatedMetricsService.getContainerSimulatedMetrics().stream().map(ContainerSimulatedMetricMessage::new).collect(Collectors.toList()));
		topicsValues.put("host-rules", () -> hostRulesService.getRules().stream().map(HostRuleMessage::new).collect(Collectors.toList()));
		topicsValues.put("app-rules", () -> appRulesService.getRules().stream().map(AppRuleMessage::new).collect(Collectors.toList()));
		topicsValues.put("service-rules", () -> serviceRulesService.getRules().stream().map(ServiceRuleMessage::new).collect(Collectors.toList()));
		topicsValues.put("container-rules", () -> containerRulesService.getRules().stream().map(ContainerRuleMessage::new).collect(Collectors.toList()));
		topicsValues.put("value-modes", () -> valueModesService.getValueModes().stream().map(ValueModeMessage::new).collect(Collectors.toList()));
		return topicsValues;
	}

	private String topics() {
		String masterManagerTopics = "apps:1:1,cloud-hosts:1:1,component-types:1:1,conditions:1:1,containers:1:1,decisions:1:1,"
			+ "edge-hosts:1:1,eips:1:1,fields:1:1,nodes:1:1,operators:1:1,services:1:1,simulated-host-metrics:1:1,"
			+ "simulated-app-metrics:1:1,simulated-service-metrics:1:1,simulated-container-metrics:1:1,host-rules:1:1,"
			+ "app-rules:1:1,service-rules:1:1,container-rules:1:1,value-modes:1:1";
		String workerManagerTopics = "host-events:1:1,service-events:1:1,host-monitoring-logs:1:1,service-monitoring-logs:1:1,"
			+ "host-decisions:1:1,service-decisions:1:1";
		return masterManagerTopics + "," + workerManagerTopics;
	}

	@Async
	public void sendApp(App app) {
		send("apps", new AppMessage(app), app.getId());
	}

	@Async
	public void deleteApp(App app) {
		sendDelete("apps", app.getId());
	}

	@Async
	public void sendCloudHost(CloudHost cloudHost) {
		send("cloud-hosts", new CloudHostMessage(cloudHost), cloudHost.getId());
	}

	@Async
	public void deleteCloudHost(CloudHost cloudHost) {
		sendDelete("cloud-hosts", cloudHost.getId());
	}

	@Async
	public void sendComponentType(ComponentType componentType) {
		send("component-types", new ComponentTypeMessage(componentType), componentType.getId());
	}

	@Async
	public void deleteComponentType(ComponentType componentType) {
		sendDelete("component-types", componentType.getId());
	}

	@Async
	public void sendCondition(Condition condition) {
		send("conditions", new ConditionMessage(condition), condition.getId());
	}

	@Async
	public void deleteCondition(Condition condition) {
		sendDelete("conditions", condition.getId());
	}

	@Async
	public void sendContainer(Container container) {
		send("containers", new ContainerMessage(container), container.getId());
	}

	@Async
	public void deleteContainer(Container container) {
		sendDelete("containers", container.getId());
	}

	@Async
	public void sendDecision(Decision decision) {
		send("decisions", new DecisionMessage(decision), decision.getId());
	}

	@Async
	public void deleteDecision(Decision decision) {
		sendDelete("decisions", decision.getId());
	}

	@Async
	public void sendEdgeHost(EdgeHost edgeHost) {
		send("edge-hosts", new EdgeHostMessage(edgeHost), edgeHost.getId());
	}

	@Async
	public void deleteEdgeHost(EdgeHost edgeHost) {
		sendDelete("edge-hosts", edgeHost.getId());
	}

	@Async
	public void sendElasticIp(ElasticIp elasticIp) {
		send("eips", new ElasticIpMessage(elasticIp), elasticIp.getId());
	}

	@Async
	public void deleteElasticIp(ElasticIp elasticIp) {
		sendDelete("eips", elasticIp.getId());
	}

	@Async
	public void sendField(Field field) {
		send("fields", new FieldMessage(field), field.getId());
	}

	@Async
	public void deleteField(Field field) {
		sendDelete("fields", field.getId());
	}

	@Async
	public void sendNode(Node node) {
		send("nodes", new NodeMessage(node), node.getId());
	}

	@Async
	public void deleteNode(Node node) {
		sendDelete("nodes", node.getId());
	}

	@Async
	public void sendOperator(Operator operator) {
		send("operators", new OperatorMessage(operator), operator.getId());
	}

	@Async
	public void deleteOperator(Operator operator) {
		sendDelete("operators", operator.getId());
	}

	@Async
	public void sendService(pt.unl.fct.miei.usmanagement.manager.services.Service service) {
		send("services", new ServiceMessage(service), service.getId());
	}

	@Async
	public void deleteService(pt.unl.fct.miei.usmanagement.manager.services.Service service) {
		sendDelete("services", service.getId());
	}

	@Async
	public void sendHostSimulatedMetric(HostSimulatedMetric hostSimulatedMetric) {
		send("simulated-host-metrics", new HostSimulatedMetricMessage(hostSimulatedMetric), hostSimulatedMetric.getId());
	}

	@Async
	public void deleteHostSimulatedMetric(HostSimulatedMetric hostSimulatedMetric) {
		sendDelete("simulated-host-metrics", hostSimulatedMetric.getId());
	}

	@Async
	public void sendAppSimulatedMetric(AppSimulatedMetric appSimulatedMetric) {
		send("simulated-app-metrics", new AppSimulatedMetricMessage(appSimulatedMetric), appSimulatedMetric.getId());
	}

	@Async
	public void deleteAppSimulatedMetric(AppSimulatedMetric appSimulatedMetric) {
		sendDelete("simulated-app-metrics", appSimulatedMetric.getId());
	}

	@Async
	public void sendServiceSimulatedMetric(ServiceSimulatedMetric serviceSimulatedMetric) {
		send("simulated-service-metrics", new ServiceSimulatedMetricMessage(serviceSimulatedMetric), serviceSimulatedMetric.getId());
	}

	@Async
	public void deleteServiceSimulatedMetric(ServiceSimulatedMetric serviceSimulatedMetric) {
		sendDelete("simulated-service-metrics", serviceSimulatedMetric.getId());
	}

	@Async
	public void sendContainerSimulatedMetric(ContainerSimulatedMetric containerSimulatedMetric) {
		send("simulated-container-metrics", new ContainerSimulatedMetricMessage(containerSimulatedMetric), containerSimulatedMetric.getId());
	}

	@Async
	public void deleteContainerSimulatedMetric(ContainerSimulatedMetric containerSimulatedMetric) {
		sendDelete("simulated-container-metrics", containerSimulatedMetric.getId());
	}

	@Async
	public void sendHostRule(HostRule hostRule) {
		send("host-rules", new HostRuleMessage(hostRule), hostRule.getId());
	}

	@Async
	public void deleteHostRule(HostRule hostRule) {
		sendDelete("host-rules", hostRule.getId());
	}

	@Async
	public void sendAppRule(AppRule appRule) {
		send("app-rules", new AppRuleMessage(appRule), appRule.getId());
	}

	@Async
	public void deleteAppRule(AppRule appRule) {
		sendDelete("app-rules", appRule.getId());
	}

	@Async
	public void sendServiceRule(ServiceRule serviceRule) {
		send("service-rules", new ServiceRuleMessage(serviceRule), serviceRule.getId());
	}

	@Async
	public void deleteServiceRule(ServiceRule serviceRule) {
		sendDelete("service-rules", serviceRule.getId());
	}

	@Async
	public void sendContainerRule(ContainerRule containerRule) {
		send("container-rules", new ContainerRuleMessage(containerRule), containerRule.getId());
	}

	@Async
	public void deleteContainerRule(ContainerRule containerRule) {
		sendDelete("container-rules", containerRule.getId());
	}

	@Async
	public void sendValueMode(ValueMode valueMode) {
		send("value-modes", new ValueModeMessage(valueMode), valueMode.getId());
	}

	@Async
	public void deleteValueMode(ValueMode valueMode) {
		sendDelete("value-modes", valueMode.getId());
	}

	@Async
	public void send(String topic, Object message, Object id) {
		boolean hasKafkaBrokers = hasKafkaBrokers();
		if (hasKafkaBrokers && populated) {
			log.info("Sending {} to kafka", message.toString());
			kafkaTemplate.send(topic, message);
		}
		else {
			log.warn("Not sending message id={} to kafka topic {} because hasKafkaBrokers={} and populated={}",
				id, topic, hasKafkaBrokers, populated);
		}
	}

	@Async
	public void sendDelete(String topic, Object id) {
		boolean hasKafkaBrokers = hasKafkaBrokers();
		if (hasKafkaBrokers && populated) {
			log.info("Sending DELETE id={} request to kafka topic {}", id, topic);
			kafkaTemplate.send(topic, "DELETE", id);
		}
		else {
			log.warn("Not sending DELETE id={} request to kafka topic {} because hasKafkaBrokers={} and populated={}",
				id, topic, hasKafkaBrokers, populated);
		}
	}

	@KafkaListener(groupId = "manager", topics = "apps", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload AppMessage appMessage) {
		log.info("key={} message={}", key, appMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "cloud-hosts", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload CloudHostMessage cloudHostMessage) {
		log.info("key={} message={}", key, cloudHostMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "component-types", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload ComponentTypeMessage componentTypeMessage) {
		log.info("key={} message={}", key, componentTypeMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "conditions", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload ConditionMessage conditionMessage) {
		log.info("key={} message={}", key, conditionMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "containers", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload ContainerMessage containerMessage) {
		log.info("key={} message={}", key, containerMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "decisions", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload DecisionMessage decisionMessage) {
		log.info("key={} message={}", key, decisionMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "edge-hosts", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload EdgeHostMessage edgeHostMessage) {
		log.info("key={} message={}", key, edgeHostMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "eips", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload ElasticIpMessage elasticIpMessage) {
		log.info("key={} message={}", key, elasticIpMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "fields", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload FieldMessage fieldMessage) {
		log.info("key={} message={}", key, fieldMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "nodes", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload NodeMessage nodeMessage) {
		log.info("key={} message={}", key, nodeMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "operators", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload OperatorMessage operatorMessage) {
		log.info("key={} message={}", key, operatorMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "services", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload ServiceMessage serviceMessage) {
		log.info("key={} message={}", key, serviceMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "simulated-host-metrics", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload HostSimulatedMetricMessage hostSimulatedMetricMessage) {
		log.info("key={} message={}", key, hostSimulatedMetricMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "simulated-app-metrics", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload AppSimulatedMetricMessage appSimulatedMetricMessage) {
		log.info("key={} message={}", key, appSimulatedMetricMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "simulated-service-metrics", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload ServiceSimulatedMetricMessage serviceSimulatedMetricMessage) {
		log.info("key={} message={}", key, serviceSimulatedMetricMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "simulated-container-metrics", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload ContainerSimulatedMetricMessage containerSimulatedMetricMessage) {
		log.info("key={} message={}", key, containerSimulatedMetricMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "host-rules", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload HostRuleMessage hostRuleMessage) {
		log.info("key={} message={}", key, hostRuleMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "app-rules", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload AppRuleMessage appRuleMessage) {
		log.info("key={} message={}", key, appRuleMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "service-rules", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload ServiceRuleMessage serviceRuleMessage) {
		log.info("key={} message={}", key, serviceRuleMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "container-rules", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload ContainerRuleMessage containerRuleMessage) {
		log.info("key={} message={}", key, containerRuleMessage.toString());
	}

	@KafkaListener(groupId = "manager", topics = "value-modes", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload ValueModeMessage valueModeMessage) {
		log.info("key={} message={}", key, valueModeMessage.toString());
	}





	@KafkaListener(groupId = "manager-master", topics = "host-events", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload HostEventMessage hostEventMessage) {
		log.info("key={} value={}", key, hostEventMessage.toString());
	}

	@KafkaListener(groupId = "manager-master", topics = "service-events", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload ServiceEventMessage serviceEventMessage) {
		log.info("key={} value={}", key, serviceEventMessage.toString());
	}

	@KafkaListener(groupId = "manager-master", topics = "host-monitoring-logs", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload HostMonitoringLogMessage hostMonitoringLog) {
		log.info("key={} value={}", key, hostMonitoringLog.toString());
	}

	@KafkaListener(groupId = "manager-master", topics = "service-monitoring-logs", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload ServiceMonitoringLogMessage serviceMonitoringLogMessage) {
		log.info("key={} value={}", key, serviceMonitoringLogMessage.toString());
	}

	@KafkaListener(groupId = "manager-master", topics = "host-decisions", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload HostDecisionMessage hostDecisionMessage) {
		log.info("key={} value={}", key, hostDecisionMessage.toString());
	}

	@KafkaListener(groupId = "manager-master", topics = "service-decisions", autoStartup = "false")
	public void listen(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload ServiceDecisionMessage serviceDecisionMessage) {
		log.info("key={} value={}", key, serviceDecisionMessage.toString());
	}

	public void restart() {
		producerFactory.reset();
		kafkaListenerEndpointRegistry.stop();
		kafkaListenerEndpointRegistry.start();
	}

	public void stop() {
		producerFactory.reset();
		kafkaListenerEndpointRegistry.stop();
	}

}