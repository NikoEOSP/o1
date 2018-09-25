package o1.gui

////////////////// NOTE TO STUDENTS //////////////////////////
// For the purposes of our course, it's not necessary
// that you understand or even look at the code in this file.
//////////////////////////////////////////////////////////////

import scala.collection.mutable.ListBuffer
import o1.gui.PicHistory.Operations._
import o1.util._

private[o1] object PicHistory {

  private val MinimumLength: Int = 1 // Has to be at least one operation (the creation)

  // The default total length of a new PicHistory; includes creation and processing.
  private val DefaultLength: Int = 10


  private[gui] def apply(creationOperation: CreationOperation): PicHistory =
    apply(creationOperation, DefaultLength)

  private[gui] def apply(creationOperation: CreationOperation, maximumHistoryLength: Int): PicHistory = 
    apply(creationOperation, List(), maximumHistoryLength)

  private[gui] def apply(creationOperation: CreationOperation, processingOperations: List[ProcessingOperation], maximumHistoryLength: Int): PicHistory = 
    new PicHistory(creationOperation, processingOperations, maximumHistoryLength atLeast MinimumLength)


  private[o1] object Operations {

    sealed abstract class AbstractOperation private[gui](private val methodName: String, val metadata: Option[Map[String, Boolean]]) {

      val calledMethodName: String = {
        val trimmed = methodName.trim
        if (trimmed.isEmpty) throw new IllegalArgumentException("Method name cannot be empty or contain only whitespace")
        trimmed
      }

      override def toString = {
        val b = StringBuilder.newBuilder
        b ++= calledMethodName
        if (metadata.isDefined) {
          b ++= " (" ++= metadata.toString ++= ")"
        }
        b.toString()
      }

      def booleanValue(key: String, defaultValue: Boolean): Boolean = 
        this.metadata.flatMap( _.get(key) ).getOrElse(defaultValue)
      
    }

    final case class CreationOperation private[gui](
        private val methodName: String, simpleDescription: String, override val metadata: Option[Map[String, Boolean]] = None)
      extends AbstractOperation(methodName, metadata)


    sealed abstract class ProcessingOperation private[gui](
          private val methodName: String, metadata: Option[Map[String, Boolean]])
        extends AbstractOperation(methodName, metadata) {
      // Whether this operation is expected to represent color or geometry transformation
      val isTransformation: Boolean = false
    }


    final case class ViewportOperation private[gui](
        private val methodName: String, override val metadata: Option[Map[String, Boolean]] = None)
        extends ProcessingOperation(methodName, metadata)


    final case class TransformationOperation private[gui](
        private val methodName: String, override val metadata: Option[Map[String, Boolean]] = None)
        extends ProcessingOperation(methodName, metadata) {
      override val isTransformation: Boolean = true
    }


    final case class MiscellaneousOperation private[gui](
        private val methodName: String, override val metadata: Option[Map[String, Boolean]] = None)
        extends ProcessingOperation(methodName, metadata)
  }

}



private[o1] class PicHistory private(
      val creationOperation: CreationOperation,
      val processingOperations: List[ProcessingOperation],
      val maximumLength: Int) {

  override def toString = methodNameList.mkString(", ")

  private[gui] def copy(newCreationOperation: CreationOperation = creationOperation,
                        newProcessingOperationList: List[ProcessingOperation] = processingOperations): PicHistory = 
    PicHistory(newCreationOperation, newProcessingOperationList, maximumLength)


  private[gui] def setHistoryLength(newMaximumLength: Int): PicHistory = {
    val length = newMaximumLength atLeast PicHistory.MinimumLength
    val newOperationList = shortenedProcessingOperationList(targetLength = length, forAddition = false)
    PicHistory(creationOperation, newOperationList, length)
  }


  private[gui] def add(processingOperation: ProcessingOperation): PicHistory = {
    if (maximumLength <= PicHistory.MinimumLength)
      return this.copy(newProcessingOperationList = List())
    val normalizedOperationList = shortenedProcessingOperationList(targetLength = maximumLength, forAddition = true)
    val newOperationList = processingOperation :: normalizedOperationList
    this.copy(newProcessingOperationList = newOperationList)
  }


  private def shortenedProcessingOperationList(targetLength: Int, forAddition: Boolean): List[ProcessingOperation] = {
    val oneForCreationOperation = 1
    val possiblyOneForAddition = if (forAddition) 1 else 0

    val numberOfOperationsToDrop =
      processingOperations.length -
          (targetLength - oneForCreationOperation - possiblyOneForAddition)

    processingOperations.dropRight(numberOfOperationsToDrop)
  }


  def methodNameList: List[String] = {
    val b = ListBuffer[String]()
    processingOperations foreach {
      b += _.calledMethodName
    }
    b += creationOperation.calledMethodName
    b.toList
  }

  private[gui] def containsTransformations: Boolean =
    processingOperations.exists( _.isTransformation )

}
