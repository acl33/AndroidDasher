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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.ArrayList;
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
public class CDasherModel extends CDasherComponent {

	/**
	 * Are the current model's predictions dependent on
	 * our context?
	 * <p>
	 * In reality almost all models are context sensitive.
	 */
	public boolean m_bContextSensitive;
	// public String m_strContextBuffer;
		
	/**
	 * The Interface to which this Model belongs.
	 */
	/*package*/final CDasherInterfaceBase m_DasherInterface;
	
/////////////////////////////////////////////////////////////////////////////
	
	// Interfaces
	
	/**
	 * LanguageModel used by this Model for prediction.
	 */
	protected CLanguageModel m_LanguageModel;     // pointer to the language model
	
	/**
	 * Alphabet used by this Model 
	 */
	protected CAlphabet m_cAlphabet;        // pointer to the alphabet
	
/////////////////////////////////////////////////////////////////////////////
	
//	protected CDasherGameMode m_GameMode;
	
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
	 * Amount to offset display by vs. what's "really" happening
	 * (used to smooth offsets/bounces over several frames in button modes)
	 */
	protected long m_iDisplayOffset;
	
	/**
	 * Minimum allowable value of m_Rootmin
	 */
	protected long m_Rootmin_min;
	
	/**
	 * Maximum allowable value of m_Rootmax
	 */
	protected long m_Rootmax_max;
	
	/**
	 * Framerate tracker
	 */
	protected CFrameRate m_fr;              // keep track of framerate
	
	/**
	 * Record of 'amount of information' entered so far, for logging purposes.
	 */
	protected double total_nats;            // Information entered so far
	
	/**
	 * The probability that gets added to every symbol
	 */ 
	protected double m_dMaxRate;
	
	/**
	 * Number of interpolated steps to use in Click Mode.
	 * 
	 * @see CClickFilter
	 */
	protected int m_Stepnum;
	
	/**
	 * Our alphabet manager, for functions which require knowledge
	 * of an Alphabet.
	 */
	protected CAlphabetManager m_AlphabetManager;
	// protected CControlManagerFactory m_ControlManagerFactory;

	/* CSFS: Converted a struct in the original C into this class */
	
	/**
	 * List of points which we are to go to before responding
	 * to user input again. 
	 */
	protected LinkedList<SGotoItem> m_deGotoQueue = new LinkedList<SGotoItem>();
	
	/**
	 * Amount to add to all symbols' probabilities, in order to
	 * avoid a zero probability.
	 */
	protected int uniformAdd;
	
	/**
	 * Probability assinged to the Control Node
	 */
	protected long ControlSpace;
	
