package azkaban.history;


import azkaban.executor.Status;

import java.util.List;
import java.util.Map;


public class GroupTask {
    private List<Map<String, String>> taskList;
    private boolean errorContinue;

    public GroupTask() {
    }

    public GroupTask(List<Map<String, String>> taskList, boolean errorContinue) {
        this.taskList = taskList;
        this.errorContinue = errorContinue;
    }

    public Map<String, String> nextTask(){
        for(int i = 0; i < taskList.size(); i++){
            Map<String, String> task = taskList.get(i);
            if(i == 0 && !task.containsKey("isSubmit")){
                return task;
            } else if (i > 0 && ("30".equals(taskList.get(i - 1).get("recoverStatus")) || ("20".equals(taskList.get(i - 1).get("recoverStatus")) && taskList.get(i - 1).containsKey("isSubmit")))) {
                return null;
            } else if(i > 0 && "50".equals(taskList.get(i - 1).get("recoverStatus")) && !task.containsKey("isSubmit")){
                return task;
            } else if(i > 0 && errorContinue
                && ("70".equals(taskList.get(i - 1).get("recoverStatus")) || "60"
                .equals(taskList.get(i - 1).get("recoverStatus")))
                && !task.containsKey("isSubmit")){
                return task;
            }
        }
        return null;
    }

    public Map<String, String> nextNoSubmitTask() {
        for(int i = 0; i < taskList.size(); i++){
            Map<String, String> task = taskList.get(i);
            if( !task.containsKey("isSubmit")){
                return task;
            }

        }
        return null;
    }

    public void removeTask(Map<String, String> task) {
        taskList.remove(task);
    }

    public void addTask(Map<String, String> targetTask) {
        Map<String, String> task = nextTask();
        int targetIndex = taskList.indexOf(task);
        if (targetIndex != -1) {
            taskList.add(targetIndex, targetTask);
        } else {
            taskList.add(targetTask);
        }
    }

    public boolean checkIsRunning() {
        for (Map<String, String> task : taskList) {
            if (task.containsKey("isSubmit") && !Status.isStatusFinished(Status.fromInteger(Integer.parseInt(task.get("recoverStatus"))))) {
                return true;
            }
        }
            return false;
            }

    public List<Map<String, String>> getTaskList() {
        return taskList;
    }

    public void setTaskList(List<Map<String, String>> taskList) {
        this.taskList = taskList;
    }

    public boolean isErrorContinue() {
        return errorContinue;
    }

    public void setErrorContinue(boolean errorContinue) {
        this.errorContinue = errorContinue;
        }


    @Override
    public String toString() {
        StringBuilder taskInfo = new StringBuilder("[");
        for (Map<String, String> task : taskList) {
            taskInfo.append("isSubmit:" + task.get("isSubmit") + ",");
            taskInfo.append("recoverStatus:" + task.get("recoverStatus") + ",");
            taskInfo.append("recoverStartTime:" + task.get("recoverStartTime"));
        }
        taskInfo.append("]");
        return "GroupTask{" +
                "taskInfo=" + taskInfo +
                ", errorContinue=" + errorContinue +
                '}';
    }
}
