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

import java.util.LinkedList;

/**
 * Dasher 'world' data structures and dynamics.
 * <p>
 * The DasherModel represents the current state of Dasher.
 * It contains a tree of DasherNodes, knows the current viewpoint
 * and knows how to evolve the viewpoint.
 * <p>
 * It also plays host to the LanguageModel, being responsible
 * for its creation and passing some requests to it from external
 * components.
 * <p>
 * The Model does not know how to draw Dasher; this is the responsibility
 * of a DasherView.
 */
public class CDasherModel extends CFrameRate {
	/** Extent of Y-axis (in theory! Exc. nonlinearities). TODO, make an int? */
	public static final long MAX_Y=4096;
	/** Coordinates of cross-hair. TODO, make ints? */
	public static final long CROSS_X=2048, CROSS_Y=2048;
	/** Interval i.e. denominator for ranges of child nodes wrt parent */
	public static final long NORMALIZATION=1<<16;
	
	/**
	 * Node which is currently root of the Node tree.
	 */
	protected CDasherNode m_Root;
	
	/**
	 * List of previous roots, to which we may revert if the
	 * user backs off sufficiently.
	 */
	protected LinkedList<CDasherNode> oldroots = new LinkedList<CDasherNode>();
	
	/**
	 * Root node's lower bound in Dasher world co-ordinates
	 */	
	protected long m_Rootmin;
	
	/**
	 * Root node's upper bound in Dasher world co-ordinates
	 */
	protected long m_Rootmax;
	
	/**
	 * Amount by which the last rendering of the display was offset from the underlying model
	 * of what's "really" happening (used to smooth offsets/bounces over several frames in button modes)
	 */
	protected long m_iDisplayOffset;
	
	/** Amount by which to offset future renderings of display. TODO, use LP_ZOOMSTEPS? */
	private int[] offsetQueue=new int[24];
	
	/** Index into offsetQueue of next value to use (wraps round) */
	private int nextOffset;
	
	/**
	 * Minimum allowable value of m_Rootmin
	 */
	private static final long ROOTMIN_MIN = Long.MIN_VALUE / NORMALIZATION / 2;
	
	/**
	 * Maximum allowable value of m_Rootmax
	 */
	private static final long ROOTMAX_MAX = Long.MAX_VALUE / NORMALIZATION / 2;
	
	/**
	 * Record of 'amount of information' entered so far, for logging purposes.
	 */
	protected double total_nats;            // Information entered so far
	
	protected double m_dLastNats;
	protected long m_iLastMinSize = MAX_Y;
	
	/* CSFS: Converted a struct in the original C into this class */
	
	/**
	 * List of points which we are to go (i.e. intermediate points,
	 * interpolated between previous location and user-supplied dest),
	 * in reverse order i.e. such that element 0 should arrive at
	 * the destination.
	 */
	private long[] m_gotoMin=new long[1], m_gotoMax=new long[1];	

	/** The next element of m_gotoMin/Max to use (-1 = nothing scheduled) */
	private int m_iGotoNext=-1; 
	
	private CDasherNode m_pLastOutput;
	
	/**
	 * Initialise a new DasherModel. Note that you'll still have to call
	 * {@link #SetNode(CDasherNode)} before you can use it...
	 */
	public CDasherModel(CDasherComponent creator) {
		super(creator); 
		
		HandleEvent(Elp_parameters.LP_NODE_BUDGET);
	}
	
	/**
	 * The Model responds to changes in the following parameters:
	 * <p>
	 * <i>LP_MAX_BITRATE</i>: Informs the CFrameRate which performs
	 * frame rate tracking for us of the new frame rate.
	 * <p>
	 * <i>BP_CONTROL_MODE</i>: Rebuilds the model (calls RebuildAroundNode) to include/exclude a control node.
	 * <p>
	 * <i>LP_UNIFORM</i>: Updates our internally cached value (uniformAdd)
	 * to reflect the new value. 
	 */	
	public void HandleEvent(EParameters eParam) {
		super.HandleEvent(eParam); //framerate watches LP_MAX_BITRATE
		if (eParam == Elp_parameters.LP_NODE_BUDGET) {
			pol = new AmortizedPolicy((int)GetLongParameter(Elp_parameters.LP_NODE_BUDGET));
		}
	}
	
