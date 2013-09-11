package com.cloud9ers.play2.sockjs

import play.api.libs.iteratee.{ Concurrent, Iteratee, Enumerator }
import scala.concurrent.{ Promise, Future }
import akka.actor.{ Actor, ActorRef, Props, Cancellable, PoisonPill }
import akka.event.Logging
import scala.concurrent.duration._
import play.api.libs.json.{ JsValue, JsArray }
import play.api.mvc.{ Request, AnyContent, RequestHeader }
import scala.concurrent.duration._
import akka.util.Timeout
import play.api.libs.json.JsArray

/**
 * Session class to queue messages over multiple connection like xhr and xhr_send
 */
class Session(handler: RequestHeader => Future[(Iteratee[JsValue, _], Enumerator[JsValue])], request: Request[AnyContent], heartBeatPeriod: Long) extends Actor {
  val logger = Logging(context.system, this)
  var heartBeatTask: Option[Cancellable] = None
  val queue = context.actorOf(Props(new MsgQueue()), "queue")
  var close = Session.Close(3000, "Go away!")

  implicit val timeout = Timeout(5.seconds)
  implicit val executionContext = context.system.dispatcher

  val p = handler(request) //FIXME: will be called on actor restart
  val upIteratee = Iteratee.flatten(p.map(_._1))
  val downEnumerator = Enumerator.flatten(p.map(_._2))

  val (upEnumerator, upChannel) = Concurrent.broadcast[JsValue]
  val downIteratee = Iteratee.foreach[JsValue](msg => queue ! MsgQueue.EnqueueOrWrite(msg))

  upEnumerator |>> upIteratee
  downEnumerator |>> downIteratee

  def receive = connecting

  def connecting: Receive = {
    case Session.Receive =>
      sender ! Session.OpenMessage
      context become open
      startHeartBeat() // start periodic task: self ! Enqueue(hearbeat), heartbeat period

  }

  def open: Receive = {
    case Session.Send(msg) =>
      println(s"Session.Send: --->>> $msg")
      msg match {
        case msg: JsArray => msg.value.foreach(m => upChannel push m)
        case msg: JsValue => upChannel push msg
      }
    
    case Session.Receive =>
      queue ! MsgQueue.DequeueOrBind(sender)
    
    case Session.HeartBeatFrame => //TODO: re-implement heart beat
    
    case close @ Session.Close(code, text) =>
      context become closing
      this.close = close
      queue ! close
      upChannel.eofAndEnd()
  }
  
  def closing: Receive = {
    case Session.Receive => sender ! close
    case "writerClosed" => self ! PoisonPill
  }

  def startHeartBeat() = { //TODO: heartbeat need test
    implicit val executionContext = context.system.dispatcher
    import scala.language.postfixOps
    heartBeatTask = Some(context.system.scheduler.schedule(500 milliseconds, heartBeatPeriod milliseconds, self, Session.SendHeartBeat))
  }

  override def postStop() = {
    heartBeatTask.map(h => h.cancel())
  }
}

object Session {
  case class Send(msg: JsValue) // e.g. XhrSend -> Session
  case object Receive // e.g. XhrPoll -> Session means XhrPoll is ready to write messages to the client 
  case object OpenMessage
  case class Message(msg: String)
  case object SendHeartBeat
  case class HeartBeatFrame(h: String)
  case class Close(code: Int, reason: String)
}

class MsgQueue extends Actor {
  val queue = scala.collection.mutable.Queue[JsValue]()
  var writer: Option[ActorRef] = None

  def write(w: ActorRef) {
    val ms = queue.dequeueAll(_ => true).toList
    w ! Session.Message(JsonCodec.encodeJson(JsArray(ms)))
    writer = None
  }

  def receive = {
    case MsgQueue.EnqueueOrWrite(msg) => queue += msg; for (w <- writer) write(w)
    case MsgQueue.DequeueOrBind(w) => writer = Some(w); if (!queue.isEmpty) write(w)
    case close @ Session.Close(code, reason) =>  for (w <- writer) w ! close; self ! PoisonPill
  }
}
object MsgQueue {
  case class EnqueueOrWrite(msg: JsValue)
  case class DequeueOrBind(listener: ActorRef)
}

class SessionManager(heartBeatPeriod: Long) extends Actor {
  def getSession(sessionId: String): Option[ActorRef] = context.child(sessionId)
  def createSession(sessionId: String, handler: RequestHeader => Future[(Iteratee[JsValue, _], Enumerator[JsValue])], request: Request[AnyContent]): ActorRef =
    context.actorOf(Props(new Session(handler, request, heartBeatPeriod)), sessionId)
  def receive = {
    case SessionManager.GetSession(sessionId) =>
      sender ! getSession(sessionId)
    case SessionManager.GetOrCreateSession(sessionId, handler, request) =>
      sender ! getSession(sessionId).orElse(Some(createSession(sessionId, handler, request)))
  }
}

object SessionManager {
  case class GetOrCreateSession(sessionId: String, handler: RequestHeader => Future[(Iteratee[JsValue, _], Enumerator[JsValue])], request: Request[AnyContent])
  case class GetSession(sessionId: String)
}