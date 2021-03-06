package com.github.mdr.mash.ns.core.help

import com.github.mdr.mash.classes.{ AbstractObjectWrapper, Field, MashClass, NewStaticMethod }
import com.github.mdr.mash.evaluator.EvaluatorException
import com.github.mdr.mash.ns.core.StringClass
import com.github.mdr.mash.runtime.{ MashString, MashValue }

object FunctionHelpClass extends MashClass("core.help.FunctionHelp") {

  object Fields {
    val Name = Field("name", Some("Function name"), StringClass)
    val FullyQualifiedName = Field("fullyQualifiedName", Some("Fully-qualified name of the function"), StringClass)
    val Aliases = Field("aliases", Some("Aliases of the method"), Seq(StringClass))
    val Summary = Field("summary", Some("Summary of what the function does (possibly null)"), StringClass)
    val CallingSyntax = Field("callingSyntax", Some("Calling syntax"), StringClass)
    val Description = Field("description", Some("Description of the function (possibly null)"), StringClass)
    val Parameters = Field("parameters", Some("Parameters of the function"), Seq(ParameterHelpClass))
    val Class = Field("class", Some("If a method, the class it belongs to (else null)"), StringClass)
  }

  import Fields._

  override val fields = Seq(Name, FullyQualifiedName, Aliases, Summary, CallingSyntax, Description, Parameters, Class)

  override val staticMethods = Seq(NewStaticMethod(this))

  override def summaryOpt = Some("Help documentation for a function")

  case class Wrapper(value: MashValue) extends AbstractObjectWrapper(value) {

    def name: String = getStringField(Name)

    def fullyQualifiedName: String = getStringField(FullyQualifiedName)

    def classOpt: Option[String] = getOptionalStringField(Class)

    def summaryOpt: Option[String] = getOptionalStringField(Summary)

    def descriptionOpt: Option[String] = getOptionalStringField(Description)

    def callingSyntax: String = getStringField(CallingSyntax)

    def aliases: Seq[String] = getListField(Aliases).map {
      case s: MashString ⇒ s.s
      case element       ⇒ throw new EvaluatorException(s"Expected aliases to be of type String, but one was of type '${element.typeName}'")
    }

    def parameters: Seq[MashValue] = getListField(Parameters)


  }

}