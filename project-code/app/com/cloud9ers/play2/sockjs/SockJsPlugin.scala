package com.cloud9ers.play2.sockjs

import play.api.Plugin
import play.api.Application
import play.api.Logger
import java.util.concurrent.TimeUnit
import play.api.mvc.Request
import play.api.Play
import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.mvc.WebSocket
import play.api.Play.current
import play.api.mvc.RequestHeader
import play.api.mvc.AnyContent
import play.api.libs.json.Json
import scala.util.Random
import java.text.SimpleDateFormat
import java.util.Date
import play.api.mvc.Result
import java.util.Date
import com.cloud9ers.play2.sockjs.transports.Transport
import com.cloud9ers.play2.sockjs.transports.WebSocketTransport
import com.cloud9ers.play2.sockjs.transports.XhrTransport
import com.cloud9ers.play2.sockjs.transports.XhrTransport
import scala.collection.mutable.{Map => MutableMap}

class SockJsPlugin(app: Application) extends Plugin {
  lazy val prefix = app.configuration.getString("sockjs.prefix").getOrElse("/")
  lazy val clientUrl = app.configuration.getString("sockjs.clientUrl")
    .getOrElse("http://cdn.sockjs.org/sockjs-0.3.4.min.js")

  override def enabled = app.configuration.getBoolean("play.sockjs.enabled").getOrElse(true)

  override def onStart() {
    Logger.info("Starting SockJs Plugin.")
  }

  override def onStop() {
    Logger.info("Stopping SockJS Plugin.")
  }

}

trait SockJs { self: Controller =>
  def randomNumber() = 2L << 30 + Random.nextInt
  lazy val prefix = current.plugin[SockJsPlugin].map(_.prefix).getOrElse("")

  val greatingRoute = s"^/$prefix/?".r
  val infoRoute = s"^/$prefix/info/?".r
  val infoDisabledWebsocketRoute = s"^/disabled_websocket_$prefix/info".r
  val iframeUrl = s"^/$prefix/iframe[0-9-.a-z_]*.html(\\?t=[0-9-.a-z_]*)?".r
  val sessionUrl = s"^/$prefix/[^.]+/[^.]+/[^.]+".r
  val sessions: MutableMap[String, Session] = MutableMap()


  lazy val iframePage = new IframePage(current.plugin[SockJsPlugin].map(_.clientUrl).getOrElse(""))

  def cors(implicit req: Request[AnyContent]) = Seq(
    ACCESS_CONTROL_ALLOW_CREDENTIALS -> "true",
    ACCESS_CONTROL_ALLOW_ORIGIN -> req.headers.get("origin").map(o => if (o != "null") o else "*").getOrElse("*"))
    .union(
      (for (acrh <- req.headers.get(ACCESS_CONTROL_REQUEST_HEADERS))
        yield (ACCESS_CONTROL_ALLOW_HEADERS -> acrh)).toSeq)
        
  def handleSession(implicit request: Request[AnyContent]) = {
    val pathList = request.path.split("/").reverse
    val (transport, sessionId, serverId) = (pathList(0), pathList(1), pathList(2))
    transport match {
      case Transport.XHR => new XhrTransport().handleRequest(sessionId)(sessions)
      case _  => Ok("o\n")
      
    }
  }
  
  def handleIframe(implicit request: Request[AnyContent]) = {
    if (request.headers.toMap.contains(IF_NONE_MATCH)) {
          NotModified
        } else {
          Ok(iframePage.content).withHeaders(
              CONTENT_TYPE  -> "text/html; charset=UTF-8", CACHE_CONTROL -> "max-age=31536000, public", 
              ETAG          -> iframePage.getEtag, 
              EXPIRES       -> (new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz"))
              .format(new Date(System.currentTimeMillis() + (365 * 24 * 60 * 60 * 1000))))
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

  def sockJsHandler = Action { implicit request =>
    request.path match {
      case greatingRoute() => Ok("Welcome to SockJS!\n").withHeaders(CONTENT_TYPE -> "text/plain;charset=UTF-8")
      case iframeUrl(_) => handleIframe
    case infoRoute() => info()
    case infoDisabledWebsocketRoute() => info(websocket = false)
    case sessionUrl() => handleSession
    case _ => NotFound("Notfound")

    }
  }
}
