package com.cloud9ers.play2.sockjs

import java.text.SimpleDateFormat
import java.util.Date
import scala.Option.option2Iterable
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Random
import com.cloud9ers.play2.sockjs.transports.{ EventSourceTransport, JsonPTransport, Transport, WebSocketTransport, XhrTransport }
import akka.actor.{ ActorRef, actorRef2Scala }
import akka.pattern.ask
import akka.util.Timeout
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.{ Enumerator, Iteratee }
import play.api.libs.json.{ JsValue, Json }
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.{ Action, AnyContent, Controller, Request, RequestHeader, Result }
import play.api.mvc.WebSocket

case class SessionResult(session: Option[ActorRef], result: Result)
case class SockJsHandler(action: Action[AnyContent], websocket: WebSocket[String])

trait SockJs { self: Controller =>
  type Handler = RequestHeader => Future[(Iteratee[JsValue, _], Enumerator[JsValue])]

  lazy val logger = SockJsPlugin.current.logger
  lazy val system = SockJsPlugin.current.system
  def randomNumber() = 2L << 30 + Random.nextInt
  lazy val prefix = SockJsPlugin.current.prefix
  lazy val maxLength: Int = SockJsPlugin.current.maxLength
  lazy val maxBytesStreaming: Int = SockJsPlugin.current.maxBytesStreaming
  val websocketEnabled: Boolean = SockJsPlugin.current.websocketEnabled

  lazy val sessionManager = SockJsPlugin.current.sessionManager
  implicit val timeout = Timeout(5.seconds)

  val greatingRoute = s"^/$prefix/?".r
  val infoRoute = s"^/$prefix/info/?".r
  val infoDisabledWebsocketRoute = s"^/disabled_websocket_$prefix/info".r
  val iframeUrl = s"^/$prefix/iframe[0-9-.a-z_]*.html(\\?t=[0-9-.a-z_]*)?".r
  val sessionUrl = s"^/$prefix/[^.]+/[^.]+/[^.]+".r
  val closeSessionUrl = "^/close/[^.]+(/[^.]+)$".r

  lazy val iframePage = new IframePage(current.plugin[SockJsPlugin].map(_.clientUrl).getOrElse(""))

  object SockJs {
    /**
     * The same as Websocket.async
     * @param f - user function that takes the request header and return Future of the user's Iteratee and Enumerator
     */
    def async(handler: Handler) = SockJsHandler(action(handler), websocket(handler))

    /**
     * Action
     */
    def action(handler: RequestHeader => Future[(Iteratee[JsValue, _], Enumerator[JsValue])]): play.api.mvc.Action[AnyContent] = Action {
      implicit request =>
        (request.method, request.path) match {
          case (_, greatingRoute()) => Ok("Welcome to SockJS!\n").withHeaders(CONTENT_TYPE -> "text/plain;charset=UTF-8")
          case (_, iframeUrl(_)) => handleIframe
          case (_, infoDisabledWebsocketRoute()) => info(websocket = false)
          case ("GET", infoRoute()) => info(websocket = websocketEnabled)
          case ("OPTIONS", infoRoute()) => handleCORSOptions(List("OPTIONS", "GET"))
          case ("OPTIONS", sessionUrl()) => handleCORSOptions(List("OPTIONS", "POST"))
          case ("POST" | "GET", sessionUrl()) =>
            Async(futureSession(handler).map(handleMessages).map(_.result))
          case (_, closeSessionUrl(sessionid)) =>
            Async(futureSession(handler).map(handleMessages).map(closeSession).map(_.result))
          case _ => NotFound("Notfound")
        }
    }

    /**
     * websocket
     */
    def websocket[String](f: RequestHeader => Future[(Iteratee[JsValue, _], Enumerator[JsValue])]) =
      WebSocketTransport.websocket(f)(play.core.server.websocket.Frames.textFrame)
  }

