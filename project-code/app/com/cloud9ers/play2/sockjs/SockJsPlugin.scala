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

class SockJsPlugin(app: Application) extends Plugin {
  lazy val baseUrl = app.configuration.getString("sockjs.baseUrl").getOrElse("/")

  override def enabled = app.configuration.getBoolean("play.sockjs.enabled").getOrElse(true)

  override def onStart() {
    val sockjsConfig = app.configuration.getConfig("sockjs")
    sockjsConfig.get.getInt("responseLimit").getOrElse(10)
    // or
    app.configuration.getInt("sockjs.responseLimit").getOrElse(10)
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
  lazy val prefix = current.plugin[SockJsPlugin].map(_.baseUrl).getOrElse("")
  def info = Action { Ok("hi") }
  def ws = WebSocket

  val greatingRoute = s"^/$prefix/echo/?".r
  val infoRoute = s"^/$prefix/info/?".r
  val websocketRout = s"^/$prefix/([0-9]+)/([a-z]+)/?".r

  def sockJsHandler = Action { request =>
    request.path match {
      case greatingRoute() => Ok("Welcome to SockJS!\n").withHeaders(CONTENT_TYPE -> "text/plain;charset=UTF-8")
      case infoRoute() => Ok("info")
      case websocketRout(x, y) => Ok(s"websocket($x, $y)")
      case _ => NotFound("Notfound")
    }
  }
}
