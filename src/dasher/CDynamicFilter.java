package dasher;

public abstract class CDynamicFilter extends CInputFilter {

	protected static final int SINGLE_PRESS=1, DOUBLE_PRESS=2, LONG_PRESS=0;
	
    private int m_iState; // 0 = paused, 1 = reversing, >=2 = running (extensible by subclasses)
    private boolean m_bKeyDown;
    private int m_iKeyId;
    private long m_iKeyDownTime;
    
	public CDynamicFilter(CDasherInterfaceBase iface,
			CSettingsStore SettingsStore, long iID, String szName) {
		super(iface, SettingsStore, iID, szName);
		// TODO Auto-generated constructor stub
	}

	/** When reversing, backs off; when paused, does nothing; when running, delegates to TimerImpl */
	@Override
	public boolean Timer(long iTime, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
		if (m_iState==PAUSED) {
			return false;
		}
		if (m_iState==REVERSING) {
			pModel.oneStepTowards(41904, GetLongParameter(Elp_parameters.LP_OY), iTime, 1.0f);
			return true;
		}
		if (m_bKeyDown && (iTime-m_iKeyDownTime)>GetLongParameter(Elp_parameters.LP_HOLD_TIME)) {
			Event(iTime,m_iKeyId, LONG_PRESS, pModel);
		}
		return TimerImpl(iTime, pView, pModel);
	}
	
	protected abstract boolean TimerImpl(long iTime, CDasherView pView, CDasherModel pModel);
	
	@Override
	public void KeyDown(long iTime, int iId, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
		if (m_bKeyDown) return; //ignore any key after first
		m_bKeyDown = true;
		if (m_iKeyId==iId && iTime>m_iKeyDownTime && iTime - m_iKeyDownTime <= GetLongParameter(Elp_parameters.LP_DOUBLE_CLICK_TIME)) {
			Event(iTime, iId, DOUBLE_PRESS, pModel);
		} else {
			m_iKeyDownTime = iTime;
			m_iKeyId = iId;
			Event(iTime, iId, SINGLE_PRESS, pModel);
		}
	}
	
	protected void Event(long iTime, int iId, int pressType, CDasherModel pModel) {
		if (m_iState==REVERSING) {
			pause(iTime, pModel);
		} else if (pressType!=SINGLE_PRESS) {
			reverse(iTime, pModel);
		} else if (m_iState==PAUSED) {
			run(iTime, pModel);
		}
	}
	
	@Override
	public void KeyUp(long iTime, int iId, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
		if (iId==m_iKeyId) m_bKeyDown=false;
	}
	
	@Override public void HandleEvent(CEvent evt) {
		if (evt instanceof CParameterNotificationEvent
				&& ((CParameterNotificationEvent)evt).m_iParameter==Ebp_parameters.BP_DASHER_PAUSED
				&& GetStringParameter(Esp_parameters.SP_INPUT_FILTER).equals(this.GetName())) {
			m_iKeyDownTime = Long.MAX_VALUE; //prevent any long-presses, etc.
			//just make sure our state is consistent...
			if (GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED)) {
				m_iState=PAUSED;
			} else if (m_iState==PAUSED) {
				//can't! don't know which way to go...
				m_Interface.PauseAt(0, 0);
			}
		}
	}

	void pause(long iTime, CDasherModel pModel) {m_iState=PAUSED; m_Interface.PauseAt(0, 0);}
	void reverse(long iTime, CDasherModel pModel) {m_iState=REVERSING; m_Interface.Unpause(iTime);}
	void run(long iTime, CDasherModel pModel) {m_iState=RUNNING; m_Interface.Unpause(iTime);}

	@Override public boolean supportsPause() {return true;}

	protected int getState() {return m_iState;}
	protected static final int PAUSED=0, REVERSING=1, RUNNING=2;
}
