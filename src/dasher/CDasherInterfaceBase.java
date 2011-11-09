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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.io.*;
import java.lang.ref.WeakReference;
import java.nio.channels.AsynchronousCloseException;

import org.xml.sax.SAXException;

import dasher.CControlManager.ControlAction;

/**
 * DasherInterfaceBase is the core of Dasher, and the entry
 * point for all high-level functions which control the system
 * as a whole.
 * <p>
 * The majority of the actual work involved in running Dasher is
 * done by the Interface's two main children, a DasherModel and
 * a DasherView; the interface primarily acts as a co-ordinator.
 * <p>
 * The DasherModel represents the tree of DasherNodes which forms
 * the core of Dasher. It knows how to update the model in response
 * to user input, and how to build, destroy and rebuild the tree.
 * <p>
 * The DasherView on the other hand is responsible for taking the abstract
 * information in the Model and rendering it, visually or otherwise,
 * in some way.
 * <p>
 * the Interface simply sits in the middle and co-ordinates
 * whole-system actions such as updating the model and drawing a new frame.
 * <p>
 * The interface has a number of methods which remain abstract;
 * this means that in order to implement Dasher, this class must be
 * extended and the missing methods implemented.
 * <p>
 * Typically one would also extend DasherInput to provide a co-ordinate
 * input of some sort, and implement DasherScreen to provide visual 
 * (or other) display.   
 */
abstract public class CDasherInterfaceBase extends CDasherComponent {
	
		
	public CFileLogger g_Logger; // CSFS: No logging yet.
	// public final eLogLevel g_iLogLevel = logNORMAL; // FIXME enums
	// public final int       g_iLogOptions = logTimeStamp | logDateStamp;
	
	/**
	 * Current colour scheme
	 */
	protected CCustomColours m_Colours;
	
	/**
	 * The DasherModel, i.e. tracks locations of nodes on screen. Only one need be created,
	 * as it can be fed nodes from multiple NodeCreation/AlphabetManagers.
	 */
	private final CDasherModel m_DasherModel = new CDasherModel(this);
	
	protected CNodeCreationManager m_pNCManager;
	
	/**
	 * Current DasherScreen
	 */
	protected CDasherScreen m_DasherScreen;
	/**
	 * The screen (i.e. same as {@link #m_DasherScreen}), <em>iff</em> it's an instance of the {@link CMarkerScreen} subinterface;
	 * otherwise, <code>null</code>. (Both set in {@link #ChangeScreen(CDasherScreen)}.
	 */
	protected CMarkerScreen m_MarkerScreen;
	
	/**
	 * Current DasherView
	 */
	protected CDasherView m_DasherView;
	
	/**
	 * Current Input device
	 */
	protected CDasherInput m_Input;
	
	/**
	 * Current AlphIO
	 */
	protected final CAlphIO m_AlphIO = new CAlphIO(this);
	
	/**
	 * Current ColourIO
	 */
	protected final CColourIO m_ColourIO = new CColourIO(this);
	
	/**
	 * Our logging module
	 */
	protected CUserLog m_UserLog;
	
	/**
	 * Current input filter
	 */
	protected CInputFilter m_InputFilter;
	
	/**
	 * The module manager
	 */
	protected final CModuleManager m_oModuleManager = new CModuleManager();;
	
	/**
	 * Lock engaged when Dasher is being destroyed
	 */
	protected boolean m_bShutdownLock;
	
	/**
	 * Lock engaged when we're in the process of connecting
	 * to a remote language model
	 */
	protected boolean m_bConnectLock; // Connecting to server.
	
	/** Message supplied in any CLockEvent causing BP_TRAINING to be set */
	protected String m_sLockMsg;
	
	/**
	 * Look for XML files whose name matches the specified prefix;
	 * feed them into the specified parser. Both "system" and "user"
	 * files/locations should be scanned.
	 * 
	 * @param parser XMLFileParser to use to process all files found.
	 * (On systems where this is relevant, "system" files should be
	 * processed with <code>parser.parseFile(&lt;file&gt;,false)</code>,
	 * to load them as immutable, whereas "user" files with <code>true</code>,
	 * to allow them to be edited.)
	 *
	 * @param prefix Only process files whose name begins with this
	 */
	protected abstract void ScanXMLFiles(XMLFileParser parser, String prefix);
	
	/**
	 * Open a specified file or file(s), from as many locations as contain it - e.g.
	 * system and user locations, JAR-packed resources, remote network locations, etc.
	 * 
	 * @param fname filename, e.g. "training_english_GB.txt"
	 * @param into Collection to which inputstreams for all files found should be added. 
	 */
	protected abstract void GetStreams(String fname, Collection<InputStream> into);
	
	/** Must return a representation of whatever we're currently editing. */
	//TODO can clients keep references to this, that persist over SetOffset(,true)?
	public abstract EditableDocument getDocument();
	
	/**
	 * Sole constructor. Sets up the tree of DasherComponents
	 * to read from the SettingsStore, with this as root and the
	 * last listener. (Thus, no more than one DasherInterfaceBase
	 * should be created per SettingsStore.)
	 * {@link #LoadData} should be called before operations like
	 * {@link #GetPermittedValues(Esp_parameters, Collection)} are meaningful;
	 * {@link #DoSetup} should be called after LoadData before e.g. frames may
	 * be rendered.
	 */
	public CDasherInterfaceBase(CSettingsStore sets) {
		super(sets);
		sets.setLastListener(this);
	}
	
