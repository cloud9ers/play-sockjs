package com.cloud9ers.play2.sockjs

import scala.collection.mutable.ArrayBuffer

class MessageFrame(sockJsMessages: Array[String]) {
  
}

object SockJsFrames {
  val OPEN_FRAME         = "o"
  val OPEN_FRAME_NL      = "o\n"
  val HEARTBEAT_FRAME    = "h"
  val HEARTBEAT_FRAME_NL = "h\n"
  
  def messageFrame(sockJsMessages: Array[String]): MessageFrame = new MessageFrame(sockJsMessages)
}