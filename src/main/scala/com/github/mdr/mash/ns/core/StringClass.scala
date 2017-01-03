package com.github.mdr.mash.ns.core

import java.time.Instant
import java.util.regex.Pattern

import com.github.mdr.mash.evaluator._
import com.github.mdr.mash.functions.{ MashMethod, Parameter, ParameterModel }
import com.github.mdr.mash.inference._
import com.github.mdr.mash.ns.os.{ PathClass, PathSummaryClass }
import com.github.mdr.mash.ns.time.{ DateClass, DateTimeClass }
import com.github.mdr.mash.os.linux.LinuxFileSystem
import com.github.mdr.mash.runtime._
import com.joestelmach.natty.Parser

import scala.collection.JavaConverters._

object StringClass extends MashClass("core.String") {

  private val fileSystem = LinuxFileSystem

  override val methods = Seq(
    ContainsMethod,
    EndsWithMethod,
    FirstMethod,
    GlobMethod,
    IsEmptyMethod,
    MatchesMethod,
    LengthMethod,
    LastMethod,
    NonEmptyMethod,
    RMethod,
    ReplaceMethod,
    ReverseMethod,
    StartsWithMethod,
    SplitMethod,
    TagMethod,
    ToDateMethod,
    ToDateTimeMethod,
    ToLowerMethod,
    ToNumberMethod,
    ToPathMethod,
    ToUpperMethod,
    TrimMethod,
    UntaggedMethod,
    MashClass.alias("g", GlobMethod))

  object ContainsMethod extends MashMethod("contains") {

    object Params {
      val Substring = Parameter(
        name = "substring",
        summary = "Substring to match")
    }

    import Params._

    val params = ParameterModel(Seq(Substring))

    def apply(target: MashValue, arguments: Arguments): MashBoolean = {
      val boundParams = params.validate(arguments)
      val s = target.asInstanceOf[MashString].s
      val pattern = ToStringifier.stringify(boundParams(Substring))
      MashBoolean(s contains pattern)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(BooleanClass)

    override def summary = "Test whether this string contains the given substring"

  }

  object GlobMethod extends MashMethod("glob") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashList = {
      params.validate(arguments)
      val pattern = target.asInstanceOf[MashString].s
      MashList(fileSystem.glob(pattern).map(PathSummaryClass.asMashObject))
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Seq(PathSummaryClass))

    override def summary = "Return paths matching a glob pattern"

  }

  object IsEmptyMethod extends MashMethod("isEmpty") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashBoolean = {
      params.validate(arguments)
      MashBoolean(target.asInstanceOf[MashString].s.isEmpty)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(BooleanClass)

    override def summary = "Return true if this is the empty string"

  }

  object NonEmptyMethod extends MashMethod("nonEmpty") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashBoolean = {
      params.validate(arguments)
      MashBoolean(target.asInstanceOf[MashString].s.nonEmpty)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(BooleanClass)

    override def summary = "Return true if this is a non-empty string"

  }

  object TrimMethod extends MashMethod("trim") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashString = {
      params.validate(arguments)
      val boundParams = params.validate(arguments)
      val s = target.asInstanceOf[MashString]
      s.modify(_.trim)
    }

    override def typeInferenceStrategy = SameStringMethodTypeInferenceStrategy

