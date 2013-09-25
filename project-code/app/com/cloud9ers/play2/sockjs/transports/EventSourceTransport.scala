package com.cloud9ers.play2.sockjs.transports

import scala.concurrent.duration.DurationInt

import com.cloud9ers.play2.sockjs.{ Session, SockJsPlugin }

import akka.actor.{ ActorRef, PoisonPill, Props, actorRef2Scala }
import play.api.Play.current
import play.api.libs.iteratee.Concurrent
import play.api.mvc.{ AnyContent, Request }

class EventSourceActor(channel: Concurrent.Channel[String], session: ActorRef, maxBytesStreaming: Int)
  extends TransportActor(session, Transport.EVENT_SOURCE) {
  var bytesSent = 0

  override def preStart() {
    import scala.language.postfixOps
    context.system.scheduler.scheduleOnce(100 milliseconds) {
      channel push "\r\n"
      session ! Session.Register
    }
  }

  def sendFrame(m: String): Boolean = {
    val msg = s"data: $m\r\n\r\n"
    bytesSent += msg.length
    println("EventSource ::<<<<<<<<< " + msg)
    channel push msg
    if (bytesSent < maxBytesStreaming)
      true
    else {
      channel.eofAndEnd()
      false
    }
  }
}

object EventSourceTransport extends TransportController {
  def eventSource(sessionId: String, session: ActorRef)(implicit request: Request[AnyContent]) = {
    val (enum, channel) = Concurrent.broadcast[String]
    // FIXME: choose eventSource actor name
    val eventSourceActor = system.actorOf(Props(new EventSourceActor(channel, session, maxBytesStreaming)), s"eventsource.$sessionId.$randomNumber")
    (Ok stream enum.onDoneEnumerating {
      eventSourceActor ! PoisonPill
      println("BOOOOOOOOOOOOOM!!!!")
    })
      .withHeaders(
        CONTENT_TYPE -> "text/event-stream;charset=UTF-8",
        CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
      .withHeaders(cors: _*)
  }
}
