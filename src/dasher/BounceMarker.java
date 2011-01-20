package dasher;

import java.util.LinkedList;

public class BounceMarker {
	static final boolean DEBUG_LEARNING=true;
	
	private static class ParzenEstimator {
		private final double m_Lambda;
		private final int m_nL;
			
		private final int[] hist;
		private int hist_push=-1;
		
		public ParzenEstimator(int histLen, int initMean, double initVar2) {
			this.m_Lambda=1.0-(1.0/histLen);
			this.m_nL=histLen;
			this.hist=new int[histLen];
			int numBuckets = (int)(3*initVar2);
			weightsNeg=new double[numBuckets];
			weightsPos=new double[numBuckets];
			AddDist(initMean, initVar2, m_nL);
		}
		
		private double[] weightsNeg,weightsPos;
		private double getWeight(int idx) {
			if (idx<0) {
				idx=-(idx+1);
				return (idx<weightsNeg.length) ? weightsNeg[idx] : 0.0;
			}
			return (idx<weightsPos.length) ? weightsPos[idx] : 0.0;
		}
		
		private void addWeight(int idx, double d) {
			if (idx<0) {
				idx=-(idx+1);
				if (idx>=weightsNeg.length) {
					double[] n=new double[Math.max(weightsNeg.length+5,idx+1)];
					System.arraycopy(weightsNeg, 0, n, 0, weightsNeg.length);
					weightsNeg=n;
					weightsNeg[idx]=d;
				} else {
					weightsNeg[idx]+=d;
				}
			} else if (idx>=weightsPos.length) {
				double[] n=new double[Math.max(weightsPos.length+5,idx+1)];
				System.arraycopy(weightsPos, 0, n, 0, weightsPos.length);
				weightsPos=n;
				weightsPos[idx]=d;
			} else {
				weightsPos[idx]+=d;
			}
		}
		
		private int mean() {
		    double tot = 0.0;
		    for (int i=0; i<weightsNeg.length; i++) tot+=weightsNeg[i];
		    for (int i=0; i<weightsPos.length; i++) tot+=weightsPos[i];
		    tot/=2.0;
			for (int i = -weightsNeg.length; ; i++) {
			    tot -= getWeight(i);
			    if (tot <= 0.0) return i;
			}
		}
		
		private static final int NUM_ROWS=6;
		
		/** Adds a gaussian with the given mean, variance, and weight, to the distribution */
		private void AddDist(int mean, double var, double wt) {
			int num = (int)(6*Math.sqrt(var)); // 6 std deviations
			double tot=0.0, dDist[] = new double[2*num]; 
			for (int i=0; i<2*num; i++)
			    tot+= dDist[i]= Math.exp(-(i-num)*(i-num)/(var*2.0));
			wt/=tot;
			for (int i = 0; i < 2*num; i++)
			    addWeight(i+mean-num,dDist[i] * wt);
			//delete dDist
			/*if (DEBUG_LEARNING) {
				//print distribution
				double dMax = 0.0;
				for (int i=0; i<weightsNeg.length; i++) dMax=Math.max(dMax,weightsNeg[i]);
				for (int i=0; i<weightsPos.length; i++) dMax=Math.max(dMax,weightsPos[i]);
				java.io.StringWriter sw=new java.io.StringWriter();
				java.io.PrintWriter pw=new java.io.PrintWriter(sw);
				pw.println(); //initial newline
				for (int row = 1; row <= NUM_ROWS; row++) {
					for (int i = -weightsNeg.length; i < weightsPos.length; i++)
						pw.print(getWeight(i)*row >= dMax ? '*' : ' ');
					pw.println();
				}
				pw.flush();
				System.err.println(sw.toString());
			}*/
		}
		/** Gets the best width (i.e. variance) of a gaussian to add to the distribution,
		 * centered upon a new click, given the current history of clicks.
		 * @return variance (as should be passed to {@link #AddDist(int, double, double)}).
		 */
		private double ExpWidth() {
			//1."standard unbiased variance estimator"...so probably not this?!?!
			int tot=0;
			for (int i = 0; i<hist.length; i++) tot+=hist[i];
			double mean = tot / (double)hist.length;
			double var=0.0;
			for (int i=0; i<hist.length; i++) {
			    double temp = hist[i] - mean;
			    var += temp*temp;
			}
			var /= (hist.length-1.0);
			//2. standard dev according to formula from Tamara's thesis...
			double d = 1.06/Math.pow(hist.length, 0.2);
			return var*d*d;
		}
		
		private void DecayAndAdd(int time, double var) {
			for (int i=0; i<weightsNeg.length; i++) weightsNeg[i]*=m_Lambda;
			for (int i=0; i<weightsPos.length; i++) weightsPos[i]*=m_Lambda;
			AddDist(time, var, 1.0);
			//remove outliers if less significant than a binormal distribution (heavy-tailed!)
			//should we scale everything up to compensate? Assume not significant...
			/*
			  #define MIN_SCALE 5.0
			  #define MIN_WEIGHT 0.01
			  while (weights[weights.minIndex()] < exp(weights.minIndex()/MIN_SCALE)*MIN_WEIGHT) weights[weights.minIndex()]=0.0;
			  while (weights[weights.maxIndex()] < exp(-weights.maxIndex()/MIN_SCALE)*MIN_WEIGHT) weights[weights.maxIndex()]=0.0;
			  weights.trim();
			*/
		}
		  
