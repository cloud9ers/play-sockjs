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

@deprecated("","")
class WebsocketActor[A](channel: Concurrent.Channel[A], session: ActorRef) extends Actor {
  session ! Session.Dequeue
  def receive = {
    case Session.Message(m) => channel push m.asInstanceOf[A]; session ! Session.Dequeue
  }
}

object WebSocketTransport extends Transport {
  /*
   * Websocket transport implementation
   */
  def websocket[A](f: RequestHeader => Future[(Iteratee[A, _], Enumerator[A])])(implicit frameFormatter: WebSocket.FrameFormatter[A]) =
    using[A] { rh =>
      val p = f(rh)
      val upIteratee = Iteratee.flatten(p.map(_._1))
      val downEnumerator = Enumerator.flatten(p.map(_._2))

      (upIteratee, downEnumerator)
    }
  /**
   * returns Handler and passes a function that pipes the user Enumerator to the sockjs Iteratee
   * and pipes the sockjs Enumerator to the user Iteratee
   */
  def using[A](f: RequestHeader => (Iteratee[A, _], Enumerator[A]))(implicit frameFormatter: WebSocket.FrameFormatter[A]): play.api.mvc.WebSocket[A] = {
    websocketHandler[A] { rh =>
      (upEnumerator: Enumerator[A], downIteratee: Iteratee[A, Unit]) =>
        // call the user function and holds the user's Iteratee (in) and Enumerator (out)
        val (upIteratee, downEnumerator) = f(rh)

        // pipes the msgs from the sockjs client to the user's Iteratee
        upEnumerator |>> upIteratee

        // pipes the msgs from the user's Enumerator to the sockjs client
        downEnumerator |>> downIteratee
    }
  }

  def websocketHandler[A](f: RequestHeader => (Enumerator[A], Iteratee[A, Unit]) => Unit)(implicit frameFormatter: WebSocket.FrameFormatter[A]) =
    play.api.mvc.WebSocket.async[A] { rh =>
      val pathList = rh.path.split("/").reverse
      val (transport, sessionId, serverId) = (pathList(0), pathList(1), pathList(2))
      val (upEnumerator, upChannel) = Concurrent.broadcast[A]
      val in = Iteratee.foreach[A](m => upChannel push m)

      val (out, outChannel) = Concurrent.broadcast[A]

      // Use of session actor
      //      (sessionManager ? SessionManager.GetOrCreateSession(sessionId))
      //        .map {
      //          case session: ActorRef =>
      //            val downIteratee = Iteratee.foreach[A] { userMsg => println(userMsg); session ! Session.Enqueue(userMsg.asInstanceOf[String]) } //enqueue
      //            Future { Thread sleep 100; system.actorOf(Props(new WebsocketActor(outChannel, session)), s"websocket-$sessionId") } // dequeue
      //            f(rh)(upEnumerator, downIteratee)
      //            (in, out)
      //        }

      Future {
        val downIteratee = Iteratee.foreach[A] { userMsg => outChannel push s"a$userMsg".asInstanceOf[A] }
        Future { Thread sleep 100; outChannel push "o".asInstanceOf[A] }
        f(rh)(upEnumerator, downIteratee)
        (in, out)
      }
    }
}
