package com.cloud9ers.play2.sockjs

import play.api.libs.iteratee.{ Concurrent, Input, Iteratee, Cont, Done }
import scala.concurrent.{ Promise, Future }
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props

/**
 * Session class to queue messages over multiple connection like xhr and xhr_send
 */
class Session extends Actor {
  val queue = scala.collection.mutable.Queue[String]()
  var listeners = List[ActorRef]()
  def encodeJson(ms: List[String]) = "a" + ms.reduceLeft(_ + _) + "\n" //TODO write the sockjs encoding function

  def receive = connecting
  def connecting: Receive = {
    case Session.Dequeue =>
      sender ! SockJsFrames.OPEN_FRAME + "\n"
      context.become(open)
  }
  def open: Receive = {
    case Session.Enqueue(msg: String) =>
      queue += msg
      println(s"session Enqueue: msg: $msg, ms: $queue, listeners: ${listeners}")
      if (!listeners.isEmpty) self ! Session.Dequeue

    case Session.Dequeue =>
      listeners = sender :: listeners
      val ms = queue.dequeueAll(_ => true).toList
      println(s"Session dequeue: ms: ${ms}, queue: $queue, listeners: ${listeners}")
      if (!ms.isEmpty) {
        listeners.foreach(sender => sender ! encodeJson(ms))
        listeners = Nil
      }
  }
}

object Session {
  case class Enqueue(msg: String)
  case object Dequeue
}

class SessionManager extends Actor {
  def getSession(sessionId: String): Option[ActorRef] = context.child(sessionId)
  def createSession(sessionId: String): ActorRef = context.actorOf(Props[Session], sessionId)
  def receive = {
    case SessionManager.GetOrCreateSession(sessionId) =>
      sender ! getSession(sessionId).getOrElse(createSession(sessionId))
  }
}

object SessionManager {
  case class GetOrCreateSession(sessionId: String)
}