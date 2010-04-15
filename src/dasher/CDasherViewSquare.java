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

import java.util.ArrayList;
import java.util.Collection;

/**
 * An implementation (currently the one and only) of DasherView.
 * <p>
 * This draws all DasherNodes as a square aligned to the Y axis
 * and with sizes dependent on their probability.
 * <p>
 * The horizontal and vertical scales are both subject to non-linearity;
 * the x axis is linear at first and logarithmic after a given
 * point, and the y axis is linear in all places but with different
 * decreasing magnification factors towards the edges.
 */
public class CDasherViewSquare extends CDasherView {

	/**
	 * Compression factor
	 */
	protected long m_Y1;
	
	/**
	 * Y co-ordinate above which to apply compression
	 */
	protected long m_Y2;
	
	/**
	 * Y co-ordinate below which to apply compression
	 */
	protected long m_Y3;
		
	/**
	 * Converts a y co-ordinate according to this schema
	 * 
	 * @param y Raw y co-ordinate
	 * @return Converted y co-ordinate
	 */
	@Override public long ymap(long y) {
		if(y > m_Y2)
			return m_Y2 + (y - m_Y2) / m_Y1;
		else if(y < m_Y3)
			return m_Y3 + (y - m_Y3) / m_Y1;
		else
			return y;
	}
	
	/**
	 * Restores a y co-ordinate by unapplying non-linearity
	 * 
	 * @param ydash Converted y co-ordinate
	 * @return Original, raw y co-ordinate
	 */
	public long yunmap(long ydash) {
		if(ydash > m_Y2)
			return (ydash - m_Y2) * m_Y1 + m_Y2;
		else if(ydash < m_Y3)
			return (ydash - m_Y3) * m_Y1 + m_Y3;
		else
			return ydash;
	}
	
	/**
	 * Scale factor applied to the logarithmic portion of the
	 * x-axis nonlinearity. Formerly known as m_dXmpa.
	 */
	protected double m_dXMappingLogarithmicScaleFactor;
	
	/**
	 * X co-ordinate at which we switch from linear to logarithmic
	 * magnification. Formerly known as m_dXmpb.
	 */
	protected double m_dXMappingLogLinearBoundary;
	
	/**
	 * Scale factor applied to the linear portion of the 
	 * x-axis nonlinearity. Formerly known as m_dXmpc.
	 */	
	protected double m_dXMappingLinearScaleFactor;
		
	/**
	 * Height of our current screen in pixels
	 */
	protected int CanvasY;
	
	// Cached values for scaling
	/**
	 * Cached values for scaling
	 */
	protected long iLRScaleFactorX;
	
	/**
	 * Cached values for scaling
	 */
	protected long iLRScaleFactorY;
	
	/**
	 * Cached values for scaling
	 */
	protected long iTBScaleFactorX;
	
	/**
	 * Cached values for scaling
	 */
	protected long iTBScaleFactorY;
	
	/**
	 * The factor that scale factors are multipled by
	 */  
	protected long m_iScalingFactor;
	
	/**
	 * Top-left corner of the current visible region
	 */
	protected CDasherView.DPoint m_iDasherMin;
	
	/**
	 * Bottom-right corner of the current visible region
	 */
	protected CDasherView.DPoint m_iDasherMax;
	
	/**
	 * Cache of LP_TRUNCATION
	 */
	protected int lpTruncation;
	
	/**
	 * Cache of LP_TRUNCATION_TYPE
	 */
	protected int lpTruncationType;
	
	/**
	 * Cache of LP_NORAMLIZATON
	 */
	protected int lpNormalisation;
	
	/**
	 * Sole constructor. Creates a new view wrapping a specified
	 * screen, fires the ChangeScreen method to configure itself
	 * around said screen, creates a DelayedDraw object for
	 * String drawing, preloads our cached parameter values
	 * and sets certain hard-coded scaling factors, such as those
	 * used in the x-axis nonlinearity.
	 * <p>
	 * The specific values at present are:
	 * <p>
	 * Multiply all linear scaled values by 0.9, switch to logarithmic
	 * scaling from the midpoint of the axis, and multiply all log
	 * scaled values by 0.2.
	 * 
	 * @param EventHandler Event handler with which to register ourselves
	 * @param SettingsStore Settings repository to use
	 * @param DasherScreen Screen to wrap and use for primitive drawing
	 */
	public CDasherViewSquare(CEventHandler EventHandler, CSettingsStore SettingsStore, CDasherScreen DasherScreen)  {
		
		super(EventHandler, SettingsStore, DasherScreen);
		
		m_DelayDraw = new CDelayedDraw();
		ChangeScreen(DasherScreen);
		
		// TODO - Make these parameters
		// tweak these if you know what you are doing
		m_dXMappingLogarithmicScaleFactor = 0.2;                // these are for the x non-linearity
		m_dXMappingLogLinearBoundary = 0.5;
		m_dXMappingLinearScaleFactor = 0.9;
				
		double dY1 = 0.25;
		double dY2 = 0.95;
		double dY3 = 0.05;
		
		m_Y2 = (long)(dY2 * lpMaxY);
		m_Y3 = (long)(dY3 * lpMaxY);
		m_Y1 = (long)(1.0 / dY1);
	
		m_bVisibleRegionValid = false;
		
		lpTruncation = (int)SettingsStore.GetLongParameter(Elp_parameters.LP_TRUNCATION);
		lpTruncationType = (int)SettingsStore.GetLongParameter(Elp_parameters.LP_TRUNCATIONTYPE);
		lpNormalisation = (int)SettingsStore.GetLongParameter(Elp_parameters.LP_NORMALIZATION);
		
		// These results are cached to make co-ordinate transformations quicker.
		// We ought not to be caught out, as I have registered the class
		// to watch for changes to these parameters.
		
	}
	
