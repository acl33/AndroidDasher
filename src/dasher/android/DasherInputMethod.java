package dasher.android;

import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import dasher.CEditEvent;
import dasher.CEvent;
import dasher.CSettingsStore;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;

public class DasherInputMethod extends InputMethodService {
	private static final DasherIntfIME intf = new DasherIntfIME();
	/* cursorPos from Android - i.e. cursor just _before_ this character */
	private DasherCanvas surf;
	@Override public void onCreate() {
		super.onCreate();
		intf.Realize(this);
	}
	
	@Override public void onDestroy() {
		Log.d("DasherIME",this+" onDestroy");
		//intf.StartShutdown();
		super.onDestroy();
	}
	
	@Override public DasherCanvas onCreateInputView() {
		intf.Realize(this); //if already initialized by this IME instance, does nothing!
		if (surf==null) {
			surf = new DasherCanvas(DasherInputMethod.this, intf, 480, 600);
			Log.d("DasherIME", this+" onCreateInputView creating surface "+surf);
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
	public void onStartInput(EditorInfo attribute, boolean restarting) {
		InputConnection ic = getCurrentInputConnection();
		String msg = this + " onStartInput ("+attribute+", "+restarting+") with IC "+ic;
		if (ic==null) {Log.d("DasherIME",msg); return;} //yes, it happens. What else can we do????
		//try and get current cursor position...
		ExtractedTextRequest req = new ExtractedTextRequest();
		req.hintMaxChars=0;
		android.view.inputmethod.ExtractedText resp = ic.getExtractedText(req, 0);
		int initCursorPos=0,initNumSel=0;
		if (resp!=null) {
			initCursorPos = resp.startOffset+resp.selectionStart;
			initNumSel = resp.selectionEnd-resp.selectionStart;
		}
		Log.d("DasherIME",msg+" cursor "+initCursorPos+" starting animation of "+surf);
		intf.SetInputConnection(ic);
		updateSel(initCursorPos,initNumSel,true);
		//that'll ensure a setOffset() task is enqueued first...
		onCreateInputView().startAnimating();
		//...and then any repaint task afterwards.
		
		//TODO, use EditorInfo to select appropriate...language? (e.g. numbers only!).
		// Passwords???
	}
	
	@Override
	public void onFinishInput() {
		Log.d("DasherIME",this + " onFinishInput");
		if (surf!=null) surf.stopAnimating(); //yeah, we can get sent onFinishInput before/without onCreate...
		intf.SetInputConnection(null);
	}

	@Override
	public void onUpdateSelection(int oldSelStart, int oldSelEnd,
								  int newSelStart, int newSelEnd,
								  int candidatesStart, int candidatesEnd) {
		updateSel(newSelStart,newSelEnd - newSelStart,false);
	}
	
	private void updateSel(int nPos, int nSel, boolean bStart) {
		boolean need2queue;
		synchronized (updSelTask) {
			need2queue = updSelTask.nPos == Integer.MIN_VALUE;
			updSelTask.nPos = nPos;
			updSelTask.nSel = nSel;
			if (bStart) updSelTask.bStarting=true;
			if (need2queue) intf.enqueue(updSelTask);
		}
		Log.d("DasherIME",this+" updateSelection("+nPos+", "+nSel+") "+need2queue);
		
	}
	
	private final class UpdateSelTask implements Runnable {
		/*package*/ int nPos=Integer.MIN_VALUE;
		/*package*/ int nSel;
		/*package*/ boolean bStarting;
		
		public void run() {
			int nPos,nSel; boolean bStarting;
			synchronized(this) {
				if (this.nPos==Integer.MIN_VALUE) return;
				nPos=this.nPos;
				nSel=this.nSel;
				bStarting = this.bStarting;
				this.nPos=Integer.MIN_VALUE;
				this.bStarting = false;
			}
			intf.lastCursorPos = nPos;
			intf.numSelectedChars = nSel;
			intf.setOffset(nPos-1, bStarting);
		}
	};
	private final UpdateSelTask updSelTask = new UpdateSelTask();
	
	@Override
	public void onDisplayCompletions(CompletionInfo[] ci) {
		Log.d("DasherIME","Completions: "+Arrays.toString(ci));
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
		
	private static class DasherIntfIME extends ADasherInterface
	{
		private InputConnection ic;
		/** Android cursor pos - so the index of the character immediately right of the cursor */
		private int lastCursorPos;
		private int numSelectedChars;
		
		@Override
		protected CSettingsStore createSettingsStore() {
			Log.d("DasherIME",androidCtx+" creating settings...");
			return new AndroidSettings(this, androidCtx.getSharedPreferences("DasherIMEPrefs", Context.MODE_PRIVATE));
		}

		@Override
		public ListIterator<Character> getContext(final int iOffset) {
			if (ic==null) return Collections.<Character>emptyList().listIterator();
			return new ListIterator<Character>() {
				/** value of lastCursorPos at time of creation of this iterator */
				private final int myCursorPos = lastCursorPos;
				private CharSequence cacheTextLeft="", cacheTextRight="";
				private int nextPos = iOffset+1;

				private boolean haveChar(int dPos) {
					if (myCursorPos != lastCursorPos) throw new ConcurrentModificationException();
					if (dPos < myCursorPos) {
						//desired character to left of cursor
						if (dPos < myCursorPos - cacheTextLeft.length()) {
							//need to extend backwards
							CharSequence temp = ic.getTextBeforeCursor(myCursorPos+1-dPos,0);
							if (temp==null || temp.length() < cacheTextLeft.length()) return false; //received less ctx than we had already!
							cacheTextLeft = temp;
							if (dPos < myCursorPos - cacheTextLeft.length()) return false; //still not enough
						}
						return true;
					}
					//desired character to right of cursor...(is this ever needed?)
					if (dPos - myCursorPos >= cacheTextRight.length()) {
						//need to extend forwards
						CharSequence temp = ic.getTextAfterCursor(dPos+1 - myCursorPos,0);
						if (temp==null || temp.length() < cacheTextRight.length()) return false; //received no additional ctx!
						cacheTextRight = temp;
						if (dPos - myCursorPos >= cacheTextRight.length()) return false; //received some additional ctx, but not enough
					}
					return true;
				}
				
				private Character getChar(int dPos) {
					return (dPos < myCursorPos) ? cacheTextLeft.charAt(dPos + cacheTextLeft.length() - myCursorPos)
							: cacheTextRight.charAt(dPos - myCursorPos);
				}
				
				public boolean hasPrevious() {return haveChar(nextPos-1);}
				public boolean hasNext() {return haveChar(nextPos);}
				
				public int previousIndex() {return nextPos-1;}
				public int nextIndex() {return nextPos;}
				
				public Character previous() {
					if (!haveChar(nextPos-1)) throw new NoSuchElementException();
					return getChar(--nextPos);
				}
				
				public Character next() {
					if (!haveChar(nextPos)) throw new NoSuchElementException();
					return getChar(nextPos++);
				}
				
				public void add(Character object) {throw new UnsupportedOperationException("Immutable");}
				public void remove() {throw new UnsupportedOperationException("Immutable");}
				public void set(Character object) {throw new UnsupportedOperationException("Immutable");}					
			};
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
					if (numSelectedChars>0) {
						Log.d("DasherIME",androidCtx+" replacing "+numSelectedChars+" following characters with "+evt.m_sText+" - ic "+ic);
						ic.deleteSurroundingText(0, numSelectedChars);
						numSelectedChars=0;
					} else {
						Log.d("DasherIME", androidCtx+" entering "+evt.m_sText+" - ic "+ic);
					}
					ic.commitText(evt.m_sText, 1); //position cursor just after
				} else if (evt.m_iEditType==2) {
					Log.d("DasherIME", androidCtx+" deleting "+evt.m_sText+" - ic "+ic);
					ic.deleteSurroundingText(evt.m_sText.length(), 0);
				}
			}
		}
		
		/*package*/ void SetInputConnection(final InputConnection _ic) {
			enqueue(new Runnable() {
				public void run() {
					DasherIntfIME.this.ic=_ic;
				}
			});
		}
		
	}
}
