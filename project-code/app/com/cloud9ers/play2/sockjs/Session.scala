package com.cloud9ers.play2.sockjs

import play.api.libs.iteratee.{ Concurrent, Iteratee, Enumerator, Input }
import scala.concurrent.{ Promise, Future }
import akka.actor.{ Actor, ActorRef, Props, Cancellable, PoisonPill }
import akka.event.Logging
import scala.concurrent.duration._
import play.api.libs.json.{ JsValue, JsArray }
import play.api.mvc.{ Request, AnyContent, RequestHeader }
import play.api.libs.json.JsArray

/**
 * Session class to queue messages over multiple connection like xhr and xhr_send
 */
class Session(handler: RequestHeader => Future[(Iteratee[JsValue, _], Enumerator[JsValue])], request: Request[AnyContent], heartBeatPeriod: Long) extends Actor {
  val logger = Logging(context.system, this)
  val pendingWrites = scala.collection.mutable.Queue[JsValue]()
  var transportListener: Option[ActorRef] = None
  var heartBeatTask: Option[Cancellable] = None
  var timer: Option[Cancellable] = None
  //TODO: Max Queue Size

  implicit val executionContext = context.system.dispatcher
  val p = handler(request)
  val upIteratee = Iteratee.flatten(p.map(_._1))
  val downEnumerator = Enumerator.flatten(p.map(_._2))

  val (upEnumerator, upChannel) = Concurrent.broadcast[JsValue]
  val downIteratee = Iteratee.foreach[JsValue](msg => self ! Session.Write(msg))

  upEnumerator |>> upIteratee
  downEnumerator |>> downIteratee

  setTimer() //if no transportListener is attached for 6 seconds, then consider the client is dead and close the session

  def receive = connecting orElse timeout

  def timeout: Receive = {
    case Session.Timeout => doClose()
  }

  def connecting: Receive = {
    case Session.Register =>
      sender ! Session.OpenMessage
      context become (open orElse timeout)
      startHeartBeat()
  }

  def open: Receive = {
    case Session.Register => register(sender)
    case Session.Send(msgs) => handleMessages(msgs)
    case Session.Write(msg) => write(msg)
    case h @ Session.HeartBeat => for (tl <- transportListener) tl ! h
    case c: Session.Close => close(c)
  }

  def closed: Receive = {
    case Session.Register => sender ! Session.Close(3000, "Go away!")
  }

  def register(transport: ActorRef) {
    if (transportListener.isEmpty || transportListener.get == sender) {
      setTimer()
      transportListener = Some(sender)
      if (!pendingWrites.isEmpty) writePendingMessages(sender)
    } else {
      sender ! Session.Close(2010, "Another connection still open")
      logger.debug(s"Refuse transport, Another connection still open")
    }
  }

  def handleMessages(msgs: JsValue) {
    msgs match {
      case msg: JsArray => msg.value.foreach(m => upChannel push m)
      case msg: JsValue => upChannel push msg
    }
  }

  def write(msg: JsValue) {
    pendingWrites += msg
    for (tl <- transportListener) writePendingMessages(tl)
  }

  def writePendingMessages(tl: ActorRef) {
    val ms = pendingWrites.dequeueAll(_ => true).toList
    tl ! Session.Message("a" + JsonCodec.encodeJson(JsArray(ms)))
    resetListener()
    logger.debug(s"writePendingMessages: tl: $tl, pendingWrites: pendingWrites")
  }

  def close(closeMsg: Session.Close) {
    logger.debug(s"Session is closing, code: ${closeMsg.code}, reason: ${closeMsg.reason}")
    context become (closed orElse timeout)
    upChannel push Input.EOF
    for (tl <- transportListener) tl ! closeMsg
  }

  def resetListener() {
    transportListener = None
    setTimer()
  }

  def setTimer() {
    import scala.language.postfixOps
    for (t <- timer) t.cancel()
    timer = Some(context.system.scheduler.scheduleOnce(6 seconds, self, Session.Timeout)) // The only case where the session is actually closed
  }

  def startHeartBeat() { //TODO: heartbeat need test
    import scala.language.postfixOps
    heartBeatTask = Some(context.system.scheduler.schedule(heartBeatPeriod milliseconds, heartBeatPeriod milliseconds, self, Session.HeartBeat))
  }

  def doClose() {
    logger.debug("Session is going to shutdown")
    self ! PoisonPill
    for (tl <- transportListener) tl ! PoisonPill
    for (h <- heartBeatTask) h.cancel()
    for (t <- timer) t.cancel()
  }
}

object Session {
  case class Send(msg: JsValue) // JSClient send
  case object Register // register the transport actor and holds for the next message to write to the JSClient
  case object OpenMessage
  case class Message(msg: String)
  case class Close(code: Int, reason: String)
  case object HeartBeat
  case class Write(msg: JsValue)
  object Timeout
}

//TODO: Organization: move SessionManager to a separate file
class SessionManager(heartBeatPeriod: Long) extends Actor {
  type Handler = RequestHeader => Future[(Iteratee[JsValue, _], Enumerator[JsValue])]
  def getSession(sessionId: String): Option[ActorRef] = context.child(sessionId)
  def createSession(sessionId: String, handler: Handler, request: Request[AnyContent]): ActorRef =
    context.actorOf(Props(new Session(handler, request, heartBeatPeriod)), sessionId)
  def receive = {
    case SessionManager.GetSession(sessionId) =>
      sender ! getSession(sessionId)
    case SessionManager.GetOrCreateSession(sessionId, handler, request) =>
      sender ! getSession(sessionId).orElse(Some(createSession(sessionId, handler, request)))
  }
}

object SessionManager {
  type Handler = RequestHeader => Future[(Iteratee[JsValue, _], Enumerator[JsValue])]
  case class GetOrCreateSession(sessionId: String, handler: Handler, request: Request[AnyContent])
  case class GetSession(sessionId: String)
}