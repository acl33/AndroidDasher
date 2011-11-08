/*
  This file is part of JDasher.

  JDasher is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  JDasher is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with JDasher; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

  Copyright (C) 2006      Christopher Smowton <cs448@cam.ac.uk>

  JDasher is a port derived from the Dasher project; for information on
  the project see www.dasher.org.uk; for information on JDasher itself
  and related projects see www.smowton.net/chris

*/

package dasher;

import static dasher.CDasherModel.*;
import dasher.CDasherView.MutablePoint;

/**
 * Dasher's current default input filter, otherwise known as 
 * Normal Control. This causes Dasher, when unpaused, to zoom
 * towards the mouse position with the goal that the spot the
 * mouse hovers over at time X should be under the crosshair at
 * some later time.
 * <p>
 * The filter also includes support for a number of Start Handlers,
 * and is responsible for instantiating and managing them if the
 * parameters relating to them are set appropriately.
 * <p>
 * It does not respond to key presses except to start and stop
 * on key 100.
 * <p>
 * Automatic Speed Control is also featured with the Default Filter,
 * using an instance of AutoSpeedControl for this purpose.
 * 
 * @see CAutoSpeedControl
 */
public class CDefaultFilter extends CDynamicFilter {

	public abstract class CStartHandler extends CDasherComponent {
		public CStartHandler() {
			super(CDefaultFilter.this);
			// TODO Auto-generated constructor stub
		}

		/** Subclasses should call this to start */
		protected void start(long iTime) {
			//ignore request if we're no longer the active StartHandler
			if (CDefaultFilter.this.m_StartHandler==this)
				CDefaultFilter.this.m_Interface.Unpause(iTime);
		}
		
		/** Subclasses should call this to stop */
		protected void stop(long iTime) {
			//ignore request if we're no longer the active StartHandler
			if (CDefaultFilter.this.m_StartHandler==this)
				CDefaultFilter.this.m_Interface.PauseAt(0, 0);
		}
		/**
		 * Similar to its companion method in CInputFilter, this gives
		 * the start handler an opportunity to draw itself and other
		 * relevant decorations during the production of a new frame.
		 * <p>
		 * Start handlers should ensure that this method can reliably
		 * terminate quickly, or performance will be greatly degraded.
		 * 
		 * @param View View to which we should draw decorations
		 * @return True if any decorating was done, false otherwise
		 */
		public abstract boolean DecorateView(CDasherView View, CDasherInput pInput);
		
		/**
		 * Fired during the start handler's parent input filter's
		 * Timer event. 
		 * 
		 * @param iTime Current system time as a unix timestamp
		 * @param inputCoords (Transformed) user input coordinates (Dasher coords)
		 * @param pView For converting coordinates into screen-space, if necessary.
		 */
		public abstract void Timer(long iTime, MutablePoint inputCoords, CDasherView pView);
	}
	
	/**
	 * Our Automatic Speed Control helper class
	 */
	protected CAutoSpeedControl m_AutoSpeedControl;
	
	/**
	 * The currently enabled start handler, if any 
	 */
	protected CStartHandler m_StartHandler;
	
	protected final MutablePoint lastInputCoords = new MutablePoint();
	
	/**
	 * Sole constructor. Constructs a DefaultFilter with an
	 * AutoSpeedControl helper class and runs CreateStartHandler
	 * to produce a start handler if this is necessary.
	 * 
	 * @param EventHandler Event handler with which to register ourselves
	 * @param SettingsStore Setting repository to use
	 * @param Interface Interface with which this Module should register itself
	 * @param m_DasherModel Model which will yield a FrameRate for creation of CAutoSpeedControl
	 * @param iID Unique ID for this module
	 * @param szName Friendly name for this module
	 */
	public CDefaultFilter(CDasherComponent creator, CDasherInterfaceBase iface, String szName)
	{ 
		super(creator, iface, szName);
		m_StartHandler = null;
		m_AutoSpeedControl = new CAutoSpeedControl(this);
		
		CreateStartHandler();
	}
		
	/**
	 * Draws the mouse square and mouse line to the specified View
	 * in the case that BP_DRAW_MOUSE and BP_DRAW_MOUSE_LINE are enabled
	 * respectively, and calls m_StartHandler's DecorateView method
	 * to draw the start handler, if applicable.
	 * 
	 * @param View View to which we should draw decorations
	 * @return True if this method drew anything, false if not.
	 */
	@Override public boolean DecorateView(CDasherView View, CDasherInput pInput) {
		
		boolean bDidSomething = (false);
		
		if (GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED)) {
			//not retrieving input coords in Timer, so better try here...
			if (!pInput.GetDasherCoords(View, lastInputCoords)) return false;
			ApplyTransform(View, lastInputCoords);
		}
		
