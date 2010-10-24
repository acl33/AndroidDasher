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

	/** Symbol value of the paragraph symbol, to which both "\r\n" and "\n" are mapped.
	 * (if {@link #UNDEFINED}, no special mapping for "\r\n" is applied.)
	 */
	private int m_ParagraphSymbol=UNDEFINED;
	
	/**
	 *  Map from unicode "code point" (a 32-bit int representing a single output text character),
	 * to Dasher's internal symbol number, but <emph>only</emph> for characters outside the range 0-255.
	 * @see #singleChars
	 */
	private final HashMap<Integer, Integer> multiChars = new HashMap<Integer, Integer>();
	
	/**
	 * Map to symbol number from output text, for symbols whose unicode value is <=255.
	 * @see #multiChars
	 */
	private int[] singleChars = new int[256];
	
	public CAlphabetMap() {
		Arrays.fill(singleChars, UNDEFINED);
	}
	
	public void AddParagraphSymbol(int value) {
		if (m_ParagraphSymbol!=UNDEFINED)
			throw new IllegalStateException("Paragraph symbol already defined as "+m_ParagraphSymbol);
		if (singleChars['\r']!=UNDEFINED) throw new IllegalStateException("Can't add paragraph symbol, \r already defined");
		if (singleChars['\n']!=UNDEFINED) throw new IllegalStateException("Can't add paragraph symbol, \n already defined");
		singleChars['\n']=m_ParagraphSymbol=value;
	}
	/**
	 * Adds a symbol to the map.
	 * <p>Note it is possible for the same value to be present under multiple keys,
	 * in which case any occurrence of any of those keys in the input, will be mapped
	 * to the same value (symbol number). However, the expected common-case of the
	 * paragraph symbol, should <em>not</em> be handled this way - it should be added
	 * via {@link AddParagraphSymbol}, so that occurrences of both <code>"\n"</code>
	 * and <code>"\r\n"</code> will be mapped to the appropriate value.
	 * @param key the text for the symbol - a single unicode character (possibly two Java "char"s)
	 * @param value Internal-to-Dasher number of the symbol
	 * @throws IllegalArgumentException if <code>key</code> is not a single unicode character,
	 * if <code>value&lt;0</code>, or if there is another symbol with the same text. 
	 */
	public void Add(String key, int value) {
		if (key.codePointCount(0, key.length())!=1) throw new IllegalArgumentException("Not a single character "+key);
		if (value<0) throw new IllegalArgumentException("Illegal symbol number "+value + " for display text "+key);
		if (key.charAt(0)<256) {
			assert (key.length()==1);
			if (singleChars[key.charAt(0)]!=UNDEFINED) throw new IllegalArgumentException("Char "+key.charAt(0)+" already mapped to symbol number "+singleChars[key.charAt(0)]);
			if (key.charAt(0)=='\r' && m_ParagraphSymbol!=UNDEFINED) throw new IllegalArgumentException("Can't define \r if paragraph symbol exists");
			singleChars[key.charAt(0)]=value;
		} else {
			assert (key.length()==1 && !Character.isHighSurrogate(key.charAt(0)) && !Character.isLowSurrogate(key.charAt(0)))
				|| (key.length()==2 && Character.isSurrogatePair(key.charAt(0), key.charAt(1)));
			int codePoint=key.codePointAt(0);
			if (multiChars.get(codePoint)!=null)
				throw new IllegalArgumentException("Key \""+key+"\" already mapped to symbol number "+multiChars.get(codePoint));
			multiChars.put(codePoint, value);
		}
	}
	
	/**
	 * Converts a string of text into a list of symbol indentifiers.
	 * 
	 * @param Symbols Collection to be filled with symbol identifiers.
	 * @param Input String to be converted.
	 */	
	public void GetSymbols(Collection<Integer> Symbols, String input) {
		for (int nextIdx=0; nextIdx<input.length(); nextIdx++) {
			char c = input.charAt(nextIdx);
			int codePoint;
			if (Character.isHighSurrogate(c)) {
				if (nextIdx+1 < input.length() && Character.isLowSurrogate(input.charAt(nextIdx+1))) {
					codePoint = input.codePointAt(nextIdx);
					//and fallthrough to multiChars lookup
				} else {
					System.err.println("High surrogate "+c+" not followed by low surrogate, skipping");
					continue;
				}
			} else if (m_ParagraphSymbol!=UNDEFINED && c=='\r') {
				if (nextIdx+1 < input.length() && input.charAt(nextIdx+1)=='\n') {
					Symbols.add(m_ParagraphSymbol);
					nextIdx++; //skip \n
				} else {
					System.err.println("Carriage return not followed by newline, skipping");
				}
				continue;
			} else if (c<256) {
				Symbols.add(singleChars[c]);
				continue;
			} else codePoint=c;
			Integer i = multiChars.get(codePoint);
			Symbols.add(i==null ? UNDEFINED : i);
		}
	}
	
	public ListIterator<Integer> GetSymbols(final ListIterator<Character> previousChars) {
		return new ListIterator<Integer>() {
			/** This'll give a false positive iff we have a bad unicode encoding underneath. */
			public boolean hasPrevious() {return previousChars.hasPrevious();}
			public Integer previous() {
				char prev = previousChars.previous();
				if (Character.isLowSurrogate(prev)) {
					if (previousChars.hasPrevious()) { 
						char p=previousChars.previous();
						if (Character.isHighSurrogate(p)) {
							Integer i = multiChars.get(Character.toCodePoint(p,prev));
							return (i==null) ? UNDEFINED : i;
						}
						previousChars.next(); //push it back
					}
					System.err.println("Ignoring low surrogate "+prev+" as not preceded by high surrogate");
					return previous();
				} else if (m_ParagraphSymbol!=UNDEFINED && prev=='\n') {
					if (previousChars.hasPrevious())
						if (previousChars.previous()!='\r') previousChars.next(); //no, don't take
					return m_ParagraphSymbol;
				}
				if (prev<256) return singleChars[prev];
				Integer i=multiChars.get((char)prev);
				return (i==null) ? UNDEFINED : i;
			}
			public int previousIndex() {throw new UnsupportedOperationException("Symbol index");}
			/** This'll give a false positive iff we have a bad unicode encoding underneath. */
			public boolean hasNext() {return previousChars.hasNext();}
			public Integer next() {
				char next = previousChars.next();
				if (Character.isHighSurrogate(next)) {
					if (previousChars.hasNext()) {
						char l = previousChars.next();
						if (Character.isLowSurrogate(l)) {
							Integer i = multiChars.get(Character.toCodePoint(next, l));
							return (i==null) ? UNDEFINED : i;
						}
						previousChars.previous(); //push back
					}
					System.err.println("Ignoring high surrogate "+next+" as not followed by low surrogate");
					return next();
				} else if (m_ParagraphSymbol!=UNDEFINED && next=='\r') {
					if (previousChars.hasNext())
						if (previousChars.next()=='\n') return m_ParagraphSymbol;
						else previousChars.previous(); //untake
					//fall through, i.e. attempt to find a symbol for just '\r'
				}
				if (next<256) return singleChars[next];
				Integer i = multiChars.get(next);
				return (i==null) ? UNDEFINED : i;
			}
			public int nextIndex() {throw new UnsupportedOperationException("Symbol index");}
			public void add(Integer i) {throw new UnsupportedOperationException("Immutable");}
			public void remove() {throw new UnsupportedOperationException("Immutable");}
			public void set(Integer i) {throw new UnsupportedOperationException("Immutable");}
		};
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
		Reader chars = new BufferedReader(new InputStreamReader(count)); //buffer just for performance
		C trainContext = model.EmptyContext();
		
		try {
			outer: while (true) {
				int c=chars.read();
				int sym;
				while (true) {
					//continues come here, i.e. check for -1
					if (c==-1) break outer; 
					if (Character.isHighSurrogate((char)c)) {
						int n=chars.read();
						if (Character.isLowSurrogate((char)n)) {
							sym=Character.toCodePoint((char)c,(char)n);
							break;
						} else {
							System.err.println("Skipping high surrogate char "+c+" as followed by "+n+" which is not low surrogate");
							c=n;
							continue;
						}
					} else if (c=='\r' && m_ParagraphSymbol!=UNDEFINED) {
						int n=chars.read();
						if (n=='\n') {
							sym=m_ParagraphSymbol;
							break;
						} else {
							System.err.println("Skipping \r as followed by "+n+" which is not \n");
							c=n;
							continue;
						}
					} else {
						if (c<256) sym=singleChars[c];
						else {
							Integer i=multiChars.get(c);
							sym=(i==null) ? UNDEFINED : i;
						}
						break;
					}
				}
				//breaks come here, with sym defined. As per C++ Dasher, we just ignore symbols not in the alphabet...
				if (sym!=CAlphabetMap.UNDEFINED) {
					trainContext = model.ContextLearningSymbol(trainContext, sym);
					if (evt!=null) {
						int iNPercent = (count.iTotalRead *100)/iTotalBytes;
						if (iNPercent != evt.m_iPercent) {
							evt.m_iPercent = iNPercent;
							model.InsertEvent(evt);
						}
					}
				}
			}
		} catch (EOFException e) {
			//that's fine!
		}
		return count.iTotalRead;
	}
}
