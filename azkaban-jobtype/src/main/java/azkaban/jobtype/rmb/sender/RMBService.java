package azkaban.jobtype.rmb.sender;

import cn.webank.rmb.api.RMB;
import cn.webank.rmb.destination.Destination;
import cn.webank.rmb.message.AppHeader;
import cn.webank.rmb.message.Message;
import cn.webank.rmb.message.RMBMessageUtil;
import cn.webank.rmb.message.SysHeader;
import com.webank.weservice.common.exception.WeServiceException;
import com.webank.weservice.protocol.wemq.WeMQMessage;
import com.webank.weservice.protocol.wemq.WeMQMessageHeader;
import com.webank.weservice.protocol.werpc.WeRpcRequest;
import com.webank.weservice.protocol.werpc.WeRpcRequestHeader;
import com.webank.weservice.protocol.werpc.WeRpcResponse;
import com.webank.weservice.protocol.werpc.WeRpcResponseHeader;
import com.webank.weservice.wemq.WeMQService;
import com.webank.weservice.wemq.api.SendMessageCallback;
import com.webank.weservice.wemq.impl.SendResult;
import com.webank.weservice.werpc.WeRpcService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class RMBService {

  private String systemId;

  private String targetDCN;

  private String serviceId;

  private String scenarioId;

  private static String jsonMessage = "";

  private static final Logger logger = LoggerFactory.getLogger(RMBService.class);

  private static final AppHeader APP_HEADER = new AppHeader();

  private final String VERSION = "1.8.2";

  public RMBService(RMBMsg msg, Properties rmbProperties) {
    systemId = rmbProperties.getProperty("rmb.client.system.id");

    targetDCN = msg.getTargetDcn();

    serviceId = msg.getServiceId();

    scenarioId = rmbProperties.getProperty("server.receive.scenario.id");

    logger.info("systemId: " + systemId + ",targetDCN: " + targetDCN + ",serviceId: " + serviceId
        + ",scenarioId: " + scenarioId);
  }

  public void sendAsyncRMBMessage(RMBMsg msg) {
    logger.info("Start to send Async RMB message !!!");
    WeMQService weMQService = new WeMQService();
    try {
      SysHeader msgHeader = RMBMessageUtil.createSysHeader(msg.getBiz(), msg.getBiz(), systemId);
      Destination destination = RMBMessageUtil.createSimpleDestination(serviceId, scenarioId, targetDCN);
      APP_HEADER.setTransCode(systemId + "_" + serviceId);
      Message textMessage = RMBMessageUtil.createMessage(msgHeader, APP_HEADER, destination, msg.getMessage());
      logger.info("RMB Message is:" + textMessage);

      //1.创建消息头
      WeMQMessageHeader header = new WeMQMessageHeader(msg.getBiz(), msg.getBiz());
      if (msg.getAddobCooperativeId()) {
        logger.info("rmb.pszTenantId is not null, add pszTenantId:{}", msg.getObCooperativeId());
        header.addExtField("pszTenantId", msg.getObCooperativeId());
      }
      //2.创建消息
      WeMQMessage message = new WeMQMessage(targetDCN, serviceId, scenarioId, header, msg.getMessage());
      weMQService = new WeMQService();
      try {
        //初始化，如果初始化失败，则抛出InitializationException异常
        weMQService.initialize();
      } catch (WeServiceException e) {
        //WeRpcService初始化/启动异常，无法正常进行服务调用，用户根据自身应用场景进行启动失败的异常处理。
        logger.error("start weservice fail", e);
        System.exit(-1);
      }
      //3.发送消息
      weMQService.publish(message, new SendMessageCallback() {
        @Override
        public void onSuccess(SendResult sendResult) {
          logger.info("publishResult is {}" , sendResult.toString());
        }

        @Override
        public void onException(WeServiceException e) {
          logger.error("publishResult error, e:{}", e);
          throw new RuntimeException("PublishCallback Call RMB or send message falied.", e);
        }
      });
    } catch (Throwable e) {
      throw new RuntimeException("Call Async RMB or send message falied!", e);
    } finally {
      logger.info(" Send ASync rmb msg end!");
      RMB.stopWait();
      RMB.destroy();
      weMQService.shutdown();
    }
  }

  public void sendSyncRMBMessage(RMBMsg msg, String reqTimeout) {

    logger.info("Start to send Sync RMB message !!!");
    //创建WeRpcService
    WeRpcService weRpcService = new WeRpcService();
    try {
      //初始化，如果初始化失败，则抛出InitializationException异常
      weRpcService.initialize();
      weRpcService.setApplicationVersion(VERSION);
      //启动服务，如启动失败，则抛出WeServiceException异常
      weRpcService.start();
    } catch (WeServiceException e) {
      //WeRpcService初始化/启动异常，无法正常进行服务调用，用户根据自身应用场景进行启动失败的异常处理。
      logger.error("start weservice fail", e);
      System.exit(-1);
    }

    //创建请求头，必须有业务流水号和系统流水号
    WeRpcRequestHeader header = new WeRpcRequestHeader(msg.getBiz(), msg.getBiz());
    if (msg.getAddobCooperativeId()) {
      logger.info("rmb.pszTenantId is not null, add pszTenantId:{}", msg.getObCooperativeId());
      header.addExtField("pszTenantId", msg.getObCooperativeId());
    }
    //此笔流水的源头调用子系统号
    header.setOrgRequestSysId(systemId);
    WeRpcRequest request = new WeRpcRequest(targetDCN, serviceId, scenarioId, header, Integer.parseInt(reqTimeout));
    logger.info("set request timeout: {} ms", reqTimeout);
    request.setBody(msg.getMessage());

    try {
      /*
       * 执行调用。
       * 如果请求下游实例成功，则返回 WeRpcResponse；
       * 如果请求失败（包括请求没发出去、请求超时、下游服务未启动等），则抛出WeServiceException异常。
       */
      WeRpcResponse response = weRpcService.invokeSync(request);
      logger.info("invokeSync success. {}", response);
      /*
       * 判断业务处理是否成功
       * 也可通过 {@link WeRpcResponse#getRetStatus()} 进行判断。如果为 {@link RetStatus.SUCCESS } 则为成功；
       * 为 {@link RetStatus.FAIL } 则为失败。
       */
      if (response.isSuccess()) {
        //业务处理成功，则获取 Header和Body进行处理
        //获取Header
        WeRpcResponseHeader responseHeader = response.getHeader();
        //获取Body
        String responseBody = response.getBody();
        if (responseBody != null) {
          if (StringUtils.isNumeric(responseBody)) { // 返回执行id，判断消息结果为数字
            logger.info(" Call RMB successfully ,response content {}"
                    + responseBody);
          } else if ("ok".equalsIgnoreCase(responseBody)) {
            logger.info(" Call RMB or send message successfully ,response content {}"
                    + responseBody);
          } else if ("failed".equalsIgnoreCase(responseBody)) {
            logger.error("Call RMB or send message failed , please confirm whether the sender has permission , response content {}", responseBody);
            throw new RuntimeException(
                    "\n Call RMB or send message failed , please confirm whether the sender has permission , response content {}"
                            + responseBody);
          } else if ("jsonParseFailed".equals(responseBody)) {
            logger.error("Call RMB or send message failed , caused by json parse failed , response content {}", responseBody);
            throw new RuntimeException(
                    "\n Call RMB or send message failed , caused by json parse failed , response content {}"
                            + responseBody);
          } else if ("nullContentFailed".equals(responseBody)) {
            logger.error("Call RMB or send message failed , caused by content is null.");
            throw new RuntimeException(
                    "\n Call RMB or send message failed , caused by content is null.");
          } else if ("eventFailed".equals(responseBody)) {
            logger.error("Execute event flow failed , caused by session or execute flow error.");
            throw new RuntimeException(
                    "\n Execute event flow failed , caused by session or execute flow error.");
          } else {
            logger.info("responseBody:{}", responseBody);
          }
        } else {
          //业务处理失败，则获取业务错误码和错误信息（此为业务层面的错误码和错误信息，有业务系统设置的）
          //获取错误码
          String retCode = response.getRetCode();
          //获取错误信息
          String retErrMsg = response.getRetErrMsg();
          logger.error("Error Code is: {}, error message is: {}", retCode, retErrMsg);
          throw new RuntimeException(String.format("Error Code is: %s, error message is: %s", retCode, retErrMsg));
        }
      } else {
        logger.error("Call RMB failed：" + " \n response：" + response + " \n bizNo：" + msg.getBiz() + " \n consumerSeqNo:" + msg.getBiz());
        throw new RuntimeException("Call RMB failed：" + " \n response：" + response + " \n bizNo：" + msg.getBiz() + " \n consumerSeqNo:" + msg.getBiz());
      }
    } catch (WeServiceException ex) {
      /*
       * 注意：此处为调用失败，不是业务失败。
       * 请求失败的异常处理。
       * WeServiceException中包含错误码和错误信息，如果有需要，可对照错误码表进行处理。
       */
      //错误码获取
      logger.error("request error errorcode:{}", String.valueOf(ex.getErrCode()));
      //错误信息获取
      logger.error("request error errorMsg:{}", ex.getErrMsg());
      throw new RuntimeException("Call RMB or send message falied!!", ex);
    } finally {
      logger.info("Start to shutdown weRpcService");
      weRpcService.shutdown();
      logger.info("Send Sync rmb msg end!");
    }
  }
}