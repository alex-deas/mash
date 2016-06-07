package com.github.mdr.mash.evaluator

import java.time.Duration
import java.time.Instant
import java.time.temporal.TemporalAmount

import scala.PartialFunction.condOpt
import scala.collection.immutable.ListMap

import com.github.mdr.mash.functions.AnonymousFunction
import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.functions.Parameter
import com.github.mdr.mash.functions.ParameterModel
import com.github.mdr.mash.functions.UserDefinedFunction
import com.github.mdr.mash.ns.core.help.HelpFunction
import com.github.mdr.mash.ns.os.PathClass
import com.github.mdr.mash.ns.os.ProcessResultClass
import com.github.mdr.mash.ns.time.ChronoUnitClass
import com.github.mdr.mash.ns.time.MillisecondsClass
import com.github.mdr.mash.os.linux.LinuxEnvironmentInteractions
import com.github.mdr.mash.parser.AbstractSyntax._
import com.github.mdr.mash.parser.BinaryOperator
import com.github.mdr.mash.parser.ConcreteSyntax
import com.github.mdr.mash.parser.QuotationType
import com.github.mdr.mash.runtime._
import com.github.mdr.mash.subprocesses.ProcessResult
import com.github.mdr.mash.subprocesses.ProcessRunner
import com.github.mdr.mash.utils.PointedRegion
import com.github.mdr.mash.utils.Utils

object Evaluator {

  private val environmentInteractions = LinuxEnvironmentInteractions

  def evaluate(expr: Expr, env: Environment): MashValue = {
    try {
      ExecutionContext.checkInterrupted()
      val v = simpleEvaluate(expr, env)
      ExecutionContext.checkInterrupted()
      val result = expr match {
        case _: Identifier | _: MemberExpr ⇒
          addLocationToExceptionIfMissing(expr.locationOpt) { immediatelyResolveNullaryFunctions(v) }
        case _ ⇒ v
      }
      result
    } catch {
      case e: EvaluatorException ⇒
        throw e
      case e: EvaluationInterruptedException ⇒
        throw e
      case t: Exception ⇒
        throw EvaluatorException("Unexpected error in evaluation: " + t.toString,
          locationOpt = expr.locationOpt,
          cause = t)
    }
  }

  /**
   * If the given value is a function or bound method that allows nullary invocation, invoke it immediately and
   * return the result.
   */
  def immediatelyResolveNullaryFunctions(v: MashValue): MashValue =
    v match {
      case f: MashFunction if f.allowsNullary ⇒ f(Arguments(Seq()))
      case BoundMethod(target, method, _) if method.allowsNullary ⇒ method(target, Arguments(Seq()))
      case _ ⇒ v
    }

  /**
   * Evaluate the given expression. If the result is a function/bound method that allows a nullary call, it is not called.
   */
  private def simpleEvaluate(expr: Expr, env: Environment): MashValue =
    expr match {
      case Hole(_) | PipeExpr(_, _, _) | HeadlessMemberExpr(_, _, _) ⇒ // Should have been removed from the AST by now
        throw EvaluatorException("Unexpected AST node: " + expr, expr.locationOpt)
      case interpolatedString: InterpolatedString ⇒
        evaluateInterpolatedString(interpolatedString, env)
      case ParenExpr(body, _) ⇒
        evaluate(body, env)
      case Literal(v, _) ⇒
        v
      case StringLiteral(s, quotationType, tildePrefix, _) ⇒
        val tagOpt = condOpt(quotationType) { case QuotationType.Double ⇒ PathClass }
        val detilded = if (tildePrefix) environmentInteractions.home + s else s
        MashString(detilded, tagOpt)
      case Identifier(name, _) ⇒
        env.get(name).orElse(env.globalVariables.get(name)).getOrElse {
          val locationOpt = expr.locationOpt
          throw EvaluatorException(s"No binding for '$name'", locationOpt)
        }
      case MinusExpr(subExpr, _) ⇒
        evaluate(subExpr, env) match {
          case n: MashNumber ⇒ n.negate
          case _             ⇒ throw new EvaluatorException("Could not negate a non-number", expr.locationOpt)
        }
      case memberExpr: MemberExpr ⇒
        evaluateMemberExpr(memberExpr, env, immediatelyResolveNullaryWhenVectorising = true).result
      case lookupExpr: LookupExpr ⇒
        evaluateLookupExpr(lookupExpr, env)
      case invocationExpr: InvocationExpr ⇒
        evaluateInvocationExpr(invocationExpr, env)
      case LambdaExpr(parameter, body, _) ⇒
        makeAnonymousFunction(parameter, body, env)
      case binOp: BinOpExpr ⇒
        evaluateBinOp(binOp, env)
      case chainedOpExpr: ChainedOpExpr ⇒
        evaluateChainedOp(chainedOpExpr, env)
      case assExpr: AssignmentExpr ⇒ evaluateAssignment(assExpr, env)
      case ifExpr: IfExpr ⇒
        evaluateIfExpr(ifExpr, env)
      case ListExpr(items, _) ⇒
        MashList(items.map(evaluate(_, env)))
      case ObjectExpr(entries, _) ⇒
        val fields = for ((label, value) ← entries) yield label -> evaluate(value, env)
        MashObject(fields, classOpt = None)
      case mishExpr: MishExpr        ⇒ evaluateMishExpr(mishExpr, env)
      case expr: MishInterpolation   ⇒ evaluateMishInterpolation(expr, env)
      case decl: FunctionDeclaration ⇒ evaluateFunctionDecl(decl, env)
      case MishFunction(command, _)  ⇒ SystemCommandFunction(command)
      case HelpExpr(expr, _)         ⇒ evaluateHelpExpr(expr, env)

    }

