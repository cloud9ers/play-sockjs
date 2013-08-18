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
  val OPEN_FRAME      = new ArrayBuffer() += "o"
  val OPEN_FRAME_NL   = new ArrayBuffer() += "o\n"
  val HEARTBEAT_FRAME = new ArrayBuffer() += "h"
  val HEARTBEAT_FRAME_NL = new ArrayBuffer() += "h\n"
  
  def messageFrame(sockJsMessages: Array[Byte], appendNewline: Boolean) = new MessageFrame(sockJsMessages).get(appendNewline)
}