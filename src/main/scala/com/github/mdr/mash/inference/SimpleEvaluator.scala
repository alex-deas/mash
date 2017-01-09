package com.github.mdr.mash.inference

import com.github.mdr.mash.evaluator.{ EvaluationContext, Evaluator }
import com.github.mdr.mash.parser.AbstractSyntax._
import com.github.mdr.mash.runtime._
import com.github.mdr.mash.utils.Utils

object SimpleEvaluator {

  def evaluate(expr: Expr)(implicit context: EvaluationContext): Option[MashValue] = {
    val result = simplyEvaluate(expr)
    expr.constantValueOpt = result
    result
  }

  def simplyEvaluate(expr: Expr)(implicit context: EvaluationContext): Option[MashValue] = expr match {
    case _: Hole | _: PipeExpr | _: HeadlessMemberExpr ⇒ None // Should have been removed from the AST by now
    case _: InterpolatedString | _: MishFunction       ⇒ None
    case literal: Literal                              ⇒ Some(literal.value)
    case stringLiteral: StringLiteral                  ⇒ Some(Evaluator.evaluateStringLiteral(stringLiteral))
    case listExpr: ListExpr                            ⇒ Utils.sequence(listExpr.elements.map(evaluate(_))).map(MashList(_))
    case identifier: Identifier                        ⇒ context.scopeStack.lookup(identifier.name)
    case objectExpr: ObjectExpr                        ⇒ simplyEvaluate(objectExpr)
    case StatementSeq(statements, _)                   ⇒ statements.map(evaluate).lastOption.getOrElse(Some(MashUnit))
    case ParenExpr(body, _)                            ⇒ evaluate(body)
    case blockExpr: BlockExpr                          ⇒ evaluate(blockExpr.expr)
    case HelpExpr(body, _)                             ⇒
      evaluate(body)
      None
    case MinusExpr(subExpr, _)                         ⇒
      evaluate(subExpr)
      None
    case memberExpr: MemberExpr                        ⇒
      evaluate(memberExpr.target)
      None
    case lookupExpr: LookupExpr                        ⇒
      evaluate(lookupExpr.index)
      evaluate(lookupExpr.target)
      None
    case invocationExpr: InvocationExpr                ⇒
      evaluate(invocationExpr.function)
      invocationExpr.arguments.collect {
        case Argument.PositionArg(value, _)       ⇒ evaluate(value)
        case Argument.LongFlag(_, Some(value), _) ⇒ evaluate(value)
      }
      None
    case LambdaExpr(params, body, _)                   ⇒
      evaluate(body)
      params.params.flatMap(_.defaultExprOpt).map(evaluate)
      None
    case binOpExpr: BinOpExpr                          ⇒
      binOpExpr.children.foreach(evaluate)
      None
    case chainedOpExpr: ChainedOpExpr                  ⇒
      chainedOpExpr.children.foreach(evaluate)
      None
    case assExpr: AssignmentExpr                       ⇒
      assExpr.children.foreach(evaluate)
      None
    case assExpr: PatternAssignmentExpr                ⇒
      evaluate(assExpr.right)
      None
    case ifExpr: IfExpr                                ⇒
      ifExpr.children.foreach(evaluate)
      None
    case mishExpr: MishExpr                            ⇒
      mishExpr.children.foreach(evaluate)
      None
    case interpolationExpr: MishInterpolation          ⇒
      interpolationExpr.part match {
        case ExprPart(partExpr) ⇒ evaluate(partExpr)
        case _                  ⇒
      }
      None
    case FunctionDeclaration(_, params, body, _)       ⇒
      evaluate(body)
      params.params.flatMap(_.defaultExprOpt).map(evaluate)
      None

  }

  def simplyEvaluate(objectExpr: ObjectExpr)(implicit context: EvaluationContext): Option[MashValue] = {
    def getFieldName(fieldNameExpr: Expr): Option[String] =
      fieldNameExpr match {
        case Identifier(name, _) ⇒ Some(name)
        case _                   ⇒ evaluate(fieldNameExpr) collect { case MashString(s, _) ⇒ s }
      }
    val fieldPairsOpt =
      objectExpr.fields.map {
        case FullObjectEntry(field, value, _) ⇒ getFieldName(field) -> evaluate(value)
        case ShorthandObjectEntry(field, _)   ⇒ Some(field) -> context.scopeStack.lookup(field)
      }.map(pairOfOptionToOptionPair)
    Utils.sequence(fieldPairsOpt).map(MashObject.of(_))
  }

  private def pairOfOptionToOptionPair[X, Y](pair: (Option[X], Option[Y])): Option[(X, Y)] =
    for (x <- pair._1; y <- pair._2) yield (x, y)

}