
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
package dasher.applet;

import java.awt.Font;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ListIterator;
import java.util.Timer;
import java.util.TimerTask;

import java.awt.datatransfer.Clipboard;
import java.io.InputStream;

import javax.swing.*;

import dasher.*;

/**
 * Applet containing a JDasherScreen panel, a JDasherEdit TextBox,
 * and a set of menus to set relevant parameters.
 *
 */
public class JDasherApplet extends JApplet implements MouseListener, KeyListener, JDasherMenuBarListener, dasher.applet.font.FontListener {

	/**
	 * Instance of Dasher which does the work
	 */
	private JDasher Dasher;

	/**
	 * Panel object which will reflect those drawings on the GUI
	 */
	private JDasherPanel panel;
	
	private JDasherThread worker;
	
	/**
	 * Our menu bar
	 */
	private JDasherMenuBar MenuBar;
	
	/**
	 * Overlay to display when Dasher is locked
	 */
	private ScreenOverlay ProgressMeter;
	
	/**
	 * Edit box in which typed text appears
	 */
	private JDasherEdit EditBox;
	
	/**
	 * Clipboard object
	 */
	public Clipboard m_Clipboard;
	
	/**
	 * Scheduling agent used to cue new frames
	 */
	private Timer taskScheduler;
	
	/**
	 * Date of last build; appears in About box
	 */
	public final String buildDate = "22:17 08/10/2008";
					
