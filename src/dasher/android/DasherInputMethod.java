package dasher.android;

import java.util.Collections;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import dasher.CEditEvent;
import dasher.CEvent;
import dasher.CEventHandler;
import dasher.CSettingsStore;
import dasher.android.DasherCanvas.TouchInput;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class DasherInputMethod extends InputMethodService {
	private DasherIntfIME intf;
	private DasherCanvas surf;
	private TouchInput touchIn;

	@Override public void onCreate() {
		super.onCreate();
		Log.d("DasherIME", "onCreate begin");
		intf = new DasherIntfIME();
		Log.d("DasherIME", "onCreate made intf");
		try {
			intf.Realize();
			Log.d("DasherIME", "onCreate realized");
		} catch (RuntimeException e) {
			Log.d("DasherIME", "onCreate failed to realize", e);
		}
	}
	
	@Override public void onDestroy() {
		Log.d("DasherIME","onDestroy");
	}
	
	@Override public View onCreateInputView() {
		if (surf==null) {
			Log.d("DasherIME","onCreateInputView");
			surf = new DasherCanvas(DasherInputMethod.this, intf);
			touchIn.setCanvas(surf);
		} else {
			Log.d("DasherIME","onCreateInputView - surf exists!");
		}
		return surf;
	}
	
	@Override 
	public void onStartInputView(EditorInfo attribute, boolean restarting) {
		Log.d("DasherIME","onStartInputView ("+attribute+", "+restarting+")");
		intf.SetInputConnection(getCurrentInputConnection());
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
		Log.d("DasherIME","onUpdateSelection");
	}
	
	@Override
	public boolean onEvaluateFullscreenMode() {
		Log.d("DasherIME","onEvaluateFullscreenMode");
		return true;
	}
	
	@Override
	public void onUpdateExtractingVisibility(EditorInfo ei) {
		super.onUpdateExtractingVisibility(ei);
		setExtractViewShown(true);
		setCandidatesViewShown(false);
	}
	
	@Override
	public void onBindInput() {
		//can getCurrentInputConnection() now. extract/cache context?
		super.onBindInput();
	}
	
	private class DasherIntfIME extends ADasherInterface
	{
		private InputConnection ic;
		
		/*package*/DasherIntfIME() {super(DasherInputMethod.this);}
		
		@Override
		protected CSettingsStore createSettingsStore(CEventHandler handler) {
			return new AndroidSettings(handler, getSharedPreferences("DasherIMEPrefs", Context.MODE_PRIVATE));
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
		public void CreateModules() {
			super.CreateModules();
			RegisterModule(touchIn = new DasherCanvas.TouchInput(this));
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
					if (_ic==null) {
						surf.stopAnimating();
					} else {
						InvalidateContext(true);
						surf.startAnimating();
					}
				}
			});
		}
		
	}
}
