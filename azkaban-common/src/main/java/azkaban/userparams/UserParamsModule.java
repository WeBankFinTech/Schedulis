package azkaban.userparams;

import com.google.inject.AbstractModule;

/**
 * @author lebronwang
 * @date 2023/04/18
 **/
public class UserParamsModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(UserParamsService.class);
  }
}
