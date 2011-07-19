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

	/** Cache whether only a single child of this node actually fitted on the screen
	 * (i.e. all others were off-screen - if any were onscreen but too small to render,
	 * they count as fitting on the screen!). Read & written by DasherModel & DasherViewSquare...
	 */
	public CDasherNode m_OnlyChildRendered;
	
	//	Information required to display the node
	
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
	 * Indicates whether this node's symbol has been output already
	 */
	protected boolean m_bSeen;
	 
	// Information internal to the data structure
	
	/**
	 * List of this node's child Nodes
	 */
	private final ArrayList<CDasherNode> m_mChildren = new ArrayList<CDasherNode>();
	
	/**
	 * Parent Node
	 */
	private CDasherNode m_Parent;

    /**
	 * This Node's display text
	 */
    public String m_strDisplayText;

    /** Cost for expanding or collapsing the node. Set and read
     *  by ExpansionPolicy - but easiest to store here... */
    public double m_dCost;
    
    private int m_iOffset;
    public int getOffset() {return m_iOffset;}
    /**
     * Whether this Node shoves or not (ie. whether the symbols
     * of its children should be displaced to the right so as
     * not to obscure this one). Default is to return true, as that's
     * what we want for almost all nodes (exception being control nodes,
     * which aren't implemented yet anyway!)
     */
    public boolean shove() {return true;}
    
    /** Whether to render this node, i.e. both fill it in its own colour, and outline it.
     * Setting to false means the node appears as part of its parent (irrespective of colours)
     */
    public boolean visible() {return true;}
	
    protected CDasherNode() {
    	
    }
    /**
	 * Initializes a Node and sets its describing variables.
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
    protected void initNode(CDasherNode Parent, int iOffset, long ilbnd, long ihbnd, int Colour, String strDisplayText) {
    	if (ihbnd < ilbnd || strDisplayText==null) throw new IllegalArgumentException();
    	m_iOffset = iOffset;
		m_iLbnd = ilbnd;
		m_iHbnd = ihbnd;
		//m_bHasAllChildren = false; //default at construction time, and cleared by DeleteNode()
		m_bSeen = false; //default
		m_iColour = Colour;
		m_Parent = Parent;
		if (Parent!=null) Parent.m_mChildren.add(this);
		this.m_strDisplayText = strDisplayText;
		numNodes++;
	}
    
    /** The number of node objects currently accessible in the tree.
     * (There may be more than this iff including further objects
     * awaiting GC)
     */
    private static int numNodes=0;
    
    public static int currentNumNodeObjects() {return numNodes;}

    public abstract int ExpectedNumChildren();
    
    /**
	 * Fills this Node's child list.
	 */
	public abstract void PopulateChildren();
	
	/**
	 * Performs output appropriate to the Node, if any.
	 * <p>
	 * This method will be called when a user enters a given Node.
	 */
	public abstract void Output();
    
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
     * @return Parent of this Node.
     */
    public abstract CDasherNode RebuildParent();
    
	/**
	 * At present, does nothing; Nodes will be "deleted" by virtue
	 * of their parents cutting them loose at the appropriate time,
	 * making them available for garbage collection.
	 *
	 */
    public void DeleteNode() {
	  Delete_children();
	  m_Parent=null;
	  numNodes--;
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
	 * Default does nothing, appropriate only for nodes which don't output anything.
	 */
	public void absorbContext(ListIterator<Character> it) {}
	
	/**
	 * Returns the size of our child list. (Avoids allocating unmodifiable lists, etc.)
	 * 
	 * @return Size of m_mChildren.
	 */
	public int ChildCount() {
	    return m_mChildren.size();
	}

	/**
	 * Returns the <code>i</code>th child of this node, so we can iterate through
	 * without allocating an iterator.
	 * @param i desired index: <code>0<=i<ChildCount()</code>
	 * @return the <code>i</code>th child node
	 */
	public CDasherNode ChildAtIndex(int i) {
		return m_mChildren.get(i);
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
		assert m_Parent==null;
	    m_Parent = NewParent;
	    m_Parent.m_mChildren.add(this);
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
	public void commit(boolean commit) {}

	/**
	 * Gets this Node's colour index
	 * 
	 * @return Colour index
	 */
	public int Colour() {
	    return m_iColour;
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
		  assert (pChild.Parent()==this);
		  pChild.m_Parent = null;
		  for(CDasherNode i : this.Children()) {
			  if(i != pChild)
				  i.DeleteNode();
		  }
		  m_mChildren.clear();
		  DeleteNode();
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
			  if(i != pChild) i.Delete_children();
		  }
	}

	/**
	 * Clears the Children list and sets our HasAllChildren flag
	 * to false; this will leave this section of tree unrefrenced
	 * and available for garbage collection.
	 *
	 */
	public void Delete_children() {
		
		for(int i=0, j=ChildCount(); i<j; i++)
			 ChildAtIndex(i).DeleteNode();
		m_mChildren.clear(); // This should be enough to render them GC-able.
		m_OnlyChildRendered = null;
	}

	/** "Have to" make colour mutable because of a case in CAlphabetManager.CGroupNode,
	 * where a group node has to override the colour of a unique child to match the parent.
	 * @param colour new colour for this node
	 */
	/*package*/ void setColour(int colour) {
		this.m_iColour = colour;
	}

}
