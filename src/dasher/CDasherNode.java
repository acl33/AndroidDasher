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
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

/**
 * A DasherNode represents a node in the DasherModel's tree; it
 * is has a probability, children and one parent, and is typically
 * drawn as a box with a letter or symbol in it.
 * <p>
 * It is capable of finding a Node at a given Screen location
 * and of performing certain tree modifications (such as deleting
 * its children) but otherwise mainly acts as a data structure.
 */
public abstract class CDasherNode {

	//	Information required to display the node
	/**
	 * Phase; this is related to colour cycling from one generation
	 * to the next.
	 */
	protected final int m_iPhase;
	
	/**
	 * Colour number if this node is using a colour scheme defined
	 * by an instance of CCustomColours
	 */
	protected int m_iColour;
	
	/**
	 * Lower cumulative probability bound relative to our parent
	 */
	protected long m_iLbnd;
	
	/**
	 * Upper cumulative probability bound relative to our parent
	 */
	protected long m_iHbnd; 

	/**
	 * Indicates whether this node is currently drawable
	 */	
	protected boolean m_bAlive;
	
	/**
	 * Indicates whether this node's symbol has been output already
	 */
	protected boolean m_bSeen;
	 
	// Information internal to the data structure
	
	/**
	 * List of this node's child Nodes
	 */
	private final ArrayList<CDasherNode> m_mChildren = new ArrayList<CDasherNode>();
	
	/**
	 * Flag indicating whether all of this node's children are present
	 */
	protected boolean m_bHasAllChildren;

	/**
	 * Parent Node
	 */
	private CDasherNode m_Parent;

    /**
	 * This Node's display text
	 */
    public String m_strDisplayText;

    /**
     * Whether this Node shoves or not (ie. whether the symbols
     * of its children should be displaced to the right so as
     * not to obscure this one)
     */
    public boolean m_bShove;
	
    /**
	 * Creates a Node and sets its describing variables.
	 * 
	 * @param Parent Parent Node
	 * @param Symbol Symbol number
	 * @param iphase Colour-cycling phase
	 * @param ColorScheme Colour scheme
	 * @param ilbnd Lower bound of cum. probability relative to parent
	 * @param ihbnd Upper bound of cum. probability relative to parent
	 * @param lm LanguageModel
	 * @param Colour Colour number
	 */
    public CDasherNode(CDasherNode Parent, int iphase, long ilbnd, long ihbnd, int Colour) {
		m_iLbnd = ilbnd;
		m_iHbnd = ihbnd;
		// m_bHasAllChildren = false; (default)
		m_bAlive = true;
		// m_bSeen = false; (default)
		m_iPhase = iphase;
		m_iColour = Colour;
		m_Parent = Parent;
		if (Parent!=null) Parent.m_mChildren.add(this);
		// m_strDisplayText = new String();
		// WARNING: Whilst I think this is redundant it MAY get used uninitialised.
		
		/* CSFS: BUGFIX: There used to be a chance this was used uninitialised.
		 * Fixed.
		 */
		
	}

    /**
	 * Fills this Node's child list.
	 */
	public abstract void PopulateChildren();
	
	/**
	 * Removes all references to this node and deallocates storage
	 * if appropriate. Essentially we should do whatever is necessary
	 * to make this Node available for garbage collection.
	 * 
	 * @param Node Node to free
	 */
	//ACL??? public abstract void ClearNode(CDasherNode Node);
	
	/**
	 * Performs output appropriate to the Node, if any.
	 * <p>
	 * This method will be called when a user enters a given Node.
	 * <p>
	 * @param Added CSymbolProbs detailing what has been output should
	 * be added to this list
	 * @param iNormalization Normalisation value (total to which
	 * node probabilities always add up)
	 */
	public abstract void Output(ArrayList<CSymbolProb> Added, int iNormalization);
    
	/**
	 * Reverse or undo the output associated with this Node
	 * <p>
	 * This method is defined to do nothing in this class; if
     * a subclass does not wish to take action at this point, it
     * is safe to avoid overriding this. 
	 */
	public void Undo() {}

    /**
     * Signals that the user has entered the. Output
     * should not be performed at this stage.
     * <p>
     * This method is defined to do nothing in this class; if
     * a subclass does not wish to take action at this point, it
     * is safe to avoid overriding this.
     */
	public void Enter() {}
    
