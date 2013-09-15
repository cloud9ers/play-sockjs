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
import scala.util.Try

class JsonpPollingActor(promise: Promise[String], session: ActorRef) extends TransportActor(session, Transport.JSON_P) {
  session ! Session.Register
  def sendFrame(msg: String): Boolean = {
    println("JSONP <<<<<<<<K: " + msg )
    promise success msg
    false
  }
}

object JsonPTransport extends TransportController {

  def jsonpPolling(sessionId: String, session: ActorRef)(implicit request: Request[AnyContent]) = Async(
    request.queryString.get("c").map { callback =>
      val promise = Promise[String]()
      system.actorOf(Props(new JsonpPollingActor(promise, session)), s"xhr-polling.$sessionId.$randomNumber")
      promise.future.map(m =>
        Ok(s"""${callback.reduceLeft(_ + _)}("${escapeJavaScript(m)}");\r\n""") // callback(\\"m\\");\r\n
          .withHeaders(
            CONTENT_TYPE -> "application/javascript;charset=UTF-8",
            CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
          .withHeaders(cors: _*))
    }.getOrElse(Future(InternalServerError("\"callback\" parameter required\n"))))

  def jsonpSend(sessionId: String, session: ActorRef)(implicit request: Request[AnyContent]): Result =
    jsonpResult { message =>
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
    }

  def jsonpResult(f: String => Result)(implicit request: Request[AnyContent]): Result =
    if (request.contentType.map(_.toLowerCase).exists(ct => ct.startsWith("application/x-www-form-urlencoded") || ct.startsWith("text/plain")))
      jsonpBody.map(body => f(body))
        .getOrElse(InternalServerError("Payload expected."))
    else InternalServerError("Invalid Content-Type")

  def jsonpBody(implicit request: Request[AnyContent]): Option[String] =
    request.body.asFormUrlEncoded.flatMap(formBody => formBody.get("d").map(seq => seq.reduceLeft(_ + _)))
      .orElse(request.body.asText
        .map(textBody => URLDecoder.decode(textBody, "UTF-8"))
        .filter(decodedBody => decodedBody.size > 2 && decodedBody.startsWith("d="))
        .map(decodedBody => decodedBody.substring(2)))
}
