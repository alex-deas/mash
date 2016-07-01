package com.github.mdr.mash.evaluator
import com.github.mdr.mash.parser.AbstractSyntax._
import com.github.mdr.mash.runtime._
import com.github.mdr.mash.parser.BinaryOperator

object AssignmentEvaluator extends EvaluatorHelper {

  def evaluateAssignment(expr: AssignmentExpr)(implicit context: EvaluationContext): MashUnit = {
    val AssignmentExpr(left, operatorOpt, right, alias, _) = expr
    val rightValue = if (alias) Evaluator.simpleEvaluate(right) else Evaluator.evaluate(right)

    left match {
      case identifier @ Identifier(name, _) ⇒
        val actualRightValue = operatorOpt match {
          case Some(op) ⇒
            val currentValue = Evaluator.evaluateIdentifier(identifier)
            BinaryOperatorEvaluator.evaluateBinOp(currentValue, op, rightValue, sourceLocation(expr))
          case None ⇒
            rightValue
        }
        context.scopeStack.set(name, actualRightValue)
      case memberExpr @ MemberExpr(_, _, /* isNullSafe */ false, _) ⇒
        evaluateAssignmentToMemberExpr(memberExpr, expr, operatorOpt, rightValue)
      case lookupExpr: LookupExpr ⇒
        evaluateAssignmentToLookupExpr(lookupExpr, expr, operatorOpt, rightValue)
      case _ ⇒
        // TODO: this is purely syntactic, and therefore should be handled by the parser/compiler, not evaluator
        throw new EvaluatorException("Expression is not assignable", sourceLocation(left))
    }
    MashUnit
  }

  private def evaluateAssignmentToLookupExpr(lookupExpr: LookupExpr, expr: AssignmentExpr, operatorOpt: Option[BinaryOperator], rightValue: MashValue)(implicit context: EvaluationContext): Unit = {
    val LookupExpr(target, index, _) = lookupExpr
    val targetValue = Evaluator.evaluate(target)
    val indexValue = Evaluator.evaluate(index)
    targetValue match {
      case xs: MashList ⇒
        evaluateAssignmentToListIndex(lookupExpr, xs, index, indexValue, operatorOpt, rightValue)
      case obj: MashObject ⇒
        evaluateAssignmentToObject(lookupExpr, expr, obj, index, indexValue, operatorOpt, rightValue)
      case x ⇒
        throw new EvaluatorException("Cannot assign to indexes of objects of type " + x.primaryClass, sourceLocation(target))
    }
  }

  private def evaluateAssignmentToListIndex(lookupExpr: LookupExpr, xs: MashList, index: Expr, indexValue: MashValue, operatorOpt: Option[BinaryOperator], rightValue: MashValue)(implicit context: EvaluationContext): Unit =
    indexValue match {
      case n: MashNumber ⇒
        val i = n.asInt.getOrElse(
          throw new EvaluatorException("Invalid list index '" + indexValue + "'", sourceLocation(index)))
        if (i < 0 || i > xs.items.size - 1)
          throw new EvaluatorException("Index out of range '" + indexValue + "'", sourceLocation(index))
        val actualRightValue = operatorOpt match {
          case Some(op) ⇒
            val currentValue = xs.items(i)
            BinaryOperatorEvaluator.evaluateBinOp(currentValue, op, rightValue, sourceLocation(lookupExpr))
          case None ⇒
            rightValue
        }
        xs.items(i) = actualRightValue
      case x ⇒
        throw new EvaluatorException("Invalid list index of type " + x.primaryClass, sourceLocation(index))
    }

  private def evaluateAssignmentToObject(lookupExpr: LookupExpr, assignmentExpr: AssignmentExpr, obj: MashObject, index: Expr, indexValue: MashValue, operatorOpt: Option[BinaryOperator], rightValue: MashValue)(implicit context: EvaluationContext): Unit = {
    val fields = obj.fields
    indexValue match {
      case MashString(fieldName, _) ⇒
        assignToField(obj, fieldName, operatorOpt, rightValue, lookupExpr, assignmentExpr)
      case _ ⇒
        throw new EvaluatorException("Invalid object index of type " + indexValue.primaryClass, sourceLocation(index))
    }
  }

  private def evaluateAssignmentToMemberExpr(memberExpr: MemberExpr, assignmentExpr: AssignmentExpr, operatorOpt: Option[BinaryOperator], rightValue: MashValue)(implicit context: EvaluationContext): Unit = {
    val MemberExpr(target, fieldName, _, _) = memberExpr
    Evaluator.evaluate(target) match {
      case obj: MashObject ⇒
        assignToField(obj, fieldName, operatorOpt, rightValue, memberExpr, assignmentExpr)
      case targetValue ⇒
        throw new EvaluatorException("Cannot assign to fields of a value of type " + targetValue.primaryClass, sourceLocation(assignmentExpr))
    }
  }

  private def assignToField(obj: MashObject, fieldName: String, operatorOpt: Option[BinaryOperator], rightValue: MashValue, objectExpr: Expr, assignmentExpr: AssignmentExpr)(implicit context: EvaluationContext): Unit = {
    val fields = obj.fields
    if (operatorOpt.isDefined && !fields.contains(fieldName))
      throw new EvaluatorException(s"No field '$fieldName' to update", sourceLocation(objectExpr))
    val actualRightValue = operatorOpt match {
      case Some(op) ⇒
        val currentValue = fields(fieldName)
        BinaryOperatorEvaluator.evaluateBinOp(currentValue, op, rightValue, sourceLocation(assignmentExpr))
      case None ⇒
        rightValue
    }
    fields += fieldName -> actualRightValue
  }

}