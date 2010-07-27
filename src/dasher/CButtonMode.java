package dasher;

/*static SModuleSettings sSettings[] = {
  // TRANSLATORS: The number of time steps over which to perform the zooming motion in button mode.
  {LP_ZOOMSTEPS, T_LONG, 1, 63, 1, 1, _("Zoom steps")},
  {LP_BUTTON_SCAN_TIME, T_LONG, 0, 2000, 1, 100, _("Scan time in menu mode (0 to not scan)")},
  {LP_B, T_LONG, 2, 10, 1, 1, _("Number of boxes")},
  {LP_S, T_LONG, 0, 256, 1, 1, _("Safety margin")},
  // TRANSLATORS: The boxes (zoom targets) in button mode can either be the same size, or different sizes - this is the extent to which the sizes are allowed to differ from each other.
  // XXX PRLW: 128 log(2) = 89, where 2 is the ratio of adjacent boxes
  // however the code seems to use ratio = (129/127)^-r, instead of
  // ratio = exp(r/128) used in the design document
  //
  {LP_R, T_LONG, -89, 89, 1, 10, _("Box non-uniformity")},
  // TRANSLATORS: Intercept keyboard events for 'special' keys even when the Dasher window doesn't have keyboard focus.
  {BP_GLOBAL_KEYBOARD, T_BOOL, -1, -1, -1, -1, _("Global keyboard grab")}
};*/

public class CButtonMode extends CDasherButtons {
	public CButtonMode(CDasherInterfaceBase iface, CSettingsStore sets, int iID, String szName) {
		super(iface,sets,iID,szName);
	}

	protected SBox[] SetupBoxes() {
		int iDasherY = (int)GetLongParameter(Elp_parameters.LP_MAX_Y);

		int iForwardBoxes = (int)GetLongParameter(Elp_parameters.LP_B);
		SBox[] m_pBoxes = new SBox[iForwardBoxes+1];

		// Calculate the sizes of non-uniform boxes using standard
		// geometric progression results

		// FIXME - implement this using DJCM's integer method?
		// See ~mackay/dasher/buttons/
		final double dRatio = Math.pow(129/127.0, GetLongParameter(Elp_parameters.LP_R));
		final int lpS = (int)GetLongParameter(Elp_parameters.LP_S);
	      
		if(iForwardBoxes == 2+1) { // Special case for two forwards buttons
			double dNorm = 1+dRatio;

			m_pBoxes[0]=new SBox(0,(int)(iDasherY/dNorm),lpS);

			m_pBoxes[1]=new SBox((int)(iDasherY/dNorm),iDasherY,lpS);
		} else {
			boolean bEven = iForwardBoxes % 2 == 0;

			int iGeometricTerms = (iForwardBoxes+1)/2;

			double dMaxSize;

			if(dRatio == 1.0) {
				dMaxSize = iDasherY / iForwardBoxes;
			} else {
				if(bEven)
					dMaxSize = iDasherY * (dRatio - 1) / (2 * (Math.pow(dRatio, iGeometricTerms) - 1));
				else
					dMaxSize = iDasherY * (dRatio - 1) / (2 * (Math.pow(dRatio, iGeometricTerms) - 1) - (dRatio - 1));
			}

			double dMin;
			
			if(bEven)
				dMin = iDasherY / 2;
			else
				dMin = (iDasherY - dMaxSize)/2;

			final int iUpBase = iForwardBoxes / 2; //round down if !bEven
			final int iDownBase = (iForwardBoxes-1)/2; //round down if bEven

			for(int i = 0; i < iGeometricTerms; ++i) { // One button reserved for backoff
				double dMax = dMin + dMaxSize * Math.pow(dRatio, i);

		        m_pBoxes[iUpBase + i]=new SBox((int)dMin,(int)dMax,lpS);
		        
		        m_pBoxes[iDownBase - i]=new SBox((int)(iDasherY - dMax),(int)(iDasherY - dMin), lpS);

		        dMin = dMax;
			}
		}
  
		m_pBoxes[m_pBoxes.length-1]=new SBox((int)(- iDasherY / 2),(int)(iDasherY * 1.5),0,iDasherY);
		
		return m_pBoxes;
	}

	@Override public void HandleEvent(CEvent pEvent) {
		if(pEvent instanceof CParameterNotificationEvent) {
			CParameterNotificationEvent pEvt = (CParameterNotificationEvent)pEvent;

			if (pEvt.m_iParameter == Elp_parameters.LP_B || pEvt.m_iParameter==Elp_parameters.LP_R) {
				m_pBoxes=SetupBoxes(); m_bDecorationChanged=true;
			}
		}
	}
}