		private void AddElem(int time) {
			if (hist_push<0) {
				//not added anything yet
				hist[hist_push^(-1)]=time;
				hist_push--;
				if (-hist_push>hist.length) {
					//points in hist have not been added. Do so now...
					double var=ExpWidth();
				    for (int i=0; i<hist.length; i++)
				    	DecayAndAdd(hist[i],var);
				    hist_push=0;
				}
			} else {
				//hist being used as queue...
				hist[hist_push++]=time;
				if (hist_push==hist.length) hist_push=0;
				DecayAndAdd(time,ExpWidth());
			}
		}

	}
	
	private int m_iLocn;
	private double m_dNumNats;
	private final ParzenEstimator window;
	
	private static final int BINS_PER_SEC=100;
	
	public BounceMarker(int iLocn) {
		this.m_iLocn = iLocn;
		this.m_dNumNats = Math.log(2.0*Math.abs(iLocn));
		
		//lambda, init mean, init variance...in (msec but with nats not bits)
		this.window = new ParzenEstimator(3, (int)(0.1*BINS_PER_SEC*2.0/Math.E), Math.pow(0.02*BINS_PER_SEC*2.0/Math.E,2.0));
	}
	
	public int GetTargetOffset(double dCurBitrate) {
		//TODO, adapt this to take into account the current display offset of the model?
		double dMean = window.mean();
		int iOffset = (int)(Math.exp(window.mean()*dCurBitrate/BINS_PER_SEC)* m_iLocn);
		android.util.Log.d("DasherIME","Computed mean " + dMean + " so offset " + iOffset);
		return iOffset;
	}
	
	/** Record of the current location of the sentence at the marker
	 * when some previous button was pushed
	 * @author acl33
	 *
	 */
	private static class PushRec {
		int pixelLocn;
		double natsSince;
		double bitrate;
		/** The next (i.e. came after / more recently in time) push after this one */
		PushRec next;
	}
	
	/** The longest-ago push we are still tracking, i.e. head of the list */
	private PushRec longest_push;
	/** The most recent push, i.e. tail of the list */
	private PushRec prev_push;
	/** Object pool of allocated but currently-unused PushRecs */
	
	private PushRec freeList;
	
	public void NotifyOffset(int iOffset, double dNats) {
		//1. apply dNats growth and iOffset offset to all previous pushes...
		for (PushRec p=longest_push; p!=null; p=p.next) {
		    p.pixelLocn = (int)(p.pixelLocn * Math.exp(dNats) - iOffset);
		    p.natsSince += dNats;
		}
		
		//2. for previous pushes that were long enough ago...
		while (longest_push!=null && longest_push.natsSince <= m_dNumNats) {
			PushRec p = longest_push;
			if ((longest_push = longest_push.next)==null) prev_push=null;
			//ok - p.pixelLocn is the position _now_ of (the sentence at the marker when offset was applied)
				//target sentence is now at 0
			double orig_pos = m_iLocn + longest_push.pixelLocn / Math.exp(longest_push.natsSince);
			double mul = orig_pos / m_iLocn;
		    //TODO - check ok for orig_pos inside&outside positive&negative m_iLocn.
		    //if not, do the long way:
		    //window.AddElem((mul<1.0 ? -ln(1.0/mul) : ln(mul)) / longest->bitrate);
		    window.AddElem((int)(Math.log(mul) * BINS_PER_SEC / longest_push.bitrate)); //msec, except nats not bits
		    android.util.Log.d("DasherIME","Updated mean to "+window.mean());
		    //TODO, store (some summary of) means&variances as a permanent setting for next time?
		    p.next=freeList; freeList=p;
		}
	}
	
	public void addPush(int iOffset, double bitrate) {
		PushRec p;
		if (freeList!=null) {
			p=freeList;
			freeList = freeList.next;
		} else p=new PushRec();
		p.next=prev_push;
		prev_push=p;
		p.pixelLocn = m_iLocn - iOffset;
		p.natsSince=0.0;
		p.bitrate = bitrate;
	}
	
	public void clearPushes() {
		if (prev_push!=null) {
			prev_push.next=freeList;
			freeList = longest_push;
			longest_push=prev_push=null;
		}
		//Don't hold onto >4 PushRec's.
		int i=0;
		for (PushRec p=freeList; p!=null; p=p.next, i++)
			if (i>4) p.next=null; //will now exit loop.	
	}
	
	private final long[] temp = new long[2];
	public void Draw(CDasherView pView, double dNats) {
		pView.Dasherline(-100, 2048-m_iLocn, -1000, 2048-m_iLocn, 3, 1);
		
		if (DEBUG_LEARNING) {
			double e = Math.exp(dNats);
			//unfortunately this won't take account of the 'smoothing' applied to Offsets...
			for (PushRec p=longest_push; p!=null; p=p.next) {
				long y = (long)(2048-p.pixelLocn * e);
				pView.Dasherline(-100, y, -1000, y, 1, 2);
				
				temp[0] = -100; temp[1] = y;
				pView.Dasher2Screen(temp);
				//and the adjustments'll only be (even roughly) right for standard left-to-right orientation...
				pView.Screen().DrawString(Double.toString(p.natsSince+dNats), (int)temp[0], (int)temp[1]-5, 10);
			}
		}
	}

}
