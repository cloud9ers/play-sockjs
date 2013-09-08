package com.cloud9ers.play2.sockjs.transports

import com.cloud9ers.play2.sockjs.{ SockJsPlugin, SockJs, SessionManager }
import play.api.mvc.{ Controller, RequestHeader, Request, Result, AnyContent }
import play.api.libs.iteratee.{ Enumerator, Iteratee, Concurrent }
import play.api.libs.json.JsValue
import akka.actor.{ Actor, ActorRef }
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import com.cloud9ers.play2.sockjs.Session
import scala.concurrent.Future

object Transport {
  val WEBSOCKET = "websocket"
  val EVENT_SOURCE = "eventsource"
  val HTML_FILE = "htmlfile"
  val JSON_P = "jsonp"
  val XHR = "xhr"
  val XHR_SEND = "xhr_send"
  val XHR_STREAMING = "xhr_streaming"
  val JSON_P_SEND = "jsonp_send"
  val CONTENT_TYPE_JAVASCRIPT = "application/javascript; charset=UTF-8"
  val CONTENT_TYPE_FORM = "application/x-www-form-urlencoded"
  val CONTENT_TYPE_PLAIN = "text/plain; charset=UTF-8"
  val CONTENT_TYPE_HTML = "text/html; charset=UTF-8"
}
class Transport extends Controller with SockJs {
  /*
   * Implements few methods that session expects to see in each transport
   */

}

object BaseTransport extends Transport {
//  def closeSession(session: Future[ActorRef])(implicit request: Request[AnyContent]): Result =
//    {
//      Async((sessionManager ? SessionManager.GetOrCreateSession(sessionId)).map {
//        case None => NotFound
//        case Some(ses: ActorRef) =>
//          val (upEnumerator, upChannel) = Concurrent.broadcast[JsValue]
//          val downIteratee = Iteratee.foreach[JsValue](userMsg => ses ! Session.Enqueue(userMsg)) //TODO: enqueue in close! is that important?
//          f(request)(upEnumerator, downIteratee)
//          upChannel.eofAndEnd()
//          ses ! Session.Close(3000, "Go Away!")
//
//          Ok //FIXME: close response
//      })
//    }
}