package com.github.mdr.mash.ns.net

import java.net.{ URI, URISyntaxException }

import com.github.mdr.mash.evaluator.ToStringifier
import com.github.mdr.mash.functions.{ BoundParams, MashFunction, Parameter, ParameterModel }
import com.github.mdr.mash.ns.core.StringClass
import com.github.mdr.mash.runtime.MashString

object UrlFunction extends MashFunction("net.url") {

  object Params {
    val Url = Parameter(
      nameOpt = Some("url"),
      summaryOpt = Some("String to interpret as a URL"))
  }
  import Params._

  val params = ParameterModel(Url)

  def call(boundParams: BoundParams): MashString = {
    val s = ToStringifier.stringify(boundParams(Url))
    try
      new URI(s)
    catch {
      case e: URISyntaxException ⇒ boundParams.throwInvalidArgument(Url, e.getMessage)
    }
    MashString(s, UrlClass)
  }

  override def typeInferenceStrategy = StringClass taggedWith UrlClass

  override def summaryOpt = Some("Interpret the given value as a URL")
}