	/**
	 * Method is called by the EventHandler when dispatching
	 * events.
	 * <p>
	 * This class responds to the follow parameter-change events:
	 * <p>
	 * <i>LP_REAL_ORIENTATION</i>: Invalidates our current visible region.
	 * <p>
	 * <i>LP_TRUNCATION, LP_TRUNCATION_TYPE, LP_NORMALIZATION</i>:
	 * Updates internally cached values of these parameters.
	 */
	public void HandleEvent(CEvent Event) {
		// Let the parent class do its stuff
		super.HandleEvent(Event);
		
		// And then interpret events for ourself
		if(Event instanceof CParameterNotificationEvent) {
			CParameterNotificationEvent Evt = (CParameterNotificationEvent)Event;
			
			if (Evt.m_iParameter == Elp_parameters.LP_REAL_ORIENTATION) {
				m_bVisibleRegionValid = false;
			}
			else if (Evt.m_iParameter == Elp_parameters.LP_TRUNCATION) {
				lpTruncation = (int)GetLongParameter(Elp_parameters.LP_TRUNCATION);
			}
			else if (Evt.m_iParameter == Elp_parameters.LP_TRUNCATIONTYPE) {
				lpTruncationType = (int)GetLongParameter(Elp_parameters.LP_TRUNCATIONTYPE);
			}
			else if (Evt.m_iParameter == Elp_parameters.LP_NORMALIZATION) {
				lpNormalisation = (int)GetLongParameter(Elp_parameters.LP_NORMALIZATION);
			}
		}
	}
	
	/**
	 * Draws a tree of nodes beginning at a given root.
	 * <p>
	 * This function will call our screen's Blank method, and then call
	 * RecursiveRender on the Node we wish to draw. Once the RecursiveRender
	 * is finished, it will call m_DelayDraw's Draw method to draw
	 * all strings which have been queued up.
	 * <p>
	 * Finally, it will draw the Crosshair.
	 * 
	 * @param Root Node at which to begin drawing
	 * @param iRootMin Bottom co-ordinate of the root node (in Dasher space)
	 * @param iRootMax Top co-ordinate of the root node (in Dasher space)
	 * @param vNodeList Collection which will be filled with drawn Nodes
	 * @param vDeleteList Collection which will be filled with undrawable Nodes
	 */
	public void Render(CDasherNode Root, long iRootMin, long iRootMax, ExpansionPolicy pol) {
		
		Screen().Blank();
		
		CDasherView.DRect visreg = VisibleRegion();
		
		RecursiveRender(Root, iRootMin, iRootMax, (int)visreg.maxX, pol);
		
		// DelayDraw the text nodes
		m_DelayDraw.Draw(Screen());
		
		Crosshair(GetLongParameter(Elp_parameters.LP_OX));  // add crosshair
	}
	
	
	/* CSFS: Heavily modified to get the new mostleft value out. I'm fairly sure this
	 * obeys the same semantics as the C++ version but this needs to be tested.
	 */
	
