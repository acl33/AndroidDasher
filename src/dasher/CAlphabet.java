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
package dasher;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ListIterator;

/**
 * Represents an Alphabet, including a list of its symbols,
 * their appearance on screen and when typed, and their foreground
 * and background colours.
 * <p>
 * Note that the colours specified here are mere ordinal numbers;
 * the resolution of these into actual displayable colours is
 * the responsibility of CCustomColours.
 * <p>
 * This class is also responsible for translating a tract of text
 * into a sequence of logical symbol identifiers (see <code>
 * GetSymbols</code>)
 */

public class CAlphabet {
	
	/**
	 * This alphabet's default orientation. Valid values are
	 * specified by Opts.AlphabetTypes, and indicate
	 * left-to-right, right-to-left, top-to-bottom or
	 * bottom-to-top screen orientation.
	 */
	protected final int m_Orientation;
	
	/**
	 * The symbol number of the new-paragraph character.
	 */
	protected final int m_ParagraphSymbol;
	
	/**
	 * The symbol number of the space character.
	 */
	protected final int m_SpaceSymbol;
	
	/**
	 * Path of a file containing training text relevant
	 * to this alphabet.
	 */
	protected final String m_TrainingFile;
	
	// Undocumented as the future of this isn't decided.
	protected final String m_GameModeFile;
	
	/**
	 * Name of the colour scheme which this alphabet
	 * 'prefers' to use. This class does not enforce its
	 * use.
	 */
	protected final String m_DefaultPalette;
		
	/**
	 * Symbols' representations when typed. These are Strings
	 * and not Characters because some alphabets may define
	 * multi-character symbols.
	 */
	protected final ArrayList<String> m_Characters; // stores the characters
	
	/**
	 * Symbols' representations on screen. Usually this is the
	 * same as m_Characters, but some symbols are displayed
	 * with extra ornamentation on screen, such as the apostrophe
	 * in English, and most combining accent characters.
	 */
	protected final ArrayList<String> m_Display;      // stores how the characters are visually represented in the Dasher nodes
	
	/**
	 * Symbols' background colours, used by CDasherView to display
	 * their nodes. These colour identifiers must be looked up
	 * by an instance of CCustomColours to retrieve actual RGB
	 * colours.
	 */
	protected final ArrayList<Integer> m_Colours;       // stores the colour of the characters
	
	/**
	 * Stores the foreground colour of the symbols.
	 */
	protected final ArrayList<Integer> m_Foreground;   // stores the colour of the character foreground
	
	/**
	 * Root of the group tree. Contains all other groups.
	 */
	public final SGroupInfo m_BaseGroup;
	public final int iNumChildNodes;
	/**
	 * Mapping from text to symbol numbers, for use in parsing
	 * training text.
	 */
	protected final CAlphabetMap TextMap=new CAlphabetMap();

	/** Value used (internally - in {@link #singleChars} and as a return value)
	 * to indicate 'no such symbol' */
	public static final int UNDEFINED = -1;
	
	/**
	 * Produces a ready-made Alphabet from an AlphInfo class
	 * which specifies the alphabet. These are typically produced
	 * by an instance of CAlphIO by reading alphabet.*.xml files
	 * but may be producd by other means.
	 * <p>
	 * The alphabet produced will have special characters
	 * (such as the start conversion character) only if AlphabetInfo
	 * specifies their text as having non-zero length.
	 * 
	 * @param AlphabetInfo The AlphInfo class containing the
	 * 					   alphabet specification.
	 * @see CAlphIO
	 * @see CAlphIO.AlphInfo
	 */
	
