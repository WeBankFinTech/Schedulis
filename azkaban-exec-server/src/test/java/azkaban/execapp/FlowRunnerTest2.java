/*
 * Copyright 2017 LinkedIn Corp.
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

package azkaban.execapp;

import static org.junit.Assert.assertEquals;

import azkaban.executor.ExecutionOptions.FailureAction;
import azkaban.executor.Status;

import java.lang.reflect.Field;
import org.junit.Before;
import org.junit.Test;


public class FlowRunnerTest2 extends FlowRunnerTestBase {

  private FlowRunnerTestUtil testUtil;

  @Before
  public void setUp() throws Exception {
    this.testUtil = new FlowRunnerTestUtil("testProject", this.temporaryFolder);
  }



  /**
   * Tests that pause and resume work
   */
  @Test
  public void testPause() throws Exception {
    this.runner = this.testUtil.createFromFlowMap("end", FailureAction.FINISH_CURRENTLY_RUNNING);
    Class clazz = this.runner.getClass();
    Field field = clazz.getDeclaredField("flowPaused");
    field.setAccessible(true);
    // 1. START FLOW
    FlowRunnerTestUtil.startThread(this.runner);
    assertEquals(false, field.getBoolean(this.runner));
    // After it starts up, only joba should be running
    waitForAndAssertFlowStatus(Status.RUNNING);

    this.runner.pause("test");
    assertEquals(true, field.getBoolean(this.runner));
    waitForAndAssertFlowStatus(Status.PAUSED);

    // 2.2 Flow is unpaused
    this.runner.resume("test");
    assertEquals(false, field.getBoolean(this.runner));
    waitForAndAssertFlowStatus(Status.RUNNING);
    this.runner.superKill("test");
  }



}