	/**
	 * Recursively renders a tree of Nodes beginning at a given root, based on the following procedure:
	 * <p>
	 * <ol><li>Calls RenderNode on this node, drawing it
	 * <li>Calls RenderGroups on this node, drawing the grouping boxes for our children
	 * <li>Recurses on each of our children in turn
	 * </ol><p>
	 * Of course, if our children are not yet present, we return
	 * after step one.
	 * <p>
	 * This method also takes care of filling our Collections of drawable
	 * and undrawable nodes, by catching NodeCannotBeDrawnExceptions in the latter
	 * case.
	 * <p>
	 * 'Shoving,' the process which keeps a child node's text from overlapping
	 * that of its parent, is also co-ordinated here; the new mostleft
	 * value returned by RenderNode is passed into the recursive
	 * calls which draw our children.
	 * 
	 * @param Render Node at which to begin rendering
	 * @param y1 Top y co-ordinate of this Node
	 * @param y2 Bottom y co-ordinate of this Node
	 * @param mostleft Shoving parameter; see above
	 * @param vNodeList Collection to fill with drawn, childless Nodes
	 * @param vDeleteList Collection to fill with undrawable Nodes
	 */
	public void RecursiveRender(CDasherNode Render, long y1, long y2, int mostleft, ExpansionPolicy pol) {
		
		// This method takes mostleft by VALUE.
		
		/* Step 1: Render *this* node */
		assert y2 >= y1;
		
		// TODO - Get sensible limits here (to allow for non-linearities)
		CDasherView.DRect visreg = VisibleRegion();
		
		// TODO - use new versions of functions
		
		//int top = Dasher2Screen(0, y1).y;
		//int bottom = Dasher2Screen(0, y2).y; 
		long iSize = ymap(y2) - ymap(y1);
		
		// Actual height in pixels
		int iHeight = (int)((iSize * CanvasY) / lpMaxY);
		
		if(iHeight <= 1 //too small to render
		   || (y1 > visreg.maxY) || (y2 < visreg.minY)) { //entirely offscreen
			// Node is not drawable - can be deleted straightaway...
			Render.Delete_children();
			Render.Alive(false);
			return;
		}
		
		//ok, render the node...
		long iDasherSize = (y2 - y1);
				
		if(lpTruncation == 0) {        // Regular squares
			DasherDrawRectangle(Math.min(iDasherSize,visreg.maxX), Math.min(y2,visreg.maxY), 0, Math.max(y1,visreg.minY), Render.m_iColour, -1, 0);
		} else {
			DasherTruncRect(y1, y2, iSize, Render.m_iColour);
		}
		
		//long iDasherAnchorX = (iDasherSize);
		
		if( Render.m_strDisplayText != null && Render.m_strDisplayText.length() > 0 )
			mostleft = (int)DasherDrawText(iDasherSize, y1, iDasherSize, y2, Render.m_strDisplayText, mostleft, Render.shove());
					
		/* If this node hasn't any children (yet), we're done */
		if(Render.ChildCount() == 0) {
			pol.pushNode(Render, (int)y1, (int)y2, true);
			return;
		}
		//else, it has children, so can be collapsed...
		pol.pushNode(Render, (int)y1, (int)y2, false);
		
		/* Step 3: Draw our child nodes */
		for(CDasherNode i : Render.Children()) {
			
			long newy1 = y1 + (iDasherSize * i.Lbnd()) / lpNormalisation;
			long newy2 = y1 + (iDasherSize * i.Hbnd()) / lpNormalisation;
			
			// FIXME - make the threshold a parameter
			
			if((newy2 - newy1 > 50) || (i.Alive())) {
				i.Alive(true);
				RecursiveRender(i, newy1, newy2, mostleft, pol);
			}
		}
		
		if (GetBoolParameter(Ebp_parameters.BP_OUTLINE_MODE)
				&& Render.outline()
				&& lpTruncation==0) {
			DasherDrawRectangle(Math.min(iDasherSize,visreg.maxX), Math.min(y2,visreg.maxY), 0, Math.max(y1,visreg.minY), -1, -1, 1);
		}
	}
	
