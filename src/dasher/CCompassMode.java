package dasher;

public class CCompassMode extends CDasherButtons {
	private int iTargetWidth;
	public CCompassMode(CDasherInterfaceBase iface, CSettingsStore sets) {
		super(iface, sets, 13, "Compass Mode");
	}
  
	@Override protected SBox[] SetupBoxes() {
		int iDasherY = (int)GetLongParameter(Elp_parameters.LP_MAX_Y);

		SBox[] m_pBoxes = new SBox[4];

		iTargetWidth = (int)(iDasherY * 1024 / GetLongParameter(Elp_parameters.LP_RIGHTZOOM));

		// FIXME - need to relate these to cross-hair position as stored in the parameters

		// Not sure whether this is at all the right algorithm here - need to check
		int iTop = (2048 - iTargetWidth / 2);
		m_pBoxes[1] = new SBox(iTop, 4096 - iTop, 0);

		// Make this the inverse of the right zoom option

		int backTop = -2048 *  iTop / (2048 -  iTop);
		m_pBoxes[3] = new SBox(backTop, 4096 - backTop, 0);
  
		m_pBoxes[0] = new SBox(-iTargetWidth, iDasherY - iTargetWidth, 0);
		m_pBoxes[2] = new SBox(iTargetWidth,iDasherY + iTargetWidth, 0);

		return m_pBoxes;
	}

	@Override public boolean DecorateView(CDasherView pView, CDasherInput pInput) {
		final long[] x=new long[2], y=new long[2];
		boolean bFirst=true;
		x[0]=-100; x[1]=-1000;
		for (int iPos = 2048 - iTargetWidth / 2; iPos >= 0; iPos -= iTargetWidth) {
			
			y[0] = y[1] = iPos;

			pView.DasherPolyline(x, y, 2, 1, bFirst ? 1 : 2);
			
			y[0] = y[1] = 4096 - iPos;

			pView.DasherPolyline(x, y, 2, 1, bFirst ? 1 : 2);
			
			bFirst = false;
		}
		return false; //never changes!
	}
 
	@Override public void HandleEvent(CEvent pEvent) {
		if(pEvent instanceof CParameterNotificationEvent) {
			CParameterNotificationEvent pEvt = (CParameterNotificationEvent)pEvent;
    		if (pEvt.m_iParameter == Elp_parameters.LP_RIGHTZOOM) {
    			m_pBoxes=SetupBoxes(); m_bDecorationChanged = true;
    		}
  		}
	}
}
