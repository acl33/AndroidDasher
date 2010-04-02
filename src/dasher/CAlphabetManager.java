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

public class CAlphabetManager {

	/**
	 * Pointer to the LanguageModel used in determining the
	 * relative probability assigned to new Nodes. 
	 */
	protected final CLanguageModel m_LanguageModel;
	
	/**
	 * Pointer to the DasherModel which performs some of the
	 * work in the course of producing probabilities.
	 */
    protected final CDasherModel m_Model;
    
    // Undocumented, as these are present as caches only.
    protected final int SPSymbol, ConvertSymbol, ContSymbol;
    
    /**
     * Pointer to the current Alphabet, used to find out what a
     * given character looks like typed (for the purposes
     * of output) and displayed (if growing the DasherNode tree).
     */
    protected final CAlphabet m_Alphabet;
    
    protected final ArrayList<Integer> m_Colours;
    protected final ArrayList<String> m_DisplayText;
    // Both undocumented (caches)
        
    /**
     * Sole constructor: produces an AlphabetManager linked to a
     * given Model and LanguageModel. These cannot be set after
     * the Manager has been created; as such the Manager must
     * be created last of these three.
     * 
     * @param Model Linked DasherModel
     * @param LanguageModel Linked LanguageModel
     */
    
    public CAlphabetManager( CDasherModel Model, CLanguageModel LanguageModel) {
    	
    	this.m_LanguageModel = LanguageModel;
    	this.m_Model = Model;
    	SPSymbol = Model.GetSpaceSymbol();
    	ConvertSymbol = Model.GetStartConversionSymbol();
    	ContSymbol = Model.GetControlSymbol();
    	
    	m_Alphabet = Model.GetAlphabet();
    	m_Colours = Model.GetColours();
    	m_DisplayText = m_Alphabet.GetDisplayTexts();
    	
    	/* Caching these as the repeated requests which were twice deferred were
    	 * actually taking 5% of our runtime
    	 */
    }

    /**
     * Creates a new root CDasherNode with the supplied parameters.
     */
    public CDasherNode GetRoot(CDasherNode Parent, long iLower, long iUpper, int iSymbol, String string) {
    	  int iColour;
    	  
    	  if(iSymbol == 0)
    	    iColour = 7;
    	  else
    	    iColour = m_Colours.get(iSymbol);

    	  CContextBase ctx = m_LanguageModel.CreateEmptyContext();
    	  m_LanguageModel.EnterText(ctx, string);
    	  
    	  CAlphNode NewNode = new CAlphNode(Parent, iSymbol, 0,
    			  (iSymbol== m_Model.GetSpaceSymbol()) ? EColorSchemes.Special1 : EColorSchemes.Nodes1,
    					  iLower, iUpper, iColour, ctx);
    	  
    	  NewNode.m_bShove = true;
    	  NewNode.m_BaseGroup = m_Alphabet.m_BaseGroup;
    	  NewNode.m_strDisplayText = m_DisplayText.get(iSymbol);
    	  NewNode.Seen(true);

    	  return NewNode;
    }
    //ACL TODO had to make this package-visible as a hack to extract SGroupInfo...
    class CAlphNode extends CDasherNode {
    
    	private boolean bCommitted;
    	
    	/**
    	 * Language model context corresponding to this node's
    	 * position in the tree.
    	 */
    	protected final CContextBase m_Context;
    	
    	/**
    	 * Symbol number represented by this node
    	 */
    	protected final int m_Symbol;	// the character to display
    	
    	/**
    	 * Root of the tree of groups into which this Node's
    	 * children are arranged.
    	 */
    	public SGroupInfo m_BaseGroup;
    	
        public CAlphNode(CDasherNode Parent, int Symbol, int iphase,
				EColorSchemes ColorScheme, long ilbnd, long ihbnd,
				int Colour, CContextBase context) {
			super(Parent, iphase, ColorScheme, ilbnd, ihbnd, Colour);
			this.m_Symbol = Symbol; 
			this.m_Context = context;
			// TODO Auto-generated constructor stub
		}

