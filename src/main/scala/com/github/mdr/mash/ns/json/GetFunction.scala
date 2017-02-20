package com.github.mdr.mash.ns.json

import java.net.URI

import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.functions.{ MashFunction, ParameterModel }
import com.github.mdr.mash.ns.core.ObjectClass
import com.github.mdr.mash.ns.http.ResponseClass.Wrapper
import com.github.mdr.mash.ns.http.{ BasicCredentials, Header, HttpFunctions, HttpOperations }
import com.github.mdr.mash.ns.json.FromFileFunction.parseJson
import com.github.mdr.mash.runtime._
import org.apache.http.client.methods.HttpGet

object GetFunction extends MashFunction("json.get") {
  import HttpFunctions.Params._

  val params = ParameterModel(Seq(Url, BasicAuth, Headers))

  def apply(arguments: Arguments): MashValue = {
    val boundParams = params.validate(arguments)
    val headers = Header.getHeaders(boundParams, Headers)
    val url = new URI(boundParams.validateString(Url).s)
    val basicCredentialsOpt = BasicCredentials.getBasicCredentials(boundParams, BasicAuth)
    val result = HttpOperations.runRequest(new HttpGet(url), headers, basicCredentialsOpt)
    parseJson(Wrapper(result).body)
  }

  override def typeInferenceStrategy = ObjectClass

  override def summaryOpt = Some("Make an HTTP GET request and return the response as JSON")

}