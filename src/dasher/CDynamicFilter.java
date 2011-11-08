package dasher;

public abstract class CDynamicFilter extends CInputFilter {

	private long m_iStartTime;

	public CDynamicFilter(CDasherComponent creator, CDasherInterfaceBase iface,
			String szName) {
		super(creator, iface, szName);
	}

	@Override
	public boolean supportsPause() {return true;}

	/** Computes multiplier to apply to speed for this frame.
	 * The default implementation returns the model's viscosity, unless it's less than
	 * <code>LP_SLOW_START_TIME</code> time since we last unpaused, in which case we
	 * interpolate from 0.1 to 1.0 times that. 
	 * @param Time current time
	 * @return multiplier to apply to speed; 0.0 = go nowhere, 1.0 = normal speed, higher = faster!
	 */
	protected float getSpeedMul(CDasherModel pModel, long Time) {
		float dMul = pModel.getViscosity();
		if (m_iStartTime==-1) m_iStartTime=Time;
		if (Time-m_iStartTime < GetLongParameter(Elp_parameters.LP_SLOW_START_TIME)) {
			dMul *= 0.1f+0.9f*(Time-m_iStartTime)/GetLongParameter(Elp_parameters.LP_SLOW_START_TIME);
		}
		return dMul;
	}

	/**
	 * Watches for unpausing so we know to begin slowstart.
	 */
	public void HandleEvent(EParameters eParam) {
		if (eParam == Ebp_parameters.BP_DASHER_PAUSED) {
			if (!GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED))
				m_iStartTime=-1;
		}
	}

}