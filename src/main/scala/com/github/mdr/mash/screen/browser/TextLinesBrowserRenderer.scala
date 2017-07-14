package com.github.mdr.mash.screen.browser

import com.github.mdr.mash.repl.browser.TextLinesBrowserState
import com.github.mdr.mash.screen.Style.StylableString
import com.github.mdr.mash.screen._
import com.github.mdr.mash.terminal.TerminalInfo

class TextLinesBrowserRenderer(state: TextLinesBrowserState, terminalInfo: TerminalInfo)
  extends AbstractBrowserRenderer(state, terminalInfo) {

  protected def renderDataLines: Seq[Line] =
    for ((l, index) <- state.model.renderedLines.drop(state.firstRow).take(windowSize).zipWithIndex)
      yield Line(l.style(Style(inverse = index == (state.selectedRow - state.firstRow))))

  protected def renderLines: Seq[Line] = {
    val upperStatusLine = renderUpperStatusLine
    val dataLines = renderDataLines
    val statusLine = renderStatusLine
    Seq(upperStatusLine) ++ dataLines ++ Seq(statusLine)
  }

  private def renderRegularStatusLine = {
    import KeyHint._
    val hints = Seq(Exit, Back, InsertWhole)
    val countChars = s"${state.selectedRow + 1}/${state.model.renderedLines.size}".style(Style(inverse = true))
    Line(countChars + " (".style + renderKeyHints(hints) + ")".style)
  }

  private def renderStatusLine: Line =
    state.expressionStateOpt match {
      case Some(expressionState) ⇒ StatusLineRenderers.renderExpressionInputStatusLine(expressionState.expression)
      case None                  ⇒ renderRegularStatusLine
    }

  protected val windowSize = state.windowSize(terminalInfo.rows)

}
