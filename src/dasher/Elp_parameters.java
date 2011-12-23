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
 * Enumeration of possible integer parameter references. See
 * CParamTables for a list and definitions.
 */
public enum Elp_parameters implements EParameters {
	  LP_MAX_BITRATE("MaxBitRateTimes100", 80, "Max Bit Rate Times 100"),
	  LP_FRAMERATE("FrameRate", 3200, "Decaying average of last known frame rates, *100"),
	  LP_VIEW_ID("ViewID", 1, "ViewID"),
	  LP_LANGUAGE_MODEL_ID("LanguageModelID", 0, "LanguageModelID"),
	  LP_DASHER_FONTSIZE("DasherFontSize", 1, "DasherFontSize"),
	  LP_UNIFORM("UniformTimes1000", 50, "UniformTimes1000"),
	  LP_MOUSEPOSDIST("MousePositionBoxDistance", 50, "MousePositionBoxDistance"),
	  //LP_STOP_IDLETIME("StopIdleTime", 1000, "StopIdleTime" ),
	  LP_LM_MAX_ORDER("LMMaxOrder", 5, "LMMaxOrder"),
	  LP_LM_UPDATE_EXCLUSION("LMUpdateExclusion", 1, "LMUpdateExclusion"),
	  LP_LM_ALPHA("LMAlpha", 49, "LMAlpha"),
	  LP_LM_BETA("LMBeta", 77, "LMBeta"),
	  //LP_LM_MIXTURE("LMMixture", 50, "LMMixture"),
	  LP_LINE_WIDTH("LineWidth", 1, "Width to draw crosshair and mouse line"),
	  //LP_LM_WORD_ALPHA("WordAlpha", 50, "Alpha value for word-based model"),
	  LP_USER_LOG_LEVEL_MASK("UserLogLevelMask", 0, "Controls level of user logging, 0 = none, 1 = short, 2 = detailed, 3 = both"),
	  LP_ZOOMSTEPS("Zoomsteps", 32, "Frames for zoom"),
	  LP_B("ButtonMenuBoxes", 4, "Number of boxes for button menu mode"),
	  LP_S("ButtonMenuSafety", 25, "Safety parameter for button mode, in percent."),
	  LP_R("ButtonModeNonuniformity", 0, "Button mode box non-uniformity"),
	  //LP_Z("ButtonMenuBackwardsBox", 1, "Number of back-up boxes for button menu mode"),
	  LP_RIGHTZOOM("ButtonCompassModeRightZoom", 5120, "Zoomfactor (*1024) for compass mode"),
	  LP_AUTOSPEED_SENSITIVITY("AutospeedSensitivity", 100, "Sensitivity of automatic speed control (percent)"),
	  /*LP_SOCKET_PORT("SocketPort", 20320, "UDP/TCP socket to use for network socket input"),
	  LP_SOCKET_INPUT_X_MIN("SocketInputXMinTimes1000", 0, "Bottom of range of X values expected from network input"),
	  LP_SOCKET_INPUT_X_MAX("SocketInputXMaxTimes1000", 1000, "Top of range of X values expected from network input"),
	  LP_SOCKET_INPUT_Y_MIN("SocketInputYMinTimes1000", 0, "Bottom of range of Y values expected from network input"),
	  LP_SOCKET_INPUT_Y_MAX("SocketInputYMaxTimes1000", 1000, "Top of range of Y values expected from network input"),*/
	  //LP_INPUT_FILTER("InputFilterID", 3, "Module ID of input filter"),
	  LP_CIRCLE_PERCENT("CirclePercent", 10, "Percentage of nominal vertical range to use for radius of start circle"),
	  LP_TWO_BUTTON_OFFSET("TwoButtonOffset", 1024, "Offset for two button dynamic mode"),
	  LP_TAP_TIME("TapTime", 100, "Max time for tap in Stylus Mode"),
	  LP_NON_LINEAR_X("NonLinearX", 8, "Nonlinear compression of X-axis (0 = none, higher = more extreme)"),
	  LP_DASHER_MARGIN("MarginWidth", 400, "Width of RHS margin (in Dasher co-ords)"),
	  LP_NODE_BUDGET("NodeBudget", 1200, "Target number of node objects"),
	  LP_BUTTON_SCAN_TIME("ButtonScanTime", 0, "Scanning interval in button mode (0 = don't scan)"),
	  LP_MIN_NODE_SIZE_TEXT("MinNodeSizeForText",40, "Minimum size for box to have text (4096=whole screen)"),
	  LP_SLOW_START_TIME("SlowStartTime", 1000, "Time in ms over which slow start is applied"),
	  LP_SWEEP_TIME("SweepTime", 3000, "Time in ms to sweep top-to-bottom"),
	  LP_X_LIMIT_SPEED("XLimitSpeed", 800, "X Co-ordinate at which maximum speed is reached (<2048=xhair)"),
	  LP_MAX_ZOOM("MaxZoom", 20, "Max factor to zoom by in stylus/click mode"),
	  LP_TARGET_OFFSET("TargetOffset", 0, "Offset target from actual mouse/touch/gaze position"),
	  /** Can we combine this with LP_TAP_TIME? Think we want this to be much longer...?*/
	  LP_HOLD_TIME("LongPressTime", 800,"Time/ms for long press (=reverse) in dynamic button mode"),
	  LP_DOUBLE_CLICK_TIME("DoublePressTime", 150,"Time/ms for double click (=reverse) in 2B-dynamic mode"),
	  LP_ONE_BUTTON_SHORT_GAP("OneButtonShortGap", 40, "Distance between up markers as % of long gap in 1B-dynamic mode"),
	  LP_ONE_BUTTON_LONG_GAP("OneButtonLongGap", 512, "Distance between down markers (long gap) in 1B-dynamic mode"),
	  LP_ONE_BUTTON_OUTER("OneButtonOuter", 1920, "Distance to up&down outer markers in 1B-dynamic mode");
		  
		  private Elp_parameters(String rName, long def, String hr) {
				humanReadable = hr;
				defaultVal = def;
				regName = rName;
				BY_NAME.put(regName,this);
			}
			
		  private static final int LP_OFFSET = Ebp_parameters.values().length;
		  public int key() {return ordinal()+LP_OFFSET;}
			public String regName() {return regName;}
			public void reset(CSettingsStore ss) {ss.SetLongParameter(this, defaultVal);}
			
			private final String regName;
			
			public final long defaultVal;
			final String humanReadable;
}