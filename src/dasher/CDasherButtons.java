package dasher;

public abstract class CDasherButtons extends CInputFilter {
	protected final boolean m_bMenu;
	private int m_iActiveBox;
	private long m_iScanTime;
	private SBox m_pBoxes[];
	private boolean m_bDecorationChanged;
	
	protected void ChangeBoxes() {m_pBoxes = SetupBoxes(); m_bDecorationChanged=true;}
	
	public CDasherButtons(CDasherInterfaceBase iface, CSettingsStore sets, boolean bMenu, int iID, String szName) {
		super(iface, sets, iID, szName);
		this.m_bMenu = bMenu;
		m_pBoxes = SetupBoxes();
	}
	
	protected static class SBox {
		/**target of zoom*/
		final int iX,iY;
		/**location of top/bottom on screen*/
		final int iDisplayTop, iDisplayBottom;
		SBox(int iTop, int iBottom, int iDisplayTop, int iDisplayBottom) {
			if (iTop>=iBottom || iDisplayTop>=iDisplayBottom) throw new IllegalArgumentException();
			iY = (iTop+iBottom)/2;
			iX = (iBottom-iTop)/2;
			this.iDisplayTop=iDisplayTop;
			this.iDisplayBottom=iDisplayBottom;
		}
		SBox(int iDisplayTop, int iDisplayBottom, int safety) {
			this(iDisplayTop-safety, iDisplayBottom+safety, iDisplayTop, iDisplayBottom);
		}
		public String toString() {
			return "{"+iDisplayTop+" - "+iDisplayBottom+" => " +iX+","+iY+"}";
		}
	}

	@Override public void Activate() {
		if (m_pBoxes==null) m_pBoxes = SetupBoxes();
		m_iScanTime = Integer.MIN_VALUE;
	}
	
	protected abstract SBox[] SetupBoxes();

	private final long[] mouseCoords = new long[2];
	private long m_iLastTime;
	
	@Override public void KeyDown(long iTime, int iId, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
		if(m_bMenu) {
			switch(iId) {
			case 1:
			case 4:
				m_bDecorationChanged = true;
				if(++m_iActiveBox == m_pBoxes.length)
					m_iActiveBox = 0;
				break;
			case 2:
			case 3:
			case 100:
				m_bDecorationChanged = true;
				pModel.ScheduleZoom(m_pBoxes[m_iActiveBox].iX, m_pBoxes[m_iActiveBox].iY);
				if(m_iActiveBox != m_pBoxes.length-1)
					m_iActiveBox = 0;
				break;
			}
		} else {
			if (iId == 100) {
				//Mouse!
				m_iActiveBox=-1;
				pInput.GetDasherCoords(pView, mouseCoords);
				for (int i = 0; i < m_pBoxes.length; i++) {
					if (isInBox(mouseCoords[0],mouseCoords[1],m_pBoxes[i])) {
						m_iActiveBox=i;
						break;
					}
				}
				if (m_iActiveBox==-1) return; //not in any box
			} else if(iId == 1)
				m_iActiveBox = m_pBoxes.length - 1; //last box = zoom out
			else if(iId <= m_pBoxes.length) 
				m_iActiveBox = iId-2;
			else
				m_iActiveBox = m_pBoxes.length-2;
			pModel.ScheduleZoom(m_pBoxes[m_iActiveBox].iX, m_pBoxes[m_iActiveBox].iY);
			m_iLastTime = iTime;
			m_bDecorationChanged = true; //we set m_iActiveBox and m_iLastTime, so it'll be highlighted for awhile
		}
	}
	
	protected boolean isInBox(long iDasherX, long iDasherY, SBox box) {
		android.util.Log.d("DasherCore","isInBox("+iDasherX+","+iDasherY+") on "+box.iDisplayTop+" - "+box.iDisplayBottom);
		return iDasherY <= box.iDisplayBottom &&
				iDasherY >= box.iDisplayTop &&
				iDasherX <= box.iDisplayBottom - box.iDisplayTop;
				//(iDasherX < box.iDisplayBottom - box.iDisplayTop || box == m_pBoxes[m_pBoxes.length-1]);
	}

	@Override public boolean Timer(long Time, CDasherView m_pDasherView, CDasherInput pInput, CDasherModel pModel) {
		if (m_bMenu) {
			if (GetLongParameter(Elp_parameters.LP_BUTTON_SCAN_TIME)>0) {
				m_bDecorationChanged = true; //pretend - so the screen repaints!!
				if (Time > m_iScanTime) {
					m_iScanTime = Time + GetLongParameter(Elp_parameters.LP_BUTTON_SCAN_TIME);
				
					if(++m_iActiveBox == m_pBoxes.length)
						m_iActiveBox = 0;
				}
			}
		} else {
			if ((Time - m_iLastTime) > 200) {
				//end any highlight
				if (m_iActiveBox!=-1) m_bDecorationChanged = true;
				m_iActiveBox = -1;
			}
		}

		return pModel.nextScheduledStep(Time, null);
	}

	public boolean DecorateView(CDasherView pView) {
		for(int i = 0; i < m_pBoxes.length; ++i) {
			if(i != m_iActiveBox)
				DrawBox(pView, m_pBoxes[i], false);
		}
		if (m_iActiveBox!=-1) DrawBox(pView, m_pBoxes[m_iActiveBox],true);

		boolean bRV = m_bDecorationChanged;
		m_bDecorationChanged = false;
		return bRV;
	}
	
	protected void DrawBox(CDasherView pView, SBox box, boolean bActive) {
	
		final long iMaxX = //(box.iDisplayBottom <= pView.VisibleRegion().minY && box.iDisplayTop <= pView.VisibleRegion().maxY) ? pView.VisibleRegion().maxX :
			box.iDisplayBottom - box.iDisplayTop;

		int iColour;
		int iWidth;

		if(bActive) {
			iColour = 1;
			iWidth = 3;
		} else {
			iColour = 2;
			iWidth = 1;
		}

		pView.DasherDrawRectangle(iMaxX, box.iDisplayBottom, 0, box.iDisplayTop, -1, iColour, iWidth);
   }

}