package dasher;

import static dasher.CDasherModel.CROSS_Y;
import dasher.CDasherView.MutablePoint;

public class TwoButtonDynamicFilter extends CDynamicPresses {

	private BounceMarker up,down;
	private boolean m_bDecorationChanged;
	private double m_dNatsAtLastApply;
	
	public TwoButtonDynamicFilter(CDasherComponent creator, CDasherInterfaceBase iface) {
		super(creator, iface, "Two-button Dynamic Mode");
		createMarkers();
	}

	@Override
	public boolean DecorateView(CDasherView pView, CDasherInput pInput) {
		up.Draw(pView);
		down.Draw(pView);
		if (m_bDecorationChanged) {
			m_bDecorationChanged=false;
			return true;
		}
		return false;
	}
	
	@Override
	public boolean TimerImpl(long iTime, CDasherView pView,CDasherModel pModel) {
		super.TimerImpl(iTime, pView, pModel);
		pModel.oneStepTowards(0, CROSS_Y, iTime, getSpeedMul(pModel, iTime));
		return true;
	}

	@Override
	public void HandleEvent(EParameters eParam) {
		if (eParam==Elp_parameters.LP_TWO_BUTTON_OFFSET) 
			createMarkers();
		super.HandleEvent(eParam);
	}
	
	private void createMarkers() {
		up = new BounceMarker((int)GetLongParameter(Elp_parameters.LP_TWO_BUTTON_OFFSET));
		down = new BounceMarker((int)-GetLongParameter(Elp_parameters.LP_TWO_BUTTON_OFFSET));
		m_bDecorationChanged=true;
	}
	
	private final MutablePoint coords=new MutablePoint();
	
	@Override public void KeyDown(long iTime, int iId, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
		if (iId==100 && pInput.GetDasherCoords(pView, coords))
			iId = (coords.y < CROSS_Y) ? 0 : 1;
		super.KeyDown(iTime, iId, pView, pInput, pModel);
	}
	
	@Override public void KeyUp(long iTime, int iId, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
		if (iId==100 && pInput.GetDasherCoords(pView, coords))
			iId = (coords.y < CROSS_Y) ? 0 : 1;
		super.KeyUp(iTime, iId, pView, pInput, pModel);
	}
	
	@Override public void Event(long iTime, int iId, int pressType, CDasherModel pModel) {
		marker: if (pressType==SINGLE_PRESS && getState()==RUNNING) {
			BounceMarker pMarker;
			if (iId==0 || iId==4) pMarker=up; else if (iId==1 || iId==2) pMarker=down; else break marker;
			//apply offset
			double dCurBitrate = GetLongParameter(Elp_parameters.LP_MAX_BITRATE) / 100.0;
			int iOffset = pMarker.GetTargetOffset(dCurBitrate*getSpeedMul(pModel, iTime));
			if (pModel.m_iDisplayOffset!=0) System.err.println("Display Offset "+pModel.m_iDisplayOffset+" reducing to "+(iOffset -= pModel.m_iDisplayOffset));
			double dNewNats = pModel.GetNats() - m_dNatsAtLastApply;
			up.NotifyOffset(iOffset, dNewNats);
			down.NotifyOffset(iOffset, dNewNats);
			pMarker.RecordPush(iOffset, 0.0, dCurBitrate);
			pModel.Offset(iOffset);
			m_dNatsAtLastApply = pModel.GetNats();
		}
		super.Event(iTime, iId, pressType, pModel);
	}
	
	@Override public void reverse(long iTime, CDasherModel pModel) {
		pModel.AbortOffset();
		up.clearPushes(); down.clearPushes();
		super.reverse(iTime, pModel);
	}
	
	@Override public void pause(long iTime, CDasherModel pModel) {
		pModel.AbortOffset();
		super.pause(iTime,pModel);
	}

}
