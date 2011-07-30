package dasher;

import java.util.ArrayList;
import java.util.List;

import dasher.CControlManager.ControlAction;

import static dasher.CDasherModel.NORMALIZATION;

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
	protected final CControlManager m_ControlManager;
	
/**
	 * Amount to add to all symbols' probabilities, EXCEPT control mode symbol,
	 * in order to avoid a zero probability.
	 */
	protected final int uniformAdd;
	
	/**
	 * Probability assigned to the Control Node
	 */
	protected final long controlSpace;
	
	/**
	 * Normalization factor as a fraction of which the Language Model should compute
	 * symbol probabilities, prior to adjusting them with adjustProbs.
	 */
	protected final long nonUniformNorm;
	
	// Both of these are to save repeated calculations of the same answers. Their
	// values are calculated when the model is created and are recalculated
	// in response to any dependent parameter changes.

	/**
	 * Create a new NCManager, including a new AlphabetManager, Language Model
	 * and ControlManager, according to standard settings (BP_CONTROL_MODE,
	 * LP_LANGUAGE_MODEL_ID, SP_ALPHABET_ID).
	 * 
	 * <p> Note this does not train the newly created language model;
-    * this must be performed from outside, typically by the Interface.
	 */
	public CNodeCreationManager(CDasherComponent creator, CDasherInterfaceBase intf) {
		this(creator,intf,makeAlphMgr(intf));
	}
	
	/** Creates a new NCManager, (re)using the specified Alphabet and Control Managers
	 * (perhaps from the previous NCManager). Again, does not perform any training.
	 */
	public CNodeCreationManager(CDasherComponent creator, CDasherInterfaceBase intf, CAlphabetManager<?> mgr) {
		super(creator);
		this.m_DasherInterface=intf;
		this.m_cAlphabet = mgr.m_Alphabet;
		this.m_AlphabetManager = mgr;
		mgr.ChangeNCManager(this);
		//System.out.print("make Control Manager...");
		mk: {
			final List<ControlAction> actions = intf.getControlActions();
			ControlAction c;
			if (actions.size()==1) c=actions.get(0);
			else if (actions.size()>1) {
				c=new ControlAction() {
					public String desc() {return "Control";} //TODO internationalize
					public void happen(CDasherNode node) {} //do nothing
					public List<ControlAction> successors() {return actions;}
				};
			} else {
				//System.out.println("No control manager");
				m_ControlManager = null;
				controlSpace = 0;
				break mk;
			}
			//System.out.println("Have control manager");
			m_ControlManager = new CControlManager(this, intf, this, c);
			//TODO fix size of control manager at 5%
			controlSpace = NORMALIZATION/20;
			break mk;
		}
		//break mk arrives here
		int iSymbols = m_cAlphabet.GetNumberSymbols();

		final long iNorm = NORMALIZATION-controlSpace;
		uniformAdd = (int)((iNorm * GetLongParameter(Elp_parameters.LP_UNIFORM)) / 1000) / iSymbols; 
		nonUniformNorm = iNorm - iSymbols * uniformAdd;
	}
	
	private static CAlphabetManager<?> makeAlphMgr(CDasherInterfaceBase intf) {
		//Convert the full alphabet to a symbolic representation for use in the language model
		
		// -- put all this in a separate method
		// TODO: Think about having 'prefered' values here, which get
		// retrieved by DasherInterfaceBase and used to set parameters
		
		// TODO: We might get a different alphabet to the one we asked for -
		// if this is the case then the parameter value should be updated,
		// but not in such a way that it causes everything to be rebuilt.
		
		CAlphIO.AlphInfo cAlphabet = intf.GetInfo(intf.GetStringParameter(Esp_parameters.SP_ALPHABET_ID));
		
		// Create an appropriate language model;
		
		switch ((int)intf.GetLongParameter(Elp_parameters.LP_LANGUAGE_MODEL_ID)) {
		default:
			// If there is a bogus value for the language model ID, we'll default
			// to our trusty old PPM language model.
		case 0:
			intf.SetBoolParameter(Ebp_parameters.BP_LM_REMOTE, false);
			return /*ACL (langMod.isRemote())
		        ? new CRemoteAlphabetManager( this, langMod)
		        :*/ new CAlphabetManager<CPPMLanguageModel.CPPMnode>( new CPPMLanguageModel(intf, cAlphabet));
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
	}
	
	public String getDefaultColourScheme() {return m_cAlphabet.GetPalette();}
	
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
	
	public CAlphabetManager<?> getAlphabetManager() {
		return m_AlphabetManager;
	}
}