	/**
	 * Makes a specified child the new root node, deleting all of its siblings
	 * (by virtue of instructing its parent, the current root, to DeleteNephews.
	 * <p>
	 * This function does not make any checks that the specified
	 * Node really is a child of the current root, or that we are
	 * in a sensible position to redefine the root; the behaviour
	 * if this is not ensured before calling is undefined.
	 * <p>
	 * The former root node will be added to oldroots, in case
	 * we need to recover it in future.
	 * <p>
	 * m_RootMax, m_TargetMax and their brethren are also updated
	 * to take into account the new root's Hbnd and Lbnd values.
	 * <p>
	 * 
	 * @param whichchild Child to make the new root node.
	 * @throws NullPointerException if the specified node is not a strict descendant of the current root.
	 */
	protected void Make_root(CDasherNode whichchild){
		if (whichchild.Parent() != m_Root)
			Make_root(whichchild.Parent());
		
		m_Root.commit(true);
		
		oldroots.addLast(m_Root);
		
		m_Root = whichchild;
		
		// Commented out so that network mode doesn't encounter problems
		// when backing off to the point that we need to call PopulateChildrenWithSymbol.
		// This method was not tolerant of the asynchronous behaviour necessitated
		// when we might have significant delay.
		
		while(oldroots.size() > 10) {
			
			/* CSFS: All rewritten to use LinkedList commands instead
			 * of deque operations. In the original version, it deleted
			 * the node which was about to get pop_front'd; hopefully
			 * this will run the destructor and cause the garbage collector
			 * to come get it.
			 */
			
			oldroots.get(0).OrphanChild(oldroots.get(1)); //deletes itself too.
			// oldroots.set(0, null);
			oldroots.removeFirst();
		}
		
		/* CSFS: These formerly used myint and have been changed to long */
		
		long range = m_Rootmax - m_Rootmin;
		m_Rootmax = m_Rootmin + (range * m_Root.Hbnd()) / NORMALIZATION;
		m_Rootmin = m_Rootmin + (range * m_Root.Lbnd()) / NORMALIZATION;
		
		for (int i = m_iGotoNext; i>=0; i--) {
			//sgi contains pairs of coordinates for the _old_ root; we need to update it to contain the corresponding
			// coordinates for the _new_ root, which will be somewhat closer together.
			// However, it's possible that the existing coordinate pairs may be bigger than would actually be allowed
			// for a root node (and hence, when we try to use them in nextScheduledStep, we'll forcibly reparent);
			// this means that we may have difficulty working with them...
			final long r = m_gotoMax[i] - m_gotoMin[i];
			m_gotoMax[i] = m_gotoMin[i] + //r * m_Root.Hbnd() / iNorm; //rewrite to ensure no overflow:
				(r / NORMALIZATION) * m_Root.Hbnd() + ((r % NORMALIZATION) * m_Root.Hbnd())/NORMALIZATION;
		    m_gotoMin[i] += // r * m_Root.Lbnd() / iNorm;
		    	(r/NORMALIZATION) * m_Root.Lbnd() + ((r % NORMALIZATION) * m_Root.Lbnd())/NORMALIZATION;
		}
	}
	
	/**
	 * Delete the root and all nodes in the queue of old root nodes.
	 * This should be called when the previous context is no longer valid.
	 */
	protected void DeleteRoot() {
		if (m_Root==null) return; //could assert oldroots empty & m_pLastOutput==null...
		if (m_pLastOutput!=null) m_pLastOutput.Leave();
		(oldroots.isEmpty() ? m_Root : oldroots.get(0)).DeleteNode();
		oldroots.clear();
		m_Root=null;
	}

