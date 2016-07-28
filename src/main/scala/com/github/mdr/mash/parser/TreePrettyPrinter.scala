package com.github.mdr.mash.parser

import com.github.mdr.mash.parser.AbstractSyntax._
import scala.PartialFunction._
import java.text.DecimalFormat
import com.github.mdr.mash.utils.NumberUtils
import com.github.mdr.mash.runtime.MashNumber
import com.github.mdr.mash.runtime.MashString
import scala.PartialFunction.condOpt

object TreePrettyPrinter {

  /**
   * Print an expression as an indented tree
   */
  def printTree(node: AstNode, depth: Int = 0) {
    val indent = "  " * depth
    print(indent)
    val typeOpt = condOpt(node) {
      case expr: Expr ⇒ expr.typeOpt
    }
    val typeDescription = typeOpt.map(" [" + _ + "]").getOrElse("")
    node match {
      case Literal(_, _) | StringLiteral(_, _, _, _) | Identifier(_, _) | Hole(_) | MishFunction(_, _) ⇒
        println(PrettyPrinter.pretty(node) + typeDescription)
      case InterpolatedString(start, parts, end, _) ⇒
        println("InterpolatedString" + typeDescription)
        println("  " * (depth + 1) + start)
        parts.foreach {
          case StringPart(s)  ⇒ println("  " * (depth + 1) + s)
          case ExprPart(expr) ⇒ printTree(expr, depth + 1)
        }
        println("  " * (depth + 1) + end)
      case IfExpr(cond, body, elseOpt, _) ⇒
        println("IfExpr" + typeDescription)
        printTree(cond, depth + 1)
        printTree(body, depth + 1)
        for (elseBody ← elseOpt)
          printTree(elseBody, depth + 1)
      case MemberExpr(left, member, isNullSafe, _) ⇒
        println("MemberExpr" + typeDescription + (if (isNullSafe) "(null safe)" else ""))
        printTree(left, depth + 1)
        println("  " * (depth + 1) + member)
      case HeadlessMemberExpr(member, isNullSafe, _) ⇒
        println("HeadlessMemberExpr" + typeDescription + (if (isNullSafe) "(null safe)" else ""))
        println("  " * (depth + 1) + member)
      case MinusExpr(subExpr, _) ⇒
        println("MinusExpr" + typeDescription)
        printTree(subExpr, depth + 1)
      case LookupExpr(expr, index, _) ⇒
        println("LookupExpr" + typeDescription)
        printTree(expr, depth + 1)
        printTree(index, depth + 2)
      case InvocationExpr(function, args, _, _) ⇒
        println("InvocationExpr" + typeDescription)
        printTree(function, depth + 1)
        for (arg ← args)
          arg match {
            case Argument.PositionArg(e, _) ⇒
              printTree(e, depth + 1)
            case Argument.ShortFlag(flags, _) ⇒
              println("  " * (depth + 1) + "-" + flags)
            case Argument.LongFlag(flag, valueOpt, _) ⇒
              println("  " * (depth + 1) + "--" + flag)
              for (value ← valueOpt)
                printTree(value, depth + 2)
          }
      case PipeExpr(left, right, _) ⇒
        println("PipeExpr" + typeDescription)
        printTree(left, depth + 1)
        printTree(right, depth + 1)
      case ParenExpr(body, _) ⇒
        println("ParenExpr" + typeDescription)
        printTree(body, depth + 1)
      case BlockExpr(body, _) ⇒
        println("BlockExpr" + typeDescription)
        printTree(body, depth + 1)
      case LambdaExpr(v, body, _) ⇒
        println("LambdaExpr" + typeDescription)
        println("  " * (depth + 1) + v)
        printTree(body, depth + 1)
      case BinOpExpr(left, op, right, _) ⇒
        println("BinOpExpr: " + op + typeDescription)
        printTree(left, depth + 1)
        printTree(right, depth + 1)
      case ChainedOpExpr(left, opRights, _) ⇒
        println("ChainedOpExpr: " + typeDescription)
        printTree(left, depth + 1)
        for ((op, right) ← opRights)
          printTree(right, depth + 1)
      case AssignmentExpr(left, operatorOpt, right, alias, _) ⇒
        println("AssignmentExpr" + typeDescription + (if (alias) " (alias)" else "") + operatorOpt.map(" " + _).getOrElse(""))
        printTree(left, depth + 1)
        printTree(right, depth + 1)
      case ListExpr(items, _) ⇒
        println("ListExpr" + typeDescription)
        for (item ← items)
          printTree(item, depth + 1)
      case StatementSeq(statements, _) ⇒
        println("StatementSeq" + typeDescription)
        for (statement ← statements)
          printTree(statement, depth + 1)
      case ObjectExpr(entries, _) ⇒
        println("ObjectExpr" + typeDescription)
        for (ObjectEntry(field, body, _) ← entries) {
          println("  " * (depth + 1) + field)
          printTree(body, depth + 2)
        }
      case MishInterpolation(part, _) ⇒
        println("MishInterpolation" + typeDescription)
        part match {
          case StringPart(s)  ⇒ println("  " * (depth + 1) + s)
          case ExprPart(expr) ⇒ printTree(expr, depth + 1)
        }
      case MishExpr(command, args, redirects, captureProcessOutput, _) ⇒
        println("MishExpr" + typeDescription + (if (captureProcessOutput) "(captureProcessOutput)" else ""))
        printTree(command, depth + 1)
        for (arg ← args)
          printTree(arg, depth + 1)
      case FunctionDeclaration(name, params, body, _) ⇒
        println("FunctionDeclaration" + typeDescription)
        println("  " * (depth + 1) + name)
        for (param ← params.params)
          printTree(param, depth + 1)
        printTree(body, depth + 1)
      case FunctionParam(nameOpt, isVariadic, defaultExprOpt, isLazy, _) ⇒
        var descr = nameOpt.getOrElse("_")
        if (isVariadic)
          descr += "..."
        if (isLazy)
          descr = "lazy " + descr 
        println("  " * depth + descr)
        for (defaultExpr ← defaultExprOpt)
          printTree(defaultExpr, depth + 2)
      case HelpExpr(subExpr, _) ⇒
        println("HelpExpr" + typeDescription)
        printTree(subExpr, depth + 1)
      case _ ⇒
    }

  }

}