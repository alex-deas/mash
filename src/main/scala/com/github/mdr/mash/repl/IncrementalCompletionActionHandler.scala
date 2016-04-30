package com.github.mdr.mash.repl

import com.github.mdr.mash.completions.CompletionResult
import com.github.mdr.mash.input.InputAction

trait IncrementalCompletionActionHandler { self: Repl ⇒
  import InputAction._

  protected def handleIncrementalCompletionAction(action: InputAction, completionState: IncrementalCompletionState) {
    action match {
      case SelfInsert(s) ⇒
        handleInsert(s, completionState)
      case BackwardDeleteChar if completionState.mementoOpt.isDefined ⇒ // restore previous state
        completionState.mementoOpt.foreach(_.restoreInto(state))
      case Complete if completionState.immediatelyAfterCompletion ⇒ // enter browse completions mode
        browseCompletions(completionState)
      case _ ⇒ // exit back to normal mode, and handle there
        state.completionStateOpt = None
        handleNormalAction(action)
    }
  }

  private def handleInsert(s: String, completionState: IncrementalCompletionState) {
    val memento = ReplStateMemento(state.lineBuffer, completionState)
    for (c ← s)
      state.updateLineBuffer(_.addCharacterAtCursor(c))

    state.completionStateOpt = None
    for (CompletionResult(completions, location) ← complete) {
      val previousLocation = completionState.replacementLocation
      val stillReplacingSameLocation = location.offset == previousLocation.offset
      if (stillReplacingSameLocation) {
        val replacedText = location.of(state.lineBuffer.text)
        completions match {
          case Seq(completion) if replacedText == completion.replacement ⇒
          // ... we leave incremental mode if what the user has typed is an exact much for the sole completion
          case _ ⇒
            val newCompletionState = IncrementalCompletionState(completions, location,
              immediatelyAfterCompletion = false, Some(memento))
            state.completionStateOpt = Some(newCompletionState)
        }
      }
    }
  }

}