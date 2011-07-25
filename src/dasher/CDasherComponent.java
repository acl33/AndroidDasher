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
 * DasherComponent is the base class of most functional units of
 * Dasher, and serves to provide a number of common functions and
 * services to its children. These include:
 * <p><ul><li>The ability to register oneself as an event listener
 * and so receive notification when other components dispatch events.
 * <li>The ability to dispatch events, of which all other Components will
 * be notified.
 * <li>The ability to get and set global settings, using some child of CSettingsStore.
 * </ul>
 * <p>
 * Generally speaking, a single EventHandler and SettingsStore should be
 * created during Dasher's initialisation, and a reference to both
 * passed to each created DasherComponent. However, there is no
 * reason why in future there could not be multiple subgroups
 * of components which are not connected, and by virtue of
 * having seperate EventHandlers and SettingsStores would not
 * hear each other's events, or be effected by each other's parameter changes.
 * <p>
 * It is, however, not recommended that any two components should
 * share a SettingsStore without also sharing an EventHandler,
 * as the SettingsStore raises an Event to notify whenever a parameter
 * is changed, a behaviour which components may depend upon.
 */
public class CDasherComponent implements Observer<EParameters> {

	/**
	 * This Component's SettingsStore
	 */
	protected final CSettingsStore m_SettingsStore;
	
	/** Creates a new CDasherComponent which is the root of a tree of
	 * CDasherComponents listening to a particular settings store.
	 * Although we do not prevent construction of multiple
	 * CDasherComponents from a single CSettingsStore in this way, such
	 * use is discouraged, and generally this constructor should only be
	 * used by {@link CDasherInterfaceBase}; components should generally
	 * use the {@link #CDasherComponent(CDasherComponent)} constructor.
	 * (Note, this constructor does <em>not</em> register the component
	 * as a listener.)
	 */
	protected CDasherComponent(CSettingsStore sets) {
		m_SettingsStore = sets;
	}
	
	/**
	 * <p>Main constructor. Creates a new component from the specified/creator
	 * component, i.e. part of the same tree under the same SettingsStore.
	 * This exists for future-proofing: subclasses are abstracted from the
	 * details of precisely what fields/members need to be copied between
	 * components.</p>
	 * <p>Also registers this component as listening to the specified SettingsStore
	 * for parameter changes, <em>if</em> the component <em>overrides</em> the
	 * {@link #HandleEvent(EParameters)} method.
	 * 
	 * @param other Parent CDasherComponent in the tree, i.e. whose SettingsStore
	 * we will use.
	 */
	public CDasherComponent(CDasherComponent other) {
		m_SettingsStore = other.m_SettingsStore;
	  try {
		  if (getClass().getMethod("HandleEvent", EParameters.class)!=CDasherComponent.class.getMethod("HandleEvent",EParameters.class))
			  m_SettingsStore.RegisterListener(this);
	  } catch (NoSuchMethodException e) {
		  //should never happen at runtime - if it does, code needs statically updating
		  throw new AssertionError(e);
	  }
	}
	
	/**
	 * Called when a settings value changes. The default asserts false:
	 * the DasherComponent will have registered itself for callbacks in
	 * its constructor if and only if it overrides this method.
	 * @param eParamChange Parameter whose value has just changed.
	 */
	public void HandleEvent(EParameters eParamChange) {assert false;}
	
	/**
	 * Retreives the value of a given global boolean parameter.
	 * <p>This request is actioned by calling the same method on m_SettingsStore
	 * 
	 * @param iParameter Parameter to retrieve
	 * @return Boolean value of this parameter
	 */
	public boolean GetBoolParameter(Ebp_parameters iParameter) {
		return m_SettingsStore.GetBoolParameter(iParameter);
	}
	
	/**
	 * Retreives the value of a given global long parameter.
	 * <p>This request is actioned by calling the same method on m_SettingsStore
	 * 
	 * @param iParameter Parameter to retrieve
	 * @return Long value of this parameter
	 */
	public long GetLongParameter(Elp_parameters iParameter) {
		  return m_SettingsStore.GetLongParameter(iParameter);
	}
	
	/**
	 * Retreives the value of a given global string parameter.
	 * <p>This request is actioned by calling the same method on m_SettingsStore
	 * 
	 * @param iParameter Parameter to retrieve
	 * @return String value of this parameter
	 */
	public String GetStringParameter(Esp_parameters iParameter) {
		  return m_SettingsStore.GetStringParameter(iParameter);
	}
	
	/**
	 * Sets the value of a given boolean parameter by calling the
	 * SettingsStore's SetBoolParameter method.
	 * 
	 * @param iParameter Parameter to set
	 * @param bValue New value for this parameter
	 */
	public void SetBoolParameter(Ebp_parameters iParameter, boolean bValue) {
		  m_SettingsStore.SetBoolParameter(iParameter, bValue);
	}
	
	/**
	 * Sets the value of a given long parameter by calling the
	 * SettingsStore's SetBoolParameter method.
	 * 
	 * @param iParameter Parameter to set
	 * @param lValue New value for this parameter
	 */
	public void SetLongParameter(Elp_parameters iParameter, long lValue) {
		  m_SettingsStore.SetLongParameter(iParameter, lValue);
	}
	
	/**
	 * Sets the value of a given string parameter by calling the
	 * SettingsStore's SetBoolParameter method.
	 * 
	 * @param iParameter Parameter to set
	 * @param sValue New value for this parameter
	 */
	public void SetStringParameter(Esp_parameters iParameter, String sValue) {
		  m_SettingsStore.SetStringParameter(iParameter, sValue);
	}

}
