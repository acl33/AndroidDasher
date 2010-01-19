package dasher;

public interface CMarkerScreen extends CDasherScreen {

	/**
	 * Informs the screen of certain drawing phases.
	 * <p>
	 * A '0' will be sent when beginning to draw persistent
	 * features (ie. those which should remain the same from
	 * frame to frame), and a '1' will be sent prior to drawing
	 * ephemeral details which should vanish if not redrawn next
	 * frame.
	 * 
	 * @param iMarker Marker number
	 */	
	public abstract void SendMarker(int iMarker);
	
	/**
	 * Signals the end of a frame.
	 *
	 */
	public abstract void Display();
}
