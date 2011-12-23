package dasher;

import static dasher.CDasherModel.CROSS_Y;

public abstract class CDynamicButtons extends CDynamicFilter {

	private CDasherModel model;
	
	/** Meaningless if we are paused */
	private boolean reversing;

	public CDynamicButtons(CDasherComponent creator, CDasherInterfaceBase iface, String szName) {
		super(creator, iface, szName);
	}

	/** When reversing, backs off; when paused, does nothing; when running, delegates to TimerImpl */
	@Override
	public void Timer(long iTime, CDasherView pView, CDasherInput pInput, CDasherModel pModel) {
		if (isPaused()) return;
		
		if (reversing)
			pModel.ScheduleOneStep(41904, CROSS_Y, iTime, getSpeedMul(pModel, iTime));
		else
			TimerImpl(iTime, pView, pModel);
	}

	protected abstract void TimerImpl(long iTime, CDasherView pView, CDasherModel pModel);

	protected void ApplyOffset(CDasherModel model, int off) {
		(this.model=model).Offset(off);
	}
	
	@Override public void pause() {
		if (model!=null) model.AbortOffset();
		super.pause();
	}
	
	protected void reverse(long iTime, CDasherModel pModel) {
		if (model!=null) model.AbortOffset(); 
		reversing=true;
		super.unpause(iTime);
	}
	protected final boolean isReversing() {return !isPaused() && reversing;}
	protected void run(long iTime, CDasherModel pModel) {
		reversing=false;
		super.unpause(iTime);
	}
	protected final boolean isRunning() {return !isPaused() && !reversing;}
	@Override protected final void unpause(long iTime) {
		throw new AssertionError("Subclasses should not call directly; call run/reverse instead");
	}
}