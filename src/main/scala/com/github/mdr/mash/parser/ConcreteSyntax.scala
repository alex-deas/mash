package com.github.mdr.mash.parser

import scala.language.implicitConversions

import com.github.mdr.mash.lexer.Token
import com.github.mdr.mash.lexer.TokenType
import com.github.mdr.mash.utils.PointedRegion
import com.github.mdr.mash.utils.Region

/**
 * Trees representing the concrete syntax of mash (retaining all the semantically uninteresting tokens)
 */
object ConcreteSyntax {

  sealed trait AstNode {

    val tokens: Seq[Token]

    def startPos: Int = tokens.head.offset

    def posAfter: Int = tokens.last.region.posAfter

    def region = Region(startPos, posAfter - startPos)

    def pointedRegion = PointedRegion(startPos, region)

  }

  sealed trait Expr extends AstNode

  case class Literal(token: Token) extends Expr {
    require(token.isLiteral)
    val tokens = Seq(token)
  }

  case class Identifier(token: Token) extends Expr { val tokens = Seq(token) }

  sealed trait InterpolationPart extends AstNode

  case class SimpleInterpolation(interpolationStart: Token, expr: Expr) extends InterpolationPart {
    lazy val tokens = interpolationStart +: expr.tokens
  }

  case class ComplexInterpolation(interpolationStart: Token, expr: Expr, rbrace: Token) extends InterpolationPart {
    lazy val tokens = interpolationStart +: expr.tokens :+ rbrace
  }

  case class StringPart(stringMiddle: Token) extends InterpolationPart {
    lazy val tokens = Seq(stringMiddle)
  }

  case class InterpolatedString(start: Token, parts: Seq[InterpolationPart], end: Token) extends Expr {
    lazy val tokens = start +: parts.flatMap(_.tokens) :+ end
  }

  case class Hole(token: Token) extends Expr { val tokens = Seq(token) }

  /**
   * a = b, a += b, a -= b, a *= b, a /= b
   */
  case class AssignmentExpr(left: Expr, equals: Token, aliasOpt: Option[Token], right: Expr) extends Expr {
    lazy val tokens = (left.tokens :+ equals) ++ aliasOpt.toSeq ++ right.tokens
  }

  case class ParenExpr(lparen: Token, expr: Expr, rparen: Token) extends Expr {
    lazy val tokens = lparen +: expr.tokens :+ rparen
  }

  case class Statement(statementOpt: Option[Expr], semiOpt: Option[Token]) extends AstNode {
    lazy val tokens = statementOpt.toSeq.flatMap(_.tokens) ++ semiOpt.toSeq
  }

  case class StatementSeq(statements: Seq[Statement]) extends Expr {
    lazy val tokens = statements.flatMap(_.tokens)
  }

  case class BlockExpr(lbrace: Token, statements: Expr, rbrace: Token) extends Expr {
    lazy val tokens = lbrace +: statements.tokens :+ rbrace
  }

  case class MemberExpr(expr: Expr, dot: Token, name: Token) extends Expr {
    require(dot.tokenType == TokenType.DOT || dot.tokenType == TokenType.DOT_NULL_SAFE)

    def isNullSafe = dot.tokenType == TokenType.DOT_NULL_SAFE

    lazy val tokens = expr.tokens :+ dot :+ name

  }

  case class HeadlessMemberExpr(dot: Token, name: Token) extends Expr {
    require(dot.tokenType == TokenType.DOT || dot.tokenType == TokenType.DOT_NULL_SAFE)

    def isNullSafe = dot.tokenType == TokenType.DOT_NULL_SAFE

    lazy val tokens = Seq(dot, name)

  }

  case class LookupExpr(expr: Expr, lsquare: Token, indexExpr: Expr, rsquare: Token) extends Expr {
    lazy val tokens = (expr.tokens :+ lsquare) ++ indexExpr.tokens :+ rsquare
  }

  /**
   * args can be Expr's, for position arguments, or Long/ShortArgs
   */
  case class InvocationExpr(function: Expr, args: Seq[AstNode]) extends Expr {
    lazy val tokens = function.tokens ++ args.flatMap(_.tokens)
  }

  case class ParenInvocationArgs(firstArg: Expr, otherArgs: Seq[(Token, Expr)]) extends AstNode {
    lazy val tokens = firstArg.tokens ++ otherArgs.flatMap { case (comma, arg) ⇒ comma +: arg.tokens }
  }

  case class ParenInvocationExpr(function: Expr, lparen: Token, argsOpt: Option[ParenInvocationArgs], rparen: Token) extends Expr {
    lazy val tokens = (function.tokens :+ lparen) ++ argsOpt.toSeq.flatMap(_.tokens) :+ rparen
  }

  case class LambdaExpr(parameter: Token, arrow: Token, body: Expr) extends Expr {
    lazy val tokens = parameter +: arrow +: body.tokens
  }

