package com.github.mdr.mash.ns.core

import com.github.mdr.mash.evaluator.Field

import com.github.mdr.mash.evaluator.MashClass
import com.github.mdr.mash.inference.Type
import com.github.mdr.mash.ns.time.DateTimeClass

object HistoryClass extends MashClass("core.History") {

  object Fields {
    val Session = Field("session", "ID of shell session", Type.Instance(StringClass))
    val CommandNumber = Field("commandNumber", "Number of the command within the session", Type.Instance(NumberClass))
    val Timestamp = Field("timestamp", "Time command was executed", Type.Instance(DateTimeClass))
    val Command = Field("command", "Command", Type.Instance(StringClass))
    val Mish = Field("mish", "Whether the command was executed in mish mode", Type.Instance(BooleanClass))
  }

  import Fields._

  override val fields = Seq(Session, CommandNumber, Timestamp, Command, Mish)

  override def summary = "A record in Mash command history"

}