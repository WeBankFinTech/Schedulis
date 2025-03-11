package azkaban.event.entity;

public class EventQueue {

    private  long msgId;

    private  String sender;

    private  String sendTime;

    private  String topic;

    private  String msgName;

    private  String msg;

    private  String sendIP;

    private  String wemqBizno;



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

    public EventQueue( String sendTime, String msg) {
        this.sendTime = sendTime;
        this.msg = msg;

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