	private void DasherTruncRect(long y1, long y2, long iSize, int Color) {
		int iDasherY = (int)lpMaxY;
		long iDasherSize = y2-y1;
		int iSpacing = iDasherY / 128;       // FIXME - assuming that this is an integer below
		
		int iXStart = 0;
		
		switch (lpTruncationType) {
		case 1:
			iXStart = (int)(iSize - iSize * lpTruncation / 200);
			break;
		case 2:
			iXStart = (int)(iSize - iSize * lpTruncation / 100);
			break;
		}
		
		int iTipMin = (int)((y2 - y1) * lpTruncation / (200) + y1);
		int iTipMax = (int)(y2 - (y2 - y1) * lpTruncation / (200));
		
		int iLowerMin = (int)(((y1 + 1) / iSpacing) * iSpacing);
		int iLowerMax = (((iTipMin - 1) / iSpacing) * iSpacing);
		
		int iUpperMin = (((iTipMax + 1) / iSpacing) * iSpacing);
		int iUpperMax = (int)(((y2 - 1) / iSpacing) * iSpacing);
		
		if(iLowerMin < 0)
			iLowerMin = 0;
		
		if(iLowerMax < 0)
			iLowerMax = 0;
		
		if(iUpperMin < 0)
			iUpperMin = 0;
		
		if(iUpperMax < 0)
			iUpperMax = 0;
		
		if(iLowerMin > iDasherY)
			iLowerMin = iDasherY;
		
		if(iLowerMax > iDasherY)
			iLowerMax = iDasherY;
		
		if(iUpperMin > iDasherY)
			iUpperMin = iDasherY;
		
		if(iUpperMax > iDasherY)
			iUpperMax = iDasherY;
		
		while(iLowerMin < y1)
			iLowerMin += iSpacing;
		
		while(iLowerMax > iTipMin)
			iLowerMax -= iSpacing;
		
		while(iUpperMin < iTipMax)
			iUpperMin += iSpacing;
		
		while(iUpperMax > y2)
			iUpperMax -= iSpacing;
		
		int iLowerCount = ((iLowerMax - iLowerMin) / iSpacing + 1);
		int iUpperCount = ((iUpperMax - iUpperMin) / iSpacing + 1);
		
		if(iLowerCount < 0)
			iLowerCount = 0;
		
		if(iUpperCount < 0)
			iUpperCount = 0;
		
		int iTotalCount = (int)(iLowerCount + iUpperCount + 6);
		
		long[] x = new long[iTotalCount];
		long[] y = new long[iTotalCount];
		
		// Weird duplication here is to make truncated squares possible too
		
		x[0] = 0;
		y[0] = y1;
		x[1] = iXStart;
		y[1] = y1;
		
		x[iLowerCount + 2] = iDasherSize;
		y[iLowerCount + 2] = iTipMin;
		x[iLowerCount + 3] = iDasherSize;
		y[iLowerCount + 3] = iTipMax;
		
		x[iTotalCount - 2] = iXStart;
		y[iTotalCount - 2] = y2;
		x[iTotalCount - 1] = 0;
		y[iTotalCount - 1] = y2;
		
		for(int i = (0); i < iLowerCount; ++i) {
			x[i + 2] = (iLowerMin + i * iSpacing - y1) * (iDasherSize - iXStart) / (iTipMin - y1) + iXStart;
			y[i + 2] = iLowerMin + i * iSpacing;
		}
		
		for(int j = (0); j < iUpperCount; ++j) {
			x[j + iLowerCount + 4] = (y2 - (iUpperMin + j * iSpacing)) * (iDasherSize - iXStart) / (y2 - iTipMax) + iXStart;
			y[j + iLowerCount + 4] = iUpperMin + j * iSpacing;
		}
		
		DasherPolygon(x, y, iTotalCount, Color);
	}
	
	/**
	 * Determines whether a node falls within our current visible
	 * region. This is determined by the simple expedient of calling
	 * VisibleRegion and comparing the passed co-ordinates.
	 * 
	 *  @param y1 Node's top y co-ordinate
	 *  @param y2 Node's bottom y co-ordinate
	 *  @return True if falls within visible region, False otherwise
	 */
	@Override
	public boolean NodeFillsScreen(long y1, long y2) {
		
		CDasherView.DRect visreg = VisibleRegion();
		
		return (y1 <= visreg.minY) && (y2 >= visreg.maxY ) && (y2-y1 >= visreg.maxX);
	}
	
	/** 
	 * Convert screen co-ordinates to dasher co-ordinates. This doesn't
	 * include the nonlinear mapping for eyetracking mode etc - it is
	 * just the inverse of the mapping used to calculate the screen
	 * positions of boxes etc.
	 * 
	 * @param iInputX Screen x co-ordinate
	 * @param iInputY Screen y co-ordinate
	 * @param b1D Should we use a simpler transform
	 * for 1D devices?
	 * @param bNonlinearity Should we use unapplyXMapping and m_ymap.unmap
	 * to reverse an applied nonlinearity?
	 * @return Point in Dasher space equivalent to the given Screen point
	 */	
	public CDasherView.DPoint Screen2Dasher(int iInputX, int iInputY, boolean b1D, boolean bNonlinearity) {
		
		// Things we're likely to need:
		
		long iDasherWidth = lpMaxY;
		long iDasherHeight = lpMaxY;
		
		int iScreenWidth = Screen().GetWidth();
		int iScreenHeight = Screen().GetHeight();
		
		if( b1D ) { // Special case for 1D mode...
			return new CDasherView.DPoint(
					iInputX * iDasherWidth / iScreenWidth,
					iInputY * iDasherHeight / iScreenHeight);
		}
		
		int eOrientation = realOrientation;
		
		long iScaleFactorX;
		long iScaleFactorY;
		
		CDasherView.DPoint scale = GetScaleFactor(eOrientation); // FIXME
		
		iScaleFactorX = scale.x;
		iScaleFactorY = scale.y;
		long rx,ry;
		switch(eOrientation) {
		case Opts.ScreenOrientations.LeftToRight:
			rx = iDasherWidth / 2 - ( iInputX - iScreenWidth / 2 ) * m_iScalingFactor / iScaleFactorX;
			ry = iDasherHeight / 2 + ( iInputY - iScreenHeight / 2 ) * m_iScalingFactor / iScaleFactorY;
		break;
		case Opts.ScreenOrientations.RightToLeft:
			rx = (iDasherWidth / 2 + ( iInputX - iScreenWidth / 2 ) * m_iScalingFactor/ iScaleFactorX);
			ry = (iDasherHeight / 2 + ( iInputY - iScreenHeight / 2 ) * m_iScalingFactor/ iScaleFactorY);
		break;
		case Opts.ScreenOrientations.TopToBottom:
			rx = (iDasherWidth / 2 - ( iInputY - iScreenHeight / 2 ) * m_iScalingFactor/ iScaleFactorY);
			ry = (iDasherHeight / 2 + ( iInputX - iScreenWidth / 2 ) * m_iScalingFactor/ iScaleFactorX);
		break;
		case Opts.ScreenOrientations.BottomToTop:
			rx = (iDasherWidth / 2 + ( iInputY - iScreenHeight / 2 ) * m_iScalingFactor/ iScaleFactorY);
			ry = (iDasherHeight / 2 + ( iInputX - iScreenWidth / 2 ) * m_iScalingFactor/ iScaleFactorX);
		break;
		default:
			throw new AssertionError();
		}
		
		// FIXME - disabled to avoid floating point
		if( bNonlinearity ) {
			rx = unapplyXMapping(rx);
			ry = (long)yunmap(ry);
		}
		
		return new CDasherView.DPoint(rx, ry);
	}
	