		public void PopulateChildren( ) {
        	PopulateChildrenWithSymbol( this, -2, null );
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
        public void Output( ArrayList<CSymbolProb> Added, int iNormalization) {
        	m_Model.m_bContextSensitive = true;
        	if(m_Symbol != 0) { // Ignore symbol 0 (root node)
        		CEditEvent oEvent = new CEditEvent(1, m_Alphabet.GetText(m_Symbol));
        		m_Model.InsertEvent(oEvent);
        		
        		// Track this symbol and its probability for logging purposes
        		if (Added != null) {
        			CSymbolProb sItem = new CSymbolProb();
        			sItem.sym    = m_Symbol;
        			sItem.prob   = GetProb(iNormalization);
        			
        			Added.add(sItem);
        		}
        	}
        }

        /**
         * Generates an EditEvent announcing that the character represented
         * by this Node should be removed.
         * 
         * @param Node Node whose symbol we wish to remove.
         */    
        public void Undo() {
        	if(m_Symbol != 0) { // Ignore symbol 0 (root node)
        		CEditEvent oEvent = new CEditEvent(2, m_Alphabet.GetText(m_Symbol));
        		m_Model.InsertEvent(oEvent);
        		bCommitted = false;
        	}
        }

		/**
		 * Reconstructs the parent of a given node, in the case that
		 * it had been deleted but the user has now backed off far
		 * enough that we need to restore.
		 * <p>
		 * This will generate an EditContextEvent to try to extend
		 * its knowledge of the current context; this is necessary
		 * because Dasher only buffers a small amount of context
		 * internally. Typically a UI component is expected to reply
		 * with the appropriate context.
		 * <p>
		 * In the event that context is not available internally
		 * and the dispatched EditContextEvent is not passed a new
		 * context, the root symbol is created and returned.
		 * 
		 * @param iGeneration The depth in the tree of this node.
		 * @param charsBefore TODO
		 * @return The newly created parent, which may be the root node.
		 */
		public CDasherNode RebuildParent(ListIterator<Character> charsBefore) {
			
			/* This used to clear m_Model.strContextBuffer. Removed as per notes
			 * at the top of CDasherInterfaceBase.
			 */
			
			/* This reconstitutes the parent of the current root in the case
			 * that we've backed off far enough to need to do so.
			 */
			
			StringBuilder ctx = new StringBuilder();
			while (charsBefore.hasPrevious() && ctx.length()<5) //ACL TODO, don't fix on 5 chars!
				ctx.append(charsBefore.previous());
			String strContext = ctx.reverse().toString();
			
			ArrayList<Integer> vSymbols = new ArrayList<Integer>();
			m_LanguageModel.getAlphabet().GetSymbols(vSymbols, strContext);
			
			CAlphNode NewNode;
			
			if(vSymbols.isEmpty()) {
				
				/* In the case that there isn't enough context to rebuild the tree,
				 * we magically reappear at the root node.
				 */
				CContextBase oContext = m_LanguageModel.CreateEmptyContext();
				m_LanguageModel.EnterText(oContext, ". ");
				
				NewNode = new CAlphNode(null, 0, 0,  EColorSchemes.Nodes1, 0, 0, 7, oContext);
			}
			else {
				
				EColorSchemes NormalScheme, SpecialScheme;
				if((ColorScheme() == EColorSchemes.Nodes1) || (ColorScheme() == EColorSchemes.Special1)) {
					NormalScheme = EColorSchemes.Nodes2;
					SpecialScheme = EColorSchemes.Special2;
				}
				else {
					NormalScheme = EColorSchemes.Nodes1;
					SpecialScheme = EColorSchemes.Special1;
				}
				
				EColorSchemes ChildScheme;
				if(vSymbols.get(vSymbols.size() - 1) == m_Model.GetSpaceSymbol())
					ChildScheme = SpecialScheme;
				else
					ChildScheme = NormalScheme;
				
				int NodeColour = m_Colours.get(vSymbols.get(vSymbols.size() - 2));
				
				if(NormalScheme == EColorSchemes.Nodes2) {
					NodeColour += 130;
				}
				
				CContextBase oContext = (m_LanguageModel.CreateEmptyContext());
				
				for(int i = (0); i < vSymbols.size() - 1; ++i)
					m_LanguageModel.EnterSymbol(oContext, vSymbols.get(i));
				
				NewNode = new CAlphNode(null, vSymbols.get(vSymbols.size() - 2), 0, ChildScheme, 0, 0, NodeColour, oContext);
			}
			
			NewNode.m_bShove = true;
			NewNode.Seen(true);
			NewNode.m_BaseGroup = m_Alphabet.m_BaseGroup;
			
			PopulateChildrenWithSymbol( NewNode, m_Symbol, this );
			if(m_Model.GetBoolParameter(Ebp_parameters.BP_LM_REMOTE)) {
				WaitForChildren(NewNode);
			}
			
			SetParent(NewNode);
			
			return NewNode;
		}
        
		@Override
		public void commit() {
			if (bCommitted) return;
			bCommitted=true;
			//ACL this was used as an 'if' condition:
			assert (m_Symbol < m_Alphabet.GetNumberTextSymbols());
			//...before performing the following. But I can't see why it should ever fail?!
			
			if (m_Model.GetBoolParameter(Ebp_parameters.BP_LM_ADAPTIVE)) {
				if (Parent() instanceof CAlphNode) {
					CContextBase learnCtx = m_LanguageModel.CloneContext(((CAlphNode)Parent()).m_Context);
					m_LanguageModel.LearnSymbol(learnCtx, m_Symbol);
					m_LanguageModel.ReleaseContext(learnCtx);
				}
				//else - do we do anything? should mean root nodes ok...
			}
			//ACL ...and using that (mutable!) context makes no sense if we ever reverse & rewrite!
		}
    }
        
