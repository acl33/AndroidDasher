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

import dasher.CAlphIO;
import dasher.CColourIO;
import dasher.CDasherInterfaceBase;
import dasher.CEventHandler;
import dasher.CLockEvent;
import dasher.CSettingsStore;
import dasher.CStylusFilter;
import dasher.XMLFileParser;
import dasher.android.DasherCanvas.TouchInput;

public abstract class ADasherInterface extends CDasherInterfaceBase {
	private final Context androidCtx;
	private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<Runnable>();
	private Thread taskThread;
	
	public void enqueue(Runnable r) {tasks.add(r);}
	
	public ADasherInterface(Context androidCtx) {this.androidCtx = androidCtx;}
	
	@Override
	public void Realize() {
		if (taskThread != null) throw new IllegalStateException("Already Realize()d!");
		super.Realize();
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
		taskThread.start();
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
		RegisterModule(new CStylusFilter(this, m_SettingsStore));
	}
	
	@Override
	public void SetupPaths() {
		// TODO Auto-generated method stub

	}

	/*package*/ CSettingsStore getSettingsStore() {
		return m_SettingsStore;
	}

	@Override
	public void ScanColourFiles(CColourIO colourIO) {
		ScanXMLFiles(colourIO, "colour");
	}

	@Override
	public void ScanAlphabetFiles(CAlphIO alphIO) {
		ScanXMLFiles(alphIO,"alphabet");
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
