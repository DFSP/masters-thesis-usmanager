/*
 * MIT License
 *
 * Copyright (c) 2020 usmanager
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

package pt.unl.fct.microservicemanagement.mastermanager.manager.hosts.cloud.aws;

import pt.unl.fct.microservicemanagement.mastermanager.exceptions.MasterManagerException;
import pt.unl.fct.microservicemanagement.mastermanager.util.Timing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DryRunResult;
import com.amazonaws.services.ec2.model.DryRunSupportedRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AwsService {

  //TODO put constants into AwsProperties
  /*private static final String DEFAULT_INSTANCE_AMI = "ami-0e02218c08bb8ffd5";
  private static final String DEFAULT_INSTANCE_SECURITY_GROUP = "us-manager-security-group";
  private static final String DEFAULT_INSTANCE_KEY_PAIR = "ec2";
  private static final int INSTANCE_OPERATION_MAX_RETRIES = 10;
  private static final long DELAY_BETWEEN_INSTANCE_OPERATION_TRIES = TimeUnit.SECONDS.toMillis(5);
  private static final long SET_INSTANCE_STATE_TIMEOUT = TimeUnit.SECONDS.toMillis(180);

  private static final String MICROSERVICES_MANAGER_TAG = "us-manager";*/

  private final AmazonEC2 ec2;
  private final String awsInstanceAmi;
  private final String awsInstanceSecurityGroup;
  private final String awsInstanceKeyPair;
  private final String awsInstanceType;
  private final String awsInstanceTag;
  private final int awsMaxRetries;
  private final int awsDelayBetweenRetries;
  private final int awsConnectionTimeout;

  public AwsService(AwsProperties awsProperties) {
    String awsAccessKey = awsProperties.getAccess().getKey();
    String awsSecretAccessKey = awsProperties.getAccess().getSecretKey();
    var awsCredentials = new BasicAWSCredentials(awsAccessKey, awsSecretAccessKey);
    var awsCredentialsProvider = new AWSStaticCredentialsProvider(awsCredentials);
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

  private List<Instance> getInstances() {
    var instances = new ArrayList<Instance>();
    var request = new DescribeInstancesRequest();
    DescribeInstancesResult result;
    do {
      result = ec2.describeInstances(request);
      result.getReservations().stream().map(Reservation::getInstances).flatMap(List::stream)
          .filter(this::isMicroserviceManagerInstance).forEach(instances::add);
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
          .flatMap(List::stream).filter(this::isMicroserviceManagerInstance).findFirst();
      if (instance.isPresent()) {
        return instance.get();
      }
      request.setNextToken(result.getNextToken());
    } while (result.getNextToken() != null);
    throw new MasterManagerException("Instance with id %s not found", id);
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
    return instance;
  }

  private String createEC2() {
    var runInstancesRequest = new RunInstancesRequest()
        .withImageId(awsInstanceAmi)
        .withInstanceType(awsInstanceType)
        .withMinCount(1)
        .withMaxCount(1)
        .withSecurityGroups(awsInstanceSecurityGroup)
        .withKeyName(awsInstanceKeyPair);
    RunInstancesResult result = ec2.runInstances(runInstancesRequest);
    Instance instance = result.getReservation().getInstances().get(0);
    String instanceId = instance.getInstanceId();
    var instanceName = String.format("ubuntu-%d", System.currentTimeMillis());
    var createTagsRequest = new CreateTagsRequest().withResources(instanceId)
        .withTags(new Tag("Name", instanceName), new Tag(awsInstanceTag, "true"));
    ec2.createTags(createTagsRequest);
    return instanceId;
  }

  public Instance startInstance(String instanceId) {
    log.info("Starting instance {}", instanceId);
    return setInstanceState(instanceId, AwsInstanceState.RUNNING);
  }

  private void startInstanceById(String instanceId) {
    DryRunSupportedRequest<StartInstancesRequest> dryRequest = () ->
        new StartInstancesRequest().withInstanceIds(instanceId).getDryRunRequest();
    DryRunResult<StartInstancesRequest> dryResponse = ec2.dryRun(dryRequest);
    if (!dryResponse.isSuccessful()) {
      throw new MasterManagerException(dryResponse.getDryRunResponse().getErrorMessage());
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
      throw new MasterManagerException(dryResponse.getDryRunResponse().getErrorMessage());
    }
    var request = new StopInstancesRequest().withInstanceIds(instanceId);
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
      throw new MasterManagerException(dryResponse.getDryRunResponse().getErrorMessage());
    }
    var request = new TerminateInstancesRequest().withInstanceIds(instanceId);
    ec2.terminateInstances(request);
  }

  private Instance setInstanceState(String instanceId, AwsInstanceState state) {
    for (var tries = 0; tries < awsMaxRetries; tries++) {
      Instance instance = getInstance(instanceId);
      int instanceState = instance.getState().getCode();
      if (instanceState == state.getCode()) {
        log.info("Instance {} is already on state {}", instanceId, state.getName());
        return instance;
      }
      try {
        switch (state) {
          case RUNNING:
            startInstanceById(instanceId);
            break;
          case STOPPED:
            stopInstanceById(instanceId);
            break;
          case TERMINATED:
            terminateInstanceById(instanceId);
            break;
          default:
            throw new UnsupportedOperationException();
        }
        instance = waitInstanceState(instanceId, state);
        log.info("Setting instance {} to {} state", instanceId, state.getName());
        return instance;
      } catch (MasterManagerException e) {
        log.info("Failed to set instance {} to {} state: {}", instanceId, state.getName(), e.getMessage());
      }
      Timing.sleep(awsDelayBetweenRetries, TimeUnit.MILLISECONDS);
    }
    throw new MasterManagerException("Unable to set instance state %d within %d tries",
        state.getName(), awsMaxRetries);
  }

  private Instance waitInstanceState(String instanceId, AwsInstanceState state) {
    Instance[] instance = new Instance[1];
    try {
      Timing.wait(() -> {
        instance[0] = getInstance(instanceId);
        return instance[0].getState().getCode() == state.getCode();
      }, awsConnectionTimeout);
    } catch (TimeoutException e) {
      log.info("Unknown status of instance {} {} operation: Timed out", instanceId, state.getName());
      throw new MasterManagerException(e.getMessage());
    }
    return instance[0];
  }

  private boolean isMicroserviceManagerInstance(Instance instance) {
    return instance.getTags().stream().anyMatch(tag ->
        Objects.equals(tag.getKey(), awsInstanceTag) && Objects.equals(tag.getValue(), "true"));
  }

}