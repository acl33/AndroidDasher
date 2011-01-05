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

import java.util.ListIterator;

/**
 * Subclass of LanguageModel which implements Prediction by
 * Partial Match. For information on the algorithm and its 
 * implementation, see (http://www.compression-links.info/PPM).
 * <p>
 * For the general contract obeyed by LanguageModel methods, see
 * the documentation of CLanguageModel.
 */
public class CPPMLanguageModel extends CLanguageModel<CPPMLanguageModel.CPPMnode> {

	public CPPMnode m_Root;

	public int m_iMaxOrder;
	
	public int NodesAllocated;

	public boolean bUpdateExclusion;

	private long lpAlpha;
	private long lpBeta;

	/**
	 * Node in PPM's prediction trie.
	 * 
	 * @see CPPMLanguageModel
	 */
	class CPPMnode {
		public CPPMnode child;
		public CPPMnode next;
		public final CPPMnode vine;
		public short count;
		public final int symbol;

		/* CSFS: Found that the C++ code used a short
		 * to represent a symbol in certain places and an
		 * int in others. As such, I've changed it to int
		 * everywhere. This ought to cause no trouble
		 * except in the case that behaviour on overflow
		 * is relied upon.
		 */

		public CPPMnode(int symbol, CPPMnode vine) {
			count = 1;
			this.symbol = symbol;
			this.vine = vine;
		}

		public CPPMnode(int sym, CPPMnode parent, CPPMnode vine) {
			this(sym,vine);
			if (vine == null) throw new IllegalArgumentException("Non-root node must have non-null vine");
			next = parent.child;
			parent.child = this;
			++NodesAllocated;
		}

		public CPPMnode find_symbol(int sym) // see if symbol is a child of node
		{
			for (CPPMnode found = child; found!=null; found=found.next) {
				if(found.symbol == sym)
					return found;
			}
			return null;
		}
	}

	public CPPMLanguageModel(CEventHandler EventHandler, CSettingsStore SettingsStore, CAlphIO.AlphInfo alph) {

		super(EventHandler, SettingsStore, alph); // Constructor of CLanguageModel

		m_Root = new CPPMnode(-1,null); // m_NodeAlloc.Alloc();
		
		// FIXME - this should be a boolean parameter
		bUpdateExclusion = ( GetLongParameter(Elp_parameters.LP_LM_UPDATE_EXCLUSION) !=0 );

		lpAlpha = GetLongParameter(Elp_parameters.LP_LM_ALPHA);
		lpBeta = GetLongParameter(Elp_parameters.LP_LM_BETA);
		m_iMaxOrder = (int)GetLongParameter(Elp_parameters.LP_LM_MAX_ORDER);
	}

	public void HandleEvent(CEvent Event) {
		super.HandleEvent(Event);

		if(Event instanceof CParameterNotificationEvent) {
			CParameterNotificationEvent evt = (CParameterNotificationEvent)Event;
			if(evt.m_iParameter == Elp_parameters.LP_LM_ALPHA) {
				lpAlpha = GetLongParameter(Elp_parameters.LP_LM_ALPHA);
			}
			else if(evt.m_iParameter == Elp_parameters.LP_LM_BETA) {
				lpBeta = GetLongParameter(Elp_parameters.LP_LM_BETA);
			}
		}
	}

	public int GetMemory() {
		return NodesAllocated;
	}

	/** Returns an array of probabilities for the next symbol
	 * @param ppmcontext context in which to make predictions
	 * @param norm value to which the probabilities should sum
	 * @return array with one element per character in the alphabet
	 * PLUS an initial zero.
	 */
	@Override
	public void GetProbs(CPPMnode ppmcontext, long[] probs, long norm) {

		/* CSFS: In the original C++ the norm value was an
		 * unsigned int. Since Java will only provide a signed
		 * int with half the capacity, I've converted it to a long.
		 */

		//exclusions[] array etc. was removed by CSFS.
		// this was CountExclusion, not UpdateExclusion - a (minor) speed
		// improvement at the cost of worse compression/prediction, hence leaving it out. 
		final int iNumSymbols = m_Alphabet.GetNumberSymbols()+1;

		long iToSpend = norm;

		for (;ppmcontext!=null; ppmcontext=ppmcontext.vine) {
			int iTotal = 0;

			for (CPPMnode pSymbol = ppmcontext.child; pSymbol != null; pSymbol=pSymbol.next) {
				iTotal += pSymbol.count;
			}

			if(iTotal != 0) {
				long size_of_slice = iToSpend;
				/* Changed type to long so that we don't run into trouble with overflows. */
				for(CPPMnode pSymbol = ppmcontext.child; pSymbol!=null;pSymbol = pSymbol.next) {
					long p = (size_of_slice) * (100 * pSymbol.count - lpBeta) / (100 * iTotal + lpAlpha);

					probs[pSymbol.symbol+1] += p;
					iToSpend -= p;
				}
			}
		}

		long size_of_slice = iToSpend;

		long each = size_of_slice / (iNumSymbols-1);
		iToSpend -= each*(iNumSymbols-1);
		
		for(int i = 1; i < iNumSymbols; i++) {
			long p = iToSpend / (iNumSymbols-i);
			probs[i] += each + p;
			iToSpend -= p;
		}
		assert(iToSpend == 0);
	}

