package dasher.android;

import java.util.List;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import dasher.CDasherInput;
import dasher.CDasherInterfaceBase;
import dasher.CDasherView;
import dasher.CParameterNotificationEvent;
import dasher.CSettingsStore;
import dasher.Ebp_parameters;

public class TiltInput extends CDasherInput implements SensorEventListener {
	private final SensorManager sm;
	
	private final PowerManager.WakeLock wl;
	
	private float fx,fy;
	private boolean bActive;
	private final Sensor s;
	
	public static TiltInput MAKE(Context androidCtx, CDasherInterfaceBase iface, CSettingsStore sets) {
		SensorManager sm=(SensorManager)androidCtx.getSystemService(Context.SENSOR_SERVICE);
		PowerManager pm = (PowerManager)androidCtx.getSystemService(Context.POWER_SERVICE);
		
		List<Sensor> ss = sm.getSensorList(Sensor.TYPE_ACCELEROMETER);
		if (ss.isEmpty()) return null;
		return new TiltInput(iface, sets, sm, pm, ss.get(0));
	}
	
	private TiltInput(CDasherInterfaceBase iface, CSettingsStore sets, SensorManager sm, PowerManager pm, Sensor s) {
		super(iface, sets, 1, "Tilt Input");
		this.sm=sm;
		this.wl = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,"tilting");
		this.s=s;
	}
	
	@Override public void Activate() {bActive=true; update();}
	@Override public void Deactivate() {bActive=false; update();}
	
	@Override public void HandleEvent(dasher.CEvent evt) {
		if (evt instanceof CParameterNotificationEvent &&
				((CParameterNotificationEvent)evt).m_iParameter == Ebp_parameters.BP_DASHER_PAUSED)
			update();
	}
	
	private void update() {
		if (bActive && !GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED)) {
			if (wl.isHeld()) {
				return;
			}
			sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);
			wl.acquire();
		} else {
			if (!wl.isHeld()) {
				return;
			}
			sm.unregisterListener(this,s);
			wl.release();
		}
	}
	
	public void onAccuracyChanged(Sensor sensor, int accuracy) {}

	public void onSensorChanged(SensorEvent event) {
		float[] vals=event.values;
		/*StringBuilder sb=new StringBuilder();
		for (int i=0; i<vals.length; i++)
			sb.append(i==0 ? "{" : ",").append(vals[i]);
		sb.append("}");
		android.util.Log.d("DasherIME","Got rotation "+sb);*/
		fx = Math.max(0.0f, Math.min(1.0f, (vals[0]-1.0f)/-2.0f));
		fy = Math.max(0.0f, Math.min(1.0f, (vals[1]-1.0f)/8.0f));
		
	}

	@Override
	public void GetScreenCoords(CDasherView pView,long[] Coordinates) {
		Coordinates[0] = (int)(fx * pView.Screen().GetWidth());
		Coordinates[1] = (int)(fy * pView.Screen().GetHeight());
		android.util.Log.d("DasherIME","Tilt: got "+Coordinates[0]+", "+Coordinates[1]);
	}

}
