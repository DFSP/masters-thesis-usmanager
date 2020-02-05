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

package pt.unl.fct.microservicemanagement.mastermanager.rulesystem.rule;

import pt.unl.fct.microservicemanagement.mastermanager.rulesystem.ComponentTypeEntity;
import pt.unl.fct.microservicemanagement.mastermanager.rulesystem.decision.ComponentDecisionLog;
import pt.unl.fct.microservicemanagement.mastermanager.rulesystem.decision.DecisionEntity;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@Setter(value = AccessLevel.PACKAGE)
@Getter
@Table(name = "rules")
public class RuleEntity {

  @Id
  @GeneratedValue
  private Long id;

  @Column(name = "rule_name")
  private String ruleName;

  @ManyToOne
  @JoinColumn(name = "component_type_id")
  private ComponentTypeEntity componentType;

  @Column(name = "priority")
  private int priority;

  @ManyToOne
  @JoinColumn(name = "decision_id")
  private DecisionEntity decision;

  @JsonIgnore
  @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private Set<RuleConditionEntity> ruleConditions = new HashSet<>();

  @JsonIgnore
  @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private Set<AppRule> appRules = new HashSet<>();

  @JsonIgnore
  @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private Set<ServiceRule> serviceRules = new HashSet<>();

  @JsonIgnore
  @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private Set<HostRule> hostRules = new HashSet<>();

  @JsonIgnore
  @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private Set<GenericHostRuleEntity> genericHostRuleEntities = new HashSet<>();

  @JsonIgnore
  @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private Set<ComponentDecisionLog> componentDecisionLogs = new HashSet<>();

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RuleEntity)) {
      return false;
    }
    RuleEntity other = (RuleEntity) o;
    return id != null && id.equals(other.getId());
  }

  @Override
  public int hashCode() {
    return 31;
  }

}
