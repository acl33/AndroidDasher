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

import java.util.Collection;

import dasher.Ebp_parameters;
import dasher.Elp_parameters;
import dasher.Esp_parameters;

/**
 * Interface which must be implemented by all classes that wish
 * to hear about events spawned by the JDasherMenuBar.  
 * <p>
 * The functions will each be called when the menu item corresponding
 * to their name is invoked.
 */
public interface JDasherMenuBarListener {
	
	public void menuNew();
	
	public void menuCut();
	
	public void menuCopy();
	
	public void menuPaste();
	
	public void menuExit();
	
	public void menuSelFont();
	
	public void menuHelpAbout();
	
	// For clipboard control
	public boolean isDataFlavorAvailable(java.awt.datatransfer.DataFlavor flavour);
	
	public void menuSetString(Esp_parameters param, String val);
	public void menuSetLong(Elp_parameters param, long val);
	public void menuSetBool(Ebp_parameters param, boolean val);
}
