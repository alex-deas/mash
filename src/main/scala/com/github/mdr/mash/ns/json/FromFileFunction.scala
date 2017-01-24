package com.github.mdr.mash.ns.json

import java.nio.charset.StandardCharsets

import com.github.mdr.mash.completions.CompletionSpec
import com.github.mdr.mash.evaluator.{ Arguments, EvaluatorException }
import com.github.mdr.mash.functions.{ MashFunction, Parameter, ParameterModel }
import com.github.mdr.mash.inference.{ ConstantTypeInferenceStrategy, Type, TypedArguments }
import com.github.mdr.mash.os.linux.LinuxFileSystem
import com.github.mdr.mash.runtime._
import com.google.gson._
import org.apache.commons.io.FileUtils

import scala.collection.JavaConverters._

object FromFileFunction extends MashFunction("json.fromFile") {

  private val filesystem = LinuxFileSystem

  object Params {
    val File = Parameter(
      nameOpt = Some("file"),
      summaryOpt = Some("File from which to read lines"))
  }
  import Params._

  val params = ParameterModel(Seq(File))

  def apply(arguments: Arguments): MashValue = {
    val boundParams = params.validate(arguments)
    val path = boundParams.validatePath(File)
    val s = FileUtils.readFileToString(path.toFile, StandardCharsets.UTF_8)
    parseJson(s)
  }

  def parseJson(s: String): MashValue = {
    val json = new JsonParser().parse(s)
    asMashObject(json)
  }

  private def asMashObject(e: JsonElement): MashValue = e match {
    case _: JsonNull      ⇒ MashNull
    case array: JsonArray ⇒ MashList(array.iterator.asScala.toSeq.map(asMashObject))
    case p: JsonPrimitive ⇒
      if (p.isNumber) MashNumber(p.getAsDouble)
      else if (p.isBoolean) MashBoolean(p.getAsBoolean)
      else if (p.isString) MashString(p.getAsString)
      else throw new EvaluatorException("Unknown primitive in JSON: " + p)
    case obj: JsonObject ⇒
      val fields =
        for (x ← obj.entrySet.iterator.asScala.toSeq)
          yield x.getKey -> asMashObject(x.getValue)
      MashObject.of(fields)
  }

  override def typeInferenceStrategy = ConstantTypeInferenceStrategy(Type.Any)

  override def getCompletionSpecs(argPos: Int, arguments: TypedArguments) = Seq(CompletionSpec.File)

  override def summaryOpt = Some("Read the given file and parse it as JSON")
}