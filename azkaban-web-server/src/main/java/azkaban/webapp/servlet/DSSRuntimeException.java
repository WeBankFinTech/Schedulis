package azkaban.webapp.servlet;

import org.apache.linkis.common.exception.WarnException;

public class DSSRuntimeException extends WarnException {

    public DSSRuntimeException(String msg){
        super(100000, msg);
    }

    public DSSRuntimeException(int errorCode, String msg) {
        super(errorCode, msg);
    }

    public DSSRuntimeException(int errorCode, String msg, Throwable e) {
        super(errorCode, msg);
        initCause(e);
    }
}
