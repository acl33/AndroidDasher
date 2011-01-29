package dasher.android;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
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
        
        IMCheckBox.setPrefScreen(getPreferenceScreen());
    
        addPermittedValues(Esp_parameters.SP_ALPHABET_ID);
        addPermittedValues(Esp_parameters.SP_COLOUR_ID);
    }
    
    private void addPermittedValues(Esp_parameters param) {
    	List<String> values = new ArrayList<String>();
       	ListPreference lp = (ListPreference)getPreferenceScreen().findPreference(param.regName());
        IMDasherInterface.INSTANCE.GetPermittedValues(param,values);
        Collections.sort(values);
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
