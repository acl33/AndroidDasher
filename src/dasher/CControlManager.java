package dasher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dasher.CDasherModel.NORMALIZATION;

public class CControlManager extends CDasherComponent {
	private final CDasherInterfaceBase m_Interface;
	private CNodeCreationManager m_pNCMgr;
	private final CDasherModel model;
	private final ControlAction root;

	CControlManager(CDasherComponent creator, CDasherInterfaceBase iface, CDasherModel model, final List<ControlAction> actions) {
		super(creator);
		this.m_Interface=iface;
		this.model=model;
		this.root=(actions.size()==1) ? actions.get(0) :
			new FixedSuccessorsAction("Control",actions); //TODO internationalize
	}

	void ChangeNCManager(CNodeCreationManager pNCMgr) {m_pNCMgr = pNCMgr;}

	static int getColour(CDasherNode parent) {return parent==null ? 7 : (parent.ChildCount()%99)+11;}

	public CDasherNode GetRoot(CDasherNode parent) {
		return root.make(this, parent);
	}

	public static interface ControlAction {
		public void happen(CControlManager mgr, CDasherNode node);
		public void undo(CControlManager mgr, CDasherNode node);
		public void populate(CControlManager mgr, CDasherNode node);
		public int expectedNumSuccs(CDasherNode node);
		public CContNode make(CControlManager mgr, CDasherNode parent);
	}
	public static interface RebuildingAction extends ControlAction {
		public CDasherNode rebuild(CControlManager mgr, CDasherNode node);
	}

	public static abstract class ControlActionBase implements ControlAction {
		private final String desc;
		public ControlActionBase(String desc) {this.desc=desc;}
		public CContNode make(CControlManager mgr, CDasherNode parent) {
			return mgr.makeCont(this, parent.getOffset(), getColour(parent), desc);
		}
		/** Subclasses should override to make the node have an effect! (Default does nothing) */
		public void happen(CControlManager mgr, CDasherNode node) {}
		public void undo(CControlManager mgr, CDasherNode node) {}
		protected final void replace(CControlManager mgr, Document node, int offset) {
			mgr.model.ReplaceLastOutputNode(
					mgr.m_pNCMgr.getAlphabetManager().GetRoot(node, offset, true));
		}
		protected void replace(CControlManager mgr, CDasherNode node) {
			replace(mgr,node,node.getOffset());
		}
	}

	public static abstract class DelaySuccessorsAction extends ControlActionBase {
		public DelaySuccessorsAction(String desc) {super(desc);}
		public final void happen(CControlManager mgr, CDasherNode node) {
			int nOffset = applyGetIndex(mgr,node);
			replace(mgr, node, nOffset);
		}
		public final void populate(CControlManager mgr, CDasherNode node) {}
		public final int expectedNumSuccs(CDasherNode node) {return 0;}
		/** Should: <OL>
		 * <LI> Apply any action the node should have;
		 * <LI> Create children for the node
		 * <LI> Return the offset of the node
		 * </OL>
		 * After calling, a new alphabet root will be constructed at the returned
		 * offset, and the node's children transferred to that root, and finally
		 * the node will be deleted.
		 */
		protected abstract int applyGetIndex(CControlManager mgr, CDasherNode node);
	}
	private final List<CDasherNode> tempList = new ArrayList<CDasherNode>();
	
	/*package*/ void populate(CDasherNode node, List<ControlAction> actions) {
		tempList.clear();
		for (int i=0; i<actions.size(); i++) {
			CDasherNode temp = (actions.get(i)==null)
					? m_pNCMgr.getAlphabetManager().GetRoot(node, node.getOffset(), false)
					: actions.get(i).make(this,node);
			if (temp!=null) tempList.add(temp);
		}
		long boundary = 0;
		for (int i=0; i<tempList.size(); i++) {
			long next = (i+1) * NORMALIZATION / tempList.size();
			tempList.get(i).Reparent(node, boundary, next);
			boundary=next;
		}
	}
	
	public static class FixedSuccessorsAction extends ControlActionBase {
		private final List<ControlAction> succs;
		public FixedSuccessorsAction(String desc, List<ControlAction> succs) {super(desc); this.succs=succs;}
		public FixedSuccessorsAction(String desc, ControlAction... succs) {
			this(desc,Arrays.asList(succs));
		}
		public void populate(CControlManager mgr, CDasherNode node) {mgr.populate(node,succs);}
		public int expectedNumSuccs(CDasherNode node) {return succs.size();}
	}
	
