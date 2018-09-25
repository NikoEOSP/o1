package o1.gui

import o1.gui.event._
import View._


private[o1] object View {
  
  // Setting this to `true` disables GUI windows created by commands such as `View.start` and `Pic.show`.
  // (This is useful for purposes of automatic testing and assessment of certain student programs.
  // Generally, there should be no need to touch this setting, which defaults to `false`.) 
  var isInTestMode = false


  private[gui] trait Controls[Model] {
    import o1.sound.sampled.Sound
  
    private[gui] def makePic(state: Model): Pic
    private[gui] def onStop() = { }  
    private[gui] def isDone(state: Model) = false 
    private[gui] def isPaused(state: Model) = false
    private[gui] def sound(state: Model): Option[Sound] = None
  
    private[gui] def onTick(previousState: Model): Model
    
    // Simple GUI handlers:
    private[gui] def onMouseMove(state: Model, position: Pos)  : Model
    private[gui] def onMouseDrag(state: Model, position: Pos)  : Model
    private[gui] def onMouseDown(state: Model, position: Pos)  : Model 
    private[gui] def onMouseUp  (state: Model, position: Pos)  : Model 
    private[gui] def onWheel    (state: Model, rotation: Int)  : Model
    private[gui] def onClick    (state: Model, position: Pos)  : Model
    private[gui] def onKeyDown  (state: Model, key: Key)       : Model
    private[gui] def onKeyUp    (state: Model, key: Key)       : Model
    private[gui] def onType     (state: Model, character: Char): Model
  
    // Full GUI handlers:
    private[gui] def onMouseMove (state: Model, event: MouseMoved     ): Model
    private[gui] def onMouseDrag (state: Model, event: MouseDragged   ): Model
    private[gui] def onMouseEnter(state: Model, event: MouseEntered   ): Model
    private[gui] def onMouseExit (state: Model, event: MouseExited    ): Model
    private[gui] def onMouseUp   (state: Model, event: MouseReleased  ): Model
    private[gui] def onMouseDown (state: Model, event: MousePressed   ): Model
    private[gui] def onWheel     (state: Model, event: MouseWheelMoved): Model
    private[gui] def onClick     (state: Model, event: MouseClicked   ): Model
    private[gui] def onKeyDown   (state: Model, event: KeyPressed     ): Model
    private[gui] def onKeyUp     (state: Model, event: KeyReleased    ): Model
    private[gui] def onType      (state: Model, event: KeyTyped       ): Model
  }

  private[gui] object NoHandlerDefined extends Throwable 
  private[gui] def unimplementedDefaultHandler: Nothing = throw NoHandlerDefined

  private[gui] trait PauseImpl { 
    self: { def isPaused: Boolean } =>

    /** Whether the view starts in a paused state. By default, always returns `false`. */
    def startsPaused = false

    private var pauseToggle = this.startsPaused
    
    /** Tells the view to pause if unpaused and vice versa. */ 
    def togglePause() = {
      this.pauseToggle = !this.pauseToggle 
    }
    private[gui] def isPaused = this.pauseToggle
  }
  
  private[gui] trait TooltipDefaults {
    import javax.swing.ToolTipManager 
    ToolTipManager.sharedInstance.setInitialDelay(150)
  }
  
}




/** This package contains the version of `View`s that we primarily use in O1: views to
  * ''mutable'' domain models.
  * 
	* The top-level package [[o1]] provides an alias to the [[ViewFrame]] class in this 
	* package, so it is available to students as `View` simply by importing `o1._`. 
	* 
	* There is an alternative implementation of `View`s in [[o1.gui.immutable]]. */
object mutable {
  import View._

  
  /** An alias for [[ViewFrame]], which is the default sort of `View`. (Cf. the alternative, [[ViewComponent]].)  */
  type View[Model <: AnyRef] = ViewFrame[Model]
  

  private[mutable] type Controls[Model] = o1.gui.View.Controls[Model]

  private[mutable] trait HasModelField[Model] {
    private[mutable] def initialModel: Model 
    /** the model object represented in the view. */
    def model = this.initialModel
  }
  
  /** Mix in this trait on a view to enable discarding its model object for another. */
  trait HasReplaceableModel[Model] extends HasModelField[Model] {
    private[this] var currentModel = this.initialModel
    /** the model object most recently set for the view. 
      * @see [[model_=]] */
    override def model = this.currentModel
    /** Replaces the model object previously set for the view with the given one. */ 
    def model_=(replacementModel: Model) = {
      this.currentModel = replacementModel
    }
  }
  
  /** Mix in this trait on a view to give it a pause toggle. You’ll still need to call `togglePause`  
    * on whichever event you want to pause the view (e.g., user hitting space bar). */
  trait HasPauseToggle[Model <: AnyRef] extends ControlDefaults[Model] with PauseImpl {  
    /** Determines whether the view should be paused at the current state. */ 
    override def isPaused = super[PauseImpl].isPaused
  }