  private def evaluateFunctionDecl(decl: FunctionDeclaration, env: Environment): MashUnit = {
    val FunctionDeclaration(name, params, body, sourceInfoOpt) = decl
    val parameters: Seq[Parameter] = params.map {
      case SimpleParam(name, _)   ⇒ Parameter(name, s"Parameter '$name'")
      case VariadicParam(name, _) ⇒ Parameter(name, s"Parameter '$name'", isVariadic = true)
    }
    if (parameters.count(_.isVariadic) > 1)
      throw new EvaluatorException("Multiple variadic parameters are not allowed")
    val variadicIndex = parameters.indexWhere(_.isVariadic)
    if (variadicIndex >= 0 && variadicIndex < params.size - 1)
      throw new EvaluatorException("A variadic parameter must be the last positional parameter")
    val fn = UserDefinedFunction(name, ParameterModel(parameters), body, env)
    env.globalVariables += name -> fn
    MashUnit
  }

  private def evaluateAssignment(expr: AssignmentExpr, env: Environment): MashUnit = {
    val AssignmentExpr(left, right, alias, _) = expr
    val rightValue = if (alias) simpleEvaluate(right, env) else evaluate(right, env)
    left match {
      case Identifier(name, _) ⇒
        env.globalVariables += name -> rightValue
      case MemberExpr(target, member, /* isNullSafe */ false, _) ⇒
        val targetValue = evaluate(target, env)
        targetValue match {
          case MashObject(fields, _) ⇒
            fields += member -> rightValue
          case _ ⇒
            throw new EvaluatorException("Cannot assign to fields of an object of this type", expr.locationOpt)
        }
      case LookupExpr(target, index, _) ⇒
        evaluateAssignmentToLookupExpr(target, index, rightValue, env)
      case _ ⇒
        throw new EvaluatorException("Expression is not assignable", left.locationOpt)
    }
    MashUnit
  }

  private def evaluateAssignmentToLookupExpr(target: Expr, index: Expr, rightValue: MashValue, env: Environment): MashValue = {
    val targetValue = evaluate(target, env)
    val indexValue = evaluate(index, env)
    targetValue match {
      case xs: MashList ⇒
        indexValue match {
          case n: MashNumber ⇒
            val i = n.asInt.getOrElse(
              throw new EvaluatorException("Invalid list index '" + indexValue + "'", index.locationOpt))
            val items = xs.items
            if (i < 0 || i > items.size - 1)
              throw new EvaluatorException("Index out of range '" + indexValue + "'", index.locationOpt)
            else {
              xs.items(i) = rightValue
              MashUnit
            }
          case _ ⇒
            throw new EvaluatorException("Invalid list index '" + indexValue + "'", index.locationOpt)
        }
      case mo: MashObject ⇒
        indexValue match {
          case MashString(s, _) ⇒ {
            mo.fields(s) = rightValue
            MashUnit
          }
          case _ ⇒
            throw new EvaluatorException("Invalid object index '" + indexValue + "'", index.locationOpt)
        }
      case _ ⇒
        throw new EvaluatorException("Cannot assign to indexes of objects of this type", target.locationOpt)
    }

  }

