package dasher;

import java.util.ArrayList;
import java.util.List;

import static dasher.CDasherModel.NORMALIZATION;

public class CNodeCreationManager extends CDasherComponent {
	
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

	/** Creates a new NCManager, (re)using the specified Alphabet and Control Managers
	 * (perhaps from the previous NCManager). Again, does not perform any training.
	 */
	public CNodeCreationManager(CDasherComponent creator, CAlphabetManager<?> mgr, CControlManager cont) {
		super(creator);
		this.m_cAlphabet = mgr.m_Alphabet;
		this.m_AlphabetManager = mgr;
		mgr.ChangeNCManager(this);
		this.m_ControlManager=cont;
		if (cont!=null) {
			//TODO fix size of control manager at 5%
			controlSpace = NORMALIZATION/20;
			cont.ChangeNCManager(this);
		} else
			controlSpace = 0;
				int iSymbols = m_cAlphabet.GetNumberSymbols();

		final long iNorm = NORMALIZATION-controlSpace;
		uniformAdd = (int)((iNorm * GetLongParameter(Elp_parameters.LP_UNIFORM)) / 1000) / iSymbols; 
		nonUniformNorm = iNorm - iSymbols * uniformAdd;
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
		CDasherNode temp = m_ControlManager.GetRoot(pParent.getOffset());
		temp.Reparent(pParent, probInfo[probInfo.length-2], probInfo[probInfo.length-1]);
	}
	
	public CAlphabetManager<?> getAlphabetManager() {
		return m_AlphabetManager;
	}
}
