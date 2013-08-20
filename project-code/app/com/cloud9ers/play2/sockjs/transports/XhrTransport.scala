package com.cloud9ers.play2.sockjs.transports

import com.cloud9ers.play2.sockjs.{ SockJsPlugin, Session, SessionManager }
import play.api.mvc.{ RequestHeader, Request, AnyContent }
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

class XhrPollingActor(promise: Promise[String], session: ActorRef) extends Actor {
  session ! Session.Dequeue
  def receive: Receive = {
    case Session.Message(m) => promise success m; self ! PoisonPill
  }
}

class XhrStreamingActor(channel: Concurrent.Channel[Array[Byte]], session: ActorRef) extends Actor {
  session ! Session.Dequeue
  def receive: Receive = {
    case Session.Message(m) => channel push m.toArray.map(_.toByte); session ! Session.Dequeue
  }
}

object XhrTransport extends Transport {
  val H_BLOCK = ((for (i <- 0 to 2047) yield "h").toArray :+ "\n").reduceLeft(_ + _).toArray.map(_.toByte)

  def xhrPolling(sessionId: String)(implicit request: Request[AnyContent]) = {
    val promise = Promise[String]()
    (sessionManager ? SessionManager.GetOrCreateSession(sessionId))
      .map(session =>
        system.actorOf(Props(new XhrPollingActor(promise, session.asInstanceOf[ActorRef])), s"xhr-polling.$sessionId"))
    Async(promise.future.map{m =>
      println("xhr --> " + m) //FIXME: In firefox prints without "\n" and doesn't fire the message event in client
      Ok(m.toString)
        .withHeaders(
          CONTENT_TYPE -> "application/javascript;charset=UTF-8",
          CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
        .withHeaders(cors: _*)})
  }

  def xhrStreaming(sessionId: String)(implicit request: Request[AnyContent]) = {
    val (enum, channel) = Concurrent.broadcast[Array[Byte]]
    val result = Ok stream enum
    Future {
      Thread sleep 100
      channel push H_BLOCK
      (sessionManager ? SessionManager.GetOrCreateSession(sessionId))
        .map(session => system.actorOf(Props(new XhrStreamingActor(channel, session.asInstanceOf[ActorRef])), s"xhr-streaming.$sessionId"))
    }
    result
      .withHeaders(
        CONTENT_TYPE -> "application/javascript;charset=UTF-8",
        CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
      .withHeaders(cors: _*)
  }

  def xhrSend[A](sessionId: String, f: RequestHeader => (Enumerator[A], Iteratee[A, Unit]) => Unit)(implicit request: Request[AnyContent]): Result = {
    val (upEnumerator, upChannel) = Concurrent.broadcast[A]
    Async((sessionManager ? SessionManager.GetSession(sessionId))
      .map {
        case None => NotFound
        case Some(ses: ActorRef) =>
          val downIteratee = Iteratee.foreach[A](userMsg => ses ! Session.Enqueue(userMsg.asInstanceOf[String]))
          // calls the user function and passes the sockjs Enumerator/Iteratee
          f(request)(upEnumerator, downIteratee)
          val contentType = request.headers.get(CONTENT_TYPE).getOrElse(Transport.CONTENT_TYPE_PLAIN) //FIXME: sometimes it's application/xml
          // Parse all content types as Text
          val message: String = request.body.asRaw
            .flatMap(r => r.asBytes(maxLength)
                //FIXME: @amal: message frame should be added in the downstream not in the upstream such as xhr or xhr_streaming .. etc
              .map(b => new String(SockJsFrames.messageFrame(b, true).toArray, request.charset.getOrElse("utf-8"))))
            .getOrElse(request.body.asText.getOrElse("")) //FIXME: In firefox request.body.asRaw returns None, works fine with chrome
            //TODO: find a more decent way to read the body
          upChannel push message.asInstanceOf[A]
          NoContent
            .withHeaders(
              CONTENT_TYPE -> contentType,
              CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
            .withHeaders(cors: _*)
      })
  }
}
