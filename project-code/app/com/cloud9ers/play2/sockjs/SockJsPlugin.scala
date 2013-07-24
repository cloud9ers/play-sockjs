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

trait SockJs {
  self: Controller =>
  lazy val baseUrl = current.plugin[SockJsPlugin].map(_.baseUrl)
  
  val infoRoute = s"^/$baseUrl/info/?".r
  val websocketRout = s"^/$baseUrl/([0-9]+)/([a-z]+)/?".r
  val iframe_url = s"^/$baseUrl/iframe[0-9-.a-z_]*.html(\\?t=[0-9-.a-z_]*)?".r
  
  lazy val iframePage = new IframePage(current.plugin[SockJsPlugin].map(_.client_url).getOrElse(""))

  def sockJsHandler = Action { request =>
    request.path match {
      case infoRoute() => Ok("info")
      case websocketRout(x, y) => Ok(s"websocket($x, $y)")
      case iframe_url(_) => Ok("iframe")

      case _ => NotFound("Notfound")
    }
  }
}
