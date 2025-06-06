/*
 * Copyright 2012 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.flow;

import azkaban.utils.Utils;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Node {

  private final String id;
  private String jobSource;
  private String propsSource;

  private Point2D position = null;
  private int level;
  private int expectedRunTimeSec = 1;
  private String type;
  private String outer;

  private String embeddedFlowId;

  private String condition = null;

  private ConditionOnJobStatus conditionOnJobStatus = ConditionOnJobStatus.ALL_SUCCESS;

  private boolean isElasticNode = false;

  private List<Integer> label;

  private String comment;

  /**
   * 节点默认自动关闭
   */
  private boolean autoDisabled;

  public Node(final String id) {
    this.id = id;
  }

  /**
   * Clones nodes
   */
  public Node(final Node clone) {
    this.id = clone.id;
    this.propsSource = clone.propsSource;
    this.jobSource = clone.jobSource;
  }

  public boolean isAutoDisabled() {
    return autoDisabled;
  }

  public void setAutoDisabled(boolean autoDisabled) {
    this.autoDisabled = autoDisabled;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public List<Integer> getLabel() {
    return label;
  }

  public void setLabel(List<Integer> label) {
    this.label = label;
  }

  public boolean isElasticNode() {
    return isElasticNode;
  }

  public void setElasticNode(boolean elasticNode) {
    isElasticNode = elasticNode;
  }

  public static Node fromObject(final Object obj) {
    final Map<String, Object> mapObj = (Map<String, Object>) obj;
    final String id = (String) mapObj.get("id");

    final Node node = new Node(id);
    final String jobSource = (String) mapObj.get("jobSource");
    final String propSource = (String) mapObj.get("propSource");
    final String jobType = (String) mapObj.get("jobType");
    final String outer = (String) mapObj.get("outer");

    final String embeddedFlowId = (String) mapObj.get("embeddedFlowId");
    final String condition = (String) mapObj.get("condition");
    final ConditionOnJobStatus conditionOnJobStatus = ConditionOnJobStatus
        .fromString((String) mapObj.get("conditionOnJobStatus"));
    final List<Integer> label = (List<Integer>) mapObj.get("label");
    final String comment = (String) mapObj.get("comment");
    final boolean autoDisabled = (boolean) mapObj.getOrDefault("autoDisabled", false);

    node.setAutoDisabled(autoDisabled);
    node.setComment(comment);
    node.setLabel(label);
    node.setJobSource(jobSource);
    node.setPropsSource(propSource);
    node.setType(jobType);
    node.setOuter(outer);
    node.setEmbeddedFlowId(embeddedFlowId);
    node.setCondition(condition);
    node.setConditionOnJobStatus(conditionOnJobStatus);
    node.setElasticNode((boolean)mapObj.getOrDefault("isElasticNode", false));
    final Integer expectedRuntime = (Integer) mapObj.get("expectedRuntime");
    if (expectedRuntime != null) {
      node.setExpectedRuntimeSec(expectedRuntime);
    }

    final Map<String, Object> layoutInfo = (Map<String, Object>) mapObj.get("layout");
    if (layoutInfo != null) {
      Double x = null;
      Double y = null;
      Integer level = null;

      try {
        x = Utils.convertToDouble(layoutInfo.get("x"));
        y = Utils.convertToDouble(layoutInfo.get("y"));
        level = (Integer) layoutInfo.get("level");
      } catch (final ClassCastException e) {
        throw new RuntimeException("Error creating node " + id, e);
      }

      if (x != null && y != null) {
        node.setPosition(new Point2D.Double(x, y));
      }
      if (level != null) {
        node.setLevel(level);
      }
    }

    return node;
  }

  public String getId() {
    return this.id;
  }

  public String getType() {
    return this.type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public String getOuter() {
    return this.outer;
  }

  public void setOuter(String outer) {
    this.outer = outer;
  }

  public Point2D getPosition() {
    return this.position;
  }

  public void setPosition(final Point2D position) {
    this.position = position;
  }

  public void setPosition(final double x, final double y) {
    this.position = new Point2D.Double(x, y);
  }

  public int getLevel() {
    return this.level;
  }

  public void setLevel(final int level) {
    this.level = level;
  }

  public String getJobSource() {
    return this.jobSource;
  }

  public void setJobSource(final String jobSource) {
    this.jobSource = jobSource;
  }

  public String getPropsSource() {
    return this.propsSource;
  }

  public void setPropsSource(final String propsSource) {
    this.propsSource = propsSource;
  }

  public int getExpectedRuntimeSec() {
    return this.expectedRunTimeSec;
  }

  public void setExpectedRuntimeSec(final int runtimeSec) {
    this.expectedRunTimeSec = runtimeSec;
  }

  public String getEmbeddedFlowId() {
    return this.embeddedFlowId;
  }

  public void setEmbeddedFlowId(final String flowId) {
    this.embeddedFlowId = flowId;
  }

  public Object toObject() {
    final HashMap<String, Object> objMap = new HashMap<>();
    objMap.put("id", this.id);
    objMap.put("jobSource", this.jobSource);
    objMap.put("propSource", this.propsSource);
    objMap.put("jobType", this.type);
    objMap.put("outer", this.outer);
    if (this.embeddedFlowId != null) {
      objMap.put("embeddedFlowId", this.embeddedFlowId);
    }
    objMap.put("expectedRuntime", this.expectedRunTimeSec);

    final HashMap<String, Object> layoutInfo = new HashMap<>();
    if (this.position != null) {
      layoutInfo.put("x", this.position.getX());
      layoutInfo.put("y", this.position.getY());
    }
    layoutInfo.put("level", this.level);
    objMap.put("layout", layoutInfo);
    objMap.put("condition", this.condition);
    objMap.put("conditionOnJobStatus", this.conditionOnJobStatus);
    objMap.put("isElasticNode", this.isElasticNode);
    objMap.put("label", this.label);
    objMap.put("comment", this.comment);
    objMap.put("autoDisabled", this.autoDisabled);
    return objMap;
  }

  public String getCondition() {
    return this.condition;
  }

  public void setCondition(final String condition) {
    this.condition = condition;
  }

  public ConditionOnJobStatus getConditionOnJobStatus() {
    return this.conditionOnJobStatus;
  }

  public void setConditionOnJobStatus(final ConditionOnJobStatus conditionOnJobStatus) {
    this.conditionOnJobStatus = conditionOnJobStatus;
  }
}
