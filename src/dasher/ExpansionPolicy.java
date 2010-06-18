package dasher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

public abstract class ExpansionPolicy {
	public abstract void pushNode(CDasherNode node, int min, int max, boolean bExpandable);
	public abstract boolean apply(CDasherModel model);
}

/*package?*/ class BudgettingPolicy extends ExpansionPolicy implements Comparator<CDasherNode> {
	private final int m_iNodeBudget;
	BudgettingPolicy(int iNodeBudget) {
		this.m_iNodeBudget=iNodeBudget;
	}
	private CDasherNode[] expandable = new CDasherNode[8],
						  collapsible = new CDasherNode[8];
	private int nextExp=0, nextCol=0;
	public void pushNode(CDasherNode node, int min, int max, boolean bExp) {
		double cost = getCost(node,min,max);
		pushToExpand: {
			if (node.Parent()!=null && cost>=node.Parent().m_dCost) {
				cost = node.Parent().m_dCost; //in case it was strictly less!
				
				if (!bExp) {
					//Parent has children, so must have been enqueued to collapse;
				    // thus, avoid enqueuing child node to collapse also: if costs are accurate
				    // (i.e. in terms of the benefit/detriment of what's onscreen), then collapsing
				    // parent will free up more nodes (by recursively collapsing child)
					break pushToExpand;
				}
				//else, fall through to push onto expandables.
			} else if (!bExp) {
				if (nextCol>=collapsible.length) collapsible=resize(collapsible);
				collapsible[nextCol++]=node;
				break pushToExpand;
			}
			//Fall-through from first case (cost == parent)&& bExp),
			// or !bExp.
			if (nextExp>=expandable.length) expandable=resize(expandable);
			expandable[nextExp++]=node;
		}
		node.m_dCost=cost;
	}
	
	private CDasherNode[] resize(CDasherNode[] arr) {
		CDasherNode[] n = new CDasherNode[arr.length*2];
		System.arraycopy(arr, 0, n, 0, arr.length);
		return n;
	}
	
	protected double getCost(CDasherNode node, int y1, int y2) {
		if (y1>4096 || y2<0) return 0.0;
		return Math.min(y2,4096) - Math.max(y1,0);
	}
	
	public int compare(CDasherNode a,CDasherNode b) {
		if (a.m_dCost != b.m_dCost) return (a.m_dCost - b.m_dCost < 0) ? -1 : 1;
		//identical costs - and nodes are not direct ancestors...
		//the following may be a bit elaborate, but should give a _total_ ordering... 
		while (true) {
			//first, order according to lexicographic order on display text...
			int i = a.m_strDisplayText.compareTo(b.m_strDisplayText);
			if (i!=0) return i;
			//display texts equal.
			CDasherNode ap=a.Parent(), bp=b.Parent();
			if (ap == bp) {
				//happens only if original a & b were of same generation.
				//ap cannot be null, as previous a&b were distinct, and only one node has null parent (the root)
				for (CDasherNode ch : ap.Children())
					if (ch==a) return -1;//a is first sibling
					else if (ch==b) return 1; //b is first sibling
				throw new AssertionError(); //should never happen - a & b should _both_ be among children!
			}
			if (ap==null) return -1;//a has no parent, i.e. is root; b has a parent, so is of younger generation
			if (bp==null) return 1;//a is of younger generation
			a=ap; b=bp;
		}
	}
	public boolean apply(CDasherModel model) {
		Arrays.sort(collapsible, 0, nextCol, this); //lowest cost first, i.e. collapse from beginning first
		Arrays.sort(expandable, 0, nextExp, this); //highest benefit last, i.e. expand from end backwards
		//did we expand anything? (if so, there may be more opportunities for expansion next frame)
		boolean bReturnValue = false;

		//maintain record of the highest cost we've incurred by collapsing a node;
		// avoid expanding anything LESS beneficial than that, as (even if we've room)
		// it'll be better to instead wait until next frame (and possibly re-expand the
		// collapsed node! Sadly we can't rely on trading one-for-one as different nodes
		// may have different numbers of children...)
		double collapseCost = Double.NEGATIVE_INFINITY;
		int collapseIdx=0; //next node in collapsible to collapse - proceed forwards!
		
		//first, make sure we are within our budget (probably only in case the budget's changed)
		while (collapseIdx<nextCol
		       && CDasherNode.currentNumNodeObjects() > m_iNodeBudget)
		{
			CDasherNode n = collapsible[collapseIdx++];
		    assert n.m_dCost >= collapseCost;
		    collapseCost = n.m_dCost;
		    n.Delete_children();
		    bReturnValue = true;
		}

		//ok, we're now within budget. However, we may still wish to "trade off" nodes
		// against each other, in case there are any unimportant (low-cost) nodes we could collapse
		// to make room to expand other more important (high-benefit) nodes.  
		while (nextExp>0)
		{
			CDasherNode nExp = expandable[nextExp-1];
			if (nExp.m_dCost <= collapseCost) break; 
			if (CDasherNode.currentNumNodeObjects()+15/*ACL TODO FIXME nExp.ExpectedNumChildren()*/ < m_iNodeBudget)
			{
		    	model.Push_Node(nExp);
		    	nextExp--;
		    	bReturnValue = true;
		    	//...and loop.
		    } else if (collapseIdx<nextCol
		               && collapsible[collapseIdx].m_dCost< nExp.m_dCost)
		    {
		    	//could be a beneficial trade - make room by performing collapse...
		    	CDasherNode c=collapsible[collapseIdx++];
		    	assert c.m_dCost >= collapseCost;
		    	collapseCost = c.m_dCost;
		    	c.Delete_children();
		    	//...and see how much room that makes
		    }
		    else break; //not enough room, nothing to collapse.
		}
		//make ready for reuse in next frame...
		nextExp=0;
		nextCol=0;
		return bReturnValue;
	}

}