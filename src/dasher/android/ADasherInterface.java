package dasher.android;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import android.content.SharedPreferences;
import android.content.res.AssetManager;

import dasher.CColourIO;
import dasher.CDasherInterfaceBase;
import dasher.CEventHandler;
import dasher.CSettingsStore;

public abstract class ADasherInterface extends CDasherInterfaceBase {
	private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<Runnable>();
	private Thread taskThread;
	public void enqueue(Runnable r) {tasks.add(r);}
	
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
	public int GetFileSize(String strFileName) {
		return (int)new File(strFileName).length();
	}

	@Override
	public void SetupPaths() {
		// TODO Auto-generated method stub

	}

	/*package*/ CSettingsStore getSettingsStore() {
		return m_SettingsStore;
	}

}
