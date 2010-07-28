package dasher.android;

import dasher.Esp_parameters;
import android.content.Context;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.AttributeSet;

public class DualIMCheckBox extends IMCheckBox {
	private final String inputDevOn,inputDevOff;
	private final String inputFilOn,inputFilOff;
	private final String childKey;
	
	public DualIMCheckBox(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		childKey = attrs.getAttributeValue(null, "childKey");
		inputDevOn = getAttribute(attrs,"inputDeviceOn","inputDevice");
		inputDevOff= getAttribute(attrs,"inputDeviceOff","inputDevice");
		inputFilOn= getAttribute(attrs,"inputFilterOn","inputFilter");
		inputFilOff = getAttribute(attrs,"inputFilterOff","inputFilter");
		CheckBoxPreference cb = (CheckBoxPreference)ps.findPreference(childKey);
		cb.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				DualIMCheckBox.this.setInputsForChildChecked(((Boolean)newValue).booleanValue());
				return true; //allow
			}
		});
	}
	
	private static String getAttribute(AttributeSet attrs, String fst, String snd) {
		String res = attrs.getAttributeValue(null,fst);
		if (res!=null) return res;
		return attrs.getAttributeValue(null, snd);
	}
	
	@Override protected void hasBecomeChecked() {
		super.hasBecomeChecked();
		setInputsForChildChecked(PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(childKey, false));
	}
	
	public void setInputsForChildChecked(boolean b) {
		String d,f;
		if (b) {
			d=inputDevOn; f=inputFilOn;
		} else {
			d=inputDevOff; f=inputFilOff;
		}
		IMDasherInterface.INSTANCE.SetStringParameter(Esp_parameters.SP_INPUT_DEVICE, d);
		IMDasherInterface.INSTANCE.SetStringParameter(Esp_parameters.SP_INPUT_FILTER, f);
	}

}
