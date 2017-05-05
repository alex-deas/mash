package com.github.mdr.mash.ns.http

import com.github.mdr.mash.functions.{ BoundParams, MashFunction, ParameterModel }
import com.github.mdr.mash.runtime._
import org.apache.http.client.methods.HttpPut

object PutFunction extends MashFunction("http.put") {
  import HttpFunctions.Params._

  val params = ParameterModel(Seq(Url, Body, File, Json, BasicAuth, Headers, QueryParams))

  def call(boundParams: BoundParams): MashObject = {
    val headers = Header.getHeaders(boundParams, Headers)
    val url = QueryParameters.getUrl(boundParams)
    val bodySource = HttpFunctions.getBodySource(boundParams)
    val json = boundParams(Json).isTruthy

    val basicCredentialsOpt = BasicCredentials.getBasicCredentials(boundParams, BasicAuth)
    HttpOperations.runRequest(new HttpPut(url), headers, basicCredentialsOpt, Some(bodySource), json)
  }

  override def typeInferenceStrategy = ResponseClass

  override def summaryOpt = Some("Make an HTTP PUT request")

}