  case class PipeExpr(left: Expr, pipe: Token, right: Expr) extends Expr {
    lazy val tokens = (left.tokens :+ pipe) ++ right.tokens
  }

  case class BinOpExpr(left: Expr, op: Token, right: Expr) extends Expr {
    lazy val tokens = (left.tokens :+ op) ++ right.tokens

    override def pointedRegion = PointedRegion(op.offset, region)

  }

  /**
   * e.g. 0 <= x < y <= 100
   */
  case class ChainedOpExpr(left: Expr, opRights: Seq[(Token, Expr)]) extends Expr {
    lazy val tokens = left.tokens ++ opRights.flatMap { case (token, expr) ⇒ token +: expr.tokens }
  }

  case class IfExpr(ifToken: Token, cond: Expr, thenToken: Token, body: Expr, elseOpt: Option[(Token, Expr)]) extends Expr {
    lazy val tokens = (ifToken +: cond.tokens :+ thenToken) ++ body.tokens ++
      elseOpt.map { case (token, expr) ⇒ token +: expr.tokens }.getOrElse(Seq())
  }

  case class ListExpr(lsquare: Token, contentsOpt: Option[ListExprContents], rsquare: Token) extends Expr {
    lazy val tokens = lsquare +: contentsOpt.toSeq.flatMap(_.tokens) :+ rsquare
  }

  case class ListExprContents(firstItem: Expr, otherItems: Seq[(Token, Expr)]) extends AstNode {
    lazy val tokens = firstItem.tokens ++ otherItems.flatMap { case (comma, item) ⇒ comma +: item.tokens }
  }

  case class ObjectEntry(fieldLabel: Token, colon: Token, value: Expr) extends AstNode {
    lazy val tokens = fieldLabel +: colon +: value.tokens
  }

  case class ObjectExpr(lbrace: Token, contentsOpt: Option[ObjectExprContents], rbrace: Token) extends Expr {
    lazy val tokens = lbrace +: contentsOpt.toSeq.flatMap(_.tokens) :+ rbrace
  }

  case class ObjectExprContents(firstEntry: ObjectEntry, otherEntries: Seq[(Token, ObjectEntry)]) extends AstNode {
    lazy val tokens = firstEntry.tokens ++ otherEntries.flatMap { case (colon, entry) ⇒ colon +: entry.tokens }
  }

  case class MinusExpr(minus: Token, expr: Expr) extends Expr {
    lazy val tokens = minus +: expr.tokens
  }

  /**
   * e.g -r
   */
  case class ShortArg(flag: Token) extends AstNode {
    lazy val tokens = Seq(flag)
  }

  /**
   * e.g.
   * --recursive
   * --foo=bar
   */
  case class LongArg(flag: Token, equalsValueOpt: Option[(Token, Expr)] = None) extends AstNode {
    lazy val tokens = Seq(flag) ++ equalsValueOpt.toSeq.flatMap { case (token, expr) ⇒ token +: expr.tokens }
  }

  /**
   * Mish item (command or argument), either a bare word, a quoted string, or a Mash interpolation
   */
  sealed trait MishItem extends AstNode

  case class MishWord(token: Token) extends MishItem {
    lazy val tokens = Seq(token)
  }

  case class MishString(expr: Expr) extends MishItem {
    lazy val tokens = expr.tokens
  }

  case class MishInterpolation(part: InterpolationPart) extends MishItem {
    lazy val tokens = part.tokens
  }

  /**
   * Raw mish command of the form: cmd arg1 arg2 arg3
   */
  case class MishExpr(command: MishItem, args: Seq[MishItem]) extends Expr {
    lazy val tokens = command.tokens ++ args.flatMap(_.tokens)
  }

  /**
   * Mish expression inside of the form !{ ... }
   */
  case class MishInterpolationExpr(start: Token, expr: MishExpr, rbrace: Token) extends Expr {
    lazy val tokens = (start +: expr.tokens) :+ rbrace
  }

  sealed trait FunctionParam extends AstNode

  case class SimpleParam(name: Token) extends FunctionParam {
    lazy val tokens = Seq(name)
  }

  case class VariadicParam(name: Token, ellipsis: Token) extends FunctionParam {
    lazy val tokens = Seq(name, ellipsis)
  }

  case class FunctionDeclaration(defToken: Token, name: Token, params: Seq[FunctionParam], equals: Token, body: Expr) extends Expr {
    lazy val tokens = Seq(defToken, name) ++ params.flatMap(_.tokens) ++ Seq(equals) ++ body.tokens
  }

  /**
   * An expression of the form: !less
   */
  case class MishFunction(word: Token) extends Expr {
    lazy val tokens = Seq(word)
  }

  case class HelpExpr(expr: Expr, question: Token) extends Expr {
    lazy val tokens = expr.tokens :+ question
  }

}