  private def lookupField(target: MashValue, name: String): Option[(Field, MashClass)] =
    condOpt(target) {
      case MashObject(_, Some(klass)) ⇒ klass.fields.find(_.name == name).map(field ⇒ (field, klass))
    }.flatten

  private def getHelpForMember(target: MashValue, name: String): Option[MashObject] = {
    val fieldHelpOpt = lookupField(target, name).map { case (field, klass) ⇒ HelpFunction.getHelp(field, klass) }
    lazy val memberHelpOpt = MemberEvaluator.maybeLookup(target, name).collect {
      case method: BoundMethod ⇒ HelpFunction.getHelp(method)
    }
    fieldHelpOpt orElse memberHelpOpt
  }

  private def evaluateHelpExpr(expr: Expr, env: Environment): MashObject =
    expr match {
      case memberExpr @ MemberExpr(targetExpr, name, _, _) ⇒
        val target = evaluate(targetExpr, env)
        val scalarHelpOpt = getHelpForMember(target, name)
        lazy val vectorHelpOpt = condOpt(target) {
          case MashList(x, _*) ⇒ getHelpForMember(x, name)
        }.flatten
        lazy val directHelp = {
          val result = evaluateMemberExpr_(memberExpr, target, env, immediatelyResolveNullaryWhenVectorising = true).result
          HelpFunction.getHelp(result)
        }
        scalarHelpOpt orElse vectorHelpOpt getOrElse directHelp
      case _ ⇒
        val x = simpleEvaluate(expr, env)
        HelpFunction.getHelp(x)
    }

  private def evaluateMishInterpolation(expr: MishInterpolation, env: Environment) =
    expr.part match {
      case StringPart(s)  ⇒ MashString(s, PathClass)
      case ExprPart(expr) ⇒ evaluate(expr, env)
    }

  private def evaluateMishExpr(expr: MishExpr, env: Environment): MashValue = {
    val MishExpr(command, args, captureProcessOutput, _) = expr
    val evaluatedCommand = evaluate(command, env)
    val evaluatedArgs = args.map(evaluate(_, env))
    val flattenedArgs: Seq[MashValue] = evaluatedArgs.flatMap {
      case xs: MashList ⇒ xs.items
      case x            ⇒ Seq(x)
    }
    val allArgs = evaluatedCommand +: flattenedArgs
    if (captureProcessOutput) {
      val processResult = ProcessRunner.runProcess(allArgs, expandTilde = true, captureProcess = captureProcessOutput)
      ProcessResultClass.fromResult(processResult)
    } else {
      ProcessRunner.runProcess(allArgs, expandTilde = true)
      MashUnit
    }
  }

  private def evaluateInterpolatedString(interpolatedString: InterpolatedString, env: Environment): MashString = {
    val InterpolatedString(start, parts, end, _) = interpolatedString
    val chunks =
      MashString(start, PathClass) +:
        parts.map {
          case StringPart(s) ⇒ MashString(s, PathClass)
          case ExprPart(expr) ⇒ evaluate(expr, env) match {
            case ms: MashString ⇒ ms
            case x              ⇒ MashString(ToStringifier.stringify(x))
          }
        } :+ MashString(end, PathClass)
    chunks.reduce(_ + _)
  }

  private def makeAnonymousFunction(parameter: String, body: Expr, env: Environment): AnonymousFunction =
    AnonymousFunction(parameter, body, env)

  private case class MemberExprEvalResult(result: MashValue, wasVectorised: Boolean)

  private def evaluateMemberExpr(memberExpr: MemberExpr, env: Environment, immediatelyResolveNullaryWhenVectorising: Boolean): MemberExprEvalResult = {
    val MemberExpr(expr, name, isNullSafe, sourceInfoOpt) = memberExpr
    val target = evaluate(expr, env)
    evaluateMemberExpr_(memberExpr, target, env, immediatelyResolveNullaryWhenVectorising)
  }

