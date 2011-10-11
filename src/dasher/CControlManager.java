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
			//position as the control-node child of an alph root. Not ideal, but given
			// most CContNode's rebuild anyway...
			CDasherNode temp = m_pNCMgr.getAlphabetManager().GetRoot(m_Interface.getDocument(), getOffset(), true);
			temp.PopulateChildren();
			CContNode replace = (CContNode)temp.ChildAtIndex(temp.ChildCount()-1);
			CDasherNode ret = m_pNCMgr.getAlphabetManager().GetRoot(m_Interface.getDocument(), getOffset(), true);
			for (int i=0; i<temp.ChildCount()-1; i++)
				temp.ChildAtIndex(i).Reparent(ret, temp.Lbnd(), temp.Hbnd());
			Reparent(ret, replace.Lbnd(), replace.Hbnd());
			return ret;
		}
		
		@Override
		public void DeleteNode() {
			super.DeleteNode();
			nodeCache.add(this);
		}
	}
	
	private final List<CContNode> nodeCache = new ArrayList<CContNode>();

	public CContNode makeCont(ControlAction act, int iOffset, int iColour, String desc) {
		CContNode node = (nodeCache.isEmpty()) ? new CContNode() : nodeCache.remove(nodeCache.size()-1);
		node.initNode(iOffset, iColour, act, desc);
		return node;
	}
}
