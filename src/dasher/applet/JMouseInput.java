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

package dasher.applet;

import java.awt.event.*;

import dasher.CDasherView;

/**
 * Simple mouse input device which uses a mouse motion listener to
 * track the mouse position and reports the latest reading when
 * GetCoordinates is called.
 * <p>
 * Only methods which differ significantly from their abstract meanings
 * documented in CDasherInput are documented here; for further details
 * see CDasherInput.
 */
public class JMouseInput extends dasher.CDasherInput implements MouseMotionListener {

	/**
	 * Last seen mouse X co-ordinate
	 */
	private int mouseX;
	
	/**
	 * Last seen mouse Y co-ordinate
	 */
	private int mouseY;
	
	public JMouseInput() {
		super("Mouse Input"); 
	}

	/**
	 * Calls through to mouseMoved - as this gets called in place of the former, if a button is being held down...
	 */
	public void mouseDragged(MouseEvent e) {
		mouseMoved(e);
	}

	/**
	 * Stores the last seen mouse coordinates.
	 */
	public void mouseMoved(MouseEvent e) {
		mouseX = e.getX();
		mouseY = e.getY();
	}

	@Override
	public boolean GetScreenCoords(CDasherView pView, long[] into) {
		into[0] = mouseX;
		into[1] = mouseY;
		return true;
	}
	
}
