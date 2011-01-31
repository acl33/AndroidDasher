package dasher;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import dasher.CControlManager.ControlAction;

public class CNodeCreationManager extends CDasherComponent {
	
	/** package-access because CAlphabetManager indirects through us to call {@link CDasherInterfaceBase#getContext(int) */
	final CDasherInterfaceBase m_DasherInterface;
	
	/**
	 * Ugh. We do enough stuff with the alphabet here that it's a pain to pull
	 * it out of the AlphabetManager every time. And lots of stuff in this class
	 * is alphabet-specific too (e.g. uniformity, according to #symbols)...
	 * TODO, move more into CAlphabetManager? (but e.g. orientation, seems out of place)
	 */
	private final CAlphIO.AlphInfo m_cAlphabet;
	/**
	 * Our alphabet manager, for functions which require knowledge
	 * of an Alphabet.
	 */
	protected final CAlphabetManager<?> m_AlphabetManager;
	protected CControlManager m_ControlManager;
	
/**
	 * Amount to add to all symbols' probabilities, EXCEPT control mode symbol,
	 * in order to avoid a zero probability.
	 */
	protected int uniformAdd;
	
	/**
	 * Probability assigned to the Control Node
	 */
	protected long controlSpace;
	
	/**
	 * Normalization factor as a fraction of which the Language Model should compute
	 * symbol probabilities, prior to adjusting them with adjustProbs.
	 */
	protected long nonUniformNorm;
	
	// Both of these are to save repeated calculations of the same answers. Their
	// values are calculated when the model is created and are recalculated
	// in response to any dependent parameter changes.

	/**
	 * Create a new NCManager. This is where the user's chosen alphabet is identified.
	 * Includes:
	 * <ul>
	 * <li>Setting global parameters which reflect information about this alphabet
	 * (eg. SP_DEFAULT_COLOUR_ID)
	 * <li> Creating the LanguageModel described by LP_LANGUAGE_MODEL_ID
	 * <li> Constructing an {@link AlphabetManager} using the previous
	 * </ul>
	 * 
	 * <p> Note this does not train the newly created language model;
-    * this must be performed from outside, typically by the Interface.
	 * @param EventHandler Interface for e.g. Event handling
	 * @param SettingsStore Settings repository
	 */
	public CNodeCreationManager(CDasherInterfaceBase intf, CSettingsStore SettingsStore) {
		super(intf, SettingsStore);
		this.m_DasherInterface=intf;
		//Convert the full alphabet to a symbolic representation for use in the language model
		
		// -- put all this in a separate method
		// TODO: Think about having 'prefered' values here, which get
		// retrieved by DasherInterfaceBase and used to set parameters
		
		// TODO: We might get a different alphabet to the one we asked for -
		// if this is the case then the parameter value should be updated,
		// but not in such a way that it causes everything to be rebuilt.
		
		m_cAlphabet = intf.GetInfo(GetStringParameter(Esp_parameters.SP_ALPHABET_ID));
		
		SetStringParameter(Esp_parameters.SP_DEFAULT_COLOUR_ID, m_cAlphabet.GetPalette());
		
		if(GetLongParameter(Elp_parameters.LP_ORIENTATION) == Opts.AlphabetDefault)
			SetLongParameter(Elp_parameters.LP_REAL_ORIENTATION, m_cAlphabet.GetOrientation());
		
		// Create an appropriate language model;
		
		switch ((int)GetLongParameter(Elp_parameters.LP_LANGUAGE_MODEL_ID)) {
		default:
			// If there is a bogus value for the language model ID, we'll default
			// to our trusty old PPM language model.
		case 0:
			
			m_AlphabetManager = /*ACL (langMod.isRemote())
		        ? new CRemoteAlphabetManager( this, langMod)
		        :*/ new CAlphabetManager<CPPMLanguageModel.CPPMnode>( this, new CPPMLanguageModel(m_EventHandler, m_SettingsStore, m_cAlphabet));
		
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
			throw new UnsupportedOperationException("(ACL) Remote LM currently unimplemented");
			//langMod = new CRemotePPM(m_EventHandler, m_SettingsStore, alphabet);
			//SetBoolParameter(Ebp_parameters.BP_LM_REMOTE, true);
		
			//break;
		/* CSFS: Commented out the other language models for the time being as they are not
		 * implemented yet.
		 */
		}
		
		computeNormFactorNoCheck();
	}
	
	/**
	 * Gets a probability distribution for a context and language model. Detailed
	 * predictions of characters are performed by {@link CLanguageModel#GetProbs(Object, long[], long)},
	 * but here we handle<UL>
	 * <LI>Object pooling of long[] arrays (useful on Android),
	 * <LI>Uniformity / smoothing - all symbol probabilities by the value of {@link #uniformAdd}
	 * <LI>Control mode - if control mode is on, we append an extra {@link #controlSpace} for the control node.
	 * 
	 * @param model LanguageModel to use for symbol probabilities
	 * @param context context to provide to language model
	 * @return array of (non-cumulative) probabilities, with first element zero,
	 *  
	 */
	public <C> long[] GetProbs(CLanguageModel<C> model, C context) {
		long[] probs;
		if (freeArrayList.isEmpty())
			probs = new long[m_cAlphabet.GetNumberSymbols()+(controlSpace==0 ? 1 : 2)];
		else {
			probs = freeArrayList.remove(freeArrayList.size()-1);
			for (int i=0; i<probs.length; i++) probs[i]=0;
		}
		model.GetProbs(context, probs, nonUniformNorm);
		
		//element 0 is just a 0, to make computing upper/lower bounds easier...
		for(int k = m_cAlphabet.GetNumberSymbols(); k >0; --k) probs[k] += uniformAdd;

		if (controlSpace!=0) probs[probs.length-1]=controlSpace;
		
		return probs;
	}
	