	/**
	 * Reconstructs the existing root's parent, so that we can
	 * back out of it.
	 * <p>
	 * This will first try to use an old Node stored in the oldroots
	 * list, but if this is empty will use the AlphabetManager's
	 * RebuildParent method to instantiate a new parent.
	 * <p>
	 * If unsuccessful in building a parent, or if the current
	 * root is the base node in which Dasher starts, the method
	 * will return without performing any action.
	 * <p>
	 * m_RootMax, m_TargetMax and their brethren will also be
	 * appropriately updated.
	 * 
	 * @return true if successfully reparented (i.e. root was changed); false if not.
	 */
	protected boolean Reparent_root() {
		
		/* Change the root node to the parent of the existing node
		 We need to recalculate the coordinates for the "new" root as the 
		 user may have moved around within the current root */
		
		CDasherNode NewRoot;
		
		if(oldroots.size() == 0) {
			
			/* If our internal buffer of old roots is exhausted, */
			NewRoot = m_Root.RebuildParent();
			if (NewRoot == null) return false; // no existing parent and no way of recreating => give up
			//RebuildParent() can create multiple generations of ((great-)*grand-)parents in one go.
			// Add all created ancestors to the root queue, to ensure they're deleted if the model is.
			for (CDasherNode temp = NewRoot; (temp=temp.Parent())!=null;) {
				oldroots.addFirst(temp);
			}
		}
		else {
			NewRoot = oldroots.removeLast();
			assert (NewRoot != null);
		}
		
		assert NewRoot == m_Root.Parent();
		
		long upper = m_Root.Hbnd(), lower = m_Root.Lbnd(), iWidth = upper-lower;
		long iRootWidth = m_Rootmax - m_Rootmin;
		
		if ((NORMALIZATION - upper) / (double)iWidth > (ROOTMAX_MAX - m_Rootmax) / (double)iRootWidth ||
				lower / (double)iWidth > (m_Rootmin - ROOTMIN_MIN)/(double)iRootWidth) {
			//new node would be too big, so don't reparent.
			// However, cache the root's parent, so (a) we don't repeatedly recreate it,
			// (b) it'll get deleted if we clear the oldroots queue.
			oldroots.addLast(NewRoot);
			return false;
		}
		m_Root.commit(false);
		m_Root = NewRoot;
		
		m_Rootmax +=  ((NORMALIZATION - upper)) * iRootWidth / iWidth;
	
		m_Rootmin -= lower * iRootWidth / iWidth;
	
		for (int i=m_iGotoNext; i>=0; i--) {
			iRootWidth = m_gotoMax[i] - m_gotoMin[i];
			m_gotoMax[i] += (NORMALIZATION - upper) * iRootWidth / iWidth;
			m_gotoMin[i] -= lower * iRootWidth / iWidth;
		}
		return true; //success!
	}
	
	/**
	 * Rebuilds the tree around the specified node,
	 * positioning the crosshair at an appropriate location
	 * within it but outside any of its children.
	 * 
	 * Any zoom scheduled using ScheduleZoom will be cancelled.
	 * @param node node within which to place the crosshair.
	 */
	public void SetNode(CDasherNode node) {
		
		//a not-in-place op, will destroy the old co-ordinate system!
		AbortOffset(); clearScheduledSteps();
			
		DeleteRoot();

		m_Root = node;
		//we've already entered the node, as it was reconstructed from previously-written context
		m_Root.Enter();
		m_Root.Seen(true);

		m_pLastOutput = node;
		
		Expand(m_Root);
		
		//calculate a new position within the root, but not inside any child
		int iWidth = (int)(MAX_Y / (1.0 + m_Root.MostProbableChild() / (double)NORMALIZATION) );
		
		m_Rootmin = MAX_Y / 2 - iWidth / 2;
		m_Rootmax = MAX_Y / 2 + iWidth / 2;
	}
	
