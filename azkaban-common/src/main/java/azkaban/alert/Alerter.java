/*
 * Copyright 2014 LinkedIn Corp.
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

package azkaban.alert;

import azkaban.history.ExecutionRecover;
import com.webank.wedatasphere.schedulis.common.executor.ExecutionCycle;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

import azkaban.executor.ExecutableFlow;
import azkaban.executor.Executor;
import azkaban.executor.ExecutorManagerException;
import azkaban.sla.SlaOption;
import azkaban.utils.Props;

public interface Alerter {


  void alertOnSuccess(ExecutableFlow exflow) throws Exception;

  void alertOnError(ExecutableFlow exflow, String... extraReasons) throws Exception;

  void alertOnFirstError(ExecutableFlow exflow) throws Exception;


  void alertOnSla(SlaOption slaOption, String slaMessage) throws Exception;

  void alertOnFailedUpdate(Executor executor, List<ExecutableFlow> executions,
      ExecutorManagerException e);

  void alertOnSla(SlaOption slaOption, ExecutableFlow exflow) throws Exception;
  void alertOnIMSRegistStart(ExecutableFlow exflow, Map<String, Props> sharedProps,Logger logger) throws Exception;

  void alertOnIMSRegistFinish(ExecutableFlow exflow,Map<String, Props> sharedProps,Logger logger) throws Exception;

  void alertOnIMSRegistError(ExecutableFlow exflow,Map<String, Props> sharedProps,Logger logger) throws Exception;


  void alertOnFinishSla(SlaOption slaOption, ExecutableFlow exflow) throws Exception;


  /**
   * flow失败暂停发送通用告警
   * @param exflow
   * @param nodePath
   * @throws Exception
   */
  void alertOnFlowPaused(ExecutableFlow exflow, String nodePath) throws Exception;

  /**
   * flow 失败暂停发送sla告警
   * @param slaOption
   * @param exflow
   * @throws Exception
   */
  void alertOnFlowPausedSla(SlaOption slaOption, ExecutableFlow exflow, String nodePath) throws Exception;

  void alertOnCycleFlowInterrupt(ExecutableFlow flow, ExecutionCycle cycleFlow, List<String> emails, String alertLevel, String... extraReasons) throws Exception;

  void alertOnHistoryRecoverFinish(ExecutionRecover executionRecover) throws Exception;
}
