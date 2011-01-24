package dasher;

public class OneButtonDynamicFilter extends CDynamicFilter {

	private BounceMarker upInner, downInner, upOuter, downOuter;
	
	private boolean m_bDecorationChanged;
	private double m_dNatsAtLastApply;
	private int m_iKeyHeldId=-1;
	/** Time at which button was first pressed (/pressed down, if we're using the release time),
	 * or Long.MAX_VALUE if no such first press has occurred.
	 */
	private long m_iFirstPressTime = Long.MAX_VALUE;
	private double m_dNatsAtFirstPress;
	
	/** locations of up/down guidelines (DasherY) - computed in Timer */
	private int guideUp, guideDown;
	private double m_dMulBoundary;
	private boolean m_bUseUpGuide;
	
	//for debugging....
	private double m_dNatsSinceLastApply;
	
	public OneButtonDynamicFilter(CDasherInterfaceBase iface,
			CSettingsStore SettingsStore) {
		super(iface, SettingsStore, 17, "One-button Dynamic Mode");
		createMarkers();
	}
	
	@Override
	public boolean DecorateView(CDasherView pView, CDasherInput pInput) {
		int y= (int)(downInner.m_iLocn*m_dMulBoundary);
		pView.Dasherline(-100, 2048-y, -1000, 2048-y, 1, 62);
		y=(int)(upInner.m_iLocn * m_dMulBoundary);
		pView.Dasherline(-100, 2048-y, -1000, 2048-y, 1, 62);
		
		//Moving markers...
		if (m_iFirstPressTime!=Long.MAX_VALUE) {
			int upCol=m_bUseUpGuide ? 240 : 61, downCol=240+61-upCol; //240 = green = active, 61 = orange/yellow = inactive
			pView.Dasherline(-100, 2048-guideUp, -1000, 2048-guideUp, 3, upCol);
			pView.Dasherline(-100, 2048-guideDown, -1000, 2048-guideDown, 3, downCol);
		}
		//Fixed markers - draw last so they go on top...
		upInner.Draw(pView, m_dNatsSinceLastApply);
		upOuter.Draw(pView, m_dNatsSinceLastApply);
		downInner.Draw(pView, m_dNatsSinceLastApply);
		downOuter.Draw(pView, m_dNatsSinceLastApply);
		
		if (m_bDecorationChanged || BounceMarker.DEBUG_LEARNING) {
			m_bDecorationChanged=false;
			return true;
		}
		return false;
	}

	@Override protected boolean TimerImpl(long iTime, CDasherView pView, CDasherModel pModel) {
		if (m_iFirstPressTime!=Long.MAX_VALUE) {
			double dGrowth = Math.exp(pModel.GetNats()-m_dNatsAtFirstPress);
			guideUp = (int)(dGrowth*upInner.m_iLocn);
			guideDown = (int)(dGrowth*downInner.m_iLocn);
			m_bUseUpGuide = dGrowth < m_dMulBoundary;
			CDasherView.DRect visReg = pView.VisibleRegion();
			if (2048-guideUp < visReg.minY && 2048-guideDown>visReg.maxY) {
				//both markers outside y-axis (well, in compressed region)
				// => waited too long for second press(/release)
				reverse(iTime, pModel);
				return false;
			}
		}
		m_dNatsSinceLastApply = pModel.GetNats() - m_dNatsAtLastApply;
		pModel.oneStepTowards(0, GetLongParameter(Elp_parameters.LP_OY), iTime, 1.0f);
        return true;
	}
	
	@Override
	public void HandleEvent(CEvent evt) {
		if (!(evt instanceof CParameterNotificationEvent)) return;
		EParameters p = ((CParameterNotificationEvent)evt).m_iParameter;
		if (p==Elp_parameters.LP_ONE_BUTTON_OUTER || p==Elp_parameters.LP_ONE_BUTTON_LONG_GAP || p==Elp_parameters.LP_ONE_BUTTON_SHORT_GAP)
			createMarkers();
		super.HandleEvent(evt);
	}
	