	/**
	 * Loads all required data from external sources:
	 * <ul>
	 * <li>available alphabets
	 * <li>available colour schemes
	 * <li>input filters and devices (via {@link #CreateModules()}
	 * <ul>
	 * Must be called after construction, and before {@link #DoSetup()}.
	 */
	protected void LoadData() {
		ScanXMLFiles(m_AlphIO, "alphabet");
		
		ScanXMLFiles(m_ColourIO, "colour");
		CreateModules();
	}
	
	/**
	 * Does the bulk  of the work in making Dasher ready for use, following
	 * a call to {@link #LoadData}. This mainly consists of setting up necessary
	 * data structures for colours, alphabet, etc., according to the user
	 * preferences. Also trains the LanguageModel via {@link #train(CAlphabetManager)},
	 * so may take some time: if the interface is only required for
	 * querying/updating settings, this method need not be called.
	 * When realize terminates, Dasher will be in a broadly usable
	 * state, tho it will need a screen which should be created
	 * externally and registered with ChangeScreen().
	 */
	protected void DoSetup() {
		ChangeColours();
		
		// Create the user logging object if we are suppose to.
		// (ACL) Used to be done following ChangeAlphabet, with comment:
		// "We wait until now so we have the real value of the parameter and not just the default."
		// - presume this was referring to non-persistent parameters such as SP_DEFAULT_COLOUR_ID,
		// which used to be set according to the Alphabet but have now been removed.
		//(Of course the whole of user logging is stubbed anyway...)
		int iUserLogLevel = (int)GetLongParameter(Elp_parameters.LP_USER_LOG_LEVEL_MASK);
		if (iUserLogLevel > 0) 
			m_UserLog = new CUserLog(this, iUserLogLevel);
		
		ChangeAlphabet();
		
		CreateInput();
		CreateInputFilter();
		
		// All the setup is done by now, so let the user log object know
		// that future parameter changes should be logged.
		if (m_UserLog != null) m_UserLog.InitIsDone();
	}
	
	/**
	 * Instructs all componenets to unregister themselves with the
	 * event handler, and nulls our pointers to them, such that
	 * they will be available for garbage collection.
	 */
	public void DestroyInterface() {
		// When we destruct on shutdown, we'll output any detailed log file
		if (m_UserLog != null)
		{
		    m_UserLog.OutputFile();
		    m_UserLog.Close();
		    // FIXME again do what's appropriate
		    m_UserLog = null;
		}
		
		if (g_Logger != null) {
		    g_Logger.Destroy();
		    g_Logger = null;
		}
	}
	
	/**
	 * Override to process event by ourselves, after (as per superclass)
	 * dispatching to all registered listeners/components.
	 * <p>
	 * The interface responds to the following parameter changes:
	 * <p>
	 * <i>BP_COLOUR_MODE</i>: redraws the display.
	 * <p>
	 * <i>BP_OUTLINE_MODE</i>: Redraws the display.
	 * <p>
	 * <i>BP_CONNECT_LOCK</i>: Sets the internal m_bConnectLock variable.
	 * <p>
	 * <i>LP_ORIENTATION</i>: Sets the LP_REAL_ORIENTATION parameter either
	 * to the value of LP_ORIENTATION, or if the latter is -2 (a special sentinel value)
	 * queries the current alphabet for its preferred orientation, and
	 * sets LP_REAL_ORIENTATION appropriately.
	 * <p>
	 * <i>SP_ALPHABET_ID</i>: Calls ChangeAlphabet() to rebuild the DasherModel
	 * appropriately
	 * <p>
	 * <i>SP_COLOUR_ID</i>: Calls ChangeColours() to insert the new colour scheme.
	 * <p>
	 * <i>BP_PALETTE_CHANGE and SP_DEFAULT_COLOUR_ID</i>: If Palette Change is true,
	 * changes COLOUR_ID to match DEFAULT_COLOUR_ID which contains the current
	 * alphabet's preferred colour scheme.
	 * <p>
	 * <i>LP_LANGUAGE_MODEL_ID</i>: Runs CreateDasherModel() to rebuild the model
	 * based on our newly chosen language model.
	 * <p>
	 * <i>SP_LM_HOST</i>: If we are currently using a remote language model,
	 * rebuilds the Model as above; otherwise, ignores.
	 * <p>
	 * <i>LP_DASHER_FONTSIZE and LP_LINE_WIDTH</i>: Redraws the display.
	 * <p>
	 * <i>SP_INPUT_FILTER</i>: Runs CreateInputFilter() to recreate the
	 * requested filter.
	 * <p>
	 * <i>SP_INPUT_DEVICE</i>: Runs CreateInput() to create the requested
	 * input device.
	 * <p>
	 * It also responds to LockEvents by setting BP_TRAINING to the value
	 * indicated and storing their message&progress in m_sLockMsg.
	 * 
	 * @param Event The event the interface is to process.
	 */
	public void HandleEvent(EParameters eParam) {
		
		if(eParam == Ebp_parameters.BP_COLOUR_MODE) {       // Forces us to redraw the display
			// TODO) { Is this variable ever used any more?
			Redraw(true);
		} else if(eParam ==  Ebp_parameters.BP_OUTLINE_MODE) {
			Redraw(true);
		} else if(eParam == Ebp_parameters.BP_CONNECT_LOCK) {
			m_bConnectLock = GetBoolParameter(Ebp_parameters.BP_CONNECT_LOCK);
		} else if(eParam ==  Esp_parameters.SP_ALPHABET_ID) {
			ChangeAlphabet();
			Redraw(true);
		} else if(eParam ==  Esp_parameters.SP_COLOUR_ID) {
			//User has requested a new colour scheme
			ChangeColours();
			Redraw(true);
		} else if(eParam == Elp_parameters.LP_LANGUAGE_MODEL_ID
				|| (eParam == Esp_parameters.SP_LM_HOST && GetLongParameter(Elp_parameters.LP_LANGUAGE_MODEL_ID)==5)) {
			m_LMcache.clear(); //All existing LMs use old param values
			CreateNCManager();
			Redraw(true);
		} else if(eParam == Elp_parameters.LP_LINE_WIDTH) {
			Redraw(false); // TODO - make this accessible everywhere
		} else if(eParam == Elp_parameters.LP_DASHER_FONTSIZE) {
			// TODO - make screen a CDasherComponent child?
			Redraw(true);
		} else if (eParam == Esp_parameters.SP_ORIENTATION) {
			if (m_DasherView!=null) m_DasherView.setOrientation(computeOrientation());
		} else if(eParam == Esp_parameters.SP_INPUT_DEVICE) {
			CreateInput();
			Redraw(false);
		} else if(eParam == Esp_parameters.SP_INPUT_FILTER) {
			List<ControlAction> prevActs = getControlActions();
			CreateInputFilter();
			if (GetBoolParameter(Ebp_parameters.BP_CONTROL_MODE)
					&& !prevActs.equals(getControlActions()))
				UpdateControlManager();
			Redraw(false);
		} else if (eParam == Ebp_parameters.BP_CONTROL_MODE || eParam == Elp_parameters.LP_UNIFORM) {
			UpdateControlManager();
		}
	}
	
