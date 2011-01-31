package dasher.android;

import android.view.KeyEvent;
import dasher.CButtonMode;
import dasher.CCompassMode;
import dasher.CDasherInterfaceBase;
import dasher.CMenuMode;
import dasher.CSettingsStore;
import dasher.OneButtonDynamicFilter;
import dasher.TwoButtonDynamicFilter;

interface AndroidKeyMap {
	/**
	 * Converts an Android key code (<tt>KEYCODE_*</tt> in class android.KeyEvent)
	 * into a Dasher key id (int) used by this input filter
	 * @param keyCode Android keycode as passed to e.g. {@link DasherInputMethod#onKeyUp}/onKeyDown
	 * @return Dasher key id to pass to {@link ADasherInterface#KeyDown(long, int)}, or
	 * return -1 to ignore the KeyEvent. 
	 */
	public int ConvertAndroidKeycode(int keyCode);
}

/*interface TeklaSwitchListener {
	public static final int TEKLA_SWITCH_RIGHT=80;
	public static final int TEKLA_SWITCH_LEFT=40;
	public static final int TEKLA_SWITCH_DOWN=20;
	public static final int TEKLA_SWITCH_UP=10;
	/** Called to handle presses of external switches connected via tekla (bluetooth) shield.
	 * @param Time timestamp, as per System.currentTimeMillis()
	 * @param iId Tekla switch event code (as per <a href="http://wiki.scyp.atrc.utoronto.ca/w/Switch_Event_Provider#Switch_Event_Flow">Tekla Switch Event Flow</a> wiki page)
	 * @param pView current view rendering to the screen
	 * @param pInput current input device for obtaining coordinates, if appropriate
	 * @param Model current dasher model, i.e. state of nodes
	 */
	/*public void TeklaSwitchEvent(long Time, int iId, CDasherView pView, CDasherInput pInput, CDasherModel model);
}*/

class Android2BDynamic extends TwoButtonDynamicFilter implements AndroidKeyMap {

	public Android2BDynamic(CDasherInterfaceBase iface,
			CSettingsStore SettingsStore) {
		super(iface, SettingsStore);
	}

	public int ConvertAndroidKeycode(int iId) {
		if (iId==KeyEvent.KEYCODE_VOLUME_UP) return 0;
		if (iId==KeyEvent.KEYCODE_VOLUME_DOWN) return 1;
		return -1;
	}
}

class AndroidCompass extends CCompassMode implements AndroidKeyMap {

	public AndroidCompass(CDasherInterfaceBase iface, CSettingsStore sets) {
		super(iface, sets);
	}

	public int ConvertAndroidKeycode(int keyCode) {
		//Trackball movement
		if (keyCode>=19 && keyCode<=22) {
			//yields 2,4,1,3
			return ((keyCode-18)*2) % 5;
		}
		return -1;
	}
	
}

class Android1BDynamic extends OneButtonDynamicFilter implements AndroidKeyMap {
	
	public Android1BDynamic(CDasherInterfaceBase iface, CSettingsStore sets) {
		super(iface,sets);
	}
	
	public int ConvertAndroidKeycode(int keyCode) {
		if (keyCode==KeyEvent.KEYCODE_MENU || keyCode==KeyEvent.KEYCODE_BACK || (keyCode>=19 && keyCode<=22))
			return -1; //don't intercept menu, back, or trackball movement (cursor) - too useful!
		return 1; //but anything else, treat the same!
	}
	
}

class AndroidMenuMode extends CMenuMode implements AndroidKeyMap {

	public AndroidMenuMode(CDasherInterfaceBase iface, CSettingsStore sets, int iID, String szName) {
		super(iface, sets, iID, szName);
	}

	/** TODO need some preferences here for user to specify what keys to use...*/
	public int ConvertAndroidKeycode(int keyCode) {
		switch (keyCode) {
		//case KeyEvent.KEYCODE_DPAD_DOWN:
		case KeyEvent.KEYCODE_VOLUME_DOWN:
		case KeyEvent.KEYCODE_SPACE:
			return 1; //scan
		//case KeyEvent.KEYCODE_DPAD_RIGHT:
		case KeyEvent.KEYCODE_VOLUME_UP:
		case KeyEvent.KEYCODE_ENTER:
			return 2; //select
		}
		return -1;
	}
	
}

class AndroidDirectMode extends CButtonMode implements AndroidKeyMap {

	public AndroidDirectMode(CDasherInterfaceBase iface, CSettingsStore sets, int iID, String szName) {
		super(iface, sets, iID, szName);
	}

	public int ConvertAndroidKeycode(int keyCode) {
		if (keyCode==KeyEvent.KEYCODE_0) return 1; //back i.e. zoom out
		if (keyCode>=KeyEvent.KEYCODE_1 && keyCode<=KeyEvent.KEYCODE_9) return keyCode-KeyEvent.KEYCODE_1+2; //2+ = select that box
		return -1;
	}
	
}