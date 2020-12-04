package pt.unl.fct.miei.usmanagement.manager.management.communication.kafka;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.ToString;
import org.hibernate.annotations.NaturalId;
import pt.unl.fct.miei.usmanagement.manager.hosts.Coordinates;
import pt.unl.fct.miei.usmanagement.manager.hosts.edge.EdgeHost;
import pt.unl.fct.miei.usmanagement.manager.metrics.simulated.HostSimulatedMetric;
import pt.unl.fct.miei.usmanagement.manager.regions.RegionEnum;
import pt.unl.fct.miei.usmanagement.manager.rulesystem.rules.HostRule;
import pt.unl.fct.miei.usmanagement.manager.workermanagers.WorkerManager;

import javax.persistence.CascadeType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@ToString
@Getter
public class EdgeHostMessage {

	private Long id;
	private String username;
	private String publicIpAddress;
	private String privateIpAddress;
	private String publicDnsName;
	private RegionEnum region;
	private Coordinates coordinates;
	private WorkerManager managedByWorker;
	private Set<HostRule> hostRules;
	private Set<HostSimulatedMetric> simulatedHostMetrics;

	public EdgeHostMessage(EdgeHost edgeHost) {
		this.id = edgeHost.getId();
		this.username = edgeHost.getUsername();
		this.publicIpAddress = edgeHost.getPublicIpAddress();
		this.privateIpAddress = edgeHost.getPrivateIpAddress();
		this.publicDnsName = edgeHost.getPublicDnsName();
		this.region = edgeHost.getRegion();
		this.coordinates = edgeHost.getCoordinates();
		this.managedByWorker = edgeHost.getManagedByWorker();
		this.hostRules = edgeHost.getHostRules();
		this.simulatedHostMetrics = edgeHost.getSimulatedHostMetrics();
	}

}
