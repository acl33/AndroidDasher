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
import android.util.Log;

import dasher.CAlphIO;
import dasher.CColourIO;
import dasher.CDasherInput;
import dasher.CDasherInterfaceBase;
import dasher.CDefaultFilter;
import dasher.CEventHandler;
import dasher.CLockEvent;
import dasher.CParameterNotificationEvent;
import dasher.CSettingsStore;
import dasher.CStylusFilter;
import dasher.Ebp_parameters;
import dasher.Elp_parameters;
import dasher.XMLFileParser;

public abstract class ADasherInterface extends CDasherInterfaceBase {
	protected Context androidCtx;
	private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<Runnable>();
	private Thread taskThread;
	private DasherCanvas surf;
	
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
				try {
					while (true) tasks.take().run();
				} catch (InterruptedException e) {
					//we are interrupted when the DasherInterface is shutting down.
					//When this happens, finish all tasks currently on the queue, and terminate.
					Queue<Runnable> remaining = new LinkedList<Runnable>();
					tasks.drainTo(remaining);
					while (!remaining.isEmpty())
						remaining.remove().run();
				}
			}
		};
		Log.d("DasherIME","Realize()ing...");
		super.Realize();
		taskThread.start();
	}
	
	@Override
	public void Redraw(boolean bChanged) {
		if (surf!=null) surf.requestRender();
	}
	
	@Override
	public void StartShutdown() {
		if (taskThread == null) throw new IllegalStateException("Already started shutdown, or never Realize()d!");
		taskThread.interrupt();
		taskThread = null;
		super.StartShutdown();
	}
	
	@Override
	public void CreateModules() {
		RegisterModule(new CStylusFilter(this, getSettingsStore()));
		RegisterModule(new CDefaultFilter(this, getSettingsStore(), 14, "Normal Control"));
		final SensorManager sm = (SensorManager)androidCtx.getSystemService(Context.SENSOR_SERVICE);
		List<Sensor> ss = sm.getSensorList(Sensor.TYPE_ACCELEROMETER);
		if (!ss.isEmpty()) {
			final Sensor s = ss.get(0);
			class TiltInput extends CDasherInput implements SensorEventListener {
				int x,y; boolean bActive;
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
					float sx = (vals[0]-1.0f)/-2.0f;
					x = (int)(m_DasherScreen.GetWidth() * Math.max(0.0f, Math.min(1.0f,sx)));
					float sy = ((vals[1]-1.0f)/8.0f);
					y = (int)(m_DasherScreen.GetHeight() * Math.max(0.0f,Math.min(1.0f,sy)));
				}

				@Override
				public int GetCoordinateCount() {return 2;}

				@Override
				public int GetCoordinates(long[] Coordinates) {
					Coordinates[0] = x;
					Coordinates[1] = y;
					return 0;
				}
			};
			RegisterModule(new TiltInput());
		}
		RegisterModule(new CDasherInput(this, getSettingsStore(), 0, "Mouse Input") {
			
			@Override
			public int GetCoordinates(long[] Coordinates) {
				surf.GetCoordinates(Coordinates);
				return 0; //screen coords
			}
				
			@Override
			public int GetCoordinateCount() {
				return 2;
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
	
	public void setCanvas(DasherCanvas surf) {
		this.surf=surf;
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
