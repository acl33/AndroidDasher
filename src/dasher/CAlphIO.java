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

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Stack;
import java.io.*;

import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import dasher.Opts.ScreenOrientations;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;

/**
 * 
 * Responsible for reading a given list of XML files, extracting
 * alphabet information, and creating a list of AlphInfo objects
 * for each.
 * <p>
 * Further, after doing so, responsible for returning an AlphInfo
 * object corresponding to a given alphabet name, and of giving
 * a full list of available alphabets.
 *
 */
public class CAlphIO extends XMLFileParser {

	/** Map from {@link CAlphIO.AlphInfo#name} to AlphInfo object */
	protected HashMap <String, AlphInfo> Alphabets = new HashMap<String, AlphInfo>(); 
	
	/**
	 * Parser to be used to import XML data.
	 */
	protected SAXParser parser;
	
	/**
	 * Simple struct representing an alphabet.
	 */
	public static class AlphInfo {
		private AlphInfo(String name) {this.name=name;}
		/**
		 * This alphabet's default orientation. Valid values are
		 * specified by Opts.AlphabetTypes, and indicate
		 * left-to-right, right-to-left, top-to-bottom or
		 * bottom-to-top screen orientation. Defaults to LeftToRight if
		 * the XML does not specify any.
		 */
		private Opts.ScreenOrientations m_Orientation=Opts.ScreenOrientations.LEFT_TO_RIGHT;
		
		/** The symbol number of the new-paragraph character, -1 for none */
		private int m_ParagraphSymbol=-1;
		
		/** The symbol number of the space character, -1 for none */
		private int m_SpaceSymbol=-1;
		
		/**
		 * Path of a file containing training text relevant
		 * to this alphabet.
		 */
		private String m_TrainingFile;
		
		// Undocumented as the future of this isn't decided.
		private String m_GameModeFile;
		
		/**
		 * Name of the colour scheme which this alphabet
		 * 'prefers' to use. This class does not enforce its
		 * use.
		 */
		private String m_DefaultPalette="";
			
		/**
		 * Symbols' representations when typed. These are Strings in order to support
		 * (a) unicode characters beyond 0xFFFF and (b) "\r\n" as the paragraph character
		 * on Windows. (Hence note the latter is special-cased in many places). 
		 * Alphabets may <em>not</em> in general define multi-character symbols.
		 */
		private final ArrayList<String> m_Characters = new ArrayList<String>();
		
		/**
		 * Symbols' representations on screen. In many cases this is the same as m_Characters,
		 * but differs for e.g. space, paragraph, apostrophe, etc.
		 */
		protected final ArrayList<String> m_Display = new ArrayList<String>();
		
		/**
		 * Symbols' background colours, where particular colours are specified
		 * (in such cases, the same colour will be used for all occurrences of that symbol).
		 * Most symbols do not specify a colour (in XML), in which case this
		 * array will store '-1', and a colour dependent upon both symbol number and <em>phase</em>
		 * will be used. All colours are indices into a table stored in a CCustomColours object
		 * which must be used to retrieve actual RGB colours.
		 */
		protected final ArrayList<Integer> m_Colours = new ArrayList<Integer>();
		
		/**
		 * Stores the foreground colour of the symbols, where specified. Not actually used at present...
		 */
		protected final ArrayList<Integer> m_Foreground = new ArrayList<Integer>();   // stores the colour of the character foreground
		
		/**
		 * Root of the group tree, i.e. <em>first</em> group at toplevel (there may be others
		 * accessed via {@link SGroupInfo#Next}).
		 */
		private SGroupInfo m_BaseGroup;
		/** Number of nodes at top level, i.e. groups plus (symbols not in a group) */
		private int iNumChildNodes;

		/**
		 * Alphabet name as per xml
		 */
		public final String name;

		/**
		 * Context to use when starting a new sentence or document. Defaults to ". "
		 */
		private String m_strDefaultContext=". ";
		
		public String getDefaultContext() {return m_strDefaultContext;}

		/** Escape character to use in training files to delimit context-switching commands,
		 * or null to not use context-switching commands. 
		 */
		protected Character ctxChar;
		
