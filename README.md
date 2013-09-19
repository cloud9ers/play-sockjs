play-sockjs
===========

Play2 plugin for SockJS (Not ready yet for using it)
* So far, you can add only one sockjs

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
5. Write your controller and inherit from SockJsTrait

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
	    val iteratee = Iteratee.foreach[JsValue] { msg =>
	      // msg: is the message comming from the client
	      // channel: is where you send messages to the client
	      channel push msg // just send back the message
	    }
	    Promise.pure(iteratee, enumerator)
	  }
	
	  def sockJsAction = SockJs.async(handler)
	
	  def websocket[String] = SockJs.websocket(handler)
	}
```
6. Add object Global in the default package in Global.scala
	* This is required so far to avoid play routing because sockjs requires complex routing scheme
	* We hope to find a better approach soon

```scala
	import com.cloud9ers.play2.sockjs.SockJsGlobalSettings
	
	object Global extends SockJsGlobalSettings {
	  def sockJsAction = controllers.SockJsService.sockJsHandler
	  def sockJsWebsocket = controllers.SockJsService.websocket
	}
```
7. Finnaly, you need to add the base Url to the configuration in application.con
```
	sockjs.prefix=echo
```

* Full configurations:

```
	sockjs.prefix=echo
	sockjs.responseLimit=1000
	sockjs.jsessionid=false
	sockjs.heartbeetDelay=1000
	sockjs.diconnectDelay=5000
	sockjs.websocketEnabled=false
	sockjs {
	 akka {
	  stdout-loglevel = "DEBUG"
	  loglevel = "DEBUG"
	    log-dead-letters = 10
	    log-dead-letters-during-shutdown = on
	  }
	}
```
