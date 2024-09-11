package azkaban.event.module;

import azkaban.event.dao.EventLoader;
import azkaban.event.dao.JdbcEventAuthImpl;
import azkaban.event.dao.JdbcEventQueueImpl;
import azkaban.event.dao.JdbcEventStatusImpl;
import azkaban.event.entity.EventAuth;
import azkaban.event.entity.EventQueue;
import azkaban.event.entity.EventStatus;
import azkaban.event.service.EventAuthManager;
import azkaban.event.service.EventQueueManager;
import azkaban.event.service.EventStatusManager;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;

public class EventModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(new TypeLiteral<EventLoader<EventAuth>>() {}).to(JdbcEventAuthImpl.class);
        bind(new TypeLiteral<EventLoader<EventQueue>>() {}).to(JdbcEventQueueImpl.class);
        bind(new TypeLiteral<EventLoader<EventStatus>>() {}).to(JdbcEventStatusImpl.class);
        bind(EventAuthManager.class);
        bind(EventQueueManager.class);
        bind(EventStatusManager.class);
    }
}
