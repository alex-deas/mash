package com.github.mdr.mash.repl

import java.nio.file.Path

import com.github.mdr.mash.commands.{ CommandResult, CommandRunner }
import com.github.mdr.mash.completions.{ Completion, CompletionResult }
import com.github.mdr.mash.editor.QuoteToggler
import com.github.mdr.mash.input.InputAction
import com.github.mdr.mash.lexer.{ MashLexer, TokenType }
import com.github.mdr.mash.ns.view.ViewClass
import com.github.mdr.mash.os.linux.LinuxFileSystem
import com.github.mdr.mash.repl.NormalActions.{ NextHistory, _ }
import com.github.mdr.mash.repl.ReplState.{ It, ResultVarPrefix, ResultsListName }
import com.github.mdr.mash.repl.browser._
import com.github.mdr.mash.repl.history.HistorySearchState
import com.github.mdr.mash.runtime.{ MashList, MashNull, MashObject, MashValue }
import com.github.mdr.mash.terminal.Terminal
import com.github.mdr.mash.utils.Region

import scala.PartialFunction.cond

trait NormalActionHandler {
  self: Repl ⇒

  private val fileSystem = LinuxFileSystem

  def handleNormalAction(action: InputAction) = {
    action match {
      case AcceptLine               ⇒ handleAcceptLine()
      case LineBufferAction(f)      ⇒ resetHistoryIfTextChanges(state.cloneFrom(state.updateLineBuffer(f)))
      case Complete                 ⇒ handleComplete()
      case ClearScreen              ⇒ handleClearScreen()
      case EndOfFile                ⇒ handleEof()
      case PreviousHistory          ⇒ handlePreviousHistory()
      case NextHistory              ⇒ handleNextHistory()
      case AssistInvocation         ⇒ handleAssistInvocation()
      case InsertLastArg            ⇒ handleInsertLastArg()
      case ToggleQuote              ⇒ handleToggleQuote()
      case ToggleMish               ⇒ handleToggleMish()
      case IncrementalHistorySearch ⇒ handleIncrementalHistorySearch()
      case BrowseLastResult         ⇒ handleBrowseLastResult()
      case _                        ⇒
    }
    if (action != NextHistory && action != PreviousHistory && action != ClearScreen)
      history.commitToEntry()
    if (action != InsertLastArg && action != ClearScreen)
      state.cloneFrom(state.copy(insertLastArgStateOpt = None))
  }

  private def handleBrowseLastResult() {
    if (state.commandNumber > 0) {
      val commandNumber = state.commandNumber - 1
      val path = s"$ResultVarPrefix$commandNumber"
      for (value ← globalVariables.get(path)) {
        val browserState = getNewBrowserState(value, path)
        state.cloneFrom(state.copy(objectBrowserStateStackOpt = Some(ObjectBrowserStateStack(List(browserState)))))
      }
    }
  }

  private def handleIncrementalHistorySearch() {
    state.cloneFrom(state.copy(
      assistanceStateOpt = None,
      historySearchStateOpt = Some(HistorySearchState())))
    history.resetHistoryPosition()
  }

  private def resetHistoryIfTextChanges[T](f: ⇒ T): T = {
    val before = state.lineBuffer.text
    val result = f
    val after = state.lineBuffer.text
    if (before != after)
      history.resetHistoryPosition()
    result
  }

  private def handlePreviousHistory() =
    if (state.lineBuffer.onFirstLine || !history.isCommittedToEntry)
      history.goBackwards(state.lineBuffer.text) match {
        case Some(cmd) ⇒ state.cloneFrom(state.copy(lineBuffer = LineBuffer(cmd)))
        case None      ⇒ state.cloneFrom(state.updateLineBuffer(_.up))
      }
    else
      state.cloneFrom(state.updateLineBuffer(_.up))

  private def handleNextHistory() =
    if (state.lineBuffer.onLastLine || !history.isCommittedToEntry)
      history.goForwards() match {
        case Some(cmd) ⇒ state.cloneFrom(state.copy(lineBuffer = LineBuffer(cmd)))
        case None      ⇒ state.cloneFrom(state.updateLineBuffer(_.down))
      }
    else
      state.cloneFrom(state.updateLineBuffer(_.down))

  private def handleToggleQuote() = resetHistoryIfTextChanges {
    state.cloneFrom(state.updateLineBuffer(QuoteToggler.toggleQuotes(_, state.mish)))
  }

  private def handleEof() {
    state.cloneFrom(state.reset.copy(continue = false))
  }

  private def handleClearScreen() {
    output.write(Terminal.ClearScreenEscapeSequence.getBytes)
    output.flush()
    previousScreenOpt = None
  }

  private def handleToggleMish() = resetHistoryIfTextChanges {
    state.cloneFrom(state.copy(lineBuffer =
      if (state.lineBuffer.text startsWith "!")
        state.lineBuffer.delete(0)
      else
        state.lineBuffer.insertCharacters("!", 0)))
  }

