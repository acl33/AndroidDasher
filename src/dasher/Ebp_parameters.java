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
			  BP_REMAP_XTREME("RemapXtreme", PERS, false, "Remap y & limit x at top/bottom extremes"),
			  BP_DRAW_MOUSE_LINE("DrawMouseLine", PERS, true, "Draw Mouse Line"),
			  BP_DRAW_MOUSE("DrawMouse", PERS, true, "Draw Mouse Position"),
			  BP_SHOW_SLIDER("ShowSpeedSlider", PERS, true, "ShowSpeedSlider"),
			  BP_START_MOUSE("StartOnLeft", PERS, true, "StartOnLeft"),
			  BP_START_SPACE("StartOnSpace", PERS, false, "StartOnSpace"),
			  BP_STOP_IDLE("StopOnIdle", PERS, false, "StopOnIdle"),
			  BP_KEY_CONTROL("KeyControl", PERS, false, "KeyControl"),
			  BP_CONTROL_MODE("ControlMode", PERS, false, "ControlMode"),
			  BP_COLOUR_MODE("ColourMode", PERS, true, "ColourMode"),
			  BP_MOUSEPOS_MODE("StartOnMousePosition", PERS, false, "StartOnMousePosition"),
			  BP_OUTLINE_MODE("OutlineBoxes", PERS, true, "OutlineBoxes"),
			  BP_PALETTE_CHANGE("PaletteChange", PERS, true, "PaletteChange"),
			  BP_NUMBER_DIMENSIONS("NumberDimensions", PERS, false, "NumberDimensions"),
			  BP_AUTOCALIBRATE("AutoAdjust", PERS, false, "Auto-adjust offset for eyetracker miscalibration"),
			  BP_DASHER_PAUSED("DasherPaused", !PERS, true, "Dasher Paused"),
			  BP_GAME_MODE("GameMode", PERS, false, "Dasher Game Mode"),
			  BP_TRAINING("Training", !PERS, false, "Provides locking during training"),
			  BP_REDRAW("Redraw", !PERS, false, "Force a full redraw at the next timer event"),
			  BP_LM_DICTIONARY("Dictionary", PERS, true, "Whether the word-based language model uses a dictionary"),
			  BP_LM_LETTER_EXCLUSION("LetterExclusion", PERS, true, "Whether to do letter exclusion in the word-based model"),
			  BP_AUTO_SPEEDCONTROL("AutoSpeedControl", PERS, true, "AutoSpeedControl"),
			  BP_LM_ADAPTIVE("LMAdaptive", PERS, true, "Whether language model should learn as you enter text"),
			  BP_BUTTONONESTATIC("ButtonOneStaticMode", PERS, false, "One-button static mode"),
			  BP_BUTTONONEDYNAMIC("ButtonOneDynamicMode", PERS, false, "One-button dynamic mode"),
			  BP_SOCKET_INPUT_ENABLE("SocketInputEnable", PERS, false, "Read pointer coordinates from network socket instead of mouse"),
			  BP_SOCKET_DEBUG("SocketInputDebug", PERS, false, "Print information about socket input processing to console"),
			  BP_CIRCLE_START("CircleStart", PERS, false, "Start on circle mode"),
			  BP_DELAY_VIEW("DelayView", !PERS, false, "Delayed dynamics (for two button mode)"),
			  BP_LM_REMOTE("RemoteLM", PERS, false, "Language model is remote and responds asynchronously."),
			  BP_CONNECT_LOCK("Connecting", !PERS, false, "Currently waiting for a connection to a remote LM; lock Dasher.");

			  private Ebp_parameters(String rName, boolean pers, boolean def, String hr) {
				humanReadable = hr;
				persistent = pers;
				defaultVal = def;
				regName = rName;
				BY_NAME.put(regName,this);
			}

			public int key() {return ordinal();}
			public String regName() {return regName;}
			public void reset(CSettingsStore ss) {ss.SetBoolParameter(this, defaultVal);}
			private final  String regName;
			final boolean persistent;
			final boolean defaultVal;
			final String humanReadable;
}