  private def evaluateMemberExpr_(memberExpr: AbstractMemberExpr, target: MashValue, env: Environment, immediatelyResolveNullaryWhenVectorising: Boolean): MemberExprEvalResult = {
    val name = memberExpr.name
    val isNullSafe = memberExpr.isNullSafe
    val locationOpt = memberExpr.sourceInfoOpt.flatMap(info ⇒ condOpt(info.expr) {
      case ConcreteSyntax.MemberExpr(_, _, name) ⇒ PointedRegion(name.offset, name.region)
    })
    if (target == MashNull && isNullSafe)
      MemberExprEvalResult(MashNull, wasVectorised = false)
    else {
      lazy val scalarLookup = MemberEvaluator.maybeLookup(target, name).map(x ⇒ MemberExprEvalResult(x, wasVectorised = false))
      lazy val vectorisedLookup = vectorisedMemberLookup(target, name, isNullSafe, immediatelyResolveNullaryWhenVectorising).map(
        x ⇒ MemberExprEvalResult(x, wasVectorised = true))
      scalarLookup orElse vectorisedLookup getOrElse (throw new EvaluatorException(s"Cannot find member '$name'", locationOpt))
    }
  }

  private def vectorisedMemberLookup(target: MashValue, name: String, isNullSafe: Boolean, immediatelyResolveNullaryWhenVectorising: Boolean): Option[MashList] =
    target match {
      case xs: MashList ⇒
        val options = xs.items.map {
          case MashNull if isNullSafe ⇒ Some(MashNull)
          case x ⇒
            val lookupOpt = MemberEvaluator.maybeLookup(x, name)
            if (immediatelyResolveNullaryWhenVectorising)
              lookupOpt.map(Evaluator.immediatelyResolveNullaryFunctions)
            else
              lookupOpt
        }
        Utils.sequence(options).map(MashList(_))
      case _ ⇒
        None
    }

  private def evaluateArgument(arg: Argument, env: Environment): EvaluatedArgument = arg match {
    case Argument.PositionArg(expr, sourceInfoOpt)        ⇒ EvaluatedArgument.PositionArg(evaluate(expr, env), Some(arg))
    case Argument.ShortFlag(flags, sourceInfoOpt)         ⇒ EvaluatedArgument.ShortFlag(flags, Some(arg))
    case Argument.LongFlag(flag, valueOpt, sourceInfoOpt) ⇒ EvaluatedArgument.LongFlag(flag, valueOpt.map(v ⇒ evaluate(v, env)), Some(arg))
  }

  private def evaluateInvocationExpr(invocationExpr: InvocationExpr, env: Environment) = {
    val InvocationExpr(functionExpr, arguments, _) = invocationExpr
    val evaluatedArguments = Arguments(arguments.map(evaluateArgument(_, env)))
    functionExpr match {
      case memberExpr: MemberExpr ⇒
        val MemberExprEvalResult(result, wasVectorised) = evaluateMemberExpr(memberExpr, env, immediatelyResolveNullaryWhenVectorising = false)
        if (wasVectorised) {
          val functions = result.asInstanceOf[MashList]
          functions.map(function ⇒ callFunction(function, evaluatedArguments, functionExpr, invocationExpr))
        } else
          callFunction(result, evaluatedArguments, functionExpr, invocationExpr)
      case _ ⇒
        val function = simpleEvaluate(functionExpr, env)
        callFunction(function, evaluatedArguments, functionExpr, invocationExpr)
    }
  }

  private def evaluateIfExpr(ifExpr: IfExpr, env: Environment) = {
    val IfExpr(cond, body, elseOpt, _) = ifExpr
    val result = evaluate(cond, env)
    if (Truthiness.isTruthy(result))
      evaluate(body, env)
    else elseOpt match {
      case None           ⇒ MashUnit
      case Some(elseBody) ⇒ evaluate(elseBody, env)
    }
  }

  private def evaluateLookupExpr(lookupExpr: LookupExpr, env: Environment): MashValue = {
    val LookupExpr(targetExpr, indexExpr, _) = lookupExpr
    val target = evaluate(targetExpr, env)
    val index = evaluate(indexExpr, env)
    val targetLocationOpt = targetExpr.locationOpt
    val indexLocationOpt = indexExpr.locationOpt
    val lookupLocationOpt = lookupExpr.locationOpt
    index match {
      case MashString(memberName, _) ⇒ MemberEvaluator.lookup(target, memberName, indexLocationOpt)
      case n: MashNumber ⇒
        val i = n.asInt.getOrElse(throw new EvaluatorException("Unable to lookup, non-integer index: " + n, lookupLocationOpt))
        target match {
          case xs: MashList ⇒
            val index = if (i < 0) i + xs.size else i
            if (index >= xs.size)
              throw new EvaluatorException("Index out of range " + n, indexLocationOpt)
            xs(index)
          case s: MashString ⇒ s.lookup(i)
          case _             ⇒ throw new EvaluatorException("Unable to lookup", lookupLocationOpt)
        }
      case _ ⇒
        throw new EvaluatorException("Unable to lookup", indexLocationOpt)
    }
  }

