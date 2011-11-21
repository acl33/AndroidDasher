package dasher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
	
	/*package*/ void populate(CDasherNode node, List<ControlAction> actions) {
		long boundary = 0;
		for (int i=0; i<actions.size(); i++) {
			long next = (i+1) * NORMALIZATION / actions.size();
			CDasherNode temp = (actions.get(i)==null)
				? m_pNCMgr.getAlphabetManager().GetRoot(node, node.getOffset(), false)
				: actions.get(i).make(this,node);
			temp.Reparent(node, boundary, next);
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
			if (mgr.GetBoolParameter(Ebp_parameters.BP_MOVE_REBUILD)
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
					(mgr.GetBoolParameter(Ebp_parameters.BP_MOVE_REBUILD) && !mgr.GetBoolParameter(Ebp_parameters.BP_MOVE_REBUILD_IMMED))
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
}
