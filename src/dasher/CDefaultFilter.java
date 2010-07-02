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
public class CDefaultFilter extends CInputFilter {

	/**
	 * Our Automatic Speed Control helper class
	 */
	protected CAutoSpeedControl m_AutoSpeedControl;
	
	/**
	 * The currently enabled start handler, if any 
	 */
	protected CStartHandler m_StartHandler;
	
	protected final long[] lastInputCoords = new long[2];
	
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
	public CDefaultFilter(CDasherInterfaceBase iface, CSettingsStore SettingsStore, long iID, String szName)
	{ 
		super(iface, SettingsStore, iID, szName);
		m_StartHandler = null;
		m_AutoSpeedControl = new CAutoSpeedControl(m_EventHandler, m_SettingsStore, iface.GetCurFPS());
		
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
	public boolean DecorateView(CDasherView View) {
		
		boolean bDidSomething = (false);
		
		if (!GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED)) {
			temp[0]=lastInputCoords[0];
			temp[1]=lastInputCoords[1];
			View.Dasher2Screen(temp);
			if(GetBoolParameter(Ebp_parameters.BP_DRAW_MOUSE)) {
				// Draw a small box at the current mouse position.
				View.Screen().DrawRectangle((int)temp[0]-5,(int)temp[1]-5,(int)temp[0]+5,(int)temp[1]+5,
								GetBoolParameter(Ebp_parameters.BP_COLOUR_MODE) ? 2 : 1, -1, 1);
				bDidSomething = true;
			}
			
			if(GetBoolParameter(Ebp_parameters.BP_DRAW_MOUSE_LINE)) {
				/**
				 * Draws a line from the origin (LP_OX, LP_OY) to the current
				 * mouse position.
				 */
				// End of line is the mouse cursor location...(set above)
				mouseX[1] = (int)temp[0];
				mouseY[1] = (int)temp[1];
				
				//Start of line is the crosshair location
				//bah. Do we really have to do this every time? Would need notifying of screen changes...???
				temp[0] = GetLongParameter(Elp_parameters.LP_OX);
				temp[1] = GetLongParameter(Elp_parameters.LP_OY);
				View.Dasher2Screen(temp);
				mouseX[0] = (int)temp[0];
				mouseY[0] = (int)temp[1];
				
				// Actually plot the line
				View.Screen().Polyline(mouseX, mouseY, (int)GetLongParameter(Elp_parameters.LP_LINE_WIDTH), GetBoolParameter(Ebp_parameters.BP_COLOUR_MODE) ? 1 : -1);

				bDidSomething = true;
			}
		}
		if(m_StartHandler != null) {
			bDidSomething = m_StartHandler.DecorateView(View) || bDidSomething;
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
	@Override public boolean Timer(long Time, CDasherView pView, CDasherInput pInput, CDasherModel m_DasherModel) {
		boolean bDidSomething;
		if (!GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED)) {
			pInput.GetDasherCoords(pView,lastInputCoords);
			ApplyTransform(pView, lastInputCoords);
			m_DasherModel.oneStepTowards(lastInputCoords[0],lastInputCoords[1], Time, null);
		
			m_AutoSpeedControl.SpeedControl(lastInputCoords[0], lastInputCoords[1], m_DasherModel.Framerate(), pView);
			
			bDidSomething = true;
		} else {
			bDidSomething = false;
		}
		if(m_StartHandler != null) {
			m_StartHandler.Timer(Time, pView, pInput, m_DasherModel);
		}
		return bDidSomething;
	}
	
	private static final double REPULSION_PARAM=0.5;

	/** Modify the input coordinates according to any desired remapping scheme.
	 * Subclasses may override to change the remapping; the default implementation:
	 * <ol>
	 * <li> _iff_ BP_SATURATE_X is true, sets x to either 0 or the view's
	 * maximum visible x coordinate ({@link CDasherView#VisibleRegion()},
	 * according to which side of the crosshair it is on;
	 * <li> applies the eyetracker-remapping from C++ Dasher
	 * _iff_ BP_COMPRESS_XTREME is set. (This compresses the y coordinate at
	 * the extremes of the viewport, and also reduces the maximum x at extreme y
	 * values, so a movement towards a corner instead becomes up/down translation.)
	 * (Subclasses may override to change remapping.)
	 * </ol>
	 * @param inputCoords dasher co-ordinates of input
	 */
	protected void ApplyTransform(CDasherView pView, long[] coords) {
		if (GetBoolParameter(Ebp_parameters.BP_SATURATE_X)) {
			if (coords[0] < GetLongParameter(Elp_parameters.LP_OX))
				coords[0] = 0;
			else coords[0] = pView.VisibleRegion().maxX;
		}
		if (GetBoolParameter(Ebp_parameters.BP_REMAP_XTREME)) {
			// Y co-ordinate...
			long dasherOY=GetLongParameter(Elp_parameters.LP_OY); 
			double double_y = ((coords[1]-dasherOY)/(double)(dasherOY) ); // Fraction above the crosshair
		  
			coords[1] = (long)(dasherOY * (1.0 + double_y + (double_y*double_y*double_y * REPULSION_PARAM )));
		  
			// X co-ordinate...  
		 	coords[0] = Math.max(coords[0],(long)(GetLongParameter(Elp_parameters.LP_OX) * xmax(double_y)));
		}
	}

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
	public void HandleEvent(CEvent Event) {
		if(Event instanceof CParameterNotificationEvent) {
			CParameterNotificationEvent Evt = (CParameterNotificationEvent)Event;
			
			if(Evt.m_iParameter == Ebp_parameters.BP_CIRCLE_START || 
					Evt.m_iParameter == Ebp_parameters.BP_MOUSEPOS_MODE) {
				CreateStartHandler();
				
			}
		}
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
			m_StartHandler = new CCircleStartHandler(m_EventHandler, m_SettingsStore, m_Interface);
		}
		else if(GetBoolParameter(Ebp_parameters.BP_MOUSEPOS_MODE)) {
			/* m_StartHandler = new CTwoBoxStartHandler(m_EventHandler, m_SettingsStore, m_Interface); */
		}
			// CSFS: Disabled for now, one is enough for testing purposes, if even that is necessary.
	}
	
	private final int[] mouseX=new int[2],mouseY=new int[2];
	private final long[] temp=new long[2];
}
