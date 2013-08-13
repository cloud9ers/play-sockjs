package com.cloud9ers.play2.sockjs

import scala.collection.mutable.ArrayBuffer

class MessageFrame(sockJsMessages: Array[String]) {
  
}

object SockJsFrames {
  val OPEN_FRAME      = new ArrayBuffer() += "o"
  val OPEN_FRAME_NL   = new ArrayBuffer() += "o\n"
  val HEARTBEAT_FRAME = new ArrayBuffer() += "h"
  val HEARTBEAT_FRAME_NL = new ArrayBuffer() += "h\n"
  
  def messageFrame(sockJsMessages: Array[String]): MessageFrame = new MessageFrame(sockJsMessages)
}