	public CAlphabet(CAlphIO.AlphInfo AlphabetInfo) { 

		//estimate of the number of symbols in alphabet; not vital, just for initial array capacity
		int nSymbols = AlphabetInfo.m_vCharacters.size() + 2;
		// +2 for space & paragraph - no control, start/end conversion.
		
		m_Characters = new ArrayList<String>(nSymbols);
		m_Display = new ArrayList<String>(nSymbols);
		m_Colours = new ArrayList<Integer>(nSymbols);
		m_Foreground = new ArrayList<Integer>(nSymbols);
		
		// Set miscellaneous options
		
		m_Orientation = AlphabetInfo.Orientation;
		m_TrainingFile = AlphabetInfo.TrainingFile;
		m_GameModeFile = AlphabetInfo.GameModeFile;
		m_DefaultPalette = AlphabetInfo.PreferredColours;
				
		for(CAlphIO.character temp : AlphabetInfo.m_vCharacters) {
			AddChar(temp.Text, temp.Display, temp.Colour, temp.Foreground);
		}
			
		// Set Space character if requested
		
		if(AlphabetInfo.ParagraphCharacter.Text.length() != 0) {
			AddChar(AlphabetInfo.ParagraphCharacter.Text, AlphabetInfo.ParagraphCharacter.Display, AlphabetInfo.ParagraphCharacter.Colour, AlphabetInfo.ParagraphCharacter.Foreground);
			m_ParagraphSymbol = GetNumberSymbols() - 1;
		} else m_ParagraphSymbol = -1;
		
		if(AlphabetInfo.SpaceCharacter.Text.length() != 0) {
			AddChar(AlphabetInfo.SpaceCharacter.Text, AlphabetInfo.SpaceCharacter.Display, AlphabetInfo.SpaceCharacter.Colour, AlphabetInfo.SpaceCharacter.Foreground);
			m_SpaceSymbol = GetNumberSymbols() - 1;
		} else m_SpaceSymbol = -1;
		// New group stuff
		
		m_BaseGroup = AlphabetInfo.m_BaseGroup;
		iNumChildNodes = AlphabetInfo.iNumChildNodes;
	}
	
	/**
	 * Converts a string of text into a list of symbol indentifiers.
	 * Typically used in the course of training a language model,
	 * this fills the Symbols Collection with a seqeunce of integer
	 * symbol identifiers. Each character is individually
	 * used as a key into TextMap to determine its symbol number;
	 * as such their is no support for multi-character symbols
	 * at present. 
	 * 
	 * @param Symbols Collection to be filled with symbol identifiers.
	 * @param Input String to be converted.
	 * @param IsMore Redundant parameter; used to signal that the last
	 * 			 	 input character may be an incomplete UTF-8
	 * 				 character, but redundant since UTF-16 is now
	 * 				 used internally.
	 */
	
	public void GetSymbols(Collection<Integer> Symbols, String input) {
		StringReader rdr=new StringReader(input);
		while (true) {
			try {
				Symbols.add(TextMap.GetNext(rdr));
			} catch (IOException e) {
				assert (e instanceof FileNotFoundException);
				break;
			}
		}
	}
	
	public ListIterator<Integer> GetSymbols(final ListIterator<Character> previousChars) {
		//assume characters are 1:1 with symbols....NOT A SAFE ASSUMPTION
		return new ListIterator<Integer>() {
			public boolean hasPrevious() {return previousChars.hasPrevious();}
			public Integer previous() {return TextMap.GetSingleChar(previousChars.previous());}
			public int previousIndex() {return previousChars.previousIndex();}
			public boolean hasNext() {return previousChars.hasNext();}
			public Integer next() {return TextMap.GetSingleChar(previousChars.next());}
			public int nextIndex() {return previousChars.nextIndex();}
			public void add(Integer i) {throw new UnsupportedOperationException("Immutable");}
			public void remove() {throw new UnsupportedOperationException("Immutable");}
			public void set(Integer i) {throw new UnsupportedOperationException("Immutable");}
		};
	}
	
	/**
	 * Adds a new character to this alphabet. The new character will
	 * be assigned the next free symbol index.
	 * 
	 * @param NewCharacter Representation of this character as to be shown in textual output.
	 * @param Display Representation to be shown on screen.
	 * @param Colour Background colour index to be used in drawing nodes containing this character. 
	 * @param Foreground String representation of foreground colour to be used drawing the text;
	 * <code>""</code> =&gt; use default of 4
	 */
	
