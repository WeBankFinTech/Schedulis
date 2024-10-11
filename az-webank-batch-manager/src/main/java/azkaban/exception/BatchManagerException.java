package azkaban.exception;


public class BatchManagerException extends Exception {

  private static final long serialVersionUID = 1L;

  public BatchManagerException(final String message) {
    super(message);
  }

  public BatchManagerException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public BatchManagerException(final Exception e) {
    super(e);
  }

}
