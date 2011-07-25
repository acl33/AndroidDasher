package dasher.android;

import java.util.Arrays;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.inputmethodservice.InputMethodService;
import android.os.PowerManager;
import android.os.SystemClock;
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
	private IMDasherInterface intf;
	@Override public void onCreate() {
		super.onCreate();
		//load data (now), and start training in background
		intf = new IMDasherInterface(this, true);
	}
	
	@Override public void onDestroy() {
		Log.d("DasherIME",this+" onDestroy");
		//intf.StartShutdown();
		super.onDestroy();
	}
	
	@Override public DasherCanvas onCreateInputView() {
		surf = new DasherCanvas(DasherInputMethod.this, intf);
		Log.d("DasherIME", this+" onCreateInputView creating surface "+surf);
		return surf;
	}
	
	private final Intent sepIntent = new Intent("ca.idi.tekla.sep.SEPService");
	{sepIntent.putExtra("ca.idi.tekla.sep.extra.SHIELD_ADDRESS", (String)null);}
	
	@Override 
	public void onStartInput(EditorInfo attribute, boolean restarting) {
		InputConnection ic = getCurrentInputConnection();
		String msg = this + " onStartInput ("+attribute+", "+restarting+") with IC "+ic;
		if (ic==null) {Log.d("DasherIME",msg); return;} //yes, it happens. What else can we do????
		//get current cursor position...
		int initCursorPos=Math.min(attribute.initialSelStart,attribute.initialSelEnd),initNumSel=Math.abs(attribute.initialSelEnd-attribute.initialSelStart);
		Log.d("DasherIME",msg+" cursor "+initCursorPos+" actionLabel "+attribute.actionLabel);
		intf.SetInputConnection(ic, attribute);
		intf.setSelection(Math.max(0,initCursorPos),initNumSel,true);
		//that'll ensure a setOffset() task is enqueued first...
		//onCreateInputView().startAnimating();
		//...and then any repaint task afterwards.
		
		//TODO, use EditorInfo to select appropriate...language? (e.g. numbers only!).
		// Passwords???
		
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("AndroidTeklaShield", false)) {
			Log.d("DasherIME","Starting Tekla Service...");
			registerReceiver(sepBroadcastReceiver, new IntentFilter("ca.idi.tekla.sep.action.SWITCH_EVENT_RECEIVED"));
			if (startService(sepIntent)!=null)
				Log.d("DasherIME","Started Tekla");
			else Log.d("DasherIME","Couldn't start Tekla");
		}
	}
	
	@Override public boolean onKeyUp(int keyCode, KeyEvent event) {
		final int id = intf.convertAndroidKeycode(keyCode);
		if (id==-1)	return super.onKeyUp(keyCode, event);
		intf.enqueue(new Runnable() {
			private final long time = System.currentTimeMillis();
			public void run() {
				intf.KeyUp(time, id);
			}
		});
		return true;
	}			
	
	@Override public boolean onKeyDown(int keyCode, KeyEvent event) {
		final int id = intf.convertAndroidKeycode(keyCode);
		if (id==-1) return super.onKeyDown(keyCode, event);
		intf.enqueue(new Runnable() {
			private final long time = System.currentTimeMillis();
			public void run() {
				intf.KeyDown(time, id);
			}
		});
		return true;
	}	
	
	@Override
	public void onFinishInput() {
		Log.d("DasherIME",this + " onFinishInput");
		//if (surf!=null) surf.stopAnimating(); //yeah, we can get sent onFinishInput before/without onCreate...
		intf.SetInputConnection(null, null);
		tekla: if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("AndroidTeklaShield", false)) {
			Log.d("DasherIME","Stopping Tekla Service...");
			try {
				unregisterReceiver(sepBroadcastReceiver);
			} catch (IllegalArgumentException e) {
				Log.d("DasherIME","Tekla service not running?");
				break tekla;
			}
			stopService(sepIntent);
			Log.d("DasherIME","Stopped Tekla");
		}
	}

	@Override
	public void onUpdateSelection(int oldSelStart, int oldSelEnd,
								  int newSelStart, int newSelEnd,
								  int candidatesStart, int candidatesEnd) {
		intf.setSelection(Math.min(newSelStart,newSelEnd),Math.abs(newSelEnd - newSelStart),false);
	}
	
	@Override
	public void onDisplayCompletions(CompletionInfo[] ci) {
		Log.d("DasherIME","Completions: "+Arrays.toString(ci));
	}
	
	private final BroadcastReceiver sepBroadcastReceiver = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) {
	    	final int keyId;
	    	switch(intent.getExtras().getInt("ca.idi.tekla.sep.extra.SWITCH_EVENT")) {
	            case 10: //up
	            	keyId=4;
	                break;
	            case 20: //down
	            	keyId=2;
	                break;
	            case 40: //right
	            	keyId=3; //=> forward
	                break;
	            case 80: //left
	            	keyId=1; //=> back box
	                break;
                default:
                	Log.d("DasherIME", "switch event received with keyid " +intent.getExtras().getInt("ca.idi.tekla.sep.extra.SWITCH_EVENT"));
                	//don't know what to do with this switch id?!?!
                	return;
	            }
	        final long time = System.currentTimeMillis();
	        intf.enqueue(new Runnable() {
	        	public void run() {
	        		intf.KeyDown(time, keyId);
	        		intf.KeyUp(time, keyId);
	        	}
	        });
	    }
	};

}
