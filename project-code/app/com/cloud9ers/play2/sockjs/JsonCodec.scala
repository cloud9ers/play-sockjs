package com.cloud9ers.play2.sockjs

import play.api.libs.json.JsValue
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsArray
import play.api.libs.json.Json
import play.api.libs.json.JsArray

object JsonCodec {
  def encodeJson(ms: JsValue): String = Json stringify ms 
  def decodeJson(msg: String): JsValue = Json parse msg match {
    case jsArr: JsArray => jsArr(0)
    case jsVal => jsVal
  }
  import scala.language.reflectiveCalls
  val JsonDecoder: Enumeratee[String, JsValue] = Enumeratee map decodeJson
  val JsonEncoder: Enumeratee[JsValue, String] = Enumeratee map encodeJson
}