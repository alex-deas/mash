package com.github.mdr.mash.ns.os.pathClass

import com.github.mdr.mash.evaluator.{ Arguments, EvaluatorException }
import com.github.mdr.mash.functions.{ BoundParams, FunctionHelpers, MashMethod, ParameterModel }
import com.github.mdr.mash.ns.core.UnitClass
import com.github.mdr.mash.ns.os.ChangeDirectoryFunction
import com.github.mdr.mash.runtime.{ MashUnit, MashValue }

object PathClassCdMethod extends MashMethod("cd") {

  val params = ParameterModel()

  def apply(target: MashValue, boundParams: BoundParams): MashUnit = {
    val path = FunctionHelpers.interpretAsPath(target)
    import ChangeDirectoryFunction._
    changeDirectory(path) match {
      case Success ⇒
        MashUnit
      case NotADirectory ⇒
        throw new EvaluatorException(s"Could not change directory to '$path', not a directory")
    }
  }

  override def typeInferenceStrategy = UnitClass

  override def summaryOpt = Some("Change directory to this path")

}