	/**
	 * Computes a set of scaling factors for use in transforming
	 * screen to Dasher co-ordinates and vice versa. This should
	 * be re-run any time the Screen's height or width are liable
	 * to have changed, or if we wish to change the size of the
	 * Dasher world's co-ordinate space.
	 */
	public void SetScaleFactor()
	{
		long iDasherWidth = lpMaxY;
		long iDasherHeight = iDasherWidth;
		
		int iScreenWidth = Screen().GetWidth();
		int iScreenHeight = Screen().GetHeight();
		
		// Try doing this a different way:
		
		long iDasherMargin = ( 300 ); // Make this a parameter
		
		long iMinX = ( 0-iDasherMargin );
		long iMaxX = ( iDasherWidth + iDasherMargin );
		long iMinY = ( 0 );
		long iMaxY = ( iDasherHeight );
		
		double dLRHScaleFactor;
		double dLRVScaleFactor;
		double dTBHScaleFactor;
		double dTBVScaleFactor;
		
		dLRHScaleFactor = iScreenWidth / (double)( iMaxX - iMinX );
		dLRVScaleFactor = iScreenHeight / (double)( iMaxY - iMinY );
		dTBHScaleFactor = iScreenWidth / (double)( iMaxY - iMinY );
		dTBVScaleFactor = iScreenHeight / (double)( iMaxX - iMinX );
		
		iLRScaleFactorX = (long)(Math.max(Math.min(dLRHScaleFactor, dLRVScaleFactor), dLRHScaleFactor / 4.0) * m_iScalingFactor);
		iLRScaleFactorY = (long)(Math.max(Math.min(dLRHScaleFactor, dLRVScaleFactor), dLRVScaleFactor / 4.0) * m_iScalingFactor);
		iTBScaleFactorX = (long)(Math.max(Math.min(dTBHScaleFactor, dTBVScaleFactor), dTBVScaleFactor / 4.0) * m_iScalingFactor);
		iTBScaleFactorY = (long)(Math.max(Math.min(dTBHScaleFactor, dTBVScaleFactor), dTBHScaleFactor / 4.0) * m_iScalingFactor);
	}
	
	/**
	 * Retrieves the x and y scaling factors relevant to our
	 * current orientation -- so iLRScaleFactorX and Y if we're
	 * in LeftToRight or RightToLeft modes, or their TB equivalents
	 * otherwise.
	 * 
	 * @param eOrientation Current orientation
	 * @return Dasher-world co-ordinate containing the width scaling
	 * factor as its x, and the height factor as its y.
	 */
	public CDasherView.DPoint GetScaleFactor(int eOrientation) {
		if(( eOrientation == Opts.ScreenOrientations.LeftToRight ) || ( eOrientation == Opts.ScreenOrientations.RightToLeft )) {
			return new CDasherView.DPoint(iLRScaleFactorX, iLRScaleFactorY);
		} else {
			return new CDasherView.DPoint(iTBScaleFactorX, iTBScaleFactorY);
		}
	}
	
