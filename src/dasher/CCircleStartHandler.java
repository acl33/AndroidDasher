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
import static dasher.CDasherModel.CROSS_Y;
import dasher.CDasherView.MutablePoint;

public class CCircleStartHandler extends CDefaultFilter.CStartHandler{
	
	protected boolean inCircle;
	/**
	 * Time (recorded as a UNIX timestamp) when we last entered the start circle
	 */
	protected long m_iEnterTime;
	
	/**
	 * Radius of the displayed circle in screen co-ordinates; -1 = needs recomputing
	 */	
	private int m_iScreenRadius=-1;
	
	/**
	 * Sole constructor. Creates a start handler in its default start state.
	 * Calculates the intial circle radius using LP_CIRCLE_PERCENT and LP_MAX_X.
	 * 
	 * @param EventHandler
	 * @param SettingsStore
	 * @param Interface
	 */
	public CCircleStartHandler(CDefaultFilter filt) { 
    	filt.super();
	}
	
	private CDasherView.Point screenCircleCenter;
	
	/** Return the center of the circle in screen coordinates */
	protected CDasherView.Point getScreenCenter(CDasherView pView) {
		if (screenCircleCenter==null)
			screenCircleCenter = pView.Dasher2Screen(CROSS_X, CROSS_Y);
		return screenCircleCenter;
	}
	
	protected int getScreenRadius(CDasherView pView) {
		if (m_iScreenRadius==-1)
			m_iScreenRadius = (int)(Math.min(pView.Screen().GetWidth(), pView.Screen().GetHeight()) * GetLongParameter(Elp_parameters.LP_CIRCLE_PERCENT)/100);
		return m_iScreenRadius;
	}
	
	/**
	 * Draws the start handler to the passed view.
	 * 
	 * @param View DasherView upon which to run drawing commands.
	 * @return True, to indicate that something has been drawn.
	 */
	@Override public boolean DecorateView(CDasherView View, CDasherInput pInput) {
				
		CDasherView.Point C = getScreenCenter(View);
		int rad = getScreenRadius(View);
		boolean bAboutToChange = inCircle && m_iEnterTime!=Integer.MAX_VALUE;
		int fillColor, lineColor, lineWidth;
		if (GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED)) {
			lineColor = 2;
			lineWidth = 1;
			fillColor = bAboutToChange ? 241 : 242;
		} else {
			lineColor = 240;
			fillColor = -1; //don't fill
			lineWidth = bAboutToChange ? 3 : 1;
		}
		View.Screen().DrawCircle(C.x, C.y, rad, fillColor, lineColor, lineWidth);
		
		return true;
	}
	
	/**
	 * Updates the start handler's current state dependent on the current
	 * mouse position.
	 * <p>
	 * In a nutshell, it determines whether the mouse is within the circle,
	 * and if so, instructs Dasher to start or stop dependent on its
	 * current state and how long the user has hovered over the circle.
	 * <p>
	 * Specifically, we start and stop if the user has hovered for over 1 second.
	 * 
	 * @param iTime Current system time, as a UNIX time stamp.
	 * @param m_DasherView View against which co-ordinate transforms should be performed.
	 * @param m_DasherModel Model to which commands should be passed.
	 */
	@Override public void Timer(long iTime, MutablePoint lastInputCoords, CDasherView pView) {

		coords.init(lastInputCoords);
		pView.Dasher2Screen(coords);
		CDasherView.Point ctr = getScreenCenter(pView);
		long xdist = ctr.x - coords.x, ydist = ctr.y - coords.y, rad=getScreenRadius(pView);
		boolean inCircleNow = (xdist*xdist + ydist*ydist) < rad*rad; 
		if (inCircleNow) {
			if (inCircle) {
				//still in circle. Note test against MAX_VALUE here because overflow not doing what I expect (?!)
				if (m_iEnterTime!=Integer.MAX_VALUE && iTime-m_iEnterTime > 1000) {
					//activate!
					if (GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED))
						start(iTime);
					else
						stop(iTime);
					//note BP_DASHER_PAUSED event handler sets 
					// m_iEnterTime = Integer.MAX_VALUE;
					//meaning we will not trigger again, until we leave the circle and then enter again
				}
			} else {
				//just entered circle
				inCircle=true;
				m_iEnterTime = iTime;
			}
		} else {
			//currently out of circle
			inCircle=false;
		}
	}
	
	private final MutablePoint coords = new MutablePoint();
	
	/**
	 * Responds to events:
	 * <p>
	 * BP_DASHER_PAUSED changes: Updates start handler state
	 * so that we don't try to stop when already stopped, etc.
	 */
	public void observe(EParameters eChangedParam) {
		if (eChangedParam == Elp_parameters.LP_CIRCLE_PERCENT) {
			m_iScreenRadius=-1;
		} else if(eChangedParam == Ebp_parameters.BP_DASHER_PAUSED) {
			//if we're in the circle, reset our entry time - so the circle won't
			// be triggered (again) unless we leave the circle then enter again.
			m_iEnterTime = Integer.MAX_VALUE;
		}
	}
	
}
