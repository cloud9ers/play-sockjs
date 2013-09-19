play-sockjs
===========

Play2 plugin for SockJS (Not ready yet for using it)

## How to use

 1. Clone the repo to your local machine
```
	git clone https://github.com/ashihaby/play-sockjs.git
```
 2. Compile and publish the plugin to local play repo
```
	cd play-sockjs/project-code
	play publish-local
```
 3. Add it to your play project dependencies
```scala
	val sockjs = "play-sockjs" % "play-sockjs_2.10" % "1.0-SNAPSHOT"
	val appDependencies = Seq(
	  sockjs
	)
```
4. Include the pluing in conf/play.plugins
```
	10000:com.cloud9ers.play2.sockjs.SockJsPlugin
```
5. Write your controler and inherit from SockJsTrait
```scala
	package controllers
	
	import com.cloud9ers.play2.sockjs.SockJs
	
	import play.api.libs.concurrent.Promise
	import play.api.libs.iteratee.{Concurrent, Iteratee}
	import play.api.libs.json.JsValue
	import play.api.mvc.{Controller, RequestHeader}
	
	object SockJsService extends Controller with SockJs {
	  def handler(rh: RequestHeader) = {
	    val (enumerator, channel) = Concurrent.broadcast[JsValue]
	    val iteratee = Iteratee.foreach[JsValue] (msg => channel push msg)
	    Promise.pure(iteratee, enumerator)
	  }
	
	  def sockJsHandler = SockJs.async(handler)
	
	  def sockJsHandler2(route: String) = sockJsHandler
	
	  def websocket[String](server: String, session: String) = SockJs.websocket(handler)
	}
```
