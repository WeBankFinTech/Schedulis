package azkaban.event.entity;

public class EventUnauth {

    private final String sender;

    private final String topic;

    private final String msgName;

    private final String recordTime;

    private String backlogAlarmUser;

    private String alertLevel;

    public EventUnauth(String sender, String topic, String msgName, String recordTime , String backlogAlarmUser, String alertLevel) {
        this.sender = sender;
        this.topic = topic;
        this.msgName = msgName;
        this.recordTime = recordTime;
        this.backlogAlarmUser = backlogAlarmUser;
        this.alertLevel = alertLevel;
    }

    public String getSender() {
        return sender;
    }

    public String getTopic() {
        return topic;
    }

    public String getMsgName() {
        return msgName;
    }

    public String getRecordTime() {
        return recordTime;
    }

    public String getBacklogAlarmUser() { return backlogAlarmUser; }

    public void setBacklogAlarmUser(String backlogAlarmUser) { this.backlogAlarmUser = backlogAlarmUser; }

    public String getAlertLevel() { return alertLevel; }

    public void setAlertLevel(String alertLevel) { this.alertLevel = alertLevel; }
}
