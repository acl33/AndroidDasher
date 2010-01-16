package dasher.android;

import dasher.CEventHandler;
import dasher.CSettingsStore;
import android.app.Activity;
import android.content.SharedPreferences;
import android.opengl.GLSurfaceView;
import android.os.Bundle;

public class DasherActivity extends Activity {
	private AndroidDasherInterface intf;
	private GLSurfaceView surf;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        intf = new AndroidDasherInterface() {
        	@Override
        	public void Redraw(boolean bChanged) {
        		surf.requestRender();
        	}

			@Override
			protected CSettingsStore createSettingsStore(CEventHandler handler) {
				return new AndroidSettings(handler, getSharedPreferences("DasherPrefs", MODE_PRIVATE));
			}
        };
        setContentView(surf = new DasherWidget(this,intf));
        intf.Realize();
    }
}