	/** Create a new NCManager and ControlManager, but using the previous' (existing) NCManager's AlphabetManager
	 * (preserving training). This'll updates cached values for normalization, uniformity, etc.  
	 */
	protected void UpdateControlManager() {
		if (m_pNCManager!=null) m_pNCManager = new CNodeCreationManager(this, m_pNCManager.getAlphabetManager(), makeControlManager());
		forceRebuild(); //perhaps overkill, but makes sure control nodes appear, pronto
	}
	
	public void Lock(String msg, int iPercent) {
		// TODO: 'Reference counting' for locks?
		if (iPercent>=0) {
			m_sLockMsg = (msg==null) ? "Training" : msg;
			if (iPercent!=0) m_sLockMsg+=" "+iPercent;
		} else {
			m_sLockMsg = null;
			forceRebuild();
		}
		Redraw(false); //assume %age or m_bLock has changed... 
	}
	
	/** Subclasses should implement to display a message to the user, e.g.
	 * in a dialog box.
	 * @param msg Message text
	 * @param iSeverity 0 for informative, 1 for warning, 2 for error
	 */
	public abstract void Message(String msg, int severity);
	
	private final Map<CAlphIO.AlphInfo,WeakReference<CLanguageModel<?>>> m_LMcache
		= new HashMap<CAlphIO.AlphInfo, WeakReference<CLanguageModel<?>>>();
	/**
	 * Creates a new DasherModel, deleting any previously existing
	 * one if necessary.
	 * <p>
	 * The DasherModel does most of the actual initialisation work,
	 * so see the constructor documentation for DasherModel for details.
	 * <p>
	 * This function also trains the newly created model using the
	 * using the new Alphabet's specified training text. 
	 */
	private void CreateNCManager() 
	{
		if(m_AlphIO == null)
			throw new IllegalStateException("Not yet constructed?");
		
		//Memory is a big issue here - we don't want train the LM too soon, i.e. until
		// the old LM can first be GC'd, as that'd need memory for both simultaneously...

		//(1) So, first we make the old NCMgr & LM unreachable (the event handler has only weakrefs)
		CControlManager cont;
		if (m_pNCManager!=null) {
			//since the AlphabetManager is about to be deleted, better write out anything unsaved...
			m_pNCManager.getAlphabetManager().WriteTrainFileFull(this);
			cont = m_pNCManager.getControlManager();
		} else 
			cont = makeControlManager();
		
		//(2)Then we construct a new NCMgr and (untrained) LM...
		
		//(2a) Get the alphabet...TODO: if the alphabet we ask for doesn't exist,
		// We might get a different/fallback one instead. Should we update the
		// parameter value to reflect this? ATM I'm thinking not, the user's
		// request stands?
		CAlphIO.AlphInfo cAlphabet = m_AlphIO.GetInfo(GetStringParameter(Esp_parameters.SP_ALPHABET_ID));
		
		//(2b) LanguageModel
		CLanguageModel<?> lm=null;
		WeakReference<CLanguageModel<?>> ref = m_LMcache.get(cAlphabet);
		if (ref!=null) {lm = ref.get(); if (lm==null) m_LMcache.remove(cAlphabet);}
		boolean bTrain;
		if (lm==null) {
			bTrain=true;
			switch ((int)GetLongParameter(Elp_parameters.LP_LANGUAGE_MODEL_ID)) {
			/* CSFS: Commented out the other language models for the time being as they are not
			 * implemented yet.
			 */
			default:
				// If there is a bogus value for the language model ID, we'll default
				// to our trusty old PPM language model.
			case 0:
				SetBoolParameter(Ebp_parameters.BP_LM_REMOTE, false);
				lm= new CPPMLanguageModel(this, cAlphabet);
				break;
			/* case 2:
				lm = new CWordLanguageModel(m_pEventHandler, m_pSettingsStore, alphabet);
				break;
			case 3:
				lm = new CMixtureLanguageModel(m_pEventHandler, m_pSettingsStore, alphabet);
				break;  
				#ifdef JAPANESE
			case 4:
				lm = new CJapaneseLanguageModel(m_pEventHandler, m_pSettingsStore, alphabet);
				break;
				#endif
			case 5:
				throw new UnsupportedOperationException("(ACL) Remote LM currently unimplemented");
				//lm = new CRemotePPM(m_EventHandler, m_SettingsStore, alphabet);
				//SetBoolParameter(Ebp_parameters.BP_LM_REMOTE, true);
				//break;
			*/
			}
		} else
			bTrain=false;
		
		m_pNCManager = new CNodeCreationManager(this, CAlphabetManager.makeAlphMgr(this,lm), cont);
		if (m_ColourIO.getByName(GetStringParameter(Esp_parameters.SP_COLOUR_ID))==null)
			ChangeColours(); //we must have been using the alphabet palette, which may have changed
		
		//Then, we rebuild the tree, so that any old nodes (referring to the old LM) are gone...
		forceRebuild();
		
		System.gc(); //the old LM should now be collectable, so just a hint...
		
		//At last we (hopefully) have enough memory to train the new LM...
		//TODO, can we train in the background, on another thread?
		if (bTrain) {
			//Put it in cache pre-emptively: we are going to train it! :)
			// If train(AlphabetManager, ProgressNotifier) is aborted, that will remove from map.
			m_LMcache.put(cAlphabet,new WeakReference<CLanguageModel<?>>(lm));
			train(m_pNCManager.getAlphabetManager());
		}
		
		//Finally we rebuild the tree _again_ :-(, so as to get probabilities from the trained LM...
		forceRebuild();
	}
	