	private class CContNode extends CDasherNode {
		
		private ControlAction act;

		protected CDasherInterfaceBase getIntf() {return m_Interface;}

		/*package*/ void initNode(int iOffset, int iColour, ControlAction act, String desc) {
			super.initNode(iOffset, iColour, desc);
			this.act=act;
		}

		@Override public float getViscosity() {
			return 0.5f;
		}

		@Override
		public int ExpectedNumChildren() {
			return act.expectedNumSuccs(this); 
		}

		@Override
		public void Output() {
			act.happen(CControlManager.this, this);
		}
		
		@Override
		public void Undo() {
			act.undo(CControlManager.this, this);
		}

		@Override
		public void PopulateChildren() {
			act.populate(CControlManager.this,this);
		}
		
		@Override public boolean shove() {return false;}

		@Override
		public CDasherNode RebuildParent() {
			if (act instanceof RebuildingAction)
				return ((RebuildingAction)act).rebuild(CControlManager.this, this);
			return rebuildAlphNode(getOffset(), this);
		}
		
		@Override
		public void DeleteNode() {
			super.DeleteNode();
			nodeCache.add(this);
			extraInfo.remove(this);
		}
	}
	/** Utility method, to build an appropriate alph-node parent (root) for a control
	 * node which has lost its parent (i.e. is having {@link CDasherNode#RebuildParent()}
	 * called on it). The supplied node will be spliced into the place of the
	 * usual control-node child of the alphabet root.
	 * @param offset Cursor position of alphabet root node to build
	 * @param controlChild control node to put in place of the usual control node
	 * @return
	 */
	protected CDasherNode rebuildAlphNode(int offset, CContNode controlChild) {
		//position as the control-node child of an alph root. Not ideal, but given
		// most CContNode's rebuild anyway...
		CDasherNode temp = m_pNCMgr.getAlphabetManager().GetRoot(m_Interface.getDocument(), offset, true);
		temp.PopulateChildren();
		CContNode replace = (CContNode)temp.ChildAtIndex(temp.ChildCount()-1);
		CDasherNode ret = m_pNCMgr.getAlphabetManager().GetRoot(m_Interface.getDocument(), offset, true);
		for (int i=0; i<temp.ChildCount()-1; i++)
			temp.ChildAtIndex(i).Reparent(ret, temp.Lbnd(), temp.Hbnd());
		controlChild.Reparent(ret, replace.Lbnd(), replace.Hbnd());
		return ret;
	}
	
	private final Map<CContNode,Object> extraInfo = new HashMap<CContNode, Object>();
	
	private final List<CContNode> nodeCache = new ArrayList<CContNode>();

	public CContNode makeCont(ControlAction act, int iOffset, int iColour, String desc) {
		CContNode node = (nodeCache.isEmpty()) ? new CContNode() : nodeCache.remove(nodeCache.size()-1);
		node.initNode(iOffset, iColour, act, desc);
		return node;
	}
	
	protected static class CommitAction extends FixedSuccessorsAction {
		CommitAction(ControlAction...actions) {super("",actions);}
		@Override public void happen(CControlManager mgr, CDasherNode node) {
			replace(mgr, node);
		}
	}
	private static final ControlAction COMMIT_ALPH = new CommitAction((ControlAction)null);
	
	private static abstract class MoveAction extends ControlActionBase implements RebuildingAction {
		MoveAction() {super(null);} //no title
		public void happen(CControlManager mgr, CDasherNode node) {
			mgr.m_Interface.getDocument().moveCursor(node.getOffset());
			if (mgr.GetBoolParameter(Ebp_parameters.BP_CONTROL_MODE_REBUILD)
					&& mgr.GetBoolParameter(Ebp_parameters.BP_MOVE_REBUILD_IMMED))
				replace(mgr,node);
		}
		public void undo(CControlManager mgr,CDasherNode node) {
			mgr.m_Interface.getDocument().moveCursor(node.Parent()==null ? getParentOffset(node) : node.Parent().getOffset());
		}
		public CDasherNode rebuild(CControlManager mgr, CDasherNode node) {
			return mgr.rebuildAlphNode(getParentOffset(node), (CContNode)node);
		}
		protected abstract int getParentOffset(CDasherNode node);
		
		public int expectedNumSuccs(CDasherNode node) {
			int num=1; //escape
			if (node.getOffset()>=0) num++;
			if (node.getCharAt(node.getOffset()+1)!=null) num++;
			return num;
		}
		
