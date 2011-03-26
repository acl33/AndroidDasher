package dasher;

import java.util.ArrayList;
import java.util.List;

import static dasher.CDasherModel.NORMALIZATION;

public class CControlManager extends CDasherComponent {
	private final CNodeCreationManager m_pNCMgr;
	private final ControlAction root;
	CControlManager(CDasherInterfaceBase intf, CSettingsStore sets, CNodeCreationManager pNCMgr, ControlAction root) {
		super(intf,sets);
		this.m_pNCMgr=pNCMgr;
		this.root=root;
	}
	
	public CContNode GetRoot(CDasherNode parent, int iOffset, long iLbnd, long iHbnd) {
		return makeCont(parent, iOffset, iLbnd, iHbnd, root, null);
	}
	
	public static interface ControlAction {
		public void happen(CDasherNode node);
		public String desc();
		public List<ControlAction> successors();
	}
	
	private class CContNode extends CDasherNode {
		
		private ControlAction act;

		/*package*/ void initNode(CDasherNode parent, int iOffset, long iLbnd, long iHbnd, ControlAction act) {
			super.initNode(parent, iOffset, iLbnd, iHbnd, 7, act.desc());
			this.act=act;
		}
		
		@Override
		public int ExpectedNumChildren() {
			return act.successors().size(); 
		}

		@Override
		public void Output() {
			act.happen(this);
		}

		@Override
		public void PopulateChildren() {
			PopulateChildren(null);
		}
		
		private void PopulateChildren(CContNode existing) {
			List<ControlAction> actions = act.successors();
			long boundary = 0;
			for (int i=0; i<actions.size(); i++) {
				long next = (i+1) * NORMALIZATION / actions.size();
				if (actions.get(i)==null)
					m_pNCMgr.getAlphabetManager().GetRoot(this, boundary, next, getOffset(), false);
				else
					makeCont(this, getOffset(), boundary, next, actions.get(i), existing);
				boundary=next;
			}
		}
		
		@Override public boolean shove() {return false;}

		@Override
		public CDasherNode RebuildParent() {
			if (root.successors().indexOf(act)!=-1) {
				CContNode c = GetRoot(null, getOffset(), 0, NORMALIZATION);
				c.PopulateChildren(this);
				return c;
			}
			return m_pNCMgr.getAlphabetManager().GetRoot(null, 0, NORMALIZATION, getOffset(), true);			
		}		
	}
	
	private final List<CContNode> nodeCache = new ArrayList<CContNode>();
	
	private CContNode makeCont(CDasherNode parent, int iOffset, long iLbnd, long iHbnd, ControlAction act, CContNode existing) {
		if (existing!=null && existing.act==act) {
			existing.SetRange(iLbnd, iHbnd);
			existing.SetParent(parent);
			return existing;
		}
		CContNode node = (nodeCache.isEmpty()) ? new CContNode() : nodeCache.remove(nodeCache.size()-1);
		node.initNode(parent, iOffset, iLbnd, iHbnd, act);
		return node;
	}
}
