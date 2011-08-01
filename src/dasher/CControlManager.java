package dasher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static dasher.CDasherModel.NORMALIZATION;

public class CControlManager extends CDasherComponent {
	private CNodeCreationManager m_pNCMgr;
	private final ControlAction root;
	CControlManager(CDasherComponent creator, final List<ControlAction> actions) {
		super(creator);
		this.root=(actions.size()==1) ? actions.get(0) :
			new ControlAction() {
				public String desc() {return "Control";} //TODO internationalize
				public void happen(CDasherNode node) {} //do nothing
				public List<ControlAction> successors() {return actions;}
			};
	}

	void ChangeNCManager(CNodeCreationManager pNCMgr) {m_pNCMgr = pNCMgr;}

	static int getColour(CDasherNode parent) {return parent==null ? 7 : (parent.ChildCount()%99)+11;}

	public CContNode GetRoot(int iOffset) {
		return makeCont(iOffset, getColour(null), root, null);
	}
	
	public static interface ControlAction {
		public void happen(CDasherNode node);
		public String desc();
		public List<ControlAction> successors();
	}
	
	private class CContNode extends CDasherNode {
		
		private ControlAction act;

		/*package*/ void initNode(int iOffset, int iColour, ControlAction act) {
			super.initNode(iOffset, iColour, act.desc());
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
				CDasherNode temp = (actions.get(i)==null)
					? m_pNCMgr.getAlphabetManager().GetRoot(getOffset(), false)
							: makeCont(getOffset(), getColour(this), actions.get(i), existing);
				temp.Reparent(this, boundary, next);
				boundary=next;
			}
		}
		
		@Override public boolean shove() {return false;}

		@Override
		public CDasherNode RebuildParent() {
			if (root.successors().indexOf(act)!=-1) {
				CContNode c = GetRoot(getOffset());
				c.PopulateChildren(this);
				return c;
			}
			return m_pNCMgr.getAlphabetManager().GetRoot(getOffset(), true);			
		}		
	}
	
	private final List<CContNode> nodeCache = new ArrayList<CContNode>();
	
	private CContNode makeCont(int iOffset, int iColour, ControlAction act, CContNode existing) {
		if (existing!=null && existing.act==act) {
			return existing;
		}
		CContNode node = (nodeCache.isEmpty()) ? new CContNode() : nodeCache.remove(nodeCache.size()-1);
		node.initNode(iOffset, iColour, act);
		return node;
	}
}