	/**
	 * Instantiates Dasher, gets a handle to the system clipboard
	 * if possible, calls constructGUIPanel to produce our GUI,
	 * and informs Dasher of the Screen Panel created by this method
	 * using the ChangeScreen method.
	 * <p>
	 * Finally, we call constructMenus to produce our menu bar.
	 */
	public void init() {
				
		Dasher = new JDasher() {
			@Override
			protected void CreateModules() {
				super.CreateModules();
				JMouseInput m_MouseInput = new JMouseInput(this, getSettingsStore());
				RegisterModule(m_MouseInput);
				JDasherApplet.this.panel.addMouseMotionListener(m_MouseInput);
			}
			
			/**
			 * External event handler; simply passes the event to our
			 * host, typically a JDasherApplet.
			 * 
			 * @param Event Event to handle
			 */
			@Override
			public void InsertEvent(CEvent event) {
				JDasherApplet.this.handleEvent(event);
				super.InsertEvent(event);
			}

			/**
			 * Attempts to create a JSettings object; if a StoreUnavailableException
			 * is produced in the course of this, we fall back and produce a 
			 * standard CSettingsStore.
			 */
			@Override
			protected CSettingsStore createSettingsStore() {
				try {
					return new JSettings(this);
				} catch (StoreUnavailableException e) {
					// We can't use the registry/config file due to security problems.
					return new CSettingsStore(this);
				}
			}

			/**
			 * Orders our host to redraw.
			 * 
			 * @param bChanged ignored
			 */
			public void Redraw(boolean bChanged) {
				JDasherApplet.this.repaint();
			}
			
			/** Try to get an InputStream for the file of the specified name, in
			 * each/all locations (system, per-user, etc.). This implementation
			 * <em>only</em> looks for a packaged resource of the specified name,
			 * i.e. in the same directory as this class file (inc. package). TODO
			 * A better approach would be to try to read files from the webserver
			 * serving up our codebase, using an HttpUrlConnection.
			 */
		    @Override
			protected void GetStreams(String fname, Collection<InputStream> into) {
				InputStream in = getClass().getResourceAsStream(fname);
				if (in!=null) into.add(in);
			}

		    /** Looks for files whose name begins with the specified prefix and
		     * ends with ".xml".
			 * Due to the difficulty in enumerating the contents of a JAR
			 * file, at present this just uses those filenames hardcoded in
			 * {@link JDasherApplet#fileList}. TODO ideally we should
			 * read the file list from some fixed location on the web (outside
			 * the .jar), and have that autogenerated from a directory listing. 
		     */
			@Override
			protected void ScanXMLFiles(XMLFileParser parser, String prefix) {
				for (String s : fileList) {
					if (!s.startsWith(prefix) || !s.endsWith(".xml")) continue;
					InputStream in = getClass().getResourceAsStream(s);
					if (in==null) {
						System.out.println("No inputstream could be obtained for "+s);
						continue;
					}
					try {
						parser.ParseFile(in, false);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			
			@Override
			public void deleteText(String ch, double prob) {
				EditBox.deleteText(ch);
			}

			@Override
			public ListIterator<Character> getContext(int iOffset) {
				return EditBox.getContext(iOffset);
			}

			@Override
			public void outputText(String ch, double prob) {
				EditBox.outputText(ch);
			}
			
		};
		
		/* Try to create a clipboard */
		
		try {
			m_Clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
		}
		catch(Exception e) {
			System.out.printf("Exception retrieving the system clipboard: %s%n", e.toString());
			m_Clipboard = new java.awt.datatransfer.Clipboard("Private clipboard");
		}
		
		ProgressMeter = new ScreenOverlay();
		
		//construct the GUI...
		getContentPane().add(constructGUIPanel(this.getSize()));
		
		//Start training...
		Dasher.Realize();
		worker.start();
		
		//Don't call ChangeScreen here - it'll get called automatically
		// by the renderer when it first sees the size of the panel.
				
		//TimerTask doFrame = new DoFrameTask(this);
		//taskScheduler = new Timer();
		//taskScheduler.scheduleAtFixedRate(doFrame, 0, 20);
		
		/* Simple threaded scheduling. The C++ version calls NewFrame directly
		 * every 20 ms as part of a message-processing loop. This is not entirely
		 * suitable for Java as we need the Applet to be idle on some occasions
		 * so that we can process the MouseEvents detailing where the pointer
		 * is at present. This is actually much less efficient, but I don't think
		 * we can ask "where is the pointer now?" in Java, as in C++; 
		 * we must use a MouseMotionListener.
		 */
		
		panel.addMouseListener(this);
		this.addKeyListener(this);
				
		/* The applet itself will handle MouseEvents relating to clicks; these will
		 * be fed into the interface via KeyDown, which also accounts for mouse-clicks.
		 * Further it handles actual keyboard events. The code for this may be moved
		 * out to some dedicated event-handler code in the future, but there is no
		 * particular reason to do so other than for tidiness' sake.
		 */
		
		/* Next, make our menus */
		
		// (Parameter-holding object, some action-listener, some item-listener)
		
		MenuBar = constructMenus();
		
		this.setJMenuBar(MenuBar);
	}
	
	/**
	 * Sets up a Panel containing the entire Applet's GUI, and
	 * returns the Panel.
	 * <p>
	 * The Panel contains a JDasherScreen (which is stored in the
	 * Screen variable) and a JDasherEdit (stored in EditBox).
	 * 
	 * @param size Size of the panel to be created
	 * @return Created Panel
	 */
	private JPanel constructGUIPanel(java.awt.Dimension size) {

		JPanel GUIPanel = new JPanel();

		GUIPanel.setSize(size);

		GUIPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10,10,10,10));

		GUIPanel.setLayout(new javax.swing.BoxLayout(GUIPanel, javax.swing.BoxLayout.Y_AXIS));

		java.awt.Dimension EditSize = new java.awt.Dimension(GUIPanel.getWidth() - 20, GUIPanel.getHeight() / 10);

		worker = new JDasherThread(Dasher);
		panel = new JDasherPanel(worker);

		GUIPanel.add(panel);

		/* The Screen is a specialisation of JPanel. All drawing is done by
		 * its paintComponent method. This is different to the original Dasher,
		 * which painted in a bottom-up method, with Dasher causing its Screen
		 * obejct to me modified; here the Screen object is very much in charge,
		 * and Dasher is invoked to determine its appearance.
		 */

		EditBox = new JDasherEdit(3,80, Dasher);

		EditBox.setLineWrap(true);

		JScrollPane EditScroll = new JScrollPane(EditBox);

		EditScroll.setPreferredSize(EditSize);
		EditScroll.setMaximumSize(EditSize);
		EditScroll.setMinimumSize(EditSize);
		EditScroll.setSize(EditSize);

		/* The EditBox is a specialisation of a JTextPane, but a much lighter
		 * one than the Screen. */

		GUIPanel.add(EditScroll);

		return GUIPanel;

	}
	
	private static final String[] fileList = new String[] {
		"alphabet.english.xml",
		"alphabet.englishC.xml",
		"alphabet.Thai.xml",
		"colour.euroasian.xml",
		"colour.rainbow.xml",
		"colour.euroasian-new.xml",
		"colour.thai.xml"};
	
	/**
	 * Produces our menu bar (a JDasherMenuBar) and returns it.
	 * <p>
	 * After instantiating, its options are set from Dasher's
	 * current settings. For example, we cause the current Alphabet
	 * to be set as 'selected' in the menu bar.
	 * 
	 * @return MenuBar to be added to our Applet
	 */
	private JDasherMenuBar constructMenus() {
		
		JDasherMenuBar MenuBar = new JDasherMenuBar(this);
		
		MenuBar.setColour(Dasher.GetStringParameter(Esp_parameters.SP_COLOUR_ID));
		
		MenuBar.setAlphabet(Dasher.GetStringParameter(Esp_parameters.SP_ALPHABET_ID));
		
		MenuBar.setSelectedFontSize((int)Dasher.GetLongParameter(Elp_parameters.LP_DASHER_FONTSIZE));
		MenuBar.setInputFilter(Dasher.GetStringParameter(Esp_parameters.SP_INPUT_FILTER));
		MenuBar.setMouseLine(Dasher.GetBoolParameter(Ebp_parameters.BP_DRAW_MOUSE_LINE));
		MenuBar.setStartMouse(Dasher.GetBoolParameter(Ebp_parameters.BP_START_MOUSE));
		MenuBar.setStartSpace(Dasher.GetBoolParameter(Ebp_parameters.BP_START_SPACE));
		MenuBar.setSpeedAuto(Dasher.GetBoolParameter(Ebp_parameters.BP_AUTO_SPEEDCONTROL));
		MenuBar.setLangModelLearns(Dasher.GetBoolParameter(Ebp_parameters.BP_LM_ADAPTIVE));
		
		m_Clipboard.addFlavorListener(MenuBar);
		
		return MenuBar;
		
	}

	/**
	 * The Applet responds to the following parameter events:
	 * <p>
	 * <i>BP_DASHER_PAUSED</i>: We start/stop requesting frames
	 * at a regular interval depending on whether Dasher is
	 * currently paused.
	 * <p>
	 * <i>LP_LANGUAGE_MODEL_ID</i>: Updates our MenuBar's currently
	 * selected language model to reflect that which has been
	 * chosen.
	 * <p>
	 * <i>SP_COLOUR_ID</i>: Updates our MenuBar's currently selected
	 * colour scheme to reflect that which has been chosen. Usually
	 * this occurs in response to an alphabet specifying its own
	 * colour scheme.
	 * <p>
	 * The Applet also responds to LockEvents by showing our ScreenOverlay
	 * when locked and updating its progress bar, and MessageEvents
	 * by showing a message dialog.
	 * <p>
	 * Finally we pass the event on to the EditBox, in case some
	 * handling is required there as well. 
	 */
	/*private*/ void handleEvent(dasher.CEvent event) {
		
		if(event instanceof CParameterNotificationEvent) { // Parameter change notification
			
			CParameterNotificationEvent evt = (dasher.CParameterNotificationEvent)event;
			
			if(evt.m_iParameter == dasher.Ebp_parameters.BP_DASHER_PAUSED) {
				if(Dasher.GetBoolParameter(dasher.Ebp_parameters.BP_DASHER_PAUSED)) {
					taskScheduler.cancel();
					taskScheduler = null;
				}
				else {
					taskScheduler = new Timer();
					taskScheduler.scheduleAtFixedRate(new TimerTask() {
						public void run() {
							JDasherApplet.this.repaint();
						}
					}, 0, 20);
				}
			}
			else if(evt.m_iParameter == dasher.Esp_parameters.SP_COLOUR_ID) {
				if(MenuBar != null) MenuBar.setColour(Dasher.GetStringParameter(dasher.Esp_parameters.SP_COLOUR_ID));
			}
			else if (evt.m_iParameter == dasher.Esp_parameters.SP_ALPHABET_ID) {
				if (MenuBar!=null) MenuBar.setAlphabet(Dasher.GetStringParameter(dasher.Esp_parameters.SP_ALPHABET_ID));
			}
			
		}
		else if(event instanceof CLockEvent) { // Lock event (training progress report) 
			CLockEvent evt = (dasher.CLockEvent)event;
			
			if(evt.m_bLock) {
				ProgressMeter.setVisible(true);
				ProgressMeter.setProgressBarVisible(true);
				
				try { 
					java.awt.Point myloc = this.getLocationOnScreen();
					ProgressMeter.setLocation(((myloc.x + this.getWidth()) / 2) - 100, ((myloc.y + this.getHeight()) / 2) - 50);
				}
				catch(Exception e) {
					// ignore; this means we're not visible.
				}
							
				ProgressMeter.setText(evt.m_strMessage);
				
				ProgressMeter.setProgress(evt.m_iPercent, 100);
			}
			else {
				ProgressMeter.setVisible(false);
			}
			
			
		}
		else if(event instanceof CMessageEvent) { // Requested message display
			
			dasher.CMessageEvent evt = (dasher.CMessageEvent)event;
			
			JOptionPane.showMessageDialog(this, evt.m_strMessage, "JDasher", getMessageType(evt.m_iType));
			
		}
	}
	
	/**
	 * Converts Dasher's message dialog types into JOptionPane's
	 * constants with the same meaning. For example, type 0 becomes
	 * JOptionPane.INFORMATION_MESSAGE.
	 * <p>
	 * If asked to convert an invalid message type, -1 is returned. 
	 * 
	 * @param type Type to convert.
	 * @return JOptionPane equivalent constant
	 */
	private int getMessageType(int type) {
		
		// Convert internal message types to those used by JOptionPane.
		
		if(type == 0) return JOptionPane.INFORMATION_MESSAGE;
		if(type == 1) return JOptionPane.WARNING_MESSAGE;
		if(type == 2) return JOptionPane.ERROR_MESSAGE;
		
		return -1;
	}
	
	/**
	 * Ignored. MousePressed and Released are handled separately.
	 */
	public void mouseClicked(MouseEvent e) {
	}

	/**
	 * Ignored
	 */
	public void mouseEntered(MouseEvent arg0) {
	}

	/**
	 * Ignored
	 */
	public void mouseExited(MouseEvent arg0) {
	}
	
	public void mousePressed(MouseEvent arg0) {
		Dasher.KeyDown(System.currentTimeMillis(), 100);
	}

	public void mouseReleased(MouseEvent arg0) {
		Dasher.KeyUp(System.currentTimeMillis(), 100);
	}
	
	/**
	 * We respond to the following key presses:
	 * <p>
	 * CTRL: Set speed boost factor to 175<br>
	 * SHIFT: Set speed boost factor to 25.
	 * </p>
	 */	
	public void keyPressed(KeyEvent e) {
		
		if(e.getKeyCode() == KeyEvent.VK_CONTROL) {
			worker.addTasklet(new Runnable() {
				public void run() {
					Dasher.SetLongParameter(Elp_parameters.LP_BOOSTFACTOR,175);
				}
			});
			
		}
		
		// Speed boost for pressing CTRL. Should this be in the interface?
		
		if(e.getKeyCode() == KeyEvent.VK_SHIFT) {
			worker.addTasklet(new Runnable() {
				public void run() {
					Dasher.SetLongParameter(Elp_parameters.LP_BOOSTFACTOR,25);
				}
			});
		}
		
		// Speed reduced when SHIFT pressed. As above?
		
	}
	
	/**
	 * Upon releasing the space bar, we signal Dasher a KeyDown
	 * event with a key of zero.
	 * <p>
	 * If either CTRL or SHIFT are released, the speed boost
	 * constant is reset to 100, 175 or 25, dependent on which
	 * keys are still down.
	 */
	public void keyReleased(KeyEvent e) {
		
		/* Dasher will start when SPACE is pressed. */
		
		if(e.getKeyCode() == KeyEvent.VK_SPACE) {
			Dasher.KeyDown(System.currentTimeMillis(), 0);
		}
		
		
		/* This completes the boost-key implementation by considering
		 * whether the other boost key is currently pressed when one
		 * is released.
		 */
		
		if(e.getKeyCode() == KeyEvent.VK_CONTROL) {
			if(e.isShiftDown()) {
				worker.addTasklet(new Runnable() {
					public void run() {
						Dasher.SetLongParameter(Elp_parameters.LP_BOOSTFACTOR,25);
					}
				});
			}
			else {
				worker.addTasklet(new Runnable() {
					public void run() {
						Dasher.SetLongParameter(Elp_parameters.LP_BOOSTFACTOR,100);
					}
				});
			}
		}
		if(e.getKeyCode() == KeyEvent.VK_SHIFT) {
			if(e.isControlDown()) {
				worker.addTasklet(new Runnable() {
					public void run() {
						Dasher.SetLongParameter(Elp_parameters.LP_BOOSTFACTOR,175);
					}
				});
			}
			else {
				worker.addTasklet(new Runnable() {
					public void run() {
						Dasher.SetLongParameter(Elp_parameters.LP_BOOSTFACTOR,100);
					}
				});
			}
		}
	}

	/**
	 * Ignored
	 */
	public void keyTyped(KeyEvent arg0) {
	}

	/**
	 * Cancels our new frame scheduler, and calls Dasher's
	 * DestroyInterface method to give it an opportunity to
	 * clean up if necessary.
	 * <p>
	 * Ultimately any neglected cleaning is likely not to
	 * cause a problem, as we are about to stop the application.
	 */
	public void stop() {
		if(taskScheduler != null) taskScheduler.cancel();
		Dasher.DestroyInterface();
	}
	
	/**
	 * Sets our edit box font
	 * 
	 * @param f New font
	 */
	public void setNewFont(Font f) {
		
		EditBox.setFont(f);
		
	}

	/**
	 * Copies the current edit box selection to the clipboard if
	 * possible.
	 */
	public void menuCopy() {
		try {
			m_Clipboard.setContents(new java.awt.datatransfer.StringSelection(EditBox.getSelectedText()), null);
		}
		catch (Exception ex) {
			System.out.printf("Copy to clipboard failed: %s%n", ex.toString());
		}
	}
	
	/**
	 * Cuts the current edit box selection to the clipboard if
	 * possible.
	 */
	public void menuCut() {
		try {
			m_Clipboard.setContents(new java.awt.datatransfer.StringSelection(EditBox.getSelectedText()), null);
			EditBox.replaceSelection("");
		}
		catch (Exception ex) {
			System.out.printf("Cut to clipboard failed: %s%n", ex.toString());
		}
	}

	/**
	 * Quits Dasher
	 */
	public void menuExit() {
		System.exit(0); // Should make this tidier...
	}

	/**
	 * Displays an About dialog showing the compilation date.
	 */
	public void menuHelpAbout() {
		
		JOptionPane.showMessageDialog(this, "JDasher\nVersion compiled: " + buildDate);
		
	}

	/**
	 * Blanks the EditBox and invalidates our current context.
	 */
	public void menuNew() {
		this.EditBox.setText("");
		//??? done by EditBox? this.Dasher.SetOffset(0,true); //InvalidateContext(true);
	}

	/**
	 * Attempts to paste from the clipboard, overwriting our current
	 * EditBox selection if there is one.
	 */
	public void menuPaste() {
		try {
			java.awt.datatransfer.Transferable clipContents = m_Clipboard.getContents(null);
			String temp = (String)clipContents.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
			EditBox.replaceSelection(temp);
		}
		catch (Exception ex) {
			System.out.printf("Paste from clipboard failed: %s%n", ex.toString());
		}
	}

	/**
	 * Opens the Select Font dialog in order to choose a new
	 * EditBox font
	 */
	public void menuSelFont() {
		new dasher.applet.font.JFontDialog(this, EditBox.getFont());
	}
	
	public void menuSetString(final Esp_parameters param, final String val) {
		worker.addTasklet(new Runnable() {
			public void run() {
				Dasher.SetStringParameter(param, val);
			}
		});
	}
	
	public void menuSetLong(final Elp_parameters param, final long val) {
		worker.addTasklet(new Runnable() {
			public void run() {
				Dasher.SetLongParameter(param, val);
			}
		});
	}
	
	public void menuSetBool(final Ebp_parameters param, final boolean val) {
		worker.addTasklet(new Runnable() {
			public void run() {
				Dasher.SetBoolParameter(param, val);
			}
		});
	}
	
	/**
	 * Checks with the clipboard whether a given data flavour
	 * is enabled.
	 * <p>
	 * Typically this is used to check whether it is sensible
	 * to attempt Paste at the moment.
	 * 
	 * @param flavour Flavour to check availability
	 * @return True if available at present, false otherwise. 
	 */
	public boolean isDataFlavorAvailable(java.awt.datatransfer.DataFlavor flavour) {
		return m_Clipboard.isDataFlavorAvailable(flavour);
	}
	
	public void GetPermittedValues(Esp_parameters param,Collection<String> into) {
		Dasher.GetPermittedValues(param, into);
	}
	
	/**
	 * Retrieves the current EditBox text. This method exists
	 * for the purposes of JavaScript calling in to invoke unsafe
	 * functions.
	 * <p>
	 * Thankfully, this doesn't work on FireFox.
	 * 
	 * @return Current EditBox contents
	 */
	public String getCurrentEditBoxText() {
		
		return EditBox.getText();
		
	}
}
