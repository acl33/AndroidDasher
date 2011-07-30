/**
 * 
 */
package dasher.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import dasher.CControlManager.ControlAction;
import dasher.CSettingsStore;
import dasher.CDasherNode;

class IMDasherInterface extends ADasherInterface
{
	//static IMDasherInterface INSTANCE;// = new IMDasherInterface();
	
	private InputConnection ic;
	/** Android cursor pos - so the index of the character immediately right of the cursor */
	private int lastCursorPos;
	private int numSelectedChars;
	
	/* request from another thread, for main Dasher thread to set lastCursorPos when it runs... (-1 = don't!) */
	private int new_lastCursorPos;
	private int new_numSelectedChars;
	private boolean bForce;
	
	private final IntQueue expectedOffsets = new IntQueue();
		
	IMDasherInterface(Context androidCtx,boolean train) {
		super(androidCtx,train);
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
					if (dPos<0) return false;
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
	
	@Override public void outputText(String ch, double prob) {
		if (ic==null) {
			Log.d("DasherIME","outputText when no InputConnection?!");
			return;
		}
		if (numSelectedChars>0) {
			Log.d("DasherIME",androidCtx+" deleting selection of "+numSelectedChars);
			ic.deleteSurroundingText(0, numSelectedChars);
			numSelectedChars=0;
		}
		ic.commitText(ch, 1); //position cursor just after
		lastCursorPos+=ch.length();
		synchronized(this) {expectedOffsets.push(lastCursorPos);}
		Log.d("DasherIME",androidCtx+" entering "+ch+" to give "+ic.getTextBeforeCursor(5, 0));
	}
	
	@Override public void deleteText(String ch, double prob) {
		if (ic==null) {
			Log.d("DasherIME","deleteText when no InputConnection?!");
			return;
		}
		ic.deleteSurroundingText(ch.length(), 0);
		lastCursorPos-=ch.length();
		synchronized(this) {expectedOffsets.push(lastCursorPos);}
		Log.d("DasherIME", androidCtx+" deleting "+ch+" to give "+ic.getTextBeforeCursor(5, 0));		
	}
	
	
	@Override public void NewFrame(long time) {
		synchronized(this) {
			if (new_lastCursorPos!=-1) {
				clearRequest: {
					Log.d("DasherIME","Request update cursor pos to "+new_lastCursorPos+","+new_numSelectedChars);
					while (expectedOffsets.size()>0)
						if (expectedOffsets.pop()==new_lastCursorPos)
							if (bForce) break; else break clearRequest;
					if (new_lastCursorPos == lastCursorPos && !bForce) break clearRequest;
					Log.d("DasherIME","Proceeding to update cursor pos to "+new_lastCursorPos+","+new_numSelectedChars);
					lastCursorPos = new_lastCursorPos;
					numSelectedChars = new_numSelectedChars;
					setOffset(lastCursorPos-1, bForce);
				} //break clearRequest
				new_lastCursorPos=-1;
				bForce=false;
			}
		}
		super.NewFrame(time);
	}
	
	/*package*/ void SetInputConnection(final InputConnection ic, final EditorInfo attribute) {
		enqueue(new Runnable() {
			public void run() {
				IMDasherInterface.this.ic=ic;
				if (attribute==null) return; //finishInput - don't recheck/compute action, wait until next StartInput()
				boolean hadAction = IMDasherInterface.this.actionLabel!=null;
				if ((attribute.imeOptions & EditorInfo.IME_FLAG_NO_ACCESSORY_ACTION)!=0
						|| (attribute.imeOptions & EditorInfo.IME_MASK_ACTION) == EditorInfo.IME_ACTION_NONE)
					IMDasherInterface.this.actionLabel=null;
				else {
					IMDasherInterface.this.actionId =
						(attribute.actionId!=EditorInfo.IME_ACTION_UNSPECIFIED && attribute.actionId!=EditorInfo.IME_ACTION_NONE)
						? attribute.actionId : (attribute.imeOptions & EditorInfo.IME_MASK_ACTION);
					if (attribute.actionLabel != null)
						IMDasherInterface.this.actionLabel = attribute.actionLabel.toString();
					else switch (IMDasherInterface.this.actionId) {
					case EditorInfo.IME_ACTION_UNSPECIFIED:
					default:
						IMDasherInterface.this.actionLabel = "Action"; //?!?!?!
						break;
					case EditorInfo.IME_ACTION_GO:
						IMDasherInterface.this.actionLabel = "Go";
						break;
					case EditorInfo.IME_ACTION_SEARCH:
						IMDasherInterface.this.actionLabel = "Search";
						break;
					case EditorInfo.IME_ACTION_SEND:
						IMDasherInterface.this.actionLabel = "Send";
						break;
					case EditorInfo.IME_ACTION_NEXT:
						IMDasherInterface.this.actionLabel = "Next";
						break;
					case EditorInfo.IME_ACTION_DONE:
						IMDasherInterface.this.actionLabel = "Done";
						break;
					}
					IMDasherInterface.this.actionHard = ((attribute.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION)!=0);
				}
				if (hadAction || IMDasherInterface.this.actionLabel!=null)
					UpdateNCManager();
			}
		});
	}
	
	/*package*/ void setSelection(int nPos, int nSel, boolean bStart) {
		synchronized (this) {
			new_lastCursorPos = nPos;
			new_numSelectedChars = nSel;
			bForce |= bStart;
			Redraw(true);
		}
	}
	
	private String actionLabel;
	private int actionId;
	private boolean actionHard;
	
	@Override public List<ControlAction> getControlActions() {
		List<ControlAction> lst = super.getControlActions();
		if (actionLabel!=null) {
			final List<ControlAction> otherActsOrExit = new ArrayList<ControlAction>(lst);
			ControlAction perform = new ControlAction() {
				public String desc() {return actionLabel;}
				public void happen(dasher.CDasherNode node) {ic.performEditorAction(actionId);}
				public List<ControlAction> successors() {return otherActsOrExit;}
			};
			if (actionHard) {
				List<ControlAction> temp = new ArrayList<ControlAction>();
				temp.add(null);
				temp.add(perform);
				temp.add(null);
				final List<ControlAction> rootSuccs = Collections.unmodifiableList(temp);
				final String title=actionLabel+"?";
				lst.add(new ControlAction() {
					public String desc() {return title;}
					public void happen(CDasherNode node) {}					
					public List<ControlAction> successors() {return rootSuccs;}
				});
			} else lst.add(perform);
		}
		return lst;
	}
	
}
