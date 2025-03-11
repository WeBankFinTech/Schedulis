package azkaban.log;

import org.apache.logging.log4j.message.AbstractMessageFactory;
import org.apache.logging.log4j.message.Message;

/**
 * @author lebronwang
 * @date 2022/03/24
 **/
public class DataMaskingMessageFactory extends AbstractMessageFactory {

  private static final long serialVersionUID = 1L;

  @Override
  public Message newMessage(Object message) {
    return new DataMaskingParameterizedMessage("{}", message);
  }

  @Override
  public Message newMessage(String message) {
    return new DataMaskingParameterizedMessage(message);
  }

  @Override
  public Message newMessage(String message, Object... params) {
    return new DataMaskingParameterizedMessage(message, params);
  }

}
