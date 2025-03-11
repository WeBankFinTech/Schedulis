package azkaban.event.module;

import azkaban.event.dao.*;
import azkaban.event.entity.*;
import azkaban.event.service.*;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;

public class EventModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(new TypeLiteral<EventLoader<EventUnauth>>() {}).to(JdbcEventUnauthImpl.class);
        bind(new TypeLiteral<EventLoader<EventAuth>>() {}).to(JdbcEventAuthImpl.class);
        bind(new TypeLiteral<EventLoader<EventQueue>>() {}).to(JdbcEventQueueImpl.class);
        bind(new TypeLiteral<EventLoader<EventStatus>>() {}).to(JdbcEventStatusImpl.class);
        bind(EventAuthManager.class);
        bind(EventQueueManager.class);
        bind(EventStatusManager.class);
        bind(EventUnauthManager.class);
    }
}
