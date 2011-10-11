package dasher.android;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.ListIterator;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.util.Log;
import dasher.CDasherInterfaceBase;
import dasher.Esp_parameters;

public class DasherActivity extends PreferenceActivity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.d("DasherIME","Activity onCreate");
        super.onCreate(savedInstanceState);
      
        //We need a DasherInterface to parse alphabet/colour files and find the
        //available schemes (in future perhaps also input filters, although
        // atm the available options there are hardcoded into prefs.xml).
        //However our Interface doesn't need to implement display, editing, etc.
        // functions, so we stub those methods. (Also it will never load a
        // training text.)
        CDasherInterfaceBase intf = new ADasherInterface(this,false);
        
        addPreferencesFromResource(R.layout.prefs);
        
        IMCheckBox.setPrefScreen(getPreferenceScreen());
    
        addPermittedValues(intf, Esp_parameters.SP_ALPHABET_ID);
        addPermittedValues(intf, Esp_parameters.SP_COLOUR_ID);
    }
    
    private void addPermittedValues(CDasherInterfaceBase intf, Esp_parameters param) {
    	List<String> values = new ArrayList<String>();
       	ListPreference lp = (ListPreference)getPreferenceScreen().findPreference(param.regName());
        intf.GetPermittedValues(param,values);
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
