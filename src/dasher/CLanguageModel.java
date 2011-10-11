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

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * LanguageModels are responsible for predicting / guessing the probabilities
 * associated with members of a given alphabet in a given context.
 * <p>
 * They have no internal state except for knowledge of the
 * alphabet in which they make predictions; 
 * rather they produce, modify and destroy Context objects
 * (subclasses of CContextBase) which represent the context in which
 * they were working. This enables the impression of multiple
 * diverging models, when in fact the model is only capable of
 * answering one question: "What comes next in this context?"
 * <p>
 * Models can adapt themselves in response to user input, and should
 * do so if they are capable when LearnSymbol is called. Calls
 * to EnterSymbol on the other hand should modify their context
 * without altering the model.
 * 
 */
public abstract class CLanguageModel<C> extends CDasherComponent {

	/**
	 * Alphabet in which we make predictions
	 */
	protected final CAlphIO.AlphInfo m_Alphabet;
	
	/**
	 * Creates a LanguageModel working in a given alphabet.
	 * 
	 * @param EventHandler Event handler with which to register ourselves
	 * @param SettingsStore Settings repository to use
	 * @param Alphabet Alphabet to work in
	 */
	
	public CLanguageModel(CDasherComponent creator, CAlphIO.AlphInfo Alphabet) {
	  super(creator);
	  m_Alphabet = Alphabet;
	}

	/**
	 * Indicates whether this is a remote/asynchronous model
	 * with respect to returning probabilities.
	 * 
	 * @return True if asynchronous, false if not
	 */
	public boolean isRemote() {
		return false;
	}
	
	/////////////////////////////////////////////////////////////////////////////
	// Context creation/destruction
	////////////////////////////////////////////////////////////////////////////

	/* CPPMLanguageModel and others: These were using a 
	 * horrible hack wherein an integer (really a CPPMContext * ) 
	 * was being used to represent the context of a given node when 
	 * outside the generating class, in order that it could be
	 * swapped out for some other CLanguageModel derivation and retain 
	 * type-compatibility at the expense of being entirely type-unsafe. 
	 * Since Java doesn't like to be type unsafe, I've replaced this by 
	 * all Context-representing classes being a child of CContextBase, which 
	 * has no members or methods, thus retaining type-safety. */
	
	/**
	 * Gets an empty context
	 * 
	 * @return Context object representing the empty context (no preceding characters)
	 */
	public abstract C EmptyContext();

	/**
	 * Turns a context back into a list of symbols.
	 * @param ctx Context to serialize
	 * @param into will be filled with the symbols which, if passed to {@link #BuildContext}
	 * (or entered in turn using {@link #ContextWithSymbol}), would result in that context. 
	 */
	public abstract void ContextToSymbols(C ctx, List<Integer> into);
	/////////////////////////////////////////////////////////////////////////////
	// Context modifiers
	////////////////////////////////////////////////////////////////////////////

	/**
	 * Modifies the supplied context, appending a given symbol.
	 * <p>
	 * The model should not alter its predictions based upon this
	 * entry.
	 * @param context Context to modify
	 * @param Symbol Symbol to enter
	 */
	public abstract C ContextWithSymbol(C ctxIn, int Symbol);

	/**
	 * Modifies the supplied context, appending a given symbol.
	 * <p>
	 * The model should learn from this if it can in order to make
	 * better predictions in future.
	 * <p>
	 * If the model is not capable of learning, this should behave
	 * precisely as EnterSymbol.
	 * 
	 * @param context Context to modify
	 * @param Symbol Symbol to enter
	 */
	public abstract C ContextLearningSymbol(C ctxIn, int Symbol);
	
	/**
	 * Attempt to "unlearn" an occurrence of a symbol in a context.
	 * The default implementation fails to unlearn, so just returns false
	 * @param parentCtx Context in which symbol occurred
	 * @param Symbol symbol to unlearn (in parentCtx)
	 * @param childCtx context returned from {@link #ContextLearningSymbol} when the symbol was learned
	 * @return true if the symbol was unlearned; false if couldn't.
	 */
	public boolean UnlearnChild(C parentCtx, int Symbol, C childCtx) {
		return false;
	}

	/////////////////////////////////////////////////////////////////////////////
	// Prediction
	/////////////////////////////////////////////////////////////////////////////

	/**
	 * Fills an array with probabilities of each symbol coming next.
	 * <p>
	 * Element 0 will be left untouched; to element 1 will be added
	 * the probability of the first symbol (sym=0); and so on.
	 * <p>
	 * @param ctx Context in which to make predictions, i.e. represents
	 * the text preceding the symbol we are trying to predict
	 * @param probs Array to fill. Must have at least
	 * <code>getAlphabet().getNumberSymbols()+1</code>
	 * elements; any excess elements will be left untouched.
	 * @param iNorm denominator or normalisation value: the
	 * generated probabilities will sum to this.
	 */
	public abstract void GetProbs(C ctx, long[] probs, long iNorm);

	/** Get some measure of the memory usage for diagnostic
	 * purposes. No need to implement this if you're not comparing
	 * language models. The exact meaning of the result will
	 * depend on the implementation (for example, could be the
	 * number of nodes in a trie, or the physical memory usage).
	 * 
	 * @return Memory usage in bytes, or 0 if unknown or we don't care
	 */
	public abstract int GetMemory();

	/**
	 * Gets our working alphabet
	 * 
	 * @return m_Alphabet
	 */
	public CAlphIO.AlphInfo getAlphabet() {
	    return m_Alphabet;
	}

	/** Build a LM context from an iterator of symbols
	 * @param previousSyms Iterator returning symbols in <em>backwards</em> order
	 * (i.e. the first call to <code>next()</code> returns the most recent symbol)
	 * @return
	 */
	public final C BuildContext(Iterator<Integer> previousSyms) {
		return BuildContext(previousSyms,0);
	}
	
	protected C BuildContext(Iterator<Integer> previousSyms, int countSoFar) {
		if (previousSyms.hasNext()) {
			int sym = previousSyms.next();
			if (sym!=CAlphabetMap.UNDEFINED)
				return ContextWithSymbol(BuildContext(previousSyms,countSoFar+1),sym);
		}
		return EmptyContext();
	}
	
}
