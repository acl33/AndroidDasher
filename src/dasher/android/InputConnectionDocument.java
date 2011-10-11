package dasher.android;

import android.view.inputmethod.InputConnection;
import dasher.Ebp_parameters;
import dasher.EditableDocument;
import dasher.Elp_parameters;
import dasher.Esp_parameters;

public class InputConnectionDocument implements EditableDocument, AndroidSettings.SettingsOverride, Runnable {
	
	private final InputConnection ic;
	private final ADasherInterface iface;
	private int numSelectedChars;
	
	/** last known position of cursor, dasher-style (0=after first character) */
	private int lastCursorPos;
	
	/* request from another thread, for main Dasher thread to set lastCursorPos when it runs... (Integer.MIN_VALUE = don't!) */
	private int new_lastCursorPos=Integer.MIN_VALUE;
	private int new_numSelectedChars;
	
	/** <p>Offsets we are still waiting for Android to tell us the cursor has moved to,
	 * after Dasher has output text that would move the cursor to that location.
	 * (Notifications from Android come asynchronously on a different thread, potentially
	 * well after Dasher has output the text, or even more text afterwards!)</p>
	 * Offsets are stored Dasher-style (0=after first character).
	 */
	private final IntQueue expectedOffsets = new IntQueue();

	/** Wraps a new InputConnection at the start of an editing session
	 *  
	 * @param iface
	 * @param ic
	 * @param cursor Initial cursor position, Android-style (0=before first character)
	 * @param numSelectedChars
	 */
	public InputConnectionDocument(ADasherInterface iface, InputConnection ic, int cursor, int numSelectedChars) {
		this.iface=iface;
		this.ic=ic;
		this.lastCursorPos = cursor-1;//convert from Android to Dasher cursor indices
		this.numSelectedChars = numSelectedChars;
		android.util.Log.d("DasherIME","Creating ICDoc w/ cursor"+lastCursorPos);
	}
	
	/** Subclasses may override this method to provide a different
	 * value for the specified boolean user preference. The default
	 * implementation returns <code>null</code>, i.e. to use the stored
	 * preferences. */
	public Boolean overrideBoolParam(Ebp_parameters bp) {
		return null;
	}
	
	/** Subclasses may override this method to provide a different
	 * value for the specified long user preference. The default
	 * implementation returns <code>null</code>, i.e. to use the stored
	 * preferences. */
	public Long overrideLongParam(Elp_parameters lp) {
		return null;
	}
	
	/** Subclasses may override this method to provide a different
	 * value for the specified String user preference. The default
	 * implementation returns <code>null</code>, i.e. to use the stored
	 * preferences. */
	public String overrideStringParam(Esp_parameters sp) {
		return null;
	}
	
	/**Called on InputMethod thread when cursor has moved.
	 * 
	 * @param nPos Position of cursor, Android-style (0=before first character)
	 * @param nSel Number of selected characters (0=no selection. We always behave
	 * as if the cursor were at the beginning of the selection.)
	 */
	/*package*/ void setSelection(int nPos, int nSel) {
		nPos--; //convert to Dasher
		synchronized (expectedOffsets) {
			while (expectedOffsets.size()>0) {
				if (expectedOffsets.pop()==nPos && nSel==0) {
					//expectedOffsets.notifyAll();
					return; //this selection change is a result of Dasher outputting text
				}
			}
			//ok - not expecting this position/selection - i.e. it is not a result of a Dasher output/delete.
			synchronized(this) {
				cacheContent=null; cacheStart=-1;
				if (new_lastCursorPos==Integer.MIN_VALUE) iface.enqueue(this);
				new_lastCursorPos = nPos;
				new_numSelectedChars = nSel;
			}
			//expectedOffsets.notifyAll();
		}
		
	}
	
	/** On Dasher Thread, when we've enqueued ourselves */
	public synchronized void run() {
		//android.util.Log.d("DasherIME","DasherThread update lastCursorPos to"+new_lastCursorPos);
		iface.setOffset(lastCursorPos = new_lastCursorPos, false);
		numSelectedChars = new_numSelectedChars;
		iface.Redraw(true);
		new_lastCursorPos=Integer.MIN_VALUE;
	}

	/** On Dasher Thread, rendering a frame, when some node has been exitted */
	public void deleteText(String ch, int offset) {
		synchronized (expectedOffsets) {
			synchronized(this) {
				//android.util.Log.d("DasherIME","Deleting "+ch+" with cursorPos "+lastCursorPos+" (will be "+(lastCursorPos-ch.length())+")");
				ic.deleteSurroundingText(ch.length(), 0);
				if (lastCursorPos!=offset) throw new IllegalStateException();
				lastCursorPos-=ch.length();
				expectedOffsets.push(lastCursorPos);
				cacheContent=null; cacheStart=-1;
			}
		}
	}

