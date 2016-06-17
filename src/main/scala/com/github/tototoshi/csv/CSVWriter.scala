/*
* Copyright 2013 Toshiyuki Takahashi
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.github.tototoshi.csv

import java.io._

class CSVWriter(protected val writer: Writer)(implicit val format: CSVFormat) extends Closeable with Flushable {

  private val printWriter: PrintWriter = new PrintWriter(writer)
  private val quoteMinimalSpecs = List("\r", "\n", format.quoteChar.toString, format.delimiter.toString)

  def close(): Unit = printWriter.close()

  def flush(): Unit = printWriter.flush()

  private def writeNext(fields: Seq[Any]): Unit = {

    def shouldQuote(field: String, quoting: Quoting): Boolean =
      quoting match {
        case QUOTE_ALL => true
        case QUOTE_MINIMAL => quoteMinimalSpecs.exists(field.contains)
        case QUOTE_NONE => false
        case QUOTE_NONNUMERIC =>
          if (field.forall(_.isDigit)) {
            false
          } else {
            val firstCharIsDigit = field.headOption.exists(_.isDigit)
            if (firstCharIsDigit && (field.filterNot(_.isDigit) == ".")) {
              false
            } else {
              true
            }
          }
      }

    def quote(field: String): String =
      if (shouldQuote(field, format.quoting))
        format.quoteChar + field + format.quoteChar.toString
      else
        field

    def repeatQuoteChar(field: String): String =
      field.replace(format.quoteChar.toString, format.quoteChar.toString * 2)

    def escapeDelimiterChar(field: String): String =
      field.replace(format.delimiter.toString, format.escapeChar.toString + format.delimiter.toString)

    def show(s: Any): String = Option(s).getOrElse("").toString

    val renderField = {
      val escape = format.quoting match {
        case QUOTE_NONE => escapeDelimiterChar _
        case _ => repeatQuoteChar _
      }
      quote _ compose escape compose show
    }

    val iterator = fields.iterator
    var hasNext = iterator.hasNext
    while (hasNext) {
      printWriter.print(renderField(iterator.next()))
      hasNext = iterator.hasNext
      if (hasNext) {
        printWriter.print(format.delimiter)
      }
    }

    printWriter.print(format.lineTerminator)
  }

  def writeAll(allLines: Seq[Seq[Any]]): Unit = {
    allLines.foreach(line => writeNext(line))
    if (printWriter.checkError) {
      throw new java.io.IOException("Failed to write")
    }
  }

  def writeRow(fields: Seq[Any]): Unit = {
    writeNext(fields)
    if (printWriter.checkError) {
      throw new java.io.IOException("Failed to write")
    }
  }
}

object CSVWriter {

  def open(writer: Writer)(implicit format: CSVFormat): CSVWriter = {
    new CSVWriter(writer)(format)
  }

  def open(file: File)(implicit format: CSVFormat): CSVWriter = open(file, false, "UTF-8")(format)

  def open(file: File, encoding: String)(implicit format: CSVFormat): CSVWriter = open(file, false, encoding)(format)

  def open(file: File, append: Boolean)(implicit format: CSVFormat): CSVWriter = open(file, append, "UTF-8")(format)

  def open(file: File, append: Boolean, encoding: String)(implicit format: CSVFormat): CSVWriter = {
    val fos = new FileOutputStream(file, append)
    open(fos, encoding)(format)
  }

  def open(fos: OutputStream)(implicit format: CSVFormat): CSVWriter = open(fos, "UTF-8")(format)

  def open(fos: OutputStream, encoding: String)(implicit format: CSVFormat): CSVWriter = {
    try {
      val writer = new OutputStreamWriter(fos, encoding)
      open(writer)(format)
    } catch {
      case e: UnsupportedEncodingException => fos.close(); throw e
    }
  }

  def open(file: String)(implicit format: CSVFormat): CSVWriter = open(file, false, "UTF-8")(format)

  def open(file: String, encoding: String)(implicit format: CSVFormat): CSVWriter = open(file, false, encoding)(format)

  def open(file: String, append: Boolean)(implicit format: CSVFormat): CSVWriter = open(file, append, "UTF-8")(format)

  def open(file: String, append: Boolean, encoding: String)(implicit format: CSVFormat): CSVWriter =
    open(new File(file), append, encoding)(format)
}