	/**Replaces the last-output node with another, putting the new node in the
	 * same position onscreen and wrt the crosshair. If the old node has any
	 * children, these will be transferred to the new node (and will not move
	 * either), which must have no children; but the new node may have its own
	 * children if the old node has none. Nodes outside the old node will be
	 * (re)constructed by {@link CDasherNode#RebuildParent()} on the new node.
	 * Note, it is safe to call this from {@link CDasherNode#Output()}: such
	 * changes will be delayed until rendering (and output) has finished.
	 * @param node new node to put in its place; must not previously be in the tree
	 * @throws IllegalStateException if there is no existing root.
	 */
	public void ReplaceLastOutputNode(CDasherNode node) {
		if (m_Root==null) throw new IllegalStateException("Must have a root");
		if (bRendering) {
			 if (m_replace!=null) {
					//one node already replacing itself this frame!
					// the second must be a child of it...
					for (CDasherNode n=m_pLastOutput; /*true=>NullPtrEx if no break*/; n=n.Parent())
						if (n==m_replace) break;
					//yes!
				}
			m_replace = m_pLastOutput;
			m_with = node;
		} else {
			ReplaceNode(m_pLastOutput, node);
		}
	}
	
	private void checkCantReach(CDasherNode from, CDasherNode to) {
		if (from == to) throw new IllegalStateException("Reachable!");
		for (int i=0; i<from.ChildCount(); i++)
			checkCantReach(from.ChildAtIndex(i), to);
	}
	
	private void ReplaceNode(CDasherNode old, CDasherNode node) {
		
		assert (old.isSeen());
		
		//to do an in-place op, move the co-ordinate system to refer
		// to the node which we will replace
		if (old!=null && old!=m_Root)
			Make_root(old);
		if (old.ChildCount()>0)
			old.transferChildrenTo(node);
		boolean bSeen = old.isSeen();
		checkCantReach(oldroots.isEmpty() ? m_Root : oldroots.get(0), node);
		//this makes new nodes disjoint from old, so can now:
		DeleteRoot(); //also Leave()s m_pLastOutput, but doesn't reset ptr 

		m_Root = node;
		
		if (bSeen) m_Root.Seen(true); //any seen children, are still the same
		if (m_pLastOutput==old) {
			//we're already inside the node, so assume it has been Output.
			m_Root.Enter();
			m_pLastOutput = node;
		}
		//else, crosshair was in a child of the new one, or outside root;
		// in either case, will still be there.
		
		Expand(m_Root);
	}

	public int GetOffset() {
		return m_pLastOutput==null ? -1 : m_pLastOutput.getOffset();
	}
	
	public float getViscosity() {
		return m_pLastOutput==null ? 1.0f : m_pLastOutput.getViscosity();
	}
	
