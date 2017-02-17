package com.github.mdr.mash.ns.http

import java.net.URI
import java.nio.charset.StandardCharsets

import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.functions.{ MashFunction, ParameterModel }
import com.github.mdr.mash.runtime._
import org.apache.commons.io.IOUtils
import org.apache.http.HttpResponse
import org.apache.http.client.CookieStore
import org.apache.http.client.config.{ CookieSpecs, RequestConfig }
import org.apache.http.client.methods.HttpGet
import org.apache.http.cookie.Cookie
import org.apache.http.impl.client.{ BasicCookieStore, HttpClients }

import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap

object GetFunction extends MashFunction("http.get") {

  object Params {
    val Url = PostFunction.Params.Url
    val BasicAuth = PostFunction.Params.BasicAuth
    val Headers = PostFunction.Params.Headers
  }

  import Params._

  val params = ParameterModel(Seq(Url, BasicAuth, Headers))

  def apply(arguments: Arguments): MashObject = {
    val boundParams = params.validate(arguments)
    val headers = Header.getHeaders(boundParams, Headers)

    val url = new URI(boundParams.validateString(Url).s)
    val request = new HttpGet(url)
    for (header <- headers)
      request.setHeader(header.name, header.value)
    BasicCredentials.getBasicCredentials(boundParams, BasicAuth).foreach(_.addCredentials(request))

    val cookieStore = new BasicCookieStore
    val client = HttpClients.custom()
      .setDefaultRequestConfig(RequestConfig.custom.setCookieSpec(CookieSpecs.DEFAULT).build())
      .setDefaultCookieStore(cookieStore)
      .setSSLContext(InsecureSsl.makeInsecureSslContext())
      .setSSLHostnameVerifier(InsecureSsl.TrustAllHostnameVerifier)
      .build
    val response = client.execute(request)

    asMashObject(response, cookieStore)
  }

  def asMashObject(response: HttpResponse, cookieStore: CookieStore): MashObject = {
    val code = response.getStatusLine.getStatusCode
    val content = response.getEntity.getContent
    val responseBody = IOUtils.toString(content, StandardCharsets.UTF_8)
    val headers = response.getAllHeaders.map(asMashObject(_))
    val cookies = cookieStore.getCookies.asScala.map(asMashObject(_))
    import ResponseClass.Fields._
    MashObject.of(ListMap(
      Status -> MashNumber(code),
      Body -> MashString(responseBody),
      Headers -> MashList(headers),
      Cookies -> MashList(cookies)), ResponseClass)
  }

  def asMashObject(cookie: Cookie): MashObject = {
    import CookieClass.Fields._
    MashObject.of(ListMap(
      Name -> MashString(cookie.getName),
      Value -> MashString(cookie.getValue)), CookieClass)
  }

  def asMashObject(header: org.apache.http.Header): MashObject = {
    import HeaderClass.Fields._
    MashObject.of(ListMap(
      Name -> MashString(header.getName),
      Value -> MashString(header.getValue)), HeaderClass)
  }

  override def typeInferenceStrategy = ResponseClass

  override def summaryOpt = Some("Make an HTTP GET request")

}