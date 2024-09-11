package azkaban.event.entity;

public class EventStatus {

    private final int msgId;

    private final String receiver;

    private final String receiveTime;

    private final String topic;

    private final String msgName;

    public EventStatus(String receiver, String receiveTime, String topic, String msgName, int msgId) {
        this.msgId = msgId;
        this.receiver = receiver;
        this.receiveTime = receiveTime;
        this.topic = topic;
        this.msgName = msgName;
    }

    public int getMsgId() {
        return msgId;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getReceiveTime() {
        return receiveTime;
    }

    public String getTopic() {
        return topic;
    }

    public String getMsgName() {
        return msgName;
    }


}
