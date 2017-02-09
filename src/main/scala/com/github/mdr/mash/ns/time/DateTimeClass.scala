package com.github.mdr.mash.ns.time

import java.time._
import java.util.Date

import com.github.mdr.mash.classes.MashClass
import com.github.mdr.mash.evaluator._
import com.github.mdr.mash.functions.{ MashMethod, ParameterModel }
import com.github.mdr.mash.ns.core.{ AnyClass, NumberClass, StringClass }
import com.github.mdr.mash.runtime._
import org.ocpsoft.prettytime.PrettyTime

object DateTimeClass extends MashClass("time.DateTime") {

  override val methods = Seq(DateMethod, FuzzyMethod, MillisSinceEpoch)

  case class Wrapper(target: MashValue) {

    def instant: Instant = target match {
      case MashWrapped(i: Instant) ⇒ i
    }

  }

  object MillisSinceEpoch extends MashMethod("millisSinceEpoch") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashNumber = {
      params.validate(arguments)
      MashNumber(Wrapper(target).instant.toEpochMilli)
    }

    override def typeInferenceStrategy = NumberClass

    override def summaryOpt = Some("Date portion of this date and time")

  }

  object FuzzyMethod extends MashMethod("fuzzy") {
    private val prettyTime = new PrettyTime

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashString = {
      params.validate(arguments)
      MashString(prettyTime.format(Date.from(Wrapper(target).instant)))
    }

    override def typeInferenceStrategy = StringClass

    override def summaryOpt = Some("Render as human-friendly relative time, e.g. 1 day ago, 3 months from now, etc")

  }

  object DateMethod extends MashMethod("date") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashWrapped = {
      params.validate(arguments)
      MashWrapped(toLocalDate(Wrapper(target).instant))
    }

    def toLocalDate(instant: Instant): LocalDate =
      LocalDateTime.ofInstant(instant, ZoneId.systemDefault).toLocalDate

    override def typeInferenceStrategy = DateClass

    override def summaryOpt = Some("Date portion of this date and time")

  }

  override def summaryOpt = Some("An instant in time")

  override def parentOpt = Some(AnyClass)

}

