package dasher.android;

import java.util.Collections;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import dasher.CEditEvent;
import dasher.CEvent;
import dasher.CEventHandler;
import dasher.CSettingsStore;
import android.R;
import android.content.Context;
import android.inputmethodservice.ExtractEditText;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

public class DasherInputMethod extends InputMethodService {
	private static DasherIntfIME intf;
	private DasherCanvas surf;
	@Override public void onCreate() {
		super.onCreate();
		Log.d("DasherIME", "onCreate...");
		synchronized(DasherInputMethod.class) {
			if (intf==null) intf = new DasherIntfIME();
			//set context. (Note, only does normal Realize() the first time)
			intf.Realize(this);
			DasherInputMethod.class.notifyAll();
		}
		Log.d("DasherIME", "onCreate made interface...");
	}
	
	@Override public void onDestroy() {
		Log.d("DasherIME","onDestroy");
		//intf.StartShutdown();
		super.onDestroy();
	}
	
	@Override public View onCreateInputView() {
		Log.d("DasherIME","onCreateInputView");
		synchronized(DasherInputMethod.class) {
			while (intf==null) try {DasherInputMethod.class.wait();} catch (InterruptedException e) {throw new RuntimeException(e);}
			surf = new DasherCanvas(DasherInputMethod.this, intf, 480, 600);
			intf.setCanvas(surf);
		}
		return surf;
	}
	
	/*private FrameLayout fl;
	@Override public View onCreateInputView() {
		if (fl==null) {
			Log.d("DasherIME","onCreateInputView");
			fl = new FrameLayout(DasherInputMethod.this);
			fl.addView(surf = new DasherCanvas(DasherInputMethod.this, intf), new LayoutParams(480,600));
			//LayoutParams params = new LayoutParams(480,600);
			//surf.setLayoutParams(params);
			touchIn.setCanvas(surf);
		} else {
			Log.d("DasherIME","onCreateInputView - surf exists!");
		}
		return fl;
	}*/
	
	@Override 
	public void onStartInputView(EditorInfo attribute, boolean restarting) {
		Log.d("DasherIME","onStartInputView ("+attribute+", "+restarting+")");
		intf.SetInputConnection(getCurrentInputConnection());
		surf.startAnimating();
		//TODO, use EditorInfo to select appropriate...language? (e.g. numbers only!).
		// Passwords???
	}
	
	@Override
	public void onFinishInput() {
		Log.d("DasherIME","onFinishInput");
		intf.SetInputConnection(null);
	}

	@Override
	public void onUpdateSelection(int oldSelStart, int oldSelEnd,
								  int newSelStart, int newSelEnd,
								  int candidatesStart, int candidatesEnd) {
		Log.d("DasherIME","onUpdateSelection "+oldSelStart+"-"+oldSelEnd+" => "+newSelStart+"-"+newSelEnd+" / "+candidatesStart+"-"+candidatesEnd);
	}
	
	@Override
	public boolean onEvaluateFullscreenMode() {
		setExtractViewShown(true);
		setCandidatesViewShown(false);
		return true;
	}
	
	/*@Override
	public boolean isExtractViewShown() {
		boolean ret=super.isExtractViewShown();
		Log.d("DasherIME","isExtractViewShown would return "+ret);
		return true;
	}*/
	
	/*@Override
	public View onCreateExtractTextView() {
		//View sup = super.onCreateExtractTextView();
		ExtractEditText ret = new ExtractEditText(DasherInputMethod.this);
		ret.setId(R.id.inputExtractEditText);
		//if (sup instanceof LinearLayout) {
		//	((LinearLayout)sup).addView(ret,new LayoutParams(LayoutParams.FILL_PARENT,LayoutParams.FILL_PARENT));
		//	return sup;
		//} else
		{
			ret.setLayoutParams(new LayoutParams(480,162));
			return ret;
		}
	}*/
		
	private class DasherIntfIME extends ADasherInterface
	{
		private InputConnection ic;
		
		@Override
		protected CSettingsStore createSettingsStore() {
			return new AndroidSettings(this, getSharedPreferences("DasherIMEPrefs", Context.MODE_PRIVATE));
		}

		@Override
		public ListIterator<Character> charactersEntered() {
			//Log.d("DasherIME","charactersEntered - getting IC", new Exception());
			if (ic==null) return Collections.<Character>emptyList().listIterator();
			return new ListIterator<Character>() {
				private int charsBack=0;
				private CharSequence cacheTextBack="";
				public boolean hasNext() {return charsBack>0;}
				/*initializer*/
				{ getCtx(5); } //initial guess
				private CharSequence getCtx(int len) {
					if (len < cacheTextBack.length() || ic==null) return cacheTextBack;
					CharSequence n = ic.getTextBeforeCursor(Math.max(len,cacheTextBack.length()*2+1), 0);
					if (n!=null && n.length() > cacheTextBack.length()) cacheTextBack = n;
					return cacheTextBack;
				}
				
				public boolean hasPrevious() {
					return charsBack < getCtx(charsBack+1).length();
				}
				
				public Character next() {
					if (charsBack<=0) throw new NoSuchElementException();
					return cacheTextBack.charAt(cacheTextBack.length()-(charsBack--));
				}

				public int nextIndex() {return 1-charsBack;}

				public Character previous() {
					getCtx(++charsBack);
					if (cacheTextBack.length() < charsBack) throw new NoSuchElementException();
					return cacheTextBack.charAt(cacheTextBack.length()-charsBack);
				}

				public int previousIndex() {return -charsBack;}
				
				public void add(Character object) {throw new UnsupportedOperationException("Immutable");}
				public void remove() {throw new UnsupportedOperationException("Immutable");}
				public void set(Character object) {throw new UnsupportedOperationException("Immutable");}					
			};
		}
		
		@Override
		public void Redraw(boolean bChanged) {
			if (surf!=null) surf.requestRender();
		}
		
		@Override
		public void InsertEvent(CEvent e) {
			super.InsertEvent(e);
			if (e instanceof CEditEvent) {
				if (ic==null) {
					Log.d("DasherIME","EditEvent on null IC!");
					return;
				}
				CEditEvent evt = (CEditEvent)e;
				if (evt.m_iEditType==1) {
					Log.d("DasherIME", "Entering "+evt.m_sText);
					ic.commitText(evt.m_sText, 1); //position cursor just after
				} else if (evt.m_iEditType==2) {
					ic.deleteSurroundingText(evt.m_sText.length(), 0);
				}
			}
		}
		
		/*package*/ void SetInputConnection(final InputConnection _ic) {
			enqueue(new Runnable() {
				public void run() {
					DasherIntfIME.this.ic=_ic;
					if (_ic!=null) InvalidateContext(true);
				}
			});
		}
		
	}
}
