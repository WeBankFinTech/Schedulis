package azkaban.event.service;

import azkaban.event.dao.EventLoader;
import azkaban.event.entity.EventQueue;
import azkaban.function.CheckedSupplier;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class EventQueueManager {

    private final EventLoader<EventQueue> eventQueueLoader;

    @Inject
    public EventQueueManager(EventLoader<EventQueue> eventQueueLoader) {
        this.eventQueueLoader = eventQueueLoader;
    }

    public int getEventQueueTotal(String search) {
        return exceptionHandler(0,
                () -> eventQueueLoader.getEventTotal(search));
    }

    public int getEventQueueTotal(String search, boolean authType, String... filterValue) {
        return exceptionHandler(0,
                () -> eventQueueLoader.getEventTotal(search, authType, filterValue));
    }

    public int getEventQueueTotal4Page(String search, boolean authType, int index, int sum, String... filterValue) {
        return exceptionHandler(0,
                () -> eventQueueLoader.getEventTotal4Page(search, authType, index, sum, filterValue));
    }

    public List<EventQueue> findEventQueueList(String search, int pageNum, int pageSize) {
        int startIndex = (pageNum - 1) * pageSize;
        return exceptionHandler(new ArrayList<>(),
                () -> eventQueueLoader.findEventList(search, startIndex, pageSize));
    }

    public List<EventQueue> findEventQueueList(String search, int pageNum, int pageSize, boolean authType, String... filterValue) {
        int startIndex = (pageNum - 1) * pageSize;
        return exceptionHandler(new ArrayList<>(),
                () -> eventQueueLoader.findEventList(search, authType, startIndex, pageSize, filterValue));
    }

    private <T> T exceptionHandler(T defaultValue, CheckedSupplier<T, SQLException> supplier) {
        try {
            return supplier.get();
        } catch (SQLException e) {
            return defaultValue;
        }
    }

    public int queryMessageNum(String... filterValue) {
        return exceptionHandler(-1,
                ()-> eventQueueLoader.queryMessageNum(filterValue));
    }
    public List<EventQueue> queryMessage(String topic,String sender,String msgName,String msgBody,String isLike,Integer pageNo,Integer pageSize) {

        return exceptionHandler(null,
                ()-> eventQueueLoader.queryMessage(topic,sender,msgName,msgBody,isLike,pageNo,pageSize));
    }

}
