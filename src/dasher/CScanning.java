package dasher;

import dasher.Opts.ScreenOrientations;

public abstract class CScanning extends CDasherButtons {
	private long m_iScanTime;
	
	public CScanning(CDasherInterfaceBase iface, CSettingsStore sets, int iID, String szName) {
		super(iface, sets, iID, szName);
	}
	
	@Override public void Activate() {
		super.Activate();
		m_iScanTime = Integer.MIN_VALUE;
	}
	
	@Override public void KeyDown(long iTime, int iId, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
		if (iId==100) {
			iId=2; //select. Unless...
			if (GetLongParameter(Elp_parameters.LP_BUTTON_SCAN_TIME)==0) {
				pInput.GetScreenCoords(pView, coords);
				if (coords[1]<pView.Screen().GetHeight()/2)
					iId=1; //scan
			}
		}
		switch(iId) {
		case 1:
		case 4:
			m_bDecorationChanged = true;
			if(++m_iActiveBox == m_pBoxes.length)
				m_iActiveBox = 0;
			m_Interface.Redraw(false);
			break;
		case 2:
		case 3:
			m_bDecorationChanged = true;			
			pModel.ScheduleZoom(m_pBoxes[m_iActiveBox].iX, m_pBoxes[m_iActiveBox].iY);
			if(m_iActiveBox != m_pBoxes.length-1)
				m_iActiveBox = 0;
			break;
		}
	}
	private final long coords[]=new long[2]; 
	
	@Override public boolean Timer(long Time, CDasherView m_pDasherView, CDasherInput pInput, CDasherModel pModel) {
		if (GetLongParameter(Elp_parameters.LP_BUTTON_SCAN_TIME)>0) {
			m_bDecorationChanged = true; //pretend - so the screen repaints!!
			if (Time > m_iScanTime) {
				m_iScanTime = Time + GetLongParameter(Elp_parameters.LP_BUTTON_SCAN_TIME);
			
				if(++m_iActiveBox == m_pBoxes.length)
					m_iActiveBox = 0;
			}
		}
		return pModel.nextScheduledStep(Time, null);
	}

}