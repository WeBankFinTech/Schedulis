package azkaban.module;

import azkaban.service.UserParamsService;
import com.google.inject.AbstractModule;

public class UserParamsModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(UserParamsService.class);
    }
}