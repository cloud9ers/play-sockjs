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
import play.api.mvc.ResponseHeader
import play.api.libs.iteratee.Enumerator
import play.api.mvc.SimpleResult
import java.text.SimpleDateFormat
import java.util.Date

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
/**
 *
 * 1- "" or "/"  ===============> show welcome page 02:47:07 PM
 * 2- "/info"  =========================> call InfoHandler to return channel info 02:49:41 PM
 * 3-"/iframe" = = =================> call IframeHandler 02:50:11 PM
 * 4-"/websockets"  ========================> call websocketHandler 02:50:55 PM
 */
trait SockJs {
  self: Controller =>
  lazy val prefix = current.plugin[SockJsPlugin].map(_.prefix).getOrElse("")

  val greatingRoute = s"^/$prefix/?".r
  val infoRoute = s"^/$prefix/info/?".r
  val iframeUrl = s"^/$prefix/iframe[0-9-.a-z_]*.html(\\?t=[0-9-.a-z_]*)?".r

  lazy val iframePage = new IframePage(current.plugin[SockJsPlugin].map(_.clientUrl).getOrElse(""))

  def sockJsHandler = Action { request =>
    request.path match {
      case greatingRoute() =>
        Ok("Welcome to SockJS!\n").withHeaders(CONTENT_TYPE -> "text/plain;charset=UTF-8")
      case iframeUrl(_) =>
        if (request.headers.toMap.contains(IF_NONE_MATCH)) {
          NotModified
        } else {
          Ok(iframePage.content).withHeaders(
              CONTENT_TYPE  -> "text/html; charset=UTF-8",
              CACHE_CONTROL -> "max-age=31536000, public",
              ETAG          -> iframePage.getEtag,
              EXPIRES       -> new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz")
                .format(new Date(System.currentTimeMillis() + (365 * 24 * 60 * 60 * 1000))))
        }
      case infoRoute() =>
        Ok("info")

      case _ =>
        NotFound("Notfound")
    }
  }
}
