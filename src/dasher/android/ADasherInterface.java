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
	private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<Runnable>();
	private Thread taskThread;
	private boolean m_bRedrawRequested;
	
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
		final TiltInput tilt=TiltInput.MAKE(androidCtx, this, getSettingsStore());
		if (tilt!=null) RegisterModule(tilt);
		final CDasherInput touch =new CDasherInput(this, getSettingsStore(), 0, "Touch Input") {
			@Override
			public void GetScreenCoords(CDasherView pView,long[] Coordinates) {
				DasherCanvas surf = (DasherCanvas)m_DasherScreen;
				surf.GetCoordinates(Coordinates);
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
			}
		};
		RegisterModule(setDefaultInput(touch));
		RegisterModule(setDefaultInputFilter(new CStylusFilter(this, getSettingsStore(), 16, "Android Touch Control") {
			/** A special CDasherInput that reads touch coordinates from the screen/DasherCanvas,
			 * but does <em>not</em> double the x coordinate even if the AndroidDoubleX preference
			 * is true. (Used for clicks, as opposed to drags)
			 */
			private final CDasherInput undoubledTouch = new CDasherInput(ADasherInterface.this,getSettingsStore(), -1, "Unregistered Input Device") {
				@Override public void GetScreenCoords(CDasherView pView, long[] coords) {
					CDasherScreen screen = pView.Screen();
					((DasherCanvas)screen).GetCoordinates(coords);
				}
			};
						
			@Override public void ApplyTransform(CDasherView pView, long[] coords) {
				if (PreferenceManager.getDefaultSharedPreferences(androidCtx).getBoolean("AndroidTouchUsesTiltX",false)) {
					long iDasherY=coords[1];
					tilt.GetDasherCoords(pView, coords);
					coords[1]=iDasherY;
				}
				super.ApplyTransform(pView, coords);
			}
			
			@Override public void KeyDown(long iTime, int iID, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
				super.KeyDown(iTime, iID, pView, undoubledTouch, pModel);
			}

			private boolean bActive;
			@Override public void Activate() {bActive=true;}
			@Override public void Deactivate() {bActive=false;}
			@Override public void HandleEvent(CEvent evt) {
				if (evt instanceof CParameterNotificationEvent && ((CParameterNotificationEvent)evt).m_iParameter==Ebp_parameters.BP_DASHER_PAUSED && bActive) {
					if (!GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED) && prefs.getBoolean("AndroidTouchUsesTiltX", false))
						tilt.Activate();
					else
						tilt.Deactivate();
				}
			}

		}));
		RegisterModule(new CDefaultFilter(this, getSettingsStore(), 14, "Android Tilt Control") {
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
				if (prefs.getBoolean("AndroidTilt2D", true))
					super.ApplyTransform(pView, coords);
				else
					Apply1DTransform(pView, coords);
			}
			
			@Override public void KeyDown(long iTime, int iID, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
				if (iID==100 && prefs.getBoolean("AndroidTiltHoldToGo", false))
					m_Interface.Unpause(iTime);
				else
					super.KeyDown(iTime, iID, pView, pInput, pModel);
			}
			
			@Override public void KeyUp(long iTime, int iID, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
				if (iID==100 && prefs.getBoolean("AndroidTiltHoldToGo", false))
					m_Interface.PauseAt(0,0);
				else
					super.KeyUp(iTime, iID, pView, pInput, pModel);
			}
		});

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
