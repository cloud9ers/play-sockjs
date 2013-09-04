package com.cloud9ers.play2.sockjs

import play.api.libs.json.JsValue
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.JsArray
import play.api.libs.json.Json
import play.api.libs.json.JsArray

object JsonCodec {
  /**
   * Encodes JSON to string that will be sent through the socket
   */
  def encodeJson(ms: JsValue): String = escapeCharacters(Json stringify ms)
  
  /**
   * Decodes the String JSON to JsValue to be sent to the user handler
   */
  def decodeJson(msg: String): JsValue = Json parse msg match {
    case jsArr: JsArray => jsArr(0)
    case jsVal => jsVal
  }
  
  import scala.language.reflectiveCalls
  val JsonEncoder: Enumeratee[JsValue, String] = Enumeratee map encodeJson
  val JsonDecoder: Enumeratee[String, JsValue] = Enumeratee map decodeJson

  /**
   * SockJS requires a special JSON codec - it requires that many other characters,
   * over and above what is required by the JSON spec are escaped.
   * To satisfy this we escape any character that escapable with short escapes and
   * any other non ASCII character we unicode escape it
   *
   * By default, Jackson does not escape unicode characters in JSON strings
   * This should be ok, since a valid JSON string can contain unescaped JSON
   * characters.
   * However, SockJS requires that many unicode chars are escaped. This may
   * be due to browsers barfing over certain unescaped characters
   * So... when encoding strings we make sure all unicode chars are escaped
   *
   * This code adapted from http://wiki.fasterxml.com/JacksonSampleQuoteChars
   *
   * Refs:
   * https://github.com/netty/netty/pull/1615/files#L29R71
   * https://github.com/eclipse/vert.x/blob/master/vertx-core/src/main/java/org/vertx/java/core/sockjs/impl/JsonCodec.java#L32
   */
  def escapeCharacters(message: String): String =
    message.foldLeft(new StringBuilder(message.length)) {
      (sb, ch) =>
        if ((ch >= '\u0000' && ch <= '\u001F') ||
          (ch >= '\uD800' && ch <= '\uDFFF') ||
          (ch >= '\u200C' && ch <= '\u200F') ||
          (ch >= '\u2028' && ch <= '\u202F') ||
          (ch >= '\u2060' && ch <= '\u206F') ||
          (ch >= '\uFFF0' && ch <= '\uFFFF'))
          sb.append('\\')
            .append('u')
            .append(Integer.toHexString(ch).toLowerCase)
        else
          sb.append(ch)
    }.toString
}