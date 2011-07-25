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
 * when instantiated. 
 * <li>Notify all registered observers whenever a parameter value changes
 * </ul>
 * <p>
 * The CSettingsStore class itself provides the last function but
 * neither of the optional bits - this means it should be perfectly
 * functional for testing purposes or environments which cannot
 * save settings.
 * 
 */
public class CSettingsStore extends Observable<EParameters> {
	private final boolean[] boolParamValues = new boolean[Ebp_parameters.values().length];
	private final long[] longParamValues = new long[Elp_parameters.values().length];
	private final String[] stringParamValues = new String[Esp_parameters.values().length];
	
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
		InsertEvent(iParameter);
		
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
		InsertEvent(iParameter);
		
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
		
		//When SP_ALPHABET_ID changes, store a history of previous used alphabets
		// in SP_ALPHABET_1, SP_ALPHABET_2 and so on.
		// TODO - make this a more general 'pre-set' event somehow.

		if(iParameter == Esp_parameters.SP_ALPHABET_ID) { 
			// Cycle the alphabet history
			if(!GetStringParameter(Esp_parameters.SP_ALPHABET_1).equals(sValue)) {
				if(!GetStringParameter(Esp_parameters.SP_ALPHABET_2).equals(sValue)) {
					if(!GetStringParameter(Esp_parameters.SP_ALPHABET_3).equals(sValue))
						SetStringParameter(Esp_parameters.SP_ALPHABET_4, GetStringParameter(Esp_parameters.SP_ALPHABET_3));
					SetStringParameter(Esp_parameters.SP_ALPHABET_3, GetStringParameter(Esp_parameters.SP_ALPHABET_2));
				}
				SetStringParameter(Esp_parameters.SP_ALPHABET_2, GetStringParameter(Esp_parameters.SP_ALPHABET_1));
			}
			SetStringParameter(Esp_parameters.SP_ALPHABET_1, GetStringParameter(Esp_parameters.SP_ALPHABET_ID));
		}	

		// Set the value
		stringParamValues[iParameter.ordinal()] = sValue;
		
		// Initiate events for changed parameter
		InsertEvent(iParameter);
		
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
