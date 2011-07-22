/*
  This file is part of JDasher.

  JDasher is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  JDasher is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with JDasher; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

  Copyright (C) 2006      Christopher Smowton <cs448@cam.ac.uk>

  JDasher is a port derived from the Dasher project; for information on
  the project see www.dasher.org.uk; for information on JDasher itself
  and related projects see www.smowton.net/chris

*/

package dasher.applet;

import java.util.ListIterator;
import java.util.NoSuchElementException;

import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.JTextArea;

/**
 * JDasherEdit is essentially an ordinary JTextArea with an added
 * event handler to respond to Dasher's Edit Events. It responds
 * to EditEvents to update the contents of the text box, and to
 * EditContextEvents to supply a context if it knows of one.
 * 
 */
public class JDasherEdit extends JTextArea implements CaretListener {
	
	/**
	 * Interface which this EditBox will control when text is
	 * entered, typically by calling InvalidateContext if the
	 * user manually edits the text.
	 */
	private dasher.CDasherInterfaceBase m_Interface;
	
	/**
	 * Where the TextArea's Dot was last event; useful in determining
	 * what kind of edit the user has made. 
	 */
	private int iPrevDot;
	
	/**
	 * Where the TextArea's Mark was last event; useful in determining
	 * what kind of edit the user has made. 
	 */
	private int iPrevMark;
	
	/**
	 * Flag to supress events which we have caused by acting on
	 * commands from the Dasher core.
	 * We listen to both Dasher's internal event system and also
	 * Java's EditEvents to spot when the user makes changes
	 * to the TextArea's contents.
	 */
	private boolean supressNextEvent;
	
	/**
	 * Creates a new EditBox with a given width and height in characters
	 * and with the ability to control a given interface.
	 * <p>
	 * The interface can be null; in this case user changes to the
	 * edit box will not be reflected in Dasher's behaviour.
	 * 
	 * @param rows EditBox width in characters
	 * @param cols EditBox height in characters
	 * @param Interface Interface to control
	 */
	public JDasherEdit(int rows, int cols, dasher.CDasherInterfaceBase Interface) {
		super(rows, cols);
		m_Interface = Interface;
		
		this.addCaretListener(this);
	}
	
	/* Handle output of text by the using entering a new DasherNode.
	 * If text is added whilst there is a selection, we delete it
	 * just as if the user had typed over the selection.
	 */
	void outputText(String ch) {
		if(this.getSelectedText()!=null) {
			supressNextEvent = true;
			this.replaceSelection("");
		}
		supressNextEvent = true;
		int oldCaret = this.getCaretPosition();
		this.insert(ch, oldCaret);
		this.setCaretPosition(oldCaret + 1);
	}
	
	/** Handle deletion of text (from reversing) by removing a character.
	 * Deleting whilst something is selected removes both the selection and its
	 * preceding character.
	 */
	void deleteText(String ch) {
		if(!this.getText().equals("")) {

			supressNextEvent = true;

			if(this.getSelectedText()!=null) {
				this.replaceSelection("");
			}

			supressNextEvent = true;
			int oldCaret = this.getCaretPosition();
			this.replaceRange("", oldCaret - ch.length(), oldCaret);
			setCaretPosition(oldCaret - ch.length());
		}

	}
	/** Gets the context, i.e. characters in the editbox,
	 * starting at the specified position
	 * @param StartPosition 0-based index of the character AFTER which the cursor is placed
	 * @return
	 */
	ListIterator<Character> getContext(final int StartPosition) {
		return new ListIterator<Character>() {
			/**0-based index of the character after which the cursor is*/
			int idx=StartPosition;
			/** The character after the cursor*/
			public Character next() {
				//if cursor is after char 0 (the first char), we should return char 1
				try {return getText(++idx,1).charAt(0);}
				catch (BadLocationException e) {idx--;
					throw new NoSuchElementException();
				}
			}
			public boolean hasNext() {return idx<getText().length()-1;}
			public int nextIndex() {return idx+1;}
			public Character previous() {
				//if cursor is after char 0 (the first char), we should return that
				try {return getText(idx--,1).charAt(0);}
				catch (BadLocationException e) {idx++;
					throw new NoSuchElementException();
				}
			}
			public boolean hasPrevious() {return idx>=0;}
			public int previousIndex() {return idx;}
			public void add(Character o) {throw new UnsupportedOperationException();}
			public void remove() {throw new UnsupportedOperationException();}
			public void set(Character o) {throw new UnsupportedOperationException();}
		};
	}			
		

	/**
	 * Called by Java's event handling subsystem when the user moves
	 * the caret, either by typing or clicking.
	 * <p>
	 * If the supressEvent flag is false, we tell the interface about our
	 * new cursor position. It will respond by rebuilding the tree, asking
	 * us about the characters in that position.
	 * <p>
	 * If the supressEvent flag is true we set it to false and
	 * do nothing else.
	 */
	public void caretUpdate(CaretEvent e) {
		
		if(e.getDot() == iPrevDot && e.getMark() == iPrevMark) return;
		
		if(!supressNextEvent) {
			//if getSelectionStart is 0, means the cursor is _before_ character 0
			// (and if the other end of the selection is >0, char 0 is selected).
			// We want to build the tree based on the nodes BEFORE char 0, as we'll
			// be inserting before char 0.
			m_Interface.setOffset(getSelectionStart()-1, true);
			
		}
		else {
			// Event caused by Dasher performing modifications; ignore.
			supressNextEvent = false;
		}
		
		iPrevDot = e.getDot();
		iPrevMark = e.getMark();
		
	}	
}
