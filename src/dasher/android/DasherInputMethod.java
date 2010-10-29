package dasher.android;

import java.util.Arrays;

import android.inputmethodservice.InputMethodService;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;

public class DasherInputMethod extends InputMethodService {
	private DasherCanvas surf;
	@Override public void onCreate() {
		super.onCreate();
		IMDasherInterface.INSTANCE.Realize(this);
	}
	
	@Override public void onDestroy() {
		Log.d("DasherIME",this+" onDestroy");
		//intf.StartShutdown();
		super.onDestroy();
	}
	
	@Override public DasherCanvas onCreateInputView() {
		IMDasherInterface.INSTANCE.Realize(this); //if already initialized by this IME instance, does nothing!
		surf = new DasherCanvas(DasherInputMethod.this, IMDasherInterface.INSTANCE);
		Log.d("DasherIME", this+" onCreateInputView creating surface "+surf);
		return surf;
	}
	
	@Override 
	public void onStartInput(EditorInfo attribute, boolean restarting) {
		InputConnection ic = getCurrentInputConnection();
		String msg = this + " onStartInput ("+attribute+", "+restarting+") with IC "+ic;
		if (ic==null) {Log.d("DasherIME",msg); return;} //yes, it happens. What else can we do????
		//get current cursor position...
		int initCursorPos=Math.min(attribute.initialSelStart,attribute.initialSelEnd),initNumSel=Math.abs(attribute.initialSelEnd-attribute.initialSelStart);
		Log.d("DasherIME",msg+" cursor "+initCursorPos+" actionLabel "+attribute.actionLabel);
		IMDasherInterface.INSTANCE.SetInputConnection(ic, attribute);
		IMDasherInterface.INSTANCE.setSelection(Math.max(0,initCursorPos),initNumSel,true);
		//that'll ensure a setOffset() task is enqueued first...
		//onCreateInputView().startAnimating();
		//...and then any repaint task afterwards.
		
		//TODO, use EditorInfo to select appropriate...language? (e.g. numbers only!).
		// Passwords???
	}
	
	@Override public boolean onKeyUp(int keyCode, KeyEvent event) {
		String im=PreferenceManager.getDefaultSharedPreferences(this).getString(IMCheckBox.SETTING, "");
		final int key;
		if (im.equals("AndroidCompass") && keyCode>=19 && keyCode<=22) {
			key = ((keyCode-18)*2) % 5; //2,4,1,3
		} else if (im.equals("AndroidSweep") && keyCode!=KeyEvent.KEYCODE_MENU && keyCode!=KeyEvent.KEYCODE_BACK && (keyCode<19 || keyCode>22)) {
			//don't intercept menu/back (too useful!) or cursor movement (for editing textbox).
			key=keyCode;
		} else if (im.equals("AndroidTwoButtonDynamic") && (keyCode==KeyEvent.KEYCODE_VOLUME_UP || keyCode==KeyEvent.KEYCODE_VOLUME_DOWN)) {
			key=(keyCode==KeyEvent.KEYCODE_VOLUME_UP) ? 0 : 1;
		} else
			return super.onKeyUp(keyCode, event);
		IMDasherInterface.INSTANCE.enqueue(new Runnable() {
			private final long time = System.currentTimeMillis();
			public void run() {
				IMDasherInterface.INSTANCE.KeyUp(time, key);
			}
		});
		return true;
	}			
	
	@Override public boolean onKeyDown(int keyCode, KeyEvent event) {
		String im=PreferenceManager.getDefaultSharedPreferences(this).getString(IMCheckBox.SETTING, "");
		final int key;
		if (im.equals("AndroidCompass") && keyCode>=19 && keyCode<=22) {
			key = ((keyCode-18)*2) % 5; //2,4,1,3
		} else if (im.equals("AndroidSweep") && keyCode!=KeyEvent.KEYCODE_MENU && keyCode!=KeyEvent.KEYCODE_BACK && (keyCode<19 || keyCode>22)) {
			//don't intercept menu/back (too useful!) or cursor movement (for editing textbox).
			key=keyCode;
		} else if (im.equals("AndroidTwoButtonDynamic") && (keyCode==KeyEvent.KEYCODE_VOLUME_UP || keyCode==KeyEvent.KEYCODE_VOLUME_DOWN)) {
			key=(keyCode==KeyEvent.KEYCODE_VOLUME_UP) ? 0 : 1;
		} else
			return super.onKeyDown(keyCode, event);
		IMDasherInterface.INSTANCE.enqueue(new Runnable() {
			private final long time = System.currentTimeMillis();
			public void run() {
				IMDasherInterface.INSTANCE.KeyDown(time, key);
			}
		});
		return true;
	}	
	
	@Override
	public void onFinishInput() {
		Log.d("DasherIME",this + " onFinishInput");
		//if (surf!=null) surf.stopAnimating(); //yeah, we can get sent onFinishInput before/without onCreate...
		IMDasherInterface.INSTANCE.SetInputConnection(null, null);
	}

	@Override
	public void onUpdateSelection(int oldSelStart, int oldSelEnd,
								  int newSelStart, int newSelEnd,
								  int candidatesStart, int candidatesEnd) {
		IMDasherInterface.INSTANCE.setSelection(Math.min(newSelStart,newSelEnd),Math.abs(newSelEnd - newSelStart),false);
	}
	
	@Override
	public void onDisplayCompletions(CompletionInfo[] ci) {
		Log.d("DasherIME","Completions: "+Arrays.toString(ci));
	}
}
