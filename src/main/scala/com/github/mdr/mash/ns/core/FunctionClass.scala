package com.github.mdr.mash.ns.core

import com.github.mdr.mash.classes.MashClass
import com.github.mdr.mash.evaluator._
import com.github.mdr.mash.functions._
import com.github.mdr.mash.ns.core.help.{ FunctionHelpClass, HelpCreator }
import com.github.mdr.mash.runtime.{ MashList, MashObject, MashValue }

object FunctionClass extends MashClass("core.Function") {

  override val methods = Seq(
    HelpMethod,
    InvokeMethod)

  object HelpMethod extends MashMethod("help") {

    val params = ParameterModel()

    def apply(target: MashValue, boundParams: BoundParams): MashObject = {
      HelpCreator.getHelp(target)
    }

    override def typeInferenceStrategy = FunctionHelpClass

    override def summaryOpt = Some("Help documentation for this function")
  }

  object InvokeMethod extends MashMethod("invoke") {

    object Params {
      val Args = Parameter(
        nameOpt = Some("args"),
        summaryOpt = Some("Positional arguments for this function"),
        defaultValueGeneratorOpt = Option(() ⇒ MashList.empty))
      val NamedArgs = Parameter(
        nameOpt = Some("namedArgs"),
        summaryOpt = Some("Named arguments for this function"),
        defaultValueGeneratorOpt = Option(() ⇒ MashObject.empty))
    }

    import Params._

    val params = ParameterModel(Seq(Args, NamedArgs))

    def apply(target: MashValue, boundParams: BoundParams): MashValue = {
      val args = boundParams.validateSequence(Args)
      val namedArgs = boundParams.validateObject(NamedArgs)
      val functionArguments = Arguments(args.map(v ⇒ EvaluatedArgument.PositionArg(SuspendedMashValue(() ⇒ v))) ++
      namedArgs.fields.toSeq.map { case (field, value) ⇒
        EvaluatedArgument.LongFlag(field, Some(SuspendedMashValue(() ⇒ value)))
      })
      target.asInstanceOf[MashFunction].apply(functionArguments)
    }

    override def summaryOpt = Some("Invoke this function with the given arguments")

  }

  override def summaryOpt = Some("A function")

  override def parentOpt = Some(AnyClass)

}