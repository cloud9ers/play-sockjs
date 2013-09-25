package com.cloud9ers.play2.sockjs

import play.api._
import play.api.mvc._

trait SockJsRouter extends GlobalSettings {
  private val prefix: String = configuration.getString("sockjs.prefix").getOrElse("echo")

  private val websocketUrl = s"^/$prefix/[^.]+/[^.]+/websocket".r
  private val closeUrl = "^/close/[^.]+/[^.]+$".r
  private val fullURL = "^/$prefix/($route)".r //@ashihaby I don't understand what that is for
  private val infoDisabledWebsocketRoute = s"^/disabled_websocket_$prefix/info"

  def sockJsHandler: SockJsHandler //abstract
  def sockJsAction: Action[play.api.mvc.AnyContent] = sockJsHandler.action
  def sockJsWebsocket: WebSocket[String] = sockJsHandler.websocket

  override def onRouteRequest(request: RequestHeader): Option[Handler] = {
    (request.method, request.path) match {
      case ("GET", websocketUrl()) => Some(sockJsWebsocket)
      case (_, r) if r.startsWith(s"/$prefix") => Some(sockJsAction)
      case ("GET", infoDisabledWebsocketRoute) => Some(sockJsAction)
      case ("POST", closeUrl()) => Some(sockJsAction)
      case _ => super.onRouteRequest(request)
    }
  }
}