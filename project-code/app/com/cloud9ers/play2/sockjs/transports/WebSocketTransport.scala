package com.cloud9ers.play2.sockjs.transports

import com.cloud9ers.play2.sockjs.{ Session, SessionManager }
import play.api.libs.iteratee.{ Concurrent, Iteratee, Enumerator }
import akka.actor.{ Actor, ActorRef, Props }
import play.api.mvc.{ RequestHeader, WebSocket }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout
import play.api.libs.json.JsValue
import com.cloud9ers.play2.sockjs.JsonCodec

object WebSocketTransport extends TransportController {
  /*
   * Websocket transport implementation
   */
  def websocket(f: RequestHeader => Future[(Iteratee[JsValue, _], Enumerator[JsValue])])(implicit frameFormatter: WebSocket.FrameFormatter[String]) =
    using { rh =>
      val p = f(rh)
      val upIteratee = Iteratee.flatten(p.map(_._1))
      val downEnumerator = Enumerator.flatten(p.map(_._2))

      (upIteratee, downEnumerator)
    }
  /**
   * returns Handler and passes a function that pipes the user Enumerator to the sockjs Iteratee
   * and pipes the sockjs Enumerator to the user Iteratee
   */
  def using(f: RequestHeader => (Iteratee[JsValue, _], Enumerator[JsValue]))(implicit frameFormatter: WebSocket.FrameFormatter[String]): play.api.mvc.WebSocket[String] = {
    websocketHandler { rh =>
      (upEnumerator: Enumerator[String], downIteratee: Iteratee[String, Unit]) =>
        // call the user function and holds the user's Iteratee (in) and Enumerator (out)
        val (upIteratee, downEnumerator) = f(rh)

        // pipes the msgs from the sockjs client to the user's Iteratee
        upEnumerator &> JsonCodec.JsonDecoder |>> upIteratee

          // pipes the msgs from the user's Enumerator to the sockjs client
          downEnumerator &> JsonCodec.JsonEncoder |>> downIteratee
    }
  }

  def websocketHandler(f: RequestHeader => (Enumerator[String], Iteratee[String, Unit]) => Unit)(implicit frameFormatter: WebSocket.FrameFormatter[String]) =
    play.api.mvc.WebSocket.async[String] { rh =>
      val pathList = rh.path.split("/").reverse
      val (transport, sessionId, serverId) = (pathList(0), pathList(1), pathList(2))
      val (upEnumerator, upChannel) = Concurrent.broadcast[String]
      val in = Iteratee.foreach[String](m => upChannel push m)
      val (out, outChannel) = Concurrent.broadcast[String]
      Future {
        val downIteratee = Iteratee.foreach[String] { userMsg => outChannel push s"a$userMsg" }
        Future { Thread sleep 100; outChannel push "o" }
        f(rh)(upEnumerator, downIteratee)
        (in, out)
      }
    }
}
