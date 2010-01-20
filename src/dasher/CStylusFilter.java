/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dasher;

/**
 *
 * @author acl33
 */
public class CStylusFilter extends CDefaultFilter {
    private long m_iKeyDownTime;
    private CDasherView m_View;

    public CStylusFilter(CDasherInterfaceBase iface, CSettingsStore store) {
        this(iface,store,15,"Stylus Control");
    }

    protected CStylusFilter(CDasherInterfaceBase iface, CSettingsStore store, long iID, String sName) {
        super(iface, store, iID, sName);
    }

    public void KeyDown(long iTime, int keyId, CDasherModel model) {
        if (keyId == 100) {
            model.clearScheduledSteps();
            m_Interface.Unpause(iTime);
            m_iKeyDownTime = iTime;
        }
    }

    public void KeyUp(long iTime, int keyId, CDasherModel model) {
        if(keyId == 100) {
            if (m_View!=null &&
                (iTime - m_iKeyDownTime < GetLongParameter(Elp_parameters.LP_TAP_TIME))) {
                CDasherView.DPoint point = m_View.getInputDasherCoords();
                model.ScheduleZoom(point.x,point.y);
                //leave unpaused
            } else {
                m_Interface.PauseAt(0, 0);
            }
        }
    }

    public boolean Timer(long iTime, CDasherView view, CDasherModel model) {
        m_View = view;
        if (model.nextScheduledStep(iTime, null)) {
            //continued scheduled zoom - must have been in middle
            // (and thus not cleared by subsequent click)
            if (model.ScheduledSteps()==0) {
                //just finished. Pause (mouse not held down, or schedule
                //would have been cleared already)
                while (model.CheckForNewRoot(view)) {
                };
                SetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED, true);
            }
            return true;
            //note that this skips the rest of CDefaultFilter::Timer;
            //however, given we're paused, this is only the Start Handler,
            //which we're not using anyway.
        }
        //no zoom was scheduled.
        if (GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED)) {
            //and mouse not held down
            return false;
        }
        //mouse down...
        return super.Timer(iTime, view, model);
    }
}