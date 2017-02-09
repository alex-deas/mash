package com.github.mdr.mash.ns.core.help

import com.github.mdr.mash.classes.{ Field, MashClass }
import com.github.mdr.mash.evaluator.NewStaticMethod
import com.github.mdr.mash.ns.core.{ BooleanClass, StringClass }

object ParameterHelpClass extends MashClass("core.help.ParameterHelp") {

  object Fields {
    val Name = Field("name", Some("Parameter name"), StringClass)
    val Summary = Field("summary", Some("Summary of what the parameter does"), StringClass)
    val Description = Field("description", Some("Description of the parameter"), StringClass)
    val ShortFlag = Field("shortFlag", Some("Alternative single-character flag form, or null if none"), StringClass)
    val IsFlagParameter = Field("isFlagParameter", Some("If true, this parameter can only be given as a flag"), BooleanClass)
    val IsOptional = Field("isOptional", Some("If true, this parameter is optional"), BooleanClass)
    val IsLast = Field("isLast", Some("If true, this parameter is the last positonal parameter"), BooleanClass)
    val IsLazy = Field("isLazy", Some("If true, this parameter is evaluated lazily"), BooleanClass)
    val IsVariadic = Field("isVariadic", Some("If true, this parameter can take an arbitrary number of positional arguments"), BooleanClass)
  }

  import Fields._

  override val fields = Seq(Name, Summary, Description, ShortFlag, IsFlagParameter, IsOptional, IsLast, IsLazy, IsVariadic)

  override val staticMethods = Seq(NewStaticMethod(this))

  override def summaryOpt = Some("Help documentation for parameters")

}