	private final List<long[]> freeArrayList=new ArrayList<long[]>();
	
	public void recycleProbArray(long[] ar) {
		//Don't pool arrays that are too short. Not sure whether this'll
		// actually happen, depends on timing of rebuilding, init'ing
		// the control manager, etc., when turning control mode on/off,
		// so programming defensively.
		if (ar.length >= m_cAlphabet.GetNumberSymbols()+(controlSpace==0 ? 1 : 2))
			freeArrayList.add(ar);
	}
	
	public void addExtraNodes(CDasherNode pParent, long[] probInfo) {
		//if (probInfo[probInfo.length-1]!=GetLongParameter(Elp_parameters.LP_NORMALIZATION)) throw new AssertionError();
		if (controlSpace==0) {
			//if (pParent.ChildAtIndex(pParent.ChildCount()-1).Hbnd()!=probInfo[probInfo.length-1]) throw new AssertionError();
			//if (probInfo.length != m_cAlphabet.GetNumberSymbols()+1) throw new AssertionError();
			return;
		}
		//if (pParent.ChildAtIndex(pParent.ChildCount()-1).Hbnd()!=probInfo[probInfo.length-2]) throw new AssertionError();
		//if (probInfo.length != m_cAlphabet.GetNumberSymbols()+2) throw new AssertionError();
		//remaining space from penultimate to last elements of probInfo is for control node.
		//control nodes have same offset as parent, not one more, as they do not enter a symbol themselves.
		m_ControlManager.GetRoot(pParent, pParent.getOffset(), probInfo[probInfo.length-2], probInfo[probInfo.length-1]);
	}
	
	public void computeNormFactor() {
		long oldSpace = controlSpace, oldNorm = nonUniformNorm;
		computeNormFactorNoCheck();
		if (nonUniformNorm!=oldNorm || controlSpace!=oldSpace)
			m_DasherInterface.forceRebuild();
	}
	
	private void computeNormFactorNoCheck() {
//		 Total number of symbols in alphabet (i.e. to which we add uniformity)
		int iSymbols = m_cAlphabet.GetNumberSymbols();
		
		long iNorm = GetLongParameter(Elp_parameters.LP_NORMALIZATION);
		iNorm-= controlSpace = initControlManager(iNorm);
		
		uniformAdd = (int)((iNorm * GetLongParameter(Elp_parameters.LP_UNIFORM)) / 1000) / iSymbols; 
		nonUniformNorm = iNorm - iSymbols * uniformAdd;
		
	}
	
	protected long initControlManager(long iNorm) {
		mk: if (GetBoolParameter(Ebp_parameters.BP_CONTROL_MODE)) {
			final List<ControlAction> actions = m_DasherInterface.getControlActions();
			ControlAction c;
			if (actions.size()==1) c=actions.get(0);
			else if (actions.size()>1) c=new ControlAction() {
				public String desc() {return "Control";} //TODO internationalize
				public void happen(CDasherNode node) {} //do nothing
				public List<ControlAction> successors() {return actions;}
			};
			else break mk;
			if (m_ControlManager==null) {
				//control mode has just been turned on...
				//delete any long[]s not big enough for a control-node probability.
				for (int i=freeArrayList.size(); i-->0; )
					if (freeArrayList.get(i).length<m_cAlphabet.GetNumberSymbols()+2)
						freeArrayList.remove(i);
			}
				freeArrayList.clear(); //elements without space for a control-node probability.
			m_ControlManager = new CControlManager(m_DasherInterface, m_DasherInterface.getSettingsStore(), this, c);
			// TODO - sort out size of control node - for the timebeing I'll fix the control node at 5%
			return iNorm/20;
		}
		//no need to clear list - the existing elements can be used by GetProbs, but will not be recycled 
		m_ControlManager=null;
		return 0;
	}
	
	@Override public void HandleEvent(CEvent event) {
		if (event instanceof CParameterNotificationEvent) {
			CParameterNotificationEvent Evt = (CParameterNotificationEvent)event;
			if (Evt.m_iParameter == Ebp_parameters.BP_CONTROL_MODE
				|| Evt.m_iParameter == Elp_parameters.LP_UNIFORM) {
				computeNormFactor();
			}
			else if(Evt.m_iParameter ==  Elp_parameters.LP_ORIENTATION) {
				SetLongParameter(Elp_parameters.LP_REAL_ORIENTATION, 
						(GetLongParameter(Elp_parameters.LP_ORIENTATION) == Opts.AlphabetDefault)
							? m_cAlphabet.GetOrientation()
							: GetLongParameter(Elp_parameters.LP_ORIENTATION));
			} else {
				//parameter does not interest us
				return;
			}
			m_DasherInterface.Redraw(true);
		}
	}

	public CAlphabetManager<?> getAlphabetManager() {
		return m_AlphabetManager;
	}

	@Override public void UnregisterComponent() {
		m_AlphabetManager.m_LanguageModel.UnregisterComponent();
		super.UnregisterComponent();
	}
}
