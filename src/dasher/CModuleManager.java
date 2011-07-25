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

import java.util.Map;
import java.util.HashMap;
import java.util.Collection;

/**
 * Helper class for the InterfaceBase which enumerates the modules
 * present in supplied module factories and returns said modules
 * by ID or by name on request.
 * <p>
 * A list of all available modules can also be supplied.
 */
public class CModuleManager {
	/**
	 * Map from modules' Strings to the modules themselves
	 */
	protected final Map<String, CDasherModule> m_mapModules = new HashMap<String, CDasherModule>();
	
	/**
	 * Store the module in our internal map so it can be retrieved
	 * using GetModuleByName <!--or GetModule-->
	 * 
	 * @param mod the new module; must have a unique name
	 */
	public void RegisterModule(CDasherModule mod) {
		String s = mod.getName();
		if (m_mapModules.containsKey(s))
			throw new IllegalArgumentException("Module "+s+" already exists!");
		m_mapModules.put(s, mod);
	}
	
	/**
	 * Gets a module with the given name and supertype (typically {@link CDasherInput} or {@link CInputFilter})
	 * 
	 * @param iID ID of the required module
	 * @return Matching Module, or null if none was found.
	 */
	public <T extends CDasherModule> T GetModuleByName(Class<T> clazz, String strName) {
		CDasherModule m = m_mapModules.get(strName);
		return (clazz.isInstance(m)) ? clazz.cast(m) : null;
	}
	
	/**
	 * Retrieves a list of all modules of a given type.
	 *  
	 * @param clazz Type of modules to enumerate - typically {@link CDasherInput} or {@link CInputFilter}
	 * @param vList Collection to be filled with the names of available
	 * modules
	 */
	public <T> void ListModules(Class<T> clazz, Collection<? super T> vList) {
		for(CDasherModule m : m_mapModules.values())
			if(clazz.isInstance(m))
				vList.add(clazz.cast(m));
	}

}
