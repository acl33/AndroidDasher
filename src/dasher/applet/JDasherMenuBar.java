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

import java.awt.BorderLayout;
import java.awt.datatransfer.FlavorEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import dasher.CDasherInterfaceBase;
import dasher.EParameters;
import dasher.Ebp_parameters;
import dasher.Elp_parameters;
import dasher.Esp_parameters;
import dasher.Observer;

/** 
 * The menu bar is entirely dumb. Its purpose is to do the donkey work of setting
 * up the menus; it then simply listens for events and calls the appropriate methods
 * belonging to its host. It is the responsibility of the host to ensure it is
 * properly set up to begin with, including setting which options are selected
 * at application startup.
 * <p>
 * Members and functions not documented; largely everything means
 * what its name suggests. If new menu items are desired, one should
 * create a new local variable referencing the new menu items,
 * and add lines to the Constructor which create the new menus.
 */
public class JDasherMenuBar extends JMenuBar implements ActionListener, ItemListener, java.awt.datatransfer.FlavorListener {
	
	private JMenu file, edit, options, control, help;
	
	private JMenuItem file_new, file_exit;
	
	private JMenuItem edit_cut, edit_copy, edit_paste;
	
	//private JMenu control_speed; 
	private JRadioButtonMenuItem control_speed_slow, control_speed_medium, control_speed_fast, control_speed_fastest;
	private JCheckBoxMenuItem control_speed_auto;
	
	private ButtonGroup control_style_group;
	private JCheckBoxMenuItem control_mousestart, control_spacestart;
		
	private JMenuItem options_editfont;
	private JRadioButtonMenuItem options_fontsize_small, options_fontsize_medium, options_fontsize_large;
	private JCheckBoxMenuItem options_mouseline;
	
	private JCheckBoxMenuItem prediction_langmodel_learn;
	
	private JMenuItem help_about;
	
	private ButtonGroup options_colours_group;
	
	private final JDasherMenuBarListener m_Host;
	/* Pointer to dasher interface. We use this for <strong>reading</strong>
	 * parameters only, not writing, as we are on the Swing GUI thread,
	 * not the JDasherThread (worker). Technically, we should synchronize
	 * reads as well (i.e. by scheduling tasklets to call Get...Param), but
	 * not bothering yet. Writing parameters is done via {@link #m_Host},
	 * as that schedules tasks on the worker thread.
	 */
	private final CDasherInterfaceBase iface;
	/** Listens to settings changes to keep menus updated. We need
	 * a strong ref as the SettingsStore keeps only a weak ref.
	 */
	@SuppressWarnings("unused")
	private final Observer<EParameters> lstnr;
	
