/**
 * 
 */
package dasher.android;

import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.inputmethod.InputConnection;
import dasher.CEditEvent;
import dasher.CEvent;
import dasher.CSettingsStore;
import dasher.Ebp_parameters;
import dasher.Elp_parameters;
import dasher.Esp_parameters;

class IMDasherInterface extends ADasherInterface
{
	static final IMDasherInterface INSTANCE = new IMDasherInterface();
	private IMDasherInterface() {}
	private InputConnection ic;
	/** Android cursor pos - so the index of the character immediately right of the cursor */
	private int lastCursorPos;
	private int numSelectedChars;
	
	@Override
	protected CSettingsStore createSettingsStore() {
		Log.d("DasherIME",androidCtx+" creating settings...");
		return new AndroidSettings(this, PreferenceManager.getDefaultSharedPreferences(androidCtx));
	}
	
	@Override public void Realize(Context ctx) {
		super.Realize(ctx);
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
				IMDasherInterface.this.ic=_ic;
			}
		});
	}
	
	/*package*/ void setSelection(int nPos, int nSel, boolean bStart) {
		boolean need2queue;
		synchronized (updSelTask) {
			need2queue = updSelTask.nPos == Integer.MIN_VALUE;
			updSelTask.nPos = nPos;
			updSelTask.nSel = nSel;
			if (bStart) updSelTask.bStarting=true;
			if (need2queue) IMDasherInterface.this.enqueue(updSelTask);
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
			IMDasherInterface.this.lastCursorPos = nPos;
			IMDasherInterface.this.numSelectedChars = nSel;
			IMDasherInterface.this.setOffset(nPos-1, bStarting);
		}
	};
	private final UpdateSelTask updSelTask = new UpdateSelTask();	
}