  private def handleInsertLastArg() = resetHistoryIfTextChanges {
    val (argIndex, oldRegion) = state.insertLastArgStateOpt match {
      case Some(InsertLastArgState(n, region)) ⇒ (n + 1, region)
      case None                                ⇒ (0, Region(state.lineBuffer.cursorOffset, 0))
    }
    history.getLastArg(argIndex) match {
      case Some(newArg) ⇒
        val newText = oldRegion.replace(state.lineBuffer.text, newArg)
        val newRegion = Region(oldRegion.offset, newArg.length)
        state.cloneFrom(state.copy(
          lineBuffer = LineBuffer(newText, newRegion.posAfter),
          insertLastArgStateOpt = Some(InsertLastArgState(argIndex, newRegion))))
      case None         ⇒
    }
  }

  private def handleAcceptLine() = {
    history.resetHistoryPosition()
    if (canAcceptBuffer) {
      updateScreenAfterAccept()
      previousScreenOpt = None

      val cmd = state.lineBuffer.text
      state.cloneFrom(state.copy(lineBuffer = LineBuffer.Empty))
      if (cmd.trim.nonEmpty)
        runCommand(cmd)
    } else
      state.cloneFrom(state.updateLineBuffer(_.addCharacterAtCursor('\n')))
  }

  def canAcceptBuffer: Boolean = {
    val tokens = MashLexer.tokenise(state.lineBuffer.text, forgiving = true, mish = state.mish).tokens
    // TODO: We'll want to be smarter than this:
    import TokenType._
    val OpenBraceTypes: Set[TokenType] = Set(LBRACE, MISH_INTERPOLATION_START, MISH_INTERPOLATION_START_NO_CAPTURE,
      STRING_INTERPOLATION_START_COMPLEX)
    val openBraceCount = tokens.count(token ⇒ OpenBraceTypes.contains(token.tokenType))
    val mismatchedBrackets = openBraceCount != tokens.count(_.tokenType == TokenType.RBRACE)
    (state.lineBuffer.cursorAtEnd || !state.lineBuffer.isMultiline) && !mismatchedBrackets
  }

  protected def updateScreenAfterAccept() {
    state.cloneFrom(state.copy(
      completionStateOpt = None,
      assistanceStateOpt = None,
      lineBuffer = state.lineBuffer.moveCursorToEndOfLine))
    draw()

    for (previousScreen ← previousScreenOpt) {
      output.write(previousScreen.acceptScreen.getBytes)
      output.flush()
    }
  }

  protected def runCommand(cmd: String) {
    val commandRunner = new CommandRunner(output, terminal.size, globalVariables, sessionId)
    val unitName = s"command-${state.commandNumber}"
    val commandResult =
      try
        commandRunner.run(cmd, unitName, state.mish, bareWords, viewConfig)
      catch {
        case e: Exception ⇒
          e.printStackTrace()
          debugLogger.logException(e)
          return
      }
    processCommandResult(cmd, commandResult, workingDirectory = fileSystem.pwd)
  }

  private def processCommandResult(cmd: String, commandResult: CommandResult, workingDirectory: Path) {
    val CommandResult(resultOpt, toggleMish, displayModelOpt) = commandResult
    val actualResultOpt = resultOpt.map(ViewClass.unpackView)
    val commandNumber = state.commandNumber
    if (toggleMish)
      state.cloneFrom(state.copy(mish = !state.mish))
    else {
      history.record(cmd, commandNumber, state.mish, actualResultOpt, workingDirectory)
      state.cloneFrom(state.incrementCommandNumber)
    }
    actualResultOpt.foreach(saveResult(commandNumber))

    for (displayModel ← displayModelOpt) {
      val isView = resultOpt.exists(cond(_) { case MashObject(_, Some(ViewClass)) ⇒ true })
      val path = if (isView) s"$ResultVarPrefix$commandNumber" else cmd
      val browserState = BrowserState.fromModel(displayModel, path)
      state.cloneFrom(state.copy(objectBrowserStateStackOpt = Some(ObjectBrowserStateStack(List(browserState)))))
    }
  }

  private def saveResult(commandNumber: Int)(result: MashValue) {
    globalVariables.set(It, result)
    globalVariables.set(ResultVarPrefix + commandNumber, result)
    saveResultInList(result, commandNumber)
  }

  private def saveResultInList(result: MashValue, commandNumber: Int) {
    val oldResults =
      globalVariables.get(ResultsListName)
        .collect { case xs: MashList ⇒ xs.immutableElements }
        .getOrElse(Seq())
    val extendedResults = oldResults ++ Seq.fill(commandNumber - oldResults.length + 1)(MashNull)
    val newResults = MashList(extendedResults.updated(commandNumber, result))
    globalVariables.set(ResultsListName, newResults)
  }

  private def handleComplete() =
    for (result ← complete) {
      history.resetHistoryPosition()
      result.completions match {
        case Seq(completion) ⇒ immediateInsert(completion, result)
        case _               ⇒ enterIncrementalCompletionState(result)
      }
    }

  private def handleAssistInvocation() =
    if (state.assistanceStateOpt.isDefined)
      state.cloneFrom(state.copy(assistanceStateOpt = None))
    else
      updateInvocationAssistance()

  private def immediateInsert(completion: Completion, result: CompletionResult) {
    val newText = result.replacementLocation.replace(state.lineBuffer.text, completion.replacement)
    val newOffset = result.replacementLocation.offset + completion.replacement.length
    state.cloneFrom(state.copy(lineBuffer = LineBuffer(newText, newOffset)))
  }

}