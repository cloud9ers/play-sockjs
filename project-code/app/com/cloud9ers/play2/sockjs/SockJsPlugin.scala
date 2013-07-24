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

class SockJsPlugin(app: Application) extends Plugin {
  lazy val prefix = app.configuration.getString("sockjs.prefix").getOrElse("/")
  lazy val client_url = app.configuration.getString("sockjs.client_url")
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
  val iframe_url = s"^/$prefix/iframe[0-9-.a-z_]*.html(\\?t=[0-9-.a-z_]*)?".r
  
  lazy val iframePage = new IframePage(current.plugin[SockJsPlugin].map(_.client_url).getOrElse(""))

  def sockJsHandler = Action { request =>
    request.path match {
      case greatingRoute() =>
        Ok("Welcome to SockJS!\n").withHeaders(CONTENT_TYPE -> "text/plain;charset=UTF-8")
      case iframe_url(_) => Ok("iframe")
      case infoRoute() =>
        Ok("info")

      case _ =>
        NotFound("Notfound")
    }
  }
}
