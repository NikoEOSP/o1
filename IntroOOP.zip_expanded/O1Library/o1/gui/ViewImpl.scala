
package o1.gui


import java.awt.image.BufferedImage

import scala.swing._

import o1.gui.View._
import o1.gui.event._
import o1.sound.sampled
import o1.util._
import o1.gui.colors.Black

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.typesafe.config.{Config, ConfigFactory}


// This stuff is common to o1.gui.mutable/immutable 
private[gui] object ViewImpl {


 
  private[gui] abstract class ViewFrame[Model](initialState: Model, tickRate: Double, protected val title: String,
                                               initialDelay: Int, val terminateOnClose: Boolean, val closeWhenDone: Boolean,  
                                               val refreshPolicy: RefreshPolicy) extends View.Controls[Model] {

    viewFrame =>

    private[this] lazy val viewComponent  = new ViewComponent(initialState, tickRate, initialDelay, refreshPolicy) with DelegateToContainingFrame
    private lazy val modelClassName = initialState.getClass.getName

    /** Causes an effect when the view’s GUI window is closed for any reason. By default, this method does nothing. */  
    def onClose(): Unit = { }

    
    private[o1] val swingFrame: Option[Frame] = if (View.isInTestMode) None else Some(this.makeSwingFrame) 

    private def makeSwingFrame = new SimpleFrame(this.title) with Escapable {
      contents = viewComponent
      override def closeOperation(): Unit = { viewFrame.shutdown() }
    } 
    
    /** whether this view’s GUI frame is visible onscreen */  
    final def visible: Boolean = this.swingFrame.map( _.visible ).getOrElse(false)

    
    /** Sets whether this view’s GUI frame is visible onscreen. */  
    final def visible_=(desiredVisibility: Boolean): Unit = {
      this.swingFrame.foreach( _.visible = desiredVisibility )
    }

    
    /** Sets a new tick rate for the view, replacing any previously set by the constructor or this method. */ 
    final def adjustSpeed(newTickRate: Double): Unit = {
      this.viewComponent.adjustSpeed(newTickRate)
    }

    
    /** Starts the view: loads the model in the GUI window, makes the window visible oncreen, and starts the clock. */
    final def start(): Unit = {
      if (View.isInTestMode) {
        println("Not starting GUI because in text-based test mode.")
      } else {
        this.viewComponent.start()
        this.swingFrame.foreach( _.pack() )
        this.visible = true
      }
    }

    private def shutdown(): Unit = {
      this.viewComponent.stop()
      this.onClose()
      if (this.terminateOnClose && !Program.isRunningInScalaREPL) {
        System.exit(0)
      }
      this.swingFrame.foreach( _.visible = false )
    }

    /** Stops the view: stops the clock, stops listening to events, and disposes of the GUI window. 
      * A stopped view cannot be restarted. */   
    final def stop(): Unit = {
      this.viewComponent.stop()
    }

    
    /** Programmatically requests an update to the graphics of the view (even though no
      * clock tick or triggering GUI event occurred). */     
    final def refresh(): Unit = {
      this.viewComponent.refresh()
    }

    /** Closes the view: stops it (as per [[stop]]), does any [[onClose]] effects, hides the GUI 
      * window, and possibly terminates the entire application (as per the constructor parameter). */
    final def close(): Unit = {
      this.shutdown()
    }

    /** the tooltip text to be displayed while the mouse overs on the view */
    final def tooltip: String = this.viewComponent.tooltip

    /** Sets the tooltip text to be displayed while the mouse overs on the view. */
    final def tooltip_=(newText: String): Unit = {
      this.viewComponent.tooltip = newText
    }

    
    /** Returns a brief textual description of the view. */ 
    override def toString: String = if (this.title.isEmpty) this.viewComponent.toString else s"""view "$title" of $modelClassName"""


    private trait DelegateToContainingFrame extends ViewComponent[Model] {
      def makePic(state: Model): Pic = viewFrame.makePic(state)
      override def isDone(state: Model): Boolean = viewFrame.isDone(state)
      override def isPaused(state: Model): Boolean = viewFrame.isPaused(state)
      override def sound(state: Model): Option[sampled.Sound] = viewFrame.sound(state)
      def onTick(previousState: Model): Model = viewFrame.onTick(previousState)
      def onMouseMove(state: Model, position: Pos): Model = viewFrame.onMouseMove(state, position)
      def onMouseDrag(state: Model, position: Pos): Model = viewFrame.onMouseDrag(state, position)
      def onWheel(state: Model, rotation: Int): Model = viewFrame.onWheel(state, rotation)
      def onClick(state: Model, position: Pos): Model = viewFrame.onClick(state, position)
      def onMouseDown(state: Model, position: Pos): Model = viewFrame.onMouseDown(state, position)
      def onMouseUp(state: Model, position: Pos): Model = viewFrame.onMouseUp(state, position)
      def onKeyDown(state: Model, key: Key): Model = viewFrame.onKeyDown(state, key)
      def onKeyUp(state: Model, key: Key): Model = viewFrame.onKeyUp(state, key)
      def onType(state: Model, character: Char): Model = viewFrame.onType(state, character)
      def onMouseMove(state: Model, event: MouseMoved): Model = viewFrame.onMouseMove(state, event)
      def onMouseDrag(state: Model, event: MouseDragged): Model = viewFrame.onMouseDrag(state, event)
      def onMouseEnter(state: Model, event: MouseEntered): Model = viewFrame.onMouseEnter(state, event)
      def onMouseExit(state: Model, event: MouseExited): Model = viewFrame.onMouseExit(state, event)
      def onMouseUp(state: Model, event: MouseReleased): Model = viewFrame.onMouseUp(state, event)
      def onMouseDown(state: Model, event: MousePressed): Model = viewFrame.onMouseDown(state, event)
      def onWheel(state: Model, event: MouseWheelMoved): Model = viewFrame.onWheel(state, event)
      def onClick(state: Model, event: MouseClicked): Model = viewFrame.onClick(state, event)
      def onKeyDown(state: Model, event: KeyPressed): Model = viewFrame.onKeyDown(state, event)
      def onKeyUp(state: Model, event: KeyReleased): Model = viewFrame.onKeyUp(state, event)
      def onType(state: Model, event: KeyTyped): Model = viewFrame.onType(state, event)
      private[gui] final override def onDone(): Unit = {
        this.stop()
        if (viewFrame.closeWhenDone) {
          viewFrame.close()
        }
      }

    }
  }




  // LATER: separate concerns (GUI component vs. events)
  private[gui] abstract class ViewComponent[Model](private val initialState: Model, tickRate: Double, private val initialDelay: Int, val refreshPolicy: RefreshPolicy)
      extends Component with View.Controls[Model] {
    view =>

    import Events._
    import Ticker._

    /** Sets a new tick rate for the view, replacing any previously set by the constructor or this method. */ 
    final def adjustSpeed(newTickRate: Double): Unit = {
      this.ticker.adjust(this.tickDelay(newTickRate))
    }

    this.listenTo(this.mouse.clicks, this.mouse.moves, this.mouse.wheel, this.keys)
    this.reactions += {case event: InputEvent => view.events ! GUIMessage(event)} 

    private[this]      val initialTickRate = tickRate
    private[this]      val eventSystem     = ActorSystem("ViewEventSystem", ConfigFactory.load("conf/akka.conf"))
    private[this] lazy val events          = eventSystem.actorOf(Props(new ModelState(initialState)).withDispatcher("view-mailbox"), name = "modelstate")
    private[this] lazy val ticker          = new Ticker(this.initialDelay, this.tickDelay(this.initialTickRate))(view.events ! new Tick)

    private def tickDelay(tickRate: Double) = TickState.fromTickRate(tickRate atMost TicksPerSecondMax)
    private[this] var hasStopped = false
    private[this] var latestComputed = Latest(initialState, None)
    private lazy val modelClassName = initialState.getClass.getName

    
    /** Starts the view: loads the model into the component and starts the clock. */
    final def start(): Unit = {
      if (this.hasStopped) {
        warn("Restarting a stopped view is not supported at the present time.")
      } else if (View.isInTestMode) {
        println("Not starting GUI because in text-based test mode.")
      } else {
        this.loadModel(this.initialState)
        this.requestFocusInWindow()
        this.ticker.start()
      }
    }


    private[gui] def onDone(): Unit = {
      this.stop()
    }


    /** Programmatically requests an update to the graphics of the view (even though no
      * clock tick or triggering GUI event occurred). */     
    final def refresh(): Unit = {
      view.events ! new Refresh
    }


    /** Stops the view: stops the clock and stops listening to events. A stopped view cannot be restarted. */   
    final def stop(): Unit = {
      this.hasStopped = true
      this.ticker.stop()
      this.eventSystem.terminate()
      this.onStop()
    }

    /** Returns a brief textual description of the view. */ 
    override def toString: String = s"view of $modelClassName"

    
    /** Renders the view as a [[https://docs.oracle.com/javase/8/docs/api/java/awt/image/BufferedImage.html
      * Java AWT `BufferedImage`]] onto the component. */
    final override def paintComponent(myGraphics: Graphics2D): Unit = {
      for (image <- latestComputed.pic.flatMap( _.toImage ) ) {
        myGraphics.drawImage(image, 0, 0, null)
      }
    }

    private[this] def loadModel(initialState: Model): Unit = {
      val initialPic =
        try {
          view.makePic(initialState).anchorAt(Anchor.Center)
        }
        catch {
          case NothingToDraw => Pic.square(150, Black)
        } 

      this.preferredSize =
          new java.awt.Dimension(
            initialPic.width.floor.toInt,
            initialPic.height.floor.toInt)

      this.latestComputed = Latest(initialState, Some(initialPic))
    }

    private[this] def renderIfAppropriate(currentState: Model): Unit = {
      if (view.refreshPolicy.shouldRefresh(this.latestComputed.state, currentState)) {
        this.render(currentState)
      } else {
        // no change; nothing to do
      }
    }

    private[this] def render(stateToDraw: Model): Unit = {
      Try(view.makePic(stateToDraw).anchorAt(Anchor.Center)) match {
        case Success(newPic)             => this.latestComputed = Latest(stateToDraw, Some(newPic)); this.repaint()
        case Failure(NothingToDraw)      => // keep earlier image
        case Failure(crashInClientCode)  => view.error("using makePic to render the view", crashInClientCode)
      }
    }

    private[this] def error(situation: String, cause: Throwable): Unit = {
      warn(s"An error occurred while $situation. Stopping the view. Here is a detailed report:", cause)
      this.stop()
    }




    private[this] final class ModelState(initialState: Model) extends Actor {

      private var isActive = true
      private var state    = initialState

      def receive: PartialFunction[Any, Unit] = {
        case message: Message => if (this.isActive) {this.handleIfHandlerEnabled(message)}
        case unexpected       => warn("Unexpected event: " + unexpected)
      }

      private def handleIfHandlerEnabled(message: Message): Unit = {
        val enabledHandlers = this.handlersFor(message).filter(_.isEnabled)

        def applyAllEnabledHandlers(): Unit = {
          val handlersForMessage = enabledHandlers.map((h: Handler) => (oldState: Model) => h(message, oldState))
          this.state = handlersForMessage.reduce(_ compose _)(this.state)
          Try(view.sound(this.state)) match {
            case Success(Some(sound))       => sound.play()
            case Success(None)              => // silence
            case Failure(crashInClientCode) => view.error("using the method sound on the view", crashInClientCode)
          }
          Try(view.isDone(this.state)) match {
            case Success(true)              => this.isActive = false; view.onDone()
            case Success(false)             => // continue
            case Failure(crashInClientCode) => view.error("using the method isDone on the view", crashInClientCode)
          }
          view.renderIfAppropriate(this.state)
        }

        message match {
          case refresh: Refresh =>
            view.renderIfAppropriate(this.state)
          case tick: Tick =>
            if (enabledHandlers.nonEmpty) {
              Try(view.isPaused(this.state)) match {
                case Success(true)              => // paused; ignore ticks
                case Success(false)             => applyAllEnabledHandlers()
                case Failure(crashInClientCode) => view.error("using the method isPaused on the view", crashInClientCode)
              }
            }
          case message: GUIMessage =>
            if (enabledHandlers.nonEmpty) {
              this.checkForGUIDelay(message)
              applyAllEnabledHandlers()
            }
        }
      }

      private def checkForGUIDelay(message: GUIMessage): Unit = {
        if (message.isDelayed) {
          val description = message.getClass
          warn(s"Response to GUI event ($description) lagging behind.")
        }
      }


      private case class Handler(underlying: PartialFunction[Message, Model => Model]) extends Function2[Message, Model, Model] {
        private var hasDefaulted = false
        private var hasCrashed   = false

        def apply(message: Message, oldState: Model): Model = {
          if (this.underlying.isDefinedAt(message)) {
            val callClientMethod = this.underlying(message)
            Try(callClientMethod(oldState)) match {
              case Success(newState)          =>
                newState
              case Failure(NoHandlerDefined)  =>
                this.hasDefaulted = true
                oldState
              case Failure(crashInClientCode) =>
                this.hasCrashed = true
                warn("An error occurred in event-handler code. Disabling the handler. Here is a detailed report:")
                crashInClientCode.printStackTrace(System.err)
                oldState
            }
          } else {
            warn("Unexpected failure to handle message: " + message)
            oldState
          }
        }
        def isEnabled: Boolean = !this.hasDefaulted && !this.hasCrashed
      }

      private def handlersFor(message: Message): Seq[Handler] = message match {
        case Tick()                             => Seq(SimpleHandlers.tick)
        case Refresh()                          => Seq()
        case GUIMessage(_: MouseEntered)        => Seq(FullHandlers.mouseEnter)
        case GUIMessage(_: MouseExited)         => Seq(FullHandlers.mouseExit)
        case GUIMessage(_: MouseReleased)       => Seq(SimpleHandlers.mouseUp, FullHandlers.mouseUp)
        case GUIMessage(_: MousePressed)        => Seq(SimpleHandlers.mouseDown, FullHandlers.mouseDown)
        case GUIMessage(_: MouseMoved)          => Seq(SimpleHandlers.mouseMove, FullHandlers.mouseMove)
        case GUIMessage(_: MouseDragged)        => Seq(SimpleHandlers.mouseDrag, FullHandlers.mouseDrag)
        case GUIMessage(_: MouseWheelMoved)     => Seq(SimpleHandlers.wheel, FullHandlers.wheel)
        case GUIMessage(_: MouseClicked)        => Seq(SimpleHandlers.click, FullHandlers.click)
        case GUIMessage(_: KeyPressed)          => Seq(SimpleHandlers.keyDown, FullHandlers.keyDown)
        case GUIMessage(_: KeyReleased)         => Seq(SimpleHandlers.keyUp, FullHandlers.keyUp)
        case GUIMessage(_: KeyTyped)            => Seq(SimpleHandlers.typed, FullHandlers.typed)
        case GUIMessage(unexpected: InputEvent) => warn("No valid handlers for event " + unexpected); Seq.empty
      }


      private[this] object SimpleHandlers {
        val tick      = Handler({case Tick() => (s: Model) => view.onTick(s)})
        val mouseMove = Handler({case GUIMessage(event: MouseMoved) => (s: Model) => view.onMouseMove(s, Pos(event.point))})
        val mouseDrag = Handler({case GUIMessage(event: MouseDragged) => (s: Model) => view.onMouseDrag(s, Pos(event.point))})
        val mouseDown = Handler({case GUIMessage(event: MousePressed) => (s: Model) => view.onMouseDown(s, Pos(event.point))})
        val mouseUp   = Handler({case GUIMessage(event: MouseReleased) => (s: Model) => view.onMouseUp(s, Pos(event.point))})
        val wheel     = Handler({case GUIMessage(event: MouseWheelMoved) => (s: Model) => view.onWheel(s, event.rotation)})
        val click     = Handler({case GUIMessage(event: MouseClicked) => (s: Model) => view.onClick(s, Pos(event.point))})
        val keyDown   = Handler({case GUIMessage(event: KeyPressed) => (s: Model) => view.onKeyDown(s, event.key)})
        val keyUp     = Handler({case GUIMessage(event: KeyReleased) => (s: Model) => view.onKeyUp(s, event.key)})
        val typed     = Handler({case GUIMessage(event: KeyTyped) => (s: Model) => view.onType(s, event.char)})
      }

      private[this] object FullHandlers {
        val mouseEnter = Handler({case GUIMessage(event: MouseEntered) => (s: Model) => view.onMouseEnter(s, event)})
        val mouseExit  = Handler({case GUIMessage(event: MouseExited) => (s: Model) => view.onMouseExit(s, event)})
        val mouseUp    = Handler({case GUIMessage(event: MouseReleased) => (s: Model) => view.onMouseUp(s, event)})
        val mouseDown  = Handler({case GUIMessage(event: MousePressed) => (s: Model) => view.onMouseDown(s, event)})
        val mouseMove  = Handler({case GUIMessage(event: MouseMoved) => (s: Model) => view.onMouseMove(s, event)})
        val mouseDrag  = Handler({case GUIMessage(event: MouseDragged) => (s: Model) => view.onMouseDrag(s, event)})
        val wheel      = Handler({case GUIMessage(event: MouseWheelMoved) => (s: Model) => view.onWheel(s, event)})
        val click      = Handler({case GUIMessage(event: MouseClicked) => (s: Model) => view.onClick(s, event)})
        val keyDown    = Handler({case GUIMessage(event: KeyPressed) => (s: Model) => view.onKeyDown(s, event)})
        val keyUp      = Handler({case GUIMessage(event: KeyReleased) => (s: Model) => view.onKeyUp(s, event)})
        val typed      = Handler({case GUIMessage(event: KeyTyped) => (s: Model) => view.onType(s, event)})
      }
    }
  }


  private case class Latest[State](state: State, pic: Option[Pic]) { 
    val timestamp: Long = System.currentTimeMillis
  }


  private val messageLagWarning = firstTimeOnly{warn("Failing to compute states fast enough. Discarding some clock ticks.")}

  private object Events {
    import akka.dispatch._
    import akka.util.StablePriorityBlockingQueue

    object Message {
      type Kind = String
    }

    sealed abstract class Message {
      val start: Long = System.currentTimeMillis()
      def delay: Long = System.currentTimeMillis() - this.start
      def isDelayed: Boolean = this.delay > 500
      def isBadlyDelayed: Boolean = this.delay > 3000
    }

    case class Tick() extends Message
    case class Refresh() extends Message
    case class GUIMessage(event: InputEvent) extends Message

    object Mailbox {
      val BacklogSizeThreshold: Int = TicksPerSecondDefault * 100
      val Priorities = PriorityGenerator{
        case tick: Tick => 100
        case guiMessage => 1
      }

      class Queue extends QueueBasedMessageQueue {
        final val queue = new StablePriorityBlockingQueue(100, Priorities)

        def dequeue(): Envelope = if (this.hasMessages) this.queue.remove() else null

        def enqueue(receiver: ActorRef, envelope: Envelope): Unit = envelope.message match {
          case Tick()          => this.addTickUnlessSwamped(envelope)
          case anyOtherMessage => this.queue.add(envelope)
        }

        private def addTickUnlessSwamped(envelope: Envelope): Unit = {
          if (this.queue.size() < Mailbox.BacklogSizeThreshold) {
            this.queue.add(envelope)
          } else {
            messageLagWarning()  // later: send to DeadLetters instead?
          }
        }
      }
    }

    case class Mailbox() extends MailboxType {
      def this(settings: ActorSystem.Settings, config: Config) = this()
      final override def create(owner: Option[ActorRef], system: Option[ActorSystem]) =
        new Mailbox.Queue
    }

  }
}
