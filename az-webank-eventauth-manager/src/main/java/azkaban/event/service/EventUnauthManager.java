package azkaban.event.service;


import azkaban.event.dao.EventLoader;
import azkaban.event.entity.EventAuth;
import azkaban.event.entity.EventUnauth;
import azkaban.function.CheckedSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

@Singleton
public class EventUnauthManager {

    private static final Logger logger = LoggerFactory.getLogger(EventUnauthManager.class);

    private static final int DEFAULT_EVENT_UNAUTH_TOTAL = 0;

    private static final List<EventUnauth> DEFAULT_EVENT_UNAUTH_LIST = new ArrayList<>();

    private final EventLoader<EventUnauth> eventUnauthLoader;

    @Inject
    public EventUnauthManager(EventLoader<EventUnauth> eventUnauthLoader) {
        this.eventUnauthLoader = requireNonNull(eventUnauthLoader);
    }

    public Integer setBacklogAlarmUser(EventUnauth eventUnauth) {
        return exceptionHandler(0,
                () -> eventUnauthLoader.setBacklogAlarmUser(eventUnauth));
    }

    public List<EventUnauth> findEventUnauthList(String search, int pageNum, int pageSize) {
        int startIndex = (pageNum - 1) * pageSize;
        return exceptionHandler(DEFAULT_EVENT_UNAUTH_LIST,
                () -> eventUnauthLoader.findEventList(search, startIndex, pageSize));
    }

    public int getEventUnauthTotal(String search) {
        return exceptionHandler(DEFAULT_EVENT_UNAUTH_TOTAL,
                () -> eventUnauthLoader.getEventTotal(search));
    }

    private <T> T exceptionHandler(T defaultValue, CheckedSupplier<T, SQLException> supplier) {
        try {
            return supplier.get();
        } catch (SQLException e) {
            return defaultValue;
        }
    }

    public List<EventUnauth> getEventUnauth(String topic, String sender, String msgName) {
        return exceptionHandler(new ArrayList<>(),
                () -> eventUnauthLoader.getEvent(topic, sender, msgName));
    }
}
