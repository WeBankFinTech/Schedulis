package azkaban.event.entity;

public class EventQueue {

    private final long msgId;

    private final String sender;

    private final String sendTime;

    private final String topic;

    private final String msgName;

    private final String msg;

    private final String sendIP;

    private final String wemqBizno;

    public EventQueue(long msgId, String sender, String sendTime, String topic, String msgName, String msg, String sendIP,String wemqBizno) {
        this.msgId = msgId;
        this.sender = sender;
        this.sendTime = sendTime;
        this.topic = topic;
        this.msgName = msgName;
        this.msg = msg;
        this.sendIP = sendIP;
        this.wemqBizno = wemqBizno;
    }

    public long getMsgId() {
        return msgId;
    }

    public String getSender() {
        return sender;
    }

    public String getSendTime() {
        return sendTime;
    }

    public String getTopic() {
        return topic;
    }

    public String getMsgName() {
        return msgName;
    }

    public String getMsg() {
        return msg;
    }

    public String getSendIP() {
        return sendIP;
    }

    public String getWemqBizno() {
        return wemqBizno;
    }
}