	private CControlManager makeControlManager() {
		List<ControlAction> actions = getControlActions();
		return actions.isEmpty() ? null : new CControlManager(this, this, m_DasherModel, actions);	
	}
	
	/**
	 *  Forces the tree of nodes to be rebuilt around the current offset
	 *  (will reposition at an "appropriate" location, as per {@link CDasherModel#SetNode(CDasherNode)}).
	 *  Uses the same NCManager, but ensures probabilities are refreshed.
	 */
	private void forceRebuild() {
		if (m_pNCManager!=null) m_DasherModel.SetNode(m_pNCManager.getAlphabetManager().GetRoot(getDocument(), m_DasherModel.GetOffset(), true));
	}
	
	/*package*/ CDasherNode getLastOutputNode() {
		return m_DasherModel.getLastOutputNode();
	}
	
	/**
	 * Creates an input device by calling GetModuleByName on the parameter
	 * SP_INPUT_DEVICE. In the event that this does not correspond
	 * to a module known by the Module Manager, m_Input will
	 * be set to null.
	 * <p>
	 * If there is an existing input device, it will be Unref'd and
	 * Deactivated first.
	 * <p>
	 * In the event that a non-null DasherInput class is created,
	 * its Ref and Activate methods will be called immediately.
	 * 
	 * @see CDasherInput
	 * @see CModuleManager
	 *
	 */	
	private void CreateInput() {
		
		// FIXME - this shouldn't be the model used here - we should just change a parameter and work from the appropriate listener
		
		if(m_Input != null) {
			m_Input.Deactivate();
		}
		
		m_Input = GetModuleByName(CDasherInput.class, GetStringParameter(Esp_parameters.SP_INPUT_DEVICE));
		if (m_Input==null) m_Input = m_DefaultInput;
		
		if(m_Input != null) {
			m_Input.Activate();
		}
	}
	
	private final List<Runnable> endOfFrameTasks = new ArrayList<Runnable>();
	
	/** Whether we have been requested to totally redraw the nodes at the next time step
	 * (i.e. even if the model/filter doesn't move).
	 */
	private boolean m_bForceRedrawNodes;
	
	private boolean m_bLastPaused=true;
	
