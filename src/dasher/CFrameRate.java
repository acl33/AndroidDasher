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

/**
 * Monitors the framerate by taking records every time CountFrame
 * is called; this is used to control how far to make Dasher move per frame.
 */
public class CFrameRate extends CDasherComponent {
/*private int logCount;	
private double logFrames;*/
	/**
	 * Last measured frame rate
	 */
	private double m_dFr;
	
	/**
	 * Target maximum rate of entering information in bits/sec
	 */
	private double m_dMaxbitrate;         // the maximum rate of entering information
	
	/**
	 * (Approximate) number of frames which we should take to move
	 * to the location under the mouse pointer; based on a decaying
	 * average of the current framerate.
	 */
	private int m_iSteps;                 // the 'Steps' parameter. See djw thesis.
	
	/**
	 * Hard limit on maximum amount of zoom per frame (based on the
	 * current / last measured framerate, rather than decaying average) 
	 */
	private double m_dRXmax;
	
	/**
	 * Number of frames elapsed
	 */
	private int m_iFrames;
	
	/**
	 * Number of frames to allow to elapse before computing average framerate over that period
	 */
	private int m_iSamples;
	
	/**
	 * Time of first frame in period being measured
	 */
	private long m_iTime;
	
	/**
	 * Cache of the natural log of 2
	 */
	private static final double LN2 = Math.log(2.0);

	/** Cache of base-2-logarithm of 5, used in calculating iSteps */
	private static final double LOG2_5 = -Math.log(0.2) / LN2;
 
	/**
	 * Gets the maximum amount by which to zoom in a single frame
	 */ 
	protected double maxZoom() {
	////// TODO: Eventually fix this so that it uses integer maths internally.
		return m_dRXmax;
	}
	
	/**
	 * Gets m_iSteps; see its description
	 * 
	 * @return m_iSteps
	 */
	protected int Steps() {
		return m_iSteps;
	} 
	
	/**
	 * Gets current frame rate
	 * 
	 * @return Frame rate (FPS)
	 */
	public double Framerate() {
		return m_dFr;
	} 
	
	// TODO: These two shouldn't be the same thing:
	
	/**
	 * Creates a new framerate monitor; all initial values
	 * are currently hard coded in.
	 * <p>
	 * We start with a frame rate of 32FPS. 
	 */
	public CFrameRate(CDasherInterfaceBase iface, CSettingsStore sets) {
		super(iface,sets);
		HandleEvent(new CParameterNotificationEvent(Elp_parameters.LP_MAX_BITRATE));
		m_dRXmax = 2;                 // only a transient effect
		m_iFrames = -1;
		m_iSamples = 1;
		
		// we dont know the framerate yet - play it safe by setting it high
		m_dFr = 1 << 5;
		
		// start off very slow until we have sampled framerate adequately
		m_iSteps = 2000;
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
				m_iSamples = Math.max(1, m_iSamples-1);;
			
			if(Time - m_iTime != 0) {
				m_dFr = m_iFrames * 1000.0 / (Time - m_iTime);
				m_iTime = Time;
				m_iFrames = 0;
			}
			/*logFrames+=m_dFr;
			if (++logCount==20) {
				android.util.Log.d("DasherCore","Framerate: "+logFrames/logCount);
				logFrames = logCount=0;
			}*/
			m_dRXmax = Math.exp(m_dMaxbitrate * LN2 / m_dFr);
			m_iSteps = (int)((m_iSteps + LOG2_5 * m_dFr / m_dMaxbitrate) / 2);
			
			// If the framerate slows to < 4 then we end up with steps < 1 ! 
			if(m_iSteps == 0)
				m_iSteps = 1;
			
		}
		
	}
	
	/**
	 * Clears our frame count. We'll start recounting/timing on the next frame.
	 */
	public void ResetFramecount() {
		m_iFrames = -1;
	}
	
	@Override public void HandleEvent(CEvent pEvent) {
		if (pEvent instanceof CParameterNotificationEvent) {
			CParameterNotificationEvent evt = (CParameterNotificationEvent)pEvent;
			if(evt.m_iParameter == Elp_parameters.LP_MAX_BITRATE
					|| evt.m_iParameter == Elp_parameters.LP_BOOSTFACTOR) {
				//both bitrate _and_ boostfactor are stored as %ages...
				m_dMaxbitrate = GetLongParameter(Elp_parameters.LP_MAX_BITRATE) * GetLongParameter(Elp_parameters.LP_BOOSTFACTOR) / 10000.0;
			}
		}
	}
}
