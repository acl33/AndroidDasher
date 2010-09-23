package dasher.android;

import java.util.List;
import java.util.NoSuchElementException;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class CalibPreference extends DialogPreference implements SensorEventListener, OnClickListener {
	static final String ANDROID_TILT_MAX_Y = "AndroidTiltMaxY";
	static final String ANDROID_TILT_MIN_Y = "AndroidTiltMinY";
	static final String ANDROID_TILT_MAX_X = "AndroidTiltMaxX";
	static final String ANDROID_TILT_MIN_X = "AndroidTiltMinX";
	
	private final SensorManager sm;
	private final WindowManager wm;
	private final Sensor s;

	private float minX,maxX,minY,maxY;
	
	private TextView calibX,calibY;
	private CheckBox chkInvert;
	private Button btnChange;
	
	public CalibPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		sm = (SensorManager)getContext().getSystemService(Context.SENSOR_SERVICE);
		List<Sensor> ss = sm.getSensorList(Sensor.TYPE_ACCELEROMETER);
		if (ss.isEmpty()) throw new NoSuchElementException("No sensors!");
		s=ss.get(0);
		wm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
	}
	
	@Override
	public void onBindDialogView(View v) {
		btnChange = (Button)v.findViewById(R.id.calibChange);
		chkInvert = (CheckBox)v.findViewById(R.id.invert);
		btnChange.setOnClickListener(this);
		calibX = (TextView)v.findViewById(R.id.calibX);
		calibY = (TextView)v.findViewById(R.id.calibY);
		loadParams();
	}
	
	@Override public void onClick(DialogInterface dialog, int which) {
		if (which==DialogInterface.BUTTON1) {
			//ok clicked. Store current vals!
			SharedPreferences.Editor e = getEditor();
			e.putFloat(ANDROID_TILT_MIN_X, minX);
			e.putFloat(ANDROID_TILT_MAX_X, maxX);
			e.putFloat(ANDROID_TILT_MIN_Y, chkInvert.isChecked() ? maxY : minY);
			e.putFloat(ANDROID_TILT_MAX_Y, chkInvert.isChecked() ? minY : maxY);
			e.commit();
			IMDasherInterface.INSTANCE.SetTiltAxes();
		}
		if (!btnChange.isEnabled()) {
			//we started listening to calib preferences...
			sm.unregisterListener(CalibPreference.this);
			if (which == DialogInterface.BUTTON2) {
				btnChange.setEnabled(true);
				loadParams();
				return; //don't dismiss
			}
		}
		super.onClick(dialog, which);
	}
					
	/** Update/reset labels to reflect currently-stored preferences. */
	private void loadParams() {
		SharedPreferences sp = getSharedPreferences();
		minX = sp.getFloat(ANDROID_TILT_MIN_X, -1.0f);
		maxX = sp.getFloat(ANDROID_TILT_MAX_X, 1.0f);
		minY = sp.getFloat(ANDROID_TILT_MIN_Y, 1.0f);
		maxY = sp.getFloat(ANDROID_TILT_MAX_Y, 9.0f);
		boolean invert;
		if (minY > maxY) {
			float temp = minY;
			minY = maxY;
			maxY = temp;
			invert = true;
		} else invert = false;
		calibX.setText( minX + " - " + maxX);
		calibY.setText(minY + " - " + maxY);
		chkInvert.setChecked(invert);
	}

	public void onSensorChanged(SensorEvent se) {
		float[] vals = se.values;
		if (wm.getDefaultDisplay().getOrientation()==1) {
			minX = Math.min(minX, 1.0f-vals[1]); //??? No - the 1.0-val should be applied only after
			maxX = Math.max(maxX, 1.0f-vals[1]); //??? vals[1] has been multiplied by x_mul and x_off added...
			minY = Math.min(minY, vals[0]);
			maxY = Math.max(maxY, vals[0]);
		} else {
			minX = Math.min(minX,vals[0]);
			maxX = Math.max(maxX,vals[0]);
			minY = Math.min(minY, vals[1]);
			maxY = Math.max(maxY, vals[1]);
		}
		calibX.setText(minX+" - "+maxX);
		calibY.setText(minY+" - "+maxY);
		//no, don't update SharedPreferences
	}
	
	@Override public void onDismiss(DialogInterface v) {
		calibX = calibY = null;
		btnChange=null;
		chkInvert=null;
	}

	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub		
	}

	public void onClick(View v) {
		if (v!=btnChange) throw new AssertionError();
		btnChange.setEnabled(false);
		minX = minY = Float.MAX_VALUE;
		maxX = maxY = Float.MIN_VALUE;
		sm.registerListener(CalibPreference.this, s, SensorManager.SENSOR_DELAY_UI);
	}
}
