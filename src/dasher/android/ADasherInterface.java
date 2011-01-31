package dasher.android;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
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
import android.os.Environment;
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
	private TiltInput tilt;
	
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
		else if (evt instanceof CMessageEvent) {
			CMessageEvent msg = (CMessageEvent)evt;
			switch (msg.m_iType) {
			case 0:
				Log.i("DasherIME",msg.m_strMessage);
				break;
			case 1:
				Log.w("DasherIME",msg.m_strMessage);
				break;
			case 2:
				Log.e("DasherIME",msg.m_strMessage);
				break;
			}
		}
	}
	
	private class Progress implements Runnable, ProgressNotifier {
		private final CLockEvent evt;
		Progress(CLockEvent evt) {
			InsertEvent(this.evt=evt);
		}
		private boolean bAbortRequested;
		//called from main Dasher thread
		public void run() {
			if (p!=this) return; //a new training request has already superceded us...
			InsertEvent(evt);
			if (!evt.m_bLock) p=null; //finished.
		}
		//called from Training thread
		public boolean notifyProgress(int iPercent) {
			evt.m_iPercent=iPercent;
			enqueue(this);
			synchronized(this) {return bAbortRequested;}
		}
	}
	private Progress p;
	@Override protected void train(final CAlphabetManager<?> mgr) {
		//1. we're on main thread, so can read p...
		if (p!=null) {
			//p's bAbortRequested field is also read by the thread currently doing training
			synchronized(p) {
				p.bAbortRequested=true;
				//wait for training thread to abort...
				while (p.evt.m_bLock)
					try {p.wait();}
					catch (InterruptedException e) {}
			}
		}
		//make new lock...
		final Progress myProg = p = new Progress(new CLockEvent("Training Dasher",true,0));
		//now we've got lock, train asynchronously...
		new Thread() {
			public void run() {
				try {
					train(mgr,myProg);
				} catch (Throwable th) {
					Log.e("DasherIME", "Error in training", th);
				}
				//completed, or aborted. Signal this...
				synchronized(myProg) {
					myProg.evt.m_bLock=false;
					myProg.notifyAll(); //in case someone was waiting for us to abort
				}
				//broadcast the unlock message (unless aborted)
				enqueue(myProg);
			}
		}.start();
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
					switch (pView.getOrientation()) {
					case LEFT_TO_RIGHT:
						Coordinates[0] = Math.min(Coordinates[0]*2,surf.getWidth());
						break;
					case RIGHT_TO_LEFT:
						Coordinates[0] = Math.max(0, 2*Coordinates[0]-surf.getWidth());
						break;
					case TOP_TO_BOTTOM:
						Coordinates[1] = Math.min(Coordinates[1]*2,surf.getHeight());
						break;
					case BOTTOM_TO_TOP:
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
		tilt=TiltInput.MAKE(androidCtx, this, getSettingsStore());
		if (tilt!=null) {
			SetTiltAxes();
			RegisterModule(tilt);
			RegisterModule(new CDasherInput(this, getSettingsStore(), 2, "Touch with tilt X") {
				long lastTouch;
				@Override public boolean GetScreenCoords(CDasherView pView, long[] coords) {
					if (!tilt.GetScreenCoords(pView, coords)) return false;
					boolean horiz = pView.getOrientation().isHorizontal;
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
		RegisterModule(new COneDimensionalFilter(this, getSettingsStore(), 14, "Android Tilt Control") {
			private final PowerManager.WakeLock wl = ((PowerManager)androidCtx.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,"tilting");
			private boolean bActive;
			@Override public boolean supportsPause() {
				return !prefs.getBoolean("AndroidTiltHoldToGo",false);
			}
			@Override public void ApplyTransform(CDasherView pView, long[] coords) {
				if (prefs.getBoolean("AndroidTiltHoldToGo", false) && prefs.getBoolean("AndroidTiltUsesTouchX", false)) {
					long iDasherY=coords[1];
					touch.GetDasherCoords(pView, coords);
					coords[1]=iDasherY;
				}
				super.ApplyTransform(pView, coords);
			}
			
			/** Override to disable offset for tilting. */
			@Override public void ApplyOffset(CDasherView pView, long[] coords) {
				//do nothing
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
					if (!GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED)) {
						if (!wl.isHeld()) {Log.d("DasherIME","Acquiring Wakelock"); wl.acquire();}
						else Log.d("DasherIME","Wakelock already held");
					} else {
						if (wl.isHeld()) {Log.d("DasherIME","Releasing Wakelock"); wl.release();}
						else Log.d("DasherIME","Wakelock already released");
					}
				} else if (evt instanceof CParameterNotificationEvent && ((CParameterNotificationEvent)evt).m_iParameter==Ebp_parameters.BP_DASHER_PAUSED) {
					if (!bActive) Log.d("DasherIME","Tilt not active");
					else if (prefs.getBoolean("AndroidTiltHoldToGo",false))
						Log.d("DasherIME","Tilt in hold-to-go mode");
					else Log.d("DasherIME","Impossible?!?!");
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
		RegisterModule(new OneButtonDynamicFilter(this, getSettingsStore()));
		RegisterModule(new TwoButtonDynamicFilter(this, getSettingsStore()));
	}
	
	public void SetTiltAxes() {
		//Should never actually be called if tilt sensor isn't present, but checking anyway
		if (tilt==null) return;
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(androidCtx);
		//TODO these defaults copied from CalibPreference; remove duplication!
		tilt.setAxes(sp.getFloat(CalibPreference.ANDROID_TILT_MIN_X, -1.0f),
					 sp.getFloat(CalibPreference.ANDROID_TILT_MAX_X, 1.0f),
					 sp.getFloat(CalibPreference.ANDROID_TILT_MIN_Y, 1.0f),
					 sp.getFloat(CalibPreference.ANDROID_TILT_MAX_Y, 9.0f));
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

	@Override protected void ScanXMLFiles(XMLFileParser parser, String prefix) {
		AssetManager assets = androidCtx.getAssets();
		try {
			for (String aFile : assets.list("")) {//DasherActivity.this.fileList()) {
				if (aFile.contains(prefix) && aFile.endsWith(".xml"))
					try {parser.ParseFile(assets.open(aFile), false);}
					catch (Exception e) {
						android.util.Log.e("DasherIME", "Could not parse/read asset "+aFile,e);
					}
			}
		} catch (IOException e) {
			android.util.Log.e("DasherIME", "Could not list assets: ",e);
		}
		for (File f : new File[] {GetPackageDir(),USER_DIR}) {
			if (f.exists()) {
				for (String aFile : f.list()) {
					if (aFile.contains(prefix) && aFile.endsWith(".xml"))
						try {parser.ParseFile(new FileInputStream(new File(f,aFile)), true);}
						catch (Exception e) {
							android.util.Log.e("DasherIME","Could not parse/read user file "+aFile,e);
						}
				}
			}
		}
	}

	@Override
	public void GetStreams(String fname, Collection<InputStream> into) {
		//1. system file...
		try {
			InputStream in = androidCtx.getAssets().open(fname);
			into.add(in);
			//AssetFileDescriptor fd = androidCtx.getAssets().openFd(fname);
			//streams.add(fd.createInputStream());
		} catch (IOException e) {
			//no system training file present. Which is fine; silently skip.
		}
		//2. user file(s)
		try {
			File f = new File(GetPackageDir(),fname); //user-specific file written by dasher
			if (f.exists()) into.add(new FileInputStream(f));
			f = new File(USER_DIR, fname); //anything explicitly/manually provided by user
			if (f.exists()) into.add(new FileInputStream(f));
		} catch (FileNotFoundException fnf) {
			//we checked f.exists()...
			throw new AssertionError();
		}
	}
	
	@Override public void WriteTrainFile(String filename, String s) {
		String msg;
		File pkgDir = GetPackageDir();
		if (pkgDir.exists() || pkgDir.mkdirs()) {
			try {
				//second parameter is whether to append to any existing file - yes, do!
				PrintWriter pw = new PrintWriter(new FileWriter(new File(pkgDir,filename), true));
				pw.print(s);
				pw.flush();
				pw.close();
				return; //ok
			} catch (IOException e) {
				msg = e.toString();
			}
		} else msg = pkgDir+" does not exist and could not create.";
		android.util.Log.e("DasherIME", "Error writing training file: "+msg);
	}
	
	/** Cache of {@link #GetPackageDir()} */
	private File PACKAGE_DIR;
	/** OS-provided directory for storing files for this app, i.e. that will be removed on app uninstallation
	 * (On API 8+, anyway!). Following Google's specification, this is something like /sdcard/Android/data/dasher.android/files/.
	 * We store text the user writes whilst using Dasher into this directory.
	 * Note, application package name is determined from the (runtime sub)class of the receiver.
	 * @return Directory for storing this application package's files.
	 */
	protected File GetPackageDir() {
		if (PACKAGE_DIR==null) getDir: {
			try {
				java.lang.reflect.Method m = Context.class.getMethod("getExternalFilesDir", new Class[] {String.class});
				PACKAGE_DIR = (File)m.invoke(androidCtx, new Object[] {null});
				//method can return null at device boot time (hypothesis:
				// Dasher service starting up before filesystem ready?).
				// if so, fallback to old API (below) 
				if (PACKAGE_DIR!=null) break getDir;
			} catch (Exception e) {/*fall through*/}
			//New API not available or not yet working. Use old API, and stick on the prescribed directory name...
			PACKAGE_DIR = Environment.getExternalStorageDirectory();
			PACKAGE_DIR = new File(new File(new File(new File(PACKAGE_DIR, "Android"),"data"),
					getClass().getPackage().getName()), "files");
		}
		return PACKAGE_DIR;
	}
	/** Additional directory from which we read any alphabet/training files placed there by the user
	 * (not removed upon app installation, but much easier for the user to find: /sdcard/dasher).
	 */
	private static final File USER_DIR = new File(Environment.getExternalStorageDirectory(),"dasher");

}
