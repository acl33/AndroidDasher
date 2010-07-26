package dasher.android;

import android.content.Context;
import android.util.AttributeSet;

import dasher.Esp_parameters;

public class SingleIMCheckBox extends IMCheckBox {
	
	private final String inputDevice;
	private final String inputFilter;

	public SingleIMCheckBox(Context ctx, AttributeSet attrs) {
		super(ctx,attrs);
		inputDevice = attrs.getAttributeValue(null,"inputDevice");
		inputFilter = attrs.getAttributeValue(null,"inputFilter");
		android.util.Log.d("DasherIME","Created"+this);
	}
	
	@Override protected void hasBecomeChecked() {
		super.hasBecomeChecked();
		IMDasherInterface.INSTANCE.SetStringParameter(Esp_parameters.SP_INPUT_DEVICE, inputDevice);
		IMDasherInterface.INSTANCE.SetStringParameter(Esp_parameters.SP_INPUT_FILTER, inputFilter);
	}

}
