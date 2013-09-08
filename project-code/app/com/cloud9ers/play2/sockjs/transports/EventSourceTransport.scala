package com.cloud9ers.play2.sockjs.transports

import com.cloud9ers.play2.sockjs.{ SockJsPlugin, Session, SessionManager }
import play.api.mvc.{ RequestHeader, Request }
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
import play.api.libs.EventSource
import play.api.libs.iteratee.Input
import akka.event.Logging
import play.api.mvc.AnyContent

class EventSourceActor(channel: Concurrent.Channel[String], session: ActorRef, maxBytesStreaming: Int) extends Actor {
  private[this] val logger = Logging(context.system, this)

  override def preStart() {
    import scala.language.postfixOps
    context.system.scheduler.scheduleOnce(100 milliseconds) {
      channel push "\r\n"
      session ! Session.Receive
    }
  }

  def receive: Receive = {
    case Session.OpenMessage =>
      channel push s"data: ${SockJsFrames.OPEN_FRAME}\r\n\r\n"; session ! Session.Receive

    case Session.Message(m) =>
      val msg = s"data: a$m\r\n\r\n"
      println("EventSource ::<<<<<<<<< " + msg)
      channel push msg
      if (m.length < maxBytesStreaming)
        session ! Session.Receive
      else {
        channel.eofAndEnd()
        self ! PoisonPill
      }

    case Session.HeartBeatFrame(h) => channel push s"data: $h\r\n\r\n"; session ! Session.Receive
  }
}

object EventSourceTransport extends Transport {
  val maxBytesStreaming = SockJsPlugin.current.maxBytesStreaming

  def eventSource(sessionId: String, session: ActorRef)(implicit request: Request[AnyContent]) = {
    val (enum, channel) = Concurrent.broadcast[String]
    // FIXME: choose eventSource actor name
    val eventSourceActor = system.actorOf(Props(new EventSourceActor(channel, session, maxBytesStreaming)), s"eventsource.$sessionId")
    (Ok stream enum.onDoneEnumerating(eventSourceActor ! PoisonPill))
      .withHeaders(
        CONTENT_TYPE -> "text/event-stream;charset=UTF-8",
        CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
      .withHeaders(cors: _*)
  }
}
