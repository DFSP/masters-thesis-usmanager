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

package pt.unl.fct.miei.usmanagement.manager.worker.management.location;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import pt.unl.fct.miei.usmanagement.manager.database.containers.ContainerEntity;
import pt.unl.fct.miei.usmanagement.manager.database.hosts.HostLocation;
import pt.unl.fct.miei.usmanagement.manager.database.regions.RegionEntity;
import pt.unl.fct.miei.usmanagement.manager.worker.management.containers.ContainersService;
import pt.unl.fct.miei.usmanagement.manager.worker.management.docker.swarm.nodes.NodesService;
import pt.unl.fct.miei.usmanagement.manager.worker.management.docker.swarm.nodes.SimpleNode;
import pt.unl.fct.miei.usmanagement.manager.worker.management.hosts.HostDetails;
import pt.unl.fct.miei.usmanagement.manager.worker.management.hosts.HostsService;
import pt.unl.fct.miei.usmanagement.manager.worker.management.rulesystem.decision.DecisionResult;
import pt.unl.fct.miei.usmanagement.manager.worker.management.rulesystem.decision.ServiceDecisionResult;

@Slf4j
@Service
public class LocationRequestService {

	// TODO refactor
	public static final String REQUEST_LOCATION_MONITOR = "request-location-monitor";
	private static final double PERCENT = 0.01;

	private final NodesService nodesService;
	private final ContainersService containersService;
	private final RegionsService regionsService;
	private final HostsService hostsService;

	private final int defaultPort;
	private final double minimumRequestCountPercentage;
	private final RestTemplate restTemplate;
	private final HttpHeaders headers;

	public LocationRequestService(ContainersService containersService, NodesService nodesService,
								  RegionsService regionsService, HostsService hostsService,
								  LocationRequestProperties locationRequestProperties) {
		this.nodesService = nodesService;
		this.containersService = containersService;
		this.regionsService = regionsService;
		this.hostsService = hostsService;
		this.defaultPort = locationRequestProperties.getPort();
		this.minimumRequestCountPercentage = locationRequestProperties.getMinimumRequestCountPercentage();
		this.restTemplate = new RestTemplate();
		this.headers = new HttpHeaders();
	}

	public ContainerEntity launchRequestLocationMonitor(String hostname) {
		return containersService.launchContainer(hostname, REQUEST_LOCATION_MONITOR, true);
	}

