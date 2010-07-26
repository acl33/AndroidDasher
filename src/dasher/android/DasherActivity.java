package dasher.android;

import java.util.ArrayList;
import java.util.Collection;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.Log;
import dasher.Esp_parameters;

public class DasherActivity extends PreferenceActivity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.d("DasherIME","Activity onCreate");
        super.onCreate(savedInstanceState);
        
        //Creating the DasherInterface first will set appropriate defaults
        // the first time the app is run, for the preferences GUI to load.
        //TODO...but can we avoid loading the entire training text?!
        IMDasherInterface.INSTANCE.Realize(this);
        
        addPreferencesFromResource(R.layout.prefs);
        
        Preference.OnPreferenceClickListener lstnr = new Preference.OnPreferenceClickListener() {
        	public boolean onPreferenceClick(Preference pref) {
        		Log.d("DasherIME","OnPreferenceClick screen "+pref);
        		IMCheckBox.set(pref.getKey());
        		return false; //allow normal click action to occur too
        	}
        };
        ((PreferenceScreen)getPreferenceScreen().findPreference("AndroidTilt"))
        	.setOnPreferenceClickListener(lstnr);
        
        ((PreferenceScreen)getPreferenceScreen().findPreference("AndroidTouch"))
        	.setOnPreferenceClickListener(lstnr);
        
        ((PreferenceScreen)getPreferenceScreen().findPreference("AndroidBoxes"))
    		.setOnPreferenceClickListener(lstnr);
        
        ((PreferenceScreen)getPreferenceScreen().findPreference("AndroidCompass"))
			.setOnPreferenceClickListener(lstnr);
        
        ((CheckBoxPreference)getPreferenceScreen().findPreference("AndroidScan"))
			.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				((DualIMCheckBox)findViewById(R.id.checkbox_boxes)).setInputsForChildChecked(((Boolean)newValue).booleanValue());
				return true;
			}
		});
    
        addPermittedValues(Esp_parameters.SP_ALPHABET_ID);
    }
    
    private void addPermittedValues(Esp_parameters param) {
    	Collection<String> values = new ArrayList<String>();
       	ListPreference lp = (ListPreference)getPreferenceScreen().findPreference(param.regName());
        IMDasherInterface.INSTANCE.GetPermittedValues(param,values);
        CharSequence[] vals = new CharSequence[values.size()];
        int i=0;
        for (String s : values) {
        	vals[i++] = s;
        }
        lp.setEntries(vals);
        lp.setEntryValues(vals);
    }
	
    @Override
    public void onDestroy() {
    	Log.d("DasherIME","Activity onDestroy");
    	super.onDestroy();
    	//intf.StartShutdown();
    }
    
}
