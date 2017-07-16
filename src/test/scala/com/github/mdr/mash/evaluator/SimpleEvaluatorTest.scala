package com.github.mdr.mash.evaluator

import com.github.mdr.mash.compiler.{ CompilationUnit, Compiler }
import com.github.mdr.mash.inference.SimpleEvaluator
import com.github.mdr.mash.parser.AbstractSyntax.Expr
import com.github.mdr.mash.parser.ParseError
import com.github.mdr.mash.runtime.MashValue
import org.scalatest.{ FlatSpec, Matchers }

class SimpleEvaluatorTest extends FlatSpec with Matchers {

  check("42")
  check("'foo'")
  check("[1, 2, 3]")
  check("[1, 2, 3][1]")
  check("{ foo: 42 }")
  check("{ foo: 42 }.foo")

  private def check(s: String) = s"Simply evaluating '$s'" should "give the same result as fully evaluating it" in {
    val env = StandardEnvironment.create
    val expr = compile(s, env.bindings)
    val context = EvaluationContext(ScopeStack(env.globalVariables))
    val actual = SimpleEvaluator.evaluate(expr)(context)
    val expected = Evaluator.evaluate(expr)(context)
    actual should equal(Some(expected))
  }

  private def compile(s: String, bindings: Map[String, MashValue]): Expr =
    Compiler.compile(CompilationUnit(s), bindings = bindings) match {
      case Left(ParseError(message, _)) ⇒ throw new AssertionError("Compilation failed: " + message)
      case Right(program)               ⇒ program.body
    }

}
