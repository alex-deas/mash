package com.github.mdr.mash.ns.git

import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.functions.FunctionHelpers
import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.functions.Parameter
import com.github.mdr.mash.functions.ParameterModel
import com.github.mdr.mash.inference.ConstantTypeInferenceStrategy
import com.github.mdr.mash.inference.Type.classToType
import com.github.mdr.mash.ns.core.BooleanClass
import com.github.mdr.mash.os.linux.LinuxFileSystem
import com.github.mdr.mash.runtime.MashBoolean
import com.github.mdr.mash.runtime.MashNull

object IsRepoFunction extends MashFunction("git.isRepo") {

  private val fileSystem = LinuxFileSystem

  object Params {
    val Dir = Parameter(
      name = "dir",
      summary = "Directory to test (default current directory)",
      defaultValueGeneratorOpt = Some(() ⇒ MashNull))
  }
  import Params._

  val params = ParameterModel(Seq(Dir))

  def apply(arguments: Arguments): MashBoolean = {
    val boundParams = params.validate(arguments)
    val path = FunctionHelpers.safeInterpretAsPath(boundParams(Dir)).getOrElse(fileSystem.pwd)
    MashBoolean(GitHelper.isRepository(path))
  }

  override def typeInferenceStrategy = ConstantTypeInferenceStrategy(BooleanClass)

  override def summary = "Return true if a directory is within a Git repository"

}