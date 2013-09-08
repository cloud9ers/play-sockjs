package com.cloud9ers.play2.sockjs.transports

import com.cloud9ers.play2.sockjs.{ SockJsPlugin, Session, SessionManager }
import play.api.mvc.{ RequestHeader, Request, AnyContent }
import play.api.libs.iteratee.{ Iteratee, Enumerator }
import play.api.libs.concurrent.Execution.Implicits._
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
import com.cloud9ers.play2.sockjs.JsonCodec
import org.codehaus.jackson.JsonParseException

class JsonpPollingActor(promise: Promise[String], session: ActorRef) extends Actor {
  session ! Session.Receive
  def receive: Receive = {
    case Session.Message(m) =>
      promise success ((if (m == "o") "" else "a") + m); self ! PoisonPill //FIXIME: Dirty solution in JsonP for append 'a'
    case Session.HeartBeatFrame(h) => promise success h; self ! PoisonPill
  }
}

object JsonPTransport extends Transport {

  def jsonpPolling(sessionId: String, session: ActorRef)(implicit request: Request[AnyContent]) = Async(
    request.queryString.get("c").map { callback =>
      val promise = Promise[String]()
        system.actorOf(Props(new JsonpPollingActor(promise, session)), s"xhr-polling.$sessionId")
      promise.future.map(m =>
        Ok(s"""${callback.reduceLeft(_ + _)}("${escapeJavaScript(m)}");\r\n""") //callback(\\"m\\");\r\n //FIXME: skip '"'
          .withHeaders(
            CONTENT_TYPE -> "application/javascript;charset=UTF-8",
            CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
          .withHeaders(cors: _*))
    }.getOrElse(Future(InternalServerError("\"callback\" parameter required\n"))))

  def jsonpSend(sessionId: String, session: ActorRef)(implicit request: Request[AnyContent]): Result =
    request.contentType.map(_.toLowerCase match {
      case "application/x-www-form-urlencoded" =>
        request.body.asFormUrlEncoded.get.get("d").getOrElse(Nil) match { //TODO: clean asFormUrlEncoded in jsonP
          case Nil =>
            Failure(new Exception("Payload expected."))
          case chars =>
            Success(chars.reduceLeft(_ + _))
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
        if (message == "")
          InternalServerError("Payload expected.")
        else
          try {
            session ! Session.Send(JsonCodec.decodeJson(message))
            println(s"JsonPSend :: ___>>>>>--" + JsonCodec.decodeJson(message))
            Ok("ok")
              .withHeaders(
                CONTENT_TYPE -> "text/plain; charset=UTF-8",
                CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
              .withHeaders(cors: _*)
          } catch {
            case e: JsonParseException => InternalServerError("Broken JSON encoding.")
          }
      case Failure(e) => InternalServerError(e.getMessage)
    }.getOrElse(InternalServerError("Invalid Content-Type"))
}
