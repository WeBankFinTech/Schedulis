package azkaban.history;

import azkaban.alert.Alerter;
import azkaban.executor.*;
import azkaban.project.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static azkaban.ServiceProvider.SERVICE_PROVIDER;

public class RecoverTrigger{

    private static Logger logger = LoggerFactory.getLogger(RecoverTrigger.class);

    private boolean hasTaskFailed = false;
    private List<GroupTask> group;
    private List<Map<String, String>> repeatList;
    private int taskSize;
    private boolean errorContinue;
    private int triggerId;
    private int projectId;
    private String flowId;
    private ExecutionRecover executionRecover;
    private final ExecutorLoader loader = SERVICE_PROVIDER.getInstance(JdbcExecutorLoader.class);
    private final AlerterHolder alerterHolder = SERVICE_PROVIDER.getInstance(AlerterHolder.class);
    private Project project;

    public RecoverTrigger(ExecutionRecover executionRecover){
        this.repeatList = (List<Map<String, String>>) executionRecover.getRepeatOption().get("repeatTimeList");
        this.taskSize = executionRecover.getTaskSize();
        this.group = new ArrayList<>(taskSize);
        this.errorContinue = "errorCountion".equals(executionRecover.getRecoverErrorOption());
        this.triggerId = executionRecover.getRecoverId();
        this.projectId = executionRecover.getProjectId();
        this.flowId = executionRecover.getFlowId();
        this.executionRecover = executionRecover;
        createGroupTask();
    }

    public int getTriggerId() {
        return triggerId;
    }

    private void createGroupTask(){
        int repeatSize = this.repeatList.size();
        if(repeatSize < taskSize){
            this.group.add(new GroupTask(repeatList, this.errorContinue));
        } else {
            int len = repeatList.size() / taskSize;
            int remainder = repeatList.size() % taskSize;
            int lastIndex = 0;
            for (int i = 0; i < this.taskSize; i++) {
                if(i < this.taskSize - 1) {
                    this.group.add(new GroupTask(repeatList.subList(lastIndex, lastIndex + len), this.errorContinue));
                    lastIndex += len;
                } else {
                    this.group.add(new GroupTask(repeatList.subList(lastIndex, lastIndex + len + remainder), this.errorContinue));
                }

            }
        }
    }

    public List<GroupTask> getGroup() {
        return group;
    }

    public void setGroup(List<GroupTask> group) {
        this.group = group;
    }


    public boolean expireConditionMet(){
        if(this.hasTaskFailed && !errorContinue){
            logger.info("errorStop, stop history recover.");
            executionRecover.setEndTime(System.currentTimeMillis());
            this.executionRecover.setRecoverStatus(Status.FAILED);
            alert();
            return true;
        }

        List<Map<String, String>> finishedTask = repeatList.stream().filter(x -> {
            Status status = Status.fromInteger(Integer.valueOf(x.get("recoverStatus")));
            return Status.isStatusFinished(status);
        }).collect(Collectors.toList());

        if(finishedTask != null && finishedTask.size() == repeatList.size()){
            logger.info("stop history recover.");
            executionRecover.setEndTime(System.currentTimeMillis());
            updateRecoverStatus();
            alert();
            return true;
        }

        return false;
    }

    private void alert(){
        if(executionRecover.isFinishedAlert()){
            logger.info("history recover alert.");
            Alerter mailAlerter = alerterHolder.get("email");
            if(null == mailAlerter){
                mailAlerter = alerterHolder.get("default");
            }
            try {
                executionRecover.setProjectName(this.project.getName());
                mailAlerter.alertOnHistoryRecoverFinish(executionRecover);
            }catch (Exception e){
                logger.error("history recover alert failed", e);
            }
        }
    }

    private void updateRecoverStatus(){
        List<Map<String, String>> failedTask = repeatList.stream().filter(x -> !"50"
            .equals(x.get("recoverStatus"))).collect(Collectors.toList());
        if(failedTask.size() == 0){
            logger.info("set history recover status to SUCCEEDED");
            executionRecover.setRecoverStatus(Status.SUCCEEDED);
        }else if(failedTask.size() != repeatList.size()){
            logger.info("set history recover status to FAILED_SUCCEEDED");
            executionRecover.setRecoverStatus(Status.FAILED_SUCCEEDED);
        } else {
            logger.info("set history recover status to FAILED");
            executionRecover.setRecoverStatus(Status.FAILED);
        }
    }

    public void updateTaskStatus(){
        List<Map<String, String>> tasks = repeatList.stream().filter(item -> {
            return (item.containsKey("isSubmit") && "20".equals(item.get("recoverStatus"))) || "30"
                .equals(item.get("recoverStatus")) ;
        }).collect(Collectors.toList());
        for(Map<String, String> task: tasks){
            try {
                int excId = Integer.valueOf(task.get("exeId"));
                ExecutableFlow executableFlow = loader.fetchExecutableFlow(excId);
                Status status = executableFlow.getStatus();
                if(Status.isStatusFinished(status)){
                    task.put("recoverStatus", String.valueOf(executableFlow.getStatus().getNumVal()));
                    if(!status.equals(Status.SUCCEEDED)){
                        logger.warn("There are tasks that failed to execute.");
                        this.hasTaskFailed = true;
                    }
                }
            }catch (ExecutorManagerException em){
                logger.error("update task status failed.", em);
            }
        }
    }

    public int getProjectId() {
        return projectId;
    }

    public void setProjectId(int projectId) {
        this.projectId = projectId;
    }

    public String getFlowId() {
        return flowId;
    }

    public void setFlowId(String flowId) {
        this.flowId = flowId;
    }

    public ExecutionRecover getExecutionRecover() {
        return executionRecover;
    }

    public void setExecutionRecover(ExecutionRecover executionRecover) {
        this.executionRecover = executionRecover;
    }

    public void setExecutionRecoverStartTime(){
        if(executionRecover.getStartTime() == -1) {
            executionRecover.setRecoverStatus(Status.RUNNING);
            executionRecover.setStartTime(System.currentTimeMillis());
        }
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    @Override
    public String toString() {
        return "RecoverTrigger{" +
            "triggerId=" + triggerId +
            ", taskSize=" + taskSize +
            ", projectId=" + projectId +
            ", flowId='" + flowId + '\'' +
            '}';
    }
}
