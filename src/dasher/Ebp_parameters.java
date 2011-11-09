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

/**
 * Enumeration of possible boolean parameter references. See
 * CParamTables for a list and definitions.
 */
public enum Ebp_parameters implements EParameters {
			  BP_REMAP_XTREME("RemapXtreme", false, "Remap y & limit x at top/bottom extremes"),
			  BP_DRAW_MOUSE_LINE("DrawMouseLine", true, "Draw Mouse Line"),
			  BP_DRAW_MOUSE("DrawMouse", true, "Draw Mouse Position"),
			  BP_SHOW_SLIDER("ShowSpeedSlider", true, "ShowSpeedSlider"),
			  BP_START_MOUSE("StartOnLeft", true, "StartOnLeft"),
			  BP_START_SPACE("StartOnSpace", false, "StartOnSpace"),
			  BP_STOP_IDLE("StopOnIdle", false, "StopOnIdle"),
			  BP_KEY_CONTROL("KeyControl", false, "KeyControl"),
			  BP_CONTROL_MODE("ControlMode", false, "ControlMode"),
			  BP_COLOUR_MODE("ColourMode", true, "ColourMode"),
			  BP_MOUSEPOS_MODE("StartOnMousePosition", false, "StartOnMousePosition"),
			  BP_OUTLINE_MODE("OutlineBoxes", true, "OutlineBoxes"),
			  BP_AUTOCALIBRATE("AutoAdjust", false, "Auto-adjust offset for eyetracker miscalibration"),
			  BP_LM_DICTIONARY("Dictionary", true, "Whether the word-based language model uses a dictionary"),
			  BP_AUTO_SPEEDCONTROL("AutoSpeedControl", true, "AutoSpeedControl"),
			  BP_LM_ADAPTIVE("LMAdaptive", true, "Whether language model should learn as you enter text"),
			  BP_CIRCLE_START("CircleStart", false, "Start on circle mode"),
			  BP_LM_REMOTE("RemoteLM", false, "Language model is remote and responds asynchronously."),
			  BP_ONE_DIMENSIONAL_MODE("OneDimensionalMode", false, "Remap x/y to radius / curve all around origin"),
			  BP_ONE_BUTTON_RELEASE_TIME("OneButtonReleaseTime", false, "Use length of single push, not gap, for 1B-dynamic mode"),
			  BP_CONTROL_MODE_REBUILD("ControlModeRebuild",true,"Replace control nodes that have happened with characters to left of cursor"),
			  BP_CONTROL_MODE_HAS_MOVE("ControlModeHasMove",true,"Include nodes to move cursor"),
			  BP_MOVE_REBUILD_IMMED("ControlMoveRebuildImmed",false,"Rebuild move nodes immediately rather than on commit"),
			  BP_CONTROL_MODE_ALPH_SWITCH("ControlModeHasAlphSwitch",true,"Include nodes to switch to previous four alphabets"),
			  BP_CONTROL_MODE_HAS_SPEED("ControlModeHasSpeed",true,"Include nodes to change speed up/down")
			  ;


			  private Ebp_parameters(String rName, boolean def, String hr) {
				humanReadable = hr;
				defaultVal = def;
				regName = rName;
				BY_NAME.put(regName,this);
			}

			public int key() {return ordinal();}
			public String regName() {return regName;}
			public void reset(CSettingsStore ss) {ss.SetBoolParameter(this, defaultVal);}
			private final  String regName;
			final boolean defaultVal;
			final String humanReadable;
}