package com.cloud9ers.play2.sockjs.transports

import scala.concurrent.duration.DurationInt

import com.cloud9ers.play2.sockjs.{Session, SockJs, SockJsFrames}

import akka.actor.{Actor, ActorRef, Cancellable, PoisonPill, Props, actorRef2Scala}
import akka.event.Logging
import play.api.mvc.Controller

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

abstract class TransportActor(session: ActorRef, kind: String = "") extends Actor {
  import scala.language.postfixOps
  implicit val executionContext = context.system.dispatcher

  val logger = Logging(context.system, this)
  val heartBeatTimeout = 35 seconds
  var heartBeatTask: Option[Cancellable] = None

  setTimer()

  /**
   * returns Boolean determines if the transport is still ready for sending more messages
   */
  def sendFrame(m: String): Boolean

  def receive: Receive = {
    case Session.OpenMessage =>
      if (sendFrame(SockJsFrames.OPEN_FRAME)) session ! Session.Register
      else self ! PoisonPill

    case Session.Message(m) =>
      setTimer()
      if (sendFrame(m)) session ! Session.Register
      else self ! PoisonPill

    case Session.HeartBeat =>
      setTimer()
      if (sendFrame(SockJsFrames.HEARTBEAT_FRAME)) session ! Session.Register
      else self ! PoisonPill

    case Session.Close(code, reason) =>
      sendFrame(SockJsFrames.closingFrame(code, reason))
      self ! PoisonPill
  }

  def setTimer() {
    for (h <- heartBeatTask) h.cancel()
    heartBeatTask = Some(context.system.scheduler.scheduleOnce(heartBeatTimeout, self, PoisonPill)) //set timer
  }

  override def postStop() {
    println(kind + " BOOOOOOOOOOM!")
  }
}

class TransportController extends Controller with SockJs {
  /*
   * Implements few methods that session expects to see in each transport
   */
  def createTransportActor(sessionId: String, transport: String, actorObject: TransportActor) =
    system.actorOf(Props(actorObject), s"eventsource.$sessionId.$randomNumber")
}
