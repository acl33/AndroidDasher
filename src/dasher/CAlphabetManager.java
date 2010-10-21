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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * AlphabetManager is a specialisation of NodeManager which
 * knows about an Alphabet.
 * <p>
 * The AlphabetManager is used by the DasherModel to perform
 * tasks which require the knowledge of which Alphabet is currently
 * in use. This includes the handling of output of text when the
 * user enters or leaves a given node and extending the Model's
 * tree of DasherNodes, either forwards or backwards, whenever
 * necessary.
 *
 */

public class CAlphabetManager<C> {
	
	/**
	 * Pointer to the LanguageModel used in determining the
	 * relative probability assigned to new Nodes. 
	 */
	public final CLanguageModel<C> m_LanguageModel;
	
	/**
	 * Pointer to the NCManager, which modifies the probabilities for uniformity and control mode
	 */
    protected final CNodeCreationManager m_Model;
    
    /**
     * Pointer to the current Alphabet, used to find out what a
     * given character looks like typed (for the purposes
     * of output) and displayed (if growing the DasherNode tree).
     */
    protected final CAlphIO.AlphInfo m_Alphabet;
    
    protected final CAlphabetMap m_AlphabetMap;
    
    private int getColour(CDasherNode parent, SGroupInfo group, int phase) {
    	if (group!=null) {
    		if (group.bVisible) return group.iColour;
    		if (parent!=null) return parent.m_iColour;
    	}
    	//colour cycle root node (only)
    	return ((phase&1)==0) ? 137 : 7;
    }

    /**
     * Construct an AlphabetManager!
     * 
     * @param pNCManager NodeCreationManager in charge of this AlphabetManager; will be used
     * to modify probabilities for control mode, etc.
     * @param LanguageModel LanguageModel to use to determine relative sizes of child nodes
     */
    public CAlphabetManager( CNodeCreationManager pNCManager, CLanguageModel<C> LanguageModel) {
    	
    	this.m_LanguageModel = LanguageModel;
    	this.m_Model = pNCManager;
    	
    	m_Alphabet = LanguageModel.getAlphabet();
    	m_AlphabetMap = m_Alphabet.makeMap();
    	
    	//ACL TODO: CSFS wrote that repeated requests to CAlphIO.AlphInfo (then CAlphabet)::GetColour,
    	// GetSpaceSymbol and GetDisplayText, were taking up 5% of our runtime; and hence, he cached
    	// the ArrayList<Integer>s here. I'm trying to improve encapsulation and hence have ended this,
    	// but ought to check that this is efficient enough...
    }

    public int TrainStream(InputStream FileIn, int iTotalBytes, int iOffset,
			 CLockEvent evt) throws IOException {
		return m_AlphabetMap.TrainStream(m_LanguageModel, FileIn, iTotalBytes, iOffset, evt);
	}
	
    /**
     * Creates a new root CDasherNode with the supplied parameters.
     * @param iOffset index of character which this root should be considered as entering;
     * -1 indicates the root group node containing all potential first (offset=0) characters
     * @return a symbol node, as long as there is at least one preceding character; a group node if not
     */
    public CAlphNode GetRoot(CDasherNode Parent, long iLower, long iUpper, int iOffset, boolean bEnteredLast) {
    	if (iOffset < -1) throw new IllegalArgumentException("offset "+iOffset+" must be at least -1");
    	ListIterator<Character> previousChars = m_Model.m_DasherInterface.getContext(iOffset);
    	assert iOffset>=0 || !previousChars.hasPrevious();
    	CAlphNode NewNode;
    	ListIterator<Integer> previousSyms = m_AlphabetMap.GetSymbols(previousChars);
    	if (bEnteredLast && previousSyms.hasPrevious()) {
    		int iSym = previousSyms.previous();
    		if (iSym==CAlphabetMap.UNDEFINED) {
    			NewNode = new SpecialNode(Parent,iOffset, previousChars.next(),iLower,iUpper,m_LanguageModel.EmptyContext());
    		} else {
        		NewNode = allocSymbol(Parent,iOffset,iSym,iLower,iUpper, 
        				m_LanguageModel.ContextWithSymbol(m_LanguageModel.BuildContext(previousSyms),iSym));
    		}
    	} else {
    		//don't use previous symbol (if any)
    		C ctx = m_LanguageModel.EmptyContext();
			ArrayList <Integer> Symbols = new ArrayList<Integer>();
	    	//TODO should get a default start-of-sentence context from the alphabet.
			m_AlphabetMap.GetSymbols(Symbols, ". "); // UTF8 bytes become Unicode Integers
			for(int i = 0; i < Symbols.size(); i++)
				ctx=m_LanguageModel.ContextWithSymbol(ctx, Symbols.get(i));
			NewNode = allocGroup(Parent, iOffset, null, iLower, iUpper, ctx);
    	}
    	
    	return NewNode;
    }
    
