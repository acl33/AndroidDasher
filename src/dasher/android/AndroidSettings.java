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

package dasher.android;

import java.security.AccessControlException;
import java.util.prefs.Preferences;

import android.content.SharedPreferences;

import dasher.CEventHandler;
import dasher.CParameterNotFoundException;
import dasher.CSettingsStore;

public class AndroidSettings extends CSettingsStore {

	private final SharedPreferences pref;
	public AndroidSettings(CEventHandler handler, SharedPreferences pref) {
		
		super(handler, false);
		this.pref=pref;
				
		LoadPersistent();
	}
	
	
	protected boolean LoadBoolSetting(String key) throws CParameterNotFoundException {
		
		/* ACL: This annoying hack is to work around the fact that any
		 * failure to return a value is responded to by returning the default!
		 */
		if (pref.getBoolean(key, false)) return true;
		//setting either absent, or actually false...
		if (!pref.getBoolean(key, true)) return false;
		//no, not specified.
		throw new CParameterNotFoundException(key);
	}


	protected long LoadLongSetting(String key) throws CParameterNotFoundException {
		long retVal = pref.getLong(key, -999);
		if (retVal == -999 && pref.getLong(key, -998)==-998)
			throw new CParameterNotFoundException(key);
		return retVal;
	}


	protected String LoadStringSetting(String key) throws CParameterNotFoundException {
		String retVal = pref.getString(key, null);
		if (retVal!=null) return retVal;
		throw new CParameterNotFoundException(key);
	}

	protected void SaveSetting(String key, boolean value) {
		SharedPreferences.Editor edit = pref.edit();
		edit.putBoolean(key, value);
		edit.commit();
	}


	protected void SaveSetting(String key, long value) {
		SharedPreferences.Editor edit = pref.edit();
		edit.putLong(key, value);
		edit.commit();
	}


	protected void SaveSetting(String key, String value) {
		SharedPreferences.Editor edit = pref.edit();
		edit.putString(key, value);
		edit.commit();
	}
		
}