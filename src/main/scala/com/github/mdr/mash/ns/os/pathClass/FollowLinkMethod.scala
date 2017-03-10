package com.github.mdr.mash.ns.os.pathClass

import java.nio.file.Files

import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.functions.{ BoundParams, FunctionHelpers, MashMethod, ParameterModel }
import com.github.mdr.mash.ns.core.StringClass
import com.github.mdr.mash.ns.os.PathClass
import com.github.mdr.mash.runtime.{ MashString, MashValue }

object FollowLinkMethod extends MashMethod("followLink") {

  val params = ParameterModel()

  def apply(target: MashValue, boundParams: BoundParams): MashString = {
    val path = FunctionHelpers.interpretAsPath(target)
    val resolved = Files.readSymbolicLink(path)
    MashString(resolved.toString, PathClass)
  }

  override def typeInferenceStrategy = StringClass taggedWith PathClass

  override def summaryOpt = Some("Follow this symbolic link")

}