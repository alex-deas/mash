package com.github.mdr.mash.repl

import com.github.mdr.mash.commands.CommandResult
import com.github.mdr.mash.commands.CommandRunner
import com.github.mdr.mash.DebugLogger
import com.github.mdr.mash.completions.Completion
import com.github.mdr.mash.completions.CompletionResult
import com.github.mdr.mash.editor.QuoteToggler
import com.github.mdr.mash.runtime.MashList
import com.github.mdr.mash.runtime.MashObject
import com.github.mdr.mash.incrementalSearch.IncrementalSearchState
import com.github.mdr.mash.input.InputAction
import com.github.mdr.mash.ns.view.ViewClass
import com.github.mdr.mash.repl.NormalActions._
import com.github.mdr.mash.terminal.Terminal
import com.github.mdr.mash.utils.Region
import com.github.mdr.mash.utils.LineInfo
import com.github.mdr.mash.runtime.MashNull
import com.github.mdr.mash.runtime.MashValue
import com.github.mdr.mash.os.linux.LinuxFileSystem
import java.nio.file.Path
import com.github.mdr.mash.lexer.MashLexer
import com.github.mdr.mash.lexer.TokenType

trait NormalActionHandler { self: Repl ⇒
  private val fileSystem = LinuxFileSystem

  def handleNormalAction(action: InputAction) = {
    action match {
      case AcceptLine               ⇒ handleAcceptLine()
      case Complete                 ⇒ handleComplete()
      case ClearScreen              ⇒ handleClearScreen()
      case EndOfFile                ⇒ handleEof()
      case PreviousHistory          ⇒ handlePreviousHistory()
      case NextHistory              ⇒ handleNextHistory()
      case BeginningOfLine          ⇒ state.updateLineBuffer(_.moveCursorToStart)
      case EndOfLine                ⇒ state.updateLineBuffer(_.moveCursorToEnd)
      case ForwardChar              ⇒ state.updateLineBuffer(_.cursorRight)
      case BackwardChar             ⇒ state.updateLineBuffer(_.cursorLeft)
      case ForwardWord              ⇒ state.updateLineBuffer(_.forwardWord)
      case BackwardWord             ⇒ state.updateLineBuffer(_.backwardWord)
      case DeleteChar               ⇒ resetHistoryIfTextChanges { state.updateLineBuffer(_.delete) }
      case BackwardDeleteChar       ⇒ resetHistoryIfTextChanges { state.updateLineBuffer(_.backspace) }
      case KillLine                 ⇒ resetHistoryIfTextChanges { state.updateLineBuffer(_.deleteToEndOfLine) }
      case KillWord                 ⇒ resetHistoryIfTextChanges { state.updateLineBuffer(_.deleteForwardWord) }
      case BackwardKillWord         ⇒ resetHistoryIfTextChanges { state.updateLineBuffer(_.deleteBackwardWord) }
      case SelfInsert(s)            ⇒ handleSelfInsert(s)
      case AssistInvocation         ⇒ handleAssistInvocation()
      case YankLastArg              ⇒ handleYankLastArg()
      case ToggleQuote              ⇒ handleToggleQuote()
      case ToggleMish               ⇒ handleToggleMish()
      case IncrementalHistorySearch ⇒ handleIncrementalHistorySearch()
      case _                        ⇒
    }
    if (action != YankLastArg && action != ClearScreen)
      state.yankLastArgStateOpt = None
  }

  private def handleSelfInsert(s: String) =
    resetHistoryIfTextChanges { for (c ← s) state.updateLineBuffer(_.addCharacterAtCursor(c)) }