  def futureSession(handler: RequestHeader => Future[(Iteratee[JsValue, _], Enumerator[JsValue])])(implicit request: Request[AnyContent]): Future[Option[ActorRef]] = {
    val pathList = request.path.split("/").reverse
    val (transport, sessionId, serverId) = (pathList(0), pathList(1), pathList(2))
    val futureSession: Future[Any] = {
      if (!transport.toLowerCase.endsWith("send")) sessionManager ? SessionManager.GetOrCreateSession(sessionId, handler, request)
      else sessionManager ? SessionManager.GetSession(sessionId)
    }
    futureSession.mapTo[Option[ActorRef]]
  }

  def handleMessages(session: Option[ActorRef])(implicit request: Request[AnyContent]): SessionResult = {
    val pathList = request.path.split("/").reverse
    val (transport, sessionId, serverId) = (pathList(0), pathList(1), pathList(2))
    val result = session match {
      case None =>
        logger.debug(s"Session didn't found, sessionId: $sessionId, transport: $transport, serverId: $serverId")
        NotFound
      case Some(session) => transport match {
        case Transport.XHR ⇒ XhrTransport.xhrPolling(sessionId, session)
        case Transport.XHR_STREAMING ⇒ XhrTransport.xhrStreaming(sessionId, session)
        case Transport.XHR_SEND ⇒ XhrTransport.xhrSend(sessionId, session)
        case Transport.JSON_P ⇒ JsonPTransport.jsonpPolling(sessionId, session)
        case Transport.JSON_P_SEND ⇒ JsonPTransport.jsonpSend(sessionId, session)
        case Transport.EVENT_SOURCE ⇒ EventSourceTransport.eventSource(sessionId, session)
      }
    }
    SessionResult(session, result)
  }

  def closeSession(sessionResult: SessionResult)(implicit request: Request[AnyContent]): SessionResult = {
    for (session <- sessionResult.session) {
      logger.debug(s"calling close session: ${session}")
      session ! Session.Close(3000, "Go away!")
    }
    sessionResult
  }

  def handleIframe(implicit request: Request[AnyContent]) =
    if (request.headers.toMap.contains(IF_NONE_MATCH)) {
      NotModified
    } else {
      Ok(iframePage.content).withHeaders(
        CONTENT_TYPE -> "text/html; charset=UTF-8", CACHE_CONTROL -> "max-age=31536000, public",
        ETAG -> iframePage.getEtag,
        EXPIRES -> (new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz"))
          .format(new Date(System.currentTimeMillis() + (365 * 24 * 60 * 60 * 1000))))
    }

  def info(websocket: Boolean = true)(implicit request: Request[AnyContent]) =
    Ok(Json.obj(
      "websocket" -> websocket,
      "cookie_needed" -> true,
      "origins" -> List("*:*"),
      "entropy" -> randomNumber))
      .withHeaders(
        CONTENT_TYPE -> "application/json;charset=UTF-8",
        CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
      .withHeaders(cors: _*)

  def handleCORSOptions(methods: List[String])(implicit request: Request[AnyContent]) = {
    val oneYearSeconds = 365 * 24 * 60 * 60
    val oneYearms = oneYearSeconds * 1000
    val expires = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")
      .format(new Date(System.currentTimeMillis() + oneYearms))
    NoContent
      .withHeaders(
        EXPIRES -> expires,
        CACHE_CONTROL -> "public,max-age=31536000",
        ACCESS_CONTROL_ALLOW_METHODS -> methods.reduceLeft(_ + ", " + _),
        ACCESS_CONTROL_MAX_AGE -> oneYearSeconds.toString)
      .withHeaders(cors: _*)
  }

  def cors(implicit req: Request[AnyContent]) = Seq(
    ACCESS_CONTROL_ALLOW_CREDENTIALS -> "true",
    ACCESS_CONTROL_ALLOW_ORIGIN -> req.headers.get("origin").map(o => if (o != "null") o else "*").getOrElse("*"))
    .union(
      (for (acrh ← req.headers.get(ACCESS_CONTROL_REQUEST_HEADERS))
        yield (ACCESS_CONTROL_ALLOW_HEADERS -> acrh)).toSeq)
}
