package com.cloud9ers.play2.sockjs

import scala.concurrent.Future
import scala.concurrent.duration.{ DurationInt, DurationLong }

import akka.actor.{ Actor, ActorRef, Cancellable, PoisonPill, actorRef2Scala }
import akka.event.Logging
import play.api.libs.iteratee.{ Concurrent, Enumerator, Input, Iteratee }
import play.api.libs.json.{ JsArray, JsValue }
import play.api.mvc.{ AnyContent, Request, RequestHeader }

/**
 * Session class to queue messages over multiple connection like xhr and xhr_send
 */
class Session(handler: RequestHeader ⇒ Future[(Iteratee[JsValue, _], Enumerator[JsValue])], request: Request[AnyContent], heartBeatPeriod: Long) extends Actor {
  val logger = Logging(context.system, this)
  val pendingWrites = scala.collection.mutable.Queue[JsValue]()
  var transportListener: Option[ActorRef] = None
  var heartBeatTask: Option[Cancellable] = None
  var timer: Option[Cancellable] = None
  var openWriten = false
  var closeMessage = Session.Close(3000, "Go away!")
  //TODO: Max Queue Size

  implicit val executionContext = context.system.dispatcher
  val p = handler(request)
  val upIteratee = Iteratee.flatten(p.map(_._1))
  val downEnumerator = Enumerator.flatten(p.map(_._2))

  val (upEnumerator, upChannel) = Concurrent.broadcast[JsValue]
  val downIteratee = Iteratee.foreach[JsValue](msg ⇒ self ! Session.Write(msg))

  upEnumerator |>> upIteratee
  downEnumerator |>> downIteratee

  setTimer() //if no transportListener is attached for 6 seconds, then consider the client is dead and close the session

  def receive = connecting orElse timeout

  def connecting: Receive = {
    case Session.Register ⇒
      logger.debug(s"state: CONNECTING, sender: $sender, message: ${Session.Register}")
      (register andThen sendOpenMessage andThen resetListener andThen becomeOpen)(sender)

    case c: Session.Close ⇒
      logger.debug(s"state: CONNECTING, sender: $sender, message: $c")
      becomeClosed.apply()
  }

  def timeout: Receive = {
    case Session.Timeout ⇒ doClose()
  }

  def open: Receive = {
    case Session.Register ⇒
      logger.debug(s"state: OPEN, sender: $sender, message: ${Session.Register}")
      register(sender)
      if (!pendingWrites.isEmpty) (writePendingMessages andThen resetListener)(sender)

    case s @ Session.Send(msgs) ⇒
      logger.debug(s"state: OPEN, sender: $sender, message: $s")
      handleMessages(msgs)

    case w @ Session.Write(msg) ⇒
      logger.debug(s"state: OPEN, sender: $sender, message: $w")
      enqueue(msg)
      transportListener map (writePendingMessages andThen resetListener)

    case Session.HeartBeat ⇒
      logger.debug(s"state: OPEN, sender: $sender, message: ${Session.HeartBeat}")
      transportListener map (sendHeartBeatMessage andThen resetListener)

    case c: Session.Close ⇒
      logger.debug(s"state: OPEN, sender: $sender, message: $c")
      this.closeMessage = c
      transportListener map (sendCloseMessage andThen resetListener andThen becomeClosed) getOrElse becomeClosed
  }

  def closed: Receive = {
    case Session.Register if !openWriten ⇒
      logger.debug(s"state: OPEN, sender: $sender, message: ${Session.Register}, openWriten: $openWriten")
      (register andThen sendOpenMessage andThen resetListener)(sender)

    case Session.Register ⇒
      logger.debug(s"state: OPEN, sender: $sender, message: ${Session.Register}, openWriten: $openWriten")
      (register andThen sendCloseMessage andThen resetListener)(sender)
  }

  val register = (tl: ActorRef) ⇒ {
    if (transportListener.isEmpty || transportListener.get == tl) {
      transportListener = Some(tl)
    } else {
      tl ! Session.Close(2010, "Another connection still open")
      logger.debug(s"Refuse transport, Another connection still open")
    }
    tl
  }: ActorRef

  val sendOpenMessage = (tl: ActorRef) ⇒ {
    tl ! Session.OpenMessage
    openWriten = true
    tl
  }: ActorRef

  val resetListener = (tl: ActorRef) ⇒ {
    //TODO: should you notify the tl?
    transportListener = None
    setTimer()
  }: Unit

  val becomeOpen = (_: Unit) ⇒ {
    context become (open orElse timeout)
    startHeartBeat()
  }: Unit

  val becomeClosed = (_: Unit) ⇒ {
    context become (closed orElse timeout)
    upChannel.eofAndEnd()
  }: Unit

  val writePendingMessages = (tl: ActorRef) ⇒ { //TODO: unify writes
    logger.debug(s"writePendingMessages: tl: $tl, pendingWrites: $pendingWrites")
    tl ! Session.Message("a" + JsonCodec.encodeJson(JsArray(pendingWrites.dequeueAll(_ ⇒ true).toList)))
    tl
  }: ActorRef

  val sendCloseMessage = (tl: ActorRef) ⇒ {
    tl ! closeMessage; tl
  }: ActorRef

  val sendHeartBeatMessage = (tl: ActorRef) ⇒ {
    tl ! Session.HeartBeat
    tl
  }: ActorRef

  def enqueue(msg: JsValue) {
    //TODO: check the queue size
    logger.debug(s"enqueue msg: $msg, pendingWrites: $pendingWrites")
    pendingWrites += msg
  }

  def handleMessages(msgs: JsValue) {
    msgs match {
      case msg: JsArray ⇒ msg.value.foreach(m ⇒ upChannel push m)
      case msg: JsValue ⇒ upChannel push msg
    }
  }

  def setTimer() {
    import scala.language.postfixOps
    for (t ← timer) t.cancel()
    timer = Some(context.system.scheduler.scheduleOnce(6 seconds, self, Session.Timeout)) // The only case where the session is actually closed
  }

  def startHeartBeat() { //TODO: heartbeat need test
    import scala.language.postfixOps
    heartBeatTask = Some(context.system.scheduler.schedule(heartBeatPeriod milliseconds, heartBeatPeriod milliseconds, self, Session.HeartBeat))
  }

  def doClose() {
    logger.debug("Session is going to shutdown")
    self ! PoisonPill
    for (tl ← transportListener) tl ! PoisonPill
    for (h ← heartBeatTask) h.cancel()
    for (t ← timer) t.cancel()
  }
}

object Session {
  case class Send(msg: JsValue) // JSClient send
  case object Register // register the transport actor and holds for the next message to write to the JSClient
  case object Unregister
  case object OpenMessage
  case class Message(msg: String)
  case class Close(code: Int, reason: String)
  case object HeartBeat
  case class Write(msg: JsValue)
  object Timeout
}
