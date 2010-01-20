package dasher.android;

import dasher.CEventHandler;
import dasher.CSettingsStore;
import dasher.CStylusFilter;
import android.app.Activity;
import android.content.SharedPreferences;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.widget.TextView;

public class DasherActivity extends Activity {
	private ADasherInterface intf;
	private DasherCanvas surf;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        intf = new ADasherInterface() {
        	@Override
        	public void Redraw(boolean bChanged) {
        		surf.requestRender();
        	}

			@Override
			protected CSettingsStore createSettingsStore(CEventHandler handler) {
				return new AndroidSettings(handler, getSharedPreferences("DasherPrefs", MODE_PRIVATE));
			}
			
			@Override
			protected void CreateModules() {
				RegisterModule(new CStylusFilter(this, m_SettingsStore));
				surf.CreateModules();
			}
        };
        surf = new DasherCanvas(this,intf);
        //TextView text = new TextView(this);
        //text.setText("Hello!");
        //setContentView(text);
        intf.Realize();
        setContentView(surf);
        surf.startAnimating();
    }
}
