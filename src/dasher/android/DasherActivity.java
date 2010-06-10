package dasher.android;

import java.util.ArrayList;
import java.util.Collection;

import dasher.Esp_parameters;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Paint.Style;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.View;

public class DasherActivity extends PreferenceActivity {
	private ADasherInterface intf;
	private DasherCanvas surf;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.d("DasherIME","Activity onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.layout.prefs);
        
        IMDasherInterface.INSTANCE.Realize(this);
        
        addPermittedValues(Esp_parameters.SP_INPUT_DEVICE);
        addPermittedValues(Esp_parameters.SP_INPUT_FILTER);
        
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