	/**
	 * Schedules one frame of continuous/steady motion towards a specified
	 * mouse position. The distance moved is based on the current frame rate
	 * and a speed multiplier passed in (this can be used to implement slow start,
	 * etc.)
	 * 
	 * @param miMousex Current mouse X co-ordinate
	 * @param miMousey Current mouse Y co-ordinate
	 * @param Time Time of current frame (used to compute framerate, which
	 * controls rate of advance per frame)
	 * @param dSpeedMul Multiplier to apply to the current speed (i.e. 0.0 = don't move, 10.0 = go 10* as fast)
	 */
	public void ScheduleOneStep(long miMousex,
			long miMousey, 
			long Time, 
			float dSpeedMul)	{
		if (dSpeedMul <= 0.0) return;
			
		//ACL I've inlined Get_new_root_coords here, so we don't have to allocate a temporary object to return two values...

		// Avoid Mousex=0, as this corresponds to infinite zoom
		if(miMousex <= 0) miMousex = 1;

		// If Mousex is too large we risk overflow errors, so make limit it
		// (this is a somewhat empirical limit - at some point we should
		// probably do it a little more scientifically)
		if(miMousex > 60000000) miMousex = 60000000;

		// Calculate what the extremes of the viewport will be when the
		// point under the cursor is at the cross-hair. This is where 
		// we want to be in iSteps updates
		long iTargetMin = (miMousey - (MAX_Y * miMousex) / (2 * CROSS_X));
		long iTargetMax = (miMousey + (MAX_Y * miMousex) / (2 * CROSS_Y));
		//back these up, we may want them later
		long origMin=iTargetMin,origMax=iTargetMax;
		
		// iSteps is the number of update steps we need to get the point
		// under the cursor over to the cross hair. Calculated in order to
		// keep a constant bit-rate.

		final int iSteps = Math.max(1,(int)(Steps()/dSpeedMul));
		
		// Calculate the new values of iTargetMin and iTargetMax required to
		// perform a single update step. Note the awkward equations
		// interpolating between (iTargetMin,iTargetMax), with weight lpMaxY,
		// and (0,iMaxY), with weight iOldWeight; in the olg algorithm, the latter was
		// (iSteps-1)*(iTargetMax-iTargetMin), but people wanted to reverse faster!
		// (TODO: should this be a parameter? I'm resisting "too many user settings" atm, but maybe...)
		final long iOldWeight = (iSteps-1) * Math.min(iTargetMax - iTargetMin, MAX_Y+(iTargetMax-iTargetMin)>>>GetLongParameter(Elp_parameters.LP_REVERSE_BOOST));
		long iDenom = MAX_Y + iOldWeight;
		long iNewTargetMin = (iTargetMin * MAX_Y) / iDenom;
		long iNewTargetMax = (iTargetMax+iOldWeight) * MAX_Y / iDenom;
		iTargetMin = iNewTargetMin;
		iTargetMax = iNewTargetMax;

		// Calculate the minimum size of the viewport corresponding to the
		// maximum zoom.
		double dNats = maxNatsPerFrame()*dSpeedMul;
		if (m_dLastNats!=dNats) m_iLastMinSize = (long)(MAX_Y/Math.exp(m_dLastNats = dNats));
		
		if((iTargetMax - iTargetMin) < m_iLastMinSize) {
			iNewTargetMin = iTargetMin * (MAX_Y - m_iLastMinSize) / (MAX_Y - (iTargetMax - iTargetMin));
		    iNewTargetMax = iNewTargetMin + m_iLastMinSize;

		    iTargetMin = iNewTargetMin;
		    iTargetMax = iNewTargetMax;
		}
		
		//Now calculate the bounds of the root node, that put (y1,y2) at the screen edges...
		// If |(0,Y2)| = |(y1,y2)|, the "zoom factor" is 1, so we just translate.
		if (MAX_Y == iTargetMax - iTargetMin) {
		    m_Rootmin -= iTargetMin;
		    m_Rootmax -= iTargetMin;
		    return;
	    }
		
		// There is a point C on the y-axis such the ratios (y1-C):(0-C) and
		// (y2-C):(iMaxY-C) are equal - iow that divides the "target" region y1-y2
		// into the same proportions as it divides the screen (0-iMaxY). I.e., this
		// is the center of expansion - the point on the y-axis which everything
		// moves away from (or towards, if reversing).
		  
		//We prefer to compute C from the _original_ (y1,y2) pair, as this is more
		// accurate (and avoids drifting up/down when heading straight along the
		// x-axis in dynamic button modes). However...
		if ((iTargetMax-iTargetMin) < MAX_Y ^ (origMax-origMin) < MAX_Y) {
		    //Sometimes (very occasionally), the calculation of a single-step above
		    // can turn a zoom-in into a zoom-out, or vice versa, when the movement
		    // is mostly translation. In which case, must compute C consistently with
		    // the (scaled, single-step) movement we are going to perform, or else we
		    // will end up suddenly going the wrong way along the y-axis (i.e., the
		    // sense of translation will be reversed) !
		    origMin=iTargetMin; origMax=iTargetMax;
		}
		final long C = (origMin * MAX_Y) / (origMin + MAX_Y - origMax);

		//finally, update the rootnode bounds to put iTargetMin/iTargetMax at (0,LP_MAX_Y).
		m_gotoMin[0] = ((m_Rootmin - C) * MAX_Y) / (iTargetMax - iTargetMin) + C;
		m_gotoMax[0] = ((m_Rootmax - C) * MAX_Y) / (iTargetMax - iTargetMin) + C;
		m_iGotoNext=0;
	}
	
