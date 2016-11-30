package com.github.mdr.mash.ns.collections

import com.github.mdr.mash.completions.CompletionSpec
import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.functions.{ MashFunction, Parameter, ParameterModel }
import com.github.mdr.mash.inference._
import com.github.mdr.mash.ns.core.ObjectClass.GetMethod
import com.github.mdr.mash.parser.AbstractSyntax.StringLiteral
import com.github.mdr.mash.runtime._

import scala.PartialFunction._

object DeselectFunction extends MashFunction("collections.deselect") {

  object Params {
    val Fields = Parameter(
      name = "fields",
      summary = "Fields from the object",
      isVariadic = true,
      variadicAtLeastOne = true)
    val Target = Parameter(
      name = "target",
      summary = "Object or sequence of objects to remove fields from",
      isLast = true)
  }

  import Params._

  val params = ParameterModel(Seq(Fields, Target))

  def apply(arguments: Arguments): MashValue = {
    val boundParams = params.validate(arguments)
    val fields: Seq[String] = boundParams.validateSequence(Fields).collect {
      case s: MashString => s.s
      case field         => boundParams.throwInvalidArgument(Fields, "Invalid field name of type: " + field.typeName)
    }
    boundParams(Target) match {
      case xs: MashList ⇒ xs.map(doDeselect(_, fields))
      case x            ⇒ doDeselect(x, fields)
    }
  }

  private def doDeselect(value: MashValue, fields: Seq[String]): MashValue = value match {
    case obj: MashObject => MashObject.of(obj.fields.filterNot(fields contains _._1))
    case _               => value
  }

  override def getCompletionSpecs(argPos: Int, arguments: TypedArguments) = {
    val argBindings = params.bindTypes(arguments)
    val completionSpecOpt =
      for {
        param ← argBindings.paramAt(argPos)
        if param == Fields
        targetExpr <- argBindings.get(Target)
        targetType <- targetExpr.typeOpt
        actualTargetType = targetType match {
          case Type.Seq(elemType) => elemType
          case _                  => targetType
        }
      } yield CompletionSpec.Items(GetMethod.getFields(actualTargetType))
    completionSpecOpt.toSeq
  }

  override def summary = "Remove fields from an object or sequence of objects"

}