		temp.init(lastInputCoords);
		View.Dasher2Screen(temp);
		if(GetBoolParameter(Ebp_parameters.BP_DRAW_MOUSE)) {
			// Draw a small box at the current mouse position.
			View.Screen().DrawRectangle((int)temp.x-5,(int)temp.y-5,(int)temp.x+5,(int)temp.y+5,
							GetBoolParameter(Ebp_parameters.BP_COLOUR_MODE) ? 2 : 1, -1, 1);
			bDidSomething = true;
		}
			
		if(GetBoolParameter(Ebp_parameters.BP_DRAW_MOUSE_LINE)) {
			/**
			 * Draws a line from the origin (LP_OX, LP_OY) to the current
			 * mouse position.
			 */
			// End of line is the mouse cursor location...(set above)
			final int mouseX = (int)temp.x, mouseY = (int)temp.y;
			
			//Start of line is the crosshair location
			//bah. Do we really have to do this every time? Would need notifying of screen changes...???
			temp.init(CROSS_X, CROSS_Y);
			View.Dasher2Screen(temp);
			
			// Actually plot the line
			View.Screen().drawLine((int)temp.x, (int)temp.y, mouseX, mouseY, (int)GetLongParameter(Elp_parameters.LP_LINE_WIDTH), GetBoolParameter(Ebp_parameters.BP_COLOUR_MODE) ? 1 : -1);

			bDidSomething = true;
		}
		
