package com.github.mdr.mash.ns.collections

import com.github.mdr.mash.functions._
import com.github.mdr.mash.inference._
import com.github.mdr.mash.runtime._

object SortByFunction extends MashFunction("collections.sortBy") {

  object Params {
    val Descending = Parameter(
      nameOpt = Some("descending"),
      shortFlagOpt = Some('d'),
      summaryOpt = Some("Sort results from highest value to lowest"),
      defaultValueGeneratorOpt = Some(MashBoolean.False),
      isFlag = true,
      isBooleanFlag = true)
    val Attributes = Parameter(
      nameOpt = Some("attributes"),
      summaryOpt = Some("Function(s) to extract a value to compare elements"),
      isVariadic = true,
      variadicAtLeastOne = true)
    val Sequence = Parameter(
      nameOpt = Some("sequence"),
      summaryOpt = Some("Sequence to sort"),
      isLast = true)
  }

  import Params._

  val params = ParameterModel(Seq(Descending, Attributes, Sequence))

  def apply(boundParams: BoundParams): MashValue = {
    val inSequence = boundParams(Sequence)
    val sequence = boundParams.validateSequence(Sequence)
    val descending = boundParams(Descending).isTruthy
    val attributes: Seq[MashValue ⇒ MashValue] =
      boundParams.validateSequence(Attributes, allowStrings = false).map(boundParams.validateFunction(Attributes, _))
    val sorted = sequence.sortWith((a, b) ⇒ {
      val as = MashList(attributes.map(_ (a)))
      val bs = MashList(attributes.map(_ (b)))
      MashValueOrdering.lt(as, bs)
    })
    val newSequence = if (descending) sorted.reverse else sorted
    WhereFunction.reassembleSequence(inSequence, newSequence)
  }

  override def typeInferenceStrategy = WhereTypeInferenceStrategy

  override def getCompletionSpecs(argPos: Int, arguments: TypedArguments) =
    MapFunction.getCompletionSpecs(argPos, arguments)

  override def summaryOpt = Some("Sort the elements of a sequence by an attribute")

  override def descriptionOpt = Some(
    """Examples:
  sortBy length ["aa", "b", "ccc"]              # ["b", "aa", "ccc"] 
  sortBy --descending length ["aa", "b", "ccc"] # ["ccc", "aa", "b"]""")

}
