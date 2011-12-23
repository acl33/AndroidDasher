/*
  This file is part of JDasher.

  JDasher is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  JDasher is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with JDasher; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

  Copyright (C) 2006      Christopher Smowton <cs448@cam.ac.uk>

  JDasher is a port derived from the Dasher project; for information on
  the project see www.dasher.org.uk; for information on JDasher itself
  and related projects see www.smowton.net/chris

*/

package dasher;

import static dasher.Elp_parameters.LP_X_LIMIT_SPEED;
import static dasher.Elp_parameters.LP_MAX_BITRATE;
import static dasher.Elp_parameters.LP_FRAMERATE;

/**
 * Monitors the framerate by taking records every time CountFrame
 * is called; this is used to control how far to make Dasher move per frame.
 */
public class CFrameRate extends CDasherComponent {
/*private int logCount;	
private double logFrames;*/
	/**
	 * (Approximate) number of frames which we should take to move
	 * to the location under the mouse pointer; based on a decaying
	 * average of the current framerate.
	 */
	private int m_iSteps;                 // the 'Steps' parameter. See djw thesis.
	
	/**
	 * Number of bits which'd take us all the way to the x limit,
	 * i.e. the point at which we are entering data at the maximum speed.
	 */
	private double m_dBitsAtLimX;
	
	/**
	 * Number of frames elapsed
	 */
	private int m_iFrames=-1;
	
	/**
	 * Number of frames to allow to elapse before computing average framerate over that period
	 */
	private int m_iSamples=2;
	
	/**
	 * Time of first frame in period being measured
	 */
	private long m_iTime=0;
	
	/**
	 * Cache of the natural log of 2
	 */
	private static final double LN2 = Math.log(2.0);

	/** Cache of base-2-logarithm of 5, used in calculating iSteps */
	private static final double LOG_MAXY = Math.log(CDasherModel.MAX_Y);
 
	/**
	 * Gets m_iSteps; see its description
	 * 
	 * @return m_iSteps
	 */
	protected int Steps() {
		return m_iSteps;
	} 
	
	/**
	 * Creates a new framerate monitor; all initial values
	 * are currently hard coded in.
	 * <p>
	 * We start with a frame rate of 32FPS. 
	 */
	public CFrameRate(CDasherComponent creator) {
		super(creator);
		HandleEvent(LP_X_LIMIT_SPEED);
	}
	
	/**
	 * Insert a new frame and recompute the frame rate, if enough
	 * samples have been gathered.
	 * <p>
	 * If this would result in a Steps parameter of 0 it is set to 1.
	 * 
	 * @param Time Time at which the new frame began, as a UNIX timestamp.
	 */
	protected void CountFrame(long Time)
	{
		if (++m_iFrames == 0) {
			//First frame
			m_iTime = Time;
		}
		
//		compute framerate if we have sampled enough frames
		if(m_iFrames == m_iSamples) {
			if(Time - m_iTime < 50)
				m_iSamples++;             // increase sample size
			else if(Time - m_iTime > 80)
				m_iSamples = Math.max(2, m_iSamples-1);;
			
			//Calculate the framerate and reset sampling statistics
			// for the next sampling period
			if(Time - m_iTime != 0) {
				double dFrNow = m_iFrames * 1000.0 / (Time - m_iTime);
				SetLongParameter(LP_FRAMERATE, (long)(GetLongParameter(LP_FRAMERATE) + (dFrNow*100))/2);
				m_iTime = Time;
				m_iFrames = 0;
			}
			
			/*logFrames+=m_dFr;
			if (++logCount==20) {
				System.out.println("Framerate: "+logFrames/logCount);
				logFrames = logCount=0;
			}*/
		}
		
	}
	
	/**
	 * Clears our frame count. We'll start recounting/timing on the next frame.
	 */
	public void ResetFramecount() {
		m_iFrames = -1;
	}
	
	@Override public void HandleEvent(EParameters eParam) {
		if (eParam==LP_X_LIMIT_SPEED) {
			//log (MAX_Y / (2*LP_X_LIM))
			//= log(MAX_Y) - log(2) - log(LP_X)
	      m_dBitsAtLimX = (LOG_MAXY - LN2 - Math.log(GetLongParameter(LP_X_LIMIT_SPEED)))/LN2;
	      //fallthrough
		} else if (eParam!=LP_MAX_BITRATE && eParam!=LP_FRAMERATE) return;
	    // => for either LP_MAX_BITRATE or LP_FRAMERATE, fallthrough
		
	    //Calculate m_iSteps from the decaying-average framerate, as the number
	    // of steps that, at the X limit, will cause LP_MAX_BITRATE bits to be
	    // entered per second
	    m_iSteps = Math.max(1,(int)(GetLongParameter(LP_FRAMERATE)*m_dBitsAtLimX/GetLongParameter(LP_MAX_BITRATE)));
	}
}
