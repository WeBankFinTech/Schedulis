package azkaban.event.entity;

public class EventAuth {

    private final String sender;

    private final String topic;

    private final String msgName;

    private final String recordTime;

    private final int allowSend;

    private String backlogAlarmUser;

    private String alertLevel;

    public EventAuth(String sender, String topic, String msgName, String recordTime, int allowSend, String backlogAlarmUser, String alertLevel) {
        this.sender = sender;
        this.topic = topic;
        this.msgName = msgName;
        this.recordTime = recordTime;
        this.allowSend = allowSend;
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

    public int getAllowSend() {
        return allowSend;
    }

    public String getBacklogAlarmUser() { return backlogAlarmUser; }

    public void setBacklogAlarmUser(String backlogAlarmUser) { this.backlogAlarmUser = backlogAlarmUser; }

    public String getAlertLevel() { return alertLevel; }

    public void setAlertLevel(String alertLevel) { this.alertLevel = alertLevel; }
}