	/**
	 * Encapsulates the entire process of drawing a
	 * new frame of the Dasher world.
	 * <p>
	 * We invoke our input filter's Timer method, which determines
	 * in what way the Model should be updated, if at all; render the
	 * model to the View (if necessary), potentially expanding/collapsing
	 * nodes; and decorate the view according to the input filter. Then
	 * (if necessary) we tell the Screen to display the newly updated world.
	 * <p>
	 * Method will return without any action if any of the three
	 * lock variables are true.
	 * 
	 * @param iTime Current system time as a UNIX time stamp.
	 */
	public void NewFrame(long iTime) {
		// Fail if Dasher is locked...
		if(m_bShutdownLock || m_bConnectLock) return;
		
		//...or we have no graphics...
		if (m_DasherView == null || m_DasherScreen==null) return;
		
		String msg = m_sLockMsg;
		if(msg!=null) {
			final int w = m_DasherScreen.GetWidth(), h=m_DasherScreen.GetHeight();
			m_DasherScreen.DrawRectangle(0, 0, w, h, 0, 0, 0); //fill in colour 0 = white
			CDasherView.Point p = m_DasherScreen.TextSize(msg, 14);
			m_DasherScreen.DrawString(msg, (m_DasherScreen.GetWidth()-p.x)/2, (m_DasherScreen.GetHeight()-p.y)/2, 14);
			return;
		}
		
		//ok, we want to render some nodes...if there are any...
		if (m_DasherModel == null) throw new IllegalStateException("Not yet constructed?");
		
		if (m_InputFilter!=null) m_InputFilter.Timer(iTime, m_DasherView, m_Input, m_DasherModel); 
					
		/*Logging code. TODO: capture int iNumDeleted / Vector<CSymbolProb>
		 * from information passed to outputText/deleteText, then:
		 *    if (iNumDeleted > 0)
		 *        m_UserLog.DeleteSymbols(iNumDeleted);
		 *    if (vAdded.size() > 0)
		 *        m_UserLog.AddSymbols(vAdded);
		 */
		final boolean bMoved = m_DasherModel.nextScheduledStep(iTime);
		if (bMoved) {
			if (m_bLastPaused) {onUnpause(); m_bLastPaused=false;}
		} else if (!m_bLastPaused) {onPause(); m_bLastPaused=true;}
		
		boolean bRedraw = false; //did nodes change (move, expand, collapse)?
		renderModel: {
			if (m_MarkerScreen!=null) {
				if (bMoved || m_bForceRedrawNodes)
					m_MarkerScreen.SendMarker(0);
				else break renderModel;
			}
			m_bForceRedrawNodes=false;
			m_DasherModel.CountFrame(iTime);
			bRedraw = m_DasherModel.RenderToView(m_DasherView) || bMoved;
		}
		
		if (m_MarkerScreen!=null)
			m_MarkerScreen.SendMarker(1);
		
		//if we moved, expanded/collapsed anything, or decorations changed...
		if ((m_InputFilter!=null && m_InputFilter.DecorateView(m_DasherView, m_Input))
				|| bRedraw) {
			//then need to blit to screen!
			if (m_MarkerScreen!=null) m_MarkerScreen.Display();
			// and also make sure we render another frame after this (necessary
			// if we expanded/collapsed; policy otherwise). That will include
			// rerendering the nodes, iff we expanded/collapsed (necessary)
			// or moved (policy).
			Redraw(bRedraw);
		}
		
		for (int i=0; i<endOfFrameTasks.size(); i++)
			endOfFrameTasks.get(i).run();
		endOfFrameTasks.clear();
	}
	
	protected void onUnpause() {
		if (m_UserLog != null)
			m_UserLog.StartWriting();
		m_DasherModel.ResetFramecount();
		Redraw(true); //kick the render thread
	}
	
	protected void onPause() {
		// Request a full redraw at the next time step.
		Redraw(true);

		if (m_UserLog != null) //Hmmm. Really? between zooms of click mode?
			m_UserLog.StopWriting((float) GetNats());
	}
	
	public void doAtFrameEnd(Runnable r) {endOfFrameTasks.add(r);}
	
	/**
	 * <p>Called to schedule a redraw of the screen. Architectures in which
	 * drawing must be initiated from the outside (e.g. Swing/AWT: app calls
	 * repaint(), and eventually Swings calls back to paint), should override
	 * to additionally request a repaint from the external framework.
	 * Architectures with e.g. a regular 20ms repaint, need do nothing (the
	 * existing method will cause NewFrame to repaint the nodes, or not, as
	 * needed.)</p>
	 * <p>Any overriding method, should make sure to call through to <code>super</code>.</p>
	 * @param bChanged True if the nodes must be repainted in the next call to NewFrame.
	 */
	public void Redraw(boolean bChanged) {
		if (bChanged) m_bForceRedrawNodes=true;
	}
	
	/**
	 * Changes the alphabet in use to that described by SP_ALPHABET_ID.
	 * <p>
	 * Writes the training file to disk using WriteTrainFileFull,
	 * and then runs CreateDasherModel, deleting an exsiting Model
	 * if it exists.
	 * <p>
	 * In the event that SP_ALPHABET_ID is empty when this function
	 * is called, it is set to the default suggested by m_AlphIO.
	 */
	public void ChangeAlphabet() {
		
		if(GetStringParameter(Esp_parameters.SP_ALPHABET_ID) == "") {
			SetStringParameter(Esp_parameters.SP_ALPHABET_ID, m_AlphIO.GetDefault());
			// This will result in ChangeAlphabet() being called again, so
			// exit from the first recursion
			return;
		}
		
		// Send a lock event
		
		if (m_pNCManager!=null) m_pNCManager.getAlphabetManager().WriteTrainFileFull(this);
		
		// Lock Dasher to prevent changes from happening while we're training.
		
		CreateNCManager();
		if (m_DasherView!=null) m_DasherView.setOrientation(computeOrientation());
	}
	
