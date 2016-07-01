package com.github.mdr.mash.evaluator

import com.github.mdr.mash.runtime.MashValue
import java.time.Instant
import com.github.mdr.mash.ns.time.MillisecondsClass
import com.github.mdr.mash.utils.PointedRegion
import com.github.mdr.mash.runtime._
import com.github.mdr.mash.ns.time.ChronoUnitClass
import java.time.temporal.TemporalAmount
import java.time.Duration
import com.github.mdr.mash.parser.BinaryOperator
import com.github.mdr.mash.runtime.MashBoolean
import com.github.mdr.mash.parser.AbstractSyntax._
import scala.collection.mutable.LinkedHashMap

object BinaryOperatorEvaluator extends EvaluatorHelper {

  def evaluateChainedOp(chainedOp: ChainedOpExpr)(implicit context: EvaluationContext): MashValue = {
    val ChainedOpExpr(left, opRights, _) = chainedOp
    val leftResult = Evaluator.evaluate(left)
    val (success, _) = opRights.foldLeft((true, leftResult)) {
      case ((leftSuccess, leftResult), (op, right)) ⇒
        lazy val rightResult = Evaluator.evaluate(right)
        lazy val thisSuccess = evaluateBinOp(leftResult, op, rightResult, sourceLocation(chainedOp)).asInstanceOf[MashBoolean].value
        (leftSuccess && thisSuccess, if (leftSuccess) rightResult else leftResult /* avoid evaluating right result if we know the expression is false */ )
    }
    MashBoolean(success)
  }

  def evaluateBinOpExpr(binOp: BinOpExpr)(implicit context: EvaluationContext): MashValue = {
    val BinOpExpr(left, op, right, _) = binOp
    lazy val leftResult = Evaluator.evaluate(left)
    lazy val rightResult = Evaluator.evaluate(right)
    evaluateBinOp(leftResult, op, rightResult, sourceLocation(binOp))
  }

  def evaluateBinOp(leftResult: ⇒ MashValue, op: BinaryOperator, rightResult: ⇒ MashValue, locationOpt: Option[SourceLocation]): MashValue = {
    def compareWith(f: (Int, Int) ⇒ Boolean): MashBoolean = MashBoolean(f(MashValueOrdering.compare(leftResult, rightResult), 0))
    op match {
      case BinaryOperator.And               ⇒ if (leftResult.isTruthy) rightResult else leftResult
      case BinaryOperator.Or                ⇒ if (leftResult.isFalsey) rightResult else leftResult
      case BinaryOperator.Equals            ⇒ MashBoolean(leftResult == rightResult)
      case BinaryOperator.NotEquals         ⇒ MashBoolean(leftResult != rightResult)
      case BinaryOperator.Plus              ⇒ add(leftResult, rightResult, locationOpt)
      case BinaryOperator.Minus             ⇒ subtract(leftResult, rightResult, locationOpt)
      case BinaryOperator.Multiply          ⇒ multiply(leftResult, rightResult, locationOpt)
      case BinaryOperator.Divide            ⇒ arithmeticOp(leftResult, rightResult, locationOpt, "divide", _ / _)
      case BinaryOperator.LessThan          ⇒ compareWith(_ < _)
      case BinaryOperator.LessThanEquals    ⇒ compareWith(_ <= _)
      case BinaryOperator.GreaterThan       ⇒ compareWith(_ > _)
      case BinaryOperator.GreaterThanEquals ⇒ compareWith(_ >= _)
      case BinaryOperator.Sequence          ⇒ leftResult; rightResult
    }
  }

  private def arithmeticOp(left: MashValue, right: MashValue, locationOpt: Option[SourceLocation], name: String, f: (MashNumber, MashNumber) ⇒ MashNumber): MashNumber =
    (left, right) match {
      case (left: MashNumber, right: MashNumber) ⇒
        f(left, right)
      case _ ⇒
        throw new EvaluatorException(s"Could not $name, incompatible operands", locationOpt)
    }

  private def multiply(left: MashValue, right: MashValue, locationOpt: Option[SourceLocation]) = (left, right) match {
    case (left: MashString, right: MashNumber) if right.isInt ⇒ left * right.asInt.get
    case (left: MashNumber, right: MashString) if left.isInt ⇒ right * left.asInt.get
    case (left: MashList, right: MashNumber) if right.isInt ⇒ left * right.asInt.get
    case (left: MashNumber, right: MashList) if left.isInt ⇒ right * left.asInt.get
    case (left: MashNumber, right: MashNumber) ⇒ left * right
    case _ ⇒ throw new EvaluatorException("Could not multiply, incompatible operands", locationOpt)
  }

  private implicit class RichInstant(instant: Instant) {
    def +(duration: TemporalAmount): Instant = instant.plus(duration)
    def -(duration: TemporalAmount): Instant = instant.minus(duration)
  }

  def add(left: MashValue, right: MashValue, locationOpt: Option[SourceLocation]): MashValue = (left, right) match {
    case (xs: MashList, ys: MashList)          ⇒ xs ++ ys
    case (s: MashString, right)                ⇒ s + right
    case (left, s: MashString)                 ⇒ s.rplus(left)
    case (left: MashNumber, right: MashNumber) ⇒ left + right
    case (left: MashObject, right: MashObject) ⇒ left + right
    case (MashWrapped(instant: Instant), MashNumber(n, Some(klass: ChronoUnitClass))) ⇒
      MashWrapped(instant + klass.temporalAmount(n.toInt))
    case (MashNumber(n, Some(klass: ChronoUnitClass)), MashWrapped(instant: Instant)) ⇒
      MashWrapped(instant + klass.temporalAmount(n.toInt))
    case _ ⇒
      throw new EvaluatorException("Could not add, incompatible operands", locationOpt)
  }

  def subtract(left: MashValue, right: MashValue, locationOpt: Option[SourceLocation]): MashValue =
    (left, right) match {
      case (left: MashNumber, right: MashNumber) ⇒
        left - right
      case (MashWrapped(instant: Instant), MashNumber(n, Some(klass: ChronoUnitClass))) ⇒
        MashWrapped(instant - klass.temporalAmount(n.toInt))
      case (MashWrapped(instant1: Instant), MashWrapped(instant2: Instant)) ⇒
        val duration = Duration.between(instant2, instant1)
        val millis = duration.getSeconds * 1000 + duration.getNano / 1000000
        MashNumber(millis, Some(MillisecondsClass))
      case (left: MashObject, right: MashString) ⇒
        left - right.s
      case _ ⇒
        throw new EvaluatorException("Could not subtract, incompatible operands", locationOpt)
    }

}