package controllers

import play.api.mvc.Controller
import com.cloud9ers.play2.sockjs.SockJs

object SockJsService extends Controller with SockJs {

  def sockJs(route: String = "") = sockJsHandler
  def sockJs2 = sockJsHandler

}