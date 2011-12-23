package dasher;

public abstract class CDynamicPresses extends CDynamicButtons {

	protected static final int SINGLE_PRESS=1, DOUBLE_PRESS=2, LONG_PRESS=0;
	
    private boolean m_bKeyDown;
    private int m_iKeyId;
    private long m_iKeyDownTime;
    
	public CDynamicPresses(CDasherComponent creator, CDasherInterfaceBase iface, String szName) {
		super(creator, iface, szName);
	}

	@Override
	protected void TimerImpl(long iTime, CDasherView pView, CDasherModel pModel) {
		if (m_bKeyDown && (iTime-m_iKeyDownTime)>GetLongParameter(Elp_parameters.LP_HOLD_TIME)) {
			Event(iTime,m_iKeyId, LONG_PRESS, pModel);
		}
	}
	
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
		if (isReversing()) {
			pause();
		} else if (pressType!=SINGLE_PRESS) {
			reverse(iTime, pModel);
		} else if (isPaused()) {
			run(iTime, pModel);
		}
	}
	
	@Override
	public void KeyUp(long iTime, int iId, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
		if (iId==m_iKeyId) m_bKeyDown=false;
	}
	
	@Override public void pause() {
		m_iKeyDownTime = Long.MAX_VALUE;
		super.pause();
	}
	
	@Override protected void reverse(long iTime,CDasherModel pModel) {
		m_iKeyDownTime = Long.MAX_VALUE;
		super.reverse(iTime, pModel);
	}
	
	@Override protected void run(long iTime, CDasherModel pModel) {
		m_iKeyDownTime = Long.MAX_VALUE;
		super.run(iTime,pModel);
	}
	
}