	/**
	 * Converts Dasher co-ordinates to the Screen co-ordinate
	 * indicating the same location.
	 * <p>
	 * Applies non-linearities to the Dasher co-ordinates using
	 * applyXMapping and ymap, before scaling appropriate
	 * to our current screen orientation.
	 * 
	 * @param iDasherX Dasher x co-ordinate
	 * @param iDasherY Dasher y co-ordinate
	 * @return Screen point corresponding to this location
	 */
	public CDasherView.Point Dasher2Screen(long iDasherX, long iDasherY) {
		
		// Apply the nonlinearities
		
		
		// FIXME
		iDasherX = applyXMapping(iDasherX);
		iDasherY = ymap(iDasherY);
		
		
		// Things we're likely to need:
		
		long iDasherWidth = lpMaxY;
		long iDasherHeight = lpMaxY;
		
		int iScreenWidth = Screen().GetWidth();
		int iScreenHeight = Screen().GetHeight();
		
		long iScaleFactorX;
		long iScaleFactorY;
		
		CDasherView.DPoint scale = GetScaleFactor(realOrientation);
		iScaleFactorX = scale.x;
		iScaleFactorY = scale.y;

		int rx, ry;
		switch( realOrientation ) {
		case Opts.ScreenOrientations.LeftToRight:
			rx = (int)(iScreenWidth / 2 - ( iDasherX - iDasherWidth / 2 ) * iScaleFactorX / m_iScalingFactor);
			ry = (int)(iScreenHeight / 2 + ( iDasherY - iDasherHeight / 2 ) * iScaleFactorY / m_iScalingFactor);
		break;
		case Opts.ScreenOrientations.RightToLeft:
			rx = (int)(iScreenWidth / 2 + ( iDasherX - iDasherWidth / 2 ) * iScaleFactorX / m_iScalingFactor);
			ry = (int)(iScreenHeight / 2 + ( iDasherY - iDasherHeight / 2 ) * iScaleFactorY / m_iScalingFactor);
		break;
		case Opts.ScreenOrientations.TopToBottom:
			rx = (int)(iScreenWidth / 2 + ( iDasherY - iDasherHeight / 2 ) * iScaleFactorX / m_iScalingFactor);
			ry = (int)(iScreenHeight / 2 - ( iDasherX - iDasherWidth / 2 ) * iScaleFactorY / m_iScalingFactor);
		break;
		case Opts.ScreenOrientations.BottomToTop:
			rx = (int)(iScreenWidth / 2 + ( iDasherY - iDasherHeight / 2 ) * iScaleFactorX / m_iScalingFactor);
			ry = (int)(iScreenHeight / 2 + ( iDasherX - iDasherWidth / 2 ) * iScaleFactorY / m_iScalingFactor);
		break;
		default:
			throw new AssertionError();
		}
		
		return new CDasherView.Point(rx, ry);
	}
	
	/**
	 * Produces a rectangle showing the region of Dasher space
	 * which is currently visible on screen.
	 * <p>
	 * This is accomplished by the simple expedient of running
	 * Screen2Dasher against the minimum and maximum screen
	 * co-ordinates.
	 * <p>
	 * This function takes our current orientation into account
	 * in producing its answer.
	 * 
	 * @return Rectangle indicating the visible region.
	 */
	public CDasherView.DRect VisibleRegion() {
		
		if(!m_bVisibleRegionValid) {
			
					
			switch( realOrientation ) {
			case Opts.ScreenOrientations.LeftToRight:
				m_iDasherMin = Screen2Dasher(Screen().GetWidth(),0,false,true);
				m_iDasherMax = Screen2Dasher(0,Screen().GetHeight(),false,true);
			break;
			case Opts.ScreenOrientations.RightToLeft:
				m_iDasherMin = Screen2Dasher(0,0,false,true);
				m_iDasherMax = Screen2Dasher(Screen().GetWidth(),Screen().GetHeight(),false,true);
			break;
			case Opts.ScreenOrientations.TopToBottom:
				m_iDasherMin = Screen2Dasher(0,Screen().GetHeight(),false,true);
				m_iDasherMax = Screen2Dasher(Screen().GetWidth(),0,false,true);
			break;
			case Opts.ScreenOrientations.BottomToTop:
				m_iDasherMin = Screen2Dasher(0,0,false,true);
				m_iDasherMax = Screen2Dasher(Screen().GetWidth(),Screen().GetHeight(),false,true);
			break;
			}
			
			m_bVisibleRegionValid = true;
		}
		
		return new CDasherView.DRect(m_iDasherMin.x, m_iDasherMin.y,
				m_iDasherMax.x, m_iDasherMax.y);
	}
	