  private def evaluateChainedOp(chainedOp: ChainedOpExpr, env: Environment): MashValue = {
    val ChainedOpExpr(left, opRights, _) = chainedOp
    val leftResult = evaluate(left, env)
    val (success, _) = opRights.foldLeft((true, leftResult)) {
      case ((leftSuccess, leftResult), (op, right)) ⇒
        lazy val rightResult = evaluate(right, env)
        lazy val thisSuccess = evaluateBinOp(leftResult, op, rightResult, chainedOp.locationOpt).asInstanceOf[MashBoolean].value
        (leftSuccess && thisSuccess, if (leftSuccess) rightResult else leftResult /* avoid evaluating right result if we know the expression is false */)
    }
    MashBoolean(success)
  }

  private def evaluateBinOp(binOp: BinOpExpr, env: Environment): MashValue = {
    val BinOpExpr(left, op, right, _) = binOp
    lazy val leftResult = evaluate(left, env)
    lazy val rightResult = evaluate(right, env)
    evaluateBinOp(leftResult, op, rightResult, binOp.locationOpt)
  }

  private def evaluateBinOp(leftResult: ⇒ MashValue, op: BinaryOperator, rightResult: ⇒ MashValue, locationOpt: Option[PointedRegion]): MashValue = {
    def compareWith(f: (Int, Int) ⇒ Boolean): MashBoolean =
      MashBoolean(PartialFunction.cond(leftResult) {
        case l: Comparable[_]              ⇒ f(l.asInstanceOf[Comparable[MashValue]].compareTo(rightResult), 0)
        case MashWrapped(l: Comparable[_]) ⇒ f(l.asInstanceOf[Comparable[Any]].compareTo(rightResult.asInstanceOf[MashWrapped].x), 0)
      })
    op match {
      case BinaryOperator.And               ⇒ if (Truthiness.isTruthy(leftResult)) rightResult else leftResult
      case BinaryOperator.Or                ⇒ if (Truthiness.isFalsey(leftResult)) rightResult else leftResult
      case BinaryOperator.Equals            ⇒ MashBoolean(leftResult == rightResult)
      case BinaryOperator.NotEquals         ⇒ MashBoolean(leftResult != rightResult)
      case BinaryOperator.Plus              ⇒ add(leftResult, rightResult, locationOpt)
      case BinaryOperator.Minus             ⇒ subtract(leftResult, rightResult, locationOpt)
      case BinaryOperator.Multiply          ⇒ multiply(leftResult, rightResult, locationOpt)
      case BinaryOperator.Divide            ⇒ arithmeticOp(leftResult, rightResult, locationOpt, "divide", _ / _)
      case BinaryOperator.LessThan          ⇒ compareWith(_ < _)
      case BinaryOperator.LessThanEquals    ⇒ compareWith(_ <= _)
      case BinaryOperator.GreaterThan       ⇒ compareWith(_ > _)
      case BinaryOperator.GreaterThanEquals ⇒ compareWith(_ >= _)
      case BinaryOperator.Sequence          ⇒ leftResult; rightResult
    }
  }

  private def arithmeticOp(left: MashValue, right: MashValue, locationOpt: Option[PointedRegion], name: String, f: (MashNumber, MashNumber) ⇒ MashNumber): MashNumber =
    (left, right) match {
      case (left: MashNumber, right: MashNumber) ⇒
        f(left, right)
      case _ ⇒
        throw new EvaluatorException(s"Could not $name, incompatible operands", locationOpt)
    }

