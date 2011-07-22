package dasher.applet;
import java.util.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import dasher.CCustomColours;
import dasher.CDasherInterfaceBase;
import dasher.CDasherScreen;
import dasher.CDasherView;

public class JDasherThread extends Thread implements CDasherScreen {

	private Image backbuffer;
	private Image frontbuffer;
	/**
	 * true: backbuffer needs painting so not ready to show; should show front
	 * false: backbuffer filled/painted; should be shown, and buffers flipped (and readyToPaint set)
	 */
	private boolean readyToPaint;
	private boolean frontbufferValid;
	private final CDasherInterfaceBase iface;
	private final Queue<Runnable> events = new LinkedList<Runnable>();
	
	private int width, height;

	/** Creates Renderer thread. However, not usable until a call to setSize is made. */
	public JDasherThread(CDasherInterfaceBase painter) {
		this.iface = painter;
	}
	
	public synchronized void addTasklet(Runnable t) {
		events.add(t);
		this.notifyAll();
	}
	
	public synchronized void setSize(int width, int height) {
		
		if(backbuffer!=null && this.width == width && this.height == height) {
			return;
		}
		frontbufferValid = false;
		readyToPaint = true;
		backbuffer = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		frontbuffer = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		this.width = width;
		this.height = height;
		events.add(new Runnable() {
			public void run() {
				iface.ChangeScreen(JDasherThread.this);
			}
		});
		this.notifyAll();
	}
	
	public synchronized Image getCurrentFrontbuffer() {
		
		while(!frontbufferValid) {
			try { wait();} catch(InterruptedException e) {}
		}
			
		readyToPaint = true;
		this.notifyAll();
		return frontbuffer;
		
	}
	
	public void run() {
		
		while(true) {
			Runnable task;
			synchronized (this) {
				while(true) {
					if (readyToPaint) {task=null;break;}
					if (!events.isEmpty()) {task = events.remove(); break;}
					try {
						wait();
					}
					catch(InterruptedException e) {
						return;
					}
				}
			}
					
			if (task!=null)
				task.run();
			else {//state was 0			
				paint = backbuffer.getGraphics();
				iface.NewFrame(System.currentTimeMillis());
				synchronized(this) {
					Image temp = frontbuffer;
					frontbuffer = backbuffer;
					backbuffer = temp;
					frontbufferValid = true;
					readyToPaint = false;
					this.notifyAll();
				}
			}
			
		}
		
	}
	

	/**
	 * Graphics context in which to draw 
	 */
	private Graphics paint;
	
	/**
	 * Custom colour scheme against which colour indices are resolved
	 */
	private Color[] colours;
		
	/**
	 * Map of known sizes of differing characters at different font sizes.
	 */
	private final HashMap<TextSize, CDasherView.Point> TextSizes = new HashMap<TextSize, CDasherView.Point>();
	
	/**
	 * Map from font sizes to the Font objects used to draw them.
	 */
	private final HashMap<Long, DasherFont> DrawFonts = new HashMap<Long, DasherFont>();;
	
	public int GetWidth() {return width;}
	
	public int GetHeight() {return height;}
	
	/**
	 * Method ignored; as we're drawing to Swing's provided surface, the actual displaying of the image will
	 * be taken care of for us.
	 */
	public void Display() {
		/* No need to do anything; we've been drawing to the content
		 * surface all along, and Swing will take care of showing it.
		 */
	}

	public void DrawCircle(int iCX, int iCY, int iR, int iFillColor, int iLineColor, int iLineWidth) {
				
		if(iFillColor!=-1) {
			setColour(iFillColor);
			paint.fillOval(iCX - iR, iCY - iR, iR, iR);		
		}
		if (iLineWidth>0) {
			setColour(iLineColor);
			paint.drawOval(iCX - iR, iCY - iR, iR, iR);
		}
	}

	
	/** Dasher specifies its co-ordinates like
	 * <pre>
	 * y2---------x2
	 * |           |
	 * |           |
	 * x1,y1-------/</pre>
	 * <p>Whereas Java wants
	 * <pre>
	 * 			width
	 * x,y---------------\
	 * |				 |
	 * |				 | height
	 * |				 |
	 * \-----------------/
	 * </pre><p>
	 * Therefore, I use (x1, y2) as the point to feed to Java, and calculate
	 * height and width.
	 * 
	 */
	
	public void DrawRectangle(int x1, int y1, int x2, int y2, int iFillColor, int iOutlineColour, int iThickness) {
		//at input, x2/y2 are opposite sides
		if (x2<x1) {
			System.out.println("Note: DrawRect x1 "+x1+" x2 "+x2);
			int temp=x1;
			x1 = x2;
			x2 = temp-x1;
		} else
			x2 = x2-x1;
		//x2 is now width
		if (y2<y1) {
			System.out.println("Note: DrawRect y1 "+y1+" y2 "+y2);
			int temp=y1;
			y1 = y2;
			y2 = temp-y1;
		} else
			y2=y2-y1;
		//y2 is now height
		if(iFillColor!=-1) {
			setColour(iFillColor);
			paint.fillRect(x1, y1, x2, y2);
		}
		
		if(iThickness>0) {
			setColour(iOutlineColour);
			paint.drawRect(x1, y1, x2, y2);
		}

		
	}

	
	/** Here be more trouble! Dasher is specifying its strings by
	 * the co-ordinates of the top-left corner of a rectangle in which
	 * the text will be drawn. So,
	 * <pre>
	 *   x,y------------\
	 *   | Some String  |
	 *   \--------------/
	 *  </pre><p>
	 *   Java's DrawString method however specifies the baseline of the
	 *   first character, which is the bottom in the case of non-descenders
	 *   such as a and b, but is not for descenders such as g.
	 *   <p>
	 *   Therefore we must figure out the height and adjust x appropriately
	 *   before drawing the string.
	 *   <p>
	 *   It's important to note also that some work in this direction is done
	 *   by DasherView calling the Screen's TextSize method. This supplies
	 *   a height and width of a given string in pixels, using a HashMap to
	 *   do so efficiently.
	 *
	 */
	
