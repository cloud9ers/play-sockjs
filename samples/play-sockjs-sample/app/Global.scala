import play.api.GlobalSettings
import com.cloud9ers.play2.sockjs.SockJsRouter

object Global extends GlobalSettings with SockJsRouter {
  def sockJsHandler = controllers.SockJsService.sockJsHandler
}