  private def multiply(left: MashValue, right: MashValue, locationOpt: Option[PointedRegion]) = (left, right) match {
    case (left: MashString, right: MashNumber) if right.isInt ⇒ left.modify(_ * right.asInt.get)
    case (left: MashNumber, right: MashString) if left.isInt ⇒ right.modify(_ * left.asInt.get)
    case (left: MashNumber, right: MashNumber) ⇒ left * right
    case _ ⇒ throw new EvaluatorException("Could not multiply, incompatible operands", locationOpt)
  }

  private implicit class RichInstant(instant: Instant) {
    def +(duration: TemporalAmount): Instant = instant.plus(duration)
    def -(duration: TemporalAmount): Instant = instant.minus(duration)
  }

  def add(left: MashValue, right: MashValue, locationOpt: Option[PointedRegion]): MashValue = (left, right) match {
    case (xs: MashList, ys: MashList)          ⇒ xs ++ ys
    case (s: MashString, right)                ⇒ s + right
    case (left, s: MashString)                 ⇒ s.rplus(left)
    case (left: MashNumber, right: MashNumber) ⇒ left + right
    case (MashWrapped(instant: Instant), MashNumber(n, Some(klass: ChronoUnitClass))) ⇒
      MashWrapped(instant + klass.temporalAmount(n.toInt))
    case (MashNumber(n, Some(klass: ChronoUnitClass)), MashWrapped(instant: Instant)) ⇒
      MashWrapped(instant + klass.temporalAmount(n.toInt))
    case _ ⇒ throw new EvaluatorException("Could not add, incompatible operands", locationOpt)
  }

  def subtract(left: MashValue, right: MashValue, locationOpt: Option[PointedRegion]): MashValue = (left, right) match {
    case (left: MashNumber, right: MashNumber) ⇒ left - right
    case (MashWrapped(instant: Instant), MashNumber(n, Some(klass: ChronoUnitClass))) ⇒
      MashWrapped(instant - klass.temporalAmount(n.toInt))
    case (MashWrapped(instant1: Instant), MashWrapped(instant2: Instant)) ⇒
      val duration = Duration.between(instant2, instant1)
      val millis = duration.getSeconds * 1000 + duration.getNano / 1000000
      MashNumber(millis, Some(MillisecondsClass))
    case _ ⇒ throw new EvaluatorException("Could not subtract, incompatible operands", locationOpt)
  }

  private def callFunction(function: MashValue, arguments: Arguments, functionExpr: Expr, invocationExpr: Expr): MashValue =
    callFunction(function, arguments, Some(functionExpr), Some(invocationExpr))

  private def addLocationToExceptionIfMissing[T <: MashValue](locationOpt: Option[PointedRegion])(p: ⇒ T): T =
    try
      p
    catch {
      case e: EvaluatorException if e.locationOpt.isEmpty ⇒
        throw e.copy(locationOpt = locationOpt)
    }

  def callFunction(function: MashValue, arguments: Arguments, functionExprOpt: Option[Expr] = None, invocationExprOpt: Option[Expr] = None): MashValue = {
    val functionLocationOpt = functionExprOpt.flatMap(_.locationOpt)
    val invocationLocationOpt = invocationExprOpt.flatMap(_.locationOpt)
    function match {
      case MashString(memberName, _) ⇒
        arguments.positionArgs match {
          case Seq(EvaluatedArgument.PositionArg(xs: MashList, _)) ⇒
            xs.map { target ⇒
              val intermediateResult = MemberEvaluator.lookup(target, memberName, functionLocationOpt)
              addLocationToExceptionIfMissing(invocationLocationOpt) {
                immediatelyResolveNullaryFunctions(intermediateResult)
              }
            }
          case Seq(EvaluatedArgument.PositionArg(target, _)) ⇒
            val intermediateResult = MemberEvaluator.lookup(target, memberName, functionLocationOpt)
            addLocationToExceptionIfMissing(invocationLocationOpt) {
              immediatelyResolveNullaryFunctions(intermediateResult)
            }
          case _ ⇒
            throw EvaluatorException(s"Cannot call a String on multiple arguments", invocationLocationOpt)
        }
      case f: MashFunction ⇒
        addLocationToExceptionIfMissing(invocationLocationOpt) {
          f(arguments)
        }
      case BoundMethod(target, method, _) ⇒
        addLocationToExceptionIfMissing(invocationLocationOpt) {
          method(target, arguments)
        }
      case _ ⇒
        throw EvaluatorException(s"Not callable", functionLocationOpt)
    }
  }
}
