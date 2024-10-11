package azkaban.jobhook;

import azkaban.hookExecutor.Hook;
import azkaban.utils.Props;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class JobHookPluginSet<T> {

  private final Map<String, Class<? extends Hook>> jobToClass;
  private final Map<String, Props> pluginJobPropsMap;
  private Props commonJobProps;

  /**
   * Base constructor
   */
  public JobHookPluginSet() {
    this.jobToClass = new HashMap<>();
    this.pluginJobPropsMap = new HashMap<>();
  }

  /**
   * Copy constructor
   */
  public JobHookPluginSet(final JobHookPluginSet clone) {
    this.jobToClass = new HashMap<>(clone.jobToClass);
    this.pluginJobPropsMap = new HashMap<>(clone.pluginJobPropsMap);
    this.commonJobProps = clone.commonJobProps;
  }

  /**
   * Gets common properties for every jobhook
   */
  public Props getCommonPluginJobProps() {
    return this.commonJobProps;
  }

  /**
   * Sets the common properties shared in every jobhook
   */
  public void setCommonPluginJobProps(final Props commonJobProps) {
    this.commonJobProps = commonJobProps;
  }

  /**
   * Get the properties that will be given to the plugin as default job properties.
   */
  public Props getPluginJobProps(final String jobHookName) {
    return this.pluginJobPropsMap.get(jobHookName);
  }

  /**
   * Gets the plugin job runner class
   */
  public Class<? extends Hook> getPluginClass(final String jobHookName) {
    return this.jobToClass.get(jobHookName);
  }

  /**
   * Gets the plugin job runner class
   */
  public Set<String> getAllPluginName() {
    return this.jobToClass.keySet();
  }

  /**
   * Adds plugin jobhook class
   */
  public void addPluginClass(final String jobHookName,
      final Class<? extends Hook> jobHookClass) {
    this.jobToClass.put(jobHookName, jobHookClass);
  }

  /**
   * Adds plugin job properties used as default runtime properties
   */
  public void addPluginJobProps(final String jobHookName, final Props props) {
    this.pluginJobPropsMap.put(jobHookName, props);
  }

}
