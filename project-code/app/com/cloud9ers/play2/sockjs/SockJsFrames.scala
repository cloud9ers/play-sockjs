package com.cloud9ers.play2.sockjs

import play.api.mvc.RawBuffer
import scala.collection.mutable.ArrayBuffer

class MessageFrame(sockJsMessages: Array[Byte]) {
  
  def get(appendNewline: Boolean) = {
    val message: ArrayBuffer[Byte] = new ArrayBuffer[Byte]()
    message.append('a')
    message.appendAll(sockJsMessages)
    if (appendNewline) message.append('\n')
    message
  }
  
}

object SockJsFrames {
  val OPEN_FRAME         = "o"
  val OPEN_FRAME_NL      = "o\n"
  val HEARTBEAT_FRAME    = "h"
  val HEARTBEAT_FRAME_NL = "h\n"
  val XHR_STREAM_H_BLOCK = ((for (i <- 0 to 2047) yield "h").toArray :+ "\n").reduceLeft(_ + _).toArray.map(_.toByte)
  def closingFrame(code: Int, reason: String) = s"""c[$code,"$reason"]"""

  def messageFrame(sockJsMessages: Array[Byte], appendNewline: Boolean) = new MessageFrame(sockJsMessages).get(appendNewline)
}