	/**
	 * Signals that the user has left this Node. Output should
	 * not be performed at this stage.
	 * <p>
	 * This method is defined to do nothing in this class; if
     * a subclass does not wish to take action at this point, it
     * is safe to avoid overriding this.
	 */
    public void Leave() {}

    /**
     * Rebuild the parent of this Node. (If the parent exists already, it can be returned!)
     * <p>
     * The new Node should have all of its children populated, one
     * of which should be this one.
     * <p>
     * See CAlphabetManager for a concrete example.
     * 
     * @param context ListIterator at end of characters immediately preceding this node
     * @return Parent of this Node.
     */
    public abstract CDasherNode RebuildParent(ListIterator<Character> context);
    
	/**
	 * At present, does nothing; Nodes will be "deleted" by virtue
	 * of their parents cutting them loose at the appropriate time,
	 * making them available for garbage collection.
	 *
	 */
    public void DeleteNode() {

	  /* CSFS: Lifted from C++ destructor function. Must be called for all nodes before they
	   * are garbage collected.
	   */
		
		// ClearNode and ReleaseContext both do nothing, so I'm experimenting with this method doing nothing.
		
      // Release any storage that the node manager has allocated,
	  // unreference ref counted stuff etc.

	  //m_NodeManager.ClearNode(this);

	  //Delete_children();
	  //if(m_Context != null)
	  //  m_LanguageModel.ReleaseContext(m_Context);
	}


    /**
     * Gets m_iLbnd
     * 
     * @return m_iLbnd
     */
	public long Lbnd() {
	  return m_iLbnd;
	}

	/**
     * Gets m_iHbnd
     * 
     * @return m_iHbnd
     */
	public long Hbnd() {
	  return m_iHbnd;
	}

	/**
	 * Gets the difference between m_iLbnd and m_iHbnd.
	 * 
	 * @return Range
	 */
	public long Range() {
	  return m_iHbnd - m_iLbnd;
	}

	/**
	 * Gets a (read-only) reference to this Node's child list. 
	 * 
	 * @return m_mChildren
	 */
	public List<CDasherNode> Children() {
		return Collections.unmodifiableList(m_mChildren);
	}
	
	/**
	 * Move the supplied ListIterator backwards over the characters output by this node.
	 * ACL this is a temporary hack solution, and is wrong - it uses the display text, not
	 * the edit text. TODO.
	 * @param it
	 */
	public void absorbContext(ListIterator<Character> it) {
		for (int i=m_strDisplayText.length(); i-->0; )
			if (it.previous() != m_strDisplayText.charAt(i))
				throw new IllegalArgumentException("Previous character "+it.next()+" but node would have output "+m_strDisplayText.substring(0,i+1));		
	}
	
	/**
	 * Returns the size of our child list.
	 * 
	 * @return Size of m_mChildren.
	 */
	public synchronized int ChildCount() {
	    return m_mChildren.size();
	}

	/**
	 * Gets this node's parent.
	 * 
	 * @return Parent node.
	 */
	public CDasherNode Parent() {
	    return m_Parent;
	}
	  
	/**
	 * Attaches this Node to a given Parent.
	 * 
	 * @param NewParent Our new parent
	 */
	public void SetParent(CDasherNode NewParent) {
	    m_Parent = NewParent;
	}
	
	/**
	 * Sets this node's probabilities.
	 * 
	 * @param iLower New Lbnd
	 * @param iUpper New Hbnd
	 */
	public void SetRange(long iLower, long iUpper) {
	    m_iLbnd = iLower;
	    m_iHbnd = iUpper;
	}

	/**
	 * Determines if this node is currently drawable.
	 * 
	 * @return m_bAlive
	 */
	public boolean Alive() {
	    return m_bAlive;
	}
	 
	/**
	 * Sets this node's Alive flag
	 * 
	 * @param b new value
	 */
	public void Alive(boolean b) {
	    m_bAlive = b;
	}

	/**
	 * Sets this node's Alive flag to false
	 *
	 */
	public void Kill() {
	    m_bAlive = false;
	}

	/**
	 * Gets this node's Seen flag
	 * 
	 * @return Seen
	 */
	public boolean isSeen() {
	    return m_bSeen;
	} 

	/**
	 * Sets this node's Seen flag
	 * 
	 * @param seen new value
	 */
	public void Seen(boolean seen) {
	    m_bSeen = seen;
	}
	
	/** Called when we've gone far enough into the node that it is no
	 *  longer a root (i.e. one of its children is!)
	 */
	public void commit() {}

