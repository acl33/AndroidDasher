package dasher.android;

import java.util.Arrays;

import android.inputmethodservice.InputMethodService;
import android.util.Log;
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
		surf = new DasherCanvas(DasherInputMethod.this, IMDasherInterface.INSTANCE, 480, 480);
		Log.d("DasherIME", this+" onCreateInputView creating surface "+surf);
		IMDasherInterface.INSTANCE.setCanvas(surf);
		return surf;
	}
	
	@Override 
	public void onStartInput(EditorInfo attribute, boolean restarting) {
		InputConnection ic = getCurrentInputConnection();
		String msg = this + " onStartInput ("+attribute+", "+restarting+") with IC "+ic;
		if (ic==null) {Log.d("DasherIME",msg); return;} //yes, it happens. What else can we do????
		//get current cursor position...
		int initCursorPos=attribute.initialSelStart,initNumSel=attribute.initialSelEnd-initCursorPos;
		Log.d("DasherIME",msg+" cursor "+initCursorPos+" starting animation of "+surf);
		IMDasherInterface.INSTANCE.SetInputConnection(ic);
		IMDasherInterface.INSTANCE.setSelection(Math.max(0,initCursorPos),initNumSel,true);
		//that'll ensure a setOffset() task is enqueued first...
		//onCreateInputView().startAnimating();
		//...and then any repaint task afterwards.
		
		//TODO, use EditorInfo to select appropriate...language? (e.g. numbers only!).
		// Passwords???
	}
	
	@Override
	public void onFinishInput() {
		Log.d("DasherIME",this + " onFinishInput");
		//if (surf!=null) surf.stopAnimating(); //yeah, we can get sent onFinishInput before/without onCreate...
		IMDasherInterface.INSTANCE.SetInputConnection(null);
	}

	@Override
	public void onUpdateSelection(int oldSelStart, int oldSelEnd,
								  int newSelStart, int newSelEnd,
								  int candidatesStart, int candidatesEnd) {
		IMDasherInterface.INSTANCE.setSelection(newSelStart,newSelEnd - newSelStart,false);
	}
	
	@Override
	public void onDisplayCompletions(CompletionInfo[] ci) {
		Log.d("DasherIME","Completions: "+Arrays.toString(ci));
	}
}
