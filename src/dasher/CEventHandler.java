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

import java.util.Set;
import java.util.HashSet;

/**
 * The EventHandler class
 * <OL>
 * <LI> Allows DasherComponents to register themselves as event listeners,
 * <LI> Allows Components to unregister themselves when they are being destroyed.
 * <LI> Accepts events from DasherComponents and notifies all other Components
 * using their {@link CDasherComponent#HandleEvent(CEvent)} method. (Note, there is no guarantee of the <em>order</em>
 * in which HandleEvents are called, not even that this wil be consistent from one event to the next)
 * <p>
 * The mode of action is to have a set of Listeners to which new listeners are added and old ones removed,
 * and which we iterate through when notified of an event, calling the HandleEvent method of each.
 * <p>
 * There is however a special case: In the event that a listener tries
 * to register or deregister itself whilst an event is being handled (for example,
 * a parameter change caused a Component to be created, or destroyed, which thus tried
 * to (de/)register itself), said component is instead added to a set of listeners
 * who are waiting to be (de/)registered.
 * <p>
 * This is because otherwise there would be some ambiguity as to
 * when a given component would start receiving events, particularly
 * as to whether it would receive that which was being handled
 * when it registered itself; and moreover, it removes any possibility
 * of ConcurrentModificationExceptions!
 * <p>
 * The result is that new listeners will begin receiving (or stop receiving)
 * events only when <em>all</em> those which were in progress when it registered
 * have finished.
 * <p>
 * It is also possible to cause the program to enter a tight
 * loop by creating a direct or indirect loop of dispatched events;
 * Components should be careful to avoid causing this situation by
 * avoiding creating events during their HandleEvent procedures which
 * are likely to cause the same event to be re-raised unless they
 * can be certain of stopping the loop at some future point.
 */
public class CEventHandler {
	  
	/**
	 * Set of currently active listeners
	 */
	protected final Set <CDasherComponent> m_vListeners = new HashSet<CDasherComponent>();
	
	/**
	 * Set of Components waiting to be either added as listeners (if not already present),
	 * or else removed (if already present), when we finish handling events.
	 */
	protected final Set<CDasherComponent> m_vListenerQueue = new HashSet<CDasherComponent>();

	/**
	 * Integer indicating how many times we are 'in' the event
	 * handler (for example, we might be in twice if a component
	 * responded to an event by issuing an event of its own). 
	 */
	private int m_iInHandler;
		
	/**
	 * Informs all registered listeners of a specified Event.
	 * <p>
	 * Before beginning, m_iInHandler is incremented to indicate
	 * that one event is currently in progress; when finished,
	 * it is decremented and, if zero, listeners which are queued
	 * up for (de/)registration will be added/removed to/from the
	 * list of active listeners.
	 * 
	 * @param Event Event to dispatch to all registered listeners.
	 */
	public void InsertEvent(CEvent Event) {

		  // We may end up here recursively, so keep track of how far down we
		  // are, and only permit new handlers to be registered after all
		  // messages are processed.

		  // An alternative approach would be a message queue - this might actually be a bit more sensible
		  ++m_iInHandler;
		  
		  // Loop through components and notify them of the event
		  		  
		  for(CDasherComponent i : m_vListeners) {
		    i.HandleEvent(Event);
		  }

		  --m_iInHandler;
		  
		  if(m_iInHandler == 0) {
			  
			  for(CDasherComponent i : m_vListenerQueue) {
				  if (m_vListeners.contains(i))
					  m_vListeners.remove(i);
				  else
					  m_vListeners.add(i); 
			  }
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
	public void RegisterListener(CDasherComponent pListener) {

		if(m_vListeners.contains(pListener) || m_vListenerQueue.contains(pListener)) return; //already present or waiting
		((m_iInHandler > 0) ? m_vListenerQueue : m_vListeners).add(pListener);
	}

	/**
	 * Removes a given Component from the list of listeners.
	 * <p>
	 * This is necessary before a given Component can be garbage
	 * collected.
	 * 
	 * @param pListener Component to remove
	 */
	public void UnregisterListener(CDasherComponent pListener) {
		if (m_iInHandler==0) {
			assert (m_vListenerQueue.size()==0);
			m_vListeners.remove(pListener);
		}
		//nope, can't do anything now...
		if(m_vListeners.contains(pListener)) {
			if (m_vListenerQueue.contains(pListener)) return; //already enqueued for removal
			m_vListenerQueue.add(pListener);
		} else {
			//not actually a listener atm. So just make sure it isn't added as one later...
			m_vListenerQueue.remove(pListener);
		}
	}

}