  private def handleIncrementalHistorySearch() {
    state.incrementalSearchStateOpt = Some(IncrementalSearchState())
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

  private def handlePreviousHistory() {
    state.lineBuffer.cursorPos.row match {
      case 0 ⇒
        for (cmd ← history.goBackwards(state.lineBuffer.text))
          state.lineBuffer = LineBuffer(cmd)
      case _ ⇒
        state.updateLineBuffer(_.up)
    }
  }

  private def handleNextHistory() {
    if (state.lineBuffer.onLastLine)
      for (cmd ← history.goForwards())
        state.lineBuffer = LineBuffer(cmd)
    else
      state.updateLineBuffer(_.down)
  }

  private def handleToggleQuote(): Unit = resetHistoryIfTextChanges {
    state.updateLineBuffer(QuoteToggler.toggleQuotes(_, state.mish))
  }

  private def handleEof() {
    state.reset()
    state.continue = false
  }

  private def handleClearScreen() {
    output.write(Terminal.ClearScreenEscapeSequence.getBytes)
    output.flush()
    previousReplRenderResultOpt = None
  }

  private def handleToggleMish(): Unit = resetHistoryIfTextChanges {
    state.lineBuffer =
      if (state.lineBuffer.text startsWith "!")
        state.lineBuffer.delete(0)
      else
        state.lineBuffer.insertCharacters("!", 0)
  }

  private def handleYankLastArg(): Unit = resetHistoryIfTextChanges {
    val (argIndex, oldRegion) = state.yankLastArgStateOpt match {
      case Some(YankLastArgState(n, region)) ⇒ (n + 1, region)
      case None                              ⇒ (0, Region(state.lineBuffer.cursorOffset, 0))
    }
    history.getLastArg(argIndex) match {
      case Some(newArg) ⇒
        val newText = oldRegion.replace(state.lineBuffer.text, newArg)
        val newRegion = Region(oldRegion.offset, newArg.length)
        state.lineBuffer = LineBuffer(newText, newRegion.posAfter)
        state.yankLastArgStateOpt = Some(YankLastArgState(argIndex, newRegion))
      case None ⇒
    }
  }

  private def handleAcceptLine() {
    val tokens = MashLexer.tokenise(state.lineBuffer.text, forgiving = true, mish = state.mish)
    // TODO: We'll want to be smarter than this:
    val mismatchedBrackets = tokens.count(_.tokenType == TokenType.LBRACE) != tokens.count(_.tokenType == TokenType.RBRACE)

    if (state.lineBuffer.isMultiline && !state.lineBuffer.cursorAtEnd || mismatchedBrackets)
      handleSelfInsert("\n")
    else {
      updateScreenAfterAccept()
      previousReplRenderResultOpt = None

      history.resetHistoryPosition()

      val cmd = state.lineBuffer.text
      state.lineBuffer = LineBuffer.Empty
      if (cmd.trim.nonEmpty)
        runCommand(cmd)
    }
  }

  private def updateScreenAfterAccept() {
    state.completionStateOpt = None
    state.assistanceStateOpt = None
    draw()

    for (renderResult ← previousReplRenderResultOpt) {
      output.write(renderResult.screen.acceptScreen.getBytes)
      output.flush()
    }
  }

  private def runCommand(cmd: String) {
    val workingDirectory = fileSystem.pwd
    val commandRunner = new CommandRunner(output, terminal.info, state.globalVariables, sessionId)
    val unitName = s"command-${state.commandNumber}"
    val commandResult =
      try
        commandRunner.run(cmd, unitName, state.mish, state.bareWords)
      catch {
        case e: Exception ⇒
          e.printStackTrace()
          debugLogger.logException(e)
          return
      }
    processCommandResult(cmd, commandResult, workingDirectory)
  }

  private def processCommandResult(cmd: String, commandResult: CommandResult, workingDirectory: Path) {
    val CommandResult(resultOpt, toggleMish, objectTableModelOpt) = commandResult
    val actualResultOpt = resultOpt.map {
      case obj @ MashObject(_, Some(ViewClass)) ⇒ obj.get(ViewClass.Fields.Data).getOrElse(obj)
      case result                               ⇒ result
    }
    val commandNumber = state.commandNumber
    if (toggleMish)
      state.mish = !state.mish
    else {
      history.record(cmd, commandNumber, state.mish, actualResultOpt, workingDirectory)
      state.commandNumber += 1
    }
    actualResultOpt.foreach(saveResult(commandNumber))

    for (objectTableModel ← objectTableModelOpt)
      state.objectBrowserStateOpt = Some(ObjectBrowserState(objectTableModel))
  }

  private def saveResult(number: Int)(result: MashValue) {
    state.globalVariables.set(ReplState.It, result)
    state.globalVariables.set(ReplState.Res + number, result)
    val oldResults = state.globalVariables.get(ReplState.Res) match {
      case Some(MashList(oldResults @ _*)) ⇒ oldResults
      case _                               ⇒ Seq()
    }
    val extendedResults = oldResults ++ Seq.fill(number - oldResults.length + 1)(MashNull)
    val newResults = MashList(extendedResults.updated(number, result))
    state.globalVariables.set(ReplState.Res, newResults)
  }

  private def handleComplete() = {
    for (result ← complete) {
      history.resetHistoryPosition()
      result.completions match {
        case Seq(completion) ⇒ immediateInsert(completion, result)
        case _               ⇒ enterIncrementalCompletionState(result)
      }
    }
  }

  private def handleAssistInvocation() {
    if (state.assistanceStateOpt.isDefined)
      state.assistanceStateOpt = None
    else
      updateInvocationAssistance()
  }

  private def immediateInsert(completion: Completion, result: CompletionResult) {
    val newText = result.replacementLocation.replace(state.lineBuffer.text, completion.replacement)
    val newOffset = result.replacementLocation.offset + completion.replacement.length
    val lineInfo = new LineInfo(newText)
    val newCursorPos = CursorPos(lineInfo.lineAndColumn(newOffset))
    state.lineBuffer = LineBuffer(newText, newCursorPos)
  }

}