	public List<LocationMonitoringResponse> getAllMonitoringDataTop(String requestLocationHostname, int seconds) {
		String url =
			String.format("http://%s:%s/api/monitoringinfo/all/top/%d", requestLocationHostname, defaultPort, seconds);
		List<LocationMonitoringResponse> locationMonitoring = new ArrayList<>();
		try {
			ResponseEntity<LocationMonitoringResponse[]> response =
				restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), LocationMonitoringResponse[].class);
			locationMonitoring = Arrays.asList(response.getBody());
		}
		catch (RestClientException e) {
			e.printStackTrace();
		}
		return locationMonitoring;
	}

	public Map<String, HostDetails> getBestLocationToStartServices(
		Map<String, List<ServiceDecisionResult>> servicesDecisions, int secondsFromLastRun) {
		LocationMonitoring locationMonitoring = getLocationMonitoring(secondsFromLastRun);
		Map<String, HostDetails> finalLocations = new HashMap<>();
		for (Entry<String, List<ServiceDecisionResult>> services : servicesDecisions.entrySet()) {
			String serviceName = services.getKey();
			List<ServiceDecisionResult> decisions = services.getValue();
			if (locationMonitoring.containsTotalRequestsCount(serviceName)) {
				if (!decisions.isEmpty()) {
					Map<String, Integer> locationsCount = locationMonitoring.getServiceLocationsCount(serviceName);
					int totalRequestsCount = locationMonitoring.getServiceTotalRequestsCount(serviceName);
					List<String> containersHostnames =
						decisions.stream().map(DecisionResult::getHostname).collect(Collectors.toList());
					Map<String, Integer> containersLocations = getContainersLocations(containersHostnames);
					HostDetails location = getBestLocationByService(serviceName, locationsCount, totalRequestsCount,
						containersLocations);
					if (location != null) {
						finalLocations.put(serviceName, location);
					}
				}
			}
		}
		return finalLocations;
	}

	private LocationMonitoring getLocationMonitoring(int seconds) {
		List<LocationMonitoringResponse> locationMonitoringData = nodesService.getReadyNodes().stream()
			.map(SimpleNode::getHostname)
			.map(hostname -> getAllMonitoringDataTop(hostname, seconds))
			.flatMap(List::stream)
			.collect(Collectors.toList());
		LocationMonitoring locationMonitoring = new LocationMonitoring();
		locationMonitoringData.forEach(locationMonitoringResponse -> {
			String serviceName = locationMonitoringResponse.getToService();
			String fromContinent = locationMonitoringResponse.getLocationData().getFromContinent();
			String fromRegion = locationMonitoringResponse.getLocationData().getFromRegion();
			String fromCountry = locationMonitoringResponse.getLocationData().getFromCountry();
			String fromCity = locationMonitoringResponse.getLocationData().getFromCity();
			List<String> locationKeys = getLocationsKeys(fromCity, fromCountry, fromRegion, fromContinent);
			int count = locationMonitoringResponse.getLocationData().getCount();
			locationMonitoring.addCount(serviceName, count, locationKeys);
		});
		return locationMonitoring;
	}

	private Map<String, Integer> getContainersLocations(List<String> hostnames) {
		Map<String, Integer> availableLocations = new HashMap<>();
		Map<String, HostDetails> hostnamesFound = new HashMap<>();
		for (String hostname : hostnames) {
			if (!hostnamesFound.containsKey(hostname)) {
				HostDetails hostDetails = hostsService.getHostDetails(hostname);
				hostnamesFound.put(hostname, hostDetails);
			}
			HostLocation hostLocation = hostnamesFound.get(hostname).getHostLocation();
			List<String> locationKeys = getLocationsKeys(hostLocation.getCity(), hostLocation.getCountry(),
				hostLocation.getRegion(), hostLocation.getContinent());
			for (String locationKey : locationKeys) {
				availableLocations.computeIfPresent(locationKey, (key, count) -> count + 1);
				availableLocations.putIfAbsent(locationKey, 1);
			}
		}
		return availableLocations;
	}

	private HostDetails getBestLocationByService(String serviceName, Map<String, Integer> locationMonitoring,
												 int totalCount, Map<String, Integer> locationsByRunningContainers) {
		List<LocationCount> locationsWithMinReqPerc = new ArrayList<>();
		for (Entry<String, Integer> locationReqCount : locationMonitoring.entrySet()) {
			double currentPercentage = ((locationReqCount.getValue() * 1.0) / (totalCount * 1.0)) / PERCENT;
			if (currentPercentage >= minimumRequestCountPercentage) {
				HostLocation hostLocation = getHostDetailsByLocationKey(locationReqCount.getKey()).getHostLocation();
				String region = hostLocation.getRegion();
				String country = hostLocation.getCountry();
				String city = hostLocation.getCity();
				int runningContainerOnRegion = locationsByRunningContainers.getOrDefault(region, 0);
				int runningContainerOnCountry = locationsByRunningContainers.getOrDefault(region + "_" + country, 0);
				int runingContainersOnLocal = locationsByRunningContainers.getOrDefault(locationReqCount.getKey(), 0);
				LocationCount locCount = new LocationCount(locationReqCount.getKey(), city, country, region, currentPercentage,
					runingContainersOnLocal, runningContainerOnCountry, runningContainerOnRegion);
				locationsWithMinReqPerc.add(locCount);
			}
		}
		Collections.sort(locationsWithMinReqPerc);
		if (!locationsWithMinReqPerc.isEmpty()) {
			log.info("Best location for {} : {}", serviceName, locationsWithMinReqPerc.get(0).toString());
			return getHostDetailsByLocationKey(locationsWithMinReqPerc.get(0).getLocationKey());
		}
		return null;
	}

	private HostDetails getHostDetailsByLocationKey(String locationKey) {
		String[] serviceLocationSplit = locationKey.split("_");
		String region = "";
		String country = "";
		String city = "";
		for (int i = 0; i < serviceLocationSplit.length; i++) {
			if (i == 0) {
				region = serviceLocationSplit[i];
			}
			else if (i == 1) {
				country = serviceLocationSplit[i];
			}
			else if (i == 2) {
				city = serviceLocationSplit[i];
			}
		}
		return new HostDetails(new HostLocation(city, country, region, ""));
	}

  /*public String getRegionByLocationKey(String locationKey) {
    return getHostDetailsByLocationKey(locationKey).getRegion();
  }*/

	private List<String> getLocationsKeys(String city, String country, String region, String continent) {
		String finalRegion = region;
		List<String> locationKeys = new ArrayList<>();
		if ("none".equals(region)) {
			finalRegion = getBestRegionByLocationInfo(city, country, continent);
		}
		if (!country.isEmpty()) {
			if (!city.isEmpty()) {
				locationKeys.add(finalRegion + "_" + country + "_" + city);
			}
			locationKeys.add(finalRegion + "_" + country);
		}
		locationKeys.add(finalRegion);
		return locationKeys;
	}

	private String getLocationKey(String region, String country, String city) {
		return region + (!country.isEmpty() ? "_" + country : "") + (!city.isEmpty() ? "_" + city : "");
	}

	// TODO: improve region choice, using country and city
	private String getBestRegionByLocationInfo(String city, String country, String continent) {
		switch (country) {
			case "pt":
				return "eu-central-1";
			case "gb":
				return "eu-west-2";
			case "us":
				return "us-east-1";
			default:
		}
		List<RegionEntity> regions = regionsService.getRegions();
		List<String> foundRegion = new ArrayList<>();
		String regionNameBegin = "";
		switch (continent) {
			case "na":
				regionNameBegin = "us-";
				break;
			case "sa":
				regionNameBegin = "sa-";
				break;
			case "eu":
			case "af":
				regionNameBegin = "eu-";
				break;
			case "as":
			case "oc":
				regionNameBegin = "ap-";
				break;
			default:
		}
		for (RegionEntity region : regions) {
			String regionName = region.getName();
			if (regionName.contains(regionNameBegin)) {
				foundRegion.add(regionName);
			}
		}
		if (!foundRegion.isEmpty()) {
			Random rand = new Random();
			int index = rand.nextInt(foundRegion.size());
			return foundRegion.get(index);
		}
		return "";
	}
}
