package com.cloud9ers.play2.sockjs.transports

import com.cloud9ers.play2.sockjs.{ SockJsPlugin, Session, SessionManager }
import play.api.mvc.{ RequestHeader, Request }
import play.api.libs.iteratee.{ Iteratee, Enumerator }
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.current
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout
import play.api.libs.iteratee.Concurrent
import scala.concurrent.{ Promise, Future }
import akka.actor.{ Actor, ActorRef, Props, PoisonPill }
import com.cloud9ers.play2.sockjs.SockJsFrames
import play.api.mvc.Result
import play.api.mvc.AnyContent
import play.api.libs.json.JsValue

class XhrPollingActor(promise: Promise[String], session: ActorRef) extends Actor {
  session ! Session.Dequeue
  def receive: Receive = {
    case Session.Message(m) => promise success ((if (m == "o") m else "a" + m ) + "\n"); self ! PoisonPill //TODO: 'a' dirty solution 
    case Session.HeartBeatFrame(h) => promise success h; self ! PoisonPill
  }
}

class XhrStreamingActor(channel: Concurrent.Channel[Array[Byte]], session: ActorRef) extends Actor {
  override def preStart() {
    import scala.language.postfixOps
    context.system.scheduler.scheduleOnce(100 milliseconds) {
      channel push SockJsFrames.XHR_STREAM_H_BLOCK
      session ! Session.Dequeue
    }
  }
  def receive: Receive = {
    case Session.Message(m) =>
      channel push (if (m == "o") m + "\n" else "a" + m + "\n").toArray.map(_.toByte); session ! Session.Dequeue //TODO: 'a' dirty solution 
    case Session.HeartBeatFrame(h) => channel push h.toArray.map(_.toByte); session ! Session.Dequeue
  }
}

object XhrTransport extends Transport {

  def xhrPolling(sessionId: String)(implicit request: Request[AnyContent]) = Async {
    val promise = Promise[String]()
    (sessionManager ? SessionManager.GetOrCreateSession(sessionId))
      .map(session =>
        system.actorOf(Props(new XhrPollingActor(promise, session.asInstanceOf[ActorRef])), s"xhr-polling.$sessionId"))
    promise.future.map { m =>
      println("xhr --> " + m) //FIXME: In firefox prints without "\n" and doesn't fire the message event in client
      Ok(m.toString)
        .withHeaders(
          CONTENT_TYPE -> "application/javascript;charset=UTF-8",
          CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
        .withHeaders(cors: _*)
    }
  }

  def xhrStreaming(sessionId: String)(implicit request: Request[AnyContent]) =
    Async((sessionManager ? SessionManager.GetOrCreateSession(sessionId)).map { session =>
      val (enum, channel) = Concurrent.broadcast[Array[Byte]]
      val xhrStreamingActor = system.actorOf(Props(new XhrStreamingActor(channel, session.asInstanceOf[ActorRef])), s"xhr-streaming.$sessionId")
      (Ok stream enum.onDoneEnumerating(xhrStreamingActor ! PoisonPill))
        .withHeaders(
          CONTENT_TYPE -> "application/javascript;charset=UTF-8",
          CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
        .withHeaders(cors: _*)
    })

  def xhrSend(sessionId: String, f: RequestHeader => (Enumerator[String], Iteratee[JsValue, Unit]) => Unit)(implicit request: Request[AnyContent]): Result =
    Async((sessionManager ? SessionManager.GetSession(sessionId)).map {
      case None => NotFound
      case Some(ses: ActorRef) =>
        val (upEnumerator, upChannel) = Concurrent.broadcast[String]
        val downIteratee = Iteratee.foreach[JsValue](userMsg => ses ! Session.Enqueue(userMsg))
        // calls the user function and passes the sockjs Enumerator/Iteratee
        f(request)(upEnumerator, downIteratee)
        val contentType = request.headers.get(CONTENT_TYPE).getOrElse(Transport.CONTENT_TYPE_PLAIN) //FIXME: sometimes it's application/xml
        // Parse all content types as Text
        val message: String = request.body.asRaw.flatMap(r => r.asBytes(maxLength).map(b => new String(b))).getOrElse(request.body.asText.getOrElse(""))
        upChannel push message
        NoContent
          .withHeaders(
            CONTENT_TYPE -> contentType,
            CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
          .withHeaders(cors: _*)
    })
}
