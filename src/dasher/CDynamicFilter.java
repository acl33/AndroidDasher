package dasher;

public abstract class CDynamicFilter extends CInputFilter {

	protected static final int PAUSED = 0;
	protected static final int REVERSING = 1;
	protected static final int RUNNING = 2;

	private int m_iState;

	public CDynamicFilter(CDasherInterfaceBase iface,
			CSettingsStore SettingsStore, long iID, String szName) {
		super(iface, SettingsStore, iID, szName);
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
		return TimerImpl(iTime, pView, pModel);
	}

	protected abstract boolean TimerImpl(long iTime, CDasherView pView, CDasherModel pModel);

	protected void pause(long iTime, CDasherModel pModel) {m_iState=PAUSED; m_Interface.PauseAt(0, 0);}

	protected void reverse(long iTime, CDasherModel pModel) {m_iState=REVERSING; m_Interface.Unpause(iTime);}

	protected void run(long iTime, CDasherModel pModel) {m_iState=RUNNING; m_Interface.Unpause(iTime);}

	@Override
	public boolean supportsPause() {return true;}

	protected int getState() {return m_iState;}
	
	@Override public void HandleEvent(CEvent evt) {
		if (evt instanceof CParameterNotificationEvent
				&& ((CParameterNotificationEvent)evt).m_iParameter==Ebp_parameters.BP_DASHER_PAUSED
				&& GetStringParameter(Esp_parameters.SP_INPUT_FILTER).equals(this.GetName())) {
			//just make sure our state is consistent...
			if (GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED)) {
				m_iState=PAUSED;
			} else if (m_iState==PAUSED) {
				//can't! don't know which way to go...
				m_Interface.PauseAt(0, 0);
			}
		}
	}
}