	/**
	 * Creates a JDasherMenuBar which signals a given listener
	 * when the user selects menu items.
	 * 
	 * @param listener Listener whose methods are to be
	 * invoked upon user commands
	 */
	public JDasherMenuBar(CDasherInterfaceBase _iface, JDasherMenuBarListener host) {
		m_Host = host;
		iface=_iface;
		lstnr = new dasher.CDasherComponent(iface) {
			@Override public void HandleEvent(EParameters eParam) {
				if(eParam == dasher.Esp_parameters.SP_COLOUR_ID) {
					setColour(GetStringParameter(dasher.Esp_parameters.SP_COLOUR_ID));
				}
			}
		};
			
		file = new JMenu("File"); this.add(file);
		edit = new JMenu("Edit"); this.add(edit);
		options = new JMenu("Options"); this.add(options);
		control = new JMenu("Control"); this.add(control);
		help = new JMenu("Help"); this.add(help);
		
		file_new = new JMenuItem("New"); file.add(file_new); file_new.addActionListener(this);
		file_exit = new JMenuItem("Exit"); file.add(file_exit); file_exit.addActionListener(this);
		
		edit_cut = new JMenuItem("Cut"); edit.add(edit_cut); edit_cut.addActionListener(this);
		edit_copy = new JMenuItem("Copy"); edit.add(edit_copy); edit_copy.addActionListener(this);
		edit_paste = new JMenuItem("Paste"); edit.add(edit_paste); edit_paste.addActionListener(this);
		
		edit_paste.setEnabled(false);
				
		options_editfont = new JMenuItem("Select Font..."); options.add(options_editfont); options_editfont.addActionListener(this);
		
		JMenu options_fontsize = new JMenu("Dasher Font Size"); options.add(options_fontsize);
			
		ButtonGroup options_fontsize_group = new ButtonGroup();
		options_fontsize_large = new JRadioButtonMenuItem("Large"); options_fontsize_group.add(options_fontsize_large); options_fontsize.add(options_fontsize_large); options_fontsize_large.addActionListener(this);
		options_fontsize_medium = new JRadioButtonMenuItem("Medium"); options_fontsize_group.add(options_fontsize_medium); options_fontsize.add(options_fontsize_medium); options_fontsize_medium.addActionListener(this);
		options_fontsize_small = new JRadioButtonMenuItem("Small"); options_fontsize_group.add(options_fontsize_small); options_fontsize.add(options_fontsize_small); options_fontsize_small.addActionListener(this);
		
		options_mouseline = new JCheckBoxMenuItem("Display Mouse Line"); options.add(options_mouseline); options_mouseline.addItemListener(this);
		
		JMenuItem options_alphabet = new JMenuItem("Alphabet..."); options.add(options_alphabet); 

		options_alphabet.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				List<String> alphs = new ArrayList<String>();
				iface.GetPermittedValues(Esp_parameters.SP_ALPHABET_ID, alphs);
				Collections.sort(alphs);
				final JList list = new JList(alphs.toArray(new String[alphs.size()]));
				list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
				list.setSelectedValue(iface.GetStringParameter(Esp_parameters.SP_ALPHABET_ID), true);
				list.addListSelectionListener(new ListSelectionListener() {
					public void valueChanged(ListSelectionEvent e) {
						String alph = (String)list.getModel().getElementAt(list.getSelectedIndex());
						m_Host.menuSetString(Esp_parameters.SP_ALPHABET_ID, alph);
					}
				});
				final JDialog dia = new JDialog((java.awt.Frame)null,"Select Alphabet",true);
				dia.setSize(300, 400);
				dia.getContentPane().add(new JScrollPane(list));
				JPanel panel = new JPanel();
				JButton b=new JButton("OK");
				b.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						dia.setVisible(false);
					}
				});
				panel.add(b);
				final String origAlph = iface.GetStringParameter(Esp_parameters.SP_ALPHABET_ID);
				b=new JButton("Cancel");
				b.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						m_Host.menuSetString(Esp_parameters.SP_ALPHABET_ID, origAlph);
						dia.setVisible(false);
					}
				});
				panel.add(b);
				dia.getContentPane().add(panel,BorderLayout.SOUTH);
				dia.setVisible(true);
			}
			
		});
		
		JMenu options_colours = new JMenu("Colour Scheme"); options.add(options_colours);
		options_colours_group = new ButtonGroup();
		populateGroup(options_colours, options_colours_group, Esp_parameters.SP_COLOUR_ID, new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					m_Host.menuSetString(Esp_parameters.SP_COLOUR_ID,e.getActionCommand());
				}
			});
		
		JMenu control_style = new JMenu("Control Style"); control.add(control_style);
		control_style_group = new ButtonGroup();
		populateGroup(control_style, control_style_group, Esp_parameters.SP_INPUT_FILTER, new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				m_Host.menuSetString(Esp_parameters.SP_INPUT_FILTER,e.getActionCommand());
			}
			
		});
		
		control_mousestart = new JCheckBoxMenuItem("Start on Mouse"); control.add(control_mousestart); control_mousestart.addItemListener(this);
						
		control_spacestart = new JCheckBoxMenuItem("Start on Space"); control.add(control_spacestart); control_spacestart.addItemListener(this);
				
		JMenu control_speed = new JMenu("Dasher Speed"); control.add(control_speed);
		ButtonGroup control_speed_group = new ButtonGroup();
		control_speed_slow = new JRadioButtonMenuItem("Slow"); control_speed_group.add(control_speed_slow); control_speed.add(control_speed_slow); control_speed_slow.addActionListener(this);
		control_speed_medium = new JRadioButtonMenuItem("Normal"); control_speed_group.add(control_speed_medium); control_speed.add(control_speed_medium); control_speed_medium.addActionListener(this);
		control_speed_fast = new JRadioButtonMenuItem("Fast"); control_speed_group.add(control_speed_fast); control_speed.add(control_speed_fast); control_speed_fast.addActionListener(this);
		control_speed_fastest = new JRadioButtonMenuItem("Fastest"); control_speed_group.add(control_speed_fastest); control_speed.add(control_speed_fastest); control_speed_fastest.addActionListener(this);
		control_speed.addSeparator();
		control_speed_auto = new JCheckBoxMenuItem("Auto-adjust"); control_speed.add(control_speed_auto); control_speed_auto.addItemListener(this);
		
		prediction_langmodel_learn = new JCheckBoxMenuItem("Language Model Learns"); options.add(prediction_langmodel_learn); prediction_langmodel_learn.addItemListener(this);
	
		help_about = new JMenuItem("About..."); help.add(help_about); help_about.addActionListener(this);

		//We set up the GUI according to the interface here; we could watch for changes
		// (and for colour, we do), but assume nothing else will change the others. 
		setColour(iface.GetStringParameter(Esp_parameters.SP_COLOUR_ID));
		setSelectedFontSize((int)iface.GetLongParameter(Elp_parameters.LP_DASHER_FONTSIZE));
		setInputFilter(iface.GetStringParameter(Esp_parameters.SP_INPUT_FILTER));
		setMouseLine(iface.GetBoolParameter(Ebp_parameters.BP_DRAW_MOUSE_LINE));
		setStartMouse(iface.GetBoolParameter(Ebp_parameters.BP_START_MOUSE));
		setStartSpace(iface.GetBoolParameter(Ebp_parameters.BP_START_SPACE));
		setSpeedAuto(iface.GetBoolParameter(Ebp_parameters.BP_AUTO_SPEEDCONTROL));
		setLangModelLearns(iface.GetBoolParameter(Ebp_parameters.BP_LM_ADAPTIVE));
	}
	
	public void actionPerformed(ActionEvent e) {
		// Handles all ordinary and radio-button menus.
		
		if(e.getActionCommand().equals("New")) {
			m_Host.menuNew();
		}
		else if(e.getActionCommand().equals("Exit")) {
			m_Host.menuExit();
		}
		else if(e.getActionCommand().equals("Cut")) {
			m_Host.menuCut();
		}
		else if(e.getActionCommand().equals("Copy")) {
			m_Host.menuCopy();
		}
		else if(e.getActionCommand().equals("Paste")) {
			m_Host.menuPaste();
		}
		else if(e.getActionCommand().equals("Select Font...")) {
			m_Host.menuSelFont();
		}
		else if(e.getActionCommand().equals("Large")) {
			m_Host.menuSetLong(Elp_parameters.LP_DASHER_FONTSIZE, 4);
		}
		else if(e.getActionCommand().equals("Medium")) {
			m_Host.menuSetLong(Elp_parameters.LP_DASHER_FONTSIZE, 2);
		}
		else if(e.getActionCommand().equals("Small")) {
			m_Host.menuSetLong(Elp_parameters.LP_DASHER_FONTSIZE, 1);
		}
		else if(e.getActionCommand().equals("Slow")) {
			m_Host.menuSetLong(Elp_parameters.LP_MAX_BITRATE, 100);
			setSpeedAbs();
		}
		else if(e.getActionCommand().equals("Normal")) {
			m_Host.menuSetLong(Elp_parameters.LP_MAX_BITRATE, 200);
			setSpeedAbs();
		}
		else if(e.getActionCommand().equals("Fast")) {
			m_Host.menuSetLong(Elp_parameters.LP_MAX_BITRATE, 400);
			setSpeedAbs();
		}
		else if(e.getActionCommand().equals("Fastest")) {
			m_Host.menuSetLong(Elp_parameters.LP_MAX_BITRATE, 800);
			setSpeedAbs();
		}
		else if(e.getActionCommand().equals("About...")) {
			m_Host.menuHelpAbout();			
		}
	}
	
	public void itemStateChanged(ItemEvent e) {
		JCheckBoxMenuItem tickbox = ((JCheckBoxMenuItem)e.getItem());
		
		if(tickbox.getText().equals("Display Mouse Line")) {
			m_Host.menuSetBool(Ebp_parameters.BP_DRAW_MOUSE_LINE,tickbox.isSelected());
		}
		else if(tickbox.getText().equals("Start on Mouse")) {
			m_Host.menuSetBool(Ebp_parameters.BP_START_MOUSE,tickbox.isSelected());
		}
		else if(tickbox.getText().equals("Start on Space")) {
			m_Host.menuSetBool(Ebp_parameters.BP_START_SPACE, tickbox.isSelected());
		}
		else if(tickbox.getText().equals("Auto-adjust")) {
			m_Host.menuSetBool(Ebp_parameters.BP_AUTO_SPEEDCONTROL, tickbox.isSelected());
			setSpeedAuto(tickbox.isSelected());
		}
		else if(tickbox.getText().equals("Language Model Learns")) {
			m_Host.menuSetBool(Ebp_parameters.BP_LM_ADAPTIVE, tickbox.isSelected());
		}
	}
	
	private void setSelectedFontSize(int size) {
		switch(size) {
		case 1:
			options_fontsize_small.setSelected(true);
			break;
		case 2:
			options_fontsize_medium.setSelected(true);
			break;
		case 4:
			options_fontsize_large.setSelected(true);
			break;
		}
	}
	
	private void setColour(String current) {
		selectInGroup(options_colours_group, current);
	}
	
	private void setInputFilter(String filter) {
		selectInGroup(control_style_group, filter);
	}
	
	private void populateGroup(JMenu menu, ButtonGroup group, Esp_parameters param, ActionListener lstnr) {
		Collection<String> options = new ArrayList<String>();
		iface.GetPermittedValues(param, options);
		for(String ColourName : options) {
			JMenuItem newColour = new JRadioButtonMenuItem(ColourName);
			newColour.addActionListener(lstnr);
			menu.add(newColour);
			group.add(newColour);
		}
	}
	
	private void selectInGroup(ButtonGroup group, String current) {
		for (Enumeration<AbstractButton> e = group.getElements();
			 e.hasMoreElements(); ) {
			AbstractButton b = e.nextElement();
			b.setSelected(b.getActionCommand().equals(current));
		}
	}
	
	private void setMouseLine(boolean enabled) {
		options_mouseline.setSelected(enabled);
	}
	
	private void setStartMouse(boolean enabled) {
		control_mousestart.setSelected(enabled);
	}
	
	private void setStartSpace(boolean enabled) {
		control_spacestart.setSelected(enabled);
	}

	public void setPasteEnabled(boolean enabled) {
		edit_paste.setEnabled(enabled);
	}
	
	private void setSpeedAbs() {
		control_speed_auto.setSelected(false);
	}
	
	private void setSpeedAuto(boolean enabled) {
		control_speed_slow.setSelected(false);
		control_speed_medium.setSelected(false);
		control_speed_fast.setSelected(false);
		control_speed_fastest.setSelected(false);
		control_speed_auto.setSelected(enabled);
	}
	
	private void setLangModelLearns(boolean enabled) {
		prediction_langmodel_learn.setSelected(enabled);
	}
	
	public void flavorsChanged(FlavorEvent arg0) {
		setPasteEnabled(m_Host.isDataFlavorAvailable(java.awt.datatransfer.DataFlavor.stringFlavor));
	}
}