    abstract class CAlphNode extends CDasherNode {
    	
    	protected final CAlphabetManager<C> mgr() {return CAlphabetManager.this;}
    	protected boolean bCommitted;
    	private long[] probInfo;
    	
    	/**
    	 * Language model context corresponding to this node's
    	 * position in the tree.
    	 */
    	private C context;
    	
    	private CAlphNode() {}
    	@Override
    	protected final void initNode(CDasherNode Parent, int iOffset, long iLbnd, long iHbnd, int colour, String label) {
    		throw new RuntimeException("Call version with extra context arg instead");
    	}
        void initNode(CDasherNode Parent, int iOffset,
				long ilbnd, long ihbnd, int Colour, C context,
				String label) {
			super.initNode(Parent, iOffset, ilbnd, ihbnd, Colour, label);
			this.context = context;
		}
        
        @Override public int ExpectedNumChildren() {
        	return m_Alphabet.numChildNodes();
        }
        @Override
		public void DeleteNode() {
        	bCommitted=false;
        	probInfo=null;
        	super.DeleteNode();
        }

        protected long[] GetProbInfo() {
        	if (probInfo == null) {
	        	probInfo = m_Model.GetProbs(m_LanguageModel,context);
	        	for (int i=1; i<probInfo.length; i++)
	        		probInfo[i]+=probInfo[i-1];
        	}
        	return probInfo;
        }
     	
        /**
		 * Reconstructs the parent of a given node, in the case that
		 * it had been deleted but the user has now backed off far
		 * enough that we need to restore.
		 * <p>
		 * In the event that context is not available, the root symbol is created and returned.
		 * 
		 * @param charsBefore the context - i.e. characters preceding this node
		 * @return The newly created parent, which may be the root node.
		 */
		protected void RebuildParent(int iNewOffset) {
				/* This used to clear m_Model.strContextBuffer. Removed as per notes
				 * at the top of CDasherInterfaceBase.
				 */
				
				/* This reconstitutes the parent of the current root in the case
				 * that we've backed off far enough to need to do so.
				 */
				
			CAlphNode newNode = GetRoot(null, 0, 0, iNewOffset, true);
			newNode.Seen(true);
			IterateChildGroups(newNode, null, this);
		}
    
		protected abstract CGroupNode rebuildGroup(CAlphNode parent, SGroupInfo group, long iLbnd, long iHbnd);

		protected abstract CDasherNode rebuildSymbol(CAlphNode parent, int sym, long iLbnd, long iHbnd);

    }

    class SpecialNode extends CAlphNode {
		SpecialNode(CDasherNode parent, int iOffset, Character sym, long iLbnd, long iHbnd, C ctx) {
			initNode(parent, iOffset,iLbnd, iHbnd, 1, ctx, sym.toString());
		}
		@Override
		protected CGroupNode rebuildGroup(CAlphNode parent, SGroupInfo group, long iLbnd, long iHbnd) {
			return CAlphabetManager.this.mkGroup(parent,group,iLbnd,iHbnd);
		}

		@Override
		protected CDasherNode rebuildSymbol(CAlphNode parent, int sym, long iLbnd, long iHbnd) {
			return CAlphabetManager.this.mkSymbol(parent, sym, iLbnd, iHbnd);
		}

