package com.github.mdr.mash.repl

import com.github.mdr.mash.input.InputAction
import com.github.mdr.mash.repl.NormalActions._

import scala.PartialFunction.condOpt

object LineBufferAction {

  def unapply(action: InputAction): Option[LineBuffer ⇒ LineBuffer] = condOpt(action) {
    case SelfInsert(c)      ⇒ _.addCharactersAtCursor(c)
    case BeginningOfLine    ⇒ _.moveCursorToStart
    case EndOfLine          ⇒ _.moveCursorToEnd
    case ForwardChar        ⇒ _.cursorRight
    case BackwardChar       ⇒ _.cursorLeft
    case ForwardWord        ⇒ _.forwardWord
    case BackwardWord       ⇒ _.backwardWord
    case DeleteChar         ⇒ _.delete
    case BackwardDeleteChar ⇒ _.backspace
    case KillLine           ⇒ _.deleteToEndOfLine
    case BackwardKillLine   ⇒ _.deleteToBeginningOfLine
    case KillWord           ⇒ _.deleteForwardWord
    case BackwardKillWord   ⇒ _.deleteBackwardWord
  }

}