	@Override
	public CPPMnode ContextLearningSymbol(CPPMnode ctx, int sym)
	// add symbol to the context
	// creates new nodes, updates counts
	// and leaves 'context' at the new context
	{
		assert(sym >= 0 && sym < m_Alphabet.GetNumberSymbols());
		CPPMnode r = AddSymbol(ctx,sym);
		while(!orderOk(r))
			r = r.vine;		
		return r;
	}
	
	protected CPPMnode AddSymbol(CPPMnode ctx, int sym) {
		CPPMnode child = ctx.find_symbol(sym);

		if(child != null) {
			child.count++;
			if(!bUpdateExclusion) {
				//update lower-order contexts - which are guaranteed to exist if the higher one does
				for (CPPMnode v = child.vine; v != null; v = v.vine) {
					assert (v==m_Root || v.symbol == sym);
					v.count++;
				}
			}
		} else {
			//symbol does not exist at this order. Record it, and recurse at lower order
			// (recursion will continue until it is found, and further if not doing update exclusion)
			child = new CPPMnode(sym, ctx, // count is initialized to 1
							(ctx==m_Root) ? m_Root : AddSymbol(ctx.vine, sym));
		}
		
		return child;
	}
	
	boolean orderOk(CPPMnode node) {
		int order=-1;
        for (; node!=null; node=node.vine) order++;
        return order<=m_iMaxOrder;
	}

	@Override
	public boolean UnlearnChild(CPPMnode parent, int sym, CPPMnode ch) {
		assert (ch.count>0);
		//do not reduce count to 0...unless we want to hang onto node via a weakref(?!)
		// and then let it be GC'd (or count reincremented first)?
		if (ch.count<=1) return false;
		ch.count--;
		return true;
	}
	
	@Override
	public CPPMnode ContextWithSymbol(CPPMnode ctx, int Symbol) {
		assert(Symbol >= 0 && Symbol < m_Alphabet.GetNumberSymbols());

		while(ctx != null) {
			CPPMnode find = ctx.find_symbol(Symbol);
			// Only try to extend the context if it's not going to make it too long
			if(find!=null && orderOk(find)) {   
				return find;
			}

			// If we can't extend the current context, follow vine pointer to shorten it and try again
			ctx = ctx.vine;
		}
		//failed to find anything...
		return m_Root;
	}	

	/**
	 * Diagnostic method; prints a given symbol.
	 * 
	 * @param sym Symbol to print
	 */
	public void dumpSymbol(int sym) {

		/* CSFS: This method appears never to be referenced.
		 * It exists only here and in one Japanese class.
		 */

		if((sym <= 32) || (sym >= 127))
			System.out.printf("<%d>", sym);
		else
			System.out.printf("%c", sym);
	}

	/**
	 * Diagnostic method; prints a given String starting
	 * at pos and with length len.
	 * 
	 * @param str String to print
	 * @param pos Position to start printing
	 * @param len Number of characters to print
	 */
	public void dumpString(String str, int pos, int len)
	// Dump the string STR starting at position POS
	{
		char cc;
		int p;
		for(p = pos; p < pos + len; p++) {
			cc = str.charAt(p);
			if((cc <= 31) || (cc >= 127))
				System.out.printf("<%d>", cc);
			else
				System.out.printf("%c", cc);
		}
	}

	public CPPMnode EmptyContext() {
		return m_Root;
	}
	
	@Override
	protected CPPMnode BuildContext(ListIterator<Integer> previousSyms, int countSoFar) {
		return (countSoFar >= m_iMaxOrder) ? EmptyContext() : super.BuildContext(previousSyms, countSoFar);
	}

}
