package com.cloud9ers.play2.sockjs.transports

import com.cloud9ers.play2.sockjs.{ SockJsPlugin, Session, SessionManager }
import play.api.mvc.{ RequestHeader, Request }
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
import play.api.mvc.AnyContent
import play.api.libs.json.JsValue
import com.cloud9ers.play2.sockjs.JsonCodec
import org.codehaus.jackson.JsonParseException

class XhrPollingActor(promise: Promise[String], session: ActorRef) extends Actor {
  session ! Session.Receive
  def receive: Receive = {
    case Session.OpenMessage =>
      println("OOOOOOOOOOPPPPPPPPPPEN")
      sendFrame(SockJsFrames.OPEN_FRAME); self ! PoisonPill
    case Session.Message(m) =>
      sendFrame("a" + m); self ! PoisonPill
    case Session.HeartBeatFrame(h) => sendFrame(h); self ! PoisonPill
    case Session.Close(code, reason) =>
      sendFrame(SockJsFrames.closingFrame(code, reason))
      self ! PoisonPill
      println("XXXXXXX Xhr closed")
  }
  def sendFrame(msg: String) = promise success msg + "\n"
}

class XhrStreamingActor(channel: Concurrent.Channel[Array[Byte]], session: ActorRef) extends Actor {
  override def preStart() {
    import scala.language.postfixOps
    context.system.scheduler.scheduleOnce(100 milliseconds) {
      channel push SockJsFrames.XHR_STREAM_H_BLOCK
      session ! Session.Receive
    }
  }

  def receive: Receive = {
    case Session.OpenMessage =>
      sendFrame(SockJsFrames.OPEN_FRAME); session ! Session.Receive
    case Session.Message(m) =>
      println("XhrStreaming ::<<<<<<<<< " + m)
      sendFrame(s"a$m"); session ! Session.Receive
    case Session.HeartBeatFrame(h) =>
      sendFrame(h); session ! Session.Receive
    case Session.Close(code, reason) =>
      sendFrame(SockJsFrames.closingFrame(code, reason))
      self ! PoisonPill
      println("XXXXXXX XhrStreaming closed")
  }

  def sendFrame(msg: String) = channel push s"$msg\n".toArray.map(_.toByte)
}

object XhrTransport extends Transport {

  def xhrPolling(sessionId: String, session: ActorRef)(implicit request: Request[AnyContent]) = Async {
    val promise = Promise[String]()
    system.actorOf(Props(new XhrPollingActor(promise, session)), s"xhr-polling.$sessionId")
    promise.future.map { m =>
      Ok(m.toString)
        .withHeaders(
          CONTENT_TYPE -> "application/javascript;charset=UTF-8",
          CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
        .withHeaders(cors: _*)
    }
  }

  def xhrStreaming(sessionId: String, session: ActorRef)(implicit request: Request[AnyContent]): Result = {
    val (enum, channel) = Concurrent.broadcast[Array[Byte]]
    val xhrStreamingActor = system.actorOf(Props(new XhrStreamingActor(channel, session.asInstanceOf[ActorRef])), s"xhr-streaming.$sessionId")
    (Ok stream enum.onDoneEnumerating(xhrStreamingActor ! PoisonPill))
      .withHeaders(
        CONTENT_TYPE -> "application/javascript;charset=UTF-8",
        CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
      .withHeaders(cors: _*)
  }

  def xhrSend(sessionId: String, session: ActorRef)(implicit request: Request[AnyContent]): Result = {
    val message: String = request.body.asRaw.flatMap(r => r.asBytes(maxLength).map(b => new String(b))).getOrElse(request.body.asText.getOrElse(""))
    if (message == "")
      InternalServerError("Payload expected.")
    else
      try {
        val contentType = request.headers.get(CONTENT_TYPE).getOrElse(Transport.CONTENT_TYPE_PLAIN) //FIXME: sometimes it's application/xml
        println(s"XHR Send -->>>>>:::: $message, decoded message: ${JsonCodec.decodeJson(message)}")
        session ! Session.Send(JsonCodec.decodeJson(message))
        NoContent
          .withHeaders(
            CONTENT_TYPE -> contentType,
            CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
          .withHeaders(cors: _*)
      } catch {
        case e: JsonParseException => InternalServerError("Broken JSON encoding.")
      }
  }
}
