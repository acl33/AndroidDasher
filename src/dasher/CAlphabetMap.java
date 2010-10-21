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

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.ListIterator;

/**
 * Map from the textual representation of alphabet symbols
 * (which can be multiple characters!) to symbol numbers.
 */
public class CAlphabetMap {
	public static final int UNDEFINED = -1;

	/** Value used (internally) to indicate that the text/etc. is a prefix of the
	 * string for a symbol (or more than one) but does not make up a symbol itself. */
	private static final int PREFIX = -2;
	
	/** Map to symbol number from string representing display text,
	 * but <emph>only</emph> for symbols whose display text is more than
	 * one character long. Note that all prefixes, of strings that are symbols,
	 * are also stored in the map with value {@link #PREFIX}.
	 * @see #singleChars
	 */
	private final HashMap<String, Integer> multiChars = new HashMap<String, Integer>();
	
	/**
	 * Map to symbol number from display text, but <emph>only</emph> for
	 * symbols whose display text is a single character. (However prefixes
	 * of multicharacter symbols are also stored, with value {@link #PREFIX}).
	 */
	private int[] singleChars = new int[0];
	
	/**
	 * Adds a symbol to the map
	 * @param key the text for the symbol
	 * @param value unique code of the symbol
	 * @throws IllegalArgumentException if <code>value&lt;0</code>, if there is another symbol
	 * with the same text, or if another symbol is a prefix of this one or vice versa.
	 */
	public void Add(String key, int value) {
		if (key.length()==1) {Add(key.charAt(0), value); return;}
		if (value<0) throw new IllegalArgumentException("Illegal symbol number "+value + " for display text "+key);
		if (multiChars.containsKey(key))
			throw new IllegalArgumentException(
					(multiChars.get(key)==PREFIX) ? "symbol "+value+" with display text "+key+" is prefix of another symbol"
							: "symbols "+value+" and "+multiChars.get(key)+" share display text "+key);
		StringBuilder sb = new StringBuilder(key);
		for (;;) {
			sb.deleteCharAt(sb.length()-1);
			if (sb.length()==1) break;
			Integer existing=multiChars.get(sb.toString());
			if (existing!=null && existing.intValue() != PREFIX)
				throw new IllegalArgumentException("symbol "+value+" with display text "+key+" is suffix of symbol "+existing+" with displaytext "+sb);
			multiChars.put(sb.toString(), PREFIX);
		}
		char first = sb.charAt(0);
		if (singleChars.length>first && singleChars[first]>=0)
			throw new IllegalArgumentException("symbol "+value+" with display text "+key+" is suffix of symbol "+singleChars[first]+" with displaytext "+first);
		multiChars.put(key,value);
	}

	/**
	 * Adds a symbol to the map
	 * @param key the text for the symbol
	 * @param value unique code of the symbol
	 * @throws IllegalArgumentException if <code>value&lt;0</code>, if there is another symbol
	 * with the same text, or if this symbol is a prefix of another symbol added previously.
	 */
	public void Add(char key,int value) {
		if (value<0) throw new IllegalArgumentException("Illegal symbol number "+value + " for display text "+key);
		if (key >= singleChars.length) {
			int[] old = singleChars;
			singleChars = new int[Math.max(old.length*2+1, key+1)];
			System.arraycopy(old, 0, singleChars, 0, old.length);
			Arrays.fill(singleChars, old.length, singleChars.length, UNDEFINED);
		} else if (singleChars[key]!=UNDEFINED)
			throw new IllegalArgumentException(
					(singleChars[key]==PREFIX) ? "Symbol "+value+" with display text "+key+" is prefix of anothr symbol"
							: "Symbols "+value+" and "+singleChars[key]+" share display text "+key
					);
		singleChars[key]=value;
	}
	
