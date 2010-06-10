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
 * A Module is a richer extension of a Component. It supplies all
 * the facilities of a Component, including registration for event
 * listening and access to a settings store, and adds some module-specific
 * functions, including:
 * <p>
 * <ul><li>Naming. Modules each have a name which is decided when
 * the class is constructed, in order to facilitate GetModuleByName.
 * <li>Type and ID, to classify modules without necessarily knowing
 * their class.
 */
public class CDasherModule extends CDasherComponent {
	
	/** {@link #GetType()} for input devices ({@link CDasherInput}) */
	public static final int INPUT_DEVICE=0;
	/** {@link #GetType()} for input filters ({@link CInputFilter}) */
	public static final int INPUT_FILTER=1;
	
	/**
	 * This module's unique identifier
	 */
	private long m_iID;
	
	/**
	 * This module's type number
	 * @see {@value #INPUT_DEVICE}, {@value #INPUT_FILTER}
	 */
	private int m_iType;
	
	/**
	 * Module name
	 */
	private String m_szName;
	
	/**
	 * Creates a new module, passing the appropriate parameters
	 * to DasherComponent's constructor. In order for this module
	 * to be used by the interface it should be wrapped in a 
	 * CWrapperFactory and then registered with the ModuleManager.
	 * 
	 * @param EventHandler EventHandler with which this module
	 * should register itself
	 * @param SettingsStore SettingsStore to use
	 * @param iID Unique ID
	 * @param iType Type number
	 * @param szName Friendly, preferably unique, name
	 */
	public CDasherModule(CEventHandler EventHandler, CSettingsStore SettingsStore, long iID, int iType, String szName) {
		super(EventHandler, SettingsStore);
		
		m_iID = iID;
		m_iType = iType;
		m_szName = szName;
	}
	
	/**
	 * Gets this module's unique ID
	 * 
	 * @return UID
	 */
	public long GetID() {
		return m_iID;
	}
	
	/**
	 * Gets this module's type ID
	 * 
	 * @return Type
	 */
	public final int GetType() {
		return m_iType;
	}
	
	/**
	 * Gets this module's name
	 * 
	 * @return Name
	 */
	public String GetName() {
		return m_szName;
	}
		
}
