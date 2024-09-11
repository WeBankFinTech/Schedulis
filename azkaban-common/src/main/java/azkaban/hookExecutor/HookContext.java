package azkaban.hookExecutor;

import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutorLoader;
import azkaban.utils.Props;
import org.slf4j.Logger;

/**
 * Hook Context keeps all the necessary information for all the hooks.
 * New implemented hook can get the jobinfo, job conf  from this hook context
 */
public class HookContext {

    static public enum HookType {
        PRE_EXEC_SYS_HOOK, POST_EXEC_SYS_HOOK, PRE_EXEC_USER_HOOK, POSTEXEC_USER_HOOK
    }

    private HookType hookType;
    private String errorMessage;
    private Throwable exception;
    private ExecutableNode node;
    private Props props;
    private Logger logger;
    private ExecutorLoader executorLoader;

    public HookContext(ExecutableNode node, Logger logger, Props props,
        ExecutorLoader executorLoader) {
        this.node = node;
        this.logger = logger;
        this.props = props;
        this.executorLoader = executorLoader;
    }

    public ExecutableNode getNode() { return node; }

    public void setNode(ExecutableNode node) { this.node = node; }

    public Logger getLogger() { return logger; }

    public void setLogger(Logger logger) { this.logger = logger; }

    public HookType getHookType() { return hookType; }

    public void setHookType(HookType hookType) {
        this.hookType = hookType;
    }

    public Props getProps() { return props; }

    public void setProps(Props props) { this.props = props; }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setException(Throwable exception) {
        this.exception = exception;
    }

    public Throwable getException() {
        return exception;
    }

    public ExecutorLoader getExecutorLoader() {
        return executorLoader;
    }

    public void setExecutorLoader(ExecutorLoader executorLoader) {
        this.executorLoader = executorLoader;
    }
}
