package com.github.mdr.mash.ns.os.pathClass

import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.functions.FunctionHelpers._
import com.github.mdr.mash.functions.{ MashMethod, ParameterModel }
import com.github.mdr.mash.ns.core.StringClass
import com.github.mdr.mash.ns.os.PathClass
import com.github.mdr.mash.os.linux.LinuxFileSystem
import com.github.mdr.mash.runtime.{ MashString, MashValue }

object PathClassAbsoluteMethod extends MashMethod("absolute") {

  private val fileSystem = LinuxFileSystem

  val params = ParameterModel()

  def apply(target: MashValue, arguments: Arguments): MashString = {
    params.validate(arguments)
    val path = interpretAsPath(target)
    asPathString(fileSystem.pwd.resolve(path).toRealPath())
  }

  override def typeInferenceStrategy = StringClass taggedWith PathClass

  override def summaryOpt = Some("The absolute path to this location")

}