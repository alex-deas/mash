package com.github.mdr.mash.functions

import com.github.mdr.mash.evaluator.EvaluationContext
import com.github.mdr.mash.runtime.MashValue
import scala.language.implicitConversions

trait ValueGenerator {

  def generate(context: EvaluationContext): MashValue

}

object ValueGenerator {

  implicit def fromThunk(gen: () ⇒ MashValue): ValueGenerator = context ⇒ gen()
  implicit def fromFun(gen: EvaluationContext ⇒ MashValue): ValueGenerator = context ⇒ gen(context)

}
