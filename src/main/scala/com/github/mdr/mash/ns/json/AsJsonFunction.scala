package com.github.mdr.mash.ns.json

import scala.collection.JavaConverters._
import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.evaluator.ToStringifier
import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.functions.Parameter
import com.github.mdr.mash.functions.ParameterModel
import com.github.mdr.mash.inference.ConstantTypeInferenceStrategy
import com.github.mdr.mash.inference.Type
import com.github.mdr.mash.inference.TypedArguments
import com.github.mdr.mash.ns.core.StringClass
import com.github.mdr.mash.runtime._
import com.google.gson._
import java.io.StringWriter
import com.google.gson.stream.JsonWriter
import com.google.gson.internal.Streams

object AsJsonFunction extends MashFunction("json.asJson") {

  object Params {
    val Value = Parameter(
      name = "value",
      summary = "Value to convert to a JSON string")
  }
  import Params._

  val params = ParameterModel(Seq(Value))

  def apply(arguments: Arguments): MashString = {
    val boundParams = params.validate(arguments)
    val value = boundParams(Value)
    val json = asJson(value)
    val stringWriter = new StringWriter
    val  jsonWriter = new JsonWriter(stringWriter)
    jsonWriter.setLenient(true)
    jsonWriter.setIndent("  ")
    Streams.write(json, jsonWriter);
    stringWriter.toString
    MashString(stringWriter.toString)
  }

  def asJson(value: MashValue): JsonElement = value match {
    case MashBoolean.True  ⇒ new JsonPrimitive(true)
    case MashBoolean.False ⇒ new JsonPrimitive(false)
    case n: MashNumber ⇒
      n.asInt.map(i ⇒ new JsonPrimitive(i)).getOrElse(new JsonPrimitive(n.n))
    case MashString(s, _) ⇒ new JsonPrimitive(s)
    case MashNull         ⇒ JsonNull.INSTANCE
    case list: MashList ⇒
      val array = new JsonArray
      for (item ← list.items)
        array.add(asJson(item))
      array
    case obj: MashObject ⇒
      val jsonObj = new JsonObject
      for ((field, value) ← obj.fields)
        jsonObj.add(field, asJson(value))
      jsonObj
    case _ ⇒
      new JsonPrimitive(ToStringifier.stringify(value))
  }

  override def typeInferenceStrategy = ConstantTypeInferenceStrategy(StringClass)

  override def summary = "Convert the given value to a JSON string"
}
