package edu.umass.cs.iesl.watr
package native
package mousetrap

import scala.scalajs.js
import scala.scalajs.js.annotation.JSName

@js.native
object Mousetrap extends js.Object {
  def bind(keys: js.Array[String], fn: js.Function1[MousetrapEvent, Boolean]) : js.Any = js.native
  def bind(key: String, fn: js.Function1[MousetrapEvent, Boolean]) : js.Any = js.native
  def bind(key: String, fn: js.Function1[MousetrapEvent, Boolean], mod: String = "keypress") : js.Any = js.native
  def unbind(key : String) : js.Any = js.native
  def reset() : js.Any = js.native
}


@js.native
trait MousetrapEvent extends js.Object