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
 * SettingsStore is a base implementation of a settings repository.
 * It has no ability to store its settings persistently, and will
 * load the defaults specified in CParamTables every time it is
 * instantiated.
 * <p>
 * In general the contract of a SettingsStore is to
 * <p><ul><li>Store parameter settings internally
 * <li>Optionally save these out to a persistent storage any time
 * a parameter changes
 * <li>Optionally restore parameters from this persistent store
 * when instaitated. 
 * <li>Raise a CParameterNotificationEvent whenever a parameter
 * is changed.
 * </ul>
 * <p>
 * Implementations wishing for persistent settings should subclass
 * CSettingsStore and, by subclassing CDasherInterfaceBase, ensure
 * that CreateSettingsStore is overridden to create their subclass.
 * <p>
 * Directly instantiating CSettingsStore will, however, be perfectly
 * functional for testing purposes or environments which cannot
 * save settings.
 * 
 */
public class CSettingsStore {
	private final boolean[] boolParamValues;
	private final long[] longParamValues;
	private final String[] stringParamValues;
	/**
	 * Event handler which we should notify whenever a parameter
	 * changes
	 */
	protected final CEventHandler m_pEventHandler;
	
	/**
	 * Same as new CSettingsStore(..., true)
	 * 
	 * @param pEventHandler Event handler which we should notify
	 * of parameter changes
	 */
	public CSettingsStore(CEventHandler pEventHandler) {
		
		this(pEventHandler, true);
		
	}
	
	/**
	 * Creates a new SettingsStore and instructs it to retrieve persistent
	 * settings if the backing store is ready to respond.
	 * 
	 * @param pEventHandler Event handler to notify of parameter changes
	 * @param readyYet Is the backing store ready; can we retrieve
	 * persistent settings?
	 */
	public CSettingsStore(CEventHandler pEventHandler, boolean readyYet) {
		
		m_pEventHandler = pEventHandler;
		boolParamValues = new boolean[Ebp_parameters.values().length];
		longParamValues = new long[Elp_parameters.values().length];
		stringParamValues = new String[Elp_parameters.values().length];
		if(readyYet) { // If the backing store is ready (prepared by a subclass)
			LoadPersistent();
		}
	}
	
	/* All C++-style integer based enums have now been replaced by three Enum types
	 * which implement EParameters, meaning one can pass both a generic parameter AND
	 * a specialised parameter. For references into the tables, the .ordinal() of
	 * a specialised parameter is used. All switch() statements should now check
	 * the parameter's type, cast it to the appropriate one, and then switch on
	 * the relevant enum. Alternatively it may be possible to have cases of a
	 * child-type, I've yet to check this.
	 * 
	 * 14/07: The whole codebase is now converted to use the new parameter scheme.
	 * It's broadly very solid; everything is passed around as enum types until the actual
	 * load/store instructions in CSettingsStore, whereupon ordinals are taken.
	 * 
	 * The only weakness is that one CANNOT in fact switch on an EParameters, since
	 * there is no way for the compiler to know that all its children are Enums.
	 * There may be some way around this -- some sort of enum-interface -- but
	 * I haven't found it yet. This can be solved by splitting any switch
	 * into three, type-checking, casting, and then switching in a type-specific
	 * manner.
	 */
	
	/**
	 * Loads persistent settings by means of the LoadSetting function.
	 * <p>
	 * If loading fails, the default value is retrieved and SaveSetting
	 * is called to save this out to our backing store. 
	 */
	public void LoadPersistent() {
		
		// Load each of the persistent parameters.  If we fail loading for the store, then 
		// we'll save the settings with the default value that comes from Parameters.h
		
		/* CSFS: The load/save settings were previously using the return value
		 * to communicate success or failure, and a reference to some temporary
		 * variable to actually confer the value. I have redesigned this
		 * to use an Exception instead.
		 */
		Ebp_parameters[] bps = Ebp_parameters.values();
		for(int i=0; i<bps.length; i++) {
			if(bps[i].persistent) {
				try {
					boolParamValues[i] = LoadBoolSetting(bps[i].regName());
				}
				catch(CParameterNotFoundException e) {
					SaveSetting(bps[i].regName(), boolParamValues[i]=bps[i].defaultVal);
				}
			} else boolParamValues[i] = bps[i].defaultVal;		            
		}
		
		Elp_parameters[] lps = Elp_parameters.values();
		for(int i=0; i<lps.length; i++) {
			if(lps[i].persistent) {
				try {
					longParamValues[i] = LoadLongSetting(lps[i].regName());
				}
				catch(CParameterNotFoundException e) {
					SaveSetting(lps[i].regName(), longParamValues[i]=lps[i].defaultVal);
				}
			} else longParamValues[i] = lps[i].defaultVal;      
		}
		
		Esp_parameters[] sps = Esp_parameters.values();
		for (int i=0; i<sps.length; i++) {
			if (sps[i].persistent) {
				try {
					stringParamValues[i] = LoadStringSetting(sps[i].regName());
				} catch (CParameterNotFoundException e) {
					SaveSetting(sps[i].regName(), stringParamValues[i] = sps[i].defaultVal);
				}
			} else stringParamValues[i] = sps[i].defaultVal;
		}
		
	}
	