	private int get(String s) {
		if (s.length()==1) {
			char c=s.charAt(0);
			return (c < singleChars.length) ? singleChars[c] : UNDEFINED;
		}
		Integer i = multiChars.get(s);
		return (i==null) ? UNDEFINED : i;
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
				Symbols.add(GetNext(rdr));
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
			public Integer previous() {return GetSingleChar(previousChars.previous());}
			public int previousIndex() {return previousChars.previousIndex();}
			public boolean hasNext() {return previousChars.hasNext();}
			public Integer next() {return GetSingleChar(previousChars.next());}
			public int nextIndex() {return previousChars.nextIndex();}
			public void add(Integer i) {throw new UnsupportedOperationException("Immutable");}
			public void remove() {throw new UnsupportedOperationException("Immutable");}
			public void set(Integer i) {throw new UnsupportedOperationException("Immutable");}
		};
	}
	/**
	 * Retrieves the next symbol in a stream of characters. Characters are read
	 * from the stream until a complete representation of a symbol is obtained,
	 * tho this may require skipping over earlier characters which are not part
	 * of said symbol.
	 * 
	 * <p>TODO: Note that if a symbol has text 'abcdef' and another 'bcd' (not
	 * a prefix!), and the stream contains 'abcdx'
	 * 
	 * 
	 * @param Key String to look up
	 * @return SSymbol containing the integer index of the symbol, or Undefined if the supplied String did not correspond to any symbol.
	 */	
	public int GetNext(Reader rdr) throws IOException {
		int c;
		while (true) {
			c = rdr.read();
			if (c==-1) throw new EOFException();
			if (c < singleChars.length) {
				int sym = singleChars[c];
				if (sym>=0) return sym;
				else if (sym==PREFIX) break;
			}
		}
		//not a symbol itself, but is a prefix of something else.
		//Slow-case string/character handling, as it's easier and this shouldn't happen often.
		StringBuilder sb=new StringBuilder();
		sb.append((char)c);
		while (true) {
			if ((c=rdr.read())==-1) throw new EOFException();
			sb.append((char)c);
			//now chop 0 or more characters off the beginning until
			// we have something that's (a prefix of) a symbol
			int sym;
			chop: while ((sym=get(sb.toString()))==UNDEFINED && sb.length()>0) {
				sb.delete(0,1);
				//look for symbols whose text we've already matched...
				// (e.g. if there are symbols 'abcdef' and 'bcd' and text was 'abcd',
				// a prefix of 'abcdef', and then we read an 'x',
				// 'abcdx' is not a prefix; but chopping off the 'a' leads to 'bcdx',
				// which is a symbol and should be returned.)
				for (int i=sb.length(); (i--)>1;)
					if ((sym=get(sb.substring(0,i)))!=UNDEFINED) {
						//first 'i' characters of sb are symbol. push back the rest...
						if (!(rdr instanceof PushbackReader))
							rdr = new PushbackReader(rdr);
						((PushbackReader)rdr).unread(sb.toString().toCharArray(), i, sb.length());
						break chop; //and return the symbol
					}
			}
			if (sym>0) return sym;
		}
	}
	
	private static final int CHAR_MASK = (1<<Character.SIZE)-1;
	public int GetSingleChar(char c) {
		if (!multiChars.isEmpty()) throw new RuntimeException("Not Yet Implemented");
		return singleChars[(int)c & CHAR_MASK];
	}
	
	
	/**
	 * Trains the language model from a given InputStream, which
	 * must be UTF-8 encoded.
	 * <p>
	 * LockEvents will be inserted every 1KB of data read, informing
	 * components and the interface of the progress made in reading
	 * the file.
	 * 
	 * @param FileIn InputStream from which to read.
	 * @param iTotalBytes Number of bytes to read.
	 * @param iOffset Offset at which to start reading.
	 * @return Number of bytes read
	 * @throws IOException 
	 */	
	public <C> int TrainStream(CLanguageModel<C> model, InputStream FileIn, int iTotalBytes, int iOffset, CLockEvent evt) throws IOException {
		
		class CountStream extends InputStream {
			/*package*/ int iTotalRead;
			private final InputStream in;
			CountStream(InputStream in, int iStartBytes) {this.in=in; this.iTotalRead=iStartBytes;}
			@Override public int available() throws IOException {return in.available();}
			@Override public int read() throws IOException {
				int res = in.read();
				if (res != -1) iTotalRead++;
				return res;
			}
			@Override public int read(byte[] buf) throws IOException {return read(buf,0,buf.length);}
			@Override public int read(byte[] buf, int start, int len) throws IOException {
				int res = in.read(buf,start,len);
				if (res>0) iTotalRead+=res; //-1 = EOF
				return res;
			}
			@Override public long skip(long n) throws IOException {//should never be called?
				long res=super.skip(n);
				if (res>0) iTotalRead+=res;
				return res;
			}
		};
		CountStream count = new CountStream(FileIn, iOffset);
		Reader chars = new BufferedReader(new InputStreamReader(count));
		C trainContext = model.EmptyContext();
		
		try {
			while (true) {
				int sym = GetNext(chars);
				trainContext = model.ContextLearningSymbol(trainContext, sym);
				if (evt!=null) {
					int iNPercent = (count.iTotalRead *100)/iTotalBytes;
					if (iNPercent != evt.m_iPercent) {
						evt.m_iPercent = iNPercent;
						model.InsertEvent(evt);
					}
				}
			}
		} catch (EOFException e) {
			//that's fine!
		}
		return count.iTotalRead;
		
	}
}
