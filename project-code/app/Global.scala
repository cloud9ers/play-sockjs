import play.api._
import play.api.mvc._

object Global extends GlobalSettings {
  private val websocketUrl = "/echo/($server)/($session)/websocket".r
  private val fullURL= "/echo/($route)".r
 
  override def onRouteRequest(request: RequestHeader): Option[Handler] = {
    (request.method, request.path) match {
    case ("OPTIONS", "/echo/info") => Some(controllers.SockJsService.sockJsHandler)
    case ("GET", websocketUrl(server, session)) => Some(controllers.SockJsService.websocket(server, session))
    case ("GET", fullURL(route)) => Some(controllers.SockJsService.sockJsHandler2(route))
    case ("GET", "/echo") => Some(controllers.SockJsService.sockJsHandler)
    case ("GET", "/disabled_websocket_echo/info") => Some(controllers.SockJsService.sockJsHandler)
    case _ => super.onRouteRequest(request)
    
    }
  }
 
 
}