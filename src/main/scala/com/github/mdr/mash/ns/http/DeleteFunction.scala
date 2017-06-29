package com.github.mdr.mash.ns.http

import com.github.mdr.mash.functions.{ BoundParams, MashFunction, ParameterModel }
import com.github.mdr.mash.runtime._
import org.apache.http.client.methods.HttpDelete

object DeleteFunction extends MashFunction("http.delete") {

  import HttpFunctions.Params._

  val params = ParameterModel(Url, BasicAuth, Headers, Cookies, QueryParams, Json)

  def call(boundParams: BoundParams): MashObject = {
    val headers = Header.getHeaders(boundParams, Headers)
    val cookies = Cookie.getCookies(boundParams, Cookies)
    val url = QueryParameters.getUrl(boundParams)
    val json = boundParams(Json).isTruthy
    val basicCredentialsOpt = BasicCredentials.getBasicCredentials(boundParams, BasicAuth)
    HttpOperations.runRequest(new HttpDelete(url), headers, cookies, basicCredentialsOpt, json = json)
  }

  override def typeInferenceStrategy = ResponseClass

  override def summaryOpt = Some("Make an HTTP DELETE request")

}