  /** A Swing-embeddable view (complete with a picture, a ticking clock, event handlers, etc.).  
    * It works like a [[ViewFrame]] except that it’s a Swing component, not a standalone GUI frame.
    * See [[ViewFrame]] for an overview.
    * 
    * @param initialModel      the model to be displayed in the view (the only required parameter).
    *                          It usually makes sense to use a mutable object here and change its state  
    *                          via the event handlers (cf. [[o1.gui.immutable.ViewComponent]]).
    * @param tickRate          the clock of the view will tick roughly this many times per second 
    *                          (optional; defaults to 24)
    * @param initialDelay      an additional delay in milliseconds between calling [[start]] and the 
    *                          clock starting (optional; defaults to 600)
    * @param refreshPolicy     a policy for how eagerly the view should try to update the graphical 
    *                          representation of its model (optional; changing this may improve 
    *                          efficiency in some circumstances)
    * @tparam Model  the type of the model object */
  abstract class ViewComponent[Model <: AnyRef](private[mutable] val initialModel: Model, tickRate: Double = TicksPerSecondDefault, initialDelay: Int = 600, refreshPolicy: RefreshPolicy = Always)
           extends ViewImpl.ViewComponent(initialModel, tickRate, initialDelay, refreshPolicy) with this.ControlDefaults[Model] 

  
  /** This class provides a framework for building simple GUIs. Each instance of the class is a graphical 
    * view to an object that represents a particular domain; that object is the [[model]] of the view. 
    * A `ViewFrame` displays the model as graphics within a GUI frame. 
    * 
    * This class is available under the alias `View` in the top-level package [[o1]], so students can 
    * access it simply by importing `o1._`.
    * 
    * The key method in the class is [[makePic]], which the view calls automatically and repeatedly to
    * determine which [[Pic]] to display in the frame at each moment in time. Concrete view objects must
    * add an implementation for this abstract method. 
    * 
    * A view listens to GUI events within the frame, but it doesn’t really do anything when notified 
    * of an event; concrete instances of the class can override this behavior by overriding one of the 
    * “on methods” (`onClick`, `onMouseMove`, etc.). The view also runs an internal clock and can react
    * to the passing of time (`onTick`). 
    * 
    * Just creating a view object is not enough to display it onscreen and start the clock; see the 
    * [[start]] method.
    *  
    * @param initialModel      the model to be displayed in the view (the only required parameter).
    *                          It usually makes sense to use a mutable object here and change its state  
    *                          via the event handlers (cf. [[o1.gui.immutable.ViewFrame]]).
    * @param tickRate          the clock of the view will tick roughly this many times per second 
    *                          (optional; defaults to 24)
    * @param title             a string to be displayed in the frame’s title bar (optional)
    * @param initialDelay      an additional delay in milliseconds between calling [[start]] and the 
    *                          clock starting (optional; defaults to 600)
    * @param terminateOnClose  whether the entire application should exit when the `ViewFrame` is closed 
    *                          (optional; defaults to `true`)   
    * @param closeWhenDone     whether the `ViewFrame` should be hidden and its clock stopped once the view  
    *                          has reached a “done state” (as per [[isDone]]) (optional; defaults to `false`)
    * @param refreshPolicy     a policy for how eagerly the view should try to update the graphical 
    *                          representation of its model (optional; changing this may improve 
    *                          efficiency in some circumstances)
    * @tparam Model  the type of the model object  */
  abstract class ViewFrame[Model <: AnyRef](private[mutable] val initialModel: Model, tickRate: Double = TicksPerSecondDefault, title: String = "", initialDelay: Int = 600, 
                                            terminateOnClose: Boolean = true, closeWhenDone: Boolean = false, refreshPolicy: RefreshPolicy = Always) 
           extends ViewImpl.ViewFrame(initialModel, tickRate, title, initialDelay, terminateOnClose, closeWhenDone, refreshPolicy) with this.ControlDefaults[Model] {
    
    /** An alternative constructor. Takes in just the model and the title; uses the defaults for all  
      * the other parameters. Please see the multi-parameter constructor for details.
      * @param initialModel      the model to be displayed in the view
      * @param title             a string to be displayed in the frame’s title bar */
    def this(initialModel: Model, title: String) = this(initialModel, TicksPerSecondDefault, title)

    /** An alternative constructor. Takes in just the model and the tick rate; uses the defaults for   
      * all the other parameters. Please see the multi-parameter constructor for details.
      * @param initialModel      the model to be displayed in the view
      * @param tickRate          the clock of the view will tick roughly this many times per second */ 
    def this(initialModel: Model, tickRate: Double) = this(initialModel, tickRate, "")
  }

  
  private[mutable] trait ControlDefaults[Model] extends Controls[Model] with TooltipDefaults with HasModelField[Model] {
    
    /** Returns a [[Pic]] that graphically represents the current state of the view’s `model` 
      * object. This method is automatically invoked by the view after GUI events and clock ticks.
      * Left abstract by this class so any concrete view needs to add a custom implementation.
      * 
      * For best results, all invocations of this method on a single view object should return
      * `Pic`s of equal dimensions. */
    def makePic: Pic
    
    
    /** Determines if the given state is a “done state” for the view. By default, this is never   
      * the case, but that behavior can be overridden. 
      *   
      *  Once done, the view stops reacting to events and updating its graphics and may close 
      *  its GUI window, depending on the constructor parameters of the view. */
    def isDone = super.isDone(this.model) 
    
    
    /** Determines whether the view should be paused at the current state. 
      * By default, always returns `false`. 
      * @see [[HasPauseToggle]] */
    def isPaused = super.isPaused(this.model)
    
    
    /** Determines whether the view should play a sound, given the current state of its model.
      * By default, no sounds are played.  
      * @return a [[Sound]] that the view should play; `None` if no sound is appropriate for 
      *         the current state */  
    def sound = super.sound(this.model)

    
    /** Causes an additional effect when the view is stopped (with `stop()`).
      * By default, this method does nothing. */  
    override def onStop() = super.onStop()  
    
    
    /** Programmatically requests an update to the graphics of the view (even though no
      * clock tick or triggering GUI event occurred). */     
    def refresh(): Unit

    
    /** Causes an effect whenever the view’s internal clock ticks.
      * Does nothing by default but can be overridden. */
    def onTick(): Unit = unimplementedDefaultHandler  
    

    
    //////////////////////////      SIMPLE HANDLERS       //////////////////////////       

    
    /** Causes an effect whenever the mouse cursor moves above the view.
      * Does nothing by default but can be overridden. 
      * 
      * If the desired behavior depends on detailed information about the GUI event, you 
      * may want to implement the other method of the same name instead of this one.  
      * 
      * @param position  the position of the mouse cursor relative to the view’s top left-hand corner */
    def onMouseMove(position: Pos): Unit = unimplementedDefaultHandler

    
    /** Causes an effect whenever the mouse cursor is dragged above the view.
      * Does nothing by default but can be overridden. 
      * 
      * If the desired behavior depends on detailed information about the GUI event, you 
      * may want to implement the other method of the same name instead of this one.  
      * 
      * @param position  the position of the mouse cursor relative to the view’s top left-hand corner */
    def onMouseDrag(position: Pos): Unit = unimplementedDefaultHandler

    
    /** Causes an effect whenever the mouse wheel is rotated above the view.
      * Does nothing by default but can be overridden. 
      * 
      * If the desired behavior depends on detailed information about the GUI event, you 
      * may want to implement the other method of the same name instead of this one.  
      * 
      * @param rotation  the number of steps the wheel rotated (negative means up, positive down) */
    def onWheel(rotation: Int): Unit = unimplementedDefaultHandler

    
    /** Causes an effect whenever a mouse button is clicked (pressed+released, possibly multiple
      * times in sequence) above the view. Does nothing by default but can be overridden. 
      * 
      * If the desired behavior depends on detailed information about the GUI event, you 
      * may want to implement the other method of the same name instead of this one.  
      * 
      * @param position  the position of the mouse cursor relative to the view’s top left-hand corner */
    def onClick(position: Pos): Unit = unimplementedDefaultHandler

    
    /** Causes an effect whenever a mouse button is pressed down above the view.
      * Does nothing by default but can be overridden. 
      * 
      * If the desired behavior depends on detailed information about the GUI event, you 
      * may want to implement the other method of the same name instead of this one.  
      * 
      * @param position  the position of the mouse cursor relative to the view’s top left-hand corner */
    def onMouseDown(position: Pos): Unit = unimplementedDefaultHandler

    
    /** Causes an effect whenever a mouse button is released above the view.
      * Does nothing by default but can be overridden. 
      * 
      * If the desired behavior depends on detailed information about the GUI event, you 
      * may want to implement the other method of the same name instead of this one.  
      * 
      * @param position  the position of the mouse cursor relative to the view’s top left-hand corner */
    def onMouseUp(position: Pos): Unit = unimplementedDefaultHandler

    
    /** Causes an effect whenever a key on the keyboard is pressed down while the view  
      * has the keyboard focus. Does nothing by default but can be overridden. 
      * 
      * If the desired behavior depends on detailed information about the GUI event, you 
      * may want to implement the other method of the same name instead of this one.  
      * 
      * @param key  the key that was pressed down  */
    def onKeyDown(key: Key): Unit = unimplementedDefaultHandler

    
    /** Causes an effect whenever a key on the keyboard is released while the view  
      * has the keyboard focus. Does nothing by default but can be overridden. 
      * 
      * If the desired behavior depends on detailed information about the GUI event, you 
      * may want to implement the other method of the same name instead of this one.  
      * 
      * @param key  the key that was released  */
    def onKeyUp(key: Key): Unit = unimplementedDefaultHandler

    
    /** Causes an effect whenever a key on the keyboard is typed (pressed+released) while   
      * the view has the keyboard focus. Does nothing by default but can be overridden. 
      * 
      * If the desired behavior depends on detailed information about the GUI event, you 
      * may want to implement the other method of the same name instead of this one.  
      * 
      * @param key  the key that was typed */
    def onType(character: Char): Unit = unimplementedDefaultHandler


    
    
    
    //////////////////////////     FULL HANDLERS       //////////////////////////       
    
        
    /** Causes an effect whenever the mouse cursor moves above the view.
      * Does nothing by default but can be overridden. 
      * 
      * If you don’t need much information about the GUI event, you may find it simpler 
      * to implement the other method of the same name instead of this one.
      * 
      * @param event  the GUI event that caused this handler to be called */
    def onMouseMove(event: MouseMoved): Unit = unimplementedDefaultHandler

    
    /** Causes an effect whenever the mouse cursor is dragged above the view.
      * Does nothing by default but can be overridden. 
      * 
      * If you don’t need much information about the GUI event, you may find it simpler 
      * to implement the other method of the same name instead of this one.
      * 
      * @param event  the GUI event that caused this handler to be called */
    def onMouseDrag(event: MouseDragged): Unit = unimplementedDefaultHandler
    
    
    /** Causes an effect whenever the mouse wheel is rotated above the view.
      * Does nothing by default but can be overridden. 
      * 
      * If you don’t need much information about the GUI event, you may find it simpler 
      * to implement the other method of the same name instead of this one.
      * 
      * @param event  the GUI event that caused this handler to be called */
    def onWheel(event: MouseWheelMoved): Unit = unimplementedDefaultHandler

    
    /** Causes an effect whenever a mouse button is clicked (pressed+released, possibly multiple
      * times in sequence) above the view. Does nothing by default but can be overridden. 
      * 
      * If you don’t need much information about the GUI event, you may find it simpler 
      * to implement the other method of the same name instead of this one.
      * 
      * @param event  the GUI event that caused this handler to be called */
    def onClick(event: MouseClicked): Unit = unimplementedDefaultHandler

    
    /** Causes an effect whenever a mouse button is pressed down above the view.
      * Does nothing by default but can be overridden. 
      * 
      * If you don’t need much information about the GUI event, you may find it simpler 
      * to implement the other method of the same name instead of this one.
      * 
      * @param event  the GUI event that caused this handler to be called */
    def onMouseDown(event: MousePressed): Unit = unimplementedDefaultHandler

    
    /** Causes an effect whenever a mouse button is released above the view.
      * Does nothing by default but can be overridden. 
      * 
      * If you don’t need much information about the GUI event, you may find it simpler 
      * to implement the other method of the same name instead of this one.
      * 
      * @param event  the GUI event that caused this handler to be called */
    def onMouseUp(event: MouseReleased): Unit = unimplementedDefaultHandler
    
    
    /** Causes an effect whenever the mouse cursor enters the view.
      * Does nothing by default but can be overridden. 
      * @param event  the GUI event that caused this handler to be called */
    def onMouseEnter(event: MouseEntered): Unit = unimplementedDefaultHandler
    
    
    /** Causes an effect whenever the mouse cursor exits the view.
      * Does nothing by default but can be overridden. 
      * @param event  the GUI event that caused this handler to be called */
    def onMouseExit(event: MouseExited): Unit = unimplementedDefaultHandler
    
    
    /** Causes an effect whenever a key on the keyboard is pressed down while the view  
      * has the keyboard focus. Does nothing by default but can be overridden. 
      * 
      * If you don’t need much information about the GUI event, you may find it simpler 
      * to implement the other method of the same name instead of this one.
      * 
      * @param event  the GUI event that caused this handler to be called */
    def onKeyDown(event: KeyPressed): Unit = unimplementedDefaultHandler
    
    
    /** Causes an effect whenever a key on the keyboard is released while the view  
      * has the keyboard focus. Does nothing by default but can be overridden. 
      * 
      * If you don’t need much information about the GUI event, you may find it simpler 
      * to implement the other method of the same name instead of this one.
      * 
      * @param event  the GUI event that caused this handler to be called */
    def onKeyUp(event: KeyReleased): Unit = unimplementedDefaultHandler
    
    
    /** Causes an effect whenever a key on the keyboard is typed (pressed+released) while   
      * the view has the keyboard focus. Does nothing by default but can be overridden. 
      * 
      * If you don’t need much information about the GUI event, you may find it simpler 
      * to implement the other method of the same name instead of this one.
      * 
      * @param event  the GUI event that caused this handler to be called */
    def onType(event: KeyTyped): Unit = unimplementedDefaultHandler


    
    
    final override private[gui] def makePic (model: Model) = this.makePic
    final override private[gui] def isDone  (model: Model) = this.isDone 
    final override private[gui] def isPaused(model: Model) = this.isPaused
    final override private[gui] def sound   (model: Model) = this.sound
  
    final override private[gui] def onTick(model: Model) = { this.onTick(); this.model } 
    
    final override private[gui] def onMouseMove(model: Model, position: Pos)   = { this.onMouseMove(position); this.model }
    final override private[gui] def onMouseDrag(model: Model, position: Pos)   = { this.onMouseDrag(position); this.model }
    final override private[gui] def onWheel    (model: Model, rotation: Int)   = { this.onWheel(rotation)    ; this.model }
    final override private[gui] def onClick    (model: Model, position: Pos)   = { this.onClick(position)    ; this.model }
    final override private[gui] def onMouseDown(model: Model, position: Pos)   = { this.onMouseDown(position); this.model }
    final override private[gui] def onMouseUp  (model: Model, position: Pos)   = { this.onMouseUp(position)  ; this.model }
    final override private[gui] def onKeyDown  (model: Model, key: Key)        = { this.onKeyDown(key)       ; this.model }
    final override private[gui] def onKeyUp    (model: Model, key: Key)        = { this.onKeyUp(key)         ; this.model }
    final override private[gui] def onType     (model: Model, character: Char) = { this.onType(character)    ; this.model }
  
    final override private[gui] def onMouseMove (model: Model, event: MouseMoved     ) = { this.onMouseMove(event) ; this.model }
    final override private[gui] def onMouseDrag (model: Model, event: MouseDragged   ) = { this.onMouseDrag(event) ; this.model }
    final override private[gui] def onMouseEnter(model: Model, event: MouseEntered   ) = { this.onMouseEnter(event); this.model }
    final override private[gui] def onMouseExit (model: Model, event: MouseExited    ) = { this.onMouseExit(event) ; this.model }
    final override private[gui] def onMouseUp   (model: Model, event: MouseReleased  ) = { this.onMouseUp(event)   ; this.model }
    final override private[gui] def onMouseDown (model: Model, event: MousePressed   ) = { this.onMouseDown(event) ; this.model }
    final override private[gui] def onWheel     (model: Model, event: MouseWheelMoved) = { this.onWheel(event)     ; this.model }
    final override private[gui] def onClick     (model: Model, event: MouseClicked   ) = { this.onClick(event)     ; this.model }
    final override private[gui] def onKeyDown   (model: Model, event: KeyPressed     ) = { this.onKeyDown(event)   ; this.model }
    final override private[gui] def onKeyUp     (model: Model, event: KeyReleased    ) = { this.onKeyUp(event)     ; this.model }
    final override private[gui] def onType      (model: Model, event: KeyTyped       ) = { this.onType(event)      ; this.model }
  
  }
}


