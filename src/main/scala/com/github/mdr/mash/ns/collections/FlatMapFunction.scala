package com.github.mdr.mash.ns.collections

import com.github.mdr.mash.completions.CompletionSpec
import com.github.mdr.mash.evaluator.{ Arguments, EvaluatorException }
import com.github.mdr.mash.functions.{ MashFunction, Parameter, ParameterModel }
import com.github.mdr.mash.inference._
import com.github.mdr.mash.runtime.{ MashString, _ }

import scala.PartialFunction.condOpt

object FlatMapFunction extends MashFunction("collections.flatMap") {

  object Params {
    val F = Parameter(
      nameOpt = Some("f"),
      summary = "Function used to transform elements of the sequence",
      descriptionOpt = Some("Must return a sequence"))
    val WithIndex = Parameter(
      nameOpt = Some("withIndex"),
      shortFlagOpt = Some('i'),
      summary = "Pass index into the function as well as the item",
      defaultValueGeneratorOpt = Some(() ⇒ MashBoolean.False),
      isFlag = true,
      isBooleanFlag = true)
    val Sequence = Parameter(
      nameOpt = Some("sequence"),
      summary = "Sequence to map over",
      isLast = true)
  }

  import Params._

  val params = ParameterModel(Seq(F, Sequence, WithIndex))

  def apply(arguments: Arguments): MashValue = {
    val boundParams = params.validate(arguments)
    val inSequence = boundParams(Sequence)
    val withIndex = boundParams(WithIndex).isTruthy
    val sequence = boundParams.validateSequence(Sequence)
    val mapped: Seq[MashValue] =
      if (withIndex) {
        val f = boundParams.validateFunction2(F).tupled
        zipWithMashIndex(sequence).map(f)
      } else {
        val f = boundParams.validateFunction(F)
        sequence.map(f)
      }
    flatten(mapped, inSequence)
  }

  private def zipWithMashIndex[T](items: Seq[T]): Seq[(T, MashNumber)] =
    items.zipWithIndex.map { case (item, index) ⇒ item -> MashNumber(index) }

  def flatten(mappedValues: Seq[MashValue], inSequence: MashValue): MashValue = {
    if (mappedValues.isEmpty)
      inSequence
    else if (mappedValues.forall(_.isAList))
      mappedValues.asInstanceOf[Seq[MashList]].fold(MashList.empty)(_ ++ _)
    else if (mappedValues.forall(_.isAString)) {
      val tagOpt = condOpt(inSequence) { case MashString(_, Some(tag)) ⇒ tag }
      mappedValues.asInstanceOf[Seq[MashString]].fold(MashString("", tagOpt))(_ + _)
    } else {
      val first = mappedValues.head // safe, mappedValues not empty
      val rest = mappedValues.tail
      val badItem =
        if (first.isAString)
          rest.find(x ⇒ !x.isAString).get // safe, because of above forall check
        else if (first.isAList)
          rest.find(x ⇒ !x.isAList).get // safe, because of above forall check
        else
          first
      throw new EvaluatorException("Invalid item of type " + badItem.typeName)

    }
  }

  override def typeInferenceStrategy = FlatMapTypeInferenceStrategy

  override def getCompletionSpecs(argPos: Int, arguments: TypedArguments) = {
    val argBindings = FlatMapFunction.params.bindTypes(arguments)
    val specOpt =
      for {
        param ← argBindings.paramAt(argPos)
        if param == F
        Type.Seq(elementType) ← argBindings.getType(Sequence)
      } yield CompletionSpec.Members(elementType)
    specOpt.toSeq
  }

  override def summary = "Transform each element of a sequence by a given function, and then flatten"

  override def descriptionOpt = Some(
    """The given function is applied to each element of the input sequence
  and is expected to yield a sequence for each element. The result is flattened to produce a sequence of transformed 
  output elements.

Examples:
  flatMap (x => [x * 10, x * 100]) [1, 2, 3] # [20, 200, 40, 400, 60, 600]""")

}

object FlatMapTypeInferenceStrategy extends TypeInferenceStrategy {

  import FlatMapFunction.Params._

  def inferTypes(inferencer: Inferencer, arguments: TypedArguments): Option[Type] = {
    val argBindings = FlatMapFunction.params.bindTypes(arguments)
    val functionOpt = argBindings.getArgument(F)
    val sequenceTypeOpt = argBindings.getType(Sequence)
    val newElementSeqTypeOpt = MapTypeInferenceStrategy.inferMappedType(inferencer, functionOpt, sequenceTypeOpt)
    val newElementType = newElementSeqTypeOpt match {
      case Some(Type.Seq(newElementType)) ⇒ newElementType
      case _                              ⇒ Type.Any
    }
    Some(newElementType.seq)
  }

}