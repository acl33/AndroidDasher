package dasher.android;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.AttributeSet;
import android.widget.CheckBox;

public abstract class IMCheckBox extends CheckBox {
	
	protected static final String androidns = "http://schemas.android.com/apk/res/android";
	public static final String SETTING = "AndroidInputMethod";
	private static final Set<WeakReference<IMCheckBox>> BOXES = new HashSet<WeakReference<IMCheckBox>>();
	private final String key;
	
	protected IMCheckBox(Context ctx, AttributeSet attrs) {
		super(ctx,attrs);
		key = attrs.getAttributeValue(androidns, "key");
		BOXES.add(new WeakReference<IMCheckBox>(this));
		((PreferenceScreen)ps.findPreference(key)).setOnPreferenceClickListener(lstnr);
	}
	
	@Override public void onFinishInflate() {
		super.onFinishInflate();
		setChecked(PreferenceManager.getDefaultSharedPreferences(getContext()).getString(SETTING,"_"+key).equals(key));
	}

	@Override public final void setChecked(boolean b) {
		if (isChecked()==b) return;
		super.setChecked(b);
		if (b) hasBecomeChecked();
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

	public static void setPrefScreen(PreferenceScreen _ps) {ps=_ps;}
	protected static PreferenceScreen ps;
	
	protected void hasBecomeChecked() {
		SharedPreferences.Editor edit = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
		edit.putString(SETTING, key);
		edit.commit();
	}
	
	public String toString() {return getClass().getName()+" w/ key "+key;}

	private static final Preference.OnPreferenceClickListener lstnr = new Preference.OnPreferenceClickListener() {
    	public boolean onPreferenceClick(Preference pref) {
    		IMCheckBox.set(pref.getKey());
    		return false; //allow normal click action to occur too
    	}
    };

}