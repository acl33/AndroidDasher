package dasher.android;

import java.util.ArrayList;
import java.util.Collection;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.CheckBox;
import dasher.Esp_parameters;

public class DasherActivity extends PreferenceActivity {
  public static final String EYETRACKER_NAME = "Tap-to-start w/ remapping";
  
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.d("DasherIME","Activity onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.layout.prefs);
        
        IMDasherInterface.INSTANCE.Realize(this);
        
        final PreferenceScreen tiltScr = (PreferenceScreen)getPreferenceScreen().findPreference("AndroidTilt");
        tiltScr.setWidgetLayoutResource(R.layout.checkbox_tilt);
        final PreferenceScreen touchScr = (PreferenceScreen)getPreferenceScreen().findPreference("AndroidTouch");
        touchScr.setWidgetLayoutResource(R.layout.checkbox_touch);
        Preference.OnPreferenceClickListener clk = new Preference.OnPreferenceClickListener() {
        	public boolean onPreferenceClick(Preference p) {
        		if (p==tiltScr) {
        			tiltCheckBox().setChecked(true);
        			touchCheckBox().setChecked(false);
        			IMDasherInterface.INSTANCE.SetStringParameter(Esp_parameters.SP_INPUT_DEVICE,"Tilt Input");
        			IMDasherInterface.INSTANCE.SetStringParameter(Esp_parameters.SP_INPUT_FILTER,EYETRACKER_NAME);
        		} else {
        			tiltCheckBox().setChecked(false);
        			touchCheckBox().setChecked(true);
        			IMDasherInterface.INSTANCE.SetStringParameter(Esp_parameters.SP_INPUT_DEVICE,"Touch Input");
        			IMDasherInterface.INSTANCE.SetStringParameter(Esp_parameters.SP_INPUT_FILTER,"Stylus Control");
        		}
        		return false; //Not handled - want ordinary behaviour (popup) to happen next!
        	}
        };
        tiltScr.setOnPreferenceClickListener(clk);
        touchScr.setOnPreferenceClickListener(clk);
        
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
    
    /**Cache components retrieved by findViewById*/
    private CheckBox m_tilt,m_touch;
    /** Get the tilt/touch checkbox - done lazily, as I'm not sure when Preferences inflate their widget layout resources...*/
    private CheckBox tiltCheckBox() {
    	if (m_tilt==null)
    		m_tilt = (CheckBox)DasherActivity.this.findViewById(R.id.checkbox_tilt);
    	return m_tilt;
    }
    private CheckBox touchCheckBox() {
    	if (m_touch==null)
    		m_touch = (CheckBox)DasherActivity.this.findViewById(R.id.checkbox_touch);
    	return m_touch;
    }
    
}