	public void DrawString(String string, int x1, int y1, long Size) {
				
		
		int thisOffset;
		
		if(DrawFonts.containsKey(Size)) {
			paint.setFont(DrawFonts.get(Size).font);
			thisOffset = DrawFonts.get(Size).drawOffset;
		}
		else {
			Font newFont = new Font("sans", 0, (int)Size);
			paint.setFont(newFont);
			FontMetrics fm = paint.getFontMetrics();
			thisOffset = fm.getAscent();
			
			DasherFont newDasherFont = new DasherFont(newFont, thisOffset, (int)Size);
			
			DrawFonts.put(Size, newDasherFont);
		}
			
		
		
		/* CSFS: Since it is necessary to generate lots of fonts in the course
		 * of Dashing AND I need to store a drawing offset for each one, 
		 * (although I may address this later by modifying the drawing code),
		 * I've adapted the hashmap method found in the GetFont method in
		 * Screen.inl to save work.
		 */
				
		paint.setColor(Color.BLACK);
		paint.drawString(string, x1, y1 + (thisOffset / 2));
	}
	
	public void Polygon(CDasherView.Point[] Points, int Number, int Color, int iWidth) {
		
		setColour(Color);
		int[] xs = new int[Points.length];
		int[] ys = new int[Points.length];
		for(int i = 0; i < xs.length; i++) {
			xs[i] = Points[i].x;
			ys[i] = Points[i].y;
		}
		
		paint.fillPolygon(xs, ys, xs.length);
		
		
	}
	
	public void drawLine(int x0, int y0, int x1, int y1, int iWidth, int Colour) {
			
		setColour(Colour);
		if (iWidth<1) return;
		if (iWidth>1) {
			
		}
		paint.drawLine(x0, y0, x1, y1);
	}

	public void SetColourScheme(CCustomColours ColourScheme) {
		colours = new Color[ColourScheme.GetNumColours()];
		for (int i=0; i<colours.length; i++)
			colours[i] = new Color(ColourScheme.GetRed(i), ColourScheme.GetGreen(i), ColourScheme.GetBlue(i));
		
	}

	
	/**
	 * Graphics.getFontMetrics().getStringBounds is used to determine a probable text size.
	 * <p>
	 * Results, referenced by a struct containing information about both the character and font size concerned,
	 * are stored in a HashMap for quick access in the future.
	 * <p>
	 * At present, StringBounds' returned answer is augmented by one pixel in the x direction in the interests
	 * of readability.
	 * 
	 * @param string String whose size we want to determine
	 * @param Size Font size to use
	 * 
	 * @return Point defining its size.
	 */
	public CDasherView.Point TextSize(String string, int Size) {
		
		TextSize testValue = new TextSize();
		testValue.glyph = string;
		testValue.size = Size;
		
		if(TextSizes.containsKey(testValue)) {
			return TextSizes.get(testValue);
		}
		else {
			paint.setFont(paint.getFont().deriveFont((float)Size));
			Rectangle2D newsize = paint.getFontMetrics().getStringBounds(string ,paint);
			CDasherView.Point newpoint = new CDasherView.Point(
					(int)newsize.getWidth() + 1,
					(int)newsize.getHeight());
			
			TextSizes.put(testValue, newpoint);
			
			// System.out.printf("Glyph %s at size %d (%d) has dimensions (%dx%d)%n", string, Size, paint.getFont().getSize(), newpoint.x, newpoint.y);
	
			return newpoint;
			
			
		}
		
	}

	/**
	 * Sets the current Graphics context's colour, interpreting
	 * a colour of -1 as the "default" colour 3.
	 * 
	 * @param iColour Colour to set
	 */
	private void setColour(int iColour) {
		if(iColour == -1) iColour = 3; // Special value used in Dasher, seems to mean 3.
		paint.setColor(colours[iColour]);
	}

	public void SendMarker(int iMarker) {
	
		// Stub: This method is, for the time being, useless since Display() and Blank() serve the same purpose.
		
	}
	
}

/**
 * Small struct representing a Font and detailing both its size
 * and the offset required to draw it using top-left co-ordinates
 * as opposed to Java's baseline co-ordinates.
 */
class DasherFont {
	/**
	 * Font
	 */
	Font font;
	
	/**
	 * Vertical offset from baseline to top-left corner in this font
	 */
	int drawOffset;
	
	/**
	 * Font size
	 */
	int size;
	
	public DasherFont(Font i_Font, int i_drawOffset, int iSize) {
		font = i_Font;
		drawOffset = i_drawOffset;
		size = iSize;
	}
}

/**
 * Small struct used to store a glyph and font-size pair in the
 * character-to-drawn-size map.
 */
class TextSize {
	/**
	 * Character(s)
	 */
	String glyph;
	
	/**
	 * Font size
	 */
	int size;
	
	/**
	 * Returns true if the String and font size both match.
	 */
	public boolean equals(Object otherone) {
		if(otherone == null) {
			return false;
		}
		if(otherone instanceof TextSize) {
			TextSize theother = (TextSize)otherone;
			return (this.glyph.equals(theother.glyph) && this.size == theother.size);
		}
		else {
			return false;
		}
	}
	
	/**
	 * Overridden to use the String's hashCode plus the size.
	 */
	public int hashCode() {
		return this.glyph.hashCode() + size;
	}
}
