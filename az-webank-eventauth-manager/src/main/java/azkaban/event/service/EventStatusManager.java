package azkaban.event.service;

import azkaban.event.dao.EventLoader;
import azkaban.event.entity.EventStatus;
import azkaban.function.CheckedSupplier;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class EventStatusManager {

    private final EventLoader<EventStatus> eventStatusLoader;

    @Inject
    public EventStatusManager(EventLoader<EventStatus> eventQueueLoader) {
        this.eventStatusLoader = eventQueueLoader;
    }

    public int getEventStatusTotal(String search) {
        return exceptionHandler(0,
                () -> eventStatusLoader.getEventTotal(search));
    }

    public int getEventStatusTotal(String search, String... filterValue) {
        return exceptionHandler(0,
                () -> eventStatusLoader.getEventTotal(search, filterValue));
    }

    public List<EventStatus> findEventStatusList(String search, int pageNum, int pageSize) {
        int startIndex = (pageNum - 1) * pageSize;
        return exceptionHandler(new ArrayList<>(),
                () -> eventStatusLoader.findEventList(search, startIndex, pageSize));
    }

    public List<EventStatus> findEventStatusList(String search, int pageNum, int pageSize, String... filterValue) {
        int startIndex = (pageNum - 1) * pageSize;
        return exceptionHandler(new ArrayList<>(),
                () -> eventStatusLoader.findEventList(search, startIndex, pageSize, filterValue));
    }

    private <T> T exceptionHandler(T defaultValue, CheckedSupplier<T, SQLException> supplier) {
        try {
            return supplier.get();
        } catch (SQLException e) {
            return defaultValue;
        }
    }
}
