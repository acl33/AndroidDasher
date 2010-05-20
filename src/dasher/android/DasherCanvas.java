package dasher.android;

import dasher.CCustomColours;
import dasher.CDasherInput;
import dasher.CDasherScreen;
import dasher.CInputFilter;
import dasher.CDasherView.Point;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Style;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.SurfaceHolder.Callback;

public class DasherCanvas extends SurfaceView implements Callback {
	private final ADasherInterface intf;
	private final RenderTask rTh;
    private boolean animating, bReady;
    private int x,y;
    
	public DasherCanvas(Context context, ADasherInterface intf) {
		super(context);
		if (intf==null) throw new NullPointerException();//just do it now!
		this.intf=intf;
		SurfaceHolder holder = getHolder();
		holder.addCallback(this);
		rTh=new RenderTask(holder);
	}

	protected void onMeasure(int widthMS, int heightMS) {
		Log.d("DasherIME","onMeasure ("+MeasureSpec.toString(widthMS)+","+MeasureSpec.toString(heightMS)+")");
		int w = ms(widthMS,480),h = ms(heightMS,600);
		setMeasuredDimension(w,h);
	}
	
	private int ms(int mSpec, int pref) {
		switch (MeasureSpec.getMode(mSpec)) {
		case MeasureSpec.AT_MOST:
			return Math.min(MeasureSpec.getSize(mSpec),pref);
		case MeasureSpec.UNSPECIFIED:
			return pref;
		case MeasureSpec.EXACTLY:
			return MeasureSpec.getSize(mSpec);
		default:
			throw new IllegalArgumentException("Invalid MeasureSpec "+mSpec);
		}
	}
	
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		Log.d("DasherIME","surfaceChanged ("+width+", "+height+")");
		intf.enqueue(new Runnable() {
			public void run() {
				intf.ChangeScreen(rTh);
				synchronized(DasherCanvas.this) {
					if (bReady || !animating) return;
				}
				bReady = true;
				intf.enqueue(rTh);
			}
		});
	}

	public void surfaceCreated(SurfaceHolder holder) {
		// TODO Auto-generated method stub
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.d("DasherIME","surfaceDestroyed");
		stopAnimating();
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent e) {
		x=(int)e.getX(); y=(int)e.getY();
		switch (e.getAction()) {
		case MotionEvent.ACTION_DOWN:
			intf.KeyDown(System.currentTimeMillis(), 100);
		case MotionEvent.ACTION_MOVE:
			break;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_OUTSIDE:
		case MotionEvent.ACTION_CANCEL:
			intf.KeyUp(System.currentTimeMillis(), 100);
			x=y=-1;
			break;
		}
		return true;
	}
	
	/* Gets (screen/pixel) x,y coordinates of last touch event*/
	public void GetCoordinates(long[] coords) {
		if (coords.length!=2) throw new IllegalArgumentException("Coordinate array must have exactly two elements");
		coords[0]=x;
		coords[1]=y;
	}
	
	public void requestRender() {
		synchronized(this) {
			if (animating || !bReady) return;
		}
		intf.enqueue(rTh);
	}
	
	public void startAnimating() {
		//or, enqueue a runnable?
		synchronized(this) { 
			if (animating) return;
			animating = true;
			if (!bReady) return; //can't do any more
		}
		intf.enqueue(rTh);
	}
	
	public void stopAnimating() {
		//or, enqueue a runnable?
		synchronized(this) {
			animating = false;
		}
	}
	
	private class RenderTask implements Runnable,CDasherScreen {
		private final SurfaceHolder holder;
		private Canvas canvas;
		private CCustomColours colours;
		
		/** Single Paint we'll use for everything - i.e. by changing
		 * all its parameters for each primitive.
		 * TODO: think about having multiple Paint objects caching different
		 * sets of parameters... 
		 */
		private Paint p = new Paint();
		/** Use a single Rect object for every rectangle too, avoiding allocation...*/
		private Rect r = new Rect();
		private RenderTask(SurfaceHolder holder) {
			this.holder=holder;
		}
		
		public void run() {
			synchronized(DasherCanvas.this) {
				if (!animating) return;
			}
			canvas = holder.lockCanvas();
			intf.NewFrame(System.currentTimeMillis());
			holder.unlockCanvasAndPost(canvas);
			canvas=null;
			intf.enqueue(this);
		}
		
		public void Blank() {
			canvas.drawARGB(255, 255, 255, 255);
		}
		public void DrawCircle(int iCX, int iCY, int iR, int iColour,
				boolean bFill) {
			p.setARGB(255, colours.GetRed(iColour), colours.GetGreen(iColour), colours.GetBlue(iColour));
			p.setStyle(bFill ? Style.FILL_AND_STROKE : Style.STROKE);
			canvas.drawCircle(iCX, iCY, iR, p);
		}
		public void DrawRectangle(int x1, int y1, int x2, int y2,
				int iFillColour, int iOutlineColour,
				int iThickness) {
			r.left = x1; r.right = x2;
			r.top = y1; r.bottom = y2;
			if (iFillColour != -1) {
				p.setARGB(255, colours.GetRed(iFillColour), colours.GetGreen(iFillColour), colours.GetBlue(iFillColour));
				p.setStyle(Style.FILL);
				canvas.drawRect(r, p);
			}
			if (iThickness>0) {
				if (iOutlineColour==-1) iOutlineColour = 3; //TODO hardcoded default
				p.setARGB(255, colours.GetRed(iOutlineColour), colours.GetGreen(iOutlineColour), colours.GetBlue(iOutlineColour));
				p.setStyle(Style.STROKE);
				p.setStrokeWidth(iThickness); 
				canvas.drawRect(r,p);
			}
		}
		public void DrawString(String string, int x1, int y1, long Size) {
			p.setTextSize(Size);
			p.setARGB(255, 0, 0, 0);
			p.setStyle(Style.FILL_AND_STROKE);
			canvas.drawText(string, x1, y1, p);
		}
		
		public int GetHeight() { 
			return DasherCanvas.this.getHeight();
		}
		public int GetWidth() {
			return DasherCanvas.this.getWidth();
		}
		public void Polygon(Point[] Points, int fillColour, int iOutlineColour,
				int iWidth) {
			// TODO Auto-generated method stub
			
		}
		public void Polyline(Point[] Points, int iWidth, int iColour) {
			p.setStrokeWidth(iWidth);
			p.setARGB(255, colours.GetRed(iColour), colours.GetGreen(iColour), colours.GetBlue(iColour));
			for (int i = 0; i < Points.length-1; i++) {
				canvas.drawLine(Points[i].x, Points[i].y, Points[i+1].x, Points[i+1].y, p);
			}
		}
		public void SetColourScheme(CCustomColours colours) {
			this.colours = colours;
		}
		public Point TextSize(String string, int iSize) {
			p.setTextSize(iSize);
			p.getTextBounds(string, 0, string.length(), r);
			assert (r.top == 0);
			assert (r.left == 0);
			return new Point(r.right, r.bottom);// - r.left, r.bottom - r.top);
		}
		
	}

}
