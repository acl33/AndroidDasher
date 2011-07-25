/*
  This file is part of JDasher.

  JDasher is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  JDasher is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with JDasher; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

  Copyright (C) 2006      Christopher Smowton <cs448@cam.ac.uk>

  JDasher is a port derived from the Dasher project; for information on
  the project see www.dasher.org.uk; for information on JDasher itself
  and related projects see www.smowton.net/chris

*/

package dasher;

import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.HashSet;
import java.util.WeakHashMap;

/**
 * <p>The Observable class provides a publish/subscribe API for some particular type of event.
 * The class manages a list of Observer<T>s, and allows subclasses (only) to broadcast T's
 * to all registered Observers. (Note, no guarantees are provided on the order of delivery
 * to different Observers.)</p>  
 * 
 * <p>However, note (in contrast to C++ Dasher): we keep only WeakReferences to the Observers,
 * such that registration does not prevent an observer subsequently being GC'd if it is not
 * otherwise reachable. Moreover, we have no deregistration method: Observers are "removed"
 * _only_ by becoming unreachable and being garbage collected. <strong>This means observers
 * should make only changes local to themselves in response to an event.</strong></p>
 * 
 * <p>However, there are a couple of dasher-specific features:</p>
 * <ul>
 * <li>The special case of a listener tries to register itself whilst an event is being
 * handled (for example, a parameter change caused a new observer to be created and
 * registered): here the addition will be delayed until _all_ current broadcasts have completed.
 * (That is, observers added during an event broadcast, will not see any events until
 * <em>all</em> those which were in progress when it registered have finished.)
 * <li>We also allow each Observable to have exactly one, distinguished, listener which
 * receives all events <em>after</em> all other listeners - set by {@link #setPostListener}.
 * Note we keep a strong reference to this, which can be changed by calling the setter at any time,
 * which will have effect immediately (i.e. any events currently in progress, which have not
 * already called the post-listener, will call to the new one).
 * </ul>
 * 
 * <p>It is also possible to cause the program to enter a tight
 * loop by creating a direct or indirect loop of dispatched events;
 * Components should be careful to avoid causing this situation by
 * avoiding creating events during their HandleEvent procedures which
 * are likely to cause the same event to be re-raised unless they
 * can be certain of stopping the loop at some future point.</p>
 */
public class Observable<T> {
	private static final Object PRESENT = new Object();
	/** The listener to call after all others */
	private Observer<T> last;
	
	/** Maps every currently-active listener to {@link #PRESENT} */
	protected final WeakHashMap<Observer<T>,Object> m_vListeners = new WeakHashMap<Observer<T>, Object>();
	
	/** Listeners waiting to be added after all in-progress events are finished */
	protected final Set<Observer<T>> m_vListenerQueue = new HashSet<Observer<T>>();

	/**
	 * Integer indicating how many times we are 'in' the event
	 * handler (for example, we might be in twice if a component
	 * responded to an event by issuing an event of its own). 
	 */
	private int m_iInHandler;
	
	/** Sets the observer which will be called last with each event,
	 * replacing any previous.
	 * @param last Listener to call last (or null =&gt; don't call any last)
	 */
	public void setLastListener(Observer<T> last) {
		this.last = last;
	}
	
	/**
	 * Informs all registered listeners of a specified Event.
	 * <p>
	 * Before beginning, m_iInHandler is incremented to indicate
	 * that one event is currently in progress; when finished,
	 * it is decremented and, if zero, listeners which are queued
	 * up for (de/)registration will be added/removed to/from the
	 * list of active listeners.
	 * 
	 * @param evt Event to dispatch to all registered listeners.
	 */
	protected void InsertEvent(T evt) {

		// We may end up here recursively, so keep track of how far down we
		// are, and only permit new handlers to be registered after all
		// messages are processed.

		// An alternative approach would be a message queue - this might actually be a bit more sensible
		++m_iInHandler;
		  
		// Loop through components and notify them of the event
		  		  
		for(Observer<T> o : m_vListeners.keySet()) {
		    o.HandleEvent(evt);
		}

		if (last!=null) last.HandleEvent(evt);
		
		--m_iInHandler;
		  
		if(m_iInHandler == 0) {			  
			for(Observer<T> o : m_vListenerQueue)
				if (m_vListeners.containsKey(o)) m_vListeners.remove(o); else m_vListeners.put(o,PRESENT);
			m_vListenerQueue.clear();
		}
	}

	/**
	 * Registers a given component as an event listener.
	 * <p>
	 * In the event that one or more events are currently in progress,
	 * it will be added to a queue of pending listeners and will
	 * be added when InsertEvent finishes for the last time.
	 * 
	 * @param pListener Component to add as a listener
	 */
	public void RegisterListener(Observer<T> obsvr) {

		if(m_vListeners.containsKey(obsvr) || m_vListenerQueue.contains(obsvr)) //already there, or pending addition
			return;
		if (m_iInHandler>0)
			m_vListenerQueue.add(obsvr);
		else
			m_vListeners.put(obsvr, PRESENT);
	}

}
