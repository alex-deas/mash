package com.github.mdr.mash.inference

import com.github.mdr.mash.classes.{ MashClass, UserDefinedMethod }
import com.github.mdr.mash.functions.{ UserDefinedFunction ⇒ UDF, _ }
import com.github.mdr.mash.ns.core._
import com.github.mdr.mash.ns.os.PathClass
import com.github.mdr.mash.parser.AbstractSyntax._
import com.github.mdr.mash.parser.DocComment

import scala.PartialFunction.condOpt
import scala.collection.immutable.ListMap
import scala.language.implicitConversions

sealed trait Type {

  def seq = Type.Seq(this)

}

object Type {

  object Patterns {

    /**
      * Match either a tagged or untagged string
      */
    object AnyString {
      def unapply(x: Any): Option[Type] = condOpt(x) {
        case t@(Type.Instance(StringClass) | Type.Tagged(StringClass, _)) ⇒ t.asInstanceOf[Type]
      }
    }

  }

  val Any = Instance(AnyClass)

  /**
    * Parameterised types
    */
  case class Generic(klass: MashClass, types: Type*) extends Type

  case class Seq(t: Type) extends Type

  case class Tagged(baseClass: MashClass, tagClass: MashClass) extends Type {
    require(baseClass == StringClass || baseClass == NumberClass)
  }

  case class Instance(klass: MashClass) extends Type {
    override def toString = klass.toString

    def unbless: Type.Object = Type.Object(klass.fields.map(f ⇒ f.name -> f.fieldType).toMap)
  }

  case class UserClass(name: String,
                       params: ParameterModel,
                       methods: ListMap[String, Type.UserDefinedFunction]) extends Type {
    override def toString = s"${classOf[UserClass].getSimpleName}($name)"
  }

  case class UserClassInstance(userClass: UserClass) extends Type {
    override def toString = s"${classOf[UserClassInstance].getSimpleName}(${userClass.name})"

    def unbless: Type.Object = {
      val pairs =
        for {
          param ← userClass.params.params
          name ← param.nameOpt
        } yield name -> Type.Any
      Type.Object(pairs.toMap)
    }
  }

  def obj(knownFields: (String, Type)*): Object = Object(knownFields.toMap)

  case class Object(knownFields: Map[String, Type]) extends Type

  /**
    * Method defined in mash, bound to a target
    */
  case class BoundUserDefinedMethod(targetType: Type, method: UserDefinedFunction) extends Type {
    override def toString = s"${classOf[BoundUserDefinedMethod].getSimpleName}($targetType, $method)"
  }

  /**
    * Built-in (Scala) method
    */
  case class BoundBuiltinMethod(targetType: Type, method: MashMethod) extends Type {
    require(!method.isInstanceOf[UserDefinedMethod])
  }

  /**
    * Function defined in Mash
    */
  case class UserDefinedFunction(docCommentOpt: Option[DocComment],
                                 isPrivate: Boolean,
                                 nameOpt: Option[String],
                                 params: ParameterModel,
                                 body: Expr,
                                 bindings: Map[String, Type]) extends Type {
    override def toString = s"${classOf[UserDefinedFunction].getSimpleName}($nameOpt)"

    def isPublic = !isPrivate
  }

  /**
    * Built-in (Scala) function
    */
  case class BuiltinFunction(f: MashFunction) extends Type {
    require(!f.isInstanceOf[UDF])
  }

  // Various implicits to make it less wordy to describe types:

  implicit def classToType[T](x: MashClass): Type = Type.Instance(x)

  implicit def seqToType[T <: MashClass](xs: scala.collection.Seq[T]): Type = {
    require(xs.length == 1)
    xs.head.seq
  }

  implicit def pathClassToType[T](x: PathClass.type): Type = StringClass taggedWith PathClass

  implicit def unitToType[T](x: Unit.type): Type = Type.Instance(UnitClass)

}
