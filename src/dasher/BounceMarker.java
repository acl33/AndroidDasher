package dasher;

public class BounceMarker {
	static final boolean DEBUG_LEARNING=true;
	
	protected static class ParzenEstimator {
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
		
		/** Get the mean of the result of multiplying this distribution
		 * with (another distribution shifted by an offset)
		 * @param other the other distribution
		 * @param offset Amount to shift the other distribution _back_ by
		 * (i.e. positive offset = pull it backwards in time,
		 * so high-positive elements there line up with lower-index elements here)
		 * @return Mean of the product distribution; if the product distribution is
		 * zero, return the midpoint of the two means (one shifted back by the offset) instead.  
		 */
		public int meanMulOff(ParzenEstimator other, int offset) {
			double tot = 0.0;
			//Element i of (other shifted back by offset) is element (i+offset) of (other).
			//No need to examine elements where one dist is definitely zero. 
			for (int i=-weightsNeg.length; i<weightsPos.length; i++) tot+=getWeight(i)*other.getWeight(i+offset);
			if (tot==0.0) {
				android.util.Log.d("DynamicLearn", "Offset "+offset+" no ovelap mean "+mean()+" w "+other.mean()+" => "+(mean() + other.mean()-offset)/2);
				//interpolate the midpoints, evenly weighted. (Better would be to weight
				// according to variance, i.e. estimator X more concentrated => result
				// closer to mean of X)
				return (mean() + other.mean()-offset)/2;
			}
			tot/=2.0;
			for (int i = -weightsNeg.length; ; i++) {
			    tot -= getWeight(i)*other.getWeight(i+offset);
			    if (tot <= 0.0) {
			    	return i;
			    }
			}
		}
		
