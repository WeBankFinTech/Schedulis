/*
 * Copyright 2020 WeBank
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webank.wedatasphere.schedulis.common.utils;

import azkaban.flow.Flow;
import azkaban.flow.Node;
import azkaban.project.Project;
import azkaban.scheduler.ScheduleManagerException;
import azkaban.sla.SlaOption;
import azkaban.utils.Utils;
import org.joda.time.Minutes;
import org.joda.time.ReadablePeriod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlertUtil {
    private static final Logger logger = LoggerFactory.getLogger(AlertUtil.class);

    //解析前端规则字符串 转换成SlaOption对象
    public static SlaOption parseSlaSetting(final String set, final Flow flow, final Project project) throws ScheduleManagerException {
        logger.info("Tryint to set sla with the following set: " + set);
        final String slaType;
        final List<String> slaActions = new ArrayList<>();
        final Map<String, Object> slaInfo = new HashMap<>();
        final String[] parts = set.split(",", -1);
        final String id = parts[0];
        final String rule = parts[1];
        final String duration = parts[2];
        final String level = parts[3];
        final String emailAction = parts[4];
        final String killAction = parts[5];

        List<Flow> embeddedFlows = project.getFlows();

        if (emailAction.equals("true") || killAction.equals("true")) {
            if (emailAction.equals("true")) {
                slaActions.add(SlaOption.ACTION_ALERT);
                slaInfo.put(SlaOption.ALERT_TYPE, "email");
            }
            if (killAction.equals("true")) {
                final String killActionType =
                        id.equals("") ? SlaOption.ACTION_CANCEL_FLOW : SlaOption.ACTION_KILL_JOB;
                slaActions.add(killActionType);
            }
            if (id.equals("")) {//FLOW告警模式设置
                if (rule.equals("SUCCESS")) {
                    slaType = SlaOption.TYPE_FLOW_SUCCEED;
                } else {
                    slaType = SlaOption.TYPE_FLOW_FINISH;
                }
            } else {//JOB告警模式设置
                Node node = flow.getNode(id);
                if(node != null && "flow".equals(node.getType())){//如果是flow类型的Job获取它真正执行的FlowId
                    slaInfo.put(SlaOption.INFO_JOB_NAME, id);
                    slaInfo.put(SlaOption.INFO_EMBEDDED_ID, node.getEmbeddedFlowId());
                }else{
                    slaInfo.put(SlaOption.INFO_JOB_NAME, id);
                }

                String str[] = id.split(":");
                for (Flow f: embeddedFlows) {
                    Node n = f.getNode(str[str.length -1]);
                    if(n != null && n.getType().equals("flow")) {
                        logger.info(id + " is embeddedFlow.");
                        slaInfo.put(SlaOption.INFO_EMBEDDED_ID, n.getEmbeddedFlowId());
                        break;
                    }
                }

                if (rule.equals("SUCCESS")) {
                    slaType = SlaOption.TYPE_JOB_SUCCEED;
                } else {
                    slaType = SlaOption.TYPE_JOB_FINISH;
                }
            }

            final ReadablePeriod dur;
            try {
                dur = parseDuration(duration);
            } catch (final Exception e) {
                throw new ScheduleManagerException("定时调度的超时时间输入格式不正确!");
            }

            slaInfo.put(SlaOption.INFO_DURATION, Utils.createPeriodString(dur));
            final SlaOption r = new SlaOption(slaType, slaActions, slaInfo, level);
            // 用于超时告警设置 回显的属性
            r.setTimeSet(duration);
            r.setEmailAction(emailAction);
            r.setKillAction(killAction);
            logger.info("Parsing sla as id:" + id + " type:" + slaType + " rule:"
                    + rule + " Duration:" + duration + " actions:" + slaActions);
            return r;
        }
        return null;
    }

    public static ReadablePeriod parseDuration(final String duration) {
        final int hour = Integer.parseInt(duration.split(":")[0]);
        final int min = Integer.parseInt(duration.split(":")[1]);
        return Minutes.minutes(min + hour * 60).toPeriod();
    }
}
