package dasher.android;

import java.nio.Buffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import dasher.CCustomColours;
import dasher.CDasherScreen;
import dasher.EColorSchemes;
import dasher.CDasherView.Point;

import android.content.Context;
import android.opengl.GLSurfaceView;
import static javax.microedition.khronos.opengles.GL10.*;

public class DasherWidget extends GLSurfaceView implements CDasherScreen {
    private final AndroidDasherInterface intf;
    /* cached values from last call to {@link onSurfaceChanged} */
    private GL10 gl;
    private int width, height;
    private CCustomColours colourScheme;
    
	public DasherWidget(Context context, AndroidDasherInterface intf) {
		super(context);
		setDebugFlags(DEBUG_CHECK_GL_ERROR | DEBUG_LOG_GL_CALLS);
		setRenderMode(RENDERMODE_WHEN_DIRTY);
		this.intf=intf;
		setRenderer(new Renderer() {
			public void onDrawFrame(GL10 gl) {
				assert (DasherWidget.this.gl == gl);
				DasherWidget.this.intf.NewFrame(System.currentTimeMillis());
				//TODO, request redraw after an appropriate delay...
			}
		
			public void onSurfaceChanged(GL10 gl, int width, int height) {
				DasherWidget.this.width=width;
				DasherWidget.this.height=height;
				DasherWidget.this.gl=gl;
				DasherWidget.this.intf.ChangeScreen(DasherWidget.this);
			}
		
			public void onSurfaceCreated(GL10 gl, EGLConfig config) {
				// TODO Auto-generated method stub
			}
		});
		intf.ChangeScreen(this);
	}
	public void Blank() {
		// TODO Auto-generated method stub
		gl.glDisable(GL_TEXTURE_2D);
		gl.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
		gl.glClear(GL_COLOR_BUFFER_BIT);
	}

	public void Display() {
		// TODO Auto-generated method stub
		
	}

	public void DrawCircle(int iCX, int iCY, int iR, int iColour, boolean bFill) {
		// TODO Auto-generated method stub
		
	}

	public void DrawRectangle(int x1, int y1, int x2, int y2, int Color,
			int iOutlineColour, EColorSchemes ColorScheme,
			boolean bDrawOutline, boolean bFill, int iThickness) {
		gl.glDisable(GL_TEXTURE_2D);
        gl.glEnableClientState(GL_VERTEX_ARRAY);
        short sx1=(short)x1,sx2=(short)x2,sy1=(short)y1,sy2=(short)y2;
        if (bFill) {//(aFillColorIndex != -1) {
                gl.glColor4f(colourScheme.GetRed(Color),
                		colourScheme.GetGreen(Color),
                		colourScheme.GetBlue(Color),
                		1.0f);
                Buffer coords = ShortBuffer.wrap(new short[] {sx1,sy1, sx2,sy1, sx1,sy2, sx2,sy2});
                gl.glVertexPointer(2, GL_SHORT, 0, coords);
                gl.glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        }
        if (bDrawOutline) {//(iThickness>0) {
                if (iOutlineColour == -1) iOutlineColour = 3;
                gl.glColor4f(colourScheme.GetRed(iOutlineColour),
                		colourScheme.GetGreen(iOutlineColour),
                		colourScheme.GetBlue(iOutlineColour),
                		1.0f);
                gl.glLineWidth(iThickness);
                Buffer coords = ShortBuffer.wrap(new short[] {sx1,sy1, sx2,sy1, sx2,sy2, sx1,sy2});
                gl.glVertexPointer(2, GL_SHORT, 0, coords);
                gl.glDrawArrays(GL_LINE_LOOP, 0, 4);
        }
	}

	public void DrawString(String string, int x1, int y1, long Size) {
		// TODO Auto-generated method stub
		
	}

	public int GetHeight() {
		return height;
	}

	public int GetWidth() {
		return width;
	}

	public void Polygon(Point[] Points, int Number, int Color) {
		// TODO Auto-generated method stub
		
	}

	public void Polygon(Point[] Points, int Number, int Color, int iWidth) {
		// TODO Auto-generated method stub
		
	}

	public void Polyline(Point[] Points, int Number, int iWidth) {
		// TODO Auto-generated method stub
		
	}

	public void Polyline(Point[] Points, int Number, int iWidth, int Colour) {
		// TODO Auto-generated method stub
		
	}

	public void SendMarker(int iMarker) {
		// TODO Auto-generated method stub
		
	}

	public void SetColourScheme(CCustomColours ColourScheme) {
		// TODO Auto-generated method stub
		
	}

	public Point TextSize(String string, int Size) {
		// TODO Auto-generated method stub
		return null;
	}

}
