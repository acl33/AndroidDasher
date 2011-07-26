package dasher.applet;
import java.util.*;

/** Simple thread that just executes tasks in the sequence they are given it. */
public class JDasherThread extends Thread {

	private final Queue<Runnable> events = new LinkedList<Runnable>();
	
		public synchronized void addTasklet(Runnable t) {
		events.add(t);
		this.notifyAll();
	}
	
	public void run() {
		
		while(true) {
			Runnable task;
			synchronized (this) {
				while(events.isEmpty()) {
					try {
						wait();
					}
					catch(InterruptedException e) {
						return;
					}
				}
				task = events.remove();
			}
			task.run();	
		}
	}
	

}