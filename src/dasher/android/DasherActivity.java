package dasher.android;

import android.app.Activity;
import android.content.SharedPreferences;
import android.opengl.GLSurfaceView;
import android.os.Bundle;

public class DasherActivity extends Activity implements AndroidDasherInterface.Host {
	private AndroidDasherInterface intf;
	private GLSurfaceView surf;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        intf = new AndroidDasherInterface(this);
        setContentView(surf = new DasherWidget(this,intf));
    }
	public void Redraw(boolean bChanged) {
		surf.requestRender();
	}
	public SharedPreferences getSharedPreferences() {
		return getSharedPreferences("DasherPrefs",MODE_PRIVATE);
		//could be MODE_WORLD_WRITABLE to make easier to change?
	}
}