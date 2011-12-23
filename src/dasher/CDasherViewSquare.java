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
import java.util.HashMap;
import java.util.Map;

import dasher.Opts.ScreenOrientations;

import static dasher.CDasherModel.*;

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

	/** Compression factor */
	protected static final long m_Y1 = 4;
	
	/** Y co-ordinate above which to apply compression */
	protected static final long m_Y2 = (19*MAX_Y) / 20;
	
	/**  Y co-ordinate below which to apply compression */
	protected static final long m_Y3 = MAX_Y/20;
		
	/**
	 * Converts a y co-ordinate according to this schema
	 * 
	 * @param y Raw y co-ordinate
	 * @return Converted y co-ordinate
	 */
	private static long ymap(long y) {
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
	private static long yunmap(long ydash) {
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
	 * Cached values for scaling
	 */
	protected long m_iScaleFactorX, m_iScaleFactorY;
	
	/**
	 * The factor that scale factors are multipled by
	 */  
	protected static final long m_iScalingFactor = 100000000;
	
	/** X center after allowing for margin */
	protected int m_iCenterX;
	
	protected DRect visibleRegion;
	
	/** Cache of LP_MIN_NODE_SIZE_TEXT */
	protected int minNodeSizeText;
	
	/** Cache of BP_OUTLINE_MODE */
	protected boolean bOutline;
	
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
	public CDasherViewSquare(CDasherComponent creator, CDasherScreen DasherScreen, Opts.ScreenOrientations orient)  {
		
		super(creator, DasherScreen, orient);
		
		m_DelayDraw = new CDelayedDraw();
		
		minNodeSizeText = (int)GetLongParameter(Elp_parameters.LP_MIN_NODE_SIZE_TEXT);
		bOutline = GetBoolParameter(Ebp_parameters.BP_OUTLINE_MODE);
		
		SetScaleFactor();
		
		// These results are cached to make co-ordinate transformations quicker.
		// We ought not to be caught out, as I have registered the class
		// to watch for changes to these parameters.
		
	}
	
	@Override public void setOrientation(Opts.ScreenOrientations orient) {
		super.setOrientation(orient);
		visibleRegion = null;
		SetScaleFactor();
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
	public void HandleEvent(EParameters eParam) {
		// Let the parent class do its stuff
		super.HandleEvent(eParam);
		
		// And then interpret events for ourself
		if (eParam == Elp_parameters.LP_MIN_NODE_SIZE_TEXT) {
			minNodeSizeText = (int)GetLongParameter(Elp_parameters.LP_MIN_NODE_SIZE_TEXT);
		} else if (eParam == Ebp_parameters.BP_OUTLINE_MODE) {
			bOutline = GetBoolParameter(Ebp_parameters.BP_OUTLINE_MODE);
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
	public CDasherNode Render(CDasherNode Root, long iRootMin, long iRootMax, ExpansionPolicy pol, CDasherModel model) {
		m_model=model;
		
		CDasherView.DRect visreg = VisibleRegion();
		output = Root.Parent();
		if (output!=null) {
			//to left of Y axis in root node's parent colour...
			DasherDrawRectangle(visreg.maxX, visreg.minY, 0, visreg.maxY, output.m_iColour, -1, -1);
			//to right of Y axis in white...
			DasherDrawRectangle(0, visreg.minY, visreg.minX, visreg.maxY, 0, -1, -1);
		} else {
			//whole screen in white...
			Screen().DrawRectangle(0, 0, Screen().GetWidth(), Screen().GetWidth(),0, -1, -1);
		}
		
		this.pol = pol;
		int textedge;
		switch (getOrientation()) {
		case RIGHT_TO_LEFT: textedge = Screen().GetWidth(); break;
		case BOTTOM_TO_TOP: textedge = Screen().GetHeight(); break;
		default:
			textedge = 0;
		}
		RecursiveRender(Root, iRootMin, iRootMax, textedge);
		
		// DelayDraw the text nodes
		m_DelayDraw.Draw(Screen());
		
		Crosshair();  // add crosshair
		return output;
	}
	
	private CDasherNode output;
	private CDasherModel m_model;
	private ExpansionPolicy pol;
	
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
	 * 
	 * @param Render Node at which to begin rendering
	 * @param y1 Top y co-ordinate of this Node
	 * @param y2 Bottom y co-ordinate of this Node
	 * @param mostleft Minimum distance from high-dasher-X edge of screen, in <em>pixels</em>,
	 * to draw text. (When a recursive call is made, the value passed in here will be the low-dasher-X
	 * edge of the text that the caller just rendered.)
	 * @param parentColour colour index in which parent rect was drawn
	 */
	protected void RecursiveRender(CDasherNode Render, long y1, long y2, int mostleft) {
		// This method takes mostleft by VALUE.
		
		CDasherView.DRect visreg = VisibleRegion();
		
		//when only a single recursive call is required (and nothing more after that recursion completes),
		// iterating round this loop allows a "tail call"-like mechanism without using any more stack space.
		// (Stack space is limited on Android and the render-any-child-more-than-half-the-size-of-parent
		// could otherwise lead to very deep recursion.)
		tailcall: while (true) {
		
			/* Step 1: Render *this* node */
			assert y2 >= y1;
			
			//ok, render the node...
			long iDasherSize = (y2 - y1);
			{
				temp.init(Math.min(iDasherSize,visreg.maxX),Math.min(y2,visreg.maxY));
				Dasher2Screen(temp);
				int left=(int)temp.x, bottom=(int)temp.y;
				temp.init(0,Math.max(y1, visreg.minY));
				Dasher2Screen(temp);
				int right=(int)temp.x, top=(int)temp.y;
				switch (getOrientation()) {
				case TOP_TO_BOTTOM:
					{int temp = top; top = bottom; bottom = temp;}
					//and fallthrough
				case RIGHT_TO_LEFT:
				case BOTTOM_TO_TOP:
					{int temp = right; right = left; left = temp;}
				}
				if (Render.visible())
					Screen().DrawRectangle(left, top, right, bottom, Render.m_iColour, -1, bOutline ? 1 : 0);
		
				if( Render.m_strDisplayText.length() > 0 ) {
					int textedge = DrawText(left, top, right, bottom, mostleft, fontSize(iDasherSize), Render.m_strDisplayText);
					if (Render.shove()) mostleft=textedge;
				}
			}
			collapse: {
				if (output == Render.Parent()) {
			
					//we may be seen as well
					if (y1<CROSS_Y && y2>CROSS_Y && (y2-y1)>CROSS_X) {
						//we are also seen!
						m_model.Output(output=Render);
						//That immediately expands the node; and we don't let the policy see/collapse it, either.
						// Hence, we must set a cost, as every child's cost will be limited to < parent.
						Render.m_dCost = Double.MAX_VALUE;
						break collapse;
					}
				}
				//else - node not now under crosshair
				/* If this node hasn't any children (yet), we're done */
				if(Render.ChildCount() == 0) {
					pol.pushNode(Render, (int)y1, (int)y2, true);
					return;
				}
				//has children, & not under xhair, so can be collapsed
				pol.pushNode(Render, (int)y1, (int)y2, false);
			}
			
			//break here if now under crosshair => output => has children;
			//fallthrough to here if not under crosshair (so enqueued to collapse) but has children from before.
			if (Render.m_OnlyChildRendered != null) {
				CDasherNode child=Render.m_OnlyChildRendered;
				long newy1 = y1 + (iDasherSize * child.Lbnd()) / NORMALIZATION;
				long newy2 = y1 + (iDasherSize * child.Hbnd()) / NORMALIZATION;
				if ((y2-y1 < minNodeSizeText && newy2>visreg.minY && newy1<visreg.maxY) //too-small - but other children smaller still
						|| (newy1 <= visreg.minY && newy2 >= visreg.maxY)) { //covers entire y axis
					//only need to render this one child. Do it by looping round...
					y1=newy1; y2=newy2;
					Render=child;
					continue tailcall;
				}
				else Render.m_OnlyChildRendered = null;
			}
			/* Step 3: Draw our child nodes */
			long newy1 = y1, newy2;
			assert newy1 <= visreg.maxY;
			int i=0; final int j=Render.ChildCount();
			for(; i<j; i++, newy1=newy2) {
				CDasherNode ch = Render.ChildAtIndex(i);
					
				newy2 = y1 + (iDasherSize * ch.Hbnd()) / NORMALIZATION;
				if (newy2 < visreg.minY) {
					//not reached screen yet.
					m_model.Collapse(ch);
					//and loop round
				} else if (newy2 - newy1 > minNodeSizeText || ch.Range()>NORMALIZATION/2) {
					//definitely big enough to render
					RecursiveRender(ch, newy1, newy2, mostleft);
					if (newy2 >= visreg.maxY) {
						//remaining children offscreen
						if (newy1 <= visreg.minY) Render.m_OnlyChildRendered = ch; //and previous ones were too!
						break; 
					}
					//and loop round
				} else {
					//did not RecursiveRender, or store into bestCh.
					m_model.Collapse(ch);
					if (newy2 > visreg.maxY) break; //rest of children are offscreen
				}
			}
			//any remaining children are offscreen, and do not need rendering
			while (++i<j) m_model.Collapse(Render.ChildAtIndex(i));
			//node rendered, no tail call required, exit
			break;
			// (otherwise, would loop round the tail-call loop)
		}
	}
	
	private int DrawText(int left, int top, int right, int bottom, int textedge, int size, String sDisplayText) {
		
		CDasherView.Point textDimensions = ScreenTextSize(sDisplayText, size);

		// Position of text box relative to anchor depends on orientation
		
		int textleft,texttop; //in screen coordinates & screen orientation
		
		switch (getOrientation()) {
		case LEFT_TO_RIGHT:
			textedge = (textleft = Math.max(left,textedge)) + textDimensions.x;
			texttop = (top+bottom - textDimensions.y) / 2;
			break;
		case RIGHT_TO_LEFT:
			textedge = textleft = Math.min(right, textedge) - textDimensions.x;
			texttop = (top+bottom - textDimensions.y) / 2;
			break;
		case TOP_TO_BOTTOM:
			textleft = (left+right - textDimensions.x) / 2;
			textedge = (texttop = Math.max(top, textedge)) + textDimensions.y;
			break;
		case BOTTOM_TO_TOP:
			textleft = (left+right - textDimensions.x) / 2;
			textedge = texttop = Math.min(bottom, textedge) - textDimensions.y;
		break;
		default:
			throw new AssertionError();
		}
		
		// Actually draw the text. We use DelayDrawText as the text should
		// be overlayed once all of the boxes have been drawn.
		
		m_DelayDraw.DelayDrawText(sDisplayText, textleft, texttop, size);
		
		return textedge;
		
	}
	
	private final Map<Integer,Map<String,CDasherView.Point>> textSizes = new HashMap<Integer,Map<String,CDasherView.Point>>();
	
	private CDasherView.Point ScreenTextSize(String sText, int iSize) {
		Map<String,CDasherView.Point> strings = textSizes.get(iSize);
		if (strings == null) textSizes.put(iSize, strings = new HashMap<String, Point>());
		Point p = strings.get(sText);
		if (p==null) strings.put(sText, p = Screen().TextSize(sText, iSize));
		return p;
	}

	/**
	 * Compute the font size to use for rendering a node label.
	 * @param iDasherX Preferred text position, i.e. the extent of node (y2-y1, or max x) in dasher-coordinates.
	 * @return 11, 14 or 20 times the LP_FONT_SIZE parameter, depending on
	 * text position as a fraction of the way across the screen from the Y-axis. 
	 */
	protected int fontSize(long iDasherX) {
		// Compute font size based on position
		
		// FIXME - this could be much more elegant, and probably needs a
		// rethink anyway - behvaiour here is too dependent on screen size
		
		long iLeftTimesFontSize = iDasherX *lpFontSize;
		
		if (iLeftTimesFontSize > MAX_Y/20)
			return lpFontSize*20;
		else if (iLeftTimesFontSize > MAX_Y/160)
			return lpFontSize*14;
		else
			return lpFontSize*11;
	}
	
	private final MutablePoint temp=new MutablePoint(), temp2=new MutablePoint();
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
	 * @return Point in Dasher space equivalent to the given Screen point
	 */	
	@Override
	public void Screen2Dasher(MutablePoint coords) {
		
		// Things we're likely to need:
		
		int iScreenWidth = Screen().GetWidth();
		int iScreenHeight = Screen().GetHeight();
		
		long rx,ry;
		switch(getOrientation()) {
		case LEFT_TO_RIGHT:
			rx = m_iCenterX - ( coords.x - iScreenWidth / 2 ) * m_iScalingFactor / m_iScaleFactorX;
			ry = MAX_Y / 2 + ( coords.y - iScreenHeight / 2 ) * m_iScalingFactor / m_iScaleFactorY;
		break;
		case RIGHT_TO_LEFT:
			rx = (m_iCenterX + ( coords.x - iScreenWidth / 2 ) * m_iScalingFactor/ m_iScaleFactorX);
			ry = (MAX_Y / 2 + ( coords.y - iScreenHeight / 2 ) * m_iScalingFactor/ m_iScaleFactorY);
		break;
		case TOP_TO_BOTTOM:
			rx = (m_iCenterX - ( coords.y - iScreenHeight / 2 ) * m_iScalingFactor/ m_iScaleFactorY);
			ry = (MAX_Y / 2 + ( coords.x - iScreenWidth / 2 ) * m_iScalingFactor/ m_iScaleFactorX);
		break;
		case BOTTOM_TO_TOP:
			rx = (m_iCenterX + ( coords.y - iScreenHeight / 2 ) * m_iScalingFactor/ m_iScaleFactorY);
			ry = (MAX_Y / 2 + ( coords.x - iScreenWidth / 2 ) * m_iScalingFactor/ m_iScaleFactorX);
		break;
		default:
			throw new AssertionError();
		}
		
		coords.init(unapplyXMapping(rx),yunmap(ry));
	}
	
	/**
	 * Computes a set of scaling factors for use in transforming
	 * screen to Dasher co-ordinates and vice versa. This should
	 * be re-run any time the Screen's height or width are liable
	 * to have changed, or if we wish to change the size of the
	 * Dasher world's co-ordinate space.
	 * 
	 * Also computes the screen coordinates of the crosshair - with the vertical bar at LP_OX 
	 * (and the horizontal bar halfway up the screen).
	 * The horizontal bar is hard coded to run from 12/14(sx)
	 * to 17/14(sx). This will cause trouble if sx is zero, and
	 * there will be issues if sx is small since we're working
	 * with integers, not floating point
	 */
	public void SetScaleFactor()
	{
		//Default values for x non-linearity (TODO - Make these parameters)
		m_dXMappingLogLinearBoundary = 0.5; //threshold: DasherX's less than (that * MAX_Y) are linear...
		m_dXMappingLinearScaleFactor = 0.9; //...but multiplied by that; DasherX's above that, are logarithmic...

		//set log scaling coefficient (unused if LP_NONLINEAR_X==0)
		// note previous value of m_dXmpa = 0.2, i.e. a value of LP_NONLINEAR_X =~= 4.8
		m_dXMappingLogarithmicScaleFactor = Math.exp(GetLongParameter(Elp_parameters.LP_NON_LINEAR_X)/-3.0);
		
		//long iDasherWidth = MAX_Y;
		//long iDasherHeight = iDasherWidth;
		
		int iScreenWidth = Screen().GetWidth();
		int iScreenHeight = Screen().GetHeight();
		
		// Try doing this a different way:
		
		final long iDasherMargin = GetLongParameter(Elp_parameters.LP_DASHER_MARGIN);
		
		final long iMinX = ( 0-iDasherMargin );
		final long iMaxX = ( MAX_Y );
		m_iCenterX = (int)((iMinX + iMaxX) / 2);
		final long iMinY = ( 0 );
		final long iMaxY = ( MAX_Y );
		
		double dScaleFactorX, dScaleFactorY;
		
		if (getOrientation().isHorizontal) {
			dScaleFactorX = iScreenWidth / (double)( iMaxX - iMinX );
			dScaleFactorY = iScreenHeight / (double)( iMaxY - iMinY );
		} else {
			dScaleFactorX = iScreenHeight / (double)( iMaxX - iMinX );
			dScaleFactorY = iScreenWidth / (double)( iMaxY - iMinY );
		}

		if (dScaleFactorX < dScaleFactorY) {
		    //fewer (pixels per dasher coord) in X direction - i.e., X is more compressed.
		    //So, use X scale for Y too...except first, we'll _try_ to reduce the difference
		    // by changing the relative scaling of X and Y (by at most 20%):
		    double dMul = Math.max(0.8, dScaleFactorX / dScaleFactorY);
		    m_dXMappingLinearScaleFactor *= dMul;
		    dScaleFactorX /= dMul;
		    m_iScaleFactorX = (long)(dScaleFactorX * m_iScalingFactor);
		    m_iScaleFactorY = (long)(Math.max(dScaleFactorX, dScaleFactorY / 4.0) * m_iScalingFactor);
		} else {
		    //X has more room; use Y scale for both -> will get lots history
		    m_iScaleFactorX = (long)(Math.max(dScaleFactorY, dScaleFactorX / 4.0) * m_iScalingFactor);
		    m_iScaleFactorY = (long)(dScaleFactorY * m_iScalingFactor);
		    // however, "compensate" by relaxing the default "relative scaling" of X
		    // (normally only 90% of Y) towards 1...
		    m_dXMappingLinearScaleFactor = Math.min(1.0,0.9 * dScaleFactorX / dScaleFactorY);
		}
		m_iCenterX *= m_dXMappingLinearScaleFactor;
		
		// Vertical bar of crosshair

		CDasherView.DRect visreg = VisibleRegion();
		
		cross_v0 = Dasher2Screen(CROSS_X, visreg.minY);
		cross_v1 = Dasher2Screen(CROSS_X, visreg.maxY);
		
		//Horizontal bar

		cross_h0 = Dasher2Screen(12 * CROSS_X / 14, CROSS_Y);
		cross_h1 = Dasher2Screen(17*CROSS_X/14, CROSS_Y);
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
	public void Dasher2Screen(MutablePoint coords) {
		
		// Apply the nonlinearities
		
		
		coords.init(applyXMapping(coords.x),ymap(coords.y));
		
		
		// Things we're likely to need:
		
		int iScreenWidth = Screen().GetWidth();
		int iScreenHeight = Screen().GetHeight();

		switch( getOrientation() ) {
		case LEFT_TO_RIGHT:
			coords.init((iScreenWidth / 2 - ( coords.x - m_iCenterX ) * m_iScaleFactorX / m_iScalingFactor),
						(iScreenHeight / 2 + ( coords.y - MAX_Y / 2 ) * m_iScaleFactorY / m_iScalingFactor));
		break;
		case RIGHT_TO_LEFT:
			coords.init((iScreenWidth / 2 + ( coords.x - m_iCenterX ) * m_iScaleFactorX / m_iScalingFactor),
						(iScreenHeight / 2 + ( coords.y - MAX_Y / 2 ) * m_iScaleFactorY / m_iScalingFactor));
		break;
		case TOP_TO_BOTTOM: {
			coords.init((iScreenWidth / 2 + ( coords.y - MAX_Y / 2 ) * m_iScaleFactorX / m_iScalingFactor),
						(iScreenHeight / 2 - ( coords.x - m_iCenterX ) * m_iScaleFactorY / m_iScalingFactor));
			break;
		}
		case BOTTOM_TO_TOP: {
			coords.init((iScreenWidth / 2 + ( coords.y - MAX_Y / 2 ) * m_iScaleFactorX / m_iScalingFactor),
						(iScreenHeight / 2 + ( coords.x - m_iCenterX ) * m_iScaleFactorY / m_iScalingFactor));
			break;
		}
		default:
			throw new AssertionError();
		}
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
		
		if(visibleRegion==null) {
			final MutablePoint min=temp,max=temp2;
			switch( getOrientation() ) {
			case LEFT_TO_RIGHT:
				min.init(Screen().GetWidth(),0);
				max.init(0,Screen().GetHeight());
			break;
			case RIGHT_TO_LEFT:
			case BOTTOM_TO_TOP:
				min.init(0,0);
				max.init(Screen().GetWidth(),Screen().GetHeight());
			break;
			case TOP_TO_BOTTOM:
				min.init(0,Screen().GetHeight());
				max.init(Screen().GetWidth(),0);
			break;
			default:
				throw new IllegalArgumentException("Unknown Orientation "+getOrientation());
			}
			Screen2Dasher(min);
			Screen2Dasher(max);
			visibleRegion = new CDasherView.DRect(min.x, min.y, max.x, max.y);
		}
		
		return visibleRegion; 
	}
	
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
		super.ChangeScreen(NewScreen);
		textSizes.clear();
		visibleRegion = null;
		SetScaleFactor();
	}
	
	// INLINE FUNCTIONS (CDasherViewSquare.inl)
	

	/// Draw the crosshair

	/**
	 * Draws the Crosshair, as computed in {@link #SetScaleFactor()}
	 */
	private void Crosshair() {
		int iColour = GetBoolParameter(Ebp_parameters.BP_COLOUR_MODE) ? 5 : -1;
		Screen().drawLine(cross_h0.x, cross_h0.y, cross_h1.x, cross_h1.y, 1, iColour);
		Screen().drawLine(cross_v0.x, cross_v0.y, cross_v1.x, cross_v1.y, 1, iColour);
	}
	/** Cache screen coordinates of the vertical & horizontal lines forming the crosshair */
	private Point cross_v0, cross_v1, cross_h0, cross_h1;
	
	/**
	 * Reverse the x co-ordinate nonlinearity.
	 * <p>
	 * For details of this non-linearity, see the constructor.
	 * 
	 * @param x Value to which the mapping should be unapplied
	 * @return Raw value
	 */
	private long unapplyXMapping(long lx) {
		double x = lx/(double)MAX_Y;
		if(x < m_dXMappingLogLinearBoundary * m_dXMappingLinearScaleFactor)
			x = x / m_dXMappingLinearScaleFactor;
		else
			x = m_dXMappingLogLinearBoundary - m_dXMappingLogarithmicScaleFactor + m_dXMappingLogarithmicScaleFactor * Math.exp((x / m_dXMappingLinearScaleFactor - m_dXMappingLogLinearBoundary) / m_dXMappingLogarithmicScaleFactor);
		return (long)(x * MAX_Y);
	}

	/**
	 * Apply the x co-ordinate nonlinearity.
	 * <p>
	 * For details of this non-linearity, see the constructor.
	 * 
	 * @param x Value to which the mapping should be applied
	 * @return Mapped value
	 */
	private long applyXMapping(long lx) {
		double x = lx / (double)MAX_Y;
		if(x < m_dXMappingLogLinearBoundary)
			x = m_dXMappingLinearScaleFactor * x;
		else
			x = m_dXMappingLinearScaleFactor * (m_dXMappingLogarithmicScaleFactor * Math.log((x + m_dXMappingLogarithmicScaleFactor - m_dXMappingLogLinearBoundary) / m_dXMappingLogarithmicScaleFactor) + m_dXMappingLogLinearBoundary);
		return (long)(x * MAX_Y);
	}
}
