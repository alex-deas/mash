package com.github.mdr.mash.evaluator

import com.github.mdr.mash.functions.{ BoundParams, MashFunction, Parameter, ParameterModel }
import com.github.mdr.mash.runtime.{ MashList, MashString, MashUnit }
import com.github.mdr.mash.subprocesses.ProcessRunner

/**
 * A function generated by the "!nano" syntax
 */
case class SystemCommandFunction(command: String) extends MashFunction(nameOpt = None) {

  object Params {
    val Args = Parameter(
      nameOpt = Some("args"),
      summaryOpt = Some("Arguments"),
      isVariadic = true)
  }
  import Params._

  val params = ParameterModel(Args)

  def call(boundParams: BoundParams): MashUnit = {
    val args = boundParams(Args) match {
      case MashList(xs: MashList) ⇒ xs.elements
      case xs: MashList           ⇒ xs.elements
      case x                      ⇒ Seq(x)
    }
    ProcessRunner.runProcess(MashString(command) +: args)
    MashUnit
  }

  override def summaryOpt = Some(s"Call the system command '$command'")

}