/** This package contains a version of `View`s that is not much used in O1: views to ''immutable''   
  * domain models. In O1, the other implementation in [[o1.gui.mutable]] is more relevant. */  
object immutable {
  
  /** An alias for [[ViewFrame]], which is the default sort of `View`. (Cf. the alternative, [[ViewComponent]].)  */
  type View[Model] = ViewFrame[Model]

  private[immutable] type Controls[Model] = o1.gui.View.Controls[Model]
  

  /** A Swing-embeddable view (complete with a picture, a ticking clock, event handlers, etc.).  
    * It works like a [[ViewFrame]] except that it’s a Swing component, not a standalone GUI frame.
    * See [[ViewFrame]] for an overview.
    * 
    * @param initialState      the initial state of the model to be displayed in the view (the only 
    *                          required parameter). This class has been designed to work conveniently 
    *                          with immutable model objects (cf. [[o1.gui.mutable.ViewComponent]]).
    * @param tickRate          the clock of the view will tick roughly this many times per second 
    *                          (optional; defaults to 24)
    * @param initialDelay      an additional delay in milliseconds between calling [[start]] and the 
    *                          clock starting (optional; defaults to 600)
    * @param refreshPolicy     a policy for how eagerly the view should try to update the graphical 
    *                          representation of its model (optional; changing this may improve 
    *                          efficiency in some circumstances)
    * @tparam Model  the type of the states of the model */
  abstract class ViewComponent[Model](initialState: Model, tickRate: Double = TicksPerSecondDefault, initialDelay: Int = 600, refreshPolicy: RefreshPolicy = Always) 
           extends ViewImpl.ViewComponent(initialState, tickRate, initialDelay, refreshPolicy) with this.ControlDefaults[Model] 

  
  /** This class provides a framework for building simple GUIs. Each instance of the class is a graphical 
    * view to objects that represent the states of a domain model; those states can be (but are not required   
    * to be) immutable objects. A `ViewFrame` displays the model as graphics within a GUI frame. 
    * 
    * '''Note to students: this is not the view class that we commonly use in O1 but an alternative 
    * implementation. For the usual `View`, see [[o1.gui.mutable.ViewFrame here]].'''
    * 
    * The key method in the class is [[makePic]], which the view calls automatically and repeatedly to
    * determine which [[Pic]] to display in the frame at each moment in time. Concrete view objects must
    * add an implementation for this abstract method. 
    * 
    * A view listens to GUI events within the frame, but it doesn’t really do anything when notified 
    * of an event; concrete instances of the class can override this behavior by overriding one of the 
    * “on methods” (`onClick`, `onMouseMove`, etc.). The view also runs an internal clock and can react
    * to the passing of time (`onTick`). 
    * 
    * Just creating a view object is not enough to display it onscreen and start the clock; see the 
    * [[start]] method.
    * 
    * Please note that even though this class is designed to work with immutable model states, the 
    * actual `ViewFrame` is not itself immutable.
    * 
    * @param initialState      the initial state of the model to be displayed in the view (the only 
    *                          required parameter). This class has been designed to work conveniently 
    *                          with immutable model objects (cf. [[o1.gui.mutable.ViewFrame]]).
    * @param tickRate          the clock of the view will tick roughly this many times per second 
    *                          (optional; defaults to 24)
    * @param title             a string to be displayed in the frame’s title bar (optional)
    * @param initialDelay      an additional delay in milliseconds between calling [[start]] and the 
    *                          clock starting (optional; defaults to 600)
    * @param terminateOnClose  whether the entire application should exit when the `ViewFrame` is closed 
    *                          (optional; defaults to `true`)   
    * @param closeWhenDone     whether the `ViewFrame` should be hidden and its clock stopped once the view  
    *                          has reached a “done state” (as per [[isDone]]) (optional; defaults to `false`)
    * @param refreshPolicy     a policy for how eagerly the view should try to update the graphical 
    *                          representation of its model (optional; changing this may improve 
    *                          efficiency in some circumstances)
    * @tparam Model  the type of the states of the model */
  abstract class ViewFrame[Model](initialState: Model, tickRate: Double = TicksPerSecondDefault, title: String = "", initialDelay: Int = 600, terminateOnClose: Boolean = true, 
                                  closeWhenDone: Boolean = false, refreshPolicy: RefreshPolicy = Always) 
           extends ViewImpl.ViewFrame(initialState, tickRate, title, initialDelay, terminateOnClose, closeWhenDone, refreshPolicy) with this.ControlDefaults[Model] {

    /** An alternative constructor. Takes in just the initial state and the title; uses the defaults  
      * for all the other parameters. Please see the multi-parameter constructor for details.
      * @param initialState      the initial state of the model to be displayed in the view 
      * @param title             a string to be displayed in the frame’s title bar */
    def this(initialState: Model, title: String)    = this(initialState, TicksPerSecondDefault, title)

    /** An alternative constructor. Takes in just the initial state and the tick rate; uses the defaults    
      * for all the other parameters. Please see the multi-parameter constructor for details.
      * @param initialState      the initial state of the model to be displayed in the view
      * @param tickRate          the clock of the view will tick roughly this many times per second */ 
    def this(initialState: Model, tickRate: Double) = this(initialState, tickRate, "")
  }
  
