package com.github.mdr.mash.printer

import java.io.PrintStream
import java.time.format.{ DateTimeFormatter, FormatStyle }
import java.time.{ Instant, ZoneId, ZonedDateTime }
import java.util.Date

import com.github.mdr.mash.classes.{ BoundMethod, MashClass }
import com.github.mdr.mash.evaluator.ToStringifier
import com.github.mdr.mash.functions.MashFunction
import com.github.mdr.mash.ns.core.BytesClass
import com.github.mdr.mash.ns.core.help._
import com.github.mdr.mash.ns.git.StatusClass
import com.github.mdr.mash.ns.os.{ PermissionsClass, PermissionsSectionClass }
import com.github.mdr.mash.ns.time.{ MillisecondsClass, SecondsClass }
import com.github.mdr.mash.ns.view.ViewClass
import com.github.mdr.mash.printer.model.TwoDTableModelCreator.isSuitableForTwoDTable
import com.github.mdr.mash.printer.model._
import com.github.mdr.mash.runtime._
import com.github.mdr.mash.utils.{ Dimensions, NumberUtils }
import org.ocpsoft.prettytime.PrettyTime

case class ViewConfig(fuzzyTime: Boolean = true, browseLargeOutput: Boolean = true)

object Printer {

  val prettyTime = new PrettyTime

  def replaceProblematicChars(s: String): String = s.map {
    case '\t' | '\n' | '\r' | '\b' ⇒ ' '
    case c                         ⇒ c
  }

}

class FieldRenderer(viewConfig: ViewConfig) {

  private val dateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)

  def renderField(value: MashValue, inCell: Boolean = false): String = value match {
    case MashBoolean.True if inCell                       ⇒ "✓"
    case MashBoolean.False if inCell                      ⇒ "✗"
    case obj@MashObject(_, Some(PermissionsClass))        ⇒ PermissionsPrinter.permissionsString(obj)
    case obj@MashObject(_, Some(PermissionsSectionClass)) ⇒ PermissionsPrinter.permissionsSectionString(obj)
    case MashNumber(n, Some(BytesClass))                  ⇒ BytesPrinter.humanReadable(n)
    case MashNumber(n, Some(MillisecondsClass))           ⇒ NumberUtils.prettyString(n) + "ms"
    case MashNumber(n, Some(SecondsClass))                ⇒ NumberUtils.prettyString(n) + "s"
    case MashNumber(n, _)                                 ⇒ NumberUtils.prettyString(n)
    case MashWrapped(i: Instant) if viewConfig.fuzzyTime  ⇒ Printer.prettyTime.format(Date.from(i))
    case MashWrapped(i: Instant)                          ⇒ dateTimeFormatter.format(ZonedDateTime.ofInstant(i, ZoneId.systemDefault))
    case xs: MashList if inCell                           ⇒ xs.elements.map(renderField(_, inCell = inCell)).mkString(", ")
    case xs: MashList                                     ⇒ xs.elements.map(renderField(_, inCell = inCell)).mkString("[", ", ", "]")
    case _                                                ⇒
      val s = ToStringifier.safeStringify(value)
      if (inCell) Printer.replaceProblematicChars(s) else s
  }

}

case class PrintResult(displayModelOpt: Option[DisplayModel] = None)

class Printer(output: PrintStream, terminalSize: Dimensions, viewConfig: ViewConfig = ViewConfig(fuzzyTime = true)) {

  private val helpPrinter = new HelpPrinter(output)
  private val fieldRenderer = new FieldRenderer(viewConfig)

  case class PrintConfig(disableCustomViews: Boolean = false,
                         alwaysUseBrowser: Boolean = false,
                         alwaysUseTreeBrowser: Boolean = false)

  private def done = PrintResult()

  private def browse(model: DisplayModel) = PrintResult(Some(model))

  def print(value: MashValue,
            printConfig: PrintConfig = PrintConfig()): PrintResult =
    if (printConfig.alwaysUseBrowser) {
      val model = DisplayModel.getDisplayModel(value, viewConfig, terminalSize)
      PrintResult(Some(model))
    } else {
      value match {
        case _: MashList | _: MashObject if printConfig.alwaysUseTreeBrowser                                ⇒
          val model = new ObjectTreeModelCreator(viewConfig).create(value)
          browse(model)
        case _ if isSuitableForTwoDTable(value)                                                             ⇒
          printTwoD(value)
        case xs: MashList if xs.nonEmpty && xs.forall(x ⇒ x.isAString || x.isNull)                          ⇒
          printTextLines(xs)
        case obj: MashObject if obj.classOpt contains ViewClass                                             ⇒
          printView(obj)
        case obj: MashObject if obj.classOpt.contains(FunctionHelpClass) && !printConfig.disableCustomViews ⇒
          helpPrinter.printFunctionHelp(obj)
          done
        case obj: MashObject if obj.classOpt.contains(FieldHelpClass) && !printConfig.disableCustomViews    ⇒
          helpPrinter.printFieldHelp(obj)
          done
        case obj: MashObject if obj.classOpt.contains(ClassHelpClass) && !printConfig.disableCustomViews    ⇒
          helpPrinter.printClassHelp(obj)
          done
        case obj: MashObject if obj.classOpt.contains(StatusClass) && !printConfig.disableCustomViews       ⇒
          new GitStatusPrinter(output).print(obj)
          done
        case obj: MashObject if obj.nonEmpty                                                                ⇒
          new SingleObjectTablePrinter(output, terminalSize, viewConfig).printObject(obj)
          done
        case xs: MashList if xs.nonEmpty && xs.forall(_ == ((): Unit))                                      ⇒
          done // Don't print out sequence of unit
        case f: MashFunction if !printConfig.disableCustomViews                                             ⇒
          print(HelpCreator.getHelp(f), printConfig)
        case method: BoundMethod if !printConfig.disableCustomViews                                         ⇒
          print(HelpCreator.getHelp(method), printConfig)
        case klass: MashClass if !printConfig.disableCustomViews                                            ⇒
          print(HelpCreator.getHelp(klass), printConfig)
        case MashUnit                                                                                       ⇒
          // Don't print out Unit
          done
        case _                                                                                              ⇒
          output.println(fieldRenderer.renderField(value, inCell = false))
          done
      }
    }

  private def printView(obj: MashObject): PrintResult = {
    val view = ViewClass.Wrapper(obj)
    val printConfig = PrintConfig(
      disableCustomViews = view.disableCustomViews,
      alwaysUseBrowser = view.useBrowser,
      alwaysUseTreeBrowser = view.useTree)
    print(view.data, printConfig)
  }

  private def printTextLines(xs: MashList): PrintResult =
    if (xs.length > terminalSize.rows && viewConfig.browseLargeOutput) {
      val model = new TextLinesModelCreator(viewConfig).create(xs)
      browse(model)
    } else {
      xs.elements.foreach(output.println)
      done
    }

  private def printTwoD(value: MashValue): PrintResult = {
    val size = value match {
      case xs: MashList    ⇒ xs.size
      case obj: MashObject ⇒ obj.size
    }
    val nonDataRows = 4 // 3 header rows + 1 footer
    if (size > terminalSize.rows - nonDataRows && viewConfig.browseLargeOutput) {
      val model = new TwoDTableModelCreator(terminalSize, supportMarking = true, viewConfig).create(value)
      browse(model)
    } else {
      new TwoDTablePrinter(output, terminalSize, viewConfig).printTable(value)
      done
    }
  }

}