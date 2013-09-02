package com.cloud9ers.play2.sockjs

import java.io.StringWriter
import java.util.Locale

object StringEscapeUtils {
  def escapeJavaScript(str: String, escapeSingleQuote: Boolean = true, escapeForwardSlash: Boolean = true): String = {
    val out = new StringWriter(str.length() * 2)
    str.foreach { ch =>
      if (ch > 0xfff) out.write("\\u" + hex(ch))
      else if (ch > 0xff) out.write("\\u0" + hex(ch))
      else if (ch > 0x7f) out.write("\\u00" + hex(ch))
      else if (ch < 32) ch match {
        case '\b' => out.write('\\'); out.write('b')
        case '\n' => out.write('\\'); out.write('n')
        case '\t' => out.write('\\'); out.write('t')
        case '\f' => out.write('\\'); out.write('f')
        case '\r' => out.write('\\'); out.write('r')
        case _ =>
          if (ch > 0xf) out.write("\\u00" + hex(ch))
          else out.write("\\u000" + hex(ch))
      }
      else ch match {
        case '\'' =>
          if (escapeSingleQuote) {
        	  out.write('\\')
        	  out.write('\'')
          }
        case '"' => out.write('\\'); out.write('"')
        case '\\' => out.write('\\'); out.write('\\')
        case '/' =>
          if (escapeForwardSlash) {
            out.write('\\')
            out.write('/')
          }
        case _ =>
          out.write(ch);
      }
    }
    out.toString
  }

  def hex(ch: Char) = Integer.toHexString(ch).toUpperCase(Locale.ENGLISH)
}