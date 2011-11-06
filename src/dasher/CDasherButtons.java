package dasher;

import dasher.CDasherView.MutablePoint;

public abstract class CDasherButtons extends CInputFilter {
	protected int m_iActiveBox;
	protected SBox m_pBoxes[];
	protected boolean m_bDecorationChanged;
	
	//protected void ChangeBoxes() {m_pBoxes = SetupBoxes(); m_bDecorationChanged=true;}
	
	public CDasherButtons(CDasherComponent creator, CDasherInterfaceBase iface, String szName) {
		super(creator, iface, szName);
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
	}
	
	protected abstract SBox[] SetupBoxes();

	private final MutablePoint mouseCoords = new MutablePoint();
	private long m_iLastTime;
	
	@Override public void KeyDown(long iTime, int iId, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
		if (iId == 100) {
			//Mouse!
			m_iActiveBox=-1;
			pInput.GetDasherCoords(pView, mouseCoords);
			for (int i = 0; i < m_pBoxes.length; i++) {
				if (isInBox(mouseCoords.x,mouseCoords.y,m_pBoxes[i])) {
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
	
	protected boolean isInBox(long iDasherX, long iDasherY, SBox box) {
		return iDasherY <= box.iDisplayBottom &&
				iDasherY >= box.iDisplayTop &&
				iDasherX <= box.iDisplayBottom - box.iDisplayTop;
				//(iDasherX < box.iDisplayBottom - box.iDisplayTop || box == m_pBoxes[m_pBoxes.length-1]);
	}

	@Override public boolean Timer(long Time, CDasherView m_pDasherView, CDasherInput pInput, CDasherModel pModel) {
		if ((Time - m_iLastTime) > 200) {
			//end any highlight
			if (m_iActiveBox!=-1) m_bDecorationChanged = true;
			m_iActiveBox = -1;
		}
		return pModel.nextScheduledStep(Time);
	}

	@Override public boolean DecorateView(CDasherView pView, CDasherInput pInput) {
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

		pView.DasherDrawRectangle(iMaxX, box.iDisplayTop, 0, box.iDisplayBottom, -1, iColour, iWidth);
   }

}