	/* CSFS: Removed functions which returned a single aspect of the visible region;
	 * it didn't seem any routine made use of them anymore.
	 */

	
	/** 
	 * Convert abstract 'input coordinates', which may or may not
	 * correspond to actual screen positions, depending on the settings,
	 * into dasher co-ordinates.
	 * <p>
	 * This should be done once initially, then we work in Dasher
	 * co-ordinates for everything else. Input co-ordinates will be
	 * assumed to range over the extent of the screen.
	 * <p>
	 * Internally, we work in three modes: Direct (ie mouse), 1D and Eyetracker.
	 * Essentially, we
	 * <ol>
	 * <li>Feed screen co-ordinates through Screen2Dasher with the
	 * appropriate flags (1D and Nonlinear as necessary)
	 * <li>Apply y co-ordinate scaling if this is a 1D device.
	 * </ol>
	 * 
	 * @param iInputX Input x co-ordinate
	 * @param iInputY Input y co-ordinate
	 * @param iType 0 if input co-ordinates are in pixels
	 * or 1 if in Dasher co-ordinates
	 * @return Dasher world point corresponding but not necessarily
	 * in the same place as this input point.
	 */
	public CDasherView.DPoint Input2Dasher(int iInputX, int iInputY, int iType) {
		// FIXME - need to incorporate one-button mode?
		boolean b1D = GetBoolParameter(Ebp_parameters.BP_NUMBER_DIMENSIONS);
		// First convert the supplied co-ordinates to 'linear' Dasher co-ordinates
			
		CDasherView.DPoint retval;
		
		switch (iType) {
		case 0:
			// Raw secreen coordinates
			
			retval = Screen2Dasher( iInputX, iInputY, b1D, !b1D);
			break;
		case 1:
			// Raw dasher coordinates
			retval = new CDasherView.DPoint(iInputX, iInputY);
			break;
		default:
			throw new AssertionError();
		}
		
		// Apply y scaling
		
		// TODO: Check that this is still doing something vaguely sensible - I think it isn't
		
		if(b1D) {
			if( GetLongParameter(Elp_parameters.LP_YSCALE) > 0 ) {
				
				double dYScale;
				
				if(( realOrientation == Opts.ScreenOrientations.LeftToRight ) || ( realOrientation == Opts.ScreenOrientations.RightToLeft )) {
					dYScale = Screen().GetHeight() / (double)(GetLongParameter(Elp_parameters.LP_YSCALE));
				}
				else {
					dYScale = Screen().GetWidth() / (double)(GetLongParameter(Elp_parameters.LP_YSCALE));
				}
				
				retval = new CDasherView.DPoint(retval.x,
						(long)((retval.y - lpMaxY/2) * dYScale + lpMaxY/2));
			}
		}
		
		return retval;
	}
	

	/**
	 * Truncate a set of co-ordinates so that they are on the screen
	 *   
	 * @param iX Screen x co-ordinate
	 * @param iY Screen y co-ordinate
	 * @return Truncated point
	 */
	public CDasherView.Point TruncateToScreen(int iX, int iY) {
		
		// I think that this function is now obsolete
		
		if(iX < 0)
			iX = 0;
		if(iX > Screen().GetWidth())
			iX = Screen().GetWidth();
			
		if(iY < 0)
			iY = 0;
		if(iY > Screen().GetHeight())
			iY = Screen().GetHeight();
		
		return new CDasherView.Point(iX, iY);
	}
	
	/**
	 * Gets the point in the Dasher world currently pointed
	 * to by our input device.
	 * <p>
	 * Internally this boils down to calling GetCoordinates
	 * with the appropriate flags and then feeding the results
	 * through Input2Dasher.
	 * @param Added Ignored, may be null
	 */
	public CDasherView.DPoint getInputDasherCoords() {
		
		// FIXME - rename this something more appropriate (all this really should do is convert the coordinates)
		
		// NOTE - we now ignore the values which are actually passed to the display
		
		// FIXME - Actually turn autocalibration on and off!
		// FIXME - AutoCalibrate should use Dasher co-ordinates, not raw mouse co-ordinates?
		// FIXME - Have I broken this by moving it before the offset is applied?
		// FIXME - put ymap stuff back in 
		
		// FIXME - optimise this
		
		long[] Coordinates = new long[GetCoordinateCount()];
		
		int iType = (GetCoordinates(Coordinates));
		
		int mousex, mousey;
		
		if(Coordinates.length == 1) {
			mousex = 0;
			mousey = (int)Coordinates[0];
		}
		else {
			mousex = (int)Coordinates[0];
			mousey = (int)Coordinates[1];
		}
		
		// Convert the input co-ordinates to dasher co-ordinates
		
		CDasherView.DPoint retval = Input2Dasher(mousex, mousey, iType);
		
		/* CSFS: As well as extensive replacement of functions which used 
		 * primitives by reference, I've removed code which saved co-ordinates
		 * to m_iDasherXCache as it appears it never gets referenced.
		 */
		
		return retval;
	}
	
	/**
	 * Draws a square highlighting the area between two given y co-ordinates.
	 * <p>
	 * Draws the square a different colour dependent on whether the
	 * 'active' flag is specified.
	 * <p>
	 * At present the colours are hard-coded: Colour 1 is used with
	 * a width of 3 for active, and colour 2 with a width of 1 for
	 * inactive.
	 * 
	 * @param iDasherMin Bottom y co-ordinate at which to align the square
	 * @param iDasherMax Top y co-ordinate at which to align the square
	 * @param bActive Draw this square in 'active' style?
	 */
	public void NewDrawGoTo(long iDasherMin, long iDasherMax, boolean bActive) {
		long iHeight = (iDasherMax - iDasherMin);
		
		int iColour;
		int iWidth;
		
		if(bActive) {
			iColour = 1;
			iWidth = 3;
		}
		else {
			iColour = 2;
			iWidth = 1;
		}
		
		CDasherView.Point[] p = new CDasherView.Point[4];
		
		p[0] = Dasher2Screen( 0, iDasherMin);
		p[1] = Dasher2Screen( iHeight, iDasherMin);
		p[2] = Dasher2Screen( iHeight, iDasherMax);
		p[3] = Dasher2Screen( 0, iDasherMax);
		
		Screen().Polyline(p, iWidth, iColour);
	}
	
//	TODO: Autocalibration should be in the eyetracker filter class
	
