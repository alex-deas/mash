package com.github.mdr.mash

trait InputAction

object InputAction {

  case class SelfInsert(c: Char) extends InputAction
  case object ClearScreen extends InputAction
  case object AcceptLine extends InputAction
  case object BeginningOfLine extends InputAction
  case object EndOfLine extends InputAction
  case object ForwardChar extends InputAction
  case object BackwardChar extends InputAction
  case object ForwardWord extends InputAction
  case object BackwardWord extends InputAction
  case object PreviousHistory extends InputAction
  case object NextHistory extends InputAction
  case object DeleteChar extends InputAction
  case object EndOfFile extends InputAction
  case object BackwardDeleteChar extends InputAction
  case object KillLine extends InputAction
  case object KillWord extends InputAction
  case object BackwardKillWord extends InputAction
  case object Complete extends InputAction
  case object BackwardComplete extends InputAction
  case object Noop extends InputAction
  case object AssistInvocation extends InputAction
  case object IncrementalHistorySearch extends InputAction
  case object PageUp extends InputAction
  case object PageDown extends InputAction
  case object YankLastArg extends InputAction
  case object QuoteWord extends InputAction

  import InputSequence._

  private val ControlD = KeyPress(Key.BasicKey('d'), control = true)
  def fetchAction(lineEmpty: Boolean, keyMap: KeyMap = BasicKeyMap): InputAction = {
    val inputSequence = fetchInputSequence()
    if (inputSequence == ControlD && lineEmpty)
      EndOfFile
    else
      keyMap.map.get(inputSequence).getOrElse(inputSequence match {
        case OtherSequence(s) ⇒ SelfInsert(s.head)
        case _                ⇒ Noop
      })
  }

}