	private Opts.ScreenOrientations computeOrientation() {
		Opts.ScreenOrientations spec = Opts.orientationFromString(GetStringParameter(Esp_parameters.SP_ORIENTATION));
		return (spec==null) ? m_pNCManager.getAlphabetManager().m_Alphabet.GetOrientation() : spec;
	}
	
	/**
	 * Changes the colour scheme to that described by SP_COLOUR_ID.
	 * <p>
	 * If m_ColourIO is null at the time, this method will return
	 * without performing any action.
	 * <p>
	 * Specifically, this method retrieves the colour scheme named
	 * by SP_COLOUR_ID from m_ColourIO, and creates a new CustomColours
	 * wrapping it, finally setting m_Colours to point to the new
	 * scheme. Finally, if successful, the screen is informed of the
	 * new scheme by calling its SetColourScheme method.
	 */
	private void ChangeColours() {
		if(m_ColourIO == null)
			throw new IllegalStateException("Not yet constructed?");
		
		CColourIO.ColourInfo info = m_ColourIO.getByName(GetStringParameter(Esp_parameters.SP_COLOUR_ID));
		if (info==null) {
			if (m_pNCManager!=null) info  = m_ColourIO.getByName(m_pNCManager.getDefaultColourScheme());
			if (info == null)
				info = m_ColourIO.getDefault();
		}
		m_Colours = new CCustomColours(info);
		
		if(m_DasherScreen != null) {
			m_DasherScreen.SetColourScheme(m_Colours);
		}
	}
	
	/**
	 * Changes the Screen to which we should send drawing instructions.
	 * <p>
	 * If a view already exists, it is notified of the new screen.
	 * <p>
	 * If no view exists, one is created by a call to ChangeView.
	 * 
	 * @param NewScreen New screen
	 */
	public void ChangeScreen(CDasherScreen NewScreen) {
		m_DasherScreen = NewScreen;
		m_MarkerScreen = (NewScreen instanceof CMarkerScreen) ? (CMarkerScreen)NewScreen : null;
		if (m_Colours!=null) m_DasherScreen.SetColourScheme(m_Colours);
		
		if(m_DasherView == null)
			m_DasherView = new CDasherViewSquare(this, m_DasherScreen, computeOrientation());
		else
			m_DasherView.ChangeScreen(m_DasherScreen);
		
		Redraw(true);
	}
	
	/** Called to train the model. This method creates and broadcasts
	 * a CLockEvent, then calls {@link #train(String, CLockEvent)},
	 * then clears the event's {@link CLockEvent#m_bLock} and broadcasts it again.
	 * 
	 * @param T alphabet-provided name of training file, e.g. "training_english_GB.txt"
	 */
	protected void train(CAlphabetManager<?> mgr) {
		// Train the new language model
		Lock("Training Dasher", 0); 
		train(mgr,new ProgressNotifier() {
			public void notifyProgress(int iPercent) {
				Lock("Training Dasher", iPercent);
			}
		});
		Lock("Training Dasher", -1);
	}
	
	/** Interface by which an object may be notified of training progress (as a %age) */
	public static interface ProgressNotifier {
		/** Notify of current progress. May also request training be aborted.
		 * @param percent Current %age progress; should be monotonic; 100% does not imply completion (but nearly!)
		 * @throws AsynchronousCloseException if training should be aborted (note if this happens once, any subsequent calls should do the same) */
		void notifyProgress(int percent) throws AsynchronousCloseException;
	}
	/**
	 * Called to train the model with all available files of the specified name
	 * (obtained via {@link #GetStreams(String, Collection)}.
	 * @param T alphabet-provided name of training file, e.g. "training_english_GB.txt"
	 * @param prog ProgressNotifier which will be notified of %progress
	 */
	protected void train(CAlphabetManager<?> mgr,ProgressNotifier prog) {
		int iTotalBytes=0;
		List<InputStream> streams=new ArrayList<InputStream>();
		GetStreams(mgr.m_Alphabet.GetTrainingFile(),streams);
		for (InputStream in : streams)
			try {
				iTotalBytes+=in.available();
			} catch (IOException e) {
				//Hmmm. Ignore? Or...how about:
				iTotalBytes = Integer.MAX_VALUE; //i.e. we won't get progress - because we can't...
				break;
			}
			
		int iRead = 0;
		for (InputStream in : streams) {
			try {
				iRead = mgr.TrainStream(in, iTotalBytes, iRead, prog);
			} catch (AsynchronousCloseException e) {
				//thrown to indicate training aborted. In that case we don't
				// want to cache the LM.
				m_LMcache.remove(mgr.m_Alphabet);
				break;
			} catch (IOException e) {
				Message("Error "+e+" in training - rest of text skipped", 1); // 1 = severity
			}
		}
	}
	
