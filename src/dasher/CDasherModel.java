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
import java.util.Collections;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

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
	protected long m_Rootmin_min;
	
	/**
	 * Maximum allowable value of m_Rootmax
	 */
	protected long m_Rootmax_max;
	
	/**
	 * Record of 'amount of information' entered so far, for logging purposes.
	 */
	protected double total_nats;            // Information entered so far
	
	/* CSFS: Converted a struct in the original C into this class */
	
	/**
	 * List of points which we are to go (i.e. intermediate points,
	 * interpolated between previous location and user-supplied dest)
	 */
	protected final LinkedList<SGotoItem> m_deGotoQueue = new LinkedList<SGotoItem>();	
	
	/**
	 * Simple struct recording a point to which we are scheduled
	 * to zoom.
	 */
	class SGotoItem {
		/**
		 * Co-ordinate 1
		 */
		public long iN1;
		/**
		 * Co-ordinate 2
		 */
		public long iN2;
	}
	
	private CDasherNode m_pLastOutput;
	
	/**
	 * Initialise a new DasherModel. Note that you'll still have to call
	 * {@link #SetOffset(int, CAlphabetManager, boolean)} before you can use it...
	 */
	public CDasherModel(CDasherInterfaceBase iface, CSettingsStore SettingsStore) {
		super(iface, SettingsStore); 
		
		int iNormalization = (int)GetLongParameter(Elp_parameters.LP_NORMALIZATION);
		
		/* CSFS: These used to be int64_max and int64_min.
		 * As far as I can determine from the internet,
		 * these are signed types like long.
		 */
		
		m_Rootmin_min = Long.MIN_VALUE / iNormalization / 2;
		m_Rootmax_max = Long.MAX_VALUE / iNormalization / 2;
		
		HandleEvent(new CParameterNotificationEvent(Elp_parameters.LP_NODE_BUDGET));
	}
	
	/**
	 * The Model responds to changes in the following parameters:
	 * <p>
	 * <i>LP_MAX_BITRATE</i>: Informs the CFrameRate which performs
	 * frame rate tracking for us of the new frame rate.
	 * <p>
	 * <i>BP_CONTROL_MODE</i>: Rebuilds the model (calls RebuildAroundNode) to include/exclude a control node.
	 * <p>
	 * <i>BP_DELAY_VIEW</i>: Sets the TargetMax to match RootMax and similarly RootMin.
	 * <p>
	 * <i>LP_UNIFORM</i>: Updates our internally cached value (uniformAdd)
	 * to reflect the new value. 
	 */	
	public void HandleEvent(CEvent Event) {
		super.HandleEvent(Event); //framerate watches LP_MAX_BITRATE
		if(Event instanceof CParameterNotificationEvent) {
			CParameterNotificationEvent Evt = (CParameterNotificationEvent)(Event);
			if (Evt.m_iParameter == Ebp_parameters.BP_DASHER_PAUSED) {
				if (!GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED)) {
					//just unpaused
					ResetFramecount();
					total_nats = 0.0;
				}
			}
			else if(Evt.m_iParameter == Ebp_parameters.BP_DELAY_VIEW) {
				MatchTarget();
			} else if (Evt.m_iParameter == Elp_parameters.LP_NODE_BUDGET) {
				pol = new AmortizedPolicy((int)GetLongParameter(Elp_parameters.LP_NODE_BUDGET));
			}
			
			
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
	 * Due to this function's lack of checks made on the validity
	 * of the requested operation, it is recommended to use
	 * RecursiveMakeRoot instead where possible.
	 * 
	 * @param whichchild Child to make the new root node.
	 */
	protected void Make_root(CDasherNode whichchild)
	//	find a new root node 
	{
		m_Root.commit(true);
		
		m_Root.m_dCost = Double.MAX_VALUE; //make sure never collapsed, and new root's cost is never limited to <= its parent
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
		m_Rootmax = m_Rootmin + (range * m_Root.Hbnd()) / GetLongParameter(Elp_parameters.LP_NORMALIZATION);
		m_Rootmin = m_Rootmin + (range * m_Root.Lbnd()) / GetLongParameter(Elp_parameters.LP_NORMALIZATION);
		
		for (SGotoItem sgi : m_deGotoQueue) {
			//sgi contains pairs of coordinates for the _old_ root; we need to update it to contain the corresponding
			// coordinates for the _new_ root, which will be somewhat closer together.
			// However, it's possible that the existing coordinate pairs may be bigger than would actually be allowed
			// for a root node (and hence, when we try to NewGoTo them, we'll forcibly reparent); this means that we
			// may have difficulty working with them...
			final long r = sgi.iN2 - sgi.iN1;
			final long iNorm = GetLongParameter(Elp_parameters.LP_NORMALIZATION);
			sgi.iN2 = sgi.iN1 + //r * m_Root.Hbnd() / iNorm; //rewrite to ensure no overflow:
				(r / iNorm) * m_Root.Hbnd() + ((r % iNorm) * m_Root.Hbnd())/iNorm;
		    sgi.iN1 += // r * m_Root.Lbnd() / iNorm;
		    	(r/iNorm) * m_Root.Lbnd() + ((r % iNorm) * m_Root.Lbnd())/iNorm;
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
				//collapsing a parent of the root, would leave nothing onscreen....
				// (and more to point: root's cost will be set to <= its parent!)
				temp.m_dCost=Double.MAX_VALUE;
				oldroots.addFirst(temp);
			}
		}
		else {
			NewRoot = oldroots.removeLast();
			assert (NewRoot != null);
		}
		
		final long lNorm = GetLongParameter(Elp_parameters.LP_NORMALIZATION);
		assert NewRoot == m_Root.Parent();
		
		long upper = m_Root.Hbnd(), lower = m_Root.Lbnd(), iWidth = upper-lower;
		long iRootWidth = m_Rootmax - m_Rootmin;
		
		if ((lNorm - upper) / (double)iWidth > (m_Rootmax_max - m_Rootmax) / (double)iRootWidth ||
				lower / (double)iWidth > (m_Rootmin - m_Rootmin_min)/(double)iRootWidth) {
			//new node would be too big, so don't reparent.
			// However, cache the root's parent, so (a) we don't repeatedly recreate it,
			// (b) it'll get deleted if we clear the oldroots queue.
			NewRoot.m_dCost = Double.MAX_VALUE;
			oldroots.addLast(NewRoot);
			return false;
		}
		m_Root.commit(false);
		m_Root = NewRoot;
		
		m_Rootmax +=  ((lNorm - upper)) * iRootWidth / iWidth;
	
		m_Rootmin -= lower * iRootWidth / iWidth;
	
		for (SGotoItem it : m_deGotoQueue) {
			iRootWidth = it.iN2 - it.iN1;
			it.iN2 += (lNorm - upper) * iRootWidth / iWidth;
			it.iN1 -= lower * iRootWidth / iWidth;
		}
		return true; //success!
	}
	
	/**
	 * Forces the current context to a given value, and resets
	 * our position in the Dasher world based upon it.
	 * <p>
	 * If the given context is empty, the context is actually set
	 * to ". " so that the prediction is the same as for the 
	 * beginning of a new sentence.
	 * <p>
	 * Internally, this works by requesting a new Root node from
	 * the Alphabet Manager, creating a blank context, and training
	 * it using the supplied String.
	 * <p>
	 * This method also has the following side-effects:
	 * <p>
	 * <ul><li>Any zoom scheduled using ScheduleZoom will be cancelled.
	 * <li>m_RootMax, m_TargetMax and their brethren will be altered
	 * to reflect the changes.
	 * 
	 * @param sNewContext Context to set
	 */
	public void SetOffset(int iOffset, CAlphabetManager<?> alphMgr, boolean bForce) {
		if (iOffset == GetOffset() && !bForce) return;
		
		/* If a zoom was in progress, cancel it -- this function will likely change
		 * our location within the Dasher world, and so the target being aimed for
		 * is likely not to be there anymore.
		 */
		m_deGotoQueue.clear();
		
		DeleteRoot();
		
		m_Root = alphMgr.GetRoot(null, 0,(int)GetLongParameter(Elp_parameters.LP_NORMALIZATION), iOffset, true);
		//we've already entered the node, as it was reconstructed from previously-written context
		m_Root.Enter();
		m_Root.Seen(true);
		m_pLastOutput=m_Root;
		Expand(m_Root);
		
		double dFraction = 1 - (1 - m_Root.MostProbableChild() / (double)(GetLongParameter(Elp_parameters.LP_NORMALIZATION)))/2.0;
		
		int iWidth = ( (int)( (GetLongParameter(Elp_parameters.LP_MAX_Y) / (2.0*dFraction)) ) );
		
		MatchTarget();
		
		m_Rootmin = GetLongParameter(Elp_parameters.LP_MAX_Y) / 2 - iWidth / 2;
		m_Rootmax = GetLongParameter(Elp_parameters.LP_MAX_Y) / 2 + iWidth / 2;
	
	}
	
	public int GetOffset() {
		return m_pLastOutput==null ? -1 : m_pLastOutput.getOffset();
	}
	
	/**
	 * Updates the model to move one step towards a specified mouse position.
	 * The distance moved is based on the current frame rate
	 * and a speed multiplier passed in (this can be used to implement slow start,
	 * etc.)
	 * 
	 * Internally, this computes the new boundaries of the current root node,
	 * and then calls {@link #NewGoTo(long, long)} to take us there.
	 * 
	 * @param miMousex Current mouse X co-ordinate
	 * @param miMousey Current mouse Y co-ordinate
	 * @param Time Time of current frame (used to compute framerate, which
	 * controls rate of advance per frame)
	 * @param dSpeedMul Multiplier to apply to the current speed (i.e. 0.0 = don't move, 10.0 = go 10* as fast)
	 */
	public void oneStepTowards(long miMousex,
			long miMousey, 
			long Time, 
			float dSpeedMul)	{
		CountFrame(Time);
		if (dSpeedMul <= 0.0) return;
			
		//ACL I've inlined Get_new_root_coords here, so we don't have to allocate a temporary object to return two values...

		// Avoid Mousex=0, as this corresponds to infinite zoom
		if(miMousex <= 0) miMousex = 1;

		// If Mousex is too large we risk overflow errors, so make limit it
		// (this is a somewhat empirical limit - at some point we should
		// probably do it a little more scientifically)
		if(miMousex > 60000000) miMousex = 60000000;

		// Cache some results so we don't do a huge number of parameter lookups
		long iMaxY = (GetLongParameter(Elp_parameters.LP_MAX_Y));
		long iOX = (GetLongParameter(Elp_parameters.LP_OX));
		long iOY = (GetLongParameter(Elp_parameters.LP_OY));

		// Calculate what the extremes of the viewport will be when the
		// point under the cursor is at the cross-hair. This is where 
		// we want to be in iSteps updates
		long iTargetMin = (miMousey - (iMaxY * miMousex) / (2 * iOX));
		long iTargetMax = (miMousey + (iMaxY * miMousex) / (2 * iOY));
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
		final long iOldWeight = (iSteps-1) * Math.min(iTargetMax - iTargetMin, iMaxY+(iTargetMax-iTargetMin)>>>GetLongParameter(Elp_parameters.LP_REVERSE_BOOST));
		long iDenom = iMaxY + iOldWeight;
		long iNewTargetMin = (iTargetMin * iMaxY) / iDenom;
		long iNewTargetMax = (iTargetMax+iOldWeight) * iMaxY / iDenom;
		iTargetMin = iNewTargetMin;
		iTargetMax = iNewTargetMax;

		// Calculate the minimum size of the viewport corresponding to the
		// maximum zoom.
		long iMinSize = (long)(iMaxY/(dSpeedMul*maxZoom()+(1.0f-dSpeedMul)));

		if((iTargetMax - iTargetMin) < iMinSize) {
			iNewTargetMin = iTargetMin * (iMaxY - iMinSize) / (iMaxY - (iTargetMax - iTargetMin));
		    iNewTargetMax = iNewTargetMin + iMinSize;

		    iTargetMin = iNewTargetMin;
		    iTargetMax = iNewTargetMax;
		}
		
		//Now calculate the bounds of the root node, that put (y1,y2) at the screen edges...
		// If |(0,Y2)| = |(y1,y2)|, the "zoom factor" is 1, so we just translate.
		if (iMaxY == iTargetMax - iTargetMin) {
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
		if ((iTargetMax-iTargetMin) < iMaxY ^ (origMax-origMin) < iMaxY) {
		    //Sometimes (very occasionally), the calculation of a single-step above
		    // can turn a zoom-in into a zoom-out, or vice versa, when the movement
		    // is mostly translation. In which case, must compute C consistently with
		    // the (scaled, single-step) movement we are going to perform, or else we
		    // will end up suddenly going the wrong way along the y-axis (i.e., the
		    // sense of translation will be reversed) !
		    origMin=iTargetMin; origMax=iTargetMax;
		}
		final long C = (origMin * iMaxY) / (origMin + iMaxY - origMax);

		//finally, update the rootnode bounds to put iTargetMin/iTargetMax at (0,LP_MAX_Y).
		NewGoTo( ((m_Rootmin - C) * iMaxY) / (iTargetMax - iTargetMin) + C,
				 ((m_Rootmax - C) * iMaxY) / (iTargetMax - iTargetMin) + C);
	}
	
	public boolean nextScheduledStep(long time) {
		if (m_deGotoQueue.size() == 0) return false;
		SGotoItem next = m_deGotoQueue.removeFirst();
		NewGoTo(next.iN1, next.iN2);
		if (ScheduledSteps()==0) {
            //just finished. Pause (mouse not held down, or schedule
            //would have been cleared already)
            SetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED, true);
        }
		return true;
	}
	
	/**
	 * Changes the state of the Model updating the values of
	 * m_RootMax and m_RootMin, which has the effect of making
	 * us appear to move around. Also pushes the node we're moving
	 * into, and cues output handling.
	 * <p>
	 * Both values are checked for sanity and truncated if necessary.
	 * <p>
	 * m_iTargetMax and Min are also updated according to the
	 * new values of RootMax/Min.
	 * <p>
	 * For the purpose of output handling, the node under the
	 * crosshair is noted before and after enacting the move,
	 * and HandleOutput invoked upon this pair.
	 * <p>
	 * At present, this function takes no action if the proposed
	 * new values are not allowable; it returns without making
	 * any changes which has the effect of causing Dasher to
	 * freeze.
	 * 
	 * @param newRootmin Desired new value of m_RootMin
	 * @param newRootmax Desired new value of m_RootMax
	 */
	protected void NewGoTo(long newRootmin, long newRootmax) {
		
		total_nats += Math.log((newRootmax-newRootmin) / (double)(m_Rootmax - m_Rootmin));
		m_iDisplayOffset = offsetQueue[nextOffset];
		offsetQueue[nextOffset]=0;
		if (++nextOffset==offsetQueue.length) nextOffset=0;
		
		//Now actually move to the new location...
		
		while (newRootmax >= m_Rootmax_max || newRootmin <= m_Rootmin_min) {
			//can't make existing root any bigger because of overflow. So force a new root
			//to be chosen (so that Dasher doesn't just stop!)...
			
			//pick _child_ covering crosshair...
			final long iWidth = m_Rootmax-m_Rootmin, iNorm = GetLongParameter(Elp_parameters.LP_NORMALIZATION), lpOY = GetLongParameter(Elp_parameters.LP_OY);
			for (CDasherNode ch : m_Root.Children()) {
				if (m_Rootmin + (ch.Hbnd() * iWidth / iNorm) > lpOY) {
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
					//we need to update the target coords (newRootmin,newRootmax)
					// to reflect the new coordinate system based upon pChild as root.
					//Make_root automatically updates any such pairs stored in m_deGotoQueue, so:
					SGotoItem temp=new SGotoItem(); temp.iN1 = newRootmin; temp.iN2 = newRootmax;
					m_deGotoQueue.add(temp);
					//...when we make pChild the root...
					Make_root(ch);
					//...we can retrieve new, equivalent, coordinates for it
					newRootmin = temp.iN1; newRootmax = temp.iN2;
					m_deGotoQueue.removeLast();
					// (note that the next check below will make sure these coords do cover (0, LP_OY))
					break;
			    }
			}
		}
		
		// Check that we haven't drifted too far. The rule is that we're not
		// allowed to let the root max and min cross the midpoint of the
		// screen.
		newRootmin = Math.min(newRootmin, GetLongParameter(Elp_parameters.LP_OY) - 1 - m_iDisplayOffset);
		newRootmax = Math.max(newRootmax, GetLongParameter(Elp_parameters.LP_OY) + 1 - m_iDisplayOffset);  
		
			
		// Only allow the update if it won't make the
		// root too small. We should have re-generated a deeper root
		// before now already, but the original root is an exception.
		// (as is trying to go back beyond the earliest char in the current
		// alphabet, if there are preceding characters not in that alphabet)
		if ((newRootmax - newRootmin) > GetLongParameter(Elp_parameters.LP_MAX_Y) / 4) {
		    m_Rootmax = newRootmax;
		    m_Rootmin = newRootmin;
		    
		    // This may have moved us around a bit...output will happen when the frame is rendered
		} //else, we just stop - this prevents the user from zooming too far back
		//outside the root node (when we can't generate an older root).
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
		NewNode.m_dCost = Double.MAX_VALUE;
	}
	
	protected void Collapse(CDasherNode node) {
		if (node.isSeen()) EraseBackTo(node.Parent());
		if (node.ChildCount()>0) node.Delete_children();
	}
	
	private void EraseBackTo(CDasherNode lastToKeep) {
		m_pLastOutput.Leave();
		while (true) {
			m_pLastOutput.Undo();
			m_pLastOutput.Seen(false);
			m_pLastOutput = m_pLastOutput.Parent();
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
		while (!View.NodeFillsScreen(m_Rootmin,m_Rootmax)) {
			if (!Reparent_root()) break;
		}
		
		CDasherNode out=View.Render(m_Root, m_Rootmin + m_iDisplayOffset, m_Rootmax + m_iDisplayOffset, pol, this);
		if (out!=m_pLastOutput) EraseBackTo(out);
		
		while (m_Root.m_OnlyChildRendered!=null) {
			// We must have zoomed sufficiently that only one child of the root node 
			// is still alive.  Let's make it the root.
				
			long y1 = m_Rootmin;
			long y2 = m_Rootmax;
			long range = y2 - y1;
			CDasherNode c = m_Root.m_OnlyChildRendered;	
			long newy1 = y1 + (range * c.Lbnd()) / (int)GetLongParameter(Elp_parameters.LP_NORMALIZATION);
			long newy2 = y1 + (range * c.Hbnd()) / (int)GetLongParameter(Elp_parameters.LP_NORMALIZATION);
			if(View.NodeFillsScreen(newy1, newy2)) {
				Make_root(c);
				//and try again, looking for a child of the new root...
			} else {
				//more than one child on screen
				break;
			}
		}

		return pol.apply(this);	
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
		
		m_deGotoQueue.clear();
		
		if (dasherx < 1) dasherx = 1;
		
		final int iSteps = (int)(GetLongParameter(Elp_parameters.LP_ZOOMSTEPS));
			
		final long y1 = dashery - dasherx, y2 = dashery + dasherx;
		final long iMaxY = GetLongParameter(Elp_parameters.LP_MAX_Y);
		long targetRootMin,targetRootMax;
		
		if (y2-y1 == iMaxY) {
			//just translate
			targetRootMin = m_Rootmin + y1;
			targetRootMax = m_Rootmax + y1;
		} else {
			//find the center of expansion / contraction - this divides interval
			// (iTarget1,iTarget2) into the same proportions as it divides (0,maxY),
			// i.e. (C-iTarget1)/(C-0) == (C-iTarget2)/(C-iMaxY)
			final long C = (y1 * iMaxY) / (y1 + iMaxY - y2);
			if (y1 != C) {
		          targetRootMin = ((m_Rootmin - C) * (0 - C)) / (y1 - C) + C;
		          targetRootMax = ((m_Rootmax - C) * (0 - C)) / (y1 - C) + C;
		      } else if (y2 != C) {
		          targetRootMin = ((m_Rootmin - C) * (iMaxY - C)) / (y2 - C) + C;
		          targetRootMax = ((m_Rootmax - C) * (iMaxY - C)) / (y2 - C) + C;
		      } else { // implies y1 = y2
		          throw new AssertionError("Impossible geometry in CDasherModel.ScheduleZoom");
		      }
		}
		
		//now a simple linear interpolation from m_Root{min,max} to targetRoot{Min,Max}
		
		for(int s = iSteps-1; s >= 0; --s) {
			SGotoItem sNewItem = new SGotoItem();
			
			sNewItem.iN1 = targetRootMin - (s * (targetRootMin - m_Rootmin))/iSteps;
			sNewItem.iN2 = targetRootMax - (s * (targetRootMax - m_Rootmax))/iSteps;
			
			m_deGotoQueue.addLast(sNewItem);
		} 
		
		SetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED, false);
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
		if (GetBoolParameter(Ebp_parameters.BP_DELAY_VIEW)) {
			double d = Math.log(Math.abs(iOffset));
			int s = (iOffset>0) ? -1 : 1;
			for (int i=0; i<offsetQueue.length; i++)
				offsetQueue[(nextOffset+i) % offsetQueue.length]+= (int)(s*Math.exp(d*(offsetQueue.length-i)/(double)offsetQueue.length));
		}
	} 
	
	/**
	 * Sets each of RootMin and RootMax to match
	 * their TargetMax and TargetMin partners.
	 */
	protected void MatchTarget() {
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
	 * Retrieves the number of points currently in the m_deGotoQueue.
	 * 
	 * @return Number of scheduled steps.
	 */
	public int ScheduledSteps() {
		return m_deGotoQueue.size();
	}
	
	/**
	 * Clears any currently-in-progress zoom (scheduled via {@link #ScheduleZoom})
	 */
	public void clearScheduledSteps() {
		m_deGotoQueue.clear();
	}
	
	public void shutdown() {
		DeleteRoot();
	}
	
}
