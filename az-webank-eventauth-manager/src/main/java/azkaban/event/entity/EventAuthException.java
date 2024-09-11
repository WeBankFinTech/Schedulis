package azkaban.event.entity;

public class EventAuthException extends Exception {

    private static final long serialVersionUID = 1L;

    public EventAuthException(String message) {
        super(message);
    }

    public EventAuthException(String message, Throwable cause) {
        super(message, cause);
    }

    public EventAuthException(Throwable cause) {
        super(cause);
    }
}
