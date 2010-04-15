package dasher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

public abstract class ExpansionPolicy {
	public abstract void pushNode(CDasherNode node, int min, int max, boolean bExpandable);
	public abstract boolean apply(CDasherModel model);
}

/*package?*/ class BudgettingPolicy extends ExpansionPolicy {
	private final int m_iNodeBudget;
	BudgettingPolicy(int iNodeBudget) {
		this.m_iNodeBudget=iNodeBudget;
	}
	private CDasherNode[] expandable = new CDasherNode[8],
						  collapsible = new CDasherNode[8];
	private int nextExp=0, nextCol=0;
	public void pushNode(CDasherNode node, int min, int max, boolean bExp) {
		double cost = getCost(node,min,max);
		if (node.Parent()!=null) cost=Math.min(cost,node.Parent().m_dCost);
		node.m_dCost=cost;
		if (bExp) {
			if (nextExp>=expandable.length) expandable=resize(expandable);
			expandable[nextExp++]=node;
		} else {
			if (nextCol>=collapsible.length) collapsible=resize(collapsible);
			collapsible[nextCol++]=node;
		}
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
	
	private static int compare(CDasherNode n1, CDasherNode n2) {
		if (n1.m_dCost != n2.m_dCost) return (n1.m_dCost< n2.m_dCost) ? -1 : 1;
		//see if either is parent of the other...
		CDasherNode n1p=n1.Parent(), n2p=n2.Parent();
		while (n1p!=null || n2p!=null) {
			if (n1p==n2) {
				//n1 is descendant of n2, so has less cost
				return -1;
			}
			else if (n2p==n1) return 1;
			
			if (n1p!=null) {
				//only go as far up tree as the costs are equal: 
				if (n1p.m_dCost!=n1.m_dCost) n1p=null;
				else n1p=n1p.Parent();
			}
			if (n2p!=null) {
				if (n2p.m_dCost!=n2.m_dCost) n2p=null;
				else n2p=n2p.Parent();
			}
		}
		return 0; //indistinguishable. Or should we make a total ordering?
	}
	
	public boolean apply(CDasherModel model) {
		sort(0, nextCol, collapsible); //lowest cost first, i.e. collapse from beginning first
		sort(0, nextExp, expandable); //highest benefit last, i.e. expand from end backwards
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

	/** Android's sort method doesn't seem to like our comparator (?!).
	 * So this actually the optimized-looking Android routine for sorting arrays of _floats_...
	 * Modified for CDasherNodes using {@link #compare}, of course!
	 * @param start
	 * @param end
	 * @param array
	 */
	private static void sort(int start, int end, CDasherNode[] array) {
        CDasherNode temp;
        int length = end - start;
        if (length < 7) {
            for (int i = start + 1; i < end; i++) {
                for (int j = i; j > start && compare(array[j], array[j - 1])<0; j--) {
                    temp = array[j];
                    array[j] = array[j - 1];
                    array[j - 1] = temp;
                }
            }
            return;
        }
        int middle = (start + end) / 2;
        if (length > 7) {
            int bottom = start;
            int top = end - 1;
            if (length > 40) {
                length /= 8;
                bottom = med3(array, bottom, bottom + length, bottom
                        + (2 * length));
                middle = med3(array, middle - length, middle, middle + length);
                top = med3(array, top - (2 * length), top - length, top);
            }
            middle = med3(array, bottom, middle, top);
        }
        CDasherNode partionValue = array[middle];
        int a, b, c, d;
        a = b = start;
        c = d = end - 1;
        while (true) {
            while (b <= c && compare(partionValue, array[b])>=0) {
                if (array[b] == partionValue) {
                    temp = array[a];
                    array[a++] = array[b];
                    array[b] = temp;
                }
                b++;
            }
            while (c >= b && compare(array[c], partionValue)>=0) {
                if (array[c] == partionValue) {
                    temp = array[c];
                    array[c] = array[d];
                    array[d--] = temp;
                }
                c--;
            }
            if (b > c) {
                break;
            }
            temp = array[b];
            array[b++] = array[c];
            array[c--] = temp;
        }
        length = a - start < b - a ? a - start : b - a;
        int l = start;
        int h = b - length;
        while (length-- > 0) {
            temp = array[l];
            array[l++] = array[h];
            array[h++] = temp;
        }
        length = d - c < end - 1 - d ? d - c : end - 1 - d;
        l = b;
        h = end - length;
        while (length-- > 0) {
            temp = array[l];
            array[l++] = array[h];
            array[h++] = temp;
        }
        if ((length = b - a) > 0) {
            sort(start, start + length, array);
        }
        if ((length = d - c) > 0) {
            sort(end - length, end, array);
        }
    }
	
	private static int med3(CDasherNode[] array, int a, int b, int c) {
		CDasherNode x = array[a], y = array[b], z = array[c];
		return compare(x,y)<0 ? (compare(y,z)<0 ? b : (compare(x,z)<0 ? c : a)) : (compare(y,z)>0 ? b : (compare(x,z)>0 ? c : a));
	}

}