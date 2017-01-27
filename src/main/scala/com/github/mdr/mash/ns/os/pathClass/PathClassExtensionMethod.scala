package com.github.mdr.mash.ns.os.pathClass

import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.functions.{ FunctionHelpers, MashMethod, ParameterModel }
import com.github.mdr.mash.inference.ConstantMethodTypeInferenceStrategy
import com.github.mdr.mash.ns.core.StringClass
import com.github.mdr.mash.runtime.{ MashNull, MashString, MashValue }

object PathClassExtensionMethod extends MashMethod("extension") {

  val params = ParameterModel()

  def apply(target: MashValue, arguments: Arguments): MashValue = {
    params.validate(arguments)
    val name = FunctionHelpers.interpretAsPath(target).getFileName.toString
    if (name contains ".")
      MashString(name.reverse.takeWhile(_ != '.').reverse)
    else
      MashNull
  }

  override def typeInferenceStrategy = StringClass

  override def summaryOpt = Some("File extension, if any, else null")

}