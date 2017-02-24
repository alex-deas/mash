package com.github.mdr.mash.ns.collections

import com.github.mdr.mash.completions.CompletionSpec
import com.github.mdr.mash.evaluator._
import com.github.mdr.mash.functions._
import com.github.mdr.mash.inference._
import com.github.mdr.mash.runtime._

import scala.collection.immutable.ListMap

/**
 * Select members of an object
 */
object SelectFunction extends MashFunction("collections.select") {

  private val AddShortFlag = 'a'

  object Params {
    val Target = Parameter(
      nameOpt = Some("target"),
      summaryOpt = Some("Either an object or sequence of objects to select fields from"),
      isLast = true)
    val Add = Parameter(
      nameOpt = Some("add"),
      summaryOpt = Some("Add the fields to the existing set of members, rather than replacing"),
      shortFlagOpt = Some(AddShortFlag),
      defaultValueGeneratorOpt = Some(() ⇒ MashBoolean.False),
      isFlag = true)
  }
  import Params._

  val params = ParameterModel(Seq(Target, Add))

  override def apply(boundParams: BoundParams): MashValue = ??? // Not used

  override def apply(arguments: Arguments): MashValue = {
    val boundParams = params.validate(arguments, ignoreAdditionalParameters = true)
    val add = boundParams(Add).isTruthy
    val target = boundParams(Target)
    val fieldsAndFunctions: Seq[(String, MashValue ⇒ MashValue)] = arguments.evaluatedArguments.init.flatMap {
      case EvaluatedArgument.PositionArg(suspendedValue, argumentNodeOpt) ⇒
        suspendedValue.resolve() match {
          case s: MashString ⇒
            Some(s.s -> FunctionHelpers.interpretAsFunction(s))
          case _ ⇒
            throw new ArgumentException("Positional arguments must be strings", argumentNodeOpt.flatMap(_.sourceInfoOpt).map(_.location))
        }
      case EvaluatedArgument.ShortFlag(flags, argumentNodeOpt) ⇒
        if (flags == Seq(AddShortFlag.toString))
          None
        else
          throw new ArgumentException("Short flags not supported by select", argumentNodeOpt.flatMap(_.sourceInfoOpt).map(_.location))
      case EvaluatedArgument.LongFlag(flag, None, _) ⇒
        if (Add.nameOpt contains flag)
          None
        else
          Some(flag -> FunctionHelpers.interpretAsFunction(MashString(flag)))
      case EvaluatedArgument.LongFlag(flag, Some(value), _) ⇒
        Some(flag -> FunctionHelpers.interpretAsFunction(value.resolve()))
    }
    target match {
      case xs: MashList ⇒ xs.map(doSelect(_, fieldsAndFunctions, add))
      case x            ⇒ doSelect(x, fieldsAndFunctions, add)
    }
  }

  private def doSelect(target: MashValue, fieldsAndFunctions: Seq[(String, MashValue ⇒ MashValue)], add: Boolean): MashObject = {
    val baseFields: ListMap[String, MashValue] =
      if (add) {
        target match {
          case MashObject(fields, _) ⇒ ListMap(fields.toSeq: _*)
          case _                     ⇒ ListMap()
        }
      } else
        ListMap()
    val mapPairs =
      for ((field, f) ← fieldsAndFunctions)
        yield field -> f(target)
    val newFields = ListMap(mapPairs: _*)
    MashObject.of(baseFields ++ newFields)
  }

  override def typeInferenceStrategy = SelectTypeInferenceStrategy

  override def getCompletionSpecs(argPos: Int, arguments: TypedArguments) =
    if (argPos < arguments.arguments.size - 1)
      arguments.arguments.last match {
        case TypedArgument.PositionArg(expr) ⇒
          expr.typeOpt match {
            case Some(Type.Seq(elementType)) ⇒ Seq(CompletionSpec.Members(elementType))
            case Some(targetType)            ⇒ Seq(CompletionSpec.Members(targetType))
            case _                           ⇒ Seq()
          }
        case _ ⇒ Seq()
      }
    else
      Seq()

  override def summaryOpt = Some("Select members of an object or sequence of objects")

  override def descriptionOpt = Some("""If the input is a sequence, the selection is applied to every element and a 
  sequence of new objects is returned.
If the input is a single object, a single new object is returned with the selection applied.

The arguments are interpreted as follows:
  1) A positional string argument -- the input object member with that name is copied to an output field with 
    the same name.
  2) A flag with no argument -- the input member with the same name as the flag is copied to an output field with
   the same name. 
  3) A flag with an argument -- the flag argument is applied as a function to the input object, and the result 
    is copied to an output field with the flag name.

The order of fields in the argument list is retained in the new output objects.

Examples:
  select "foo" { foo: 42, bar: 24 }              # { foo: 42 }
  select --foo { foo: 42, bar: 24 }              # { foo: 42 }
  select --foo { foo: 42, bar: 24 }              # { foo: 42 }
  select --baz=(_.foo * 2) { foo: 42, bar: 24 }  # { baz: 84 }
  select "bar" "foo" { foo: 42, bar: 24 }        # { bar: 24, foo: 42 }
  select --add --baz=(_.foo * 2) { foo: 42 }     # { foo: 42, baz: 84 }
  select "foo" [{ foo: 42, bar: 24 }, { foo: 12, bar: 18 }] # [{ foo: 42 }, { foo: 12 }]""")
}

