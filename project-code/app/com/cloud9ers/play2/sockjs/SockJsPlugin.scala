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
  //  lazy val baseUrl = app.configuration.getString("sockjs.baseUrl").getOrElse("/")

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

trait SockJs {
  self: Controller =>
  //  lazy val baseUrl = current.plugin[SockJsPlugin].map(_.baseUrl)
  def info = Action { Ok("hi") }
  def ws = WebSocket

  val infoRoute = "info/?".r
  val websocketRout = "([0-9]+)/([a-z]+)/?".r

  def sockJsHandler(route: String) = Action {
    println(route)
    route match {
      case infoRoute() => Ok("info")
      case websocketRout(x, y) => Ok(s"websocket($x, $y)")
      case _ => NotFound("Notfound")
    }
  }
}