	// Both of these are to save repeated calculations of the same answers. Their
	// values are calculated when the model is created and are recalculated
	// in response to any dependent parameter changes.
	
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
		/**
		 * 2 if this is the last point in a sequence, 1 if not.
		 */
		public int iStyle;
	}
	
	/**
	 * Initialise a new DasherModel. This consists of:
	 * <p><ul>
	 * <li>Retrieving an AlphInfo object describing the user's chosen alphabet
	 * (ie. which corresponds to SP_ALPHABET_ID)
	 * <li>Setting global parameters which reflect information about this alphabet
	 * (eg. SP_TRAIN_FILE)
	 * <li>Creating a new CAlphabet which describes this alphabet, by calling
	 * its constructor which takes an AlphInfo
	 * <li>Creating a CSymbolAlphabet which wraps this Alphabet
	 * <li>Creating the LanguageModel described by LP_LANGUAGE_MODEL_ID
	 * <li>Initialising this LanguageModel with a blank context.
	 * </ul><p>
	 * This does not train the newly created language model;
	 * this must be requested from outside, typically by the
	 * Interface.
	 * 
	 * @param EventHandler Event handler with which to register ourselves
	 * @param SettingsStore Settings repository
	 * @param DashIface Interface which we serve
	 * @param AlphIO AlphIO object from which to retrieve the AlphInfo object describing the user's chosen alphabet
	 */
	public CDasherModel(CDasherInterfaceBase iface, CSettingsStore SettingsStore, CAlphIO AlphIO) {
	super(iface, SettingsStore); 
	m_DasherInterface = iface;
	
	// Set max bitrate in the FrameRate class
	m_dMaxRate = GetLongParameter(Elp_parameters.LP_MAX_BITRATE) / 100.0;
	
	m_fr = new CFrameRate();
	m_fr.SetMaxBitrate(m_dMaxRate);
	
	// Convert the full alphabet to a symbolic representation for use in the language model
	
	// -- put all this in a separate method
	// TODO: Think about having 'prefered' values here, which get
	// retrieved by DasherInterfaceBase and used to set parameters
	
	// TODO: We might get a different alphabet to the one we asked for -
	// if this is the case then the parameter value should be updated,
	// but not in such a way that it causes everything to be rebuilt.
	
	CAlphIO.AlphInfo oAlphInfo = AlphIO.GetInfo(GetStringParameter(Esp_parameters.SP_ALPHABET_ID));
	CAlphabet alphabet = m_cAlphabet = new CAlphabet(oAlphInfo);
	
	SetStringParameter(Esp_parameters.SP_TRAIN_FILE, m_cAlphabet.GetTrainingFile());
	SetStringParameter(Esp_parameters.SP_DEFAULT_COLOUR_ID, m_cAlphabet.GetPalette());
	
	if(GetLongParameter(Elp_parameters.LP_ORIENTATION) == Opts.AlphabetDefault)
		SetLongParameter(Elp_parameters.LP_REAL_ORIENTATION, m_cAlphabet.GetOrientation());
		
	// Create an appropriate language model;
	
	switch ((int)GetLongParameter(Elp_parameters.LP_LANGUAGE_MODEL_ID)) {
	case 0:
		m_LanguageModel = new CPPMLanguageModel(m_EventHandler, m_SettingsStore, alphabet);
		SetBoolParameter(Ebp_parameters.BP_LM_REMOTE, false);
		break;
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
		m_LanguageModel = new CRemotePPM(m_EventHandler, m_SettingsStore, alphabet);
		SetBoolParameter(Ebp_parameters.BP_LM_REMOTE, true);
	
		break;
	/* CSFS: Commented out the other language models for the time being as they are not
	 * implemented yet.
	 */
		
	default:
		// If there is a bogus value for the language model ID, we'll default
		// to our trusty old PPM language model.
		m_LanguageModel = new CPPMLanguageModel(m_EventHandler, m_SettingsStore, alphabet);    
		break;
	}
	
	int iNormalization = (int)GetLongParameter(Elp_parameters.LP_NORMALIZATION);
	
	/* CSFS: These used to be int64_max and int64_min.
	 * As far as I can determine from the internet,
	 * these are signed types like long.
	 */
	
	m_Rootmin_min = Long.MIN_VALUE / iNormalization / 2;
	m_Rootmax_max = Long.MAX_VALUE / iNormalization / 2;
	
	m_AlphabetManager = (m_LanguageModel.isRemote())
	                    ? new CRemoteAlphabetManager( this, m_LanguageModel)
			            : new CAlphabetManager( this, m_LanguageModel);
	// m_ControlManagerFactory = new CControlManagerFactory(this, m_LanguageModel);
	
	m_bContextSensitive = true;
	
	uniformAdd = getUniformAdd((int)GetLongParameter(Elp_parameters.LP_NORMALIZATION));
	
	ControlSpace = getControlSpace((int)GetLongParameter(Elp_parameters.LP_NORMALIZATION));
	
	}
	
	/**
	 * The Model responds to the following events:
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
		
		if(Event instanceof CParameterNotificationEvent) {
			CParameterNotificationEvent Evt = (CParameterNotificationEvent)(Event);
			
			if(Evt.m_iParameter == Elp_parameters.LP_MAX_BITRATE
					|| Evt.m_iParameter == Elp_parameters.LP_BOOSTFACTOR
					|| Evt.m_iParameter == Elp_parameters.LP_SPEED_DIVISOR) {
				m_dMaxRate = GetLongParameter(Elp_parameters.LP_MAX_BITRATE) * GetLongParameter(Elp_parameters.LP_BOOSTFACTOR) / 100 / (double)(GetLongParameter(Elp_parameters.LP_SPEED_DIVISOR));
				m_fr.SetMaxBitrate(m_dMaxRate);
			}
			else if(Evt.m_iParameter == Ebp_parameters.BP_CONTROL_MODE) { // Rebuild the model if control mode is switched on/off
				RebuildAroundNode(Get_node_under_crosshair());
				uniformAdd = getUniformAdd((int)GetLongParameter(Elp_parameters.LP_NORMALIZATION));
				ControlSpace = getControlSpace((int)GetLongParameter(Elp_parameters.LP_NORMALIZATION));
			}
			else if(Evt.m_iParameter == Ebp_parameters.BP_DELAY_VIEW) {
				MatchTarget();
			}
			else if(Evt.m_iParameter == Elp_parameters.LP_UNIFORM) {
				uniformAdd = getUniformAdd((int)GetLongParameter(Elp_parameters.LP_NORMALIZATION));
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
		m_Root.commit();
		
		m_Root.DeleteNephews(whichchild);
		
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
			
			oldroots.get(0).OrphanChild(oldroots.get(1));
			oldroots.get(0).DeleteNode();
			// oldroots.set(0, null);
			oldroots.removeFirst();
		}
		
		/* CSFS: These formerly used myint and have been changed to long */
		
		long range = m_Rootmax - m_Rootmin;
		m_Rootmax = m_Rootmin + (range * m_Root.Hbnd()) / GetLongParameter(Elp_parameters.LP_NORMALIZATION);
		m_Rootmin = m_Rootmin + (range * m_Root.Lbnd()) / GetLongParameter(Elp_parameters.LP_NORMALIZATION);
	}
	
	/**
	 * Forget about the queue of old root nodes.
	 * <p>
	 * This should be called when the previous context is no
	 * longer valid. 
	 *
	 */
	protected void ClearRootQueue() {
		while(oldroots.size() > 0) {
			if(oldroots.size() > 1) {
				oldroots.get(0).OrphanChild(oldroots.get(1));
			}
			else {
				oldroots.get(0).OrphanChild(m_Root);
			}
			
			/* CSFS: Again, this originally would delete oldroots[0] before
			 * pop_front'ing the deque.
			 */
			
			oldroots.get(0).DeleteNode();
			oldroots.set(0, null);
			oldroots.removeFirst();
		}
	}

	/**
	 * Calls Make_root repeatedly to make a specified node the new root node.
	 * <p>
	 * This function must be called on some descendent of the current root.
	 * <p>
	 * Behaviour if called upon a node which is not is undefined;
	 * most likely it would eventually run into a node whose parent
	 * has been deleted, and fail with a NullPointerException.
	 * 
	 * @param NewRoot Node to make the root.
	 */
	protected void RecursiveMakeRoot(CDasherNode NewRoot) {
		if(NewRoot == null)
			return;
		
		if(NewRoot == m_Root)
			return;
		
		// FIXME - we really ought to check that pNewRoot is actually a
		// descendent of the root, although that should be guaranteed
		
		if(NewRoot.Parent() != m_Root)
			RecursiveMakeRoot(NewRoot.Parent());
		
		Make_root(NewRoot);
	}
	
	/**
	 * Makes a given Node the root, and then deletes and rebuilds
	 * its children.
	 * <p>
	 * This is intended for situations when an existing Node has
	 * children which are no longer correct; for instance, if
	 * Control Mode has been switched on necessitating a new child
	 * Node, or if the language model has been changed.
	 * <p>
	 * The specified Node must be a descendent of the existing
	 * root, or RecursiveMakeRoot will fail to find a link
	 * between the two.
	 * 
	 * @param Node New root node after the rebuild.
	 */
	protected void RebuildAroundNode(CDasherNode Node) {
		RecursiveMakeRoot(Node);
		ClearRootQueue();
		Node.Delete_children();
		Node.PopulateChildren();
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
	 * @param lower Current root's Lbnd
	 * @param upper Current root's Hbnd
	 */
	protected void Reparent_root() {
		
		/* Change the root node to the parent of the existing node
		 We need to recalculate the coordinates for the "new" root as the 
		 user may have moved around within the current root */
		
		CDasherNode NewRoot;
		
		if(oldroots.size() == 0) {
			
			/* If our internal buffer of old roots is exhausted, */
			
			ListIterator<Character> allEntered = m_DasherInterface.charactersEntered();
			
			for (CDasherNode cur = Get_node_under_crosshair(); ; cur=cur.Parent()) {
				cur.absorbContext(allEntered);
				if (cur == m_Root) break;
			}
			
			NewRoot = m_Root.RebuildParent(allEntered);
			if (NewRoot == null) return; // no existing parent and no way of recreating => give up
		}
		else {
			NewRoot = oldroots.removeLast();
			assert (NewRoot != null);
		}
		
		final long lNorm = GetLongParameter(Elp_parameters.LP_NORMALIZATION);
		
		for (CDasherNode pCurrent = m_Root; pCurrent != NewRoot; ) {
			long upper = pCurrent.Hbnd(), lower = pCurrent.Lbnd(), iWidth = upper-lower;
			long iRootWidth = m_Rootmax - m_Rootmin;
			pCurrent = pCurrent.Parent();
		
			if ((lNorm - upper) / (double)iWidth >
			(m_Rootmax_max - m_Rootmax) / (double)iRootWidth ||
			lower / (double)iWidth > (m_Rootmin - m_Rootmin_min)/(double)iRootWidth) {
				//Fail and undo root creation, new node would be too big
				NewRoot.OrphanChild(m_Root); //ACL is this ok? Is NewRoot necessarily a parent of m_Root (subnodes!)?
				return;
			}
			
			m_Root = NewRoot;
			
		
			m_Rootmax +=  ((lNorm - upper)) * iRootWidth / iWidth;
		
			m_Rootmin -= lower * iRootWidth / iWidth;
		
			for (SGotoItem it : m_deGotoQueue) {
				iRootWidth = it.iN2 - it.iN1;
				it.iN2 += (lNorm - upper) * iRootWidth / iWidth;
				it.iN1 -= lower * iRootWidth / iWidth;
			}
		}
	}

	protected CDasherNode Get_node_under_crosshair() {
		return m_Root.Get_node_under(GetLongParameter(Elp_parameters.LP_NORMALIZATION), m_Rootmin + m_iDisplayOffset, m_Rootmax + m_iDisplayOffset, GetLongParameter(Elp_parameters.LP_OX), GetLongParameter(Elp_parameters.LP_OY));
	}
	

	/**
	 * Gets the node under the current mouse position.
	 * 
	 * @param Mousex Current mouse x co-ordinate
	 * @param Mousey Current mouse y co-ordinate
	 * @return Reference to Node under mouse
	 */
	protected CDasherNode Get_node_under_mouse(long Mousex, long Mousey) {
		return m_Root.Get_node_under(GetLongParameter(Elp_parameters.LP_NORMALIZATION), m_Rootmin + m_iDisplayOffset, m_Rootmax + m_iDisplayOffset, Mousex, Mousey);
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
	public void SetContext(String sNewContext) {
		
		m_deGotoQueue.clear();
		
		/* If a zoom was in progress, cancel it -- this function will likely change
		 * our location within the Dasher world, and so the target being aimed for
		 * is likely not to be there anymore.
		 */
		
		if(oldroots.size() > 0) {
			oldroots.get(0).DeleteNode();
			oldroots.clear();
			// At this point we have also deleted the root - so better NULL pointer
		}
		
		/* CSFS: BUGFIX: Didn't used to check the root really exists before deleting it */
		
		if(m_Root != null) {
			m_Root.DeleteNode();
		}
		m_Root = null;
			
		int iRootSymbol;
		if(sNewContext.length() == 0) {
			iRootSymbol = 0;
			sNewContext = ". ";
		} else {
			ArrayList<Integer> vSymbols = new ArrayList<Integer>();
			m_LanguageModel.getAlphabet().GetSymbols(vSymbols, sNewContext);

			iRootSymbol = (vSymbols.get(vSymbols.size()-1));
		}
		
		m_Root = m_AlphabetManager.GetRoot(null, 0,(int)GetLongParameter(Elp_parameters.LP_NORMALIZATION), iRootSymbol, sNewContext);
		
		Recursive_Push_Node(m_Root, 0);
		
		double dFraction = ( 1 - (1 - m_Root.MostProbableChild() / (double)(GetLongParameter(Elp_parameters.LP_NORMALIZATION))) / 2.0 );
		
		int iWidth = ( (int)( (GetLongParameter(Elp_parameters.LP_MAX_Y) / (2.0*dFraction)) ) );
		
		m_Rootmin = GetLongParameter(Elp_parameters.LP_MAX_Y) / 2 - iWidth / 2;
		m_Rootmax = GetLongParameter(Elp_parameters.LP_MAX_Y) / 2 + iWidth / 2;
		
		m_iDisplayOffset = 0;
	}
	
	/**
	 * Simple struct for returning min/max pairs.
	 */
	class GNRCReturn {
		/**
		 * New max
		 */
		long iNewMin;
		/**
		 * New min
		 */
		long iNewMax;
	}
	
	/**
	 * Calculate the new co-ordinates for the root node after a single
	 * update step. For further information, see Doc/geometry.tex.
	 * 
	 * @param Mousex x mouse co-ordinate measured right to left.
     * @param Mousey y mouse co-ordinate measured top to bottom.
     * @return New root co-ordinates.
	
	 */
	protected GNRCReturn Get_new_root_coords(long Mousex, long Mousey) {
	  // Comments refer to the code immedialtely before them

	  if(Mousex <= 0) {
	    Mousex = 1;
	  }

	  // Avoid Mousex=0, as this corresponds to infinite zoom

	  // If Mousex is too large we risk overflow errors, so make limit it
	  // (this is a somewhat empirical limit - at some point we should
	  // probably do it a little more scientifically)

	  if(Mousex > 60000000)
	    Mousex = 60000000;

	  // Cache some results so we don't do a huge number of parameter lookups

	  long iMaxY = (GetLongParameter(Elp_parameters.LP_MAX_Y));
	  long iOX = (GetLongParameter(Elp_parameters.LP_OX));
	  long iOY = (GetLongParameter(Elp_parameters.LP_OY));

	  /* CSFS: In the original C++ iTargetMin and iTargetMax were, apparently
	   * intentionally, ints and not longs. I've changed this since it seems they may
	   * be liable to overflow if simply assigned the values given.
	   */
	  
	  long iTargetMin = (Mousey - (iMaxY * Mousex) / (2 * iOX));
	  long iTargetMax = (Mousey + (iMaxY * Mousex) / (2 * iOY));

	  // Calculate what the extremes of the viewport will be when the
	  // point under the cursor is at the cross-hair. This is where 
	  // we want to be in iSteps updates

	  int iSteps = m_fr.Steps();

	  assert(iSteps > 0);

	  // iSteps is the number of update steps we need to get the point
	  // under the cursor over to the cross hair. Calculated in order to
	  // keep a constant bit-rate.

	  
	  long iNewTargetMin = (iTargetMin * iMaxY / (iMaxY + (iSteps - 1) * (iTargetMax - iTargetMin)));

	  long iNewTargetMax = ((iTargetMax * iSteps - iTargetMin * (iSteps - 1)) * iMaxY) / (iMaxY + (iSteps - 1) * (iTargetMax - iTargetMin));

	  iTargetMin = iNewTargetMin;
	  iTargetMax = iNewTargetMax;

	  // Calculate the new values of iTargetMin and iTargetMax required to
	  // perform a single update step. Note that the slightly awkward
	  // expressions are in order to reproduce the behaviour of the old
	  // algorithm

	  long iMinSize = (m_fr.MinSize(iMaxY));

	  // Calculate the minimum size of the viewport corresponding to the
	  // maximum zoom.

	  if((iTargetMax - iTargetMin) < iMinSize) {
	    iNewTargetMin = iTargetMin * (iMaxY - iMinSize) / (iMaxY - (iTargetMax - iTargetMin));
	    iNewTargetMax = iNewTargetMin + iMinSize;

	    iTargetMin = iNewTargetMin;
	    iTargetMax = iNewTargetMax;
	  }

	  GNRCReturn retval = new GNRCReturn();
	  
	  retval.iNewMin = (((m_Rootmin - iTargetMin) * GetLongParameter(Elp_parameters.LP_MAX_Y)) / (iTargetMax - iTargetMin));
	  retval.iNewMax = (((m_Rootmax - iTargetMax) * GetLongParameter(Elp_parameters.LP_MAX_Y)) / (iTargetMax - iTargetMin) + GetLongParameter(Elp_parameters.LP_MAX_Y));
	  return retval;
	}
	
	/**
	 * Performs a single update of the model based on a given
	 * mouse position and the time elpased since the last
	 * update took place.
	 * <p>
	 * If BP_OLD_STYLE_PUSH is enabled, prompts the creation
	 * of new segments of tree where appropriate, using
	 * OldPush. If not, we let node pushing take place elsewhere.
	 * <p>
	 * In the case that the m_deGotoQueue contains any points,
	 * indicating that a pre-scheduled zoom is currently in
	 * progress, we move to the next specified point in the queue
	 * instead and ignore the mouse position.
	 * <p>
	 * Internally, this method uses Get_new_root_coords to work
	 * out where to go, and NewGoto to actually go there.
	 * <p>
	 * If this method is called whilst Dasher is paused, it
	 * will do nothing and return false.
	 * 
	 * @param miMousex Current mouse X co-ordinate
	 * @param miMousey Current mouse Y co-ordinate
	 * @param Time Current system time as a UNIX timestamp
	 * @param pAdded Ignored parameter
	 * @return True if we have updated the model, false otherwise.
	 */
	public void oneStepTowards(long miMousex,
			long miMousey, 
			long Time, 
			ArrayList<CSymbolProb> pAdded)	{

		/* CSFS: There used to be an extra parameter here called pNumDeleted
		 * which was an int * which looked as if it was intended to return the
		 * number of deleted nodes to the calling routine; however, every
		 * call in the current source-code supplies a null pointer, so rather
		 * than work on actually returning NumDeleted I've removed the parameter.
		 */
		
		//		TODO: Reimplement this 
//		Clear out parameters that might get passed in to track user activity
		if (pAdded != null)	pAdded.clear();
			
		GNRCReturn retval = Get_new_root_coords(miMousex, miMousey);
		if(GetBoolParameter(Ebp_parameters.BP_OLD_STYLE_PUSH))
			OldPush(miMousex, miMousey);
		NewGoTo(retval.iNewMin, retval.iNewMax);
	}
	
	public boolean nextScheduledStep(long time, ArrayList<CSymbolProb> vAdded) {
		if (vAdded!=null) vAdded.clear();
		if (m_deGotoQueue.size() == 0) return false;
		SGotoItem next = m_deGotoQueue.removeFirst();
		NewGoTo(next.iN1, next.iN2);
		return true;
	}
	
	/**
	 * Expands sections of the node tree, using random point
	 * selection and the Get_node_under method to try to bias
	 * the process in favour of larger nodes.
	 * <p>
	 * Takes account of the current frame rate, and does more
	 * pushing if Dasher is performing better.
	 * <p>
	 * This method is only ever used if BP_OLD_STYLE_PUSH is
	 * enabled which is not the default.
	 * <p>
	 * The actual node pushing is done by Push_Node.
	 * 
	 * @param iMousex Current mouse X co-ordinate
	 * @param iMousey Current mosue Y co-ordinate
	 */
	protected void OldPush(long iMousex, long iMousey) {
//		push node under mouse
		CDasherNode UnderMouse = Get_node_under_mouse(iMousex, iMousey);
		
		/* CSFS: This code originally made liberal use of RandomInt, which on
		 * most platforms called rand(), which in turn generates a random integer
		 * between 0 and, on GCC, the largest signed integer. This may however
		 * vary from platform to platform. This is my attempt to emulate this
		 * under Java.
		 */ 
		
		Push_Node(UnderMouse);
		
		if(Framerate() > 4) {
//			push node under mouse but with x coord on RHS
			CDasherNode Right = Get_node_under_mouse(50, iMousey);
			Push_Node(Right);
		}
		
		if(Framerate() > 8) {
//			push node under the crosshair
			CDasherNode UnderCross = Get_node_under_crosshair();
			Push_Node(UnderCross);
		}
		
		java.util.Random rgen = new java.util.Random();
		int iRandom = rgen.nextInt(Integer.MAX_VALUE);
		
		if(Framerate() > 8) {
//			add some noise and push another node
			CDasherNode Right = Get_node_under_mouse(50, iMousey + iRandom % 500 - 250);
			Push_Node(Right);
		}
		
		iRandom = rgen.nextInt(Integer.MAX_VALUE);
		
		if(Framerate() > 15) {
//			add some noise and push another node
			CDasherNode Right = Get_node_under_mouse(50, iMousey + iRandom % 500 - 250);
			Push_Node(Right);
		}
		
//		only do this if Dasher is flying
		if(Framerate() > 30) {
			for(int i = 1; i < (Framerate() - 30) / 3; i++) {
				
				int iRandom2 = rgen.nextInt(Integer.MAX_VALUE);
				
				if(Framerate() > 8) {
//					add some noise and push another node
					CDasherNode Right = Get_node_under_mouse(50, iMousey + iRandom2 % 500 - 250);
					Push_Node(Right);
				}
				
				iRandom = rgen.nextInt(Integer.MAX_VALUE);
//				push at a random node on the RHS
				CDasherNode Right = Get_node_under_mouse(50, iMousey + iRandom % 1000 - 500);
				Push_Node(Right);
				
			}
		}
	}
	
	/**
	 * Given a Node which is supposed to be the current end of
	 * output, recursively checks whether the parent's Seen property
	 * is true (which indicates it has been output if appropriate)
	 * and invokes the AlphabetManager's Enter and Output methods
	 * on each unseen ancestor in tree-descending order.
	 * <p>
	 * The nodes' Seen flag is also set, preventing repeated
	 * output of the same nodes.
	 * 
	 * @param Node Node to output, possibly including its ancestors
	 * @param pAdded ArrayList which will be filled with output characters
	 * for logging purposes.
	 */
	protected void RecursiveOutput(CDasherNode Node, ArrayList<CSymbolProb> pAdded) {
		if(Node.Parent() != null && (!Node.Parent().isSeen())) 
			RecursiveOutput(Node.Parent(), pAdded);
		
		if(Node.Parent() != null) Node.Parent().Leave();
			
		Node.Enter();
		
		Node.Seen(true);
		Node.Output(pAdded, (int)GetLongParameter(Elp_parameters.LP_NORMALIZATION));
	}

	/*
	class PNGCReturn {
		int iSteps;
		long o1;
		long o2;
		long n1;
		long n2;
		double originalRetval;
	}*/
	
//	public PNGCReturn Plan_new_goto_coords(int iRxnew, long mousey)
	
	/* CSFS: Here we have another class which uses its arguments as output.
	 * As per usual when it's more than one item, I use a small class to return values.
	 * The variable names are the same as they were in the old method prototype.
	 * Potential problem: it's possible they may carry values when submitted to the
	 * routine which are important; however, looking at it I'm fairly sure they're all
	 * written before they're read.
	 */
	
/*	{	
		
		final int ZOOMDENOM = (1<<10);
		final int STEPDENOM = 64;
		PNGCReturn retval = new PNGCReturn(); // create class to return multiple values.
		
		m_Stepnum = (int)GetLongParameter(Elp_parameters.LP_ZOOMSTEPS);
		int iRxnew_dup = iRxnew;
		// note -- iRxnew is the zoom factor  in units of ZOOMDENOM
		retval.o1 = m_Rootmin;
		retval.o2 = m_Rootmax;
		assert(iRxnew > 0);
		if (iRxnew < ZOOMDENOM && m_Rootmax< GetLongParameter(Elp_parameters.LP_MAX_Y) && m_Rootmin>0 ) {
			// refuse to zoom backwards if the entire root node is visible.
			retval.iSteps = 0 ;
			retval.n1 = m_Rootmin;
			retval.n2 = m_Rootmax;
		} 
		else {
			long above=(mousey-retval.o1);
			long below=(retval.o2-mousey);
			
			long miNewrootzoom=GetLongParameter(Elp_parameters.LP_MAX_Y)/2 ;
			long newRootmax=miNewrootzoom+(below*iRxnew/ZOOMDENOM); // is there a risk of overflow in this multiply?
			long newRootmin=miNewrootzoom-(above*iRxnew/ZOOMDENOM);
			
			retval.n1 = newRootmin;
			retval.n2 = newRootmax;
			
			retval.iSteps = 1;
			
			// We might be moving at zoomfactor one vertically, in which case the below invention won't
			// come up with more than one step.  Look for a mousey difference and use an iSteps concordant
			// to that if it would be larger than the iSteps created by taking the log of the zoomfactor. 
			
			/* CSFS: Changed 'distance' to a long as it didn't seem safe to be casting
			 * longs to ints and simply hoping they fit.
			 */
			
			/*long distance = mousey - (GetLongParameter(Elp_parameters.LP_MAX_Y)/2);
			
			double s = (Math.log(2.0) * 2 / Math.log( (STEPDENOM*1.0)/(m_Stepnum*1.0)) ) / 4096;
			
			double alpha = 2 * (2 * s);
			int alternateSteps = (int)(alpha * Math.abs(distance));
			
			// Take log of iRxnew to base ( STEPDENOM / STEPNUM ):
			if ( STEPDENOM > m_Stepnum && m_Stepnum > 0 ) { // check that the following loop will terminate.
				//cout << "iRxnew is " << iRxnew << " and ZOOMDENOM is" << ZOOMDENOM << endl;
				if ( iRxnew > ZOOMDENOM ) {
					while ( iRxnew > ZOOMDENOM ) {
						retval.iSteps += 1;
						iRxnew = iRxnew * m_Stepnum / STEPDENOM;
					}
				} else {
					while ( iRxnew < ZOOMDENOM ) {
						retval.iSteps += 1;
						iRxnew = iRxnew * STEPDENOM / m_Stepnum;
					}
				}
			}
			
			// Done taking log of iRxnew. 
			if (alternateSteps > retval.iSteps) {
				retval.iSteps = alternateSteps;
			}
		}
		
		double iRxnew_ratio = (double) iRxnew_dup / ZOOMDENOM;
		double iRxnew_log = Math.log(iRxnew_ratio);
		retval.originalRetval = iRxnew_log;
		return retval;
	}*/
	
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
		// Find out the current node under the crosshair
		CDasherNode old_under_cross=Get_node_under_crosshair();
		
		// Update the max and min of the root node to make iTargetMin and iTargetMax the edges of the viewport.
			
		if(newRootmin + m_iDisplayOffset > GetLongParameter(Elp_parameters.LP_MAX_Y) / 2 - 1)
			newRootmin = GetLongParameter(Elp_parameters.LP_MAX_Y) / 2 - 1 - m_iDisplayOffset;
		
		if(newRootmax + m_iDisplayOffset < GetLongParameter(Elp_parameters.LP_MAX_Y) / 2 + 1)
			newRootmax = GetLongParameter(Elp_parameters.LP_MAX_Y) / 2 + 1 - m_iDisplayOffset;
		
		// Check that we haven't drifted too far. The rule is that we're not
		// allowed to let the root max and min cross the midpoint of the
		// screen.
		
		if(newRootmax < m_Rootmax_max && newRootmin > m_Rootmin_min && (newRootmax - newRootmin) > GetLongParameter(Elp_parameters.LP_MAX_Y) / 4) {
			// Only update if we're not making things big enough to risk
			// overflow. In theory we should have reparented the root well
			// before getting this far.
			//
			// Also don't allow the update if it will result in making the
			// root too small. Again, we should have re-generated a deeper
			// root in most cases, but the original root is an exception.
			
			m_Rootmax = newRootmax;
			m_Rootmin = newRootmin;
			
			m_iDisplayOffset = (m_iDisplayOffset*9)/10;
		}
		else {
			// TODO - force a new root to be chosen, so that we get better
			// behaviour than just having Dasher stop at this point.
		}
		
		// push node under crosshair
		CDasherNode new_under_cross = Get_node_under_crosshair();
		Push_Node(new_under_cross);
		
		HandleOutput(new_under_cross, old_under_cross);
		//ACL TODO: following should(/did) use original newRootmin/max params
        // not modified versions...
        total_nats += -1.0 * Math.log((newRootmax - newRootmin) / 4096.0);
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
	protected void HandleOutput(CDasherNode NewNode, CDasherNode OldNode) {
		if(NewNode != OldNode)
			DeleteCharacters(NewNode, OldNode);
		
		if(NewNode.isSeen())
			return;
		
		// TODO: Reimplement second parameter
		RecursiveOutput(NewNode, null);
	}
	
	/**
	 * Traces back from oldnode to either a) newnode, in the case
	 * that oldnode is a descendent of newnode, or b) to the nearest
	 * common ancestor (ie. the nearest ancestor of newnode which
	 * has its seen flag set), calling the AlphabetManager's
	 * Undo method on each in turn.
	 * <p>
	 * In case a) we will now be consistent with the user's current
	 * location; in case b) we have deleted the text belonging
	 * to oldnode's branch but not output the text for newnode;
	 * HandleOutput must be used to finish the job.
	 * <p>
	 * If either parameter is null, this method will return false
	 * and take no action.
	 * 
	 * @param newnode Node currently under the crosshair
	 * @param oldnode Node previously under the crosshair
	 * @return False if either argument as null, True otherwise.
	 */
	protected boolean DeleteCharacters(CDasherNode newnode, CDasherNode oldnode) {
			
		/* CSFS: Much like tap_on_screen, this method used to have an int *
		 * argument named pNumDeleted. However, every call from an external
		 * method passed in a null argument; only DeleteCharacters itself ever
		 * made use of it. I'm assuming this is fossilised code and have
		 * removed the argument.
		 */
		
		if(newnode == null || oldnode == null)
			return false;
		
		// This deals with the trivial instance - we're reversing back over
		// text that we've seen already
		if(newnode.isSeen() == true) {
			if(oldnode.Parent() == newnode) {
				oldnode.Undo();
				oldnode.Parent().Enter();
				oldnode.Seen(false);
				return true;
			}
			if(DeleteCharacters(newnode, oldnode.Parent()) == true) {
				oldnode.Undo();
				oldnode.Parent().Enter();
				oldnode.Seen(false);
				return true;
			}
		}
		else {
			// This one's more complicated - the user may have moved onto a new branch
			// Find the last seen node on the new branch
			CDasherNode lastseen = newnode.Parent();
			
			while(lastseen != null && lastseen.isSeen() == false) {
				lastseen = lastseen.Parent();
			}
			// Delete back to last seen node
			while(oldnode != lastseen) {
				
				oldnode.Seen(false);
				
				oldnode.Undo();
				oldnode.Parent().Enter();
				oldnode = oldnode.Parent();
				if(oldnode == null) {
					return false;
				}
			}
		}
		return false;
	}

	/**
	 * Requests node probabilities from the language model given
	 * a context and a normalization value (usually LP_NORMALIZATION).
	 * <p>
	 * Internally, this recalculates the norm value using getNonUniformNorm,
	 * and passes the context and this new norm onto m_LanguageModel.getProbs.
	 * <p>
	 * Once the probabilities are calculated, it runs them
	 * through adjustProbs before returning.
	 *  
	 * @param context Language model context in which the
	 * probabilities are to be found.
	 * @param iNorm Normalization
	 * @return Array of probabilities, in alphabet order.
	 */
	public long[] GetProbs(CContextBase context, int iNorm) {
		
		/* Lots of refactoring has gone on here. The model used to do three
		 * things in this function: calculate the non-uniform norm, control
		 * space and so forth, retrieve the probs, and then perform a little
		 * post-processing before returning its answer. I have refactored
		 * these out into three seperate stages. My reason for doing so was
		 * so that the networking system can call the stages asynchronously,
		 * but it also helps readability.
		 */
		
		long[] probs = m_LanguageModel.GetProbs(context, getNonUniformNorm(iNorm));
		
		adjustProbs(probs);
		
		return probs;
		
	}
	
	/**
	 * Increments all probabilities by the value of uniformAdd,
	 * and sets the final probability to ControlSpace.
	 * 
	 * @param probs The probabilities to modify
	 */
	public void adjustProbs(long[] probs) {
		
		for(int k = 1; k < probs.length; ++k) probs[k] += uniformAdd;
		
		probs[probs.length - 1] = ControlSpace;

		
	}
	
	/**
	 * Calculates the non-uniform norm.
	 * 
	 * @param iNorm Normalization value, usually LP_NORMALIZATION
	 * @return Non-uniform norm
	 */
	public int getNonUniformNorm(int iNorm) {
//		 Total number of symbols
		int iSymbols = m_cAlphabet.GetNumberSymbols();      // note that this includes the control node and the root node
		
		//NewSymbols.ensureCapacity(iSymbols);
		//for(int i = 0; i < iSymbols; i++) {
		//	NewSymbols.add(i);          // This will be replaced by something that works out valid nodes for this context
		//}
		
		// TODO - sort out size of control node - for the timebeing I'll fix the control node at 5%
		
		int uniform_add;
		long control_space; // CSFS: Changed to long to match the probs.
		long uniform = GetLongParameter(Elp_parameters.LP_UNIFORM);
		
		if(!GetBoolParameter(Ebp_parameters.BP_CONTROL_MODE)) {
			control_space = 0;
			uniform_add = (int)((iNorm * uniform) / 1000) / (iSymbols - 2);  // Subtract 2 from no symbols to lose control/root nodes
			return iNorm - (iSymbols - 2) * uniform_add;
		}
		else {
			control_space = (long)(iNorm * 0.05);
			uniform_add = (int)(((iNorm - control_space) * uniform / 1000) / (iSymbols - 2));        // Subtract 2 from no symbols to lose control/root nodes
			return (int)(iNorm - control_space - (iSymbols - 2) * uniform_add);
		}
		
	}
	
	/**
	 * Calculates a value to add to all probabilities before use,
	 * preventing any zero values from being used and potentially
	 * resulting in missing nodes.
	 * 
	 * @param iNorm Normalization value (usually LP_NORMALIZATION)
	 * @return Uniform value to add to all probabilities.
	 */
	protected int getUniformAdd(int iNorm) {
		
		int iSymbols = m_cAlphabet.GetNumberSymbols();      // note that this includes the control node and the root node
		long uniform = GetLongParameter(Elp_parameters.LP_UNIFORM);
		
		if(!GetBoolParameter(Ebp_parameters.BP_CONTROL_MODE)) {
			return (int)((iNorm * uniform) / 1000) / (iSymbols - 2);  // Subtract 2 from no symbols to lose control/root nodes
		}
		else {
			return (int)(((iNorm - getControlSpace(iNorm)) * uniform / 1000) / (iSymbols - 2));        // Subtract 2 from no symbols to lose control/root nodes
		}
	}
	
	/**
	 * Gets the probability to associate with the control node
	 * <p>
	 * This will be either 5% of the norm value if control mode
	 * is enabled, or zero otherwise.
	 * 
	 * @param iNorm Normalization value (usually LP_NORMALIZATION)
	 * @return Probability of control node.
	 */
	protected long getControlSpace(int iNorm) {
		if(!GetBoolParameter(Ebp_parameters.BP_CONTROL_MODE)) {
			return 0;
		}
		else {
			return (long)(iNorm * 0.05);
		}
	}
	
	/**
	 * Deletes and then repopulates the children of a given
	 * node.
	 * <p>
	 * If the pushed node has no associated context, an attempt
	 * is made to derive it by extending that of the node's
	 * parent, and as a last ditch, creating an empty one.
	 * 
	 * @param Node Node to push. Must not be null.
	 */
	protected void Push_Node(CDasherNode Node) {
		
		if(Node.HasAllChildren()) {
			assert(Node.ChildCount() > 0);
			// if there are children just give them a poke
			
			for(CDasherNode i : Node.Children()) {
				i.Alive(true);
			}
			return;
		}
		
		Node.Delete_children();
		
		Node.Alive(true);
		
		Node.PopulateChildren();
		Node.SetHasAllChildren(true);
	}
	
	/**
	 * Pushes a given node and recursively pushes its children to a given depth.
	 * <p>
	 * The tree of recursive pushes will be truncated whenever an attempt
	 * is made to push a node filling less than 10% of the screen
	 * or which is the control node.
	 * 
	 * @param Node Node at which to start pushing
	 * @param iDepth Maximum depth to which we should push
	 */
	protected void Recursive_Push_Node(CDasherNode Node, int iDepth) {
		
		if(Node.Range() < 0.1 * GetLongParameter(Elp_parameters.LP_NORMALIZATION)) {
			return;
		}
		
		// Resurrect this if/when we redo control mode...
		// if(Node.Symbol() == GetControlSymbol()) {
		//	return;
		//}
		
		Push_Node(Node);
		
		if(iDepth == 0)
			return;
		
		for(CDasherNode ch : Node.Children()) {
			Recursive_Push_Node(ch, iDepth - 1);
		}
	}
	
	/**
	 * Calls the View's Render method on our current Root; the View
	 * will take care of all drawing from here on in.
	 * <p>
	 * If BP_OLD_STYLE_PUSH is not enabled, all Nodes which the
	 * View reports it has drawn will be pushed here, and all which
	 * the View reported as undrawable due to being too small,
	 * off the screen, etc, will have their children deleted.
	 * 
	 * @param View View to which we wish to draw
	 * @param bRedrawDisplay Whether to force a complete redraw
	 * @return True if the View changed anything, false otherwise
	 */	
	public boolean RenderToView(CDasherView View, boolean bRedrawDisplay) {
		ArrayList<CDasherNode> vNodeList = new ArrayList<CDasherNode>();
		ArrayList<CDasherNode> vDeleteList = new ArrayList<CDasherNode>();
		
		boolean bReturnValue = View.Render(m_Root, m_Rootmin + m_iDisplayOffset, m_Rootmax + m_iDisplayOffset, vNodeList, vDeleteList, bRedrawDisplay);

		if(!GetBoolParameter(Ebp_parameters.BP_OLD_STYLE_PUSH)) {
			for(CDasherNode it : vNodeList) {
				Push_Node(it);
			}
		}
		
		for(CDasherNode it : vDeleteList) {
			it.Delete_children();
		}	
		return bReturnValue;
	}
	
	/**
	 * If the view reports that our current root node isn't
	 * visible, calls Reparent_root; if only one child of the
	 * current root is Alive (ie. on screen and visible), makes
	 * this child the root.
	 * <p>
	 * The actual work will be done by Reparent_root and Make_root
	 * respectively; this just decides which to use and when.
	 * 
	 * @param View View against which to check node visibility.
	 * @return True if Reparent_root made any changes, false otherwise.
	 */
	public void CheckForNewRoot(CDasherView View) {
		
		if(m_deGotoQueue.size() > 0)
			return;
		
		if(!View.NodeFillsScreen(m_Rootmin,m_Rootmax)) {
			Reparent_root();
			return;
		}
		
		while (true) {
			int alive = 0;
			CDasherNode theone = null;
			
			// Find whether there is exactly one alive child; if more, we don't care.
				
			for(CDasherNode i : m_Root.Children()) {
				if(i.Alive()) {
					alive++;
					theone = i;
					if(alive > 1)
						break;
				}
			}
			
			if(alive == 1) {
				// We must have zoomed sufficiently that only one child of the root node 
				// is still alive.  Let's make it the root.
				
				/* CSFS: All formerly myints */
				
				long y1 = m_Rootmin;
				long y2 = m_Rootmax;
				long range = y2 - y1;
				
				long newy1 = y1 + (range * theone.Lbnd()) / (int)GetLongParameter(Elp_parameters.LP_NORMALIZATION);
				long newy2 = y1 + (range * theone.Hbnd()) / (int)GetLongParameter(Elp_parameters.LP_NORMALIZATION);
				if(View.NodeFillsScreen(newy1, newy2)) {
					Make_root(theone);
					continue; //try again, looking for a child of the new root...
				}
			}
			//either no child on screen (?!), or more than one
			return;
		}
	}

	/**
	 * TODO work out what this does and document.
	 * 
	 * @param dasherx Dasher X co-ordinate
	 * @param dashery Dasher Y co-ordinate
	 * @return Correction factor
	 */
	protected double CorrectionFactor(long dasherx, long dashery) {
		double dX = 1 - dasherx/2048.0;
		double dY = dashery/2048.0 - 1;
		
		/* CSFS: All C++ maths functions switched for Java's equivalent;
		 * now I just hope for equivalent function!
		 */
		
		double dR = Math.sqrt(Math.pow(dX, 2.0) + Math.pow(dY, 2.0));
		
		if(Math.abs(dX) < 0.1)
			return dR * (1 + dX /2.0+ Math.pow(dX, 2.0) / 3.0 + Math.pow(dX, 3.0) / 4.0 + Math.pow(dX, 4.0) / 5.0);
		else
			return -dR * Math.log(1 - dX) / dX;
	}
	
	/**
	 * Interpolates between our current position and a given
	 * new position, scheduling a zoom to the new position by
	 * adding the interpolated points to m_deGotoQueue.
	 * <p>
	 * dasherx will be increased to 100 if this is not already so.
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
		
		/* CSFS: In a few locations here there were implicit casts
		 * where an integer/long is multiplied by some double
		 * and then is implictly cast back to int/long. I've made
		 * these explicit and again hope this is what's intended...
		 */
		
		// TODO: What is the following line for?
		if (dasherx < 2) { dasherx = 100; }
		
		double dCFactor = CorrectionFactor(dasherx, dashery);
		
		int iSteps = (int)(GetLongParameter(Elp_parameters.LP_ZOOMSTEPS) * dCFactor);
		long n1, n2, iTarget1, iTarget2;
			
		iTarget1 = dashery - dasherx;
		iTarget2 = dashery + dasherx;
		
		double dZ = 4096 / (double)(iTarget2 - iTarget1);
		
		n1 = (long)((m_Rootmin - iTarget1) * dZ);
		n2 = (long)((m_Rootmax - iTarget2) * dZ + 4096);
		
		m_deGotoQueue.clear();
		
		
		for(int s = 1; s < iSteps; ++s) {
			// use simple linear interpolation. Really should do logarithmic interpolation, but
			// this will probably look fine.
			SGotoItem sNewItem = new SGotoItem();
			
			sNewItem.iN1 = (s * n1 + (iSteps-s) * m_Rootmin) / iSteps;
			sNewItem.iN2 = (s * n2 + (iSteps-s) * m_Rootmax) / iSteps;
			sNewItem.iStyle = 1;
			
			m_deGotoQueue.addLast(sNewItem);
		} 
		
		SGotoItem sNewItem = new SGotoItem();
		
		sNewItem.iN1 = n1;
		sNewItem.iN2 = n2;
		sNewItem.iStyle = 2;
		
		m_deGotoQueue.addLast(sNewItem);
		
		/* CSFS: Swapped push_back for addLast in both cases */
	}
	
	/**
	 * Runs the LanguageModel's UnregisterComponent method, if
	 * there is currently a LanguageModel.
	 *
	 */
	public void deleteLM() {
		if(m_LanguageModel != null) m_LanguageModel.UnregisterComponent();
	}
	
	/**
	 * Unregisters the language model and then ourselves.
	 */
	public void UnregisterComponent() {
		if(m_LanguageModel != null) m_LanguageModel.UnregisterComponent();
		super.UnregisterComponent();
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
		if (GetBoolParameter(Ebp_parameters.BP_DELAY_VIEW))
			m_iDisplayOffset -= iOffset;
	} 
	
	/**
	 * Sets each of RootMin and RootMax to match
	 * their TargetMax and TargetMin partners.
	 */
	protected void MatchTarget() {
		m_Rootmin += m_iDisplayOffset;
		m_Rootmax += m_iDisplayOffset;
		m_iDisplayOffset = 0;
	}
	
	/* CSFS: Both of these methods currently use boolean types to
	 * indicate failure or success. Ideally these would use exceptions
	 * but it doesn't seem to cause a problem at present.
	 */
	
	//Framerate functions:
	
	/**
	 * Deferred to m_fr.
	 */
	public void NewFrame(long Time) { // Used to be unsigned -- potential problem.
		m_fr.NewFrame(Time);
	}
	
	/**
	 * Deferred to m_fr.
	 */
	public double Framerate() {
	    return m_fr.Framerate();
	}
	
	/**
	 * Deferred to m_fr.
	 */
	public void Reset_framerate(long Time) {
	    m_fr.Reset(Time);
	}
	
	/**
	 * Deferred to m_fr.Initialise().
	 */
	public void Halt() {
	    m_fr.Initialise();
	}
	
	/**
	 * Deferred to m_fr.
	 */
	public void SetBitrate(double TargetRate) {
	    m_fr.SetBitrate(TargetRate);
	}
	
	/**
	 * Sets total_nats back to 0.
	 */
	public void ResetNats() {
		total_nats = 0;
	}
	
	/**
	 * Gets total_nats
	 * @return total_nats
	 */
	public double GetNats() {
		return total_nats;
	}
	
	/**
	 * Gets our current alphabet's Space symbol
	 * 
	 * @return Requested symbol number
	 */
	public int GetSpaceSymbol() {
		return m_cAlphabet.GetSpaceSymbol();
	}
	
	/**
	 * Gets our current alphabet's Control symbol
	 * 
	 * @return Requested symbol number
	 */
	public int GetControlSymbol() {
		return m_cAlphabet.GetControlSymbol();
	}
	
	/**
	 * Gets our current alphabet's Start-conversion symbol
	 * 
	 * @return Requested symbol number
	 */
	public int GetStartConversionSymbol() {
		return m_cAlphabet.GetStartConversionSymbol();
	}
	
	// Potentially redundant function.
	/**
	 * Deferred to m_cAlphabet
	 */
	public String GetDisplayText(int iSymbol) {
		return m_cAlphabet.GetDisplayText(iSymbol);
	}
	
	/* CSFS: Potential trouble here, as it seems one of these may be intended
	 * to produce a copy of the alphabet, and the other to return a reference
	 * to the live one.
	 */
	
	/**
	 * Retrieves our current alphabet.
	 */
	public CAlphabet GetAlphabet() {
		return m_cAlphabet; // FIXME CLONE
	}
	
	/**
	 * Retrieves our current alphabet.
	 */
	public CAlphabet GetAlphabetNew() {
		return m_cAlphabet;
	}
	
	/* CSFS: In the original C++, these read:
	 * 
	 * const CAlphabet & GetAlphabet() const {
     * return *m_pcAlphabet;
     * }
     *
     * CAlphabet *GetAlphabetNew() const {
     * return m_pcAlphabet;
     * }
     * 
     * Confusingly, it looks as if the return from GetAlphabet will be
     * a clone of the Alphabet currently registered, whereas the
     * return from GetAlphabetNew will be a pointer to the current
     * alphabet which one could then alter
     */
	
	/**
	 * Constructs a new root Node of a given type, with a given parent
	 * which may be null if necessary.
	 * 
	 * @param iType Type of node to create. At present, ignored; only ordinary letter nodes are created.
	 * @param Parent Parent of the node we will create. May be null.
	 * @param iLower Desired Lbnd of the new Node
	 * @param iUpper Desired Hbnd of the new Node
	 * @return Newly created DasherNode
	 */
	public CDasherNode GetRoot( int iType, CDasherNode Parent, long iLower, long iUpper, int UserData ) {
		
		//ACL fake out empty context here. This method seems a bit meaningless/pointless right now...
			return m_AlphabetManager.GetRoot(Parent, iLower, iUpper, UserData, ". ");
		// case 1:
			// return m_ControlManagerFactory.GetRoot(Parent, iLower, iUpper, UserData);
		//case 2:
		//	return m_ConversionManagerFactory.GetRoot(pParent, iLower, iUpper, UserData);
		
		
		/* CSFS: Excluded the last case for now as it was surrounded
		 * by #ifdef JAPANESE in the original source, and I have not
		 * ported the Japanese modules yet.
		 */
		
	}
	
	// Control mode stuff
	
	/**
	 * Stub; implement if Control Mode is required.
	 * <p>
	 * The code for this is commented but present in the source.
	 */
	public void RegisterNode( int iID, String strLabel, int iColour ) {
		// m_ControlManagerFactory.RegisterNode(iID, strLabel, iColour);
	}
	
	/**
	 * Stub; implement if Control Mode is required.
	 * <p>
	 * The code for this is commented but present in the source.
	 */
	public void ConnectNode(int iChild, int iParent, int iAfter) {
		// m_ControlManagerFactory.ConnectNode(iChild, iParent, iAfter);
	}
	
	/**
	 * Stub; implement if Control Mode is required.
	 * <p>
	 * The code for this is commented but present in the source.
	 */
	public void DisconnectNode(int iChild, int iParent) {
		// m_ControlManagerFactory.DisconnectNode(iChild, iParent);
	}
	
	/* CSFS: These mades stubs for now until I've written Control Mode back in */
	
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
	
	/* CSFS: This class used to return a context; these have
	 * been converted to a class as detailed in the notes found
	 * in CLanguageModel.
	 */
	
	/**
	 * Deferred to m_LanguageModel
	 */
	protected CContextBase CreateEmptyContext() {
		return m_LanguageModel.CreateEmptyContext();
	}
	
	/**
	 * Deferred to m_cAlphabet
	 */
	public int GetColour(int s) {
		return m_cAlphabet.GetColour(s);
	}

	/**
	 * Deferred to m_cAlphabet
	 */	
	public ArrayList<Integer> GetColours() {
		// See comments in CAlphabet
		return m_cAlphabet.GetColours();
	}
	
}