	private void createMarkers() {
		final int outer = (int)GetLongParameter(Elp_parameters.LP_ONE_BUTTON_OUTER),
				biggap = (int)GetLongParameter(Elp_parameters.LP_ONE_BUTTON_LONG_GAP),
				down = outer - biggap,
				up = outer - (int)(biggap * GetLongParameter(Elp_parameters.LP_ONE_BUTTON_SHORT_GAP))/100;
				
		upInner = new BounceMarker(up);
		upOuter = new BounceMarker(outer);
		downInner = new BounceMarker(-down);
		downOuter = new BounceMarker(-outer);
		m_dMulBoundary = outer/Math.sqrt(up*down);
	}
	
	@Override public void KeyDown(long iTime, int iId, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
		if (m_iKeyHeldId!=-1 && iId != m_iKeyHeldId) return; //ignore subsequent presses whilst button down
		m_iKeyHeldId=iId;
		switch (getState()) {
		case REVERSING:
			pause(iTime, pModel);
			break;
		case PAUSED:
			run(iTime, pModel);
			break;
		case RUNNING:
			if (m_iFirstPressTime==Long.MAX_VALUE) {
				m_iFirstPressTime = iTime;
				m_dNatsAtFirstPress = pModel.GetNats();
			} else
				secondPress(iTime,pModel);
			break;
		}
	}
	
	@Override public void KeyUp(long iTime, int iId, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
		if (iId == m_iKeyHeldId) {
			m_iKeyHeldId=-1;
			if (m_iFirstPressTime!=Long.MAX_VALUE && GetBoolParameter(Ebp_parameters.BP_ONE_BUTTON_RELEASE_TIME))
				secondPress(iTime,pModel);
		}
	}
	
	protected void secondPress(long iTime, CDasherModel pModel) {
		BounceMarker inner,outer;
		if (m_bUseUpGuide) {
			inner = upInner; outer=upOuter;
		} else {
			inner = downInner; outer = downOuter;
		}
		double dCurBitrate = GetLongParameter(Elp_parameters.LP_MAX_BITRATE) * GetLongParameter(Elp_parameters.LP_BOOSTFACTOR) / 10000.0;
		int iOffset = inner.GetTargetOffset(dCurBitrate, outer, iTime - m_iFirstPressTime);
		if (pModel.m_iDisplayOffset!=0) {
			iOffset -= pModel.m_iDisplayOffset;
			System.err.println("Display Offset "+pModel.m_iDisplayOffset+" reducing to "+iOffset);
		}
		double dNewNats = pModel.GetNats() - m_dNatsAtLastApply;
		upInner.NotifyOffset(iOffset, dNewNats); upOuter.NotifyOffset(iOffset, dNewNats);
		downInner.NotifyOffset(iOffset, dNewNats); downOuter.NotifyOffset(iOffset, dNewNats);
		inner.RecordPush(iOffset, pModel.GetNats() - m_dNatsAtFirstPress, dCurBitrate);
		outer.RecordPush(iOffset, 0.0, dCurBitrate);
		pModel.Offset(iOffset);
		m_dNatsAtLastApply = pModel.GetNats();
		m_iFirstPressTime = Long.MAX_VALUE;
	}
	
	@Override public void reverse(long iTime, CDasherModel pModel) {
		pModel.MatchTarget();
		upInner.clearPushes(); upOuter.clearPushes(); downInner.clearPushes(); downOuter.clearPushes();
		m_iFirstPressTime = Long.MAX_VALUE;
		super.reverse(iTime, pModel);
	}
	
	@Override public void Activate() {
		super.Activate();
		m_Interface.SetBoolParameter(Ebp_parameters.BP_DELAY_VIEW, true);
	}
	
	@Override public void Deactivate() {
		m_Interface.SetBoolParameter(Ebp_parameters.BP_DELAY_VIEW, false);
		super.Deactivate();
	}
	
}
