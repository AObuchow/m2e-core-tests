package org.eclipse.m2e.editor.pom;


import org.eclipse.swt.events.TypedEvent;
import org.eclipse.swt.widgets.Event;

/**
 * Instances of this class are sent whenever the platform-
 * specific trigger for showing a context menu is detected.
 *
 * @see MenuDetectListener
 * @see <a href="http://www.eclipse.org/swt/">Sample code and further information</a>
 *
 * @since 3.3
 */

public final class MenuDetectEvent extends TypedEvent {

	/**
	 * the display-relative x coordinate of the pointer
	 * at the time the context menu trigger occurred
	 */
	public int x;
	
	/**
	 * the display-relative y coordinate of the pointer
	 * at the time the context menu trigger occurred
	 */	
	public int y;
	
	/**
	 * A flag indicating whether the operation should be allowed.
	 * Setting this field to <code>false</code> will cancel the operation.
	 */
	public boolean doit;

	private static final long serialVersionUID = -3061660596590828941L;

/**
 * Constructs a new instance of this class based on the
 * information in the given untyped event.
 *
 * @param e the untyped event containing the information
 */
public MenuDetectEvent(Event e) {
	super(e);
	this.x = e.x;
	this.y = e.y;
	this.doit = e.doit;
}

/**
 * Returns a string containing a concise, human-readable
 * description of the receiver.
 *
 * @return a string representation of the event
 */
public String toString() {
	String string = super.toString ();
	return string.substring (0, string.length() - 1) // remove trailing '}'
		+ " x=" + x //$NON-NLS-1$
		+ " y=" + y //$NON-NLS-1$
		+ " doit=" + doit //$NON-NLS-1$
		+ "}"; //$NON-NLS-1$
}
}
