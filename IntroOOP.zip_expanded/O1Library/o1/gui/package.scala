package o1


import java.awt.{Dimension, Point}

import scala.language.implicitConversions

import smcl.colors.rgb.{Color => SMCLColor}
import smcl.pictures.{Viewport => SMCLViewport}
import scala.swing.{event => SwEv}

import o1.world._
import o1.util._
import o1.{world => W}
import o1.gui.event._

import javax.swing.KeyStroke


/** This package contains tools for building simple GUIs. The toolkit is particularly well suited 
  * to constructing GUIs that display information as loaded 2D images and/or geometric shapes.
  * It is not designed for demanding graphical needs that call for high efficiency. Some of the 
  * tools in this package are built on the Swing GUI library, and the two libraries can be used in 
  * combination.  
  *  
  * Some of the types in this package have aliases in the top-level package [[o1]], so they are 
  * accessible to students simply via `import o1._`. Some of the package contents are not available
  * in the top-level package and/or and included in this documentation. 
  * 
  * '''Please note:''' One of the key components of this package (views) comes in multiple varieties,  
  * which are defined in the [[mutable]] and [[immutable]] subpackages and not listed below. The 
  * `View` that is most commonly used in O1 (and aliased as `o1.View` in the top-level package) is 
  * [[o1.gui.mutable.ViewFrame]]. */