		public void populate(CControlManager mgr,CDasherNode node) {
			int backSz=0;
			CContNode back = BACK.make(mgr,node);
			if (back!=null) {//can move back
				backSz = (this==BACK) ?
						(Character.isJavaIdentifierPart(node.getCharAt(node.getOffset())) ? 4 : 2) : 1;
			}
			int fwdSz=0;
			CContNode fwd = FWD.make(mgr,node);
			if (fwd!=null) {
				fwdSz = (this==FWD) ?
						(Character.isJavaIdentifierPart(node.getCharAt(node.getOffset()+1)) ? 4 : 2) : 1;
			}
			int total=fwdSz+backSz+1, low=0;
			if (back!=null)
				back.Reparent(node, low, low+=(NORMALIZATION*backSz)/total);
			CDasherNode alph =
					(mgr.GetBoolParameter(Ebp_parameters.BP_CONTROL_MODE_REBUILD) && !mgr.GetBoolParameter(Ebp_parameters.BP_MOVE_REBUILD_IMMED))
					? COMMIT_ALPH.make(mgr, node) : mgr.m_pNCMgr.getAlphabetManager().GetRoot(node, node.getOffset(), false);
			alph.Reparent(node, low, low+=NORMALIZATION/total);
			if (fwd!=null)
				fwd.Reparent(node, low, NORMALIZATION);
		}
	}
	
	private static final MoveAction FWD = new MoveAction() {
		private final List<Integer> tempList = new ArrayList<Integer>();
		public CContNode make(CControlManager mgr, CDasherNode parent) {
			int nOffset = parent.getOffset()+1;
			final Character c =parent.getCharAt(nOffset);
			if (c==null) return null;
			Character c2;
			String nxChar = (Character.isHighSurrogate(c)
					&& (c2=parent.getCharAt(++nOffset))!=null
					&& Character.isLowSurrogate(c2))
				? new String(new char[] {c,c2}) : c.toString();
			tempList.clear();
			mgr.m_pNCMgr.getAlphabetManager().m_AlphabetMap.GetSymbols(tempList, nxChar);
			assert tempList.size()==1;
			String text = (tempList.get(0)==0) ? nxChar : mgr.m_pNCMgr.getAlphabetManager().m_Alphabet.GetDisplayText(tempList.get(0));
			//sb.append('\u20D5'); //combining clockwise arrow above, but not in Android
			return mgr.makeCont(this, nOffset, 11, ">"+text);
		}
		protected int getParentOffset(CDasherNode child) {
			int chLength = child.m_strDisplayText.length()-1;
			return child.getOffset()-chLength;
		}
	};
	private static final MoveAction BACK = new MoveAction() {
		private final List<Integer> tempList = new ArrayList<Integer>();
		public CContNode make(CControlManager mgr, CDasherNode parent) {
			int nOffset = parent.getOffset()-1;
			if (parent.getOffset()<0) return null;
			final Character c = parent.getCharAt(nOffset+1);
			if (c==null) return null;
			char c2;
			String nxChar = (Character.isLowSurrogate(c)
					&& nOffset>=0
					&& Character.isHighSurrogate(c2=parent.getCharAt(nOffset--)))
				? new String(new char[] {c2,c}) : c.toString();
			tempList.clear();
			mgr.m_pNCMgr.getAlphabetManager().m_AlphabetMap.GetSymbols(tempList, nxChar);
			assert tempList.size()==1;
			String text = (tempList.get(0)==0) ? nxChar : mgr.m_pNCMgr.getAlphabetManager().m_Alphabet.GetDisplayText(tempList.get(0));
			//sb.append('\u20D4'); //combining anticlockwise arrow above, but not in Android
			return mgr.makeCont(this, nOffset, 13, "<"+text);
		}
		protected int getParentOffset(CDasherNode child) {
			int chLength = child.m_strDisplayText.length()-1;
			return child.getOffset()+chLength;
		}
	};

	/** Just offers a choice between forwards and back - but have to do it the
	 * hard way, as (a) don't want to rebuild, nor have escape node; (b) might
	 * not want fwd/back/either according to node offset.
	 */
	public static final ControlAction MOVE = new FixedSuccessorsAction("\u21C6") {//2194, 21C4, 21D4

		public void populate(CControlManager mgr, CDasherNode node) {
			//Note this duplicates a fair bit of MoveAction.populate...
			CContNode b = BACK.make(mgr,node);
			CContNode f = FWD.make(mgr,node);
			if (b!=null)
				b.Reparent(node, 0, f!=null ? NORMALIZATION/2 : NORMALIZATION);
			if (f!=null)
				f.Reparent(node, b!=null ? NORMALIZATION/2 : 0, NORMALIZATION);
		}

		public int expectedNumSuccs(CDasherNode node) {
			return (node.getOffset()>=0 ? 1 : 0) + (node.getCharAt(node.getOffset()+1)!=null ? 1 : 0);
		}
	};
	