    override def summary = "Strip initial and trailing whitespace"

  }

  object RMethod extends MashMethod("r") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashString = {
      params.validate(arguments)
      target.asInstanceOf[MashString].copy(tagClassOpt = Some(RegexClass))
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(Type.Tagged(StringClass, RegexClass))

    override def summary = "This string as a regular expression"

  }

  object TagMethod extends MashMethod("tag") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashValue = {
      params.validate(arguments)
      target.asInstanceOf[MashString].tagClassOpt.getOrElse(MashNull)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(ClassClass)

    override def summary = "This string's tagged type, if any"
  }

  object UntaggedMethod extends MashMethod("untagged") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashString = {
      params.validate(arguments)
      target.asInstanceOf[MashString].copy(tagClassOpt = None)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(StringClass)

    override def summary = "This string without any tag class"
  }

  object MatchesMethod extends MashMethod("matches") {

    object Params {
      val _Pattern = Parameter(
        name = "pattern",
        summary = "Regular expression pattern")
      val IgnoreCase = Parameter(
        name = "ignoreCase",
        summary = "Perform a case-insensitive match",
        shortFlagOpt = Some('i'),
        isFlag = true,
        defaultValueGeneratorOpt = Some(() ⇒ MashBoolean.False),
        isBooleanFlag = true)
    }

    import Params._

    val params = ParameterModel(Seq(_Pattern, IgnoreCase))

    def apply(target: MashValue, arguments: Arguments): MashBoolean = {
      val boundParams = params.validate(arguments)
      val s = target.asInstanceOf[MashString].s
      val pattern = ToStringifier.stringify(boundParams(_Pattern))
      val ignoreCase = boundParams(IgnoreCase).isTruthy
      val flags = if (ignoreCase) Pattern.CASE_INSENSITIVE else 0
      MashBoolean(Pattern.compile(pattern, flags).matcher(s).find)
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(BooleanClass)

    override def summary = "Test whether this string contains a match within it to a given regular expression"

  }

  object SplitMethod extends MashMethod("split") {

    object Params {
      val Regex = Parameter(
        name = "regex",
        shortFlagOpt = Some('r'),
        summary = "Interpret separator as a regular expression; otherwise, interpret separator as the literal string (default false)",
        defaultValueGeneratorOpt = Some(() ⇒ MashBoolean.False),
        isFlag = true,
        isBooleanFlag = true)
      val Separator = Parameter(
        name = "separator",
        summary = "Separator to split string on; if not provided, the default is to split on whitespace",
        defaultValueGeneratorOpt = Some(() ⇒ MashNull))
    }

    import Params._

    val params = ParameterModel(Seq(
      Regex,
      Separator))

    def apply(target: MashValue, arguments: Arguments): MashList = {
      val boundParams = params.validate(arguments)
      val targetString = target.asInstanceOf[MashString]
      val regex = boundParams(Regex).isTruthy
      val separator = boundParams(Separator) match {
        case MashNull                 ⇒
          "\\s+"
        case MashString(separator, _) ⇒
          if (regex) separator else Pattern.quote(separator)
        case x                        ⇒
          boundParams.throwInvalidArgument(Separator, "Invalid separator of type " + x.typeName)
      }
      val pieces = targetString.s.split(separator, -1)
      def makePiece(s: String) = MashString(s, targetString.tagClassOpt)
      MashList(pieces.map(makePiece))
    }

    override def typeInferenceStrategy = new MethodTypeInferenceStrategy {

      override def inferTypes(inferencer: Inferencer, targetTypeOpt: Option[Type], arguments: TypedArguments): Option[Type] =
        targetTypeOpt.orElse(Some(Type.Instance(StringClass))).map(_.seq)

    }

    override def summary = "Split this string into a sequence of substrings using a separator"

  }

  object ReplaceMethod extends MashMethod("replace") {

    object Params {
      val Target = Parameter(
        name = "target",
        summary = "String to replace")
      val Replacement = Parameter(
        name = "replacement",
        summary = "Replacement string")
      val Regex = Parameter(
        name = "regex",
        shortFlagOpt = Some('r'),
        summary = "Interpret target as a regular expression",
        defaultValueGeneratorOpt = Some(() ⇒ MashBoolean.False),
        isFlag = true,
        isBooleanFlag = true)

    }

    import Params._

    val params = ParameterModel(Seq(Target, Replacement, Regex))

    def apply(target: MashValue, arguments: Arguments): MashString = {
      val boundParams = params.validate(arguments)
      val s = target.asInstanceOf[MashString]
      val regex = boundParams(Regex).isTruthy
      val targetString = boundParams.validateString(Target).s
      val replacement = boundParams.validateString(Replacement).s
      if (regex)
        s.modify(_.replaceAll(targetString, replacement))
      else
        s.modify(_.replace(targetString, replacement))
    }

    override def typeInferenceStrategy = SameStringMethodTypeInferenceStrategy

    override def summary = "Replace occurrences of a string with another"

  }

  object LengthMethod extends MashMethod("length") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashNumber = {
      params.validate(arguments)
      target.asInstanceOf[MashString].length
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(NumberClass)

    override def summary = "Length of this string"

  }

  object ToLowerMethod extends MashMethod("toLower") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashString = {
      params.validate(arguments)
      target.asInstanceOf[MashString].modify(_.toLowerCase)
    }

    override def summary = "Convert string to lowercase"

    override def typeInferenceStrategy = SameStringMethodTypeInferenceStrategy

  }

  object ToUpperMethod extends MashMethod("toUpper") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashString = {
      params.validate(arguments)
      target.asInstanceOf[MashString].modify(_.toUpperCase)
    }

    override def summary = "Convert string to uppercase"

    override def typeInferenceStrategy = SameStringMethodTypeInferenceStrategy

  }

  object ToNumberMethod extends MashMethod("toNumber") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashNumber = {
      params.validate(arguments)
      MashNumber(target.asInstanceOf[MashString].s.toDouble)
    }

    override def summary = "Parse this string as a number"

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(NumberClass)

  }

  object ToDateTimeMethod extends MashMethod("toDateTime") {

    val params = ParameterModel()

    private val parser = new Parser

    def parseInstant(s: String): Option[Instant] =
      parser.parse(s).asScala.headOption.flatMap(_.getDates.asScala.headOption).map(_.toInstant)

    def apply(target: MashValue, arguments: Arguments): MashValue = {
      params.validate(arguments)
      parseInstant(target.asInstanceOf[MashString].s).map(MashWrapped).getOrElse(MashNull)
    }

    override def summary = "Parse this string as a DateTime"

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(DateTimeClass)

  }

  object ToDateMethod extends MashMethod("toDate") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashValue = {
      params.validate(arguments)
      ToDateTimeMethod.parseInstant(target.asInstanceOf[MashString].s)
        .map(DateTimeClass.DateMethod.toLocalDate)
        .map(MashWrapped)
        .getOrElse(MashNull)
    }

    override def summary = "Parse this string as a Date"

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(DateClass)

  }

  object ToPathMethod extends MashMethod("toPath") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashString = {
      params.validate(arguments)
      target.asInstanceOf[MashString].copy(tagClassOpt = Some(PathClass))
    }

    override def summary = "Tag this string as a path"

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(StringClass taggedWith PathClass)

  }

  object LastMethod extends MashMethod("last") {

    object Params {
      val N = Parameter(
        name = "n",
        summary = "Number of characters",
        defaultValueGeneratorOpt = Some(() ⇒ MashNull))
    }
    import Params._

    val params = ParameterModel(Seq(N))

    def apply(target: MashValue, arguments: Arguments): MashString = {
      val boundParams = params.validate(arguments)
      val countOpt = MashNull.option(boundParams(N)).map(_.asInstanceOf[MashNumber].asInt.get)
      val s = target.asInstanceOf[MashString]
      countOpt match {
        case None    ⇒ s.last
        case Some(n) ⇒ s.modify(_.takeRight(n))
      }
    }

    override def summary = "Last character(s) of this string"

    override def typeInferenceStrategy = SameStringMethodTypeInferenceStrategy

  }

  object FirstMethod extends MashMethod("first") {

    object Params {
      val N = Parameter(
        name = "n",
        summary = "Number of characters",
        defaultValueGeneratorOpt = Some(() ⇒ MashNull))
    }
    import Params._

    val params = ParameterModel(Seq(N))

    def apply(target: MashValue, arguments: Arguments): MashString = {
      val boundParams = params.validate(arguments)
      val countOpt = MashNull.option(boundParams(N)).map(_.asInstanceOf[MashNumber].asInt.get)
      val s = target.asInstanceOf[MashString]
      countOpt match {
        case None    ⇒ s.first
        case Some(n) ⇒ s.modify(_.take(n))
      }
    }

    override def summary = "First character(s) of the string"

    override def typeInferenceStrategy = SameStringMethodTypeInferenceStrategy

  }

  object ReverseMethod extends MashMethod("reverse") {

    val params = ParameterModel()

    def apply(target: MashValue, arguments: Arguments): MashString = {
      params.validate(arguments)
      target.asInstanceOf[MashString].reverse
    }

    override def typeInferenceStrategy = SameStringMethodTypeInferenceStrategy

    override def summary = "Reverse this string"

  }

  object StartsWithMethod extends MashMethod("startsWith") {

    object Params {
      val Prefix = Parameter(
        name = "prefix",
        summary = "Prefix to test")
    }
    import Params._

    val params = ParameterModel(Seq(Prefix))

    def apply(target: MashValue, arguments: Arguments): MashBoolean = {
      val boundParams = params.validate(arguments)
      val s = target.asInstanceOf[MashString]
      val pattern = boundParams(Prefix).asInstanceOf[MashString]
      MashBoolean(s.startsWith(pattern))
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(BooleanClass)

    override def summary = "Check if this string starts with another"

  }

  object EndsWithMethod extends MashMethod("endsWith") {

    object Params {
      val Suffix = Parameter(
        name = "suffix",
        summary = "Suffix to test")
    }
    import Params._

    val params = ParameterModel(Seq(Suffix))

    def apply(target: MashValue, arguments: Arguments): MashBoolean = {
      val boundParams = params.validate(arguments)
      val s = target.asInstanceOf[MashString]
      val pattern = boundParams(Suffix).asInstanceOf[MashString]
      MashBoolean(s.reverse.startsWith(pattern.reverse))
    }

    override def typeInferenceStrategy = ConstantMethodTypeInferenceStrategy(BooleanClass)

    override def summary = "Check if this string ends with another"

  }

  override def summary = "A string"

  def taggedWith(klass: MashClass) = Type.Tagged(this, klass)

  override def parentOpt = Some(AnyClass)

}

object SameStringMethodTypeInferenceStrategy extends MethodTypeInferenceStrategy {

  override def inferTypes(inferencer: Inferencer, targetTypeOpt: Option[Type], arguments: TypedArguments): Option[Type] =
    targetTypeOpt.orElse(Some(Type.Instance(StringClass)))

}