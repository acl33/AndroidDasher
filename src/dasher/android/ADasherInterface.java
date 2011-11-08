package dasher.android;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Environment;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import dasher.*;
import dasher.CControlManager.ControlAction;
import dasher.android.AndroidSettings.SettingsOverride;
import dasher.CDasherView.MutablePoint;

public class ADasherInterface extends CDasherInterfaceBase {
	/** SettingsStore in use. We keep a reference so we can override
	 * the stored settings on a per-document basis, in {@link #SetDocument(EditableDocument, ControlAction, int)}.
	 * TODO, there is the question as to whether this is sufficient mechanism for
	 * per-document customization, or whether we also need documents to be able
	 * to provide a new (/edit existing) alphabet, etc... */ 
	private final AndroidSettings sets;
	protected final Context androidCtx;
	private final BlockingQueue<Runnable> tasks = supportsLinkedBlockingQueue ? new LinkedBlockingQueue<Runnable>() : new ArrayBlockingQueue<Runnable>(5);
	private final Thread taskThread;
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
	
	public ADasherInterface(Context androidCtx, boolean train) {
		this(new AndroidSettings(PreferenceManager.getDefaultSharedPreferences(androidCtx)), androidCtx, train);
	}
	
	private ADasherInterface(AndroidSettings sets, Context androidCtx, boolean train) {
		super(sets);
		this.sets=sets;
		this.androidCtx = androidCtx;
		taskThread = new Thread() {
			public void run() {
				try {
					Queue<Runnable> frameTasks = new LinkedList<Runnable>();
					while (true) {
						if (m_DasherScreen!=null && doc!=null) {
							tasks.drainTo(frameTasks);
							((DasherCanvas)m_DasherScreen).renderFrame();
							//that'll call round to Redraw(boolean) to schedule another frame
							// if anything happened in this one.
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
				} catch (ThreadDeath d) {}//exit
				//android.util.Log.d("DasherIME","Background thread for "+this+" exitting");
			}
		};
		LoadData();
		taskThread.setDaemon(true);
		if (train) {
			DoSetup();
			taskThread.start();
		}
	}

	public void enqueue(Runnable r) {tasks.add(r);}
	
	@Override
	public void Redraw(final boolean bChanged) {
		if (Thread.currentThread()==taskThread)
			super.Redraw(bChanged);
		else
			enqueue(new Runnable() {
				public void run() {
					Redraw(bChanged);
				}
			}); //enqueue-ing, will unblock the taskThread waiting in tasks.take()...
	}

	@Override public void Message(String msg, int iSeverity) {
		switch (iSeverity) {
		case 0:
			Log.i("DasherIME",msg);
			break;
		case 1:
			Log.w("DasherIME",msg);
			break;
		case 2:
			Log.e("DasherIME",msg);
			break;
		}
	}
	
	private class Progress implements Runnable, ProgressNotifier {
		private final String desc;
		private int percent;
		Progress(String desc) {
			Lock(this.desc=desc, this.percent=0);
		}
		private boolean bAbortRequested;
		//called from main Dasher thread
		public void run() {
			//synchronize on this??
			if (p!=this) return; //a new training request has already superceded us...
			Lock(desc,percent);
			if (percent<0) p=null; //finished.
		}
		//Call from main Dasher thread, but waits to for training thread to respond.
		public synchronized void abort() {
			//bAbortRequested is also read by the thread currently doing training
			bAbortRequested=true;
			//wait for training thread to abort...
			while (percent>=0)
				try {wait();}
				catch (InterruptedException e) {}
		}
		//called from Training thread
		public boolean notifyProgress(int iPercent) {
			boolean bRes;
			synchronized (this) {
				percent=iPercent;
				bRes=bAbortRequested;
			}
			enqueue(this);
			return bRes;
		}
	}
	private Progress p;
	@Override protected void train(final CAlphabetManager<?> mgr) {
		if (Thread.currentThread()!=taskThread) {
			enqueue(new Runnable() {
				public void run() {train(mgr);}
			});
			return;
		}
		//1. we're on main thread, so can read p...
		if (p!=null) p.abort();
		//make new lock...
		final Progress myProg = p = new Progress("Training Dasher");
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
					myProg.percent=-1;
					myProg.notifyAll(); //in case someone was waiting for us to abort
				}
				//broadcast the unlock message (unless aborted)
				enqueue(myProg);
			}
		}.start();
	}
	
	@Override
	public void StartShutdown() {
		if (Thread.currentThread()!=taskThread) {
			//Log.d("DasherIME","StartShutdown...");
			//We just want a holder for a boolean, don't actually need atomicity properties.
			final java.util.concurrent.atomic.AtomicBoolean done 
				=new java.util.concurrent.atomic.AtomicBoolean(false);
			enqueue(new Runnable() {
				public void run() {
					StartShutdown();
					synchronized (done) {
						done.set(true);
						done.notifyAll();
					}
					throw new ThreadDeath();
				}
			});
			synchronized(done) {
				while (!done.get())
					try {done.wait();}
					catch (InterruptedException e) {}
			}
			return;
		}
		if (p!=null) p.abort();
		super.StartShutdown();
	}
	
	@Override
	public void CreateModules() {
		final SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(androidCtx);
		final CDasherInput touch =new CDasherInput("Touch Input") {
			@Override
			public boolean GetScreenCoords(CDasherView pView, MutablePoint Coordinates) {
				DasherCanvas surf = (DasherCanvas)m_DasherScreen;
				if (!surf.GetCoordinates(Coordinates)) return false;
				if (prefs.getBoolean("AndroidDoubleX", false)) {
					switch (pView.getOrientation()) {
					case LEFT_TO_RIGHT:
						Coordinates.x = Math.min(Coordinates.x*2,surf.getWidth());
						break;
					case RIGHT_TO_LEFT:
						Coordinates.x = Math.max(0, 2*Coordinates.x-surf.getWidth());
						break;
					case TOP_TO_BOTTOM:
						Coordinates.y = Math.min(Coordinates.y*2,surf.getHeight());
						break;
					case BOTTOM_TO_TOP:
						Coordinates.y = Math.max(0,2*Coordinates.y-surf.getHeight());
						break;
					default:
						throw new AssertionError();
					}
				}
				return true;
			}
		};
		RegisterModule(setDefaultInput(touch));
		tilt=TiltInput.MAKE(androidCtx);
		if (tilt!=null) {
			//load initial values
			new CalibPreference(androidCtx, null).loadParams(prefs);
			//the assumption is the tilt prefs will now not be changed EXCEPT by a CalibPreference...
			RegisterModule(tilt);
			RegisterModule(new CDasherInput("Touch with tilt X") {
				long lastTouch;
				@Override public boolean GetScreenCoords(CDasherView pView, MutablePoint coords) {
					if (!tilt.GetScreenCoords(pView, coords)) return false;
					boolean horiz = pView.getOrientation().isHorizontal;
					long tiltC = (horiz) ? coords.x : coords.y;
					if (touch.GetScreenCoords(pView, coords))
						lastTouch = (horiz) ? coords.y : coords.x;
					else if (horiz) coords.y=lastTouch; else coords.x=lastTouch;
					if (horiz) coords.x=tiltC; else coords.y=tiltC;
					return true;
				}
				@Override public void Activate() {tilt.Activate();}
				@Override public void Deactivate() {tilt.Deactivate();}
			});
		}
		
		RegisterModule(setDefaultInputFilter(new CStylusFilter(this, this, "Android Touch Control") {
			/** A special CDasherInput that reads touch coordinates from the screen/DasherCanvas,
			 * but does <em>not</em> double the x coordinate even if the AndroidDoubleX preference
			 * is true, nor get its X coordinate from tilting. (Used for clicks, as opposed to drags)
			 */
			private final CDasherInput undoubledTouch = new CDasherInput("Unregistered Input Device") {
				@Override public boolean GetScreenCoords(CDasherView pView, MutablePoint coords) {
					return ((DasherCanvas)pView.Screen()).GetCoordinates(coords);
				}
			};
						
			@Override public void KeyUp(long iTime, int iID, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
				super.KeyUp(iTime, iID, pView, undoubledTouch, pModel);
			}
		}));
		RegisterModule(new COneDimensionalFilter(this, this, "Android Tilt Control") {
			private final PowerManager mgr = (PowerManager)androidCtx.getSystemService(Context.POWER_SERVICE);
			private final PowerManager.WakeLock wl = mgr.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,"tilting");

			@Override public boolean supportsPause() {
				return !prefs.getBoolean("AndroidTiltHoldToGo",false);
			}
			@Override public void ApplyTransform(CDasherView pView, MutablePoint coords) {
				if (prefs.getBoolean("AndroidTiltHoldToGo", false) && prefs.getBoolean("AndroidTiltUsesTouchX", false)) {
					long iDasherY=coords.y;
					touch.GetDasherCoords(pView, coords);
					coords.y=iDasherY;
				}
				super.ApplyTransform(pView, coords);
			}
			
			/** Override to disable offset for tilting. */
			@Override public void ApplyOffset(CDasherView pView, MutablePoint coords) {
				//do nothing
			}
			
			@Override public void KeyDown(long iTime, int iID, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
				if (iID==100 && prefs.getBoolean("AndroidTiltHoldToGo", false)) {
					unpause(iTime);
				} else
					super.KeyDown(iTime, iID, pView, pInput, pModel);
			}
			
			@Override public void pause() {
				if (wl.isHeld()) wl.release();
				super.pause();
			}
			@Override protected void unpause(long iTime) {
				if (!wl.isHeld()) wl.acquire();
				super.unpause(iTime);
			}
			
			@Override public void KeyUp(long iTime, int iID, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
				if (iID==100 && prefs.getBoolean("AndroidTiltHoldToGo", false))
					pause();
				else
					super.KeyUp(iTime, iID, pView, pInput, pModel);
			}
		});
		RegisterModule(new AndroidDirectMode(this, this, "Direct Mode"));
		RegisterModule(new AndroidMenuMode(this, this, "Scanning Menu Mode"));
		RegisterModule(new AndroidCompass(this,this));
		RegisterModule(new Android1BDynamic(this, this));
		RegisterModule(new Android2BDynamic(this, this));
	}
	
	@Override public void KeyDown(final long iTime, final int iId) {
		if (Thread.currentThread()==taskThread)
			super.KeyDown(iTime, iId);
		else enqueue(new Runnable() {
			public void run() {KeyDown(iTime,iId);}
		});
	}
	
	@Override public void KeyUp(final long iTime, final int iId) {
		if (Thread.currentThread()==taskThread)
			super.KeyUp(iTime, iId);
		else enqueue(new Runnable() {
			public void run() {KeyUp(iTime,iId);}
		});
	}
	
	@Override public void ChangeScreen(CDasherScreen surf) {
		if (surf==null) {
			m_DasherScreen=null;
			// this is used as a sentinel to avoid rendering etc.
			// We do not (and cannot) call super.ChangeScreen(null),
			// or construct a View around a null screen, so for now - we don't!
		} else if (surf instanceof DasherCanvas) {
			//even if same object as before - may have changed size...
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
			if (android.os.Debug.isDebuggerConnected()) {
				//truncate file to 3k to speed up debugging...
				byte[] b = new byte[3000]; int p=0;
				for (int r; (r=in.read(b, p, b.length-p))>0; p+=r);
				in=new ByteArrayInputStream(b, 0, p);
			}
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
	
	/*package*/ int convertAndroidKeycode(int keyCode) {
		return (m_InputFilter instanceof AndroidKeyMap) ? ((AndroidKeyMap)m_InputFilter).ConvertAndroidKeycode(keyCode) : -1;
	}
	
	/** Cache of {@link #GetPackageDir()} */
	private File PACKAGE_DIR;
	private EditableDocument doc;
	private List<ControlAction> icActions = Collections.emptyList();
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
	public EditableDocument getDocument() {
		//note this can be called when doc==null, for a common case
		// of building a new tree at offset -1 - i.e. no context. This
		// makes no use of the document, so returning null is fine...
		return doc;
	}

	/**
	 * Switch to a new document - includes committing (learning) any text entered
	 * in the previous document, and rebuilding the tree. The document may optionally
	 * override some user settings, in which case the values returned from GetBoolParameter,
	 * etc., will change accordingly (and appropriate notifications may be generated).
	 * @param doc the new document to edit. Note, this may optionally be an instance of
	 * {@link AndroidSettings.SettingsOverride}; if so, it will be used to override
	 * stored user settings. (If not, any override due to the previous document, will be
	 * cleared.)
	 * @param action If non-null, a command (provided by the IME, or otherwise)
	 * for which to produce control nodes to perform it on the document.
	 * @param cursorPos initial cursor position (i.e. to build initial tree of nodes).
	 */
	protected void SetDocument(final EditableDocument doc, final List<ControlAction> actions, final int cursorPos) {
		enqueue(new Runnable() {
			public void run() {
				Log.d("DasherIME","SetDocument Runnable "+doc);
				if (ADasherInterface.this.doc!=null) {
					//get rid of any existing nodes belonging to the old document
					// (this is in case either old or new documents overrides BP_LM_ADAPTIVE)
					setOffset(-1,true);
				}
				ADasherInterface.this.doc = doc;
				sets.setOverride(doc instanceof SettingsOverride ? (SettingsOverride)doc : null);
				if (doc==null) return; //finishInput - don't recheck/compute action, wait until next StartInput()
				boolean hadAction = !icActions.isEmpty();
				ADasherInterface.this.icActions=actions;
				if (hadAction || !icActions.isEmpty())
				    UpdateControlManager();
				setOffset(cursorPos,true);
			}
		});
	}

	@Override
	public List<ControlAction> getControlActions() {
		List<ControlAction> lst = super.getControlActions();
		if (GetBoolParameter(Ebp_parameters.BP_CONTROL_MODE)) lst.addAll(icActions);
		return lst;
	}

	/** Additional directory from which we read any alphabet/training files placed there by the user
	 * (not removed upon app installation, but much easier for the user to find: /sdcard/dasher).
	 */
	private static final File USER_DIR = new File(Environment.getExternalStorageDirectory(),"dasher");

}
