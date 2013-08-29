package com.cloud9ers.play2.sockjs

import play.api.libs.iteratee.{ Concurrent, Input, Iteratee, Cont, Done }
import scala.concurrent.{ Promise, Future }
import akka.actor.{ Actor, ActorRef, Props, Cancellable, PoisonPill }
import akka.event.Logging
import scala.concurrent.duration._

/**
 * Session class to queue messages over multiple connection like xhr and xhr_send
 */
class Session(heartBeatPeriod: Long) extends Actor {
  private[this] val logger = Logging(context.system, this)
  private[this] val queue = scala.collection.mutable.Queue[String]()
  private[this] var listeners = List[ActorRef]()
  private[this] var heartBeatTask: Option[Cancellable] = None

  def encodeJson(ms: List[String]) = ms.reduceLeft(_ + _) //TODO: Should be static (in a singleton object)

  def receive = connecting

  def connecting: Receive = {
    case Session.Dequeue =>
      logger.debug("dequeue OPEN FRAME")
      sender ! Session.Message(SockJsFrames.OPEN_FRAME)
      context.become(open)
//      startHeartBeat() // start periodic task: self ! Enqueue(hearbeat), heartbeat period
  }

  def open: Receive = {
    case Session.Enqueue(msg: String) =>
      queue += msg
      logger.debug(s"session Enqueue: msg: $msg, ms: $queue, listeners: ${listeners}")
      if (!listeners.isEmpty) self ! Session.Dequeue

    case Session.Dequeue =>
      listeners = sender :: listeners
      val ms = queue.dequeueAll(_ => true).toList
      logger.debug(s"Session dequeue: ms: ${ms}, queue: $queue, listeners: ${listeners}")
      if (!ms.isEmpty) {
        listeners.foreach(sender => sender ! Session.Message(encodeJson(ms)))
        listeners = Nil
      }

    case Session.SendHeartBeat =>
      listeners.foreach(sender => sender ! Session.HeartBeatFrame(SockJsFrames.HEARTBEAT_FRAME))
      listeners = Nil
      //TODO: heart beat: kill the actor if no transport actor sent ack on the previous heart beat
  }

  def startHeartBeat() = { //TODO: heartbeat need test
    implicit val executionContext = context.system.dispatcher
    import scala.language.postfixOps
    heartBeatTask = Some(context.system.scheduler.schedule(200 milliseconds, heartBeatPeriod milliseconds, self, Session.SendHeartBeat))
  }

  override def postStop() = {
    heartBeatTask.map(h => h.cancel())
  }
}

object Session {
  case class Enqueue(msg: String)
  case object Dequeue
  case class Message(msg: String)
  case object SendHeartBeat
  case class HeartBeatFrame(h: String)
}

class SessionManager(heartBeatPeriod: Long) extends Actor {
  def getSession(sessionId: String): Option[ActorRef] = context.child(sessionId)
  def createSession(sessionId: String): ActorRef = context.actorOf(Props(new Session(heartBeatPeriod)), sessionId)
  def receive = {
    case SessionManager.GetSession(sessionId) =>
      sender ! getSession(sessionId)
    case SessionManager.GetOrCreateSession(sessionId) =>
      sender ! getSession(sessionId).getOrElse(createSession(sessionId))
  }
}

object SessionManager {
  case class GetOrCreateSession(sessionId: String)
  case class GetSession(sessionId: String)
}