	/**
	 * Retrieves a list of available font sizes. This class
	 * returns a generic reasonably sensible list 
	 * (11, 14, 20, 22, 28, 40, 44, 56, 80) but should be
	 * overridden by the implementing class if a better answer
	 * can be retrieved.
	 * 
	 * @param FontSizes Collection to be filled with available sizes
	 */
	public void GetFontSizes(Collection<Integer> FontSizes) {
		FontSizes.add(20);
		FontSizes.add(14);
		FontSizes.add(11);
		FontSizes.add(40);
		FontSizes.add(28);
		FontSizes.add(22);
		FontSizes.add(80);
		FontSizes.add(56);
		FontSizes.add(44);
	}
	
	/**
	 * Stub. Ought to return the current characters per minute,
	 * but this is not yet implemented.
	 *  
	 * @return 0
	 */
	public double GetCurCPM() {
		//
		return 0;
	}
	
	/**
	 * Deferred to CDasherModel
	 * 
	 * @see CDasherModel
	 */
	public double GetNats() {
		if(m_DasherModel != null)
			return m_DasherModel.GetNats();
			else
				return 0.0;
	}
	
	/**
	 * Positions the model at the specified offset, retrieving fresh context at that
	 * offset and rebuilding the Dashernode tree if necessary. Includes pausing Dasher
	 * if the tree is rebuilt.
	 * @param bForce If true, model is rebuilt even if it was already at the right position.
	 * (appropriate if the text being edited may have changed regardless of cursor position -
	 * e.g. if moving from one editbox to another.)
	 */
	public void setOffset(int iOffset, boolean bForce) {
		if (m_DasherModel==null) throw new IllegalStateException("Not yet constructed?");
		if (iOffset == m_DasherModel.GetOffset() && !bForce) return;
		m_InputFilter.pause();
		
		m_DasherModel.SetNode(m_pNCManager.getAlphabetManager().GetRoot(getDocument(), iOffset, true));
		
		Redraw(true);
		
	}
	
	/**
	 * Gets a reference to m_UserLog.
	 * 
	 * @return m_UserLog
	 */
	public CUserLog GetUserLogPtr() {
		return m_UserLog;
	}
	
	/**
	 * Signals a key press to our input filter. This should be invoked
	 * by an implementation whenever a key is pressed.
	 * <p>
	 * Key presses signalled in this way are ignored if we have
	 * no input filter, or if BP_TRAINING is true.
	 * <p>
	 * Currently assigned key IDs:
	 * <p><ul>
	 * <li>0: Start/stop (keyboard)
	 * <li>1: Move east (for button modes)
	 * <li>2: Move north
	 * <li>3: Move west
	 * <li>4: Move south
	 * <li>100: Mouse click
	 * </ul>
	 * <p>
	 * The actual physical keys assigned to these functions
	 * are down to the implementation, and may be user-definable.
	 * 
	 * @param iTime System time as a UNIX timestamp at which the key was pressed
	 * @param iId Identifier of the pressed key
	 */
	public void KeyDown(long iTime, int iId) {
		if(m_InputFilter != null && m_sLockMsg==null) {
			m_InputFilter.KeyDown(iTime, iId, m_DasherView, m_Input, m_DasherModel);
		}
	}
	
	/**
	 * Signals a key press to our input filter. This should be invoked
	 * by an implementation whenever a key is released.
	 * <p>
	 * Key presses signalled in this way are ignored if we have
	 * no input filter, or if BP_TRAINING is true.
	 * <p>
	 * Currently assigned key IDs:
	 * <p><ul>
	 * <li>0: Start/stop
	 * <li>1: Move east (for button modes)
	 * <li>2: Move north
	 * <li>3: Move west
	 * <li>4: Move south
	 * <li>100: Left mouse click (or equivalent)
	 * </ul>
	 * <p>
	 * The actual physical keys assigned to these functions
	 * are down to the implementation, and may be user-definable.
	 * 
	 * @param iTime System time as a UNIX timestamp at which the key was pressed
	 * @param iId Identifier of the pressed key
	 */
	public void KeyUp(long iTime, int iId) {
		if(m_InputFilter != null && m_sLockMsg==null) {
			m_InputFilter.KeyUp(iTime, iId, m_DasherView, m_Input, m_DasherModel);
		}
	}
	
	/**
	 * Creates m_InputFilter by retrieving the module named
	 * in SP_INPUT_FILTER.
	 * <p>
	 * If this is successful and an input filter is created,
	 * it will be Activated immediately.
	 * <p>
	 * If unsuccessful, m_InputFilter will be set to null.
	 * <p>
	 * If there is an existing filter, it is Deactivated first.
	 */
	private void CreateInputFilter() {
		m_InputFilter = GetModuleByName(CInputFilter.class, GetStringParameter(Esp_parameters.SP_INPUT_FILTER));
		if (m_InputFilter == null) m_InputFilter = m_DefaultInputFilter;
		if(m_InputFilter != null) {
			m_InputFilter.Activate();
		}
	}
	
	/**
	 * Deferred to m_oModuleManager
	 * 
	 * @see CModuleManager
	 */
	public void RegisterModule(CDasherModule mod) {
		m_oModuleManager.RegisterModule(mod);
	}
	
	/**
	 * Deferred to m_oModuleManager
	 * 
	 * @see CModuleManager
	 */
	public <T extends CDasherModule> T GetModuleByName(Class<T> clazz, String strName) {
		return m_oModuleManager.GetModuleByName(clazz, strName);
	}
	
