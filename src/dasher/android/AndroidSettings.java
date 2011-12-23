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

import java.lang.ref.WeakReference;

import android.content.SharedPreferences;

import dasher.CParameterNotFoundException;
import dasher.CSettingsStore;
import dasher.EParameters;
import dasher.Ebp_parameters;
import dasher.Elp_parameters;
import dasher.Esp_parameters;

public class AndroidSettings extends CSettingsStore implements SharedPreferences.OnSharedPreferenceChangeListener {

	private final SharedPreferences pref;
	private final SharedPreferences.Editor edit;
	//private boolean modified; //whether anything's been written to the Editor
	/** We allow the stored settings to be temporarily overriden
	 * by providing (at most one at a time) instance of this class
	 * - this allows e.g. properties specific to the document /
	 * input field being edited, to override the user's general preferences.
	 * TODO, this is an Android-specific thing at present; consider
	 * moving into the core of dasher?
	 */
	public static interface SettingsOverride {
		/** Override a boolean parameter
		 * @return null to use the user's stored preference; Boolean.TRUE or FALSE
		 * to ignore user preference and instead use that value.
		 */
		public Boolean overrideBoolParam(Ebp_parameters bp);
		
		/** Override a long parameter
		 * @return null to use the user's stored preference; an instance of Long
		 * to ignore user preference and instead use that value.
		 */
		public Long overrideLongParam(Elp_parameters lp);
		
		/** Override a String parameter.
		 * @return null to use the user's stored preference; any
		 * other value to use instead of the user preference.
		 */
		public String overrideStringParam(Esp_parameters sp);
	};
	
	/** Instance of SettingsOverride in use, or null to <em>just</em>
	 * use the stored preferences.
	 */
	private SettingsOverride over;
	
	public AndroidSettings(SharedPreferences pref) {
		this.pref=pref;
		this.edit=pref.edit();
		Thread t = new Thread(new SaveTask(this));
		t.setPriority(Thread.MIN_PRIORITY);
		t.start();
		pref.registerOnSharedPreferenceChangeListener(this);
		LoadPersistent();
	}
	
	private static class SaveTask implements Runnable {
		private final WeakReference<AndroidSettings> sets;
		private final SharedPreferences.Editor edit;
		SaveTask(AndroidSettings sets) {
			this.sets = new WeakReference<AndroidSettings>(sets);
			this.edit = sets.edit;
		}
		public void run() {
			for (;;) {
				try {Thread.sleep(1000);}
				catch (InterruptedException e) {}
				AndroidSettings s = this.sets.get();
				synchronized(edit) {
				//	if (sets!=null && !sets.modified)
				//		continue;
					edit.commit();
				}
				if (s==null) break;//if GC'd, won't be making any more changes!
			}
		}
	}
	
	/** Override stored settings with e.g. document-specific ones.
	 * Broadcasts an event to all registered listeners, for any parameter
	 * changing as a result (e.g. overridden when was not before, no
	 * longer overridden when it was before, overridden to a different value). 
	 * @param over SettingsOverride to query for overridden settings,
	 * or null to just use stored settings; replaces any previous such in use.
	 */
	public void setOverride(SettingsOverride over) {
		SettingsOverride oldOver = this.over;
		this.over=over;
		for (Ebp_parameters bp : Ebp_parameters.values())
			if ((oldOver==null ? null : oldOver.overrideBoolParam(bp))
					!= (over==null ? null : over.overrideBoolParam(bp)))
				InsertEvent(bp);
		for (Elp_parameters lp : Elp_parameters.values())
			if ((oldOver==null ? null : oldOver.overrideLongParam(lp))
					!= (over==null ? null : over.overrideLongParam(lp)))
				InsertEvent(lp);
		for (Esp_parameters sp : Esp_parameters.values()) {
			final String old = oldOver==null ? null : oldOver.overrideStringParam(sp),
					n = over==null ? null : over.overrideStringParam(sp);
			if (old==null ? n!=null : (n==null || !old.equals(n)))
				InsertEvent(sp);
		}
	}
	
	@Override public boolean GetBoolParameter(Ebp_parameters bp) {
		if (over!=null) {
			Boolean b = over.overrideBoolParam(bp);
			if (b!=null) return b.booleanValue();
		}
		return super.GetBoolParameter(bp);
	}
	
	@Override public long GetLongParameter(Elp_parameters lp) {
		if (over!=null) {
			Long l = over.overrideLongParam(lp);
			if (l!=null) return l.longValue();
		}
		return super.GetLongParameter(lp);
	}
	
	@Override public String GetStringParameter(Esp_parameters sp) {
		if (over!=null) {
			String s = over.overrideStringParam(sp);
			if (s!=null) return s;
		}
		return super.GetStringParameter(sp);
	}
	
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		
		// TODO Auto-generated method stub
		EParameters param = EParameters.BY_NAME.get(key);
		assert param.regName().equals(key);
		//changed in the backing store, update in memory.
		//note this'll end up writing it back to the backing store _again_,
		// which'll call this again - but the second call to SetXXXXParameter
		// will return without doing anything as the in-memory value will already be correct.
		try {
			if (param instanceof Ebp_parameters)
				SetBoolParameter((Ebp_parameters)param,LoadBoolSetting(key));
			else if (param instanceof Elp_parameters)
				SetLongParameter((Elp_parameters)param,LoadLongSetting(key));
			else if (param instanceof Esp_parameters)
				SetStringParameter((Esp_parameters)param,LoadStringSetting(key));
			//silently return...maybe a SharedPreferences not representing a Dasher parameter??
		} catch (CParameterNotFoundException e) {
			//hmmm. if the key has just been deleted?
			throw new RuntimeException(e);
		}
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
		synchronized (edit) {edit.putBoolean(key, value);}
		
	}


	protected void SaveSetting(String key, long value) {
		synchronized (edit) {edit.putLong(key, value);}
	}


	protected void SaveSetting(String key, String value) {
		synchronized (edit) {edit.putString(key, value);}
	}
	
}