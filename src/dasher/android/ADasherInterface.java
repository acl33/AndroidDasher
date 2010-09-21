package dasher.android;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import dasher.*;

public abstract class ADasherInterface extends CDasherInterfaceBase {
	protected Context androidCtx;
	private final BlockingQueue<Runnable> tasks = supportsLinkedBlockingQueue ? new LinkedBlockingQueue<Runnable>() : new ArrayBlockingQueue<Runnable>(5);
	private Thread taskThread;
	private boolean m_bRedrawRequested;
	private static final boolean supportsLinkedBlockingQueue;
	
	static {
		boolean ok;
		try {
			BlockingQueue<Integer> q = new LinkedBlockingQueue<Integer>();
			Integer one=1, two=2;
			q.put(one);
			q.put(two);
			q.remove(one);
			q.remove(two);
			q.put(3);
			q.take();
			//all ok
			ok = true;
		} catch (InterruptedException e) {
			//shouldn't happen?!?!
			ok = false;
		} catch (NullPointerException e) {
			Log.d("DasherIME","LinkedBlockingQueue threw NPE, must be old version, using ArrayBlockingQueue instead");
			ok = false;
		}
		supportsLinkedBlockingQueue = ok;
	}
	
	public void enqueue(Runnable r) {tasks.add(r);}
	
	@Override
	protected final void Realize() {
		throw new RuntimeException("Should not call no-args Realize directly, rather call Realize(Context).");
	}
	
