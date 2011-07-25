package dasher.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.AttributeSet;

import dasher.Esp_parameters;

public class SingleIMCheckBox extends IMCheckBox {
	
	private final String inputDevice;
	private final String inputFilter;

	public SingleIMCheckBox(Context ctx, AttributeSet attrs) {
		super(ctx,attrs);
		inputDevice = attrs.getAttributeValue(null,"inputDevice");
		inputFilter = attrs.getAttributeValue(null,"inputFilter");
	}
	
	@Override protected void hasBecomeChecked() {
		super.hasBecomeChecked();
		//writing to the persistent settings store will trigger an 
		// OnSharedPreferencesChanged listener in the main/IME service
		// (if its active) which will update the in-memory parameters
		// there. (Using an AndroidSettings might be more modular, but
		// would entail reading all settings into memory every time!)
		SharedPreferences.Editor edit = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
		edit.putString(Esp_parameters.SP_INPUT_DEVICE.regName(), inputDevice);
		edit.putString(Esp_parameters.SP_INPUT_FILTER.regName(), inputFilter);
		edit.commit();
	}

}
