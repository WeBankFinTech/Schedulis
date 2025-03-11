package azkaban.system;

/**
 * Created by zhu on 7/6/18.
 */
public class SystemUserManagerException extends Exception {

  private static final long serialVersionUID = 1L;

  public SystemUserManagerException(final String message) {
    super(message);
  }

  public SystemUserManagerException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public SystemUserManagerException(final Exception e) {
    super(e);
  }

}