	/** On Dasher Thread, rendering a frame, when some node has been entered */
	public void outputText(String ch, int offset) {
		synchronized(expectedOffsets) {
			synchronized(this) {
				if (numSelectedChars>0) {
					//android.util.Log.d("DasherIME","Deleting "+numSelectedChars+" selected chars with cursorPos "+lastCursorPos);
					ic.deleteSurroundingText(0, numSelectedChars);
					numSelectedChars=0;
				}
				//android.util.Log.d("DasherIME","Outputting "+ch+" with cursorPos "+lastCursorPos+" (will be "+(lastCursorPos+ch.length())+")");
				if (lastCursorPos != offset-ch.length()) throw new IllegalStateException();
				ic.commitText(ch, 1); //position cursor just after
				lastCursorPos+=ch.length();
				expectedOffsets.push(lastCursorPos);
				cacheContent=null; cacheStart=-1;
			}
		}
	}

	/** Get char at position <code>idx</code>, or null if no such;
	 * should be called only on Dasher thread.
	 * Attempts to update cacheContent/cacheStart to include the specified character,
	 * if necessary; then returns contents of cache.
	 * @param num Index of desired <em>character</em> (not cursor position - so 0 = first char)
	 * @return null, if we couldn't get that character from the InputConnection; else, a single
	 * char, wrapped in a Character
	 */
	public Character getCharAt(int num) {
		/*synchronized(expectedOffsets) {
		while (expectedOffsets.size()>0)
			//we've sent text to the IC, but it hasn't called us back yet.
			//So we don't know where the IC thinks the cursor is atm.
			try {
				android.util.Log.d("DasherIME","Waiting for IC callback...");
				expectedOffsets.wait();
			} catch (InterruptedException e) {}*/
		synchronized(this) {
			if (cacheContent==null || cacheStart>num || cacheContent.length()+cacheStart<=num) {
				int cursorPos = (new_lastCursorPos==Integer.MIN_VALUE) ? lastCursorPos : new_lastCursorPos;
				//in latter case, IC has told us cursor has moved, but we've not rebuilt yet(?!)
				if (num>cursorPos) {
					//desired character is after cursor
					CharSequence s = ic.getTextAfterCursor(num-cursorPos, 0);
					if (s==null || s.length() < num-cursorPos) {
						//android.util.Log.d("DasherIME","Requested "+(num-cursorPos)+" chars after cursor, got \""+s+"\"");
						return null;
					}
					// ok, must have received some data we didn't have before
					if (cacheContent!=null && cacheStart<=cursorPos && cacheStart+cacheContent.length()>cursorPos)
						//keep what we already had _before_ the cursor, if contiguous up to the cursor
						cacheContent = cacheContent.substring(0,cursorPos+1-cacheStart)+s;
					else {
						cacheStart = cursorPos+1;//first char in cache, is _after_ cursor
						cacheContent=s.toString();
					}
				} else {
					CharSequence s = ic.getTextBeforeCursor(cursorPos+1-num, 0);
					if (s==null || s.length() < cursorPos+1-num) {
						//android.util.Log.d("DasherIME","Requested "+(cursorPos+1-num)+" chars before cursor, got \""+s+"\"");
						return null;
					}
					if (cacheContent!=null && cacheStart<=cursorPos && cacheStart+cacheContent.length()>cursorPos+1) {
						//keep anything strictly after cursor, if contiguous with what's before
						cacheContent = s + cacheContent.substring(cursorPos+1-cacheStart);
					} else cacheContent = s.toString();
					cacheStart = cursorPos+1-s.length();
				}
			}
			return cacheContent.charAt(num-cacheStart);
		}
	//}
	}

	private String cacheContent;
	private int cacheStart=-1; //cacheContent[x] = edittext[x+cacheStart]

	public void moveCursor(int iNewOffset) {
		synchronized (expectedOffsets) {
			synchronized(this) {
				ic.setSelection(iNewOffset+1, iNewOffset+1);
				expectedOffsets.push(lastCursorPos=iNewOffset);
			}
		}
	}

	/*package*/ InputConnection getInputConnection() {
		return ic;
	}

	public String toString() {
		return "ICDoc("+ic+")";
	}

}
