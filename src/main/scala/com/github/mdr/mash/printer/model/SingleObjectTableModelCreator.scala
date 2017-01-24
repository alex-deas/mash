package com.github.mdr.mash.printer.model

import com.github.mdr.mash.printer.{ ColumnAllocator, ColumnSpec, FieldRenderer, ViewConfig }
import com.github.mdr.mash.runtime.{ MashObject, MashValue }
import com.github.mdr.mash.terminal.TerminalInfo

class SingleObjectTableModelCreator(terminalInfo: TerminalInfo, viewConfig: ViewConfig) {

  private val fieldRenderer: FieldRenderer = new FieldRenderer(viewConfig)

  def create(obj: MashObject): SingleObjectTableModel = {
    val classNameOpt = obj.classOpt.flatMap(_.nameOpt)

    val (fieldColumnWidth, valueColumnWidth) = calculateColumnWidths(obj, classNameOpt)

    val fields = obj.immutableFields
    val renderedFields =
      for ((field, value) ← fields)
        yield field -> renderValue(value)
    SingleObjectTableModel(classNameOpt, renderedFields, fieldColumnWidth, valueColumnWidth, obj, fields)
  }

  private def calculateColumnWidths(obj: MashObject, classNameOpt: Option[String]): (Int, Int) = {
    val requestedFieldWidth = maxFieldWidth(obj)
    val requestedValueWidth = maxValueWidth(obj)

    val fieldColumn = ColumnSpec("field", 10)
    val valueColumn = ColumnSpec("value", 1)
    val requestedWidths = Map(fieldColumn -> requestedFieldWidth, valueColumn -> requestedValueWidth)
    val columns = Seq(fieldColumn, valueColumn)
    val allocatedWidths = ColumnAllocator.allocateColumns(columns, requestedWidths, terminalInfo.columns - 3)

    val fieldColumnWidth = allocatedWidths(fieldColumn)
    val valueColumnWidth = allocatedWidths(valueColumn)
    val extra = extraWidthForClassName(classNameOpt, fieldColumnWidth, valueColumnWidth)

    (fieldColumnWidth, valueColumnWidth + extra)
  }

  /**
    * Add some extra width if we need more room for the class name:
    */
  private def extraWidthForClassName(classNameOpt: Option[String], fieldColumnWidth: Int, valueColumnWidth: Int): Int = {
    val classNameWidth = classNameOpt.getOrElse("").size
    val extraNeededForClassName = classNameWidth - (fieldColumnWidth + valueColumnWidth + 1)
    val remainingSpare = terminalInfo.columns - (fieldColumnWidth + valueColumnWidth + 3)
    math.max(0, math.min(extraNeededForClassName, remainingSpare))
  }

  private def maxValueWidth(obj: MashObject): Int =
    if (obj.fields.isEmpty)
      0
    else
      obj.fields.values.map(valueWidth).max

  private def valueWidth(value: MashValue): Int = renderValue(value).size

  private def renderValue(value: MashValue): String = fieldRenderer.renderField(value, inCell = true)

  private def maxFieldWidth(obj: MashObject): Int =
    if (obj.fields.isEmpty) 0 else obj.fields.keySet.map(_.size).max

}