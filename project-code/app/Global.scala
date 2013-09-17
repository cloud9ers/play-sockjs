import play.api._
import play.api.mvc._
import play.router.RoutesCompiler.RouteFileParser

object Global extends GlobalSettings {
  private val prefix: String = configuration.getString("sockjs.prefix").getOrElse("echo")

  private val websocketUrl = s"^/$prefix/([^.]+)/([^.]+)/websocket".r
  private val sessionUrl = s"^/$prefix/([^.]+)/([^.]+)/([^.]+)".r
  private val closeUrl = "^/close/*".r
//  private val fullURL= s"^/$prefix/($route)".r
  private val greatingUrl = s"^/$prefix/?".r
  private val infoDisabledWebsocketRoute = s"^/disabled_websocket_$prefix/info".r
  private val infoRoute = s"^/$prefix/info/?".r
 
  override def onRouteRequest(request: RequestHeader): Option[Handler] = {
    (request.method, request.path) match {
    case ("OPTIONS", "/echo/info") =>
      println(1)
      Some(controllers.SockJsService.sockJsHandler)
    case ("GET", "/echo/info") =>
      println(2)
      Some(controllers.SockJsService.sockJsHandler)
    case ("GET", websocketUrl(server, session)) => 
      println(3)
      Some(controllers.SockJsService.websocket(server, session))
    case ("POST", sessionUrl(serverId, sessionId, transport)) => 
      println(4)
      Some(controllers.SockJsService.sockJsHandler)
//    case ("GET", fullURL(route)) => Some(controllers.SockJsService.sockJsHandler2(route))
    case ("GET", greatingUrl()) => 
      println(5)
      Some(controllers.SockJsService.sockJsHandler)
    case ("GET", "/disabled_websocket_echo/info") => 
      println(6)
      Some(controllers.SockJsService.sockJsHandler)
    case ("POST", closeUrl()) => 
      println(7)
      Some(controllers.SockJsService.sockJsHandler)
    case _ => 
      Some(controllers.SockJsService.sockJsHandler)
      println(8)
      super.onRouteRequest(request)
    }
  }
}