	private void AddChar(String NewCharacter, String Display, int Colour, String Foreground) {
		m_Characters.add(NewCharacter);
		m_Display.add(Display);
		m_Colours.add(Colour);
		m_Foreground.add(Foreground.equals("") ? 4 : Integer.parseInt(Foreground));
		
		//don't add a symbol with no text (the map's only used for
		// importing training text, i.e. converting from text to symbol!)
		if (NewCharacter.length()>0)
			TextMap.Add(NewCharacter, m_Characters.size() - 1);
	}
	
	/**
	 * Gets number of symbols in this alphabet, including special characters.
	 * 
	 * @return Symbol count in this Alphabet.
	 */
	
	public int GetNumberSymbols() {
		return m_Characters.size();
	}
	
	/**
	 * Gets the orientation associated with this alphabet.
	 * Allowable values are enumerated by Opts.ScreenOrientations.
	 * 
	 * @return This Alphabet's preferred orientation. 
	 */
	
	public int GetOrientation() {
		return m_Orientation;
	}
	
	/**
	 * Gets the training file to be used training a language
	 * model which uses this alphabet.
	 * 
	 * @return Path to the specified training file.
	 */
	
	public String GetTrainingFile() {
		return m_TrainingFile;
	}
	
	// Undocumented pending changes to this.
	
	public String GetGameModeFile() {
		return m_GameModeFile;
	}
	
	/**
	 * Gets the name of the colour scheme preferred by this
	 * alphabet.
	 * 
	 * @return Preferred colour scheme name.
	 */
	
	public String GetPalette() {
		return m_DefaultPalette;
	}
	
	/**
	 * Gets the array of display texts. This should not be used
	 * to make changes to the alphabet.
	 * 
	 * @return Reference to the array of characters' display texts.
	 */
	
	public ArrayList<String> GetDisplayTexts() {
		return m_Display;
		// Another method for efficiency, allowing an outside class to
		// get a handle to our display text list. This shouldn't be bad
		// as they only get a reference and do not copy data.
	}
	
	/**
	 * Retrieves the String which should be displayed to represent a given character.
	 * 
	 * @param i Index of the character to be looked up.
	 * @return Display string for this character.
	 */
	
	public String GetDisplayText(int i) {
		return m_Display.get(i);
	}
	
	/**
	 * Retrieves the typed character representation of a given symbol.
	 * 
	 * @param i Symbol to look up
	 * @return Character which should be typed in the edit box (for example) to represent it.
	 */
	
	public String GetText(int i) {
		return m_Characters.get(i);
	} 
	
	/**
	 * Gets a reference to the array of background colours used
	 * when drawing symbols. The returned array should not be
	 * modified, and GetColour(i) should be used where possible;
	 * this is for increased efficiency when iteration over the
	 * array is desired.
	 * 
	 * @return Array of background colours.
	 */
	
	public ArrayList<Integer> GetColours() {
		// Allows another class to obtain a reference to the colour map.
		// This is in order to increase the efficiency of PopulateChildrenWithSymbol.
		
		return m_Colours;
	}
	
	/**
	 * Retrieves the background colour for a given symbol.
	 * 
	 * @param i Symbol indentifier
	 * @return Background colour for this symbol.
	 */
	
	public int GetColour(int i) {
		return m_Colours.get(i);
	}

	/**
	 * Retrieves the foreground (text) colour for a given symbol.
	 * 
	 * @param i Symbol identifier.
	 * @return Foreground colour for this symbol.
	 */
	
	public int GetForeground(int i) {
	      return m_Foreground.get(i);
	} 
	
	/**
	 * Gets the instance of CAlphabetMap which maps Strings
	 * to symbols in this Alphabet.
	 * 
	 * @return TextMap
	 */
	
	public CAlphabetMap GetAlphabetMap() {
		return TextMap;
	}
	
	/**
	 * Gets the index of this alphabet's paragraph symbol.
	 * 
	 * @return index of paragraph symbol.
	 */
	
	public int GetParagraphSymbol()  {
		return m_ParagraphSymbol;
	}
	
	/**
	 * Gets the index of this alphabet's space symbol.
	 * 
	 * @return index of space symbol.
	 */
	
	public int GetSpaceSymbol()  {
		return m_SpaceSymbol;
	}
	
}
