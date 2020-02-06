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

package pt.unl.fct.microservicemanagement.mastermanager.manager.services;

import pt.unl.fct.microservicemanagement.mastermanager.manager.apps.AppPackage;
import pt.unl.fct.microservicemanagement.mastermanager.manager.prediction.ServiceEventPrediction;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceRepository extends CrudRepository<ServiceEntity, Long> {

  Optional<ServiceEntity> findByServiceNameIgnoreCase(@Param("serviceName") String serviceName);

  List<ServiceEntity> findByDockerRepository(@Param("dockerRepository") String dockerRepository);

  @Query("select d.serviceDependency "
      + "from ServiceEntity s inner join s.dependencies d "
      + "where s.id = :serviceId")
  List<ServiceEntity> getServiceDependencies(@Param("serviceId") long serviceId);

  @Modifying
  @Query("select d.serviceDependency "
          + "from ServiceEntity s inner join s.dependencies d "
          + "where s.id = :serviceId and d.id = :dependencyId")
  Optional<ServiceEntity> getDependency(@Param("serviceId") long serviceId, @Param("dependencyId") long dependencyId);

  /*@Query("delete from Service s inner join s.dependencies d "
          + "where s.id = :serviceId and d.id = :dependencyId")
  void removeDependency(@Param("serviceId") long serviceId, @Param("dependencyId") long dependencyId);*/

  @Query("select count (*) "
      + "from ServiceEntity s inner join s.dependencies d "
      + "where s.id = :serviceId and d.serviceDependency.serviceName = :otherServiceName")
  int serviceDependsOnOtherService(@Param("serviceId") long serviceId,
                                   @Param("otherServiceName") String otherServiceName);

  @Query("select d.serviceDependency "
      + "from ServiceEntity s inner join s.dependencies d "
      + "where s.id = :serviceId and d.serviceDependency.serviceType = :serviceType")
  List<ServiceEntity> getDependenciesByType(@Param("serviceId") long serviceId,
                                            @Param("serviceType") String serviceType);

  //TODO single ou lista?
  @Query("select apps.appPackage "
      + "from ServiceEntity s inner join s.appServices apps "
      + "where s.serviceName = :serviceName")
  AppPackage getAppsByServiceName(@Param("serviceName") String serviceName);

  @Query("select s.maxReplics "
      + "from ServiceEntity s "
      + "where s.serviceName = :serviceName")
  int getMaxReplicsByServiceName(@Param("serviceName") String serviceName);

  @Query("select s.minReplics "
      + "from ServiceEntity s "
      + "where s.serviceName = :serviceName")
  int getMinReplicsByServiceName(@Param("serviceName") String serviceName);

  @Query("select p "
      + "from ServiceEntity s join s.eventPredictions p "
      + "where s.id = :serviceId and p.id = :serviceEventPredictions")
  Optional<ServiceEventPrediction> getServiceEventPrediction(@Param("serviceId") long serviceId,
                                                             @Param("serviceEventPredictions")
                                                                 long serviceEventPredictions);
}