    /**
     * Populates the children of a given Node. This function
     * calls CLanguageModel.getProbs on the Context associated
     * with this Node; its function is identical to the four-arg
     * version of the function called upon the same probabilities.
     * 
     * @param Node Node whose children we wish to populate.
     * @param iExistingSymbol Symbol of child node which already exists, if any; -2 if none.
     * @param ExistingChild Reference to the pre-existing child, if one exists.  
     */

    public void PopulateChildrenWithSymbol(CAlphNode Node, int iExistingSymbol, CDasherNode ExistingChild) {
    	long[] cum = m_Model.GetProbs(Node.m_Context, (int)m_Model.GetLongParameter(Elp_parameters.LP_NORMALIZATION));
    	
    	PopulateChildrenWithSymbol(Node, iExistingSymbol, ExistingChild, cum);
    }
    
    /**
     * Creates the children of a given Node given a set of probabilities
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
    public void PopulateChildrenWithSymbol( CAlphNode Node, int iExistingSymbol, CDasherNode ExistingChild, long[] cum) {
    	
    	// CSFS: In the name of efficiency have removed ArrayLists from all of this.
    	// It now uses a raw array and runs much faster.
    	
    	// Actually create the children here
    	
    	// FIXME: this has to change for history stuff and Japanese dasher
    	//ArrayList<Integer> newchars = new ArrayList<Integer>(); // place to put this list of characters
    	    	
    	// CSFS: At the moment, newchars(j) = j as that's all the model puts in there.
    	// To save time I've put this in for now.
    	
    	int iChildCount = m_Alphabet.GetNumberSymbols();    //newchars.size();
    	
//  	DASHER_TRACEOUTPUT("ChildCount %d\n", iChildCount);
    	// work out cumulative probs in place
    	for(int i = 1; i < iChildCount; i++)
    		cum[i] += cum[i-1];
    	
    	// create the children
    	EColorSchemes NormalScheme, SpecialScheme;
    	if((Node.ColorScheme() == EColorSchemes.Nodes1) || (Node.ColorScheme() == EColorSchemes.Special1)) {
    		NormalScheme = EColorSchemes.Nodes2;
    		SpecialScheme = EColorSchemes.Special2;
    	}
    	else {
    		NormalScheme = EColorSchemes.Nodes1;
    		SpecialScheme = EColorSchemes.Special1;
    	}
    	
    	EColorSchemes ChildScheme;
    	
    	long iLbnd = 0;
    	
    	for(int j = 0; j < iChildCount; j++) {
    		if(j == SPSymbol)
    			ChildScheme = SpecialScheme;
    		else
    			ChildScheme = NormalScheme;
    		CDasherNode NewNode;
    		
    		if(j == ContSymbol)
    			NewNode = m_Model.GetRoot(1, Node, iLbnd, cum[j], 0);
    		else if(j == ConvertSymbol) {
    					NewNode = m_Model.GetRoot(0, Node, iLbnd, cum[j], 0);
    					NewNode.Seen(false);
    		}
    		else if( j == iExistingSymbol) {
    				NewNode = ExistingChild;
    				NewNode.SetRange(iLbnd, cum[j]);
    		}
    		else {
    			int iColour = (m_Colours.get(j));
    			// This is provided for backwards compatibility. 
    			// Colours should always be provided by the alphabet file
    			if(iColour == -1) {
    				if(j == SPSymbol) {
    					iColour = 9;
    				}
    				else if(j == ContSymbol) {
    					iColour = 8;
    				}
    				else {
    					iColour = (j % 3) + 10;
    				}
    			}

    			// Loop colours if necessary for the colour scheme
    			if((ChildScheme.ordinal() % 2) == 1 && iColour < 130) {    // We don't loop on high
    				iColour += 130;
    			}

    			//ACL make the new node's context ( - this used to be done only in PushNode(),
    			// before calling populate...)
    			CContextBase cont;
    			if (j < m_Alphabet.GetNumberTextSymbols() && j > 0) {
					// Normal symbol - derive context from parent
					cont = m_LanguageModel.CloneContext(Node.m_Context);
					m_LanguageModel.EnterSymbol(cont, j);
				} else {
					// For new "root" nodes (such as under control mode), we want to 
					// mimic the root context
					cont = m_LanguageModel.CreateEmptyContext();
					//      EnterText(cont, "");
				}
    			CAlphNode n = new CAlphNode(Node, j, j, ChildScheme, iLbnd, cum[j], iColour, cont);
    			n.m_bShove = true;
    			n.m_BaseGroup = m_Alphabet.m_BaseGroup;
    			NewNode = n;
    		}

    		NewNode.m_strDisplayText = m_DisplayText.get(j);
    		iLbnd = cum[j];
    	}
    }

    /**
     * Stub; to be used if in future there is work to be done
     * upon deleting a node.
     * <p>
     * Note that this method should NOT actually destroy
     * the node, but should remove the Manager's references
     * to it, if any exist.
     * 
     * @param Node Node to be deleted
     */
    public void ClearNode( CDasherNode Node ) {
    	// Should this be responsible for actually doing the deletion

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
