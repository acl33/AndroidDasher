package dasher.android;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import dasher.CDasherNode;
import dasher.Ebp_parameters;
import dasher.CControlManager.ControlAction;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.inputmethodservice.InputMethodService;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class DasherInputMethod extends InputMethodService {
	private DasherCanvas surf;
	private ADasherInterface intf;
	private InputConnectionDocument doc;
	@Override public void onCreate() {
		super.onCreate();
		//load data (now), and start training in background
		intf = new ADasherInterface(this, true);
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
	
	@Override public void onBindInput() {
		super.onBindInput();
		Log.d("DasherIME","onBindInput - getIC = "+getCurrentInputConnection());
	}
	
	@Override public void onUnbindInput() {
		super.onUnbindInput();
		Log.d("DasherIME","onUnbindInput");
		intf.SetDocument(doc=null, null, -1);
	}
	
	@Override 
	public void onStartInput(final EditorInfo attribute, boolean restarting) {
		super.onStartInput(attribute, restarting);
		final InputConnection ic = getCurrentInputConnection();
		Log.d("DasherIME",this + " onStartInput ("+InputTypes.getDesc(attribute)+", "+restarting+") with IC "+ic);
		if (ic==null) return; //yes, it happens. What else can we do????
		if (restarting) {
			if (doc!=null && doc.getInputConnection()==ic) return;
			Log.e("DasherIME","Restarting without document?");
		}
		int initCursorPos=Math.max(0,Math.min(attribute.initialSelStart,attribute.initialSelEnd)),
			initNumSel=Math.abs(attribute.initialSelEnd-attribute.initialSelStart);
		//Log.d("DasherIME","cursor "+initCursorPos+" actionLabel "+attribute.actionLabel);
		//Prevent learn-as-you-write when editing any field that is a password
		doc=InputTypes.isPassword(attribute) ? new InputConnectionDocument(intf, ic, initCursorPos, initNumSel) {
			public Boolean overrideBoolParam(Ebp_parameters bp) {
				return (bp==Ebp_parameters.BP_LM_ADAPTIVE) ? Boolean.FALSE : null;
			}
			public String toString() {return "ICDoc-no-learn";}
		} : new InputConnectionDocument(intf, ic, initCursorPos, initNumSel);
		intf.SetDocument(doc, makeICAction(ic, attribute), initCursorPos-1);

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
		super.onFinishInput();
		//if (surf!=null) surf.stopAnimating(); //yeah, we can get sent onFinishInput before/without onCreate...
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
		doc.setSelection(Math.min(newSelStart,newSelEnd),Math.abs(newSelEnd - newSelStart));
	}
	
	/*@Override
	public void onDisplayCompletions(CompletionInfo[] ci) {
		Log.d("DasherIME","Completions: "+Arrays.toString(ci));
	}*/
	
	private static ControlAction makeICAction(final InputConnection ic,
			final EditorInfo attribute) {
		if ((attribute.imeOptions & EditorInfo.IME_FLAG_NO_ACCESSORY_ACTION)!=0
				|| (attribute.imeOptions & EditorInfo.IME_MASK_ACTION) == EditorInfo.IME_ACTION_NONE)
			return null;
		
		final int actionId =
			(attribute.actionId!=EditorInfo.IME_ACTION_UNSPECIFIED && attribute.actionId!=EditorInfo.IME_ACTION_NONE)
			? attribute.actionId : (attribute.imeOptions & EditorInfo.IME_MASK_ACTION);
		final String actionLabel = getActionLabel(attribute, actionId);
		final ControlAction act = new ControlAction() {
			public String desc() {return actionLabel;}
			public void happen(dasher.CDasherNode node) {ic.performEditorAction(actionId);}
			public List<ControlAction> successors() {return Collections.<ControlAction>singletonList(null);}
		};
		if ((attribute.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION)==0) return act;
		
		return new ControlAction() {
			private final List<ControlAction> baseSuccs = Arrays.asList(new ControlAction[] {null, act, null});
			public String desc() {return actionLabel+"?";}
			public void happen(CDasherNode node) {}					
			public List<ControlAction> successors() {return baseSuccs;}
		};
	}

	private static String getActionLabel(EditorInfo attribute, int actionId) {
		if (attribute.actionLabel != null)
			return attribute.actionLabel.toString();
		switch (actionId) {
		case EditorInfo.IME_ACTION_UNSPECIFIED:
		default:
			return "Action"; //?!?!?!
		case EditorInfo.IME_ACTION_GO:
			return "Go";
		case EditorInfo.IME_ACTION_SEARCH:
			return "Search";
		case EditorInfo.IME_ACTION_SEND:
			return "Send";
		case EditorInfo.IME_ACTION_NEXT:
			return "Next";
		case EditorInfo.IME_ACTION_DONE:
			return "Done";
		}
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
