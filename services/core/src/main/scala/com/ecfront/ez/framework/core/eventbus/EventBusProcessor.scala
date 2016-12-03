package com.ecfront.ez.framework.core.eventbus

import com.ecfront.common.JsonHelper
import com.ecfront.ez.framework.core.EZ
import com.ecfront.ez.framework.core.logger.Logging
import com.ecfront.ez.framework.core.rpc.{Method, RPCProcessor}

import scala.concurrent.Future

trait EventBusProcessor extends Logging {

  private[ecfront] val FLAG_CONTEXT = "__ez_context__"
  val ADDRESS_SPLIT_FLAG = "@"

  private val DEFAULT_TIMEOUT =
    if (EZ.isDebug) {
      Long.MaxValue
    } else {
      60L * 1000
    }

  def publish(address: String, message: Any, args: Map[String, String] = Map()): Unit = {
    val addr = packageAddress(Method.PUB_SUB.toString, address)
    val msg = toAllowedMessage(message)
    logger.trace(s"[EB] Publish a message [$addr] : $args > ${RPCProcessor.cutPrintShow(msg.toString)} ")
    doPublish(addr, msg, args)
  }

  protected def doPublish(address: String, message: Any, args: Map[String, String]): Unit

  def request(address: String, message: Any, args: Map[String, String] = Map(), ha: Boolean = true): Unit = {
    val addr = packageAddress(Method.REQ_RESP.toString, address)
    val msg = toAllowedMessage(message)
    logger.trace(s"[EB] Request a message [$addr] : $args > ${RPCProcessor.cutPrintShow(msg.toString)} ")
    doRequest(addr, msg, args, ha)
  }

  protected def doRequest(address: String, message: Any, args: Map[String, String], ha: Boolean): Unit

  def pubReq(address: String, message: Any, args: Map[String, String] = Map(), ha: Boolean = true): Unit = {
    publish(address, message, args)
    request(address, message, args, ha)
  }

  def ack[E: Manifest](address: String, message: Any, args: Map[String, String] = Map(), timeout: Long = DEFAULT_TIMEOUT): (E, Map[String, String]) = {
    val addr = packageAddress(Method.ACK.toString, address)
    val msg = toAllowedMessage(message)
    logger.trace(s"[EB] ACK a message [$addr] : $args > ${RPCProcessor.cutPrintShow(msg.toString)} ")
    doAck[E](addr, msg, args, timeout)
  }

  protected def doAck[E: Manifest](address: String, message: Any, args: Map[String, String], timeout: Long): (E, Map[String, String])

  def ackAsync[E: Manifest](address: String, message: Any, args: Map[String, String] = Map(), timeout: Long = DEFAULT_TIMEOUT)(replyFun: => (E, Map[String, String]) => Unit): Unit = {
    val addr = packageAddress(Method.ACK.toString, address)
    val msg = toAllowedMessage(message)
    logger.trace(s"[EB] ACK async a message [$addr] : $args > ${RPCProcessor.cutPrintShow(msg.toString)} ")
    doAckAsync[E](replyFun, addr, msg, args, timeout)
  }

  protected def doAckAsync[E: Manifest](replyFun: => (E, Map[String, String]) => Unit, address: String, message: Any, args: Map[String, String], timeout: Long): Unit

  def subscribe[E: Manifest](address: String, reqClazz: Class[E] = null)(receivedFun: (E, Map[String, String]) => Unit): Unit = {
    doSubscribe[E](packageAddress(Method.PUB_SUB.toString, address), reqClazz)(receivedFun)
  }

  protected def doSubscribe[E: Manifest](address: String, reqClazz: Class[E])(receivedFun: (E, Map[String, String]) => Unit): Unit

  def response[E: Manifest](address: String, reqClazz: Class[E] = null)(receivedFun: (E, Map[String, String]) => Unit): Unit = {
    doResponse[E](packageAddress(Method.REQ_RESP.toString, address), reqClazz)(receivedFun)
  }

  protected def doResponse[E: Manifest](address: String, reqClazz: Class[E])(receivedFun: (E, Map[String, String]) => Unit): Unit

  def reply[E: Manifest](address: String, reqClazz: Class[E] = null)(receivedFun: (E, Map[String, String]) => (Any, Map[String, String])): Unit = {
    doReply[E](packageAddress(Method.ACK.toString, address), reqClazz)(receivedFun)
  }

  protected def doReply[E: Manifest](address: String, reqClazz: Class[E])(receivedFun: (E, Map[String, String]) => (Any, Map[String, String])): Unit

  def replyAsync[E: Manifest](address: String, reqClazz: Class[E] = null)(receivedFun: (E, Map[String, String]) => Future[(Any, Map[String, String])]): Unit = {
    doReplyAsync[E](packageAddress(Method.ACK.toString, address), reqClazz)(receivedFun)
  }

  protected def doReplyAsync[E: Manifest](address: String, reqClazz: Class[E])(receivedFun: (E, Map[String, String]) => Future[(Any, Map[String, String])]): Unit


  def packageAddress(defaultMethod: String, path: String): String = {
    val formatPath = if (path.endsWith("/")) path else path + "/"
    if (formatPath.contains("@")) {
      formatPath
    } else {
      defaultMethod + ADDRESS_SPLIT_FLAG + formatPath
    }
  }

  private[ecfront] def toAllowedMessage(message: Any): Any = {
    message match {
      case m if m.isInstanceOf[String] || m.isInstanceOf[Int] ||
        m.isInstanceOf[Long] || m.isInstanceOf[Double] || m.isInstanceOf[Float] ||
        m.isInstanceOf[Boolean] || m.isInstanceOf[BigDecimal] || m.isInstanceOf[Short] => message
      case _ => JsonHelper.toJsonString(message)
    }
  }

  protected def toObject[E](message: Any, reqClazz: Class[E])(implicit e: Manifest[E]): E = {
    if (reqClazz != null) {
      reqClazz match {
        case m if classOf[String].isAssignableFrom(m) ||
          classOf[Int].isAssignableFrom(m) ||
          classOf[Long].isAssignableFrom(m) ||
          classOf[Double].isAssignableFrom(m) ||
          classOf[Float].isAssignableFrom(m) ||
          classOf[Boolean].isAssignableFrom(m) ||
          classOf[BigDecimal].isAssignableFrom(m) ||
          classOf[Short].isAssignableFrom(m) => message.asInstanceOf[E]
        case _ => JsonHelper.toObject(message, reqClazz)
      }
    } else {
      if (e == manifest[String]) {
        if (message.isInstanceOf[String]) {
          message.asInstanceOf[E]
        } else {
          JsonHelper.toJsonString(message).asInstanceOf[E]
        }
      } else {
        JsonHelper.toObject[E](message)
      }
    }
  }

}