	/**
	 * Gets this node's colour-cycling phase
	 * 
	 * @return Phase
	 */
	public int Phase() {
	    return m_iPhase;
	}
	// DJW Sort out the colour scheme / phase confusion
	  
	/**
	 * Gets this Node's colour index
	 * 
	 * @return Colour index
	 */
	public int Colour() {
	    return m_iColour;
	} 

	/**
	 * Checks whether all of this Node's children exist at present
	 * 
	 * @return m_bHasAllChildren
	 */
    public boolean HasAllChildren() {
	    return m_bHasAllChildren;
	}
	 
    /**
     * Sets the HasAllChildren flag
     *
     * @param val new value
     */
    public void SetHasAllChildren(boolean val) {
	    m_bHasAllChildren = val;
	}
    
    /**
     * Gets the probability associated with this node's most
     * probable child.
     * 
     * @return Probability
     */
    public long MostProbableChild() {
    	
    	long iMax = 0;
    	    	
    	for(CDasherNode i : this.Children()) {
    		if(i.Range() > iMax) iMax = i.Range();
    	}
    	
    	return iMax;
    }

   
    /**
     * Finds the node under a given mouse location, given our boundaries
     * in Dasher space. Essentially this iterates through our children
     * checking whether the point falls within each child, and if so recalls
     * this method in the child. If no children match, we replace a reference
     * to ourselves.
     * <p>
     * The "mouse position" may of course be replaced by any other
     * point we want to find the node under. 
     * 
     * @param iNormalization Normalization value, usually LP_NORMALIZATION
     * @param miY1 Our upper bound in Dasher co-ordinate space
     * @param miY2 Our lower bound in Dasher co-ordiante space
     * @param miMousex Current mouse position X
     * @param miMousey Current mouse position Y
     * @return Node under this mouse position.
     */
    public CDasherNode Get_node_under(long iNormalization, long miY1, long miY2, long miMousex, long miMousey) {
		
		long miRange = miY2 - miY1;
        m_bAlive = true;
        
        for(CDasherNode i : this.Children()) {
        	long miNewy1 = miY1 + (miRange * i.m_iLbnd) / iNormalization;
        	long miNewy2 = miY1 + (miRange * i.m_iHbnd) / iNormalization;
        	if(miMousey < miNewy2 && miMousey > miNewy1 && miMousex < miNewy2 - miNewy1)
        		return i.Get_node_under(iNormalization, miNewy1, miNewy2, miMousex, miMousey);
        }
        return this;
	}
	
//		 kill ourselves and all other children except for the specified
//		 child
//		 FIXME this probably shouldn't be called after history stuff is working
	
    /**
     * Delete our children except for a given one, the delete ourself.
     * 
     * @param pChild Child to keep
     */
    public void OrphanChild(CDasherNode pChild) {
		  assert(ChildCount() > 0);
		  
		  /* CSFS: Iteration through children changed as per previous
		   * and also the new DeleteNode() routines used.
		   * Finally delete replaced with orphaning the object,
		   * leaving it ready for GC.
		   */
	  
		  for(CDasherNode i : this.Children()) {
			  if(i != pChild) {
				  i.Delete_children();
				  i.DeleteNode();
			  }
			  
		  }
		  //"Delete"ing anything is a misnomer, of course, due to GC. So same as:
		  Delete_children();
	}


    /**
     * Deletes the nephews of a given child; that is to say,
     * deletes its siblings' children. 
     * 
     * @param pChild Child whose children will NOT be deleted.
     */
	public void DeleteNephews(CDasherNode pChild) {
		  assert(ChildCount() > 0);
		  
		  for(CDasherNode i : this.Children()) {
		    if(i != pChild) {
		      i.Delete_children();
		    }

		  }
	}

	/**
	 * Clears the Children list and sets our HasAllChildren flag
	 * to false; this will leave this section of tree unrefrenced
	 * and available for garbage collection.
	 *
	 */
	public void Delete_children() {
		
		//for(CDasherNode i : this.GetChildren()) {
			// i.DeleteNode(); (stub method)
		//	i = null;
		//}
		m_mChildren.clear(); // This should be enough to render them GC-able.
		SetHasAllChildren(false);
	}

//		 Gets the probability of this node 
	
	/**
	 * Gets this node's probability
	 * 
	 * @param iNormalization Norm value, usually LP_NORMALIZATION
	 */
	public double GetProb(int iNormalization) {    
		  return (double) (m_iHbnd - m_iLbnd) / (double) iNormalization;
	}

}
