package dasher;

public class TwoButtonDynamicFilter extends CDynamicFilter {

	private BounceMarker up,down;
	private boolean m_bDecorationChanged;
	private double m_dNatsAtLastApply;
	
	//for debugging....
	private double m_dNatsSinceLastApply;
	
	public TwoButtonDynamicFilter(CDasherInterfaceBase iface,
			CSettingsStore SettingsStore) {
		super(iface, SettingsStore, 19, "Two-button Dynamic Mode");
		createMarkers();
	}

	@Override
	public boolean DecorateView(CDasherView pView, CDasherInput pInput) {
		up.Draw(pView, m_dNatsSinceLastApply);
		down.Draw(pView, m_dNatsSinceLastApply);
		if (m_bDecorationChanged || BounceMarker.DEBUG_LEARNING) {
			m_bDecorationChanged=false;
			return true;
		}
		return false;
	}
	
	@Override
	public boolean TimerImpl(long Time, CDasherView pView,CDasherModel pModel) {
		m_dNatsSinceLastApply = pModel.GetNats() - m_dNatsAtLastApply;
		pModel.oneStepTowards(0, GetLongParameter(Elp_parameters.LP_OY), Time, 1.0f);
		return true;
	}

	@Override
	public void HandleEvent(CEvent evt) {
		if (evt instanceof CParameterNotificationEvent
				&& ((CParameterNotificationEvent)evt).m_iParameter==Elp_parameters.LP_TWO_BUTTON_OFFSET) 
			createMarkers();
		super.HandleEvent(evt);
	}
	
	private void createMarkers() {
		up = new BounceMarker((int)GetLongParameter(Elp_parameters.LP_TWO_BUTTON_OFFSET));
		down = new BounceMarker((int)-GetLongParameter(Elp_parameters.LP_TWO_BUTTON_OFFSET));
		m_bDecorationChanged=true;
	}
	
	private final long[] coords=new long[2];
	
	@Override public void KeyDown(long iTime, int iId, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
		if (iId==100 && pInput.GetDasherCoords(pView, coords))
			iId = (coords[1] < GetLongParameter(Elp_parameters.LP_OY)) ? 0 : 1;
		super.KeyDown(iTime, iId, pView, pInput, pModel);
	}
	
	@Override public void KeyUp(long iTime, int iId, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
		if (iId==100 && pInput.GetDasherCoords(pView, coords))
			iId = (coords[1] < GetLongParameter(Elp_parameters.LP_OY)) ? 0 : 1;
		super.KeyUp(iTime, iId, pView, pInput, pModel);
	}
	
	@Override public void Event(long iTime, int iId, int pressType, CDasherModel pModel) {
		marker: if (pressType==SINGLE_PRESS && getState()==RUNNING) {
			BounceMarker pMarker;
			if (iId==0) pMarker=up; else if (iId==1) pMarker=down; else break marker;
			//apply offset
			double dCurBitrate = GetLongParameter(Elp_parameters.LP_MAX_BITRATE) * GetLongParameter(Elp_parameters.LP_BOOSTFACTOR) / 10000.0;
			int iOffset = pMarker.GetTargetOffset(dCurBitrate);
			double dNewNats = pModel.GetNats() - m_dNatsAtLastApply;
			up.NotifyOffset(iOffset, dNewNats);
			down.NotifyOffset(iOffset, dNewNats);
			pMarker.addPush(iOffset, dCurBitrate);
			pModel.Offset(iOffset);
			m_dNatsAtLastApply = pModel.GetNats();
		}
		super.Event(iTime, iId, pressType, pModel);
	}
	
	@Override public void reverse(long iTime, CDasherModel pModel) {
		pModel.MatchTarget();
		up.clearPushes(); down.clearPushes();
		super.reverse(iTime, pModel);
	}
	
}
