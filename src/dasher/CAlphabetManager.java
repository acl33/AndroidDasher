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
    /** Context to write out to trainfile, i.e. in which strTrainfileBuffer begins */
    protected final StringBuilder strTrainfileContext = new StringBuilder();
    /*package*/ final List<Integer> tempList = new ArrayList<Integer>();
    
    /** The last alphnode to be output (seen). So we can flush _all_ output characters
     * to file + train LM when changing context / exitting.
     */
    private CAlphNode lastOutput;
    
    /**
	 * Writes all the text entered by the user to the training file
	 * (by calling {@link #WriteTrainFile(String, String)})
	 * @param filename name of training file, e.g. "training_english_GB.txt"
	 */
	protected void WriteTrainFileFull(CDasherInterfaceBase intf) {
		if (strTrainfileBuffer.length()==0) return;
		if (strTrainfileContext.length()!=0) {
			String defCtx=m_Alphabet.getDefaultContext();
			if (strTrainfileContext.length()>=defCtx.length() && strTrainfileContext.substring(0, defCtx.length()).equals(defCtx))
				strTrainfileContext.delete(0, defCtx.length());
			//Now encode a context-switch command (if possible)
			if (m_Alphabet.ctxChar!=null) {
				char delimiter;
				for (delimiter=33; !isValidDelim(delimiter); delimiter++);
				//(guaranteed to terminate, the context has only ten characters!)
				strTrainfileContext.insert(0, delimiter); strTrainfileContext.append(delimiter);
				strTrainfileContext.insert(0,m_Alphabet.ctxChar);
				strTrainfileBuffer.insert(0,strTrainfileContext);
			}
			strTrainfileContext.setLength(0);
		}
		intf.WriteTrainFile(m_Alphabet.GetTrainingFile(),strTrainfileBuffer.toString());
		strTrainfileBuffer.setLength(0);
	}
	
	private boolean isValidDelim(char c) {
		if (c==m_Alphabet.ctxChar) return false;
		for (int i=0; i<strTrainfileContext.length(); i++)
			if (strTrainfileContext.charAt(i)==c) return false;
		return true;
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
        	if (lastOutput==this) lastOutput=null;
        	if (isSeen() && !m_bCommitted) {
        		// Node will already have put itself into strTrainfileBuffer,
        		// i.e. for loading the next session's LM from disk.
        		// So train the current in-memory LM too...
        		commit(true);
        	}
        	super.DeleteNode();
        	m_bCommitted=false;
        }

        @Override public void Output() {
        	if (lastOutput!=null && lastOutput==Parent())
        		lastOutput=this;
        	//Case where lastOutput != Parent left to subclasses, if they want to
        	//Note if lastOutput==null, we leave it so - so the first letter after
        	// startup will be treated as a context switch.
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
			CAlphNode newNode = GetRoot(this, iNewOffset, true);
			IterateChildGroups(newNode, null, this);
			CAlphNode node = this;
			do {
				node = (CAlphNode)node.Parent();
				if (this.isSeen()) node.Seen(true);
				if (this.m_bCommitted) node.m_bCommitted=true;
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
			m_Interface.GetActiveInputFilter().pause();
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
        
       	/** Text to write to training file. Identical to output text,
       	 * except that if the user actually writes the context-switching
       	 * escape character, we double it up (as in \\).
       	 */
       	private String trainText() {
       		String s = outputText();
       		if (m_Alphabet.ctxChar!=null && 
       				s.length()==1 && 
           			m_Alphabet.ctxChar.charValue()==s.charAt(0))
       			return s+=s;
       		return s;
       	}
       	
       	@Override public void Output() {
       		if (m_pNCManager.GetBoolParameter(Ebp_parameters.BP_LM_ADAPTIVE)) {
       			//Record what we've written in buffer, to save to disk later for next session
       			if (lastOutput != Parent()) {
       				//Context changed. Flush to disk the old context + text written in it
       				WriteTrainFileFull(m_Interface);
       				
       				//Now extract the context in which this node is written.
       				// We'll get it from the LanguageModel, even though we could
       				// get it from the document/context (as the node is being output
       				// into that document/context now, so it must exist!)
       				tempList.clear(); strTrainfileContext.setLength(0);
       				m_LanguageModel.ContextToSymbols(checkCast(Parent()).context,tempList);
       				for (int i=0; i<tempList.size(); i++)
       					strTrainfileContext.append(m_Alphabet.GetText(tempList.get(i)));
       			}
       			//Now handle outputting of this node
       			lastOutput = this;
       			strTrainfileBuffer.append(trainText());
       		}
       		super.Output();
       	}
       	
       	@Override public void Undo() {
       		if (m_pNCManager.GetBoolParameter(Ebp_parameters.BP_LM_ADAPTIVE)) {
       			if (lastOutput==this) {
       				//Erase from training buffer (so the next session _won't_ learn
       				// from it), and move lastOutput backwards,
       				// iff this node was actually written (i.e. not rebuilt from context!)
       				String s = trainText();
       				if (strTrainfileBuffer.length()>=s.length()
       						&& strTrainfileBuffer.substring(strTrainfileBuffer.length()-s.length()).equals(s)) {
       					strTrainfileBuffer.delete(strTrainfileBuffer.length()-s.length(), strTrainfileBuffer.length());
       					//lastOutput = checkCast(Parent());//done by super.Undo
       				}
       			}
       		}
       		super.Undo();
       	}
       	
        @Override
        public void commit(boolean bNv) {
        	if (((CAlphNode)this).m_bCommitted || !bNv) return;
			//ACL this was used as an 'if' condition:
			assert (m_Symbol < m_Alphabet.GetNumberSymbols());
			//...before performing the following. But I can't see why it should ever fail?!
			
			if (m_pNCManager.GetBoolParameter(Ebp_parameters.BP_LM_ADAPTIVE)) {
				//try to commit (to in-memory LanguageModel)...if we have parent
				// (else, rebuilding (backwards) -> don't). This'll learn symbols
				// that may not be written to disk (i.e. if they are subsequently
				// deleted), as we can't "untrain" the in-memory LM, but we kinda
				// have to (we can't delay training the LM indefinitely!)
				CAlphNode parent = checkCast(Parent());
				if (parent!=null) {
					//learn symbol in the parent context,
					// and update this node's context with the new one
					// (assists later learning, plus in case this node
					// ever regenerates its children)
					((CAlphNode)this).context = m_LanguageModel.ContextLearningSymbol(parent.context, m_Symbol);
				}
			}
			super.commit(bNv);
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

    static <T> CAlphabetManager<T> makeAlphMgr(CDasherInterfaceBase intf, CLanguageModel<T> lm) {
    	return new CAlphabetManager<T>(intf, lm);
    }
    
}
