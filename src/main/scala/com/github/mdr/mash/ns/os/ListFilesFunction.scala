package com.github.mdr.mash.ns.os

import java.nio.file.Files
import java.nio.file.Path

import scala.collection.JavaConverters._
import com.github.mdr.mash.completions.CompletionSpec
import com.github.mdr.mash.evaluator._
import com.github.mdr.mash.functions.FunctionHelpers._
import com.github.mdr.mash.inference._
import com.github.mdr.mash.os._
import com.github.mdr.mash.os.linux.LinuxFileSystem
import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.functions.ParameterModel
import com.github.mdr.mash.functions.Parameter
import com.github.mdr.mash.functions.FunctionHelpers
import com.github.mdr.mash.runtime.MashObject
import com.github.mdr.mash.runtime.MashList
import com.github.mdr.mash.runtime.MashBoolean

object ListFilesFunction extends MashFunction("os.listFiles") {

  private val fileSystem: FileSystem = LinuxFileSystem

  object Params {
    val Paths = Parameter(
      name = "paths",
      summary = "Paths to list files",
      isVariadic = true,
      defaultValueGeneratorOpt = Some(() ⇒ MashList.of(asPathString(""))),
      descriptionOpt = Some(s"""Paths can either be strings or ${PathSummaryClass.fullyQualifiedName} objects. 
If a given path is a file, it will be included in the output. 
If a given path is a directory, its children will be included, unless the
   directory parameter is true, then it will be included directly. 
If no paths are provided, the default is the current working directory."""))
    val All = Parameter(
      name = "all",
      summary = "Include files starting with a dot (default false)",
      shortFlagOpt = Some('a'),
      isFlag = true,
      defaultValueGeneratorOpt = Some(() ⇒ MashBoolean.False),
      isBooleanFlag = true)
    val Recursive = Parameter(
      name = "recursive",
      summary = "Recursively retrieve results from directories (default false)",
      shortFlagOpt = Some('r'),
      isFlag = true,
      defaultValueGeneratorOpt = Some(() ⇒ MashBoolean.False),
      isBooleanFlag = true)
    val Directory = Parameter(
      name = "directory",
      summary = "List directories themselves, not their contents (default false)",
      shortFlagOpt = Some('d'),
      isFlag = true,
      defaultValueGeneratorOpt = Some(() ⇒ MashBoolean.False),
      isBooleanFlag = true)
  }

  import Params._

  val params = ParameterModel(Seq(Paths, All, Recursive, Directory))

  def apply(arguments: Arguments): MashList = {
    val boundParams = params.validate(arguments)
    val ignoreDotFiles = boundParams(All).isFalsey
    val recursive = boundParams(Recursive).isTruthy
    val directory = boundParams(Directory).isTruthy
    val paths = boundParams.validatePaths(Paths)

    def listPath(path: Path): Seq[MashObject] =
      if (Files.isDirectory(path) && !directory)
        ChildrenFunction.getChildren(path, ignoreDotFiles = ignoreDotFiles, recursive = recursive)
      else
        Seq(PathSummaryClass.asMashObject(fileSystem.getPathSummary(path)))
    MashList(paths.flatMap(listPath))
  }

  override def typeInferenceStrategy = ConstantTypeInferenceStrategy(Seq(PathSummaryClass))

  override def getCompletionSpecs(argPos: Int, arguments: TypedArguments) = Seq(CompletionSpec.File)

  override def summary = "List files"

  override def descriptionOpt = Some(s"""List files and directories, returning a sequence of ${PathSummaryClass.fullyQualifiedName} objects. 
If no paths are supplied, the current directory is used as the default.""")

}