	/**
	 * Sets the value of a given boolean parameter.
	 * <p>
	 * This will raise a ParameterNotificationEvent with our
	 * event handler.
	 * 
	 * @param iParameter Parameter to set
	 * @param bValue New value for this parameter
	 */
	public void SetBoolParameter(Ebp_parameters iParameter, boolean bValue) {
		
		if(bValue == GetBoolParameter(iParameter))
			return;
		
		// Set the value
		boolParamValues[iParameter.ordinal()] = bValue;
		
		// Initiate events for changed parameter
		m_pEventHandler.InsertEvent(new CParameterNotificationEvent(iParameter));
		
		// Write out to permanent storage
		if(iParameter.persistent)
			SaveSetting(iParameter.regName(), bValue);
	}
	
	/**
	 * Sets the value of a given long parameter.
	 * <p>
	 * This will raise a ParameterNotificationEvent with our
	 * event handler.
	 * 
	 * @param iParameter Parameter to set
	 * @param lValue New value for this parameter
	 */
	public void SetLongParameter(Elp_parameters iParameter, long lValue) {
		
		if(lValue == GetLongParameter(iParameter))
			return;
		
		// Set the value
		longParamValues[iParameter.ordinal()] = lValue;
		
		// Initiate events for changed parameter
		m_pEventHandler.InsertEvent(new CParameterNotificationEvent(iParameter));
		
		// Write out to permanent storage
		if(iParameter.persistent)
			SaveSetting(iParameter.regName(), lValue);
	}
	
	/**
	 * Sets the value of a given string parameter.
	 * <p>
	 * This will raise a ParameterNotificationEvent with our
	 * event handler.
	 * 
	 * @param iParameter Parameter to set
	 * @param sValue New value for this parameter
	 */
	public void SetStringParameter(Esp_parameters iParameter, String sValue) {
		
		if(sValue.equals(GetStringParameter(iParameter)))
			return;
		
		// Set the value
		stringParamValues[iParameter.ordinal()] = sValue;
		
		// Initiate events for changed parameter
		m_pEventHandler.InsertEvent(new CParameterNotificationEvent(iParameter));
		
		// Write out to permanent storage
		if(iParameter.persistent)
			SaveSetting(iParameter.regName(), sValue);
	}
	
	/**
	 * Gets the value of a boolean parameter
	 * 
	 * @param iParameter Parameter to query
	 * @return Value of this parameter
	 */
	public boolean GetBoolParameter(Ebp_parameters iParameter) {
		return boolParamValues[iParameter.ordinal()];
	}
	
	/**
	 * Gets the value of an integer parameter
	 * 
	 * @param iParameter Parameter to query
	 * @return Value of this parameter
	 */
	public long GetLongParameter(Elp_parameters iParameter) {
		return longParamValues[iParameter.ordinal()];
	}
	
	/**
	 * Gets the value of a String parameter
	 * 
	 * @param iParameter Parameter to query
	 * @return Value of this parameter
	 */
	public String GetStringParameter(Esp_parameters iParameter) {
		return stringParamValues[iParameter.ordinal()];
	}
	
	/* CSFS: There were some deprecated functions below here, named GetBoolOption
	 * and the obvious brethren. Since nobody seemed to be calling these anymore,
	 * I've removed them.
	 */	
	
	/* Private functions -- Settings are not saved between sessions unless these
	 functions are over-ridden.
	 --------------------------------------------------------------------------*/
	
	/**
	 * Loads a given boolean setting from the backing store.
	 * 
	 * @param Key Name of parameter to retrieve
	 * @return Value of this setting
	 * @throws CParameterNotFoundException if loading failed, eg. because the backing
	 * store did not contain information about this parameter.
	 */
	protected boolean LoadBoolSetting(String Key) throws CParameterNotFoundException {
		throw new CParameterNotFoundException(Key);
	}
	
	/**
	 * Loads a given integer setting from the backing store.
	 * 
	 * @param Key Name of parameter to retrieve
	 * @return Value of this setting
	 * @throws CParameterNotFoundException if loading failed, eg. because the backing
	 * store did not contain information about this parameter.
	 */
	protected long LoadLongSetting(String Key) throws CParameterNotFoundException {
		throw new CParameterNotFoundException(Key);
	}
	
	/**
	 * Loads a given String setting from the backing store.
	 * 
	 * @param Key Name of parameter to retrieve
	 * @return Value of this setting
	 * @throws CParameterNotFoundException if loading failed, eg. because the backing
	 * store did not contain information about this parameter.
	 */
	protected String LoadStringSetting(String Key) throws CParameterNotFoundException {
		throw new CParameterNotFoundException(Key);
	}
	
	/**
	 * Saves a given bool parameter to the backing store. In this base
	 * class, this method is a stub; it should be overridden by a
	 * subclass if persistent settings are desired.
	 * 
	 * @param Key Name of the parameter to save
	 * @param Value Value of the parameter
	 */
	protected void SaveSetting(String Key, boolean Value) {
	}
	
	/**
	 * Saves a given integer parameter to the backing store. In this base
	 * class, this method is a stub; it should be overridden by a
	 * subclass if persistent settings are desired.
	 * 
	 * @param Key Name of the parameter to save
	 * @param Value Value of the parameter
	 */
	protected void SaveSetting(String Key, long Value) {
	}
	
	/**
	 * Saves a given string parameter to the backing store. In this base
	 * class, this method is a stub; it should be overridden by a
	 * subclass if persistent settings are desired.
	 * 
	 * @param Key Name of the parameter to save
	 * @param Value Value of the parameter
	 */
	protected void SaveSetting(String Key, String Value) {
	}

	
}
