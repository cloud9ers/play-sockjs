package com.cloud9ers.play2.sockjs.transports

import com.cloud9ers.play2.sockjs.{ Session, SessionManager }
import play.api.libs.iteratee.{ Concurrent, Iteratee, Enumerator }
import akka.actor.{ Actor, ActorRef }
import play.api.mvc.{ RequestHeader, WebSocket }
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout

class WebsocketActor(channel: Concurrent.Channel[String], session: ActorRef) extends Actor {
  session ! Session.Dequeue
  def receive = {
    case Session.Message(m) => channel push m; session ! Session.Dequeue
  }
}

object WebSocketTransport extends Transport {
  /*
   * Websocket transport implementation
   */
  def websocket[String](f: RequestHeader => Future[(Iteratee[String, _], Enumerator[String])])(implicit frameFormatter: WebSocket.FrameFormatter[String]) =
    WebSocket.async[String] { rh =>
      import play.api.libs.concurrent.{ Promise => PlayPromise }
      val pathList = rh.path.split("/").reverse
      val (transport, sessionId, serverId) = (pathList(0), pathList(1), pathList(2))

      val p = f(rh)
      val (upEnumerator, upChannel) = Concurrent.broadcast[String]
      val upIteratee = Iteratee.foreach[String]{m => upChannel push m; println(s"upIteratee: $m")}

      val (downEnumerator, downChannel) = Concurrent.broadcast[String]
      (sessionManager ? SessionManager.GetOrCreateSession(sessionId))
        .map { case session: ActorRef => Iteratee.foreach[String](m => session ! Session.Enqueue(m.asInstanceOf[java.lang.String])) }
        .map(downIteratee => p.map(_._2 |>> downIteratee))

      p.map(upEnumerator |>> _._1)

      PlayPromise.pure(upIteratee, downEnumerator)
    }
}
