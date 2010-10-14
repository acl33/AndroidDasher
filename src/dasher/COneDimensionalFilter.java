package dasher;

/** Overrides ApplyTransform to apply 1D remapping (using old x coord as radius)
 * if and only if {@link Ebp_parameters#BP_ONE_DIMENSIONAL_MODE} is enabled.
 * @author Alan Lawrence <acl33@inf.phy.cam.ac.uk>
 */
public class COneDimensionalFilter extends CDefaultFilter {

	public COneDimensionalFilter(CDasherInterfaceBase iface, CSettingsStore SettingsStore, long iID, String szName) {
		super(iface, SettingsStore, iID, szName);
	}

	/**
	 * If {@link Ebp_parameters#BP_ONE_DIMENSIONAL_MODE} is set, calls {@link #Apply1DTransform}
	 * (and then skips superclass method); otherwise, falls back to superclass.
	 */
	@Override public void ApplyTransform(CDasherView pView, long[] coords) {
		if (GetBoolParameter(Ebp_parameters.BP_ONE_DIMENSIONAL_MODE))
			Apply1DTransform(pView, coords);
		else
			super.ApplyTransform(pView, coords);
	}
	
	private final int forwardmax=(int)(GetLongParameter(Elp_parameters.LP_MAX_Y)/2.5); //of 1D transform
	
	/**
	 * Applies the 1D remapping: as Y increases from 0 to LP_MAX_Y, starts & ends at the origin,
	 * then moves in two semicircles round the back, joined by a larger semicircle allowing forwards motion.
	 * X co-ordinate is used as radius: X=0 => full radius, X=(max visible x) => 0-radius. This is calculated
	 * using dasher co-ords (inc. logarithmic compression left of crosshair), so the input x has to be quite
	 * far to the left (=high) to reduce the radius significantly.
	 * @param pView
	 * @param coords
	 */
	protected void Apply1DTransform(CDasherView pView, long[] coords) {
		// The distance between the Y coordinate and the centreline in pixels
		final long disty=GetLongParameter(Elp_parameters.LP_OY)-coords[1];
		  
		final long circlesize = (long)(forwardmax*(1.0-coords[0]/(double)pView.VisibleRegion().maxX));
		final long yforwardrange = (GetLongParameter(Elp_parameters.LP_MAX_Y)*5)/16;
		final long yfullrange = GetLongParameter(Elp_parameters.LP_MAX_Y)/2;
		  
		double x,y; //0,0=on crosshair; positive=forwards/up...	
		
		if (disty<=yforwardrange && disty>=-yforwardrange) {
			//go forwards!
			final double angle=((disty*3.14159/2)/(double)yforwardrange);
			x=Math.cos(angle);
			y=-Math.sin(angle);
		} else if (disty<=yfullrange && disty>=-yfullrange) {
			final long ybackrange = yfullrange-yforwardrange;
			final long ellipse_eccentricity=6;
			//backwards, off bottom or top...
			final double yb = (Math.abs(disty)-yforwardrange)/(double)ybackrange;
			final double angle=(yb*3.14159)*(yb+(1-yb)*(ybackrange/(double)yforwardrange/ellipse_eccentricity));
		    
		    x=-Math.sin(angle)*ellipse_eccentricity/2.0;
		    y=(1.0+Math.cos(angle))/2.0;
		    if (disty>yforwardrange) y=-y; //backwards off top
		} else {
			//off limits, go nowhere
			x=0; y=0;
		} 
		coords[0] = GetLongParameter(Elp_parameters.LP_OX)-(long)(x*circlesize);
		coords[1] = GetLongParameter(Elp_parameters.LP_OY)+(long)(y*circlesize);
	}
	
	@Override protected void CreateStartHandler() {
		if (GetBoolParameter(Ebp_parameters.BP_CIRCLE_START)) {
			m_StartHandler= new CCircleStartHandler(m_Interface, m_SettingsStore) {
				private CDasherView.Point fwdCircle;
				@Override protected CDasherView.Point getScreenCenter(CDasherView pView) {
					if (GetBoolParameter(Ebp_parameters.BP_DASHER_PAUSED)
							&& GetBoolParameter(Ebp_parameters.BP_ONE_DIMENSIONAL_MODE)) {
						//move circle
						if (fwdCircle==null) {
							long rad = GetLongParameter(Elp_parameters.LP_CIRCLE_PERCENT)*GetLongParameter(Elp_parameters.LP_OY)/100;
							fwdCircle = pView.Dasher2Screen(GetLongParameter(Elp_parameters.LP_OX)-forwardmax+rad, GetLongParameter(Elp_parameters.LP_OY));
							
						}
						return fwdCircle;
					}
					return super.getScreenCenter(pView);
				}
			};
		} else
			super.CreateStartHandler();
	}
	
}
