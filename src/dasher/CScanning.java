package dasher;

import dasher.Opts.ScreenOrientations;

public abstract class CScanning extends CDasherButtons {
	private long m_iScanTime;
	
	public CScanning(CDasherComponent creator, CDasherInterfaceBase iface, String szName) {
		super(creator, iface, szName);
	}
	
	@Override public void Activate() {
		super.Activate();
		m_iScanTime = Integer.MIN_VALUE;
	}
	
	@Override public void KeyDown(long iTime, int iId, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
		if (iId==100) {
			pInput.GetScreenCoords(pView, coords);
			iId = (coords[1]<pView.Screen().GetHeight()/2)
				? 1 //scan
				: 2; //select
			
		}
		switch(iId) {
		case 1:
		case 4:
			//scan.
			if (GetLongParameter(Elp_parameters.LP_BUTTON_SCAN_TIME)==0) {
				m_bDecorationChanged = true;
				if(++m_iActiveBox == m_pBoxes.length)
					m_iActiveBox = 0;
				m_Interface.Redraw(false);
				break;
			} else iId=2; //automatic scanning by timer, fall through to select
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
		return pModel.nextScheduledStep(Time);
	}

}