	/* CSFS: There were two functions here called ResetSum and ResetSumCounter
	 * I've removed them, their calls, and their relevant variables, since the
	 * Eclipse asserted these variables were only ever set and so were useless.
	 */
	
	/**
	 * Sets a new screen, invalidates our current visible region,
	 * and recalls SetScalingFactor, since the relationships between
	 * Dasher and Screen co-ordinates may well have changed at this
	 * point.
	 * <p>
	 * Also caches the Screen's current height in CanvasY.
	 *
	 * @param NewScreen New screen
	 */
	public void ChangeScreen(CDasherScreen NewScreen) {
		m_Screen = NewScreen;
		m_bVisibleRegionValid = false;
		//int Width = Screen().GetWidth();
		int Height = Screen().GetHeight();
		//CanvasX = 9 * Width / 10;
		// CanvasBorder = Width - CanvasX; REMOVED: redundant according to Eclipse.
		CanvasY = Height;
		m_iScalingFactor = 100000000;
		SetScaleFactor();
	}
	
	// INLINE FUNCTIONS (CDasherViewSquare.inl)
	

	/// Draw the crosshair

	/**
	 * Draws the Crosshair, with the vertical bar at a specified
	 * x co-ordinate and the horizontal bar halfway up the screen.
	 * <p>
	 * The horizontal bar is hard coded to run from 12/14(sx)
	 * to 17/14(sx). This will cause trouble if sx is zero, and
	 * there will be issues if sx is small since we're working
	 * with integers, not floating point.
	 * <p>
	 * This method could do with being rewritten.
	 * 
	 * @param sx X co-ordinate for the vertical bar. Not zero, or
	 * the horizontal bar will not correctly display at present.
	 */
	public void Crosshair(long sx) {
		long[] x = new long[2];
		long[] y = new long[2];

		// Vertical bar of crosshair

		/* CSFS: These used to use the 'old' get-visible-extent functions. Since I
		 * had deleted these, I have converted them to use the new version.
		 */

		CDasherView.DRect visreg = VisibleRegion();

		x[0] = sx;
		y[0] = visreg.minY;

		x[1] = sx;
		y[1] = visreg.maxY;

		if(GetBoolParameter(Ebp_parameters.BP_COLOUR_MODE) == true) {
			DasherPolyline(x, y, 2, 1, 5);
		}
		else {
			DasherPolyline(x, y, 2, 1, -1);
		}

		// Horizontal bar of crosshair

		x[0] = 12 * sx / 14;
		y[0] = lpMaxY / 2;

		x[1] = 17 * sx / 14;
		y[1] = lpMaxY / 2;

		if(GetBoolParameter(Ebp_parameters.BP_COLOUR_MODE) == true) {
			DasherPolyline(x, y, 2, 1, 5);
		}
		else {
			DasherPolyline(x, y, 2, 1, -1);
		}
	}

	/**
	 * Reverse the x co-ordinate nonlinearity.
	 * <p>
	 * For details of this non-linearity, see the constructor.
	 * 
	 * @param x Value to which the mapping should be unapplied
	 * @return Raw value
	 */
	public long unapplyXMapping(long lx) {
		double x = lx/(double)lpMaxY;
		if(x < m_dXMappingLogLinearBoundary * m_dXMappingLinearScaleFactor)
			x = x / m_dXMappingLinearScaleFactor;
		else
			x = m_dXMappingLogLinearBoundary - m_dXMappingLogarithmicScaleFactor + m_dXMappingLogarithmicScaleFactor * Math.exp((x / m_dXMappingLinearScaleFactor - m_dXMappingLogLinearBoundary) / m_dXMappingLogarithmicScaleFactor);
		return (long)(x * lpMaxY);
	}

	/**
	 * Apply the x co-ordinate nonlinearity.
	 * <p>
	 * For details of this non-linearity, see the constructor.
	 * 
	 * @param x Value to which the mapping should be applied
	 * @return Mapped value
	 */
	@Override public long applyXMapping(long lx) {
		double x = lx / (double)lpMaxY;
		if(x < m_dXMappingLogLinearBoundary)
			x = m_dXMappingLinearScaleFactor * x;
		else
			x = m_dXMappingLinearScaleFactor * (m_dXMappingLogarithmicScaleFactor * Math.log((x + m_dXMappingLogarithmicScaleFactor - m_dXMappingLogLinearBoundary) / m_dXMappingLogarithmicScaleFactor) + m_dXMappingLogLinearBoundary);
		return (long)(x * lpMaxY);
	}
}