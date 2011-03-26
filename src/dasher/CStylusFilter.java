package dasher;

import static dasher.CDasherModel.CROSS_X;

/**
 *
 * @author acl33
 */
public class CStylusFilter extends CDefaultFilter {
    private long m_iKeyDownTime;
    private long minX;
    
    public CStylusFilter(CDasherInterfaceBase iface, CSettingsStore store) {
        this(iface,store,15,"Stylus Control");
    }

    protected CStylusFilter(CDasherInterfaceBase iface, CSettingsStore store, long iID, String sName) {
        super(iface, store, iID, sName);
        HandleEvent(new CParameterNotificationEvent(Elp_parameters.LP_MAX_ZOOM));
    }
    
    @Override
    public void KeyDown(long iTime, int keyId, CDasherView pView, CDasherInput pInput, CDasherModel model) {
        if (keyId == 100) {
            model.clearScheduledSteps();
            m_Interface.Unpause(iTime);
            m_iKeyDownTime = iTime;
        }
    }

    @Override
    public void KeyUp(long iTime, int keyId, CDasherView pView, CDasherInput pInput, CDasherModel model) {
        if(keyId == 100) {
            if ((iTime - m_iKeyDownTime < GetLongParameter(Elp_parameters.LP_TAP_TIME))) {
            	pInput.GetDasherCoords(pView, lastInputCoords);
    			ApplyClickTransform(pView, lastInputCoords);
    			model.ScheduleZoom(Math.max(minX,lastInputCoords[0]),lastInputCoords[1]);
    			//leave unpaused
            } else {
                m_Interface.PauseAt(0, 0);
            }
        }
    }
    
    @Override
    public boolean Timer(long iTime, CDasherView view, CDasherInput pInput, CDasherModel model) {
        if (model.nextScheduledStep(iTime)) {
            //continued scheduled zoom - must have been in middle
            // (and thus not cleared by subsequent click)
            return true;
            //note that this skips the rest of CDefaultFilter::Timer;
            //however, given we're paused, this is only the Start Handler,
            //which we're not using anyway.
        }
        //no zoom was scheduled.
        return super.Timer(iTime, view, pInput, model);
    }
    /**
     * Called to apply any coordinate transform required for
     * <em>click</em> coords (i.e. for a scheduled zoom, rather
     * than continuous movement towards.)
     * <p>The default is to multiply the x coordinate to incorporate the {@link Elp_parameters#LP_S}
     * safety margin, but <em>not</em> to call {@link #ApplyTransform(CDasherView, long[])};
     * subclasses may override to provide different behaviour.
     * @param dasherCoords x&amp;y dasher coordinates which will be target of zoom.
     */
    protected void ApplyClickTransform(CDasherView pView, long[] dasherCoords) {
    	dasherCoords[0] = (dasherCoords[0]*(1024+GetLongParameter(Elp_parameters.LP_S))/1024);
    }
    
    @Override public void HandleEvent(CEvent evt) {
		if (evt instanceof CParameterNotificationEvent
				&& ((CParameterNotificationEvent)evt).m_iParameter==Elp_parameters.LP_MAX_ZOOM)
			minX = Math.max(2, CROSS_X/GetLongParameter(Elp_parameters.LP_MAX_ZOOM));
		super.HandleEvent(evt);
	}
    
    /** Make sure no start handler is created - even tho we ignore changes 
     * to BP_CIRCLE_START and BP_MOUSEPOS_MODE, this still gets called by
     * the superclass constructor (if a preference enabling a start handler
     * was saved by a previous session)
     */
    @Override public void CreateStartHandler() {
    	
    }
}