package com.github.mdr.mash.ns.collections

import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.functions.ParameterModel
import com.github.mdr.mash.evaluator.MashNumber
import com.github.mdr.mash.evaluator.MashString
import com.github.mdr.mash.functions.Parameter
import com.github.mdr.mash.evaluator.Arguments
import com.github.mdr.mash.functions.FunctionHelpers
import com.github.mdr.mash.utils.Utils
import com.github.mdr.mash.evaluator.EvaluatorException
import com.github.mdr.mash.functions.BoundParams
import com.github.mdr.mash.inference.AnnotatedExpr
import com.github.mdr.mash.inference.Inferencer
import com.github.mdr.mash.inference.TypedArguments
import com.github.mdr.mash.inference.Type
import com.github.mdr.mash.ns.core.StringClass
import scala.PartialFunction.condOpt
import com.github.mdr.mash.inference.TypeInferenceStrategy
import com.github.mdr.mash.evaluator.MashList

object MaxFunction extends MashFunction("collections.max") {

  object Params {
    val Items = Parameter(
      name = "items",
      summary = "Items to find the maximum of",
      descriptionOpt = Some("""If a single argument is provided, it must be a sequence; the largest element of the sequence is returned.
If multiple arguments are provided, the largest argument is returned."""),
      isVariadic = true)
  }

  import Params._

  val params = ParameterModel(Seq(Items))

  def apply(arguments: Arguments): Any = {
    val boundParams = params.validate(arguments)
    val sequence = getSequence(boundParams, Items)
    sequence.max(Utils.AnyOrdering)
  }

  def getSequence(boundParams: BoundParams, itemsParam: Parameter) =
    boundParams.validateSequence(itemsParam) match {
      case Seq() ⇒
        throw new EvaluatorException("Must provide at least one argument")
      case Seq(seq @ (_: MashString | _: MashList)) ⇒
        FunctionHelpers.interpretAsSequence(seq)
      case Seq(other) ⇒
        boundParams.throwInvalidArgument(Items, "A single argument must be a sequence")
      case items ⇒
        items
    }

  override def typeInferenceStrategy = MaxTypeInferenceStrategy

  override def summary = "Find the largest element of a sequence"

  override def descriptionOpt = Some("""Examples:
  max [1, 2, 3] # 3
  max 1 2 3     # 3""")

}

object MaxTypeInferenceStrategy extends TypeInferenceStrategy {

  def inferTypes(inferencer: Inferencer, arguments: TypedArguments): Option[Type] = {
    val argBindings = MaxFunction.params.bindTypes(arguments)
    import MaxFunction.Params._
    if (arguments.positionArgs.size == 1)
      for {
        AnnotatedExpr(_, inputTypeOpt) ← argBindings.get(Items)
        Type.Seq(inputType) ← inputTypeOpt
        elementType ← condOpt(inputType) {
          case Type.Seq(elementType)                                    ⇒ elementType
          case Type.Instance(StringClass) | Type.Tagged(StringClass, _) ⇒ inputType
        }
      } yield elementType
    else
      for {
        AnnotatedExpr(_, inputTypeOpt) ← argBindings.get(Items)
        Type.Seq(elementType) ← inputTypeOpt
      } yield elementType
  }

}