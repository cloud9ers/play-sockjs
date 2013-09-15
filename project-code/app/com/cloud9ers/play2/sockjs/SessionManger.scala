package com.cloud9ers.play2.sockjs

import scala.concurrent.Future

import akka.actor.{Actor, ActorRef, Props, actorRef2Scala}
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.JsValue
import play.api.mvc.{AnyContent, Request, RequestHeader}

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