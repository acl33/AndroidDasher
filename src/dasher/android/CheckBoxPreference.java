package dasher.android;

//import java.lang.ref.WeakReference;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.AttributeSet;

/** Extend Android's default CheckBoxPreference to listen to changes
 * (to the value stored in SharedPreferences) that might come from
 * other sources. Hopefully this'll let us have multiple CheckBoxPreferences,
 * with the same key, at different points in the Preference screen hierarchy,
 * and keep them in sync...
 * @author Alan Lawrence <acl33@inf.phy.cam.ac.uk>
 */
public class CheckBoxPreference extends android.preference.CheckBoxPreference implements SharedPreferences.OnSharedPreferenceChangeListener {

	public CheckBoxPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	    //Handily, SharedPreferences already keeps its listeners in a WeakHashMap,
	    // so registering as such does not prevent this from being GC'd; and when this
	    // is GC'd, the WeakHashMap'll automatically deregister us.
		PreferenceManager.getDefaultSharedPreferences(context)
			.registerOnSharedPreferenceChangeListener(this);
	}
	
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(getKey())) {
			onSetInitialValue(true, null); //true = "get from sharedpreferences"
		}
	  }

}