		@Override
		public void Output() {
			//probability 0 will break user trials, but user trials shouldn't involve unknown symbols anyway...?
			m_Model.InsertEvent(new CEditEvent(1, m_strDisplayText, 0.0));
		}
		
		@Override public void Undo() {
			m_Model.InsertEvent(new CEditEvent(2, m_strDisplayText, 0.0));
		}

		@Override
		public void PopulateChildren() {
			IterateChildGroups(this,null,null);
		}

		@Override
		public CDasherNode RebuildParent() {
			if (Parent()==null) {
				//make a node for the previous symbol - i.e. as we'd expect our parent to be...
				CAlphNode n = GetRoot(null, 0, 0, getOffset()-1, true);
				n.Seen(true);

				//however, n won't generate us as a child. That's ok: we'll put in
				// its children for it, now, giving this special character a probability of 1/4
				// and reducing everything else accordingly (&all together)...
				final long norm = m_Model.GetLongParameter(Elp_parameters.LP_NORMALIZATION), cutOff = (norm*3)/4;
				//Firstly, a node containing what should be our _siblings_ - this could be our parent,
				// if we were a normal symbol, but instead will contain all the
				// sensible, normal, symbols the user could enter in our place.
				// However, it will sit beneath our common faked-out parent...
				GetRoot(n, 0, cutOff, getOffset()-1, false);
				
				//make ourselves a child too - as long as n remembers...
				SetRange(cutOff,norm);
				SetParent(n);
				//So if we ever reverse far enough that n is collapsed, and then
				// regenerates its children, this SpecialNode'll be missing - and
				// there'll be no way to re-enter this symbol, ever. (well, short
				// of changing alphabet). Which is fine....
			}
			return Parent();
		}
		@Override
		public void Enter() {
			//Make damn sure the user notices something funny is going on by
			// stopping him in his tracks. He can continue by unpausing...
			m_Model.m_DasherInterface.PauseAt(0, 0);
		}
	}

    protected class CSymbolNode extends CAlphNode {
    	private CSymbolNode() {}
    	
    	@Override
    	final void initNode(CDasherNode Parent, int iOffset, long ilbnd, long ihbnd, int Colour, C context, String label) {
    		throw new RuntimeException("Use (CDasherNode, int, int, long, long, C) instead");
    	}
    	void initNode(CDasherNode Parent, int iOffset, int symbol, long ilbnd, long ihbnd, C context) {
			super.initNode(Parent, iOffset, ilbnd, ihbnd, m_Alphabet.GetColour(symbol, iOffset), context, m_Alphabet.GetDisplayText(symbol));
			this.m_Symbol = symbol;
		}

    	@Override
    	public void PopulateChildren() {
    		IterateChildGroups(this, null, null);
    	}
		
    	/**
    	 * Symbol number represented by this node
    	 */
    	protected int m_Symbol;	// the character to display
    
    	/** Absorb the characters output by this symbol in our alphabet */	
    	@Override public void absorbContext(ListIterator<Character> it) {
    		String text = m_Alphabet.GetText(m_Symbol);
    		for (int i=text.length(); i-->0; )
    			if (it.previous() != text.charAt(i))
    				throw new IllegalArgumentException("Previous character "+it.next()+" but node would have output "+text.substring(0,i+1));		
   		}
    	 
    	/**
         * Generates an EditEvent announcing a new character has been
         * entered, inferring the character from the Node supplied.
         * <p>
         * The second and third parameters are solely for logging
         * purposes. Logging is not currently enabled in JDasher
         * and so these can safely be set to null and 0 respectively.
         * <p>
         * In the case that logging is enabled, passing the second parameter
         * as null will cause this addition not to be logged.
         * 
         * @param Node The node whose symbol we wish to look up and announce.
         * @param Added An ArrayList<CSymbolProb> to which the typed symbol, annotated with its probability, will be added for logging purposes.
         * @param iNormalization The total to which probabilities should add (usually LP_NORMALIZATION) for the purposes of generating the logged probability.
         */
    	@Override
        public void Output() {
        	CEditEvent oEvent = new CEditEvent(1, m_Alphabet.GetText(m_Symbol), GetProb());
        	m_Model.InsertEvent(oEvent);
        }