		if(m_StartHandler != null) {
			bDidSomething = m_StartHandler.DecorateView(View, pInput) || bDidSomething;
		}
		return bDidSomething;
	}
	
	/**
	 * See CInputFilter for an description of the method's purpose.
	 * <p>
	 * This filter simply asks the View for our mouse co-ordinates
	 * in Dasher space, and feeds them to the Model's Tap_on_display
	 * method which causes the model to move in this direction.
	 * <p>
	 * The coordinates are then fed to our AutoSpeedControl helper
	 * to fine tune the dashing speed, and our start handler
	 * is passed the View and Model pointers in case it too
	 * has action to take.
	 * 
	 * @param Time System time at which this event was called, as a UNIX timestamp
	 * @param m_DasherView View to query for input co-ordinates
	 * @param m_DasherModel Model to alter using these co-ordinates
	 * @return True if the model has been changed, false if not.
	 */
	@Override public boolean Timer(long Time, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
		boolean bDidSomething;
		if (!GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED)) {
			if (pInput.GetDasherCoords(pView,lastInputCoords)) {
				ApplyTransform(pView, lastInputCoords);
				float fSpeedMul = getSpeedMul(pModel, Time);
				pModel.oneStepTowards(lastInputCoords.x,lastInputCoords.y, Time, fSpeedMul);
			
				//Only measure the user's accuracy (for speed control) when going at full speed
				if (GetBoolParameter(Ebp_parameters.BP_AUTO_SPEEDCONTROL) && fSpeedMul==1.0f)
					m_AutoSpeedControl.SpeedControl(lastInputCoords.x, lastInputCoords.y, pView);
			} else {
				m_Interface.PauseAt(0, 0);
			}
			bDidSomething = true;
		} else {
			bDidSomething = false;
		}
		if(m_StartHandler != null) {
			m_StartHandler.Timer(Time, lastInputCoords, pView);
		}
		return bDidSomething;
	}
	
	/** Modify the input coordinates according to any desired remapping scheme.
	 * Subclasses may override to change the remapping; the default implementation:
	 * <ol>
	 * <li> First calls {@link #ApplyOffset(CDasherView, MutablePoint)};
	 * <li> then applies the eyetracker-remapping from C++ Dasher
	 * _iff_ BP_COMPRESS_XTREME is set. (This compresses the y coordinate at
	 * the extremes of the viewport, and also reduces the maximum x at extreme y
	 * values, so a movement towards a corner instead becomes up/down translation.)
	 * (Subclasses may override to change remapping.)
	 * </ol>
	 * @param inputCoords dasher co-ordinates of input
	 */
	protected void ApplyTransform(CDasherView pView, MutablePoint coords) {
		ApplyOffset(pView, coords);
		if (GetBoolParameter(Ebp_parameters.BP_REMAP_XTREME)) {
			// Y co-ordinate...
			double double_y = ((coords.y-CROSS_Y)/(double)CROSS_Y ); // Fraction above the crosshair
		  
			coords.y = (long)(CROSS_Y * (1.0 + double_y + (double_y*double_y*double_y * REPULSION_PARAM )));
		  
			// X co-ordinate...  
		 	coords.x = Math.max(coords.x,(long)(CROSS_X * xmax(double_y)));
		}
	}
	/**
	 * Adjusts the Y co-ordinate up or down according to LP_TARGET_OFFSET times 10.
	 * Then, <em>iff</em> <code>BP_AUTOCALIBRATE</code> is true and
	 * <code>BP_DASHER_PAUSED</code> is false, updates the offset params accordingly.
	 */
	protected void ApplyOffset(CDasherView pView, MutablePoint coords) {
		coords.x += GetLongParameter(Elp_parameters.LP_TARGET_OFFSET) * 10; //Urgh, arbitrary constants. Better would be screen range in dasher coords / pixels ???
		if (GetBoolParameter(Ebp_parameters.BP_AUTOCALIBRATE)) {
			if (!GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED)) {
			  m_iSum += (CROSS_Y - coords.y);
			  if (++m_iCounter>20) {
				  if (Math.abs(m_iSum) > MAX_Y/2)
					  SetLongParameter(Elp_parameters.LP_TARGET_OFFSET, GetLongParameter(Elp_parameters.LP_TARGET_OFFSET) + ((m_iSum>0) ? -1 : 1)) ;
				  m_iSum=m_iCounter=0; //TODO, reset m_iSum only if increment/decrement applied?
			  }
			}
		}
	}
	
	/** Parameters for auto-offset-calibration: avg offset over last m_iCounter frames, #frames since last adjust. */
	private int m_iSum, m_iCounter;
	
	/** Parameter for repelling Y value away from crosshair */
	private static final double REPULSION_PARAM=0.5;
	
	/** Parameters for determining max X from Y value */
	private static final int A=1, B=1, C=100;
	
	private double xmax(double y) {
		// DJCM -- define a function xmax(y) thus:
		// xmax(y) = a*[exp(b*y*y)-1] 
		// then:  if(x<xmax(y) [if the mouse is to the RIGHT of the line xmax(y)]
		// set x=xmax(y).  But set xmax=c if(xmax>c).
		// I would set a=1, b=1, c=16, to start with. 
		  
		return Math.min(C,A * (Math.exp(B * y * y) - 1));
		//cout << "xmax = " << xmax << endl;
	}
	
	/**
	 * Called by the InterfaceBase when a key is pressed. This
	 * filter responds to space and mouse by starting and stopping
	 * Dasher if BP_START_SPACE and BP_START_MOUSE are set respectively.
	 * <p>
	 * For a complete listing of key IDs, see the documentation
	 * for DasherInterfaceBase.KeyDown.
	 * 
	 * @param iTime System time when this key was pressed, as a UNIX timestamp
	 * @param iId Key identifier
	 * @param Model Ignored by this filter
	 */
	@Override public void KeyDown(long iTime, int iId, CDasherView pView, CDasherInput pInput, CDasherModel Model) {
		
		switch(iId) {
		case 0: // Start on space
			// FIXME - wrap this in a 'start/stop' method (and use for buttons as well as keys)
			if(GetBoolParameter(Ebp_parameters.BP_START_SPACE)) {
				if(GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED))
					m_Interface.Unpause(iTime);
				else
					m_Interface.PauseAt(0, 0);
			}
			break; 
		case 100: // Start on mouse
			if(GetBoolParameter(Ebp_parameters.BP_START_MOUSE)) {
				if(GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED))
					m_Interface.Unpause(iTime);
				else
					m_Interface.PauseAt(0, 0);
			}
			break;
		}
	}
	
	/**
	 * Called by the event handler when dispatching events.
	 * <p>
	 * This class responds to the following parameter changes:
	 * <p><i>BP_CIRCLE_START</i> and <i>BP_MOUSEPOS_MODE</i>: Recalls CreateStartHandler()
	 * in case we need to change our set start handler.
	 * 
	 * @param Event Event to handle
	 */
	public void HandleEvent(EParameters eParam) {
		if(eParam == Ebp_parameters.BP_CIRCLE_START || eParam == Ebp_parameters.BP_MOUSEPOS_MODE) {
			CreateStartHandler();
		}
		super.HandleEvent(eParam);
	}
	
	/**
	 * Creates a start handler. At present, this method will
	 * create a CircleStartHandler in response to BP_CIRCLE_START only.
	 * If CTwoBoxStartHandler is ported to Java in the future,
	 * there is commented code in the source which would instantiate
	 * this in response to BP_MOUSEPOS_MODE also.
	 */
	protected void CreateStartHandler() {
				
		if(GetBoolParameter(Ebp_parameters.BP_CIRCLE_START)) {
			m_StartHandler = new CCircleStartHandler(this);
		}
		/*else if(GetBoolParameter(Ebp_parameters.BP_MOUSEPOS_MODE))
			 m_StartHandler = new CTwoBoxStartHandler(m_EventHandler, m_SettingsStore, m_Interface); */
			// CSFS: Disabled for now, one is enough for testing purposes, if even that is necessary.
		else
			m_StartHandler = null; //will allow to be GC'd. Ignore if it's still around...
	}
	
	private final MutablePoint temp=new MutablePoint();
}