	public static class AlphSwitcher extends CDasherComponent implements ControlAction, Runnable {
		private String switchTo;
		
		AlphSwitcher(CDasherComponent creator) {
			super(creator);
			HandleEvent(Esp_parameters.SP_ALPHABET_ID);
		}
		
		private static final Esp_parameters[] PAST_ALPHS = {
			Esp_parameters.SP_ALPHABET_1, Esp_parameters.SP_ALPHABET_2, Esp_parameters.SP_ALPHABET_3, Esp_parameters.SP_ALPHABET_4
		};
	
		/** cache for next */
		private final List<ControlAction> alphChanges = new ArrayList<ControlAction>();
		
		@Override public void HandleEvent(EParameters param) {
			if (param == Esp_parameters.SP_ALPHABET_ID) {
				alphChanges.clear();
				String n = GetStringParameter(Esp_parameters.SP_ALPHABET_ID);
				List<String> all = new ArrayList<String>(PAST_ALPHS.length+1);
				all.add(n); //include for summarizing
				for (int i=0; i<PAST_ALPHS.length; i++) {
					String h = GetStringParameter(PAST_ALPHS[i]);
					if (h.length()>0 && !all.contains(h))
						all.add(h); //TODO wd like 2 check h exists in CAlphIO?
				}
				String[] titles = summarize(all.toArray(new String[all.size()]));
				//but don't make a node to "change" to the current alph
				for (int i=1; i<titles.length; i++) {
					final String alphName = all.get(i);
					alphChanges.add(new FixedSuccessorsAction(titles[i]) {
						public void happen(CControlManager mgr, CDasherNode node) {
							switchTo = alphName;
							mgr.m_Interface.doAtFrameEnd(AlphSwitcher.this);
						}
					});
				}
			}
		}

		//this is the header, it doesn't do anything
		public void happen(CControlManager mgr, CDasherNode node) {}
		public void undo(CControlManager mgr, CDasherNode node) {}
		
		public void populate(CControlManager mgr, CDasherNode node) {
			mgr.populate(node, alphChanges);
		}
		
		public int expectedNumSuccs(CDasherNode node) {return alphChanges.size();}		
		public CContNode make(CControlManager mgr, CDasherNode parent) {
			if (alphChanges.isEmpty()) return null;
			if (alphChanges.size()==1) return alphChanges.get(0).make(mgr, parent);
			return mgr.makeCont(this, parent.getOffset(), 7, "Alph"); //TODO internationalize
		}
		
		public void run() {
			SetStringParameter(Esp_parameters.SP_ALPHABET_ID, switchTo);
		}
	
		private static final String[] NO_STRINGS = {};
	
		/**
		 * @param in Array of strings to summarize; may be destructively updated
		 * @param index highest index to which all supplied Strings are known to be the same
		 * @return new array of summaries
		 */
		private static String[] summarize(String[] in) {
			if (in.length==0) return NO_STRINGS;
			String[] out = new String[in.length];
			StringBuilder sb = new StringBuilder(); //the bit which is all the same
			int c;
			advanceChar: for (c=0;;c++) {
				int s;
				for (s=0; s<in.length; s++)
					if (in[s].length()<=c || in[s].charAt(c)!=in[0].charAt(c)) break advanceChar;
				//all the same; add to caption accordingly
				if (c<3)
					sb.append(in[0].charAt(c));
				else if (c<6)
					sb.append(".");
				//TODO unicode ellipsis
			}
			//some are different, or else some terminate, at index c
			Arrays.fill(out, sb.toString()); //caption for the bit that's the same
			str: for (int s=0; s<in.length; s++) {
				if (in[s].length()<=c) continue; //don't recurse, just use caption above
				//ok, make recursive call for all elements same as this one at index c,
				// if we haven't done such already
				for (int o=0; o<s; o++)
					if (in[o].length()>c && in[o].charAt(c)==in[s].charAt(c))
						continue str; //no, already done in previous iter of str
				//yes, ok, make recursive call.
				//first gather together all later elements matching this one...
				List<String> temp = new ArrayList<String>();
				temp.add(in[s].substring(c));
				for (int o=s+1; o<in.length; o++)
					if (in[o].length()>c && in[o].charAt(c)==in[s].charAt(c))
						temp.add(in[o].substring(c));
				//now make recursive call to get captions summarizing
				// the remaining portion of each String
				String[] sub = summarize(temp.toArray(new String[temp.size()]));
				//append results of recursive calls to captions already computed
				out[s]+=sub[0];
				for (int o=s+1, n=0; o<in.length; o++)
					if (in[o].length()>c && in[o].charAt(c)==in[s].charAt(c))
						out[o]+=sub[++n];
			}
			return out; //filled in by recursive calls
		}
	}
	