	/**
	 * Applies the next scheduled step of movement, if any, updating
	 * m_RootMax and m_RootMin. Does not perform output - that's done
	 * by RenderToView.
	 */
	public boolean nextScheduledStep(long time) {
		if (m_iGotoNext==-1) return false;
		m_iDisplayOffset = offsetQueue[nextOffset];
		offsetQueue[nextOffset]=0;
		if (++nextOffset==offsetQueue.length) nextOffset=0;
		
		//Now actually move to the new location...
		
		while (m_gotoMax[m_iGotoNext] >= ROOTMAX_MAX || m_gotoMin[m_iGotoNext] <= ROOTMIN_MIN) {
			//can't make existing root any bigger because of overflow. So force a new root
			//to be chosen (so that Dasher doesn't just stop!)...
			
			//pick _child_ covering crosshair...
			final long iWidth = m_Rootmax-m_Rootmin;
			for (CDasherNode ch : m_Root.Children()) {
				if (m_Rootmin + (ch.Hbnd() * iWidth / NORMALIZATION) > CROSS_Y) {
					//found child to make root. TODO, proceed only if new root is on the game path....
					/*if (m_bGameMode && !pChild->GetFlag(NF_GAME)) {
					  //If the user's strayed that far off the game path,
					  // having Dasher stop seems reasonable!
					  return;
					}*/
					//make pChild the root node...
					//first we're gonna have to force it to be output, as a non-output root won't work...
					if (!ch.isSeen()) Output(ch); //(parent=old root has already been seen)
					m_Root.DeleteNephews(ch);
					//Make_root automatically updates all target coords at indices 0-m_iGotoNext...
					// to reflect the new coordinate system with pChild as root
					Make_root(ch);
					// (note that the next check below will make sure these coords do cover (0, LP_OY))
					break;
			    }
			}
		}
		
		long newRootmin = m_gotoMin[m_iGotoNext], newRootmax = m_gotoMax[m_iGotoNext];
		m_iGotoNext--;
		
		// Check that we haven't drifted too far. The rule is that we're not
		// allowed to let the root max and min cross the midpoint of the
		// screen.
		newRootmin = Math.min(newRootmin, CROSS_Y - 1 - m_iDisplayOffset);
		newRootmax = Math.max(newRootmax, CROSS_Y + 1 - m_iDisplayOffset);  
		
			
		// Only allow the update if it won't make the
		// root too small. We should have re-generated a deeper root
		// before now already, but the original root is an exception.
		// (as is trying to go back beyond the earliest char in the current
		// alphabet, if there are preceding characters not in that alphabet)
		if ((newRootmax - newRootmin) > MAX_Y / 4) {
		    total_nats += Math.log((newRootmax-newRootmin) / (double)(m_Rootmax - m_Rootmin));
		    
		    m_Rootmax = newRootmax;
		    m_Rootmin = newRootmin;
		    
		    // This may have moved us around a bit...output will happen when the frame is rendered
		} //else, we just stop - this prevents the user from zooming too far back
		//outside the root node (when we can't generate an older root).
		return true;
	}
	
	/**
	 * Handles output based on the nodes which were under the
	 * crosshair before and after a move.
	 * <p>
	 * If the NewNode has its Seen flag already set, returns
	 * without taking any action.
	 * <p>
	 * Internally this works by calling DeleteCharacters on the
	 * two nodes if they are different, and then RecursiveOutput
	 * on the current node and null.
	 * 
	 * @param NewNode Node now under the crosshair
	 * @param OldNode Node previously under the crosshair (maybe the same as NewNode)
	 */
	protected void Output(CDasherNode NewNode) {
		if (NewNode.isSeen()) return;
		if (NewNode.Parent()!=null
				&& !NewNode.Parent().isSeen())
			throw new IllegalArgumentException("Parent must be output first");
		if (m_pLastOutput!=NewNode.Parent()) EraseBackTo(NewNode.Parent());

		if (m_pLastOutput!=null) m_pLastOutput.Leave();
		NewNode.Enter();
		NewNode.Seen(true);
		m_pLastOutput=NewNode;
		NewNode.Output();
		Expand(NewNode);
	}
	
