package com.cloud9ers.play2.sockjs.transports

import com.cloud9ers.play2.sockjs.SockJs
import play.api.mvc.Controller

object Transport {
  val WEBSOCKET    = "websocket"
  val EVENT_SOURCE = "eventsource"
  val HTML_FILE    = "htmlfile"
  val JSON_P       = "jsonp"
  val XHR          = "xhr"
  val XHR_SEND     = "xhr_send"
  val XHR_STREAMING = "xhr_streaming"
  val JSON_P_SEND  = "jsonp_send"
  val CONTENT_TYPE_JAVASCRIPT = "application/javascript; charset=UTF-8"
  val CONTENT_TYPE_FORM = "application/x-www-form-urlencoded"
  val CONTENT_TYPE_PLAIN = "text/plain; charset=UTF-8"
  val CONTENT_TYPE_HTML = "text/html; charset=UTF-8"
}
class Transport extends Controller with SockJs{
  /*
   * Implements few methods that session expects to see in each transport
   */

}