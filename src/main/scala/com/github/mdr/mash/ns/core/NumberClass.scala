package com.github.mdr.mash.ns.core

import com.github.mdr.mash.evaluator._
import com.github.mdr.mash.inference._
import com.github.mdr.mash.ns.time._
import com.github.mdr.mash.functions.MashMethod
import com.github.mdr.mash.functions.ParameterModel
import com.github.mdr.mash.runtime.MashNumber
import com.github.mdr.mash.runtime.MashNull
import com.github.mdr.mash.runtime.MashValue

object NumberClass extends MashClass("core.Number") {

  import MashClass.alias

  override val methods = Seq(
    BytesMethod,
    DaysMethod,
    GbMethod,
    HoursMethod,
    KbMethod,
    MbMethod,
    MillisecondsMethod,
    MinutesMethod,
    MonthsMethod,
    NegateMethod,
    SecondsMethod,
    TagMethod,
    ToIntMethod,
    UntaggedMethod,
    WeeksMethod,
    alias("day", DaysMethod),
    alias("hour", HoursMethod),
    alias("millisecond", MillisecondsMethod),
    alias("minute", MinutesMethod),
    alias("month", MonthsMethod),
    alias("second", SecondsMethod),
    alias("week", WeeksMethod))

  object ToIntMethod extends MashMethod("toInt") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashNumber = {
      params.validate(arguments)
      target.asInstanceOf[MashNumber].modify(n ⇒ n.toInt)
    }

    override def typeInferenceStrategy = new MethodTypeInferenceStrategy {
      def inferTypes(inferencer: Inferencer, targetTypeOpt: Option[Type], arguments: TypedArguments): Option[Type] =
        targetTypeOpt
    }

    override def summary = "Convert number to an integer (rounding towards zero)"

  }
  
  object TagMethod extends MashMethod("tag") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashValue = {
      params.validate(arguments)
      target.asInstanceOf[MashNumber].tagClassOpt.getOrElse(MashNull)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(ClassClass)

    override def summary = "This number's tagged type if any, else null"

  }

  object UntaggedMethod extends MashMethod("untagged") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashNumber = {
      params.validate(arguments)
      target.asInstanceOf[MashNumber].copy(tagClassOpt = None)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(NumberClass)

    override def summary = "This number without any tag class"
  }

  object BytesMethod extends MashMethod("bytes") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashNumber = {
      params.validate(arguments)
      target.asInstanceOf[MashNumber].copy(tagClassOpt = Some(BytesClass))
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(NumberClass, BytesClass))

    override def summary = "This number of bytes"
  }

  object KbMethod extends MashMethod("kb") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashNumber = {
      params.validate(arguments)
      val n = target.asInstanceOf[MashNumber]
      MashNumber(n.n * 1024, BytesClass)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(NumberClass, BytesClass))

    override def summary = "This number of kilobytes"
  }

  object MbMethod extends MashMethod("mb") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashNumber = {
      params.validate(arguments)
      val n = target.asInstanceOf[MashNumber]
      MashNumber(n.n * 1024 * 1024, BytesClass)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(NumberClass, BytesClass))

    override def summary = "This number of megabytes"
  }

  object GbMethod extends MashMethod("gb") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashNumber = {
      params.validate(arguments)
      val n = target.asInstanceOf[MashNumber]
      MashNumber(n.n * 1024 * 1024 * 1024, BytesClass)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(NumberClass, BytesClass))

    override def summary = "This number of gigabytes"

  }

  abstract class ChronoUnitMethod(name: String, klass: MashClass) extends MashMethod(name) {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashNumber = {
      params.validate(arguments)
      target.asInstanceOf[MashNumber].withTag(klass)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(NumberClass, klass))

    override def summary = "This number of " + name

  }

  object MillisecondsMethod extends ChronoUnitMethod("milliseconds", MillisecondsClass)
  object SecondsMethod extends ChronoUnitMethod("seconds", SecondsClass)
  object MinutesMethod extends ChronoUnitMethod("minutes", MinutesClass)
  object HoursMethod extends ChronoUnitMethod("hours", HoursClass)
  object DaysMethod extends ChronoUnitMethod("days", DaysClass)
  object WeeksMethod extends ChronoUnitMethod("weeks", WeeksClass)
  object MonthsMethod extends ChronoUnitMethod("months", MonthsClass)

  object NegateMethod extends MashMethod("negate") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashNumber = {
      params.validate(arguments)
      val n = target.asInstanceOf[MashNumber]
      n.negate
    }

    override def typeInferenceStrategy = new MethodTypeInferenceStrategy {
      def inferTypes(inferencer: Inferencer, targetTypeOpt: Option[Type], arguments: TypedArguments): Option[Type] =
        targetTypeOpt
    }

    override def summary = "Negate this number"

  }

  def summary = "A number"
}