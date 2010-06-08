package dasher.android;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Paint.Style;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

public class DasherActivity extends Activity {
	private ADasherInterface intf;
	private DasherCanvas surf;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.d("DasherIME","Activity onCreate");
        super.onCreate(savedInstanceState);
        
        setContentView(new View(this) {
        	@Override
        	public void onDraw(Canvas c) {
        		Paint p = new Paint();
        		p.setARGB(255,255,0,0);
        		int w=getWidth(), h=getHeight();
        		c.drawLine(0, 0, w, h, p);
        		p.setARGB(255,0,255,0);
        		doubleDraw(c,0,h,p);
        		p.setARGB(255,255,255,0);
        		doubleDraw(c,0,h/2,p);
        		doubleDraw(c,h/2,h,p);
        		p.setARGB(255,0,0,255);
        		draw(c, 0,0, w*2,h/2,0,h,p);
        		p.setARGB(255,0,255,255);
        		p.setStyle(Style.STROKE);
        		c.drawArc(new RectF(-w,100,w,h+100), 270, 180, false, p);
        		p.setARGB(255,255,0,255);
        		c.drawArc(new RectF(-w/2,100,w/2,h/2+100),270,180,false,p);
        		c.drawArc(new RectF(-w/2,h/2+100,w/2,h+100),270,180,false,p);
        	}
        	private static final int NUM_STEPS=40;
        	private void draw(Canvas c,int x1, int y1, int x2, int y2, int x3, int y3, Paint p) {
        		int x=x1, y=y1;
        		for (int i=1; i<=NUM_STEPS; i++) {
        			float f=i/(float)NUM_STEPS, of = 1.0f-f;
        			int nx = (int)(of*of*x1 + 2.0*of*f*x2 + f*f*x3);
        			int ny = (int)(of*of*y1 + 2.0*of*f*y2 + f*f*y3);
        			c.drawLine(x,y,nx,ny,p);
        			x=nx; y=ny;
        		}
        	}
        	private void doubleDraw(Canvas c,int y1,int y2,Paint p) {
        		int midY = (y1+y2)/2;
        		int right =((y2-y1) * getWidth()) / getHeight();
        		//peaked shape:
        		draw(c,0,y1,right/2,y1,right,midY,p);
        		draw(c,right,midY,right/2,y2,0,y2,p);
        		//semi-peaked:
        		//draw(c, 0,y1, (int)(right*RR2),y1/*(int)(y1*RR2 + midY*(1.0-RR2))*/, right,midY, p);
        		//draw(c, right,midY, (int)(right*RR2),y2/*(int)(y2*RR2 + midY*(1.0-RR2))*/, 0,y2, p);
        		
        		draw(c, 0,y1, (int)(right*RR2),(int)(y1*RR2 + midY*(1.0-RR2)), right,midY, p);
        		draw(c, right,midY, (int)(right*RR2),(int)(y2*RR2 + midY*(1.0-RR2)), 0,y2, p);
        	}
        });
        /*intf.Realize();
        Log.d("DasherIME","Activity realize()d, setting content view...");
        setContentView(surf);
        surf.startAnimating();
        Log.d("DasherIME","Activity onCreate() finished");*/
    }
    private static final double RR2 = 1.0/Math.sqrt(2.0);
	
    @Override
    public void onDestroy() {
    	Log.d("DasherIME","Activity onDestroy");
    	super.onDestroy();
    	//intf.StartShutdown();
    }
    
}
