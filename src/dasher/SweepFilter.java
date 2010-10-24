package dasher;

public class SweepFilter extends COneDimensionalFilter {

	public SweepFilter(CDasherInterfaceBase iface,
			CSettingsStore SettingsStore) {
		super(iface, SettingsStore, 17, "One-Button Sweep Mode");
		fakeInput = new CDasherInput(iface,SettingsStore, -1, "Unregistered Module") {
			@Override public boolean GetDasherCoords(CDasherView pView, long[] coords) {
				coords[0] = 0; coords[1] = m_iLastY;
				return true;
			}
		};
	}
	
	private long m_iLastY;
	private long m_iLastTime;
	private boolean increasing;
	
	private final CDasherInput fakeInput;
	/** apply 1D transform irrespective of BP_ONE_DIMENSIONAL_MODE */
	@Override public void ApplyTransform(CDasherView pView, long[] coords) {
		Apply1DTransform(pView, coords);
	}
	
	@Override public boolean Timer(long Time, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
		if (GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED)) return false;
		long move = (Time-m_iLastTime) * GetLongParameter(Elp_parameters.LP_MAX_Y) / GetLongParameter(Elp_parameters.LP_SWEEP_TIME);
		m_iLastTime = Time;
		if (increasing
				? (m_iLastY+=move) >= GetLongParameter(Elp_parameters.LP_MAX_Y)
						: (m_iLastY-=move) <=0) {
			m_Interface.PauseAt(0, 0);
			return false;
		}
		return super.Timer(Time, pView, fakeInput, pModel);
	}

	@Override public void KeyDown(long time, int id, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
		if (GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED)) {
			//if (we paused because) we reached an extreme, reverse
			if (m_iLastY>=GetLongParameter(Elp_parameters.LP_MAX_Y)) {
				m_iLastY = GetLongParameter(Elp_parameters.LP_MAX_Y);
				increasing=false;
			} else if (m_iLastY <= 0) {
				m_iLastY=0;
				increasing=true;
			}
			//and unpause!
			m_Interface.Unpause(time);
			m_iLastTime=time;
		} else //reverse! 
			increasing = !increasing;
	}
	
	@Override public boolean supportsPause() {return true;}
	@Override public void CreateStartHandler() {}
}