    	private double GetProb() {
    		double prob = 1.0; CDasherNode p=this;
        	do {
        		prob *= p.Range();
        		p=p.Parent();
        		if (p==null) return prob; //shouldn't really happen, but...?
        		prob /= p.ChildAtIndex(p.ChildCount()-1).Hbnd();
        	} while (!(p instanceof CAlphabetManager<?>.CSymbolNode));
        	return prob;
    	}
        /**
         * Generates an EditEvent announcing that the character represented
         * by this Node should be removed.
         * 
         * @param Node Node whose symbol we wish to remove.
         */    
        public void Undo() {
        	CEditEvent oEvent = new CEditEvent(2, m_Alphabet.GetText(m_Symbol), GetProb());
       		m_Model.InsertEvent(oEvent);
       		bCommitted = false;
        }
        
        @Override
		public void commit() {
			if (bCommitted) return;
			bCommitted=true;
			//ACL this was used as an 'if' condition:
			assert (m_Symbol < m_Alphabet.GetNumberSymbols());
			//...before performing the following. But I can't see why it should ever fail?!
			
			if (m_Model.GetBoolParameter(Ebp_parameters.BP_LM_ADAPTIVE)) {
				if (Parent() instanceof CAlphabetManager<?>.CAlphNode) {
					//type erasure means can't check parent has _same_ context type.
					CAlphabetManager<?>.CAlphNode other = (CAlphabetManager<?>.CAlphNode)Parent();
					//however, we _can_ check that it's from the same AlphMgr, in which case we know we're safe...
					if (other.mgr() == mgr()) {
						C otherContext = (C)other.context; //yes, warning of unsafe cast - safe because of above
						m_LanguageModel.ContextLearningSymbol(otherContext, m_Symbol);
					}
				}
				//else - do we do anything? should mean root nodes ok...
			}
		}
        
        @Override
        public CDasherNode RebuildParent() {
	        if (Parent()==null && getOffset()>=0) RebuildParent(getOffset()-1);
			return Parent();
	    }
        
		public CGroupNode rebuildGroup(CAlphNode parent, SGroupInfo group, long iLbnd, long iHbnd) {
			CGroupNode ret = CAlphabetManager.this.mkGroup(parent, group, iLbnd, iHbnd);
			if (group.iStart <= m_Symbol && group.iEnd > m_Symbol) {
				ret.Seen(true);
				//created group node should contain this symbol
				IterateChildGroups(ret, group, this);
			}
			return ret;
		}

		public CDasherNode rebuildSymbol(CAlphNode parent, int sym, long iLbnd, long iHbnd) {
			if (sym==m_Symbol) {
				SetRange(iLbnd, iHbnd);
				SetParent(parent);
				return this;
			}
			return CAlphabetManager.this.mkSymbol(parent, sym, iLbnd, iHbnd);
		}
		
		@Override
		public void DeleteNode() {
			super.DeleteNode();
			freeSymbolList.add(this);
		}

    }
    
    protected class CGroupNode extends CAlphNode {
    	private CGroupNode() {}
    	@Override
    	final void initNode(CDasherNode Parent, int iOffset, long ilbnd, long ihbnd, int Colour, C context, String label) {
    		throw new RuntimeException("Use (CDasherNode, int, SGroupInfo, long, long, C) instead");
    	}
    	
    	void initNode(CDasherNode Parent, int iOffset, SGroupInfo group,
				long ilbnd, long ihbnd, C context) {
			super.initNode(Parent, iOffset, ilbnd, ihbnd, getColour(Parent, group,iOffset), context, (group==null || !group.bVisible) ? "" : group.strLabel);
			this.m_Group = group;
		}

    	@Override
    	public boolean outline() {
    		return (m_Group==null || m_Group.bVisible);
    	}
    	