		/**
		 * Gets the indices between which some portion of the distribution lies
		 * @param frac 0<frac<0.5 Proportion of distribution to exclude at each end (e.g. top & bottom 5%)
		 * @param into 2-element array into which indices will be written
		 */
		public void getPercentile(double frac, int[] into) {
			double tot = 0.0;
		    for (int i=0; i<weightsNeg.length; i++) tot+=weightsNeg[i];
		    for (int i=0; i<weightsPos.length; i++) tot+=weightsPos[i];
		    double acc = 0.0; boolean fst=true;
		    for (int i= -weightsNeg.length; ; i++) {
		    	if ((acc+=getWeight(i)) >= tot*(fst ? frac : (1.0-frac))) {
		    		into[fst ? 0 : 1]=i;
		    		if (fst) fst=false; else return;
		    	}
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
	
	protected final int m_iLocn;
	protected final ParzenEstimator window;
	
	protected static final int BINS_PER_SEC=100;
	
	public BounceMarker(int iLocn) {
		this.m_iLocn = iLocn;
		
		//lambda, init mean, init variance...in (msec but with nats not bits)
		this.window = new ParzenEstimator(3, (int)(0.1*BINS_PER_SEC*2.0/Math.E), Math.pow(0.02*BINS_PER_SEC*2.0/Math.E,2.0));
	}

	public int GetTargetOffset(double dCurBitrate, BounceMarker other, long interval) {
		double expectedInterval = Math.log(other.m_iLocn / (double)m_iLocn) / dCurBitrate;
		//shift second (outer) dist. back in time relative to first:
		int iShift = (int) ((interval/1000.0 - expectedInterval)*BINS_PER_SEC);
		// (positive offset = long gap = pull the second-press dist back in time,
		// so late (high-index) presses there line up with early (low-index) presses here)
		int iMean = window.meanMulOff(other.window, iShift);
		//iMean is the mean of the product distribution, but expressed in the indices of the first distr.
		// We need the target coordinate relative to the _second_ (outer) marker, because
		// that's where the sentence is now...
		int iOffset = (int)(Math.exp((iMean+iShift)*dCurBitrate/BINS_PER_SEC)* other.m_iLocn);
		android.util.Log.d("DynamicLearn","Shift "+iShift+" => mean " + iMean + " & offset " + iOffset+" from "+other.m_iLocn);
		return iOffset;
	}

	public int GetTargetOffset(double dCurBitrate) {
		double dMean = window.mean();
		int iOffset = (int)(Math.exp(window.mean()*dCurBitrate/BINS_PER_SEC)* m_iLocn);
		android.util.Log.d("DynamicLearn","Computed mean " + dMean + " so offset " + iOffset);
		return iOffset;
	}
	
	/**
	 * Learn user's push distribution from the record of a previous push
	 * @param curPos The offset that would have to be applied _now_, to
	 * bring to the center of the Y axis, the sentence that was at the marker
	 * when the _original_ offset was applied.
	 * @param natsSince Total nats since the original offset
	 * @param bitrateThen Bitrate at the time the original offset was applied
	 */
	protected void learn(int curPos, double natsSince, double bitrateThen) {
		//When the user pushed the button (and the sentence at curPos was beside the marker),
		// (assume) they were aiming at the sentence that is _now_ at the center of the Y axis (offset 0).
		//To compute the location _then_ of this target sentence, we have to apply curPos offset _in_reverse_,
		// scaled down according to the nats entered since...
		double targetPos = m_iLocn - curPos / Math.exp(natsSince);
		//turn that into an offset in _time_, at which they must have clicked
		// (negative = sentence not yet reached marker)
		double time = Math.log(targetPos / m_iLocn);
		int iOldMean=window.mean();
		window.AddElem((int)(time * BINS_PER_SEC / bitrateThen)); //msec, except nats not bits);
		android.util.Log.d("DynamicLearn","Added push at offset "+curPos+" => target "+targetPos+"=time "+time+" => mean changed from "+iOldMean+" to "+window.mean());
	}
	
	/** Records info about some previous push of a button */
	private static class PushRec {
		/** The current location of the sentence that was besides
		 * the marker when the button was pushed, stored as the offset
		 * that would have to be applied to bring it to the center of the
		 * Y axis (i.e., 2048 less the dasherY that would appear there onscreen)
		 */
		int curPosn;
		/** Total nats entered since that push of the button (or since the corresponding Offset() was applied) */
		double natsSince;
		/** Bitrate at the time of that push */
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
	private static double NATS_TO_LEARN=Math.log(4096/30.0);//30 =~= line thickness in dasher coords (3 pixels)...
	
	/** Tell the marker that a call to {@link CDasherModel#Offset(int)} has been made.
	 * The marker needs to know this, to adjust its own records of previous
	 * pushes, and so to learn the user's push distribution. 
	 * @return Whether any change to the user distribution was made */
	public void NotifyOffset(int iOffset, double dNats) {
		//1. apply dNats growth and iOffset offset to all previous pushes...
		for (PushRec p=longest_push; p!=null; p=p.next) {
		    p.curPosn = (int)(p.curPosn * Math.exp(dNats) - iOffset);
		    p.natsSince += dNats;
		}
		
		//2. for previous pushes that were long enough ago...
		while (longest_push!=null && longest_push.natsSince >= NATS_TO_LEARN) {
			PushRec p = longest_push;
			if ((longest_push = longest_push.next)==null) prev_push=null;
			learn(p.curPosn, p.natsSince, p.bitrate);
			p.next=freeList; freeList=p;
		}
	}
	
	public void RecordPush(int iOffset, double dNatsAgo, double bitrate) {
		PushRec p;
		if (freeList!=null) {
			p=freeList;
			freeList = freeList.next;
		} else p=new PushRec();
		p.next=prev_push;
		prev_push=p;
		if (longest_push==null) longest_push=p;
		p.curPosn = (int)(Math.exp(-dNatsAgo)*m_iLocn) - iOffset;
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
				long y = (long)(2048-p.curPosn * e);
				pView.Dasherline(-100, y, -1000, y, 1, 2);
				int dThick = (int)(Math.exp(p.natsSince+dNats)*30);
				pView.DasherDrawRectangle(-300, y-dThick, -1000, y+dThick, 7, 0, 0);
				temp[0] = -100; temp[1] = y;
				pView.Dasher2Screen(temp);
				//and the adjustments'll only be (even roughly) right for standard left-to-right orientation...
				pView.Screen().DrawString(Double.toString(p.natsSince+dNats), (int)temp[0], (int)temp[1]-5, 10);
			}
		}
	}

}