	public synchronized void Realize(Context androidCtx) {
		this.androidCtx = androidCtx;
		if (taskThread!=null) return;
		taskThread = new Thread() {
			public void run() {
				Queue<Runnable> frameTasks = new LinkedList<Runnable>();
				while (true) {
					if (m_DasherScreen!=null && (!GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED) || m_bRedrawRequested)) {
						m_bRedrawRequested = false;
						((DasherCanvas)m_DasherScreen).renderFrame();
						tasks.drainTo(frameTasks);
						while (!frameTasks.isEmpty())
							frameTasks.remove().run();
					} else {
						try {
							tasks.take().run();
						} catch (InterruptedException e) {
							//we are interrupted if ever BP_DASHER_PAUSED is cleared
							// (to tell us to start rendering!)
							// - so loop round
						}
					}
				}
			}
		};
		Log.d("DasherIME","Realize()ing...");
		super.Realize();
		taskThread.start();
	}
	
	@Override
	public void Redraw(final boolean bChanged) {
		if (Thread.currentThread()==taskThread)
			m_bRedrawRequested=true;
		else
			enqueue(new Runnable() {
				public void run() {
					Redraw(bChanged);
				}
			});
	}
	
	@Override
	public void InsertEvent(CEvent evt) {
		super.InsertEvent(evt);
		if (evt instanceof CParameterNotificationEvent
				&& ((CParameterNotificationEvent)evt).m_iParameter == Ebp_parameters.BP_DASHER_PAUSED
				&& !GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED))
			taskThread.interrupt();
	}
	
	/*@Override
	public void StartShutdown() {
		if (taskThread == null) throw new IllegalStateException("Already started shutdown, or never Realize()d!");
		taskThread.interrupt();
		taskThread = null;
		super.StartShutdown();
	}*/
	
	@Override
	public void CreateModules() {
		final SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(androidCtx);
		final CDasherInput touch =new CDasherInput(this, getSettingsStore(), 0, "Touch Input") {
			@Override
			public boolean GetScreenCoords(CDasherView pView,long[] Coordinates) {
				DasherCanvas surf = (DasherCanvas)m_DasherScreen;
				if (!surf.GetCoordinates(Coordinates)) return false;
				if (prefs.getBoolean("AndroidDoubleX", false)) {
					switch ((int)GetLongParameter(Elp_parameters.LP_REAL_ORIENTATION)) {
					case Opts.ScreenOrientations.LeftToRight:
						Coordinates[0] = Math.min(Coordinates[0]*2,surf.getWidth());
						break;
					case Opts.ScreenOrientations.RightToLeft:
						Coordinates[0] = Math.max(0, 2*Coordinates[0]-surf.getWidth());
						break;
					case Opts.ScreenOrientations.TopToBottom:
						Coordinates[1] = Math.min(Coordinates[1]*2,surf.getHeight());
						break;
					case Opts.ScreenOrientations.BottomToTop:
						Coordinates[1] = Math.max(0,2*Coordinates[1]-surf.getHeight());
						break;
					default:
						throw new AssertionError();
					}
				}
				return true;
			}
		};
		RegisterModule(setDefaultInput(touch));
		final TiltInput tilt=TiltInput.MAKE(androidCtx, this, getSettingsStore());
		if (tilt!=null) {
			RegisterModule(tilt);
			RegisterModule(new CDasherInput(this, getSettingsStore(), 2, "Touch with tilt X") {
				long lastTouch;
				@Override public boolean GetScreenCoords(CDasherView pView, long[] coords) {
					if (!tilt.GetScreenCoords(pView, coords)) return false;
					int orient = (int)GetLongParameter(Elp_parameters.LP_REAL_ORIENTATION);
					boolean horiz = (orient == Opts.ScreenOrientations.LeftToRight) || (orient==Opts.ScreenOrientations.RightToLeft);
					long tiltC = (horiz) ? coords[0] : coords[1];
					if (touch.GetScreenCoords(pView, coords))
						lastTouch = (horiz) ? coords[1] : coords[0];
					else coords[horiz ? 1 : 0] = lastTouch;
					coords[horiz ? 0 : 1] = tiltC;
					return true;
				}
				@Override public void Activate() {tilt.Activate();}
				@Override public void Deactivate() {tilt.Deactivate();}
			});
		}
		
		RegisterModule(setDefaultInputFilter(new CStylusFilter(this, getSettingsStore(), 16, "Android Touch Control") {
			/** A special CDasherInput that reads touch coordinates from the screen/DasherCanvas,
			 * but does <em>not</em> double the x coordinate even if the AndroidDoubleX preference
			 * is true, nor get its X coordinate from tilting. (Used for clicks, as opposed to drags)
			 */
			private final CDasherInput undoubledTouch = new CDasherInput(ADasherInterface.this,getSettingsStore(), -1, "Unregistered Input Device") {
				@Override public boolean GetScreenCoords(CDasherView pView, long[] coords) {
					return ((DasherCanvas)pView.Screen()).GetCoordinates(coords);
				}
			};
						
			@Override public void KeyUp(long iTime, int iID, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
				super.KeyUp(iTime, iID, pView, undoubledTouch, pModel);
			}
		}));
		RegisterModule(new CDefaultFilter(this, getSettingsStore(), 14, "Android Tilt Control") {
			private final PowerManager.WakeLock wl = ((PowerManager)androidCtx.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,"tilting");
			private boolean bActive;
			
			private void Apply1DTransform(CDasherView pView, long[] coords) {
				// The distance between the Y coordinate and the centreline in pixels
				final long disty=GetLongParameter(Elp_parameters.LP_OY)-coords[1];
				  
				final long circlesize = (long)(GetLongParameter(Elp_parameters.LP_MAX_Y)*(1.0-coords[0]/(double)pView.VisibleRegion().maxX)/2.5);
				final long yforwardrange = (GetLongParameter(Elp_parameters.LP_MAX_Y)*5)/16;
				final long yfullrange = GetLongParameter(Elp_parameters.LP_MAX_Y)/2;
				  
				double x,y; //0,0=on crosshair; positive=forwards/up...	
				
				if (disty<=yforwardrange && disty>=-yforwardrange) {
					//go forwards!
					final double angle=((disty*3.14159/2)/(double)yforwardrange);
					x=Math.cos(angle);
					y=-Math.sin(angle);
				} else if (disty<=yfullrange && disty>=-yfullrange) {
					final long ybackrange = yfullrange-yforwardrange;
					final long ellipse_eccentricity=6;
					//backwards, off bottom or top...
					final double yb = (Math.abs(disty)-yforwardrange)/(double)ybackrange;
					final double angle=(yb*3.14159)*(yb+(1-yb)*(ybackrange/(double)yforwardrange/ellipse_eccentricity));
				    
				    x=-Math.sin(angle)*ellipse_eccentricity/2.0;
				    y=(1.0+Math.cos(angle))/2.0;
				    if (disty>yforwardrange) y=-y; //backwards off top
				} else {
					//off limits, go nowhere
					x=0; y=0;
				} 
				coords[0] = GetLongParameter(Elp_parameters.LP_OX)-(long)(x*circlesize);
				coords[1] = GetLongParameter(Elp_parameters.LP_OY)+(long)(y*circlesize);
			}
			@Override public void ApplyTransform(CDasherView pView, long[] coords) {
				if (prefs.getBoolean("AndroidTiltHoldToGo", false) && prefs.getBoolean("AndroidTiltUsesTouchX", false)) {
					long iDasherY=coords[1];
					touch.GetDasherCoords(pView, coords);
					coords[1]=iDasherY;
				}
				if (prefs.getBoolean("AndroidTilt2D", false)) //false=default
					super.ApplyTransform(pView, coords);
				else
					Apply1DTransform(pView, coords);
			}
			
			@Override public void KeyDown(long iTime, int iID, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
				if (iID==100 && prefs.getBoolean("AndroidTiltHoldToGo", false)) {
					m_Interface.Unpause(iTime);
				} else
					super.KeyDown(iTime, iID, pView, pInput, pModel);
			}
			@Override public void Activate() {bActive=true;}
			@Override public void Deactivate() {bActive=false;}
			
			@Override public void HandleEvent(CEvent evt) {
				super.HandleEvent(evt);
				if (bActive &&
						evt instanceof CParameterNotificationEvent &&
						((CParameterNotificationEvent)evt).m_iParameter == Ebp_parameters.BP_DASHER_PAUSED &&
						!prefs.getBoolean("AndroidTiltHoldToGo", false)) {
					if (GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED)) {
						if (!wl.isHeld()) wl.acquire();
					} else {
						if (wl.isHeld()) wl.release();
					}
				}
			}
			
			@Override public void KeyUp(long iTime, int iID, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
				if (iID==100 && prefs.getBoolean("AndroidTiltHoldToGo", false))
					m_Interface.PauseAt(0,0);
				else
					super.KeyUp(iTime, iID, pView, pInput, pModel);
			}
		});
		RegisterModule(new CButtonMode(this, getSettingsStore(), 12, "Direct Mode"));
		RegisterModule(new CMenuMode(this, getSettingsStore(), 11, "Scanning Menu Mode"));
		RegisterModule(new CCompassMode(this,getSettingsStore()));
	}
	
	@Override
	public void SetupPaths() {
		// TODO Auto-generated method stub

	}

	@Override
	public void ScanColourFiles(CColourIO colourIO) {
		ScanXMLFiles(colourIO, "colour");
	}

	@Override
	public void ScanAlphabetFiles(CAlphIO alphIO) {
		ScanXMLFiles(alphIO,"alphabet");
	}
	
	@Override public void ChangeScreen(CDasherScreen surf) {
		if (surf==null) {
			m_DasherScreen=null;
			// this is used as a sentinel to avoid rendering etc.
			// We do not (and cannot) call super.ChangeScreen(null),
			// or construct a View around a null screen, so for now - we don't!
		} else if (surf instanceof DasherCanvas) {
			if (surf!=m_DasherScreen)
				super.ChangeScreen(surf);
		} else
			throw new IllegalArgumentException(surf+" is not a DasherCanvas!");
	}

	private void ScanXMLFiles(XMLFileParser parser, String prefix) {
		AssetManager assets = androidCtx.getAssets();
		try {
			for (String aFile : assets.list("")) {//DasherActivity.this.fileList()) {
				if (aFile.contains(prefix) && aFile.endsWith(".xml"))
					try {parser.ParseFile(assets.open(aFile), false);}
					catch (Exception e) {
						System.err.println("Could not parse/read asset "+aFile+": "+e);
					}
			}
		} catch (IOException e) {
			System.err.println("Could not list assets: " + e.toString());
		}
		File userDir = androidCtx.getDir("data", Context.MODE_WORLD_WRITEABLE);
		
		for (String aFile : userDir.list()) {
			if (aFile.contains(prefix) && aFile.endsWith(".xml"))
				try {parser.ParseFile(new FileInputStream(new File(userDir,aFile)), true);}
				catch (Exception e) {
					System.err.println("Could not parse/read user file "+aFile+": "+e);
				}
		}
	}

	protected void train(String trainFileName, CLockEvent evt) {
		int iTotalBytes=0;
		List<InputStream> streams=new ArrayList<InputStream>();
		//1. system file...
		try {
			InputStream in = androidCtx.getAssets().open(trainFileName);
			iTotalBytes+=in.available();
			streams.add(in);
			//AssetFileDescriptor fd = androidCtx.getAssets().openFd(trainFileName);
			//iTotalBytes += fd.getLength();
			//streams.add(fd.createInputStream());
		} catch (IOException e) {
			//no system training file present. Which is fine; silently skip.
		}
		//2. user file
		File f=new File(trainFileName);
		if (f.exists()) {
			try {
			iTotalBytes += f.length();
				streams.add(new FileInputStream(f));
			} catch (FileNotFoundException fnf) {
				//we checked f.exists()...
				throw new AssertionError();
			}
		}
		
		int iRead = 0;
		for (InputStream in : streams) {
			try {
				iRead = m_DasherModel.TrainStream(in, iTotalBytes, iRead, evt);
			} catch (IOException e) {
				android.util.Log.e("dasher", "error in training - rest of text skipped", e);
			}
		}
	}

}
