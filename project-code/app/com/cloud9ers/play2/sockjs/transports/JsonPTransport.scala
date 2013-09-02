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
import scala.util.{ Success, Failure }
import java.net.URLDecoder
import java.io.UnsupportedEncodingException
import play.api.libs.json.JsValue
import com.cloud9ers.play2.sockjs.StringEscapeUtils.escapeJavaScript

class JsonpPollingActor(promise: Promise[String], session: ActorRef) extends Actor {
  session ! Session.Dequeue
  def receive: Receive = {
    case Session.Message(m) =>
      promise success ((if (m == "o") "" else "a") + m); self ! PoisonPill //FIXIME: Dirty solution in JsonP for append 'a'
    case Session.HeartBeatFrame(h) => promise success h; self ! PoisonPill
  }
}

object JsonPTransport extends Transport {

  def jsonpPolling(sessionId: String)(implicit request: Request[AnyContent]) = Async(
    request.queryString.get("c").map { callback =>
      val promise = Promise[String]()
      (sessionManager ? SessionManager.GetOrCreateSession(sessionId))
        .map(session =>
          system.actorOf(Props(new JsonpPollingActor(promise, session.asInstanceOf[ActorRef])), s"xhr-polling.$sessionId"))
      promise.future.map(m =>
        Ok(s"""${callback.reduceLeft(_ + _)}("${escapeJavaScript(m)}");\r\n""") //callback(\\"m\\");\r\n //FIXME: skip '"'
          .withHeaders(
            CONTENT_TYPE -> "application/javascript;charset=UTF-8",
            CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
          .withHeaders(cors: _*))
    }.getOrElse(Future(InternalServerError("\"callback\" parameter required\n"))))

  def jsonpSend(sessionId: String, f: RequestHeader => (Enumerator[String], Iteratee[JsValue, Unit]) => Unit)(implicit request: Request[AnyContent]): Result =
    Async(
      request.contentType.map(_.toLowerCase match {
        case "application/x-www-form-urlencoded" =>
          request.body.asFormUrlEncoded.get("d") match {
            case Nil => Failure(new Exception("Payload expected."))
            case chars => Success(chars.reduceLeft(_ + _))
          }
        case "text/plain" =>
          request.body.asText match {
            case None => Failure(new Exception("Payload expected."))
            case Some(body) =>
              try Success(URLDecoder.decode(body, "UTF-8"))
              catch { case e: UnsupportedEncodingException => Failure(new Exception("No UTF-8!")) }
          }
        case _ => Failure(new Exception("Invalid Content-type"))

      }).map {
        case Success(message) =>
          (sessionManager ? SessionManager.GetSession(sessionId)).map {
            case None => NotFound
            case Some(ses: ActorRef) =>
              val (upEnumerator, upChannel) = Concurrent.broadcast[String]
              val downIteratee = Iteratee.foreach[JsValue](userMsg => ses ! Session.Enqueue(userMsg))
              f(request)(upEnumerator, downIteratee)
              upChannel push message
              Ok("ok")
                .withHeaders(
                  CONTENT_TYPE -> "text/plain; charset=UTF-8",
                  CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
                .withHeaders(cors: _*)
            case Failure(e) => InternalServerError(e.getMessage)
          }
      }.getOrElse(Future(InternalServerError("Invalid Content-Type"))))
}
