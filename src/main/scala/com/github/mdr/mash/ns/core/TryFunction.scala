package com.github.mdr.mash.ns.core

import com.github.mdr.mash.evaluator.{ Arguments, EvaluationInterruptedException }
import com.github.mdr.mash.functions.{ BoundParams, MashFunction, Parameter, ParameterModel }
import com.github.mdr.mash.inference._
import com.github.mdr.mash.runtime.MashValue

object TryFunction extends MashFunction("core.try") {

  object Params {
    val Body = Parameter(
      nameOpt = Some("body"),
      summaryOpt = Some("Code to execute"),
      isLazy = true)
    val Catch = Parameter(
      nameOpt = Some("catch"),
      summaryOpt = Some("Code to execute if an exception is thrown in the body"),
      isLazy = true)
  }

  import Params._

  val params = ParameterModel(Seq(Body, Catch))

  def apply(boundParams: BoundParams): MashValue = {
    val body = boundParams(Body).asInstanceOf[MashFunction]
    val catchBlock = boundParams(Catch).asInstanceOf[MashFunction]
    try
      body.applyNullary()
    catch {
      case e: EvaluationInterruptedException ⇒ throw e
      case _: Throwable ⇒ catchBlock.applyNullary()
    }
  }

  override def typeInferenceStrategy = new TypeInferenceStrategy {

    def inferTypes(inferencer: Inferencer, arguments: TypedArguments): Option[Type] =
      params.bindTypes(arguments).getType(Body)

  }

  override def summaryOpt = Some("Execute the given code, catching any exceptions")

}