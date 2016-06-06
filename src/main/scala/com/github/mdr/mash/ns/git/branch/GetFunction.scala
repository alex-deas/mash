package com.github.mdr.mash.ns.git.branch

import com.github.mdr.mash.completions.CompletionSpec
import com.github.mdr.mash.evaluator._
import com.github.mdr.mash.functions._
import com.github.mdr.mash.inference.ConstantTypeInferenceStrategy
import com.github.mdr.mash.inference.TypedArguments
import com.github.mdr.mash.ns.git.GitHelper
import org.eclipse.jgit.api.Git
import scala.collection.JavaConverters._
import com.github.mdr.mash.runtime.MashObject
import com.github.mdr.mash.runtime.MashNull
import com.github.mdr.mash.runtime.MashValue

object GetFunction extends MashFunction("git.branch.get") {

  object Params {
    val Branch = Parameter(
      name = "branch",
      summary = "Local branch to get")
  }
  import Params._

  val params = ParameterModel(Seq(Branch))

  def apply(arguments: Arguments): MashValue = {
    val boundParams = params.validate(arguments)
    val branch = DeleteFunction.validateBranch(boundParams, Branch, boundParams(Branch))
    GitHelper.withRepository { repo ⇒
      val git = new Git(repo)
      git.branchList.call().asScala.find(_.getName == "refs/heads/" + branch).map(ListFunction.asMashObject(repo)).getOrElse(MashNull)
    }
  }

  override def getCompletionSpecs(argPos: Int, arguments: TypedArguments) =
    params.bindTypes(arguments).paramAt(argPos).toSeq.collect {
      case Branch ⇒ CompletionSpec.Items(SwitchFunction.getLocalBranches)
    }

  override def typeInferenceStrategy = ConstantTypeInferenceStrategy(LocalBranchClass)

  override def summary = "Get a local branch by name"
}