	protected void Collapse(CDasherNode node) {
		if (node.isSeen()) EraseBackTo(node.Parent());
		if (node.ChildCount()>0) node.Delete_children();
	}
	
	private void EraseBackTo(CDasherNode lastToKeep) {
		m_pLastOutput.Leave();
		while (true) {
			//update m_pLastOutput first, so GetOffset() consistent with having left the node
			CDasherNode leave=m_pLastOutput;
			m_pLastOutput = m_pLastOutput.Parent();
			leave.Undo();
			leave.Seen(false);
			if (m_pLastOutput == lastToKeep) break;
		}
		if (m_pLastOutput!=null) m_pLastOutput.Enter();
	}
	
	/**
	 * Populates the children of a given node, if it doesn't have its children already
	 * <p>
	 * (We assume that if a node has any children, it has all its children; and that
	 * in that case, there is no point in deleting/recreating.)
	 * @param Node Node to push. Must not be null.
	 */
	protected void Expand(CDasherNode Node) {
		
		if(Node.ChildCount() == 0)
			Node.PopulateChildren();
	}
	
	/** Whether there is currently a call to RenderToView in progress.
	 * If so, we'd better not make any significant changes to the tree
	 * (expanding nodes probably ok but that's about it)
	 */
	private boolean bRendering;
	
	private CDasherNode m_replace;
	private CDasherNode m_with;
	
	/**
	 * Calls the View's Render method on our current Root; the View
	 * will take care of all drawing from here on in. However,
	 * the model will take care of using an ExpansionPolicy to expand
	 * and/or contract nodes. 
	 * 
	 * @param View View to which we wish to draw
	 * @return whether anything was changed (i.e. nodes were expanded or contracted)
	 */	
	public boolean RenderToView(CDasherView View) {
		if (bRendering) throw new IllegalStateException("Some thread already in call to RenderToView!");
		bRendering=true;
		while (!View.NodeFillsScreen(m_Rootmin,m_Rootmax)) {
			if (!Reparent_root()) break;
		}
		
		CDasherNode out=View.Render(m_Root, m_Rootmin + m_iDisplayOffset, m_Rootmax + m_iDisplayOffset, pol, this);
		if (out!=m_pLastOutput) EraseBackTo(out);
		
		while (m_Root.m_OnlyChildRendered!=null) {
			// We have zoomed sufficiently that only one child of the root node 
			// is still alive. We may be able to make it the root.
				
			final long range = m_Rootmax - m_Rootmin;
			final CDasherNode c = m_Root.m_OnlyChildRendered;	
			final long newy1 = m_Rootmin + (range * c.Lbnd()) / NORMALIZATION;
			final long newy2 = m_Rootmin + (range * c.Hbnd()) / NORMALIZATION;
			if(View.NodeFillsScreen(newy1, newy2)) {
				Make_root(c);
				//and try again, looking for a child of the new root...
			} else {
				//parent still on screen as well (to left)
				break;
			}
		}

		boolean bRes = pol.apply(this);
		bRendering=false;
		if (m_replace!=null) {
			ReplaceNode(m_replace, m_with);
			m_replace=m_with=null;
		}
		return bRes;
	}
	/**
	 * ExpansionPolicy to determine which CDasherNodes to expand or collapse in each frame.
	 * Reused between frames to save on allocation.
	 */
	private ExpansionPolicy pol;

