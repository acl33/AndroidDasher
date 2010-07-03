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
	private DasherCanvas surf;
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
					if (surf!=null && (!GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED) || m_bRedrawRequested)) {
						m_bRedrawRequested = false;
						surf.renderFrame();
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
		RegisterModule(setDefaultInputFilter(new CStylusFilter(this, getSettingsStore())));
		RegisterModule(new CDefaultFilter(this, getSettingsStore(), 14, "Normal Control"));
		final SensorManager sm = (SensorManager)androidCtx.getSystemService(Context.SENSOR_SERVICE);
		List<Sensor> ss = sm.getSensorList(Sensor.TYPE_ACCELEROMETER);
		if (!ss.isEmpty()) {
			final Sensor s = ss.get(0);
			//is its own class in order to extend DasherInput _and_ implement SensorEventListener...
			class TiltInput extends CDasherInput implements SensorEventListener {
				float fx,fy; boolean bActive;
				private final PowerManager.WakeLock wl = ((PowerManager)androidCtx.getSystemService(Context.POWER_SERVICE)).
					newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,"tilting");
				
				public TiltInput() {
					super(ADasherInterface.this, getSettingsStore(), 1, "Tilt Input");
				}
				
				@Override public void Activate() {bActive=true; update();}
				@Override public void Deactivate() {bActive=false; update();}
				
				@Override public void HandleEvent(dasher.CEvent evt) {
					if (evt instanceof CParameterNotificationEvent &&
							((CParameterNotificationEvent)evt).m_iParameter == Ebp_parameters.BP_DASHER_PAUSED)
						update();
				}
				
				private void update() {
					if (bActive && !GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED)) {
						if (wl.isHeld()) {
							return;
						}
						sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);
						wl.acquire();
					} else {
						if (!wl.isHeld()) {
							return;
						}
						sm.unregisterListener(this,s);
						wl.release();
					}
				}
				
				public void onAccuracyChanged(Sensor sensor, int accuracy) {}

				public void onSensorChanged(SensorEvent event) {
					if (m_DasherScreen==null) return;
					float[] vals=event.values;
					/*StringBuilder sb=new StringBuilder();
					for (int i=0; i<vals.length; i++)
						sb.append(i==0 ? "{" : ",").append(vals[i]);
					sb.append("}");
					android.util.Log.d("DasherIME","Got rotation "+sb);*/
					fx = Math.max(0.0f, Math.min(1.0f, (vals[0]-1.0f)/-2.0f));
					fy = Math.max(0.0f, Math.min(1.0f, (vals[1]-1.0f)/8.0f));
				}

				@Override
				public void GetScreenCoords(CDasherView pView,long[] Coordinates) {
					Coordinates[0] = (int)(fx * pView.Screen().GetWidth());
					Coordinates[1] = (int)(fy * pView.Screen().GetHeight());
				}
			};
			RegisterModule(new TiltInput());
		}
		RegisterModule(setDefaultInput(new CDasherInput(this, getSettingsStore(), 0, "Touch Input") {
			
			@Override
			public void GetScreenCoords(CDasherView pView,long[] Coordinates) {
				surf.GetCoordinates(Coordinates);
				if (PreferenceManager.getDefaultSharedPreferences(androidCtx).getBoolean("AndroidDoubleX", false)) {
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
		
		}));
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
	
	public void setCanvas(DasherCanvas surf) {
		this.surf=surf;
		if (m_DasherScreen==null && surf!=null)
			ChangeScreen(surf);
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
