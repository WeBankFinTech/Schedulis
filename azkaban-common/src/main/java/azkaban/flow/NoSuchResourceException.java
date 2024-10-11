package azkaban.flow;

/**
 * @author lebronwang
 * @date 2023/12/11
 **/
public class NoSuchResourceException extends RuntimeException {

  public NoSuchResourceException(final String message) {
    super(message);
  }

  public NoSuchResourceException(final String message, final Throwable cause) {
    super(message, cause);
  }

  public NoSuchResourceException(final Exception e) {
    super(e);
  }
}