	/**
	 * Interpolates between our current position and a given
	 * new position, scheduling a zoom to the new position by
	 * adding the interpolated points to m_deGotoQueue.
	 * <p>
	 * dasherx will be increased to 1 if this is not already so.
	 * <p>
	 * The number of points to interpolate (and hence the smoothness
	 * of the zoom, at the expense of speed,) is controlled by
	 * LP_ZOOMSTEPS.
	 * 
	 * @param dasherx Destination Dasher X co-ordinate
	 * @param dashery Destination Dasher Y co-ordinate
	 */
	public void ScheduleZoom(long dasherx, long dashery) {
		
		// Takes dasher co-ordinates and 'schedules' a zoom to that location
		// by storing a sequence of moves in 'm_deGotoQueue'
		
		if (dasherx < 1) dasherx = 1;
		
		final int iSteps = (int)(GetLongParameter(Elp_parameters.LP_ZOOMSTEPS));

		final long y1 = dashery - dasherx, y2 = dashery + dasherx;

		long targetRootMin,targetRootMax;
		
		if (y2-y1 == MAX_Y) {
			//just translate
			targetRootMin = m_Rootmin + y1;
			targetRootMax = m_Rootmax + y1;
		} else {
			//find the center of expansion / contraction - this divides interval
			// (iTarget1,iTarget2) into the same proportions as it divides (0,maxY),
			// i.e. (C-iTarget1)/(C-0) == (C-iTarget2)/(C-iMaxY)
			final long C = (y1 * MAX_Y) / (y1 + MAX_Y - y2);
			if (y1 != C) {
		          targetRootMin = ((m_Rootmin - C) * (0 - C)) / (y1 - C) + C;
		          targetRootMax = ((m_Rootmax - C) * (0 - C)) / (y1 - C) + C;
		      } else if (y2 != C) {
		          targetRootMin = ((m_Rootmin - C) * (MAX_Y - C)) / (y2 - C) + C;
		          targetRootMax = ((m_Rootmax - C) * (MAX_Y - C)) / (y2 - C) + C;
		      } else { // implies y1 = y2
		          throw new AssertionError("Impossible geometry in CDasherModel.ScheduleZoom");
		      }
		}
		
		//now a simple linear interpolation from m_Root{min,max} to targetRoot{Min,Max}
		if (m_gotoMin.length < iSteps) {
			m_gotoMin = new long[iSteps];
			m_gotoMax = new long[iSteps];
		}
		
		m_iGotoNext=iSteps-1;
		
		//note element 0 arrives at destination.
		for(int s = 0; s<iSteps; ++s) {
			
			m_gotoMin[s] = targetRootMin + (s * (m_Rootmin - targetRootMin))/iSteps;
			m_gotoMax[s] = targetRootMax + (s * (m_Rootmax - targetRootMax))/iSteps;
		}
	}
	
	/**
	 * Sets each of Rootmin and Rootmax to TargetMin and TargetMax
	 * plus a given offset. 
	 * 
	 * @param iOffset Offset to add
	 */
	public void Offset(int iOffset) {
		m_Rootmin += iOffset;
		m_Rootmax += iOffset;

		double d = Math.log(Math.abs(iOffset));
		int s = (iOffset>0) ? -1 : 1;
		for (int i=0; i<offsetQueue.length; i++)
			offsetQueue[(nextOffset+i) % offsetQueue.length]+= (int)(s*Math.exp(d*(offsetQueue.length-i)/(double)offsetQueue.length));		
	} 
	
	/**
	 * Sets each of RootMin and RootMax to match
	 * their TargetMax and TargetMin partners.
	 */
	public void AbortOffset() {
		m_Rootmin += m_iDisplayOffset;
		m_Rootmax += m_iDisplayOffset;
		m_iDisplayOffset = 0;
		for (int i=0; i<offsetQueue.length; i++) offsetQueue[i]=0;
	}
	
	/**
	 * Gets total_nats
	 * @return total_nats
	 */
	public double GetNats() {
		return total_nats;
	}
	
	/**
	 * Retrieves the number of steps currently scheduled
	 * and yet-to-be executed
	 * @return Number of scheduled steps.
	 */
	public int ScheduledSteps() {
		return m_iGotoNext+1;
	}
	
	/**
	 * Clears any currently-in-progress zoom (scheduled via {@link #ScheduleZoom})
	 */
	public void clearScheduledSteps() {
		m_iGotoNext=-1;
	}
	
	public void shutdown() {
		DeleteRoot();
	}

	/*package*/ CDasherNode getLastOutputNode() {return m_pLastOutput;}
	
}
