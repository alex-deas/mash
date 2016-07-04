package com.github.mdr.mash.ns.core

import scala.collection.JavaConverters._
import com.github.mdr.mash.evaluator._
import com.github.mdr.mash.functions.FunctionHelpers._
import com.github.mdr.mash.inference._
import com.github.mdr.mash.os._
import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.functions.ParameterModel
import com.github.mdr.mash.Singletons
import com.github.mdr.mash.repl.history.History
import scala.collection.immutable.ListMap
import com.github.mdr.mash.repl.history.HistoryEntry
import com.github.mdr.mash.runtime.MashObject
import com.github.mdr.mash.runtime.MashString
import com.github.mdr.mash.runtime.MashNumber
import com.github.mdr.mash.runtime.MashList
import com.github.mdr.mash.runtime.MashNull
import com.github.mdr.mash.runtime.MashBoolean
import com.github.mdr.mash.runtime.MashWrapped
import com.github.mdr.mash.functions.FunctionHelpers

object HistoryFunction extends MashFunction("os.history") {

  private def history: History = Singletons.history

  val params = ParameterModel()

  def apply(arguments: Arguments): MashList = {
    params.validate(arguments)
    MashList(history.getHistory.reverse.map(asObject))
  }

  private def asObject(entry: HistoryEntry): MashObject = {
    import HistoryClass.Fields._
    MashObject(
      ListMap(
        Session -> entry.sessionIdOpt.map(id ⇒ MashString(id.toString)).getOrElse(MashNull),
        CommandNumber -> MashNumber(entry.commandNumber),
        Timestamp -> MashWrapped(entry.timestamp),
        Command -> MashString(entry.command),
        Mish -> MashBoolean(entry.mish),
        Result -> entry.resultOpt.getOrElse(MashNull),
        WorkingDirectory -> entry.workingDirectoryOpt.map(FunctionHelpers.asPathString).getOrElse(MashNull)),
      HistoryClass)
  }

  override def typeInferenceStrategy = ConstantTypeInferenceStrategy(Seq(HistoryClass))

  override def summary = "Return the command history as a sequence of History objects"

}