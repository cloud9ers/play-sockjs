package com.cloud9ers.play2.sockjs.transports

import java.net.URLDecoder

import scala.concurrent.{Future, Promise}

import org.codehaus.jackson.JsonParseException

import com.cloud9ers.play2.sockjs.{JsonCodec, Session}
import com.cloud9ers.play2.sockjs.StringEscapeUtils.escapeJavaScript

import akka.actor.{ActorRef, Props, actorRef2Scala}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{AnyContent, Request, Result}

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
