package azkaban.history;

import azkaban.executor.ExecutorLoader;
import azkaban.executor.JdbcExecutorLoader;
import azkaban.project.ProjectManager;

import java.util.List;
import java.util.Map;

import static azkaban.ServiceProvider.SERVICE_PROVIDER;

public class GroupTask {
    private List<Map<String, String>> taskList;
    private boolean errorContinue;

    public GroupTask(List<Map<String, String>> taskList, boolean errorContinue) {
        this.taskList = taskList;
        this.errorContinue = errorContinue;
    }

    public Map<String, String> nextTask(){
        for(int i = 0; i < taskList.size(); i++){
            Map<String, String> task = taskList.get(i);
            if(i == 0 && !task.containsKey("isSubmit")){
                return task;
            } else if (i > 0 && taskList.get(i - 1).get("recoverStatus").equals("30")) {
                break;
            } else if(i > 0 && taskList.get(i - 1).get("recoverStatus").equals("50") && !task.containsKey("isSubmit")){
                return task;
            } else if(i > 0 && errorContinue
                && (taskList.get(i - 1).get("recoverStatus").equals("70") || taskList.get(i - 1).get("recoverStatus").equals("60"))
                && !task.containsKey("isSubmit")){
                return task;
            }
        }
        return null;
    }


}
