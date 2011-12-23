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

import static dasher.CDasherModel.CROSS_X;
import dasher.CDasherView.MutablePoint;

/**
 * This is an InputFilter implementation which accepts mouse clicks
 * and causes Dasher to zoom to the location of successive clicks.
 * <p>
 * The filter does not pay any attention to the mouse position
 * except for when the user clicks the display, and does not
 * decorate the display in any way.
 * <p>
 * In order to zoom smoothly to a given location, it invokes
 * CDasherModel.ScheduleZoom, which interpolates a number of points
 * between the current crosshair location and the point clicked by
 * the user, and jumps to these points on each successive frame.
 * <p>
 * This filter registers itself with the name <i>Click Mode</i>.
 */

public class CClickFilter extends CStaticFilter {

	private long minX;
	/**
	 * Sole constructor. Calls the CInputFilter constructor with a type of 7,
	 * an ID of 1, and the name <i>Click Mode</i>.
	 * 
	 * @param EventHandler Event handler.
	 * @param SettingsStore Settings repository.
	 * @param Interface Interface with which the filter should be registered.
	 */
	public CClickFilter(CDasherComponent creator, CDasherInterfaceBase iface) {
	  super(creator, iface, "Click Mode");
	  HandleEvent(Elp_parameters.LP_MAX_ZOOM);
	}

	/**
	 * KeyDown is to be called by the Interface when the user
	 * presses a key or clicks the mouse. ClickFilter responds to:
	 * 
	 * <b>Left mouse button</b>: Schedules a zoom to the clicked location.
	 * 
	 * @param iTime Current system time as a UNIX timestamp.
	 * @param iId Key/button identifier.
	 * @param Model DasherModel which should be zoomed in response to clicks.
	 */
	@Override public void KeyDown(long iTime, int iId, CDasherView pView, CDasherInput pInput, CDasherModel Model) {

	  switch(iId) {
	  case 100: // Mouse clicks
	    pInput.GetDasherCoords(pView,inputCoords);
	    scheduleZoom(Model, Math.max(minX, (inputCoords.x*(1024+GetLongParameter(Elp_parameters.LP_S)))/1024),inputCoords.y);
	    m_Interface.Redraw(false);
	    break;
	  }
	}
	private final MutablePoint inputCoords = new MutablePoint();  

	@Override public void HandleEvent(EParameters eParam) {
		if (eParam==Elp_parameters.LP_MAX_ZOOM)
			minX = Math.max(2, CROSS_X/GetLongParameter(Elp_parameters.LP_MAX_ZOOM));
	}
}