		public CAlphabetMap makeMap() {
			CAlphabetMap m = new CAlphabetMap(this);
			if (m_ParagraphSymbol!=CAlphabetMap.UNDEFINED) m.AddParagraphSymbol(m_ParagraphSymbol);
			for (int i=0; i<m_Characters.size(); i++) {
				if (i!=m_ParagraphSymbol) m.Add(m_Characters.get(i), i);
			}
			return m;
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
		
		public Opts.ScreenOrientations GetOrientation() {
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
		 * Retrieves the background colour for a given symbol at a given offset
		 * 
		 * @param i Symbol identifier
		 * @param offset index into text buffer - used for colour cycling (according to the low bit)
		 * @return Background colour for this symbol.
		 */
		public int GetColour(int i, int offset) {
	    	int iColour = m_Colours.get(i);
	    	//ACL if sym==0, use colour 7???
			// This is provided for backwards compatibility. 
			// Colours should always be provided by the alphabet file
			if(iColour == -1) {
				if(i == m_SpaceSymbol) {
					iColour = 9;
				} /*else if(sym == ContSymbol) {
					iColour = 8;
				} */else {
					iColour = (i % 3) + 10;
				}
			}

	    	if (iColour<130 && (offset&1)==0) iColour+=130;
	    	return iColour;
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
		
		/** Index of this alphabet's paragraph symbol, or -1 for none */
		public int GetParagraphSymbol()  {
			return m_ParagraphSymbol;
		}
		
		/** Index of this alphabet's space symbol, or -1 for none */
		
		public int GetSpaceSymbol()  {
			return m_SpaceSymbol;
		}
		public SGroupInfo getBaseGroup() {return m_BaseGroup;}
		public int numChildNodes() {return iNumChildNodes;}
	}
	
	/**
	 * Sole constructor. This will parse the list of files given in Fnames
	 * by attempting both ordinary file I/O and applet-style
	 * web retrieval. Once the constructor terminates, all XML
	 * files have been read and the object is ready to be queried
	 * for alphabet names.
	 *  
	 * @param SysLoc System data location, for retrieval of DTD files. Optional; if not supplied, this location will not be considered for DTD location.
	 * @param UserLoc User data location, for retrieval of DTD files. Optional; if not supplied, this location will not be considered for DTD location.
	 * @param Fnames Filenames to parse; these may be relative or absolute.
	 * @param Interface Reference to the InterfaceBase parent class for applet-style IO. Optional; if not supplied, applet-style IO will fail.
	 */
	public CAlphIO(CDasherInterfaceBase intf) {
		super(intf);
		CreateDefault();
		
		SAXParserFactory factory = SAXParserFactory.newInstance();
		
		try {
			parser = factory.newSAXParser();
		}
		catch(Exception e) {
			System.out.printf("Error creating SAX parser: %s%n", e);
			return;
		}
		
	}
	
	private static String getLineSeparator() {
		try {
			return System.getProperty("line.separator");
		} catch (SecurityException e) {
			/* CSFS: This slightly odd route is used because the traditional method,
			 * which is to read the system property 'line.seperator' is in fact
			 * forbidden for applets! Why it's potentially dangerous to establish
			 * how to terminate lines, I'm not sure.
			 */
			return String.format("%n");
		}
	}
	
	/**
	 * Parse a given XML file for alphabets. Any resulting alphabets
	 * will be added to the internal buffer ready for retrieval
	 * using GetInfo or GetAlphabets.
	 * 
	 * @param filename File to parse
	 * @param bLoadMutable ignored
	 */
	public void ParseFile(InputStream in, final boolean bLoadMutable) throws SAXException, IOException {
		
		InputSource XMLInput = new InputSource(in);
		
		DefaultHandler handler = new DefaultHandler() {
			
			protected CAlphIO.AlphInfo currentAlph;
			protected String currentTag;
			protected SGroupInfo currentGroup;
			protected boolean bFirstGroup;
			private final Stack<SGroupInfo> groupStack = new Stack<SGroupInfo>();
			
			public void startElement(String namespaceURI, String simpleName, String qualName, Attributes tagAttributes) throws SAXException {
				
				String tagName = (simpleName.equals("") ? qualName : simpleName);
				
				if(tagName.equals("alphabet")) {
					/* A new alphabet is beginning. Find its name... */
					String name=null; String ctxEscape="Â";
				    for(int i = 0; i < tagAttributes.getLength(); i++) {
				    	String attributeName = (tagAttributes.getLocalName(i).equals("") ? tagAttributes.getQName(i) : tagAttributes.getLocalName(i));
				    	if(attributeName.equals("name")) {
				    		name=tagAttributes.getValue(i);
				    	} else if (attributeName.equals("escape")) {
				    		ctxEscape = tagAttributes.getValue(i);
				    	}
				    }
					
				    currentAlph = new AlphInfo(name);
				    if (name==null) {
						m_Interface.Message("Alphabet does not have a name, ignoring", 1);
						//subtags etc. will be recorded in the AlphInfo object with null name anyway;
						// but this will not be added to the list of available alphabets.
					} else if (ctxEscape.length()!=1) {
						m_Interface.Message("Alphabet "+name+" has invalid escape character, will not use context commands.", 1);
						currentAlph.ctxChar = null;
					} else {
						currentAlph.ctxChar = ctxEscape.charAt(0);
					}
					
				    bFirstGroup = true;
				}
				
				else if(tagName.equals("orientation")) {
					for(int i = 0; i < tagAttributes.getLength(); i++) {
						String attributeName = (tagAttributes.getLocalName(i).equals("") ? tagAttributes.getQName(i) : tagAttributes.getLocalName(i));
						if(attributeName.equals("type")) {
							ScreenOrientations spec = Opts.orientationFromString(tagAttributes.getValue(i));
							currentAlph.m_Orientation = (spec==null) ? Opts.ScreenOrientations.LEFT_TO_RIGHT : spec;
						}
					}
				}
				
				else if(tagName.equals("encoding")) {
					/*We don't actually do anything with this, so...I don't think we need it?
					 * for(int i = 0; i < tagAttributes.getLength(); i++) {
					 *
					 *	 String attributeName = (tagAttributes.getLocalName(i).equals("") ? tagAttributes.getQName(i) : tagAttributes.getLocalName(i));
					 *	 if(attributeName == "type") {
					 *		currentAlph.Encoding = StoT.get(tagAttributes.getValue(i));
					 *	 }
					 * }
					 */
				}
				
				else if(tagName.equals("palette")) {
					currentTag = "palette"; // will be handled by characters routine
				}
				
				else if(tagName.equals("train")) {
					currentTag = "train"; // Likewise
				}
				
				else if (tagName.equals("context")) {
					for (int i=0; i<tagAttributes.getLength(); i++)
						if (tagAttributes.getLocalName(i).equals("default"))
							currentAlph.m_strDefaultContext = tagAttributes.getValue(i);
				}
				else if(tagName.equals("paragraph")) {
					//note index of paragraph symbol in order to handle \n / \r\n special-casing...
					currentAlph.m_ParagraphSymbol = currentAlph.m_Characters.size();
					//Extract default text as the line separator...
					//then read as any other character
					readChar(getLineSeparator(),tagAttributes);
				}
				
				else if(tagName.equals("space")) {
					//note index of space symbol, it gets used occassionally...
					currentAlph.m_SpaceSymbol = currentAlph.m_Characters.size();
					readChar(null, tagAttributes);
				}
				
				else if(tagName.equals("control")) {
					//ACL Skip, as not implemented in Java. But we could read in display info, as follows...
					/*for(int i = 0; i < tagAttributes.getLength(); i++) {
						String attributeName = (tagAttributes.getLocalName(i).equals("") ? tagAttributes.getQName(i) : tagAttributes.getLocalName(i));
						if(attributeName == "d") {
							currentAlph.ControlCharacter.Display = tagAttributes.getValue(i);
						}
						if(attributeName == "t") {
							currentAlph.ControlCharacter.Text = tagAttributes.getValue(i);
						}
						if(attributeName == "b") {
							currentAlph.ControlCharacter.Colour = Integer.parseInt(tagAttributes.getValue(i));
						}
						if(attributeName == "f") {
							currentAlph.ControlCharacter.Foreground = tagAttributes.getValue(i);
						}
					}*/
				}
				
				else if(tagName.equals("convert")) {
					//ACL Skip, as not implemented in Java. But we could read in display info, as follows...
					/*for(int i = 0; i < tagAttributes.getLength(); i++) {
						String attributeName = (tagAttributes.getLocalName(i).equals("") ? tagAttributes.getQName(i) : tagAttributes.getLocalName(i));
						if(attributeName == "d") {
							currentAlph.StartConvertCharacter.Display = tagAttributes.getValue(i);
						}
						if(attributeName == "t") {
							currentAlph.StartConvertCharacter.Text = tagAttributes.getValue(i);
						}
						if(attributeName == "b") {
							currentAlph.StartConvertCharacter.Colour = Integer.parseInt(tagAttributes.getValue(i));
						}
						if(attributeName == "f") {
							currentAlph.StartConvertCharacter.Foreground = tagAttributes.getValue(i);
						}
					}*/
				}
				
				else if(tagName.equals("protect")) {
					//ACL Skip, as (conversion) not yet implemented in Java. But we could read in display info, as follows...
					/*for(int i = 0; i < tagAttributes.getLength(); i++) {
						String attributeName = (tagAttributes.getLocalName(i).equals("") ? tagAttributes.getQName(i) : tagAttributes.getLocalName(i));
						if(attributeName == "d") {
							currentAlph.EndConvertCharacter.Display = tagAttributes.getValue(i);
						}
						if(attributeName == "t") {
							currentAlph.EndConvertCharacter.Text = tagAttributes.getValue(i);
						}
						if(attributeName == "b") {
							currentAlph.EndConvertCharacter.Colour = Integer.parseInt(tagAttributes.getValue(i));
						}
						if(attributeName == "f") {
							currentAlph.EndConvertCharacter.Foreground = tagAttributes.getValue(i);
						}
					}*/
				}
				
				else if(tagName.equals("group")) {
					String label=""; boolean visible=!bFirstGroup; int col=0;
					bFirstGroup=false;
					
					for(int i = 0; i < tagAttributes.getLength(); i++) {
						String attributeName = (tagAttributes.getLocalName(i).equals("") ? tagAttributes.getQName(i) : tagAttributes.getLocalName(i));
						if(attributeName.equals("b")) {
							col = Integer.parseInt(tagAttributes.getValue(i));
						}
						if(attributeName.equals("visible")) {
							if(tagAttributes.getValue(i).equals("yes") || tagAttributes.getValue(i).equals("on")) {
								visible = true;
							}
							else if(tagAttributes.getValue(i).equals("no") || tagAttributes.getValue(i).equals("off")) {						
								visible = false;
							}
						}
						if(attributeName.equals("label")) {
							label = tagAttributes.getValue(i);
						}
					}
					
					SGroupInfo currentGroup = new SGroupInfo(label,col,visible);
					
				    currentGroup.iStart = numOrderedCharacters(currentAlph);

				    if(!groupStack.isEmpty()) {
				    	SGroupInfo parent = groupStack.peek();
				    	currentGroup.Next = parent.Child;
				    	parent.Child = currentGroup;
				    	parent.iNumChildNodes++;
				    } else {
				      currentGroup.Next = currentAlph.m_BaseGroup;
				      currentAlph.m_BaseGroup = currentGroup;
				      currentAlph.iNumChildNodes++;
				    }
				    
				    groupStack.push(currentGroup);
				}
						
				else if(tagName.equals("s")) {
					readChar(null, tagAttributes);
				}
			}
			
			private void readChar(String text,Attributes tagAttributes) {
				String display=null; //default: use text
				int bgcol=-1, fgcol=4;
				
				for(int i = 0; i < tagAttributes.getLength(); i++) {
					String attributeName = (tagAttributes.getLocalName(i).equals("") ? tagAttributes.getQName(i) : tagAttributes.getLocalName(i));
					if (attributeName.length()>1) continue;
					switch (attributeName.charAt(0)) {
					case 'd':
						display = tagAttributes.getValue(i);
						break;
					case 't':
						if (text==null) {
							text = tagAttributes.getValue(i);
							if (text.codePointCount(0, text.length())!=1) {
								m_Interface.Message("Illegal character \""+text+"\" - should be exactly one unicode char. Skipping...", 1);
								return;
							}
						}
						else m_Interface.Message("Unnecessary or duplicate text for character '"+text+"'", 1);
						break;
					case 'b':
						bgcol = Integer.parseInt(tagAttributes.getValue(i));
						break;
					case 'f':
						fgcol = Integer.parseInt(tagAttributes.getValue(i));
						break;
					}
				}
				currentAlph.m_Characters.add(text);
				currentAlph.m_Display.add(display == null ? text : display);
				currentAlph.m_Colours.add(bgcol);
				currentAlph.m_Foreground.add(fgcol);
				if (groupStack.isEmpty())
					currentAlph.iNumChildNodes++;
				else
					groupStack.peek().iNumChildNodes++;
				
			}
			
			public void endElement(String namespaceURI, String simpleName, String qualName) {
				String tagName = (simpleName.equals("") ? qualName : simpleName);
				
				if(tagName.equals("alphabet")) {
					if (currentAlph.name == null) return; //we warned at start, now drop it.
					//alphabet finished, i.e. all read in. Groups will have been stored in linked-list
					// in reverse order to how they were in the file, so put them the right way round...
					currentAlph.m_BaseGroup = Reverse(currentAlph.m_BaseGroup);
					//Also to match old behaviour of Dasher, we put the paragraph & space symbols at the end
					// (many alphabet files put paragraph/space tags at beginning of alphabet when this is not wanted.
					// TODO, a better solution would be to fix the alphabet files, putting space/para last for the
					// (presumed majority of) users who want that, so that people who do actually want space
					// or paragraph anywhere else, could then do so...)
					if (currentAlph.m_ParagraphSymbol!=CAlphabetMap.UNDEFINED) {
						//if space symbol is after paragraph, it's about to be moved one earlier
						if (currentAlph.m_SpaceSymbol > currentAlph.m_ParagraphSymbol) currentAlph.m_SpaceSymbol--;
						currentAlph.m_ParagraphSymbol=moveCharToEnd(currentAlph,currentAlph.m_ParagraphSymbol);
					}
					if (currentAlph.m_SpaceSymbol!=CAlphabetMap.UNDEFINED) {
						//if paragraph symbol is (now) after space (which it will be if it was defined, because of above),
						// it'll get moved one earlier...
						if (currentAlph.m_ParagraphSymbol > currentAlph.m_SpaceSymbol) currentAlph.m_ParagraphSymbol--;
						currentAlph.m_SpaceSymbol = moveCharToEnd(currentAlph,currentAlph.m_SpaceSymbol);
					}
					Alphabets.put(currentAlph.name, currentAlph);
				}
				
				else if(tagName.equals("palette")) {
					currentTag = "";
				}
				
				else if(tagName.equals("train")) {
					currentTag = "";
				}
				// Both of these are to prevent the parser from dumping unwanted CDATA
				// once the tags we're interested in have been closed.

				else if(tagName.equals("group")) {
					SGroupInfo finish = groupStack.pop();
					finish.iEnd = numOrderedCharacters(currentAlph);
					finish.Child = Reverse(finish.Child);
				}
				
			}
			
			public void characters(char[] chars, int start, int length) throws SAXException {
				//pointer comparison ok, currentTag was set to a coded-in constant, so intern()d automatically.
				if(currentTag == "palette") {
					currentAlph.m_DefaultPalette = new String(chars, start, length);
				}
				
				if(currentTag == "train") {
					currentAlph.m_TrainingFile = new String(chars, start, length);
				}
				
			}

			public InputSource resolveEntity(String publicName, String systemName) throws IOException, SAXException {
				/* This is here because SAX will by default look in a system location first,
				 * which throws a security exception when running as an Applet.
				 */
				return systemName.contains("alphabet.dtd") ? getStream("alphabet.dtd") : null;
			}
			
		};
		// Pass in the Alphabet HashMap so it can be modified

		parser.parse(XMLInput, handler);
	}
	
	private static int moveCharToEnd(AlphInfo alph,int pos) {
		alph.m_Characters.add(alph.m_Characters.get(pos)); alph.m_Characters.remove(pos);
		alph.m_Colours.add(alph.m_Colours.get(pos)); alph.m_Colours.remove(pos);
		alph.m_Foreground.add(alph.m_Foreground.get(pos)); alph.m_Foreground.remove(pos);
		alph.m_Display.add(alph.m_Display.get(pos)); alph.m_Display.remove(pos);
		return alph.m_Characters.size()-1;
	}
	
	/** Returns the number of characters in the specified alphabet which will stay
	 * in their current order when the alphabet is complete
	 * (paragraph and space will not, as they will be moved to the end, past any
	 * other characters entered since or not-yet-). Thus, this is the size (one more
	 * than index) of the list of characters read so far, excluding space and para.
	 * @param alph AlphInfo in process of being built (i.e. still having characters added,
	 * so space and paragraph not yet moved to end)
	 * @return number of non-space, non-paragraph, characters so far in alphabet. 
	 */
	private static int numOrderedCharacters(AlphInfo alph) {
		int i = alph.m_Characters.size();
		if (alph.m_SpaceSymbol!=CAlphabetMap.UNDEFINED) i--;
		if (alph.m_ParagraphSymbol!=CAlphabetMap.UNDEFINED) i--;
		return i;
	}
	
	/**
	 * Fills the passed Collection with the names of all available alphabets.
	 * 
	 * @param AlphabetList Collection to be filled. 
	 */
	public void GetAlphabets(java.util.Collection<String> AlphabetList) {
		
		/* CSFS: Changed from a C++ listIterator */
		
		AlphabetList.clear();
		for(Map.Entry<String, AlphInfo> m : Alphabets.entrySet()) {
			AlphabetList.add(m.getValue().name);
		}
	}
	
	/**
	 * Retrieves the name of the default alphabet. At present this
	 * will return English with limited punctuation if available,
	 * or Default if not.
	 * 
	 * @return Name of a reasonable default alphabet.
	 */
	public String GetDefault() {
		if(Alphabets.containsKey("English with limited punctuation")) {
			return ("English with limited punctuation");
		}
		else {
			return ("Default");
		}
	}
	
	/**
	 * Returns an AlphInfo object representing the alphabet with
	 * a given name. In the event that it could not be found,
	 * the Default alphabet is returned instead. To ensure that
	 * this is not the case, check the available alphabets first
	 * using GetAlphabets().
	 * 
	 * @param AlphID Name of the alphabet to be retrieved.
	 * @return Either the asked alphabet, or the default.
	 */
	public AlphInfo GetInfo(String AlphID) {
		if(Alphabets.containsKey(AlphID)) {
			// if we have the alphabet they ask for, return it
			return Alphabets.get(AlphID);
		}
		else {
			// otherwise, give them default - it's better than nothing
			return Alphabets.get("Default");
		}
	}
	
	/**
	 * Creates the default alphabet and stores as an available
	 * alphabet. This will be returned in the case that a requested
	 * alphabet cannot be retrieved; at present it is essentially
	 * lower-case english with no punctuation or numerals.
	 * <p>
	 * The constructor calls this method prior to attempting
	 * to read XML files; it should not need to be called
	 * more than once unless the Default is deleted.
	 *
	 */
	protected void CreateDefault() {
		// TODO I appreciate these strings should probably be in a resource file.
		// Not urgent though as this is not intended to be used. It's just a
		// last ditch effort in case file I/O totally fails.
		AlphInfo Default = new AlphInfo("Default");
		//Default.Type = Opts.AlphabetTypes.Western;
		//Is the default already: Default.m_Orientation = Opts.ScreenOrientations.LEFT_TO_RIGHT;
		
		Default.m_TrainingFile = "training_english_GB.txt";
		Default.m_GameModeFile = "gamemode_english_GB.txt";
		Default.m_DefaultPalette = "Default";
		
		String Chars = "abcdefghijklmnopqrstuvwxyz";
		
		Default.m_BaseGroup = null;
		
		for(Character c : Chars.toCharArray()) {
			Default.m_Characters.add(c.toString());
			Default.m_Display.add(c.toString());
			Default.m_Colours.add(-1); //ACL modified, was 10????
			Default.m_Foreground.add(4);
		}
		
		//paragraph
		Default.m_ParagraphSymbol = Default.m_Characters.size();
		Default.m_Characters.add(getLineSeparator());
		Default.m_Display.add("¶");
		Default.m_Colours.add(9); //ACL modified, was unspecified (=> -1)
		Default.m_Foreground.add(4);
		//space
		Default.m_SpaceSymbol = Default.m_Characters.size();
		Default.m_Characters.add(" ");
		Default.m_Display.add("_");
		Default.m_Colours.add(9);
		Default.m_Foreground.add(4);
		
		Alphabets.put("Default", Default);
	}
	
	private static SGroupInfo Reverse(SGroupInfo s) {
		SGroupInfo prev=null;
		while (s!=null) {
			SGroupInfo next = s.Next;
			s.Next=prev;
			prev = s;
			s=next;
		}
		return prev;
	}
	
}