	/**
	 * Manually registers a number of input filters.
	 * <p>
	 * At present this registers only Normal Control and Click Mode, and sets
	 * Normal Control as the default.
	 * As and when others are implemented, will register these also.
	 * Subclasses can & should override if they want anything different
	 * or extra.
	 * <p>
	 * The same input filter may be registered repeatedly under
	 * a variety of different names if desired (so long as the
	 * filter's constructor permits a user-defined name).
	 * <p>
	 * This is commonly used to produce a number of different
	 * button modes which use the same filter class.
	 *
	 */
	protected void CreateModules() {
		RegisterModule(setDefaultInputFilter(new CDefaultFilter(this, this, "Normal Control")));
		RegisterModule(new COneDimensionalFilter(this, this, "One Dimensional Mode"));
		RegisterModule(new CStylusFilter(this, this));
		
		RegisterModule(new CClickFilter(this, this));
		RegisterModule(new TwoButtonDynamicFilter(this, this));
		RegisterModule(new OneButtonDynamicFilter(this, this));
		
		RegisterModule(new CCompassMode(this, this));
		RegisterModule(new CMenuMode(this, this, "Menu Mode"));
		RegisterModule(new CButtonMode(this, this, "Direct Mode"));

		//Not yet implemented:
		//RegisterModule(new CDasherButtons(this, m_SettingsStore, this, 3, 3, false,12, "Alternating Direct Mode"));
	}
	
	
	public void GetPermittedValues(Esp_parameters param, Collection<String> vList) {
		if (param == Esp_parameters.SP_ALPHABET_ID)
			m_AlphIO.GetAlphabets(vList);
		else if (param == Esp_parameters.SP_COLOUR_ID)
			m_ColourIO.GetColours(vList);
		else {
			List<CDasherModule> mods=new ArrayList<CDasherModule>();
			if(param == Esp_parameters.SP_INPUT_FILTER)
				m_oModuleManager.ListModules(CInputFilter.class, mods);
			else if (param==Esp_parameters.SP_INPUT_DEVICE)
				m_oModuleManager.ListModules(CDasherInput.class, mods);
			else
				return;
			for (CDasherModule m : mods) vList.add(m.getName());
		}
	}
	
	/**
	 * Engages the shutdown lock (m_bShutdownLock).
	 */
	public void StartShutdown() {
		m_bShutdownLock = true;
		if (m_DasherModel!=null) m_DasherModel.shutdown();
	}
	
	/**
	 * Append user-written text to a training file - which should be found
	 * if the same filename is passed to {@link #GetStreams(String, Collection)}
	 * Default implementation does nothing; subclasses which are able
	 * to perform file I/O, should override to do so. (e.g. applet cannot!).
	 * 
	 * @param trainFileName name of training file, e.g. "training_english_GB.txt"
	 * @param strNewText user-written text to append to file
	 */
	public void WriteTrainFile(String trainFileName, String strNewText) {
		/* Empty method: at the platform-independent level
		 * we can't know how to write the file.
		 */
	}
	
	/**
	 * Handle to the default filter to use, in the case that the
	 * SP_INPUT_FILTER setting does not identify an alternative.
	 */
	private CInputFilter m_DefaultInputFilter;
	
	/**
	 * Sets the default filter, for when SP_INPUT_FILTER does not identify one;
	 * most likely, subclasses should call this in {@link #CreateModules()}.
	 * @param defaultInputFilter the filter to use if no other can be identified.
	 * @return the filter passed in
	 */
	public CInputFilter setDefaultInputFilter(CInputFilter defaultInputFilter) {
		return m_DefaultInputFilter = defaultInputFilter;
	}
	
	/**
	 * Handle to the default input device to use, in the case that the
	 * SP_INPUT_DEVICE setting does not identify an alternative.
	 */
	private CDasherInput m_DefaultInput;
	
	/**
	 * Sets the default input device, for when SP_INPUT_DEVICE does not identify one;
	 * most likely, subclasses should call this in {@link #CreateModules()}.
	 * @param defaultInput the device to use if no other can be identified
	 * @return the device passed in
	 */
	public CDasherInput setDefaultInput(CDasherInput defaultInput) {
		return m_DefaultInput = defaultInput;	
	}

	public List<CControlManager.ControlAction> getControlActions() {
		if (!GetBoolParameter(Ebp_parameters.BP_CONTROL_MODE)) return Collections.emptyList();
		List<ControlAction> acts = new ArrayList<ControlAction>();
		if (m_InputFilter!=null && m_InputFilter.supportsPause()) acts.add(CControlManager.PAUSE_ACTION);
		if (GetBoolParameter(Ebp_parameters.BP_CONTROL_MODE_HAS_MOVE)) acts.add(CControlManager.MOVE);
		if (GetBoolParameter(Ebp_parameters.BP_CONTROL_MODE_ALPH_SWITCH)) acts.add(new CControlManager.AlphSwitcher(this));
		if (GetBoolParameter(Ebp_parameters.BP_CONTROL_MODE_HAS_SPEED)) acts.add(CControlManager.SPEED_CHANGE);
		return acts;
	}
	
	public CInputFilter GetActiveInputFilter() {return m_InputFilter;}

}