package object gui {

  o1.util.smclInit()

  
  /** The `Key` type represents keys on the keyboard; it is an alias for the corresponding type 
    * in Scala’s Swing GUI library */
  type Key = SwEv.Key.Value
  /** The `Key` type represents keys on the keyboard; it is an alias for the corresponding type 
    * in Scala’s Swing GUI library */
  val Key = SwEv.Key




  /** The `Pos` type represents locations on a two-dimensional plane; it is an alias for the 
    * [[o1.world.Pos class of the same name in `o1.world`]]. */
  type Pos = W.Pos
  /** The `Pos` type represents locations on a two-dimensional plane; it is an alias for the 
    * [[o1.world.Pos class of the same name in `o1.world`]]. */
  val Pos = W.Pos


  /** The `Bounds` type represents rectangular boundaries on a two-dimensional plane; it is an 
    * alias for the [[o1.world.Bounds class of the same name in `o1.world`]]. */
  type Bounds = W.Bounds
  /** The `Bounds` type represents rectangular boundaries on a two-dimensional plane; it is an 
    * alias for the [[o1.world.Bounds class of the same name in `o1.world`]]. */
  val Bounds = W.Bounds


  /** The `Anchor` type represents anchoring points of two-dimensional elements (such as [[Pic]]s) within   
    * other such elements; it is an alias for the [[o1.world.objects.Anchor class of the same name in `o1.world.objects`]]. */
  type Anchor = W.objects.Anchor
  /** The `Anchor` type represents anchoring points of two-dimensional elements (such as [[Pic]]s) within   
    * other such elements; it is an alias for the [[o1.world.objects.Anchor class of the same name in `o1.world.objects`]]. */
  val Anchor = W.objects.Anchor
  /** A supertype for two-dimensional elements that have an anchoring point; this is an alias for the 
    * [[o1.world.objects.Anchor class of the same name in `o1.world.objects`]]. */
  type HasAnchor = W.objects.HasAnchor


  /** Give this convenience trait to a Swing GUI frame to make it terminate the application when closed. */
  trait TerminatesOnClose extends swing.Window {
    /** Call `closeOperation` on the superclass, then terminates the entire application.
      * (Skips termination if it seems the program is running in the REPL.) */
    override def closeOperation(): Unit = {
      super.closeOperation()
      if (!Program.isRunningInScalaREPL) {
        System.exit(0)
      }
    }
  }


  /** Give this convenience trait to a Swing GUI frame to place it at (200, 200) and set it as unresizeable. */
  trait DefaultFrameSettings extends swing.Frame {
    resizable = false
    location = new Point(200, 200)
  }


  /** Inherit this class to obtain a Swing GUI frame that has the [[DefaultFrameSettings]] and the given title. */
  class SimpleFrame(initialTitle: String) extends swing.Frame with DefaultFrameSettings {
    this.title = initialTitle
  }


  /** Give this convenience trait to a Swing GUI frame to make it close when the Escape key is pressed. */
  trait Escapable extends swing.Frame {
    val escape: Int = java.awt.event.KeyEvent.VK_ESCAPE
    val escapeStroke: KeyStroke = javax.swing.KeyStroke.getKeyStroke(escape, 0)
    val wholeWindowScope: Int = javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
    this.peer.getRootPane.registerKeyboardAction( _ => this.closeOperation() , escapeStroke, wholeWindowScope)
  }


  private[o1] abstract class SimpleButton(title: String) extends swing.Button(title) {
    listenTo(this)
    def onClick(): Unit
    this.reactions += { case _: swing.event.ButtonClicked => this.onClick() }
  }

  
  private[o1] implicit class ConvenientDimension(val self: java.awt.Dimension) extends AnyVal {
    def withWidth(width: Int): Dimension = new Dimension(width, self.height)
    def withHeight(height: Int): Dimension = new Dimension(self.width, height)
    def wider(amount: Int): Dimension = this.withWidth(self.width + amount)
    def higher(amount: Int): Dimension = this.withHeight(self.height + amount)
  }


  private[o1] implicit class ConvenientImage(val self: java.awt.image.BufferedImage) extends AnyVal {
    def dimensions = new Dimension(self.getWidth, self.getHeight)
    def width: Int = self.getWidth
    def height: Int = self.getHeight
  }


  private[o1] implicit class ConvenientLabel(val self: scala.swing.Label) extends AnyVal {
    import javax.swing.Icon
    def icon = Option(self.icon)
    def icon_=(newIcon: Option[Icon]) = {
      self.icon = newIcon.getOrElse(null)
    }
  }


  private[o1] trait EverpresentTooltips {
    import javax.swing.ToolTipManager
    ToolTipManager.sharedInstance.setInitialDelay(0)
    ToolTipManager.sharedInstance.setDismissDelay(Int.MaxValue)
  }


  private[o1] trait O1SwingDefaults extends scala.swing.Reactor { // later: replace reactor with union type for App/SwingApplication/swing.Frame etc.
    O1SwingDefaults()
  }
  
  private[o1] object O1SwingDefaults {
    def apply() = {
      import javax.swing.{ToolTipManager, UIManager}
      ToolTipManager.sharedInstance.setInitialDelay(150)
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName)
    }
  }



  /** The number of clock ticks (24) that a `View` aims to generate per clock tick,
    * unless otherwise specified. */ 
  val TicksPerSecondDefault = 24

  private[gui] val TicksPerSecondMax = 1000
  
  
  /** A superclass for the different policies for updating the image visible in a `View` in  
    * response to a change in the model. 
    *
    * '''Note to students: You’re unlikely to need this for anything in O1.''' */
  abstract class RefreshPolicy {
    /** Returns a `Boolean` that indicates whether the `View` should try to update itself 
      * after observing a given state of the model. 
      * @param from  the model object at the previous update
      * @param to    the current model object (possibly the same object; possibly identical) */
    def shouldRefresh(from: Any, to: Any): Boolean
  }


  /** The [[RefreshPolicy]] of seeking to update the image visible in a `View` as often as possible. 
    * (Time-consuming but always safe.) 
    *
    * '''Note to students: You’re unlikely to need this for anything in O1.''' */
  case object Always extends RefreshPolicy {
    /** Returns `true` to indicated that the `View` should try to update itself no matter the 
      * current and earlier states of the model. */
    def shouldRefresh(from: Any, to: Any) = true
  }
  /** The [[RefreshPolicy]] of seeking to update the image visible in a `View` only when the current model
    * object is different in identity than the previous one shown. 
    *
    * '''Note to students: You’re unlikely to need this for anything in O1.''' */
  case object UnlessSameReference extends RefreshPolicy {
    /** Returns `true` if given two references to the same objects or two `AnyVal`s that are equal.
      * @param from  the model object at the previous update
      * @param to    the current model object (possibly the same object; possibly identical) */
    def shouldRefresh(from: Any, to: Any) = (from, to) match {
      case (ref1: AnyRef, ref2: AnyRef) => ref1 ne ref2
      case otherwise                    => from != to
    }
  }
  /** The [[RefreshPolicy]] of seeking to update the image visible in a `View` only when the current model
    * object is non-identical in terms of `equals` than the previous one shown.
    *
    * '''Note to students: You’re unlikely to need this for anything in O1.''' */
  case object UnlessIdentical extends RefreshPolicy {
    /** Returns `true` if given two objects aren’t equal (`!=`).
      * @param from  the model object at the previous update
      * @param to    the current model object (possibly the same object; possibly identical) */
    def shouldRefresh(from: Any, to: Any) = from != to
  }
  


  private[gui] object NothingToDraw extends RuntimeException
  
  
  private[gui] def warn(descriptor: Any): Unit = {
    System.err.println("o1.gui warning: " + descriptor)
  }
  private[gui] def warn(descriptor: Any, cause: Throwable): Unit = {
    warn(descriptor)
    cause.printStackTrace(System.err)
  }


  private[gui] implicit class ConvertableSMCLViewport(val self: SMCLViewport) extends AnyVal {
    def toO1Viewport: Viewport = Option(self).map( vp => Viewport(vp.boundary.toO1Bounds) ).getOrElse(Viewport.NotSet)  
  }
  

  private[gui] implicit class ConvertableSMCLColor(val self: SMCLColor) extends AnyVal {
    def toO1Color: Color = Color(self)
  }
  

}  

