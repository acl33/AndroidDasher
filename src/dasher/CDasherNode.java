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

/**
 * <p>A DasherNode represents a node in the DasherModel's tree; it
 * is has a probability, children and one parent, and is typically
 * drawn as a box with a letter or symbol in it.</p>
 * 
 * <p>Most methods in DasherNode deal with exploring the tree: expanding
 * ({@link #PopulateChildren()}, collapsing, and actions to be taken when nodes
 * are entered, exited etc. - subclasses override these to provide a variety of
 * different effects.</p>
 * 
 * <p>Also implements the {@link Document} interface: the document represented
 * by each node, is the document that <em>would</em> exist <em>if</em> that node
 * were the last output (i.e. with none of its children output). Other versions
 * of the Document methods (specifically {@link #hasCharsBackTo(CDasherNode, int)}
 * and {@link #getCharBackTo(CDasherNode, int)}) allow reconstruction of said Document
 * from that existing in the Interface (i.e. which represents the document resulting
 * from the last node that actually has been output).</p>
 */
public abstract class CDasherNode implements Document {

	/** Cache whether only a single child of this node actually fitted on the screen
	 * (i.e. all others were off-screen - if any were onscreen but too small to render,
	 * they count as fitting on the screen!). Read & written by DasherModel & DasherViewSquare...
	 */
	public CDasherNode m_OnlyChildRendered;

	/** A viscous node is harder to move through, i.e. slower.
	 * The default is to return 1.0, i.e. "normal" viscosity and speed.
	 * @return multiplier to apply to speed (<1.0 = slower)
	 */
	public float getViscosity() {return 1.0f;}

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
	 * Note, the node expects a call to {@link #Reparent(CDasherNode, long, long)}
	 *  if it is to be the child of another node; this method initializes
	 *  Parent to null, and low &amp; high bounds to 0 &amp; max respectively 
	 * 
	 * @param Symbol Symbol number
	 * @param iphase Colour-cycling phase
	 * @param ColorScheme Colour scheme
	 * @param ilbnd Lower bound of cum. probability relative to parent
	 * @param ihbnd Upper bound of cum. probability relative to parent
	 * @param lm LanguageModel
	 * @param Colour Colour number
	 */
    protected void initNode(int iOffset, int Colour, String strDisplayText) {
    	if (strDisplayText==null || iOffset<-1) throw new IllegalArgumentException();
    	m_iOffset = iOffset;
		m_iLbnd = 0;
		m_iHbnd = CDasherModel.NORMALIZATION;
		//m_bHasAllChildren = false; //default at construction time, and cleared by DeleteNode()
		m_bSeen = false; //default
		m_iColour = Colour;
		m_Parent = null; //until Reparent called
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

	protected abstract CDasherInterfaceBase getIntf();
	
	/** <p>Gets the character that would be at the specified position,
	 * if this node were the last output.</p>
	 * <p>Subclasses <em>must</em> override to take into account any
	 * modifications to the document that the node performs; they <em>may</em>
	 * override to change the policy for handling {@link #isSeen()} nodes. (If
	 * the default policy, which follows, is ok, they can return <code>super.getCharAt(i)</code>,
	 * for <code>i</code> being the index in the document existing prior to this node being output.)</p>
	 * <ul>
	 * <li>For un-seen nodes, the default implementation recurses on the Parent
	 * if there is one (note unseen nodes must have a parent, unless they are
	 * the root node and nothing has been output), at the same index (as is
	 * appropriate for a node that produces no output).</li>
	 * <li>For seen nodes, there is a choice: we can recurse on the Parent,
	 * <em>or</em> we can fall back to the Document provided by {@link #getIntf()},
	 * i.e. which encapsulates all nodes that have been output. The default is
	 * to prefer the Parent to provide information for characters at offsets
	 * <em>before</em> this node, but to fall back to the interface (via {@link #getCharWithout(int)})
	 * for characters <em>after</em> where this node would output. This policy is appropriate
	 * for nodes which output text sequentially (e.g. Alphabet nodes), as using the
	 * tree of existing nodes may be more efficient/reliable than making API calls
	 * out to an external edit box etc. (e.g. on Android); however, nodes implementing
	 * (say) backwards movement, might prefer an alternative policy. 
	 * </ul>
	 */
	public Character getCharAt(int idx) {
		if (Parent()==null) return getIntf().getDocument().getCharAt(idx);
		if (isSeen() && idx > getOffset()) {
			//if we are seen, there necessarily is a last-output node...
			CDasherInterfaceBase intf = getIntf();
			return intf.getDocument().getCharAt(intf.getLastOutputNode().undoTransformIndices(this, idx));
		}
		return Parent().getCharAt(idx);
	}
	
	/** Get the index, within the document existing *with* this node output, of the
     * character that would be at the specified position in the document *without* this
     * node having been output
     * @param idx Position in the parent-node's document
     * @return Corresponding position in the document produced by this node
     */
    public int undoTransformIndex(int idx) {return idx;}

    /** Get the index, within the document existing *with* this node output, of the
     * character that would be at the specified position in the document that would
     * exist if *only* the specified node (an ancestor of this), and no other node
     * since, had been output.
     * @param upTo Last node to consider as having been output
     * @param idx Position in that node's document
     * @return Corresponding position in the document produced by this node
     */
    private int undoTransformIndices(CDasherNode upTo, int idx) {
        if (upTo!=this) idx = Parent().undoTransformIndices(upTo, idx); 
        return undoTransformIndex(idx);
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
	 * Attaches this Node to a given Parent, setting its bounds;
	 * or, changes the bounds, without moving the node to a new parent.
	 * The former occurs if we previously have no parent; the latter,
	 * if the new parent is the same as the existing parent.
	 * @param NewParent The new parent (may be the same as the existing)
	 */
	public void Reparent(CDasherNode NewParent, long iLower, long iUpper) {
		if (m_Parent!=NewParent) {
			assert m_Parent==null;
		    m_Parent = NewParent;
		    assert m_Parent.m_mChildren.get(m_Parent.m_mChildren.size()-1).m_iHbnd==iLower;
		    m_Parent.m_mChildren.add(this);
		}
	    m_iLbnd = iLower;
	    m_iHbnd = iUpper;
	}
	
	void transferChildrenTo(CDasherNode NewParent) {
		if (NewParent.ChildCount()!=0) throw new IllegalArgumentException("New (target) parent must have no children");
		for (CDasherNode c : m_mChildren)
			(c.m_Parent=NewParent).m_mChildren.add(c);
		m_mChildren.clear();
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

	public String toString() {
		return m_strDisplayText+"@"+getOffset();
	}
	
}