  /** Mix in this trait to a view to give it a pause toggle. You’ll still need to call `togglePause`  
    * on whichever event you want to pause the view (e.g., user hitting space bar). */
  trait HasPauseToggle[Model] extends ControlDefaults[Model] with PauseImpl {
    /** Determines whether the view should be paused at the given state. 
      * @param state  a state of the model */ 
    override def isPaused(state: Model) = super[PauseImpl].isPaused
  }

  private[immutable] trait ControlDefaults[Model] extends Controls[Model] with TooltipDefaults {

    /** Returns a [[Pic]] that graphically represents the given state of the view’s model. 
      * This method is automatically invoked by the view after GUI events and clock ticks.
      * Left abstract by this class so any concrete view needs to add a custom implementation.
      * 
      * For best results, all invocations of this method on a single view object should return
      * `Pic`s of equal dimensions.
      *  
      * @param state  a state of the model to be displayed */ 
    def makePic(state: Model): Pic

    
    /** Determines if the given state is a “done state” for the view. By default, this is never   
      * the case, but that behavior can be overridden. 
      *   
      *  Once done, the view stops reacting to events and updating its graphics and may close 
      *  its GUi window, depending on the constructor parameters of the view.
      *  
      * @param state  a state of the model (possibly a done state) */ 
    override def isDone(state: Model) = super.isDone(state)
    
    
    /** Determines whether the view should be paused at the given state. 
      * By default, always returns `false`. 
      * @param state  a state of the model  
      * @see [[HasPauseToggle]] */
    override def isPaused(state: Model) = super.isPaused(state)


    /** Determines whether the view should play a sound, given a state of its model.
      * By default, no sounds are played.  
      * @param state  a state of the model  
      * @return a [[Sound]] that the view should play; `None` if no sound is appropriate  
      *         for the given state */  
    override def sound(state: Model) = super.sound(state)
  
    
    /** Causes an additional effect when the view is stopped (with `stop()`).
      * By default, this method does nothing. */  
    override def onStop() = super.onStop()  
  
    
    /** Determines what state should follow the given one on a tick of the view’s internal
      * clock. By default, just returns the unchanged state, but this can be overridden.
      * @param state  the state of the model before the clock tick */
    def onTick(previousState: Model): Model = unimplementedDefaultHandler
    
    
    
    //////////////////////////      SIMPLE HANDLERS       //////////////////////////       

        
    /** Determines what state should follow the given one when the mouse cursor moves above
      * the view. By default, just returns the unchanged state, but this can be overridden. 
      * 
      * If the desired behavior depends on detailed information about the GUI event, you 
      * may want to implement the other method of the same name instead of this one.  
      * 
      * @param state     the state of the model at the time of the move event 
      * @param position  the position of the mouse cursor relative to the view’s top left-hand corner */
    def onMouseMove(state: Model, position: Pos): Model = unimplementedDefaultHandler     

    
    /** Determines what state should follow the given one when the mouse cursor is dragged above
      * the view. By default, just returns the unchanged state, but this can be overridden. 
      * 
      * If the desired behavior depends on detailed information about the GUI event, you 
      * may want to implement the other method of the same name instead of this one.  
      * 
      * @param state     the state of the model at the time of the drag event 
      * @param position  the position of the mouse cursor relative to the view’s top left-hand corner */
    def onMouseDrag(state: Model, position: Pos): Model = unimplementedDefaultHandler

    
    /** Determines what state should follow the given one when the mouse wheel is rotated above
      * the view. By default, just returns the unchanged state, but this can be overridden. 
      * 
      * If the desired behavior depends on detailed information about the GUI event, you 
      * may want to implement the other method of the same name instead of this one.  
      * 
      * @param state     the state of the model at the time of the wheel event 
      * @param rotation  the number of steps the wheel rotated (negative means up, positive down) */
    def onWheel(state: Model, rotation: Int): Model = unimplementedDefaultHandler
    
    
    /** Determines what state should follow the given one when a mouse button is clicked 
      * (pressed+relesed, possibly multiple times in sequence) above the view. By default, 
      * just returns the unchanged state, but this can be overridden. 
      * 
      * If the desired behavior depends on detailed information about the GUI event, you 
      * may want to implement the other method of the same name instead of this one.  
      * 
      * @param state     the state of the model at the time of the click event 
      * @param position  the position of the mouse cursor relative to the view’s top left-hand corner */
    def onClick(state: Model, position: Pos): Model = unimplementedDefaultHandler
    
    
    /** Determines what state should follow the given one when a mouse button is pressed down 
      * above the view. By default, just returns the unchanged state, but this can be overridden. 
      * 
      * If the desired behavior depends on detailed information about the GUI event, you 
      * may want to implement the other method of the same name instead of this one.  
      * 
      * @param state     the state of the model at the time of the mouse event 
      * @param position  the position of the mouse cursor relative to the view’s top left-hand corner */
    def onMouseDown(state: Model, position: Pos): Model = unimplementedDefaultHandler
    
    
    /** Determines what state should follow the given one when a mouse button is released above 
      * the view. By default, just returns the unchanged state, but this can be overridden. 
      * 
      * If the desired behavior depends on detailed information about the GUI event, you 
      * may want to implement the other method of the same name instead of this one.  
      * 
      * @param state     the state of the model at the time of the mouse event 
      * @param position  the position of the mouse cursor relative to the view’s top left-hand corner */
    def onMouseUp(state: Model, position: Pos): Model = unimplementedDefaultHandler
    
    
    /** Determines what state should follow the given one when a key on the keyboard is pressed
      * down while the view has the keyboard focus. By default, just returns the unchanged state, 
      * but this can be overridden. 
      * 
      * If the desired behavior depends on detailed information about the GUI event, you 
      * may want to implement the other method of the same name instead of this one.  
      * 
      * @param state  the state of the model at the time of the keyboard event 
      * @param key    the key that was pressed down */
    def onKeyDown(state: Model, key: Key): Model = unimplementedDefaultHandler
    
    
    /** Determines what state should follow the given one when a key on the keyboard is released 
      * while the view has the keyboard focus. By default, just returns the unchanged state, but 
      * this can be overridden. 
      * 
      * If the desired behavior depends on detailed information about the GUI event, you 
      * may want to implement the other method of the same name instead of this one.  
      * 
      * @param state  the state of the model at the time of the keyboard event 
      * @param key    the key that was released */
    def onKeyUp(state: Model, key: Key): Model = unimplementedDefaultHandler
    
    
    /** Determines what state should follow the given one when a key on the keyboard is typed 
      * (pressed+released) while the view has the keyboard focus. By default, just returns the 
      * unchanged state, but this can be overridden. 
      * 
      * If the desired behavior depends on detailed information about the GUI event, you 
      * may want to implement the other method of the same name instead of this one.  
      * 
      * @param state  the state of the model at the time of the keyboard event 
      * @param key    the key that was typed */
    def onType(state: Model, character: Char): Model = unimplementedDefaultHandler
  

    
    
    
    //////////////////////////     FULL HANDLERS       //////////////////////////       
    
    
    /** Determines what state should follow the given one when the mouse cursor moves above
      * the view. By default, just returns the unchanged state, but this can be overridden. 
      * 
      * If you don’t need much information about the GUI event, you may find it simpler 
      * to implement the other method of the same name instead of this one.
      * 
      * @param state  the state of the model at the time of the move event 
      * @param event  the GUI event that caused this handler to be called */
    def onMouseMove(state: Model, event: MouseMoved): Model = unimplementedDefaultHandler

    
    /** Determines what state should follow the given one when the mouse cursor is dragged above
      * the view. By default, just returns the unchanged state, but this can be overridden. 
      * 
      * If you don’t need much information about the GUI event, you may find it simpler 
      * to implement the other method of the same name instead of this one.
      * 
      * @param state  the state of the model at the time of the drag event 
      * @param event  the GUI event that caused this handler to be called */
    def onMouseDrag(state: Model, event: MouseDragged): Model = unimplementedDefaultHandler
    
    
    /** Determines what state should follow the given one when the mouse wheel is rotated above
      * the view. By default, just returns the unchanged state, but this can be overridden. 
      * 
      * If you don’t need much information about the GUI event, you may find it simpler 
      * to implement the other method of the same name instead of this one.
      * 
      * @param state  the state of the model at the time of the wheel event 
      * @param event  the GUI event that caused this handler to be called */
    def onWheel(state: Model, event: MouseWheelMoved): Model = unimplementedDefaultHandler

    
    /** Determines what state should follow the given one when a mouse button is clicked 
      * (pressed+relesed, possibly multiple times in sequence) above the view. By default, 
      * just returns the unchanged state, but this can be overridden. 
      * 
      * If you don’t need much information about the GUI event, you may find it simpler 
      * to implement the other method of the same name instead of this one.
      * 
      * @param state  the state of the model at the time of the click event 
      * @param event  the GUI event that caused this handler to be called */
    def onClick(state: Model, event: MouseClicked): Model = unimplementedDefaultHandler

    
    /** Determines what state should follow the given one when a mouse button is pressed down 
      * above the view. By default, just returns the unchanged state, but this can be overridden. 
      * 
      * If you don’t need much information about the GUI event, you may find it simpler 
      * to implement the other method of the same name instead of this one.
      * 
      * @param state  the state of the model at the time of the mouse event 
      * @param event  the GUI event that caused this handler to be called */
    def onMouseDown(state: Model, event: MousePressed): Model = unimplementedDefaultHandler

    
    /** Determines what state should follow the given one when a mouse button is released above 
      * the view. By default, just returns the unchanged state, but this can be overridden. 
      * 
      * If you don’t need much information about the GUI event, you may find it simpler 
      * to implement the other method of the same name instead of this one.
      * 
      * @param state  the state of the model at the time of the mouse event 
      * @param event  the GUI event that caused this handler to be called */
    def onMouseUp(state: Model, event: MouseReleased): Model = unimplementedDefaultHandler

    
    /** Determines what state should follow the given one when the mouse cursor enters the
      * view. By default, just returns the unchanged state, but this can be overridden. 
      * @param state  the state of the model at the time of the mouse event 
      * @param event  the GUI event that caused this handler to be called */
    def onMouseEnter(state: Model, event: MouseEntered): Model = unimplementedDefaultHandler
    
    
    /** Determines what state should follow the given one when the mouse cursor exits the
      * view. By default, just returns the unchanged state, but this can be overridden. 
      * @param state  the state of the model at the time of the mouse event 
      * @param event  the GUI event that caused this handler to be called */
    def onMouseExit(state: Model, event: MouseExited): Model = unimplementedDefaultHandler
    
    
    
    /** Determines what state should follow the given one when a key on the keyboard is pressed
      * down while the view has the keyboard focus. By default, just returns the unchanged state, 
      * but this can be overridden. 
      * 
      * If you don’t need much information about the GUI event, you may find it simpler 
      * to implement the other method of the same name instead of this one.
      * 
      * @param state  the state of the model at the time of the keyboard event 
      * @param event  the GUI event that caused this handler to be called */
    def onKeyDown(state: Model, event: KeyPressed): Model = unimplementedDefaultHandler
    
    
    /** Determines what state should follow the given one when a key on the keyboard is released 
      * while the view has the keyboard focus. By default, just returns the unchanged state, but 
      * this can be overridden. 
      * 
      * If you don’t need much information about the GUI event, you may find it simpler 
      * to implement the other method of the same name instead of this one.
      * 
      * @param state  the state of the model at the time of the keyboard event 
      * @param event  the GUI event that caused this handler to be called */
    def onKeyUp(state: Model, event: KeyReleased): Model = unimplementedDefaultHandler
    
    
    /** Determines what state should follow the given one when a key on the keyboard is typed 
      * (pressed+released) while the view has the keyboard focus. By default, just returns the 
      * unchanged state, but this can be overridden. 
      * 
      * If you don’t need much information about the GUI event, you may find it simpler 
      * to implement the other method of the same name instead of this one.
      * 
      * @param state  the state of the model at the time of the keyboard event 
      * @param event  the GUI event that caused this handler to be called */
    def onType(state: Model, event: KeyTyped): Model = unimplementedDefaultHandler
  }
  
  
  
}