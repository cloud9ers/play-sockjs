package com.cloud9ers.play2.sockjs

import com.cloud9ers.play2.sockjs.transports.Transport
import java.text.SimpleDateFormat
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.util.Date
import scala.util.Random
import play.api.libs.iteratee.{ Concurrent, Enumerator, Iteratee }
import play.api.mvc.{ Action, WebSocket, Controller, Request, RequestHeader, AnyContent, Result }
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.Play.current
import play.api.Logger
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout
import akka.actor.ActorRef
import com.cloud9ers.play2.sockjs.transports.{ WebSocketTransport, XhrTransport, EventSourceTransport, JsonPTransport }
import play.api.libs.json.JsValue

trait SockJs { self: Controller =>
  lazy val logger = SockJsPlugin.current.logger
  lazy val system = SockJsPlugin.current.system
  def randomNumber() = 2L << 30 + Random.nextInt
  lazy val prefix = SockJsPlugin.current.prefix
  lazy val maxLength: Int = SockJsPlugin.current.maxLength
  val websocketEnabled: Boolean = SockJsPlugin.current.websocketEnabled
  
  lazy val sessionManager = SockJsPlugin.current.sessionManager
  implicit val timeout = Timeout(5.seconds)

  val greatingRoute = s"^/$prefix/?".r
  val infoRoute = s"^/$prefix/info/?".r
  val infoDisabledWebsocketRoute = s"^/disabled_websocket_$prefix/info".r
  val iframeUrl = s"^/$prefix/iframe[0-9-.a-z_]*.html(\\?t=[0-9-.a-z_]*)?".r
  val sessionUrl = s"^/$prefix/[^.]+/[^.]+/[^.]+".r

  lazy val iframePage = new IframePage(current.plugin[SockJsPlugin].map(_.clientUrl).getOrElse(""))

  object SockJs {
    /**
     * The same as Websocket.async
     * @param f - user function that takes the request header and return Future of the user's Iteratee and Enumerator
     */
    def async(f: RequestHeader => Future[(Iteratee[JsValue, _], Enumerator[JsValue])]): play.api.mvc.Action[AnyContent] = {
      using { rh =>
        val p = f(rh)
        val upIteratee = Iteratee.flatten(p.map(_._1))
        val downEnumerator = Enumerator.flatten(p.map(_._2))
        (upIteratee, downEnumerator)
      }
    }

    /**
     * returns Handler and passes a function that pipes the user Enumerator to the sockjs Iteratee
     * and pipes the sockjs Enumerator to the user Iteratee
     */
    def using(f: RequestHeader => (Iteratee[JsValue, _], Enumerator[JsValue])): play.api.mvc.Action[AnyContent] =
      handler { rh =>
        (upEnumerator: Enumerator[String], downIteratee: Iteratee[JsValue, Unit]) =>
          // call the user function and holds the user's Iteratee (in) and Enumerator (out)
          val (upIteratee, downEnumerator) = f(rh)

          // pipes the msgs from the sockjs client to the user's Iteratee
          upEnumerator &> JsonCodec.JsonDecoder |>> upIteratee

          // pipes the msgs from the user's Enumerator to the sockjs client
          downEnumerator |>> downIteratee
      }

    /**
     * websocket
     */
    def websocket[String](f: RequestHeader => Future[(Iteratee[JsValue, _], Enumerator[JsValue])]) =
      WebSocketTransport.websocket(f)(play.core.server.websocket.Frames.textFrame)
  }
  /**
   * Mainly passes the sockjs Enumerator/Iteratee to the function that associate them with the user's Iteratee/Enumerator respectively
   * According to the transport, it creates the sockjs Enumerator/Iteratee and return Handler in each path
   * calls enqueue/dequeue of the session to handle msg queue between send and receive
   */
  def handler(f: RequestHeader => (Enumerator[String], Iteratee[JsValue, Unit]) => Unit) =
    Action { implicit request =>
      logger.debug(request.path)
      request.path match {
        case greatingRoute() => Ok("Welcome to SockJS!\n").withHeaders(CONTENT_TYPE -> "text/plain;charset=UTF-8")
        case iframeUrl(_) => handleIframe
        case infoRoute() => info(websocket = websocketEnabled)
        case infoDisabledWebsocketRoute() => info(websocket = false)
        case sessionUrl() => handleSession(f)
        case _ => NotFound("Notfound")
      }
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

  def handleSession(f: RequestHeader => (Enumerator[String], Iteratee[JsValue, Unit]) => Unit)(implicit request: Request[AnyContent]): Result = {
    val pathList = request.path.split("/").reverse
    val (transport, sessionId, serverId) = (pathList(0), pathList(1), pathList(2))
    transport match {
      case Transport.XHR			⇒ XhrTransport.xhrPolling(sessionId)
      case Transport.XHR_STREAMING	⇒ XhrTransport.xhrStreaming(sessionId)
      case Transport.XHR_SEND		⇒ XhrTransport.xhrSend(sessionId, f)
      case Transport.JSON_P			⇒ JsonPTransport.jsonpPolling(sessionId)
      case Transport.JSON_P_SEND	⇒ JsonPTransport.jsonpSend(sessionId, f)
      case Transport.EVENT_SOURCE	⇒ EventSourceTransport.eventSource(sessionId)
    }
  }

  def info(websocket: Boolean = true)(implicit request: Request[AnyContent]) = request.method match {
    case "GET" =>
      Ok(Json.obj(
        "websocket" -> websocket,
        "cookie_needed" -> true,
        "origins" -> List("*:*"),
        "entropy" -> randomNumber))
        .withHeaders(
          CONTENT_TYPE -> "application/json;charset=UTF-8",
          CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
        .withHeaders(cors: _*)
    case "OPTIONS" =>
      val oneYearSeconds = 365 * 24 * 60 * 60
      val oneYearms = oneYearSeconds * 1000
      val expires = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")
        .format(new Date(System.currentTimeMillis() + oneYearms))
      NoContent
        .withHeaders(
          EXPIRES -> expires,
          CACHE_CONTROL -> "public,max-age=31536000",
          ACCESS_CONTROL_ALLOW_METHODS -> "OPTIONS, GET",
          ACCESS_CONTROL_MAX_AGE -> oneYearSeconds.toString)
        .withHeaders(cors: _*)
  }

  def cors(implicit req: Request[AnyContent]) = Seq(
    ACCESS_CONTROL_ALLOW_CREDENTIALS -> "true",
    ACCESS_CONTROL_ALLOW_ORIGIN -> req.headers.get("origin").map(o => if (o != "null") o else "*").getOrElse("*"))
    .union(
      (for (acrh <- req.headers.get(ACCESS_CONTROL_REQUEST_HEADERS))
        yield (ACCESS_CONTROL_ALLOW_HEADERS -> acrh)).toSeq)
}
