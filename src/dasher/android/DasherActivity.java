package dasher.android;

import java.util.ArrayList;
import java.util.Collection;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;
import dasher.Esp_parameters;

public class DasherActivity extends PreferenceActivity {
  public static final String EYETRACKER_NAME = "Tap-to-start w/ remapping";
	private ADasherInterface intf;
	private DasherCanvas surf;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.d("DasherIME","Activity onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.layout.prefs);
        
        IMDasherInterface.INSTANCE.Realize(this);
        
        final CheckBoxPreference tilt = (CheckBoxPreference)getPreferenceScreen().findPreference("AndroidTilt");
        final CheckBoxPreference touch =(CheckBoxPreference)getPreferenceScreen().findPreference("AndroidTouch");
        Preference.OnPreferenceChangeListener chg = new Preference.OnPreferenceChangeListener() {
        	public boolean onPreferenceChange(Preference p, Object value) {
        		if (!((Boolean)value).booleanValue()) return false; //disallow deactivation 
        		if (p==tilt) {
        			touch.setChecked(false);
        			IMDasherInterface.INSTANCE.SetStringParameter(Esp_parameters.SP_INPUT_DEVICE,"Tilt Input");
        			IMDasherInterface.INSTANCE.SetStringParameter(Esp_parameters.SP_INPUT_FILTER,EYETRACKER_NAME);
        		} else {
        			tilt.setChecked(false);
        			IMDasherInterface.INSTANCE.SetStringParameter(Esp_parameters.SP_INPUT_DEVICE,"Touch Input");
        			IMDasherInterface.INSTANCE.SetStringParameter(Esp_parameters.SP_INPUT_FILTER,"Stylus Control");
        		}
        		return true; //...and set.
        	}
        };
        tilt.setOnPreferenceChangeListener(chg);
        touch.setOnPreferenceChangeListener(chg);
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
    private static final double RR2 = 1.0/Math.sqrt(2.0);
	
    @Override
    public void onDestroy() {
    	Log.d("DasherIME","Activity onDestroy");
    	super.onDestroy();
    	//intf.StartShutdown();
    }
    
}
