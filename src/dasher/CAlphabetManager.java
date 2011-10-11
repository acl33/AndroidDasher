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
import java.util.Iterator;
import java.util.List;

import static dasher.CDasherModel.NORMALIZATION;

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
	private final CLanguageModel<C> m_LanguageModel;

	private final CDasherInterfaceBase m_Interface;
	
	/**
	 * Pointer to the NCManager, which modifies the probabilities for uniformity and control mode
	 */
    private CNodeCreationManager m_pNCManager;
    
    void ChangeNCManager(CNodeCreationManager newMgr) {this.m_pNCManager = newMgr;}
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
    public CAlphabetManager(CDasherInterfaceBase intf, CLanguageModel<C> LanguageModel) {
    	this.m_Interface = intf;
    	this.m_LanguageModel = LanguageModel;
    	
    	m_Alphabet = LanguageModel.getAlphabet();
    	m_AlphabetMap = m_Alphabet.makeMap();
    	
    	//ACL TODO: CSFS wrote that repeated requests to CAlphIO.AlphInfo (then CAlphabet)::GetColour,
    	// GetSpaceSymbol and GetDisplayText, were taking up 5% of our runtime; and hence, he cached
    	// the ArrayList<Integer>s here. I'm trying to improve encapsulation and hence have ended this,
    	// but ought to check that this is efficient enough...
    }

    public int TrainStream(InputStream FileIn, int iTotalBytes, int iOffset,
			 CDasherInterfaceBase.ProgressNotifier prog) throws IOException {
		return m_AlphabetMap.TrainStream(m_LanguageModel, FileIn, iTotalBytes, iOffset, prog);
	}
	
    /**
     * Creates a new root CDasherNode with the supplied parameters. (Parent, Lower, Upper:
     * these can be changed afterwards using Reparent)
     * @param iOffset index of character which this root should be considered as entering;
     * -1 indicates the root group node containing all potential first (offset=0) characters
     * @return a symbol node, as long as there is at least one preceding character; a group node if not
     */
    public CAlphNode GetRoot(Document doc, int iOffset, boolean bEnteredLast) {
    	if (iOffset < -1) throw new IllegalArgumentException("offset "+iOffset+" must be at least -1");
    	C ctx;
    	Iterator<Integer> previousSyms = m_AlphabetMap.GetSymbolsBackwards(doc, iOffset);
		if (bEnteredLast) {
    		if (previousSyms.hasNext()) {
	    		int iSym = previousSyms.next();
	    		CAlphNode NewNode;
	    		if (iSym==CAlphabetMap.UNDEFINED) {
	    			char c = doc.getCharAt(iOffset);
	    			String s = (Character.isLowSurrogate(c) && Character.isHighSurrogate(doc.getCharAt(iOffset-1)))
	    				? new String(new char[] {doc.getCharAt(iOffset-1),c}) : Character.toString(c);
	    			NewNode = new SpecialNode(iOffset, s, m_LanguageModel.EmptyContext());
	    		} else {
	        		NewNode = allocSymbol(iOffset,iSym, 
	        				m_LanguageModel.ContextWithSymbol(m_LanguageModel.BuildContext(previousSyms),iSym));
	    		}
	    		NewNode.m_bCommitted = true;
	    		return NewNode;
    		}
    		//else, no previous symbol:
    		ctx = m_AlphabetMap.defaultContext(m_LanguageModel);
    	} else {
    		//told not to use previous symbol
    		ctx = m_LanguageModel.BuildContext(previousSyms);
    	}
    	return allocGroup(iOffset, null, getColour(null, null, iOffset), ctx);
    }
    
    /** Entered text which has not yet been written out to disk */
    protected final StringBuilder strTrainfileBuffer = new StringBuilder();
    /** Context representing the last characters in strTrainfileBuffer */
    private C bufCtx;
    
    /** The last alphnode to be output (seen). So we can flush _all_ output characters
     * to file + train LM when changing context / exitting.
     */
    private CAlphNode lastOutput;
    
    protected void Learn(CSymbolNode child) {
    	CAlphNode parent = checkCast(child.Parent());
    	C parCtx = (parent==null) ? m_AlphabetMap.defaultContext(m_LanguageModel) : parent.context;
        if (bufCtx==null || !bufCtx.equals(parCtx)) {
	    	//changing context. First write the old to the training file...
			if (strTrainfileBuffer.length()>0) WriteTrainFileFull(m_Interface);
			
			//Now encode a context-switch command (if possible)
			if (m_Alphabet.ctxChar!=null) {
				// Unfortunately getting the new context may not be possible:
				// If we're committing a string because the user has finished editing and
				// switched out of the textbox, we won't be able to get a meaningful context string.
				// So, we rely on the LM being able to turn a context back into a string...
				String ctx;
				if (parent!=null) {
					List<Integer> syms = new ArrayList<Integer>();
					m_LanguageModel.ContextToSymbols(parent.context, syms);
					
					StringBuilder sb=new StringBuilder();
					for (int i=0; i<syms.size(); i++)
						sb.append(m_Alphabet.GetText(syms.get(i)));
					ctx=sb.toString();
					//Whatever we write out, when we read in the context-switch command, we automatically 
					// enter the alphabet default context first. So avoid entering it twice!
					if (ctx.startsWith(m_Alphabet.getDefaultContext()))
						ctx = ctx.substring(m_Alphabet.getDefaultContext().length());
				} else ctx="";
				//now put the record of it into the buffer. The command format
				//requires an arbitrary delimiter, so look for the first character
				//(strictly after 32=space) that's _not_ in the context we need to delimit...
				char delimiter;
				for (delimiter=33; ctx.indexOf(delimiter)!=-1 || delimiter==m_Alphabet.ctxChar; delimiter++);
				//(guaranteed to terminate, the context has only ten characters!)
				
				strTrainfileBuffer.append(m_Alphabet.ctxChar).append(delimiter).append(ctx).append(delimiter);
			}
        }
        String sym = m_Alphabet.GetText(child.m_Symbol);
		if (sym.equals(m_Alphabet.ctxChar)) {
			//the context-command character is actually being written as a genuine character. Escape it by writing it twice...
			strTrainfileBuffer.append(m_Alphabet.ctxChar); // (it gets written once more below)
		}
		//now schedule the character for writing...
		strTrainfileBuffer.append(sym); bufCtx = ((CAlphNode)child).context;
		//and train the LM. Hmmm - this may keep trainfile (eventually) in sync with model,
		// but still only writes/trains on committed text, i.e. will miss the last few characters
		// (written but not committed, at the end) of each session (e.g. prior to each textbox/context switch)
		m_LanguageModel.ContextLearningSymbol(parCtx,child.m_Symbol);
        //Also note we don't update the child context with the result of ContextLearningSymbol:
        // this might be different if this is the first time that child symbol has been seen,
        // but the same context may/will also be stored in child sub (group) nodes,
        // and unless we also update all of those, <bufCtx> will get out-of-sync,
        // resulting in lots of spurious context switches being written out to file :-(
	}
    
    protected void flush(CAlphNode output) {
    	if (output==null || output.m_bCommitted) return;
    	flush(checkCast(output.Parent()));
   		output.commit(true);
    }
	
    /**
	 * Writes all the text entered by the user to the training file
	 * (by calling {@link #WriteTrainFile(String, String)})
	 * @param filename name of training file, e.g. "training_english_GB.txt"
	 */
	protected void WriteTrainFileFull(CDasherInterfaceBase intf) {
		intf.WriteTrainFile(m_Alphabet.GetTrainingFile(),strTrainfileBuffer.toString());
		strTrainfileBuffer.setLength(0);
	}
	
	CAlphNode checkCast(CDasherNode n) {
		//type erasure means can't check parent has _same_ context type.
		if (n instanceof CAlphabetManager<?>.CAlphNode) {
			CAlphabetManager<?>.CAlphNode nn = (CAlphabetManager<?>.CAlphNode)n;
			//however, we _can_ check that it's from the same AlphMgr, in which case we know we're safe...
				if (nn.mgr()==this)
					return (CAlphNode)nn; //warning unchecked cast, we know safe because of above
		}
		return null;
	}
	
    abstract class CAlphNode extends CDasherNode {
    	
    	protected final CAlphabetManager<C> mgr() {return CAlphabetManager.this;}
    	protected CDasherInterfaceBase getIntf() {return CAlphabetManager.this.m_Interface;}
    	private long[] probInfo;
    	private boolean m_bCommitted;
    	/**
    	 * Language model context corresponding to this node's
    	 * position in the tree.
    	 */
    	private C context;
    	
    	private CAlphNode() {}
    	@Override
    	protected final void initNode(int iOffset, int colour, String label) {
    		throw new RuntimeException("Call version with extra context arg instead");
    	}
        void initNode(int iOffset, int Colour, C context, String label) {
			super.initNode(iOffset, Colour, label);
			this.context = context;
			this.m_bCommitted = false;
		}
        
        @Override public int ExpectedNumChildren() {
        	return m_Alphabet.numChildNodes();
        }
        @Override
        public void DeleteNode() {
        	if (probInfo!=null) {
        		m_pNCManager.recycleProbArray(probInfo);
        		probInfo=null;
        	}
        	super.DeleteNode();
        	m_bCommitted=false;
        }

        @Override public void Output() {
        	if (lastOutput!=Parent()) {
        		flush(lastOutput);
        	}
        	lastOutput=this;
        }
        
        @Override public void Undo() {
        	if (lastOutput==this) {
        		lastOutput = checkCast(Parent());
        	}
        	m_bCommitted = false;
        }
        
        @Override public void commit(boolean bNv) {
        	//we don't allow uncommitting.
        	m_bCommitted |= bNv;
        }
        
        protected long[] GetProbInfo() {
        	if (probInfo == null) {
	        	probInfo = m_pNCManager.GetProbs(m_LanguageModel,context);
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
				
			CAlphNode newNode = GetRoot(this, iNewOffset, true);
			IterateChildGroups(newNode, null, this);
			CAlphNode node = this;
			do {
				node = (CAlphNode)node.Parent();
				node.Seen(true);
				node.m_bCommitted=true;
			} while (node != newNode);
		}
    
		protected abstract CGroupNode rebuildGroup(CAlphNode parent, SGroupInfo group, long iLbnd, long iHbnd);

		protected abstract CDasherNode rebuildSymbol(CAlphNode parent, int sym, long iLbnd, long iHbnd);

    }
    
    abstract class COutputNode extends CAlphNode {
    	private COutputNode() {}
    	protected abstract String outputText();
    	
    	/** Outputs {@link outputText} to the document at this node's offset. */
    	@Override public void Output() {
			super.Output();
			m_Interface.getDocument().outputText(outputText(), getOffset());
		}
    	
    	/** Removes {@link #outputText()} from the document at this node's offset. */
		@Override public void Undo() {
			super.Undo();
			m_Interface.getDocument().deleteText(outputText(), getOffset());
		}
		/** Begins a fresh copy of the whole alphabet/letter tree */ 
		@Override
    	public void PopulateChildren() {
    		IterateChildGroups(this, null, null);
    	}
		
    	@Override
    	public Character getCharAt(int idx) {
    		String s = outputText();
			if (idx>getOffset())
				idx -= s.length();
			else if (idx>getOffset()-s.length())
				return s.charAt(idx-getOffset()+s.length()-1);
			return super.getCharAt(idx);
		}
    	
    	@Override
    	public int undoTransformIndex(int index) {
    		int len = outputText().length();
    		//characters after our output position, would be at higher offsets given the output of this node...
    		if (index>getOffset()-len) index+=len;
    		return index;
    	}

    }

    class SpecialNode extends COutputNode {
		SpecialNode(int iOffset, String string, C ctx) {
			initNode(iOffset, 1, ctx, string);
		}
		@Override
		protected CGroupNode rebuildGroup(CAlphNode parent, SGroupInfo group, long iLbnd, long iHbnd) {
			return CAlphabetManager.this.mkGroup(parent,group,iLbnd,iHbnd);
		}

		@Override
		protected CDasherNode rebuildSymbol(CAlphNode parent, int sym, long iLbnd, long iHbnd) {
			return CAlphabetManager.this.mkSymbol(parent, sym, iLbnd, iHbnd);
		}

		protected String outputText() {return m_strDisplayText;}
		
		@Override
		public CDasherNode RebuildParent() {
			if (Parent()==null) {
				//make a node for the previous symbol - i.e. as we'd expect our parent to be...
				CAlphNode n = GetRoot(this, getOffset()-m_strDisplayText.length(), true);
				n.Seen(true); n.m_bCommitted=true;

				//however, n won't generate us as a child. That's ok: we'll put in
				// its children for it, now, giving this special character a probability of 1/4
				// and reducing everything else accordingly (&all together)...
				final long cutOff = (NORMALIZATION*3)/4;
				//Firstly, a node containing what should be our _siblings_ - this could be our parent,
				// if we were a normal symbol, but instead will contain all the
				// sensible, normal, symbols the user could enter in our place.
				// However, it will sit beneath our common faked-out parent...
				CAlphNode temp = GetRoot(this, getOffset()-m_strDisplayText.length(), false);
				temp.Reparent(n, 0, cutOff);
				
				//make ourselves a child too - as long as n remembers...
				Reparent(n, cutOff,NORMALIZATION);
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
			m_Interface.PauseAt(0, 0);
		}
	}

    protected class CSymbolNode extends COutputNode {
    	private CSymbolNode() {}
    	
    	@Override
    	final void initNode(int iOffset, int Colour, C context, String label) {
    		throw new RuntimeException("Use (int, int, C) instead");
    	}
    	void initNode(int iOffset, int symbol, C context) {
			super.initNode(iOffset, m_Alphabet.GetColour(symbol, iOffset), context, m_Alphabet.GetDisplayText(symbol));
			this.m_Symbol = symbol;
		}
    	
    	protected String outputText() {return m_Alphabet.GetText(m_Symbol);}
    	
    	/**
    	 * Symbol number represented by this node
    	 */
    	protected int m_Symbol;	// the character to display
    
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
        
        @Override
        public void commit(boolean bNv) {
        	if (((CAlphNode)this).m_bCommitted || !bNv) return;
			//ACL this was used as an 'if' condition:
			assert (m_Symbol < m_Alphabet.GetNumberSymbols());
			//...before performing the following. But I can't see why it should ever fail?!
			
			super.commit(bNv);
			if (m_pNCManager.GetBoolParameter(Ebp_parameters.BP_LM_ADAPTIVE)) {
				Learn(this);
			}
		}
        
        @Override
        public CDasherNode RebuildParent() {
	        if (Parent()==null && getOffset()>=0) RebuildParent(getOffset()-m_Alphabet.GetText(m_Symbol).length());
			return Parent();
	    }
        
		public CGroupNode rebuildGroup(CAlphNode parent, SGroupInfo group, long iLbnd, long iHbnd) {
			CGroupNode ret = CAlphabetManager.this.mkGroup(parent, group, iLbnd, iHbnd);
			if (group.iStart <= m_Symbol && group.iEnd > m_Symbol) {
				//created group node should contain this symbol
				IterateChildGroups(ret, group, this);
			}
			return ret;
		}

		public CDasherNode rebuildSymbol(CAlphNode parent, int sym, long iLbnd, long iHbnd) {
			if (sym==m_Symbol) {
				Reparent(parent, iLbnd, iHbnd);
				return this;
			}
			return CAlphabetManager.this.mkSymbol(parent, sym, iLbnd, iHbnd);
		}
		
		@Override
		public void DeleteNode() {
			if (lastOutput==this) {
				flush(this);
				lastOutput=null;
			}
			super.DeleteNode(); //clears Parent(), hence have to do the above first...
			freeSymbolList.add(this);
		}

    }
    
    protected class CGroupNode extends CAlphNode {
    	private CGroupNode() {}
    	@Override
    	final void initNode(int iOffset, int Colour, C context, String label) {
    		throw new RuntimeException("Use (int, SGroupInfo, long, long, C) instead");
    	}
    	
    	void initNode(int iOffset, SGroupInfo group, int iColour, C context) {
			super.initNode(iOffset, iColour, context, (group==null || !group.bVisible) ? "" : group.strLabel);
			this.m_Group = group;
		}

    	@Override
    	public boolean visible() {
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
    			assert (child.Lbnd() == 0 && child.Hbnd() == NORMALIZATION);
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
    			//subgroups use same probinfo as parent...
    			CAlphabetManager<?>.CAlphNode p = (CAlphabetManager<?>.CAlphNode)Parent();
    			assert p.mgr() == mgr();
    			return p.GetProbInfo();
    			//note, long[] is still stored only in parent.
    		}
    		return super.GetProbInfo();
    	}

    	protected SGroupInfo m_Group;

		public CGroupNode rebuildGroup(CAlphNode parent, SGroupInfo group, long iLbnd, long iHbnd) {
			if (group==this.m_Group) {
				Reparent(parent, iLbnd, iHbnd);
				return this;
			}
			CGroupNode ret=CAlphabetManager.this.mkGroup(parent, group, iLbnd, iHbnd);
			if (group.iStart <= m_Group.iStart && group.iEnd >= m_Group.iEnd) {
			    //created group node should contain this one
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
    	else {iMin = 0; iMax = m_Alphabet.GetNumberSymbols(); iRange = NORMALIZATION;}
    	  
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

    	    final long iLbnd = ((probInfo[iStart] - probInfo[iMin]) * NORMALIZATION) /
    	                         iRange;
    	    final long iHbnd = ((probInfo[iEnd] - probInfo[iMin]) * NORMALIZATION) /
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
    	  if (parentGroup==null) m_pNCManager.addExtraNodes(Node, probInfo);
    }
    
    /** General/utility method (e.g. for subclasses, perhaps to override)
     * to make a symbol node, as a child of another, in the default manner. 
     * @return
     */
    CDasherNode mkSymbol(CAlphNode parent, int sym, long iLbnd, long iHbnd) {
    	CSymbolNode n = allocSymbol(parent.getOffset()+m_Alphabet.GetText(sym).length(), sym,
    			m_LanguageModel.ContextWithSymbol(parent.context, sym));
    	n.Reparent(parent, iLbnd, iHbnd);
    	return n;
    }
    
    CGroupNode mkGroup(CAlphNode parent, SGroupInfo group, long iLbnd, long iHbnd) {
    	CGroupNode n = allocGroup(parent.getOffset(), group, getColour(parent, group, parent.getOffset()), parent.context);
    	n.Reparent(parent, iLbnd, iHbnd);
    	return n;
    }
    
    private final List<CGroupNode> freeGroupList = new ArrayList<CGroupNode>();
    
    private CGroupNode allocGroup(int iOffset, SGroupInfo group, int iColour, C ctx) {
    	CGroupNode node = (freeGroupList.isEmpty()) ? new CGroupNode() : freeGroupList.remove(freeGroupList.size()-1);
    	node.initNode(iOffset, group, iColour, ctx);
    	return node;
    }

    private final List<CSymbolNode> freeSymbolList = new ArrayList<CSymbolNode>();
    
    private CSymbolNode allocSymbol(int iOffset, int sym, C ctx) {
    	CSymbolNode node = (freeSymbolList.isEmpty()) ? new CSymbolNode() : freeSymbolList.remove(freeSymbolList.size()-1);
    	node.initNode(iOffset, sym, ctx);
    	return node;
    }

    static CAlphabetManager<?> makeAlphMgr(CDasherInterfaceBase intf) {
		//Convert the full alphabet to a symbolic representation for use in the language model
		
		// -- put all this in a separate method
		// TODO: Think about having 'prefered' values here, which get
		// retrieved by DasherInterfaceBase and used to set parameters
		
		// TODO: We might get a different alphabet to the one we asked for -
		// if this is the case then the parameter value should be updated,
		// but not in such a way that it causes everything to be rebuilt.
		
		CAlphIO.AlphInfo cAlphabet = intf.GetInfo(intf.GetStringParameter(Esp_parameters.SP_ALPHABET_ID));
		
		// Create an appropriate language model;
		
		switch ((int)intf.GetLongParameter(Elp_parameters.LP_LANGUAGE_MODEL_ID)) {
		default:
			// If there is a bogus value for the language model ID, we'll default
			// to our trusty old PPM language model.
		case 0:
			intf.SetBoolParameter(Ebp_parameters.BP_LM_REMOTE, false);
			return /*ACL (langMod.isRemote())
		        ? new CRemoteAlphabetManager( this, langMod)
		        :*/ new CAlphabetManager<CPPMLanguageModel.CPPMnode>(intf, new CPPMLanguageModel(intf, cAlphabet));
		/* case 2:
			m_pLanguageModel = new CWordLanguageModel(m_pEventHandler, m_pSettingsStore, alphabet);
			break;
		case 3:
			m_pLanguageModel = new CMixtureLanguageModel(m_pEventHandler, m_pSettingsStore, alphabet);
			break;  
			#ifdef JAPANESE
		case 4:
			m_pLanguageModel = new CJapaneseLanguageModel(m_pEventHandler, m_pSettingsStore, alphabet);
			break;
			#endif */
			
		case 5:
			throw new UnsupportedOperationException("(ACL) Remote LM currently unimplemented");
			//langMod = new CRemotePPM(m_EventHandler, m_SettingsStore, alphabet);
			//SetBoolParameter(Ebp_parameters.BP_LM_REMOTE, true);
		
			//break;
		/* CSFS: Commented out the other language models for the time being as they are not
		 * implemented yet.
		 */
		}
	}    
}
