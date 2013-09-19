package com.cloud9ers.play2.sockjs

import play.api._
import play.api.mvc._

abstract class SockJsGlobalSettings extends GlobalSettings {
  private val prefix: String = configuration.getString("sockjs.prefix").getOrElse("echo")

  private val websocketUrl = s"^/$prefix/([^.]+)/([^.]+)/websocket".r
  private val sessionUrl = s"^/$prefix/([^.]+)/([^.]+)/([^.]+)".r
  private val closeUrl = "^/close/*".r
  //  private val fullURL= s"^/$prefix/($route)".r //@ashihaby I don't understand what that is for
  private val greatingUrl = s"^/$prefix/?".r
  private val infoDisabledWebsocketRoute = s"^/disabled_websocket_$prefix/info".r
  private val infoRoute = s"^/$prefix/info/?".r

  def sockJsAction: play.api.mvc.Action[play.api.mvc.AnyContent]
  
  def sockJsWebsocket: play.api.mvc.WebSocket[String]

  override def onRouteRequest(request: RequestHeader): Option[Handler] = {
    (request.method, request.path) match {
      case ("OPTIONS", "/echo/info") =>
        println(1)
        Some(sockJsAction)
      case ("GET", "/echo/info") =>
        println(2)
        Some(sockJsAction)
      case ("GET", websocketUrl(server, session)) =>
        println(3)
        Some(sockJsWebsocket)
      case ("POST", sessionUrl(serverId, sessionId, transport)) =>
        println(4)
        Some(sockJsAction)
      case ("GET", sessionUrl(serverId, sessionId, transport)) =>
        println(4.5)
        Some(sockJsAction)
      //    case ("GET", fullURL(route)) => Some(controllers.SockJsService.sockJsHandler2(route))
      case ("GET", greatingUrl()) =>
        println(5)
        Some(sockJsAction)
      case ("GET", "/disabled_websocket_echo/info") =>
        println(6)
        Some(sockJsAction)
      case ("POST", closeUrl()) =>
        println(7)
        Some(sockJsAction)
      case _ =>
        Some(sockJsAction)
        println(8)
        super.onRouteRequest(request)
    }
  }
}