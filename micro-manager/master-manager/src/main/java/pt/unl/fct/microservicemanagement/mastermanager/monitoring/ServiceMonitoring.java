/*
 * MIT License
 *
 * Copyright (c) 2020 micro-manager
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

package pt.unl.fct.microservicemanagement.mastermanager.monitoring;

import java.sql.Timestamp;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NoArgsConstructor
@Getter
@Setter
@ToString
@EqualsAndHashCode
@Entity
@Table(name = "service_monitoring")
public class ServiceMonitoring {

  @Id
  @GeneratedValue
  private long id;

  @Column(name = "container_id")
  private String containerId;

  @Column(name = "service_name")
  private String serviceName;

  @Column(name = "field")
  private String field;

  @Column(name = "min_value")
  private double minValue;

  @Column(name = "max_value")
  private double maxValue;

  @Column(name = "sum_value")
  private double sumValue;

  @Column(name = "last_value")
  private double lastValue;

  @Column(name = "count")
  private long count;

  @Basic
  @Column(name = "last_update")
  private Timestamp lastUpdate;

  ServiceMonitoring(String containerId, String serviceName, String field, double minValue, double maxValue,
                           double sumValue, double lastValue, long count, Timestamp lastUpdate) {
    this.containerId = containerId;
    this.serviceName = serviceName;
    this.field = field;
    this.minValue = minValue;
    this.maxValue = maxValue;
    this.sumValue = sumValue;
    this.lastValue = lastValue;
    this.count = count;
    this.lastUpdate = lastUpdate;
  }

  void logValue(double value, Timestamp updateTime) {
    setMinValue(Math.min(value, getMinValue()));
    setMaxValue(Math.max(value, getMaxValue()));
    setLastValue(value);
    setSumValue(getSumValue() + value);
    setCount(getCount() + 1);
    setLastUpdate(updateTime);
  }

}