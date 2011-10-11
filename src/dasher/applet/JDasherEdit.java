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

import dasher.CDasherInterfaceBase;
import dasher.EditableDocument;

/**
 * JDasherEdit is essentially an ordinary JTextArea with methods to deal with
 * outputting/deleting text for Dasher and which listens to caret updates
 * from Swing to rebuild the Dasher tree accordingly.
 */
public class JDasherEdit extends JTextArea implements CaretListener, EditableDocument {
	
	/**
	 * Interface which this EditBox will control when text is
	 * entered, typically by calling InvalidateContext if the
	 * user manually edits the text.
	 */
	private dasher.CDasherInterfaceBase m_Interface;
	
	/**
	 * Creates a new EditBox with a given width and height in characters,
	 * which will inform the given interface if the cursor is moved.
	 * 
	 * @param rows EditBox width in characters
	 * @param cols EditBox height in characters
	 * @param intf Interface to control; must not be null
	 * @throws IllegalArgumentException if <code>intf</code> is null.
	 */
	public JDasherEdit(int rows, int cols, dasher.CDasherInterfaceBase intf) {
		super(rows, cols);
		m_Interface = intf;
		
		this.addCaretListener(this);
	}
	
	/* Handle output of text by the using entering a new DasherNode.
	 * If text is added whilst there is a selection, we delete it
	 * just as if the user had typed over the selection.
	 */
	public void outputText(String ch, int offset) {
		if(this.getSelectedText()!=null) {
			this.replaceSelection("");
		}
		int oldCaret = this.getCaretPosition();
		this.insert(ch, oldCaret);
		this.setCaretPosition(oldCaret + 1);
	}
	
	/** Handle deletion of text (from reversing) by removing a character.
	 * Deleting whilst something is selected removes both the selection and its
	 * preceding character.
	 */
	public void deleteText(String ch, int offset) {
		if(!this.getText().equals("")) {

			if(this.getSelectedText()!=null) {
				this.replaceSelection("");
			}
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
	 * We call through to {@link CDasherInterfaceBase#setOffset} to update the model
	 * if the new cursor location is different from where the model thinks it should be.
	 * (This is the case if the user has moved the cursor, but if the call to caretUpdate
	 * is in response to Dasher entering text itself, the model will already be in the
	 * right position.)
	 */
	public void caretUpdate(CaretEvent e) {
		
		m_Interface.setOffset(getSelectionStart()-1, false);
		
	}

	@Override
	public Character getCharAt(int pos) {
		String s = getText();
		if (pos>=s.length()) return null;
		return s.charAt(pos);
	}
}
