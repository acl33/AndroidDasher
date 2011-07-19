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
 * Defines a screen which Dasher can use to draw graphics
 * primitives.
 */
public interface CDasherScreen {

	/**
	 * Gets the width, and hence maximum X screen co-ordinate.
	 * 
	 * @return width
	 */
	public abstract int GetWidth();
	
	/**
	 * Gets the height, and hence maximum Y screen co-ordinate.
	 * 
	 * @return height
	 */
	public abstract int GetHeight();
	
	/**
	 * Determines / guesses the size of a given string as drawn on this screen.
	 * <p>
	 * This is used in determining how much to displace other drawn
	 * strings in order to prevent them from overlapping.
	 * <p>
	 * This must be carried out by the screen because only the
	 * actual drawing surface can know how fonts appear when
	 * rendered.
	 * 
	 * @param string String whose size we wish to determine
	 * @param Size Font size in points
	 * @return Point specifying the width and height of this string.
	 */
	public abstract CDasherView.Point TextSize(String string, int Size);
	
	
	/**
	 * Draws a string
	 * 
	 * @param string String to draw
	 * @param x1 Top-left hand corner x co-ordinate
	 * @param y1 Top-left hand corner y co-ordinate
	 * @param Size Font size in points
	 */
	public abstract void DrawString(String string, int x1, int y1, long Size);
	
  /** Draw a filled rectangle
	* 
	* @param x1 top left of rectangle (x coordinate)
	* @param y1 top left corner of rectangle (y coordinate)
	* @param x2 bottom right of rectangle (x coordinate)
	* @param y2 bottom right of rectangle (y coordinate)
	* @param iFillColour fill colour to be used (numeric, -1 => don't fill)
	* @param iOutlineColour colour to draw outline (numeric, -1 => don't outline)
	* @param ColorScheme Which colourscheme is to be used
	* @param iThickness Line thickness for outline (<1 => don't outline)
	*/
	public abstract void DrawRectangle(int x1, int y1, int x2, int y2, int iFillColour, int iOutlineColour,int iThickness);
	
	/**
	 * Draw a circle
	 * 
	 * @param iCX X co-ordinate of the centre
	 * @param iCY Y co-ordinate of the centre
	 * @param iR Radius in pixels
	 * @param iFillColour Colour number to fill (-1 = don't fill)
         * @param iLineColour Colour number to outline (-1 = use default)
         * @param iLineWidth line width of outline (<=0 = don't outline)
	 */	
	public abstract void DrawCircle(int iCX, int iCY, int iR, int iFillColour, int iLineColour, int iLineWidth);
	
	/**
	 * Draws a line segment.
	 * @param x0 Starting x coordinate
	 * @param y0 Starting y coordinate
	 * @param x1 End x coordinate
	 * @param y1 End y coordinate
	 * @param iWidth Line width
	 * @param Colour Colour number. Use -1 if you don't want to specify a colour...
	 */
	public abstract void drawLine(int x0, int y0, int x1, int y1, int iWidth, int Colour);
	
   /**
	* Draw a filled polygon - given vertices and color id.
	* This is not (currently) used in standard Dasher. However, it could be very
	* useful in the future. Please implement unless it will be very difficult,
	* in which case make this function call Polyline.
	* Draw a filled polygon
	*
	* @param Points array of points defining the edge of the polygon
	* @param Number number of points in the array
	* @param fillColour colour of the polygon (numeric)
	* @param iOutlineColour colour for border (-1 => no border)
	* @param iWidth line width of border (<1 => no border)
	*/
	public abstract void Polygon(CDasherView.Point[] Points, int fillColour, int iOutlineColour, int iWidth);
	
	/**
	 * Sets the colour scheme to use
	 * 
	 * @param ColourScheme New colour scheme
	 */
	public abstract void SetColourScheme(CCustomColours ColourScheme);
	
}
