package dasher.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.widget.CheckBox;

import java.util.*;
import java.lang.ref.WeakReference;

import dasher.Esp_parameters;

public class IMCheckBox extends CheckBox {
	
	private static final String androidns="http://schemas.android.com/apk/res/android";
	public static final String SETTING = "AndroidInputMethod";
	private static final Set<WeakReference<IMCheckBox>> BOXES = new HashSet<WeakReference<IMCheckBox>>();
	final String key;
	final String inputDevice;
	final String inputFilter;
	public IMCheckBox(Context ctx, AttributeSet attrs) {
		super(ctx,attrs);
		key = attrs.getAttributeValue(androidns, "key");
		android.util.Log.d("DasherPrefs","Created checkbox w/id "+getId()+", "+key);
		inputDevice = attrs.getAttributeValue(null,"inputDevice");
		inputFilter = attrs.getAttributeValue(null,"inputFilter");
		BOXES.add(new WeakReference<IMCheckBox>(this));
	}
	
	@Override public void onFinishInflate() {
		super.onFinishInflate();
		setChecked(PreferenceManager.getDefaultSharedPreferences(getContext()).getString(SETTING,"_"+key).equals(key));
	}

	@Override public void setChecked(boolean b) {
		if (isChecked()==b) return;
		super.setChecked(b);
		if (b) {
			IMDasherInterface.INSTANCE.SetStringParameter(Esp_parameters.SP_INPUT_DEVICE, inputDevice);
			IMDasherInterface.INSTANCE.SetStringParameter(Esp_parameters.SP_INPUT_FILTER, inputFilter);
			SharedPreferences.Editor edit = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
			edit.putString(SETTING, key);
			edit.commit();
		}
	}
	
	public static void set(String key) {
		for (Iterator<WeakReference<IMCheckBox>> it=BOXES.iterator(); it.hasNext();) {
			WeakReference<IMCheckBox> wr = it.next();
			IMCheckBox r = wr.get();
			if (r==null)
				it.remove();
			else
				r.setChecked(r.key.equals(key));
		}	
	}

}
