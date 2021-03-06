package com.github.mdr.mash.completions

import com.github.mdr.mash.lexer.{ Token, TokenType }
import com.github.mdr.mash.os.{ EnvironmentInteractions, FileSystem }
import com.github.mdr.mash.runtime.MashValue
import com.github.mdr.mash.utils.{ Region, Utils }

object Completer {

  private val PrimaryTokens: Set[TokenType] = {
    import TokenType._
    Set[TokenType](STRING_LITERAL, IDENTIFIER, LONG_FLAG, MINUS, DOT, DOT_NULL_SAFE)
  }

}

class Completer(fileSystem: FileSystem, envInteractions: EnvironmentInteractions) {

  import Completer._

  private val stringCompleter = new StringCompleter(fileSystem, envInteractions)

  def complete(s: String, pos: Int, bindings: Map[String, MashValue], mish: Boolean): Option[CompletionResult] = {
    val parser = new CompletionParser(bindings, mish)
    findNearbyToken(s, pos, parser).flatMap(completeToken(s, pos, parser, bindings)).map(_.sorted)
  }

  /**
    * Find a nearby token to use as the start point for the completion search
    */
  private def findNearbyToken(s: String, pos: Int, parser: CompletionParser): Option[Token] = {
    val tokens = parser.tokenise(s)
    val beforeTokenOpt = tokens.find(_.region.posAfter == pos)
    val onTokenOpt = tokens.find(_.region contains pos)
    def isPrimary(token: Token) = PrimaryTokens contains token.tokenType
    Utils.optionCombine[Token](beforeTokenOpt, onTokenOpt, {
      case (beforeToken, onToken) if isPrimary(onToken)     ⇒ onToken
      case (beforeToken, onToken) if isPrimary(beforeToken) ⇒ beforeToken
      case (beforeToken, onToken)                           ⇒ onToken
    })
  }

  private def completeToken(text: String, pos: Int, parser: CompletionParser, bindings: Map[String, MashValue])(nearbyToken: Token): Option[CompletionResult] =
    nearbyToken.tokenType match {
      case TokenType.STRING_LITERAL                ⇒
        completeStringLiteral(text, nearbyToken, parser)
      case TokenType.LONG_FLAG                     ⇒
        completeLongFlag(text, nearbyToken, parser)
      case TokenType.SHORT_FLAG                    ⇒
        stringCompleter.completeAsString(text, nearbyToken, parser).completionResultOpt
      case TokenType.MINUS                         ⇒
        completeMinus(text, nearbyToken, parser)
      case TokenType.IDENTIFIER                    ⇒
        completeIdentifier(text, nearbyToken, parser, bindings)
      case TokenType.DOT | TokenType.DOT_NULL_SAFE ⇒
        completeDot(text, nearbyToken, pos, parser)
      case _                                       ⇒
        completeMisc(text, nearbyToken, pos, parser, bindings)
    }

  private def completeStringLiteral(text: String, stringLiteral: Token, parser: CompletionParser): Option[CompletionResult] =
    stringCompleter.completeAsString(text, stringLiteral, parser).completionResultOpt

  private def completeLongFlag(text: String, flag: Token, parser: CompletionParser): Option[CompletionResult] = {
    val flagResultOpt = FlagCompleter.completeLongFlag(text, flag, parser)
    val asStringResultOpt = stringCompleter.completeAsString(text, flag, parser).completionResultOpt
    CompletionResult.merge(flagResultOpt, asStringResultOpt)
  }

  private def completeMinus(text: String, minus: Token, parser: CompletionParser): Option[CompletionResult] = {
    val flagResultOpt = FlagCompleter.completeAllFlags(text, minus, parser)
    val asStringResultOpt = stringCompleter.completeAsString(text, minus, parser).completionResultOpt
    CompletionResult.merge(asStringResultOpt, flagResultOpt)
  }

  private def completeIdentifier(text: String, identifier: Token, parser: CompletionParser, bindings: Map[String, MashValue]): Option[CompletionResult] = {
    val StringCompletionResult(isPathCompletion, asStringResultOpt) =
      stringCompleter.completeAsString(text, identifier, parser)
    val MemberCompletionResult(isMemberExpr, memberResultOpt, prioritiseMembers) =
      MemberCompleter.completeIdentifier(text, identifier, parser)
    val bindingResultOpt =
      if (isMemberExpr)
        None // It would be misleading to try and complete bindings in member position
      else
        BindingCompleter.completeBindings(text, identifier, parser, bindings)
    val stringBindingResultOpt =
      if (isPathCompletion)
        CompletionResult.merge(asStringResultOpt, bindingResultOpt)
      else
        asStringResultOpt orElse bindingResultOpt

    if (prioritiseMembers)
      memberResultOpt orElse stringBindingResultOpt
    else
      stringBindingResultOpt orElse memberResultOpt
  }

  private def completeDot(text: String, dotToken: Token, pos: Int, parser: CompletionParser): Option[CompletionResult] = {
    lazy val memberResultOpt = MemberCompleter.completeImmediatelyAfterDot(text, dotToken, pos, parser)
    lazy val asStringResultOpt = stringCompleter.completeAsString(text, dotToken, parser).completionResultOpt

    val tokens = parser.tokenise(text)
    val nonWhitespaceTokens = tokens.filterNot(token ⇒ token.isComment || token.isWhitespace)
    val previousTokenOpt = Utils.itemBefore(tokens, dotToken)
    val previousNonWhitespaceTokenOpt = Utils.itemBefore(nonWhitespaceTokens, dotToken)

    val posBefore = dotToken.offset - 1
    val isMemberDot = posBefore >= 0 && !text(posBefore).isWhitespace && text(posBefore) != '.'

    val isImmediatelyAfterStringOrIdent = previousTokenOpt.exists(t ⇒ t.isString || t.isIdentifier)
    val isAfterPipe = previousNonWhitespaceTokenOpt.exists(t ⇒ t.tokenType == TokenType.PIPE)
    if (isMemberDot && !isImmediatelyAfterStringOrIdent || isAfterPipe)
      memberResultOpt orElse asStringResultOpt
    else
      asStringResultOpt orElse memberResultOpt
  }

  private def completeMisc(text: String, nearbyToken: Token, pos: Int, parser: CompletionParser, bindings: Map[String, MashValue]): Option[CompletionResult] = {
    val StringCompletionResult(isPathCompletion, asStringResultOpt) =
      stringCompleter.completeAsString(text, nearbyToken.region, parser) orElse
        stringCompleter.completeAsString(text, Region(pos, 0), parser)
    val bindingResultOpt = BindingCompleter.completeBindings(text, bindings, pos, parser)
    // Path completions should merge with binding completions, other types of string completions should take priority
    // over binding completions:
    if (isPathCompletion)
      CompletionResult.merge(asStringResultOpt, bindingResultOpt)
    else
      asStringResultOpt orElse bindingResultOpt
  }

}