    	@Override public int ExpectedNumChildren() {
    		return (m_Group==null) ? super.ExpectedNumChildren() : m_Group.iNumChildNodes;
    	}
    	
    	@Override
    	public void PopulateChildren() {
    		IterateChildGroups(this, m_Group, null);
    		if (ChildCount()==1) {
    			//avoid colours blinking as the child entirely covers over this...
    			CDasherNode child = Children().get(0);
    			assert (child.Lbnd() == 0 && child.Hbnd() == m_Model.GetLongParameter(Elp_parameters.LP_NORMALIZATION));
    			child.setColour(Colour());
    		}
    	}
    	
    	@Override
    	public CDasherNode RebuildParent() {
    		if (Parent()==null && m_Group!=null) RebuildParent(getOffset());
			return Parent();
    	}
    	
    	@Override
    	protected long[] GetProbInfo() {
    		if (m_Group!=null && (Parent() instanceof CAlphabetManager<?>.CAlphNode)) {
    			CAlphabetManager<?>.CAlphNode p = (CAlphabetManager<?>.CAlphNode)Parent();
    			assert p.mgr() == mgr();
    			return p.GetProbInfo();
    		}
    		return super.GetProbInfo();
    	}

    	protected SGroupInfo m_Group;

		@Override
		public void Output() {
			// Do nothing.
		}

		public CGroupNode rebuildGroup(CAlphNode parent, SGroupInfo group, long iLbnd, long iHbnd) {
			if (group==this.m_Group) {
				SetRange(iLbnd, iHbnd);
				SetParent(parent);
				return this;
			}
			CGroupNode ret=CAlphabetManager.this.mkGroup(parent, group, iLbnd, iHbnd);
			if (group.iStart <= m_Group.iStart && group.iEnd >= m_Group.iEnd) {
			    //created group node should contain this one
				ret.Seen(true);
			    IterateChildGroups(ret,group,this);
			  }
			return ret;
		}

		public CDasherNode rebuildSymbol(CAlphNode parent, int sym, long iLbnd, long iHbnd) {
			return CAlphabetManager.this.mkSymbol(parent, sym, iLbnd, iHbnd);
		}
    	
		@Override
		public void DeleteNode() {
			super.DeleteNode();
			freeGroupList.add(this);
		}
    }

    /**
     * Creates the children of a given Node, from which probabilities are extracted.
     * associated with said children and, perhaps, one child which already exists.
     * <p>
     * The probabilties supplied should not be cumulative, but should be normalised
     * such that they add up to the value of LP_NORMALIZATION.
     * 
     * @param Node Node whose children are to be populated.
     * @param iExistingSymbol Symbol of its existing child, or -2 if there is none.
     * @param ExistingChild Reference to its existing child, if any.
     * @param cum Probabilities to be associated with the children,
     *            supplied in alphabet symbol order.
     */    
    public void IterateChildGroups( CAlphNode Node, SGroupInfo parentGroup, CAlphNode buildAround) {
    	
    	long[] probInfo = Node.GetProbInfo();
    	
    	final int iMin,iMax; //first & last syms
    	final long iRange; //range of probabilities for all children (syms as prev, plus "extras" e.g. Control Nodes)
    	if (parentGroup!=null) {iMin = parentGroup.iStart; iMax = parentGroup.iEnd; iRange = probInfo[iMax]-probInfo[iMin];}
    	else {iMin = 0; iMax = m_Alphabet.GetNumberSymbols(); iRange = m_Model.GetLongParameter(Elp_parameters.LP_NORMALIZATION);}
    	  
    	  // Create child nodes and add them
    	  
    	  int i=iMin; //lowest index of child which we haven't yet added
    	  SGroupInfo group = (parentGroup==null) ? m_Alphabet.getBaseGroup() : parentGroup.Child;
    	  // The SGroupInfo structure has something like linked list behaviour
    	  // Each SGroupInfo contains a pNext, a pointer to a sibling group info
    	  while (i < iMax) {
    	    CDasherNode pNewChild;
    	    boolean bSymbol = group==null //gone past last subgroup
    	                  || i < group.iStart; //not reached next subgroup
    	    final int iStart=i, iEnd = (bSymbol) ? i+1 : group.iEnd;

    	    long iLbnd = ((probInfo[iStart] - probInfo[iMin]) *
    	                          (m_Model.GetLongParameter(Elp_parameters.LP_NORMALIZATION))) /
    	                         iRange;
    	    long iHbnd = ((probInfo[iEnd] - probInfo[iMin]) *
    	                          (m_Model.GetLongParameter(Elp_parameters.LP_NORMALIZATION))) /
    	                         iRange;
    	    
    	    if (bSymbol) {
    	      pNewChild = (buildAround==null) ? mkSymbol(Node, i, iLbnd, iHbnd) : buildAround.rebuildSymbol(Node, i, iLbnd, iHbnd);
    	      i++; //make one symbol at a time - move onto next in next iteration
    	    } else { //in/reached subgroup - do entire group in one go:
    	      pNewChild= (buildAround==null) ? mkGroup(Node, group, iLbnd, iHbnd) : buildAround.rebuildGroup(Node, group, iLbnd, iHbnd);
    	      i = group.iEnd; //make one group at a time - so move past entire group...
    	      group = group.Next;
    	    }
    	    assert Node.Children().get(Node.ChildCount()-1)==pNewChild;
    	  }
    	  if (parentGroup==null) m_Model.addExtraNodes(Node, probInfo);
    }
    
