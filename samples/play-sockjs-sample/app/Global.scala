import com.cloud9ers.play2.sockjs.SockJsGlobalSettings

object Global extends SockJsGlobalSettings {
  def sockJsAction = controllers.SockJsService.sockJsHandler
  def sockJsWebsocket = controllers.SockJsService.websocket
}