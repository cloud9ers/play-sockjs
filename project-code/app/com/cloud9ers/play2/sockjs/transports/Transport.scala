package com.cloud9ers.play2.sockjs.transports

object Transport {
  val WEBSOCKET    = "websocket"
  val EVENT_SOURCE = "eventsource"
  val HTML_FILE    = "htmlfile"
  val JSON_P       = "jsonp"
  val XHR          = "xhr"
  val XHR_SEND     = "xhr_send"
  val XHR_STREAMING = "xhr_streaming"
  val JSON_P_SEND  = "jsonp_send"
}
class Transport {
  /*
   * Implements few methods that session expects to see in each transport
   */

}