    CDasherNode mkSymbol(CAlphNode parent, int sym, long iLbnd, long iHbnd) {
    	//ACL make the new node's context ( - this used to be done only in PushNode(),
		// before calling populate...)
		C cont;
		if (sym < m_Alphabet.GetNumberSymbols() && sym >= 0) {
			// Normal symbol - derive context from parent
			cont = m_LanguageModel.ContextWithSymbol(parent.context,sym);
		} else {
			assert false; //ACL I don't see how this can/should ever happen?
			// For new "root" nodes (such as under control mode), we want to 
			// mimic the root context
			cont = m_LanguageModel.EmptyContext();
			//      EnterText(cont, "");
		}
		return allocSymbol(parent, parent.getOffset()+1, sym, iLbnd, iHbnd, cont);
    }
    
    CGroupNode mkGroup(CAlphNode parent, SGroupInfo group, long iLbnd, long iHbnd) {
    	return allocGroup(parent, parent.getOffset(), group, iLbnd, iHbnd, parent.context);
    }
    
    private final List<CGroupNode> freeGroupList = new ArrayList<CGroupNode>();
    
    private CGroupNode allocGroup(CDasherNode parent, int iOffset, SGroupInfo group, long iLbnd, long iHbnd, C ctx) {
    	CGroupNode node = (freeGroupList.isEmpty()) ? new CGroupNode() : freeGroupList.remove(freeGroupList.size()-1);
    	node.initNode(parent, iOffset, group, iLbnd, iHbnd, ctx);
    	return node;
    }

    private final List<CSymbolNode> freeSymbolList = new ArrayList<CSymbolNode>();
    
    private CSymbolNode allocSymbol(CDasherNode parent, int iOffset, int sym, long iLbnd, long iHbnd, C ctx) {
    	CSymbolNode node = (freeSymbolList.isEmpty()) ? new CSymbolNode() : freeSymbolList.remove(freeSymbolList.size()-1);
    	node.initNode(parent, iOffset, sym, iLbnd, iHbnd, ctx);
    	return node;
    }
    /**
     * Suspends the current thread until a given Node's children
     * have been created. This is for use with specialised
     * AlphabetManagers which populate their child lists
     * asynchronously such as RemoteAlphabetManager.
     * <p>
     * This simply polls the child-list every 50ms, and returns
     * when it finds it is neither null nor empty.
     * 
     * @param node Node whose children we wish to wait for.
     */
    public void WaitForChildren(CDasherNode node) {
    	while (node.ChildCount() == 0) {
    		try {
    			Thread.sleep(50);
    		}
    		catch(InterruptedException e) {
    			// Do nothing
    		}
    	}
    	
    }
    
    
}