	private static final String SPEED_CHANGE_HEADER = "Speed"; //TODO Internationalize
	
	public static final ControlAction SPEED_CHANGE = new ControlActionBase(SPEED_CHANGE_HEADER) {
		// array of slowdown coefficients, in range 0<x<1, with lowest (most extreme slowdown) first
		private final double[] FRAC = {0.67, 0.95};
		//Node boundaries in probability (i.e. dasherY) space: for
		// FRAC decreases; escape to alphabet; then corresponding increases
		private final long[] BOUNDS = new long[FRAC.length*2+2];
		{
			//fill in BOUNDS array. We make the most extreme increase & decrease be
			// relative size 1; the next most extreme 2; and so on, with the escape
			// to alphabet being the same size as each of the least-extreme speed changes. 
			final int max = FRAC.length * (FRAC.length+2);
			for (int i=0, v=0; i<BOUNDS.length; i++) {
				BOUNDS[i] = (v*NORMALIZATION)/max;
				if (i<FRAC.length) {
					v+=i+1;
				} else if (i==FRAC.length) {
					v+=i;
				} else {
					v+=BOUNDS.length-1-i;
				}
			}
		}
		public void populate(CControlManager mgr, CDasherNode node) {
			//We generate children all with the same ControlAction, abusing the
			// node's m_strDisplayText to tell us what change (if any) it makes.
			final long base = (node.m_strDisplayText!=SPEED_CHANGE_HEADER)
				? (long)(Double.parseDouble(node.m_strDisplayText)*100)
				: mgr.GetLongParameter(Elp_parameters.LP_MAX_BITRATE);
			long lower=0;
			for (int i=0; i<BOUNDS.length-1; i++) {
				long upper = BOUNDS[i+1];
				CDasherNode n = (i==FRAC.length)
						? //escape to alphabet
								(mgr.GetBoolParameter(Ebp_parameters.BP_CONTROL_MODE_REBUILD) 
										? COMMIT_ALPH.make(mgr, node)
										: mgr.m_pNCMgr.getAlphabetManager().GetRoot(node, node.getOffset(), false))
						: mgr.makeCont(this, node.getOffset(), i+99, 
								Double.toString( ((long)(i<FRAC.length ? base*FRAC[i] : base/FRAC[FRAC.length*2-i])) / 100.0));
				n.Reparent(node, lower, upper);
				lower=upper;
			}
		}
		
		@Override
		public void happen(CControlManager mgr, CDasherNode node) {
			//As previous, abusing m_strDisplayText to distinguish
			// between the "Speed" header and a new speed. 
			if (node.m_strDisplayText!=SPEED_CHANGE_HEADER) {
				//backup old speed in case we need to undo
				mgr.extraInfo.put((CContNode)node,mgr.GetLongParameter(Elp_parameters.LP_MAX_BITRATE));
				Double d = Double.parseDouble(node.m_strDisplayText);
				mgr.SetLongParameter(Elp_parameters.LP_MAX_BITRATE, (long)(d*100));
			}
		}
		@Override public void undo(CControlManager mgr, CDasherNode node) {
			//if there's anything in the map, it should have been put there by our happen(),
			// so it jolly well should be a Long!
			Long oldSpeed = (Long)mgr.extraInfo.get(node);
			if (oldSpeed!=null) mgr.SetLongParameter(Elp_parameters.LP_MAX_BITRATE, oldSpeed.longValue());
			mgr.extraInfo.remove(node);
		}

		public int expectedNumSuccs(CDasherNode node) {
			return BOUNDS.length-1;
		}		
	};

	public static void main(String[] args) {
		String[] s = AlphSwitcher.summarize(args);
		for (int i=0; i<s.length; i++)
			System.out.println(s[i]);
	}
}
