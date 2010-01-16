package dasher.android;

import java.nio.Buffer;
import java.nio.ByteBuffer;
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
		this.intf=intf;
		setRenderer(new Renderer() {
			public void onDrawFrame(GL10 gl) {
				assert (DasherWidget.this.gl == gl);
				DasherWidget.this.intf.NewFrame(System.currentTimeMillis());
				requestRender();
			}
		
			public void onSurfaceChanged(GL10 gl, int width, int height) {
				gl.glViewport(0,0, width, height);
				gl.glMatrixMode(GL_PROJECTION);
			    gl.glLoadIdentity();
			    gl.glOrthox(0, width, height, 0, -1, 1);
			    DasherWidget.this.width=width;
				DasherWidget.this.height=height;
				DasherWidget.this.gl=gl;
				DasherWidget.this.intf.ChangeScreen(DasherWidget.this);
			}
		
			public void onSurfaceCreated(GL10 gl, EGLConfig config) {
				gl.glShadeModel(GL_FLAT);
				gl.glEnable(GL_BLEND);
				gl.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
				gl.glMatrixMode(GL_MODELVIEW);
				gl.glLoadIdentity();
			}
		});
		setRenderMode(RENDERMODE_WHEN_DIRTY);
		//intf.ChangeScreen(this);
	}
	public void Blank() {
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

	private ByteBuffer buf = ByteBuffer.allocate(16);
	public void DrawRectangle(int x1, int y1, int x2, int y2, int iFillColour,
			int iOutlineColour, EColorSchemes ColorScheme,
			int iThickness) {
		gl.glDisable(GL_TEXTURE_2D);
        gl.glEnableClientState(GL_VERTEX_ARRAY);
        short sx1=(short)x1,sx2=(short)x2,sy1=(short)y1,sy2=(short)y2;
        if (iFillColour != -1) {
                gl.glColor4f(colourScheme.GetRed(iFillColour),
                		colourScheme.GetGreen(iFillColour),
                		colourScheme.GetBlue(iFillColour),
                		1.0f);
                buf.clear();
                buf.putShort(sx1); buf.putShort(sy1);
                buf.putShort(sx2); buf.putShort(sy1);
                buf.putShort(sx1); buf.putShort(sy2);
                buf.putShort(sx2); buf.putShort(sy2);
                buf.flip();
                gl.glVertexPointer(2, GL_SHORT, 0, buf);
                gl.glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        }
        if (iThickness>0) {
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
        gl.glDisableClientState(GL_VERTEX_ARRAY);
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

	public void Polygon(Point[] Points, int iFillColour, int iOutlineColour, int iWidth) {
		// TODO Auto-generated method stub
		
	}

	public void Polyline(Point[] Points, int iWidth, int Colour) {
		// TODO Auto-generated method stub
		
	}

	public void SendMarker(int iMarker) {
		// TODO Auto-generated method stub
		
	}

	public void SetColourScheme(CCustomColours colourScheme) {
		this.colourScheme = colourScheme;
		
	}

	public Point TextSize(String string, int Size) {
		// TODO Auto-generated method stub
		return new Point(0,0);
	}

}
