package com.github.mdr.mash.ns.git

import java.io.File

import org.eclipse.jgit.api.Git

import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.functions.Parameter
import com.github.mdr.mash.functions.ParameterModel
import com.github.mdr.mash.inference.ConstantTypeInferenceStrategy
import com.github.mdr.mash.inference.Type.unitToType
import com.github.mdr.mash.runtime.MashNull
import com.github.mdr.mash.runtime.MashUnit

object CloneFunction extends MashFunction("git.clone") {

  object Params {
    val Repository = Parameter(
      name = "repository",
      summary = "Repository to clone")
    val Directory = Parameter(
      name = "directory",
      summary = "Name to give to new repository directory",
      defaultValueGeneratorOpt = Some(() ⇒ MashNull))
  }
  import Params._

  val params = ParameterModel(Seq(Repository, Directory))

  def apply(arguments: Arguments): MashUnit = {
    val boundParams = params.validate(arguments)
    val repository = boundParams.validateString(Repository).s
    val directoryOpt = boundParams.validateStringOpt(Directory).map(_.s)
    val cmd = Git.cloneRepository
    cmd.setURI(repository)
    cmd.setCloneAllBranches(true)
    cmd.setNoCheckout(false)
    for (directory ← directoryOpt)
      cmd.setDirectory(new File(directory))
    val repo = cmd.call()
    repo.close()
    MashUnit
  }

  override def typeInferenceStrategy = ConstantTypeInferenceStrategy(Unit)

  override def summary = "Clone a repository into a new directory."

}