package dasher.android;

import java.util.List;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import android.util.Log;
import android.view.WindowManager;
import dasher.CDasherInput;
import dasher.CDasherInterfaceBase;
import dasher.CDasherView;
import dasher.CParameterNotificationEvent;
import dasher.CSettingsStore;
import dasher.Ebp_parameters;

public class TiltInput extends CDasherInput implements SensorEventListener {
	private final SensorManager sm;
	private final WindowManager wm;
	
	/** Last (/current) values of tilt position, as floats in (0,1) */
	private float fx,fy;
	/** X & Y values from h/w tilt sensor are multiplied by these scales, then offsets added */
	private float y_mul, y_off, x_mul, x_off;
	
	public void setAxes(float minX, float maxX, float minY, float maxY) {
		y_mul = 1.0f/(maxY-minY); this.y_off = minY/(maxY-minY); //these may be inverted (min > max)
		x_mul = 1.0f/(minX-maxX); this.x_off = maxX/(minX-maxX); //these we definitely expect to be inverted!
	}
	
	private boolean bActive;
	private final Sensor s;
	
	public static TiltInput MAKE(Context androidCtx, CDasherInterfaceBase iface, CSettingsStore sets) {
		SensorManager sm=(SensorManager)androidCtx.getSystemService(Context.SENSOR_SERVICE);
		WindowManager wm = (WindowManager)androidCtx.getSystemService(Context.WINDOW_SERVICE);
		List<Sensor> ss = sm.getSensorList(Sensor.TYPE_ACCELEROMETER);
		if (ss.isEmpty()) return null;
		return new TiltInput(iface, sets, sm, ss.get(0),wm);
	}
	
	private TiltInput(CDasherInterfaceBase iface, CSettingsStore sets, SensorManager sm, Sensor s, WindowManager wm) {
		super(iface, sets, 1, "Tilt Input");
		this.sm=sm;
		this.s=s;
		this.wm=wm;
	}
	
	@Override public void Activate() {
		if (bActive) return;
		bActive=true;
		sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);
	}
	
	@Override public void Deactivate() {
		if (!bActive) return;
		bActive=false;
		sm.unregisterListener(this,s);
	}
	
	public void onAccuracyChanged(Sensor sensor, int accuracy) {}
	private int m_iLastOrient;
	public void onSensorChanged(SensorEvent event) {
		float[] vals=event.values;
		/*StringBuilder sb=new StringBuilder();
		for (int i=0; i<vals.length; i++)
			sb.append(i==0 ? "{" : ",").append(vals[i]);
		sb.append("}");
		android.util.Log.d("DasherIME","Got rotation "+sb);*/
		int orient = wm.getDefaultDisplay().getOrientation();
		if (orient != m_iLastOrient) Log.d("DasherIME", "Orientation changed to "+(m_iLastOrient=orient));
		if ((orient&1)==1) {
			fx = vals[1]*x_mul - x_off;
			if ((orient&2)==0) fx=1.0f-fx;
			fy = vals[0]*y_mul - y_off;
		} else {
			fx = vals[0]*x_mul - x_off;
			fy = vals[1]*y_mul - y_off;
		}
		fx = Math.max(0.0f, Math.min(1.0f, fx));
		fy = Math.max(0.0f, Math.min(1.0f, fy));
	}

	@Override
	public boolean GetScreenCoords(CDasherView pView,long[] Coordinates) {
		if (!bActive) return false;
		Coordinates[0] = (int)(fx * pView.Screen().GetWidth());
		Coordinates[1] = (int)(fy * pView.Screen().GetHeight());
		//android.util.Log.d("DasherIME","Tilt: got "+Coordinates[0]+", "+Coordinates[1]);
		return true;
	}

}
