package controllers

import com.cloud9ers.play2.sockjs.SockJs
import play.api.libs.iteratee.{ Concurrent, Iteratee }
import play.api.mvc.{ Controller, WebSocket }
import play.api.libs.concurrent.Promise
import play.api.mvc.RequestHeader
import play.api.libs.json.JsValue
import play.api.libs.iteratee.Enumerator
import play.api.mvc.AnyContent
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc.Action
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Actor
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout
import akka.actor.ActorRef
import play.api.mvc.Request
import play.api.libs.json.JsString
import akka.actor.PoisonPill

class X(c: Concurrent.Channel[String]) extends Actor {
  override def preStart() = context.system.scheduler.scheduleOnce(100 milliseconds)(self ! "start")

  def receive() = {
    case "start" =>
      println("FUCK!!!!"); c push "FUCK!!!!"; context.system.scheduler.scheduleOnce(500 milliseconds)(self ! "start")
    case "stop" =>
      println("swollow PoisonPill"); self ! PoisonPill
  }
  override def postStop() = println("BOOOOOOOOOOOOOM!!!")
}

object SockJsService extends Controller with SockJs {
  /*
   * userHandler (user of the plugin) -> downEnumerator -> downIteratee -> sockjsClient (browser)
   * sockjsClient -> upEnumerator -> upIteratee -> userHandler
   */

  /**
   * The sockJs Handler that the user of the plugin will write to handle the service logic
   * It has the same interface of the websocket
   * returns (Iteratee, Enumerator):
   * Iteratee - to iterate over msgs that will be received from the sockjs client
   * Enumerator - to enumerate the msgs that will be sent to the sockjs client
   */
  def handler(rh: RequestHeader) = {
    val (downEnumerator, downChannel) = Concurrent.broadcast[JsValue]
    val upIteratee = Iteratee.foreach[JsValue] { msg => downChannel push msg; println(s"handler ::::::::::: message: $msg") }
    Promise.pure(upIteratee, downEnumerator)
  }

  def sockJsHandler = SockJs.async(handler) //TODO: Try to make it a single function and pass the complementary path instead
  //hint https://github.com/cgbystrom/sockjs-netty/blob/master/src/main/java/com/cgbystrom/sockjs/ServiceRouter.java#L94

  def sockJsHandler2(route: String) = sockJsHandler

  def websocket[String](server: String, session: String) = SockJs.websocket(handler)

  def sss1() = {
    val (e, ch) = Concurrent.broadcast[String]
    var i = 1
    Future(while (i > 0) {
      ch push s"msg $i"
      i = i + 1
      println(i)
      Thread sleep 500
    })
    Action(Ok.stream(e.onDoneEnumerating { i = -100; println("BOOOOOOOOOM!") }))
  }

  def sss() = {
    val (e, ch) = Concurrent.broadcast[String]
    val x = system.actorOf(Props(new X(ch)), "Fuck.Actor")
    Action(implicit request =>
      Ok.stream(e.onDoneEnumerating { x ! "stop"; println("BOOOOOOOOOM!") })
        .withHeaders(
          CONTENT_TYPE -> "text/event-stream;charset=UTF-8",
          CACHE_CONTROL -> "no-store, no-cache, must-revalidate, max-age=0")
        .withHeaders(cors: _*))
  }

  object SockJs2 {
    val system = ActorSystem("sockJs")
    val sessionManager = system.actorOf(Props(new SessionManager()), "session-manager")
    implicit val timeout = Timeout(5.seconds)
    def async(f: RequestHeader => Future[(Iteratee[JsValue, _], Enumerator[JsValue])]): play.api.mvc.Action[AnyContent] = {
      Action { request =>
        val pathList = request.path.split("/").reverse
        val (transport, sessionId, serverId) = (pathList(0), pathList(1), pathList(2))

        Async(
          transport match {
            case "xhr_send" => (sessionManager ? Get(sessionId)).map { case s: ActorRef => s ! Send(JsString("hi")); Ok }
            case "xhr" => (sessionManager ? GetOrCreate(sessionId, f, request)).flatMap { case s: ActorRef => (s ? Receive).map { case m: String => Ok(m) } }
          })

      }

      Action(r => Async(Future(Ok)))
      //      using { rh =>
      //        val p = f(rh)
      //        val upIteratee = Iteratee.flatten(p.map(_._1))
      //        val downEnumerator = Enumerator.flatten(p.map(_._2))
      //        (upIteratee, downEnumerator)
      //      }
    }
  }
}
case class Get(sessionId: String)
case class GetOrCreate(sessionId: String, handler: RequestHeader => Future[(Iteratee[JsValue, _], Enumerator[JsValue])], request: Request[AnyContent])
case class Send(m: JsValue)
case object Receive
class SessionManager extends Actor {
  def receive = {
    case GetOrCreate(sessionId, handler, request) => context.child(sessionId).getOrElse(context.actorOf(Props(new Session(sessionId, handler, request))))
  }
}
class Session(sessionId: String, handler: RequestHeader => Future[(Iteratee[JsValue, _], Enumerator[JsValue])], request: Request[AnyContent]) extends Actor {
  val p = handler(request)
  val upIteratee = Iteratee.flatten(p.map(_._1))
  val downEnumerator = Enumerator.flatten(p.map(_._2))

  val (upEnumerator, upChannel) = Concurrent.broadcast[JsValue]
  val queue = context.actorOf(Props(new MsgQueue()), "queue")
  val downIteratee = Iteratee.foreach[JsValue](msg => queue ! Enqueue(msg))

  upEnumerator |>> upIteratee
  downEnumerator |>> downIteratee

  def receive = {
    case Send(msg) => upChannel push msg
    case Receive => queue ! Dequeue(sender)
  }
}
case class Enqueue(msg: JsValue)
case class Dequeue(listener: ActorRef)
class MsgQueue extends Actor {
  val queue = scala.collection.mutable.Queue[JsValue]()
  var writer: Option[ActorRef] = None

  def write(w: ActorRef) {
    w ! queue.dequeueAll(_ => true).toList
    writer = None
  }

  def receive = {
    case Enqueue(msg) =>
      queue += msg; for (w <- writer) write(w)
    case Dequeue(w) => writer = Some(w); if (!queue.isEmpty) write(w)
  }
}