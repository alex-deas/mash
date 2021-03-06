package com.github.mdr.mash.ns.os

import com.github.mdr.mash.classes.{ AbstractObjectWrapper, Field, MashClass, NewStaticMethod }
import com.github.mdr.mash.ns.core._
import com.github.mdr.mash.os.PermissionsSection
import com.github.mdr.mash.runtime.{ MashBoolean, MashObject, MashValue }

import scala.collection.immutable.ListMap

object PermissionsSectionClass extends MashClass("os.PermissionsSection") {

  object Fields {
    val CanRead = Field("canRead", Some("Can read"), BooleanClass)
    val CanWrite = Field("canWrite", Some("Can write"), BooleanClass)
    val CanExecute = Field("canExecute", Some("Can execute"), BooleanClass)
  }

  import Fields._

  override val fields = Seq(CanRead, CanWrite, CanExecute)

  override val staticMethods = Seq(NewStaticMethod(this))

  case class Wrapper(x: MashValue) extends AbstractObjectWrapper(x) {
    def canRead = getBooleanField(CanRead)
    def canWrite = getBooleanField(CanWrite)
    def canExecute = getBooleanField(CanExecute)
  }

  def asMashObject(section: PermissionsSection): MashObject = {
    val PermissionsSection(canRead, canWrite, canExecute) = section
    MashObject.of(ListMap(
      CanRead -> MashBoolean(canRead),
      CanWrite -> MashBoolean(canWrite),
      CanExecute -> MashBoolean(canExecute)),
      PermissionsSectionClass)
  }

  override def summaryOpt = Some("File permissions for particular class of user (owner, group or other)")

}

