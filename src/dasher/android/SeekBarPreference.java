/* The following code was written by Matthew Wiggins 
 * and is released under the APACHE 2.0 license 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package dasher.android;

import dasher.EParameters;
import dasher.Elp_parameters;
import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.LinearLayout;


public class SeekBarPreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener
{
  private static final String androidns="http://schemas.android.com/apk/res/android";

  private SeekBar mSeekBar;
  private TextView mValueText;
  
  private final String mSuffix, mTitle;
  private final long lDef;
  private int lMin, lMax, lDiv;
  private long lValue;

  public SeekBarPreference(Context context, AttributeSet attrs) { 
    super(context,attrs); 
    String temp=attrs.getAttributeValue(androidns, "title");
    if (temp.startsWith("@")) {
    	try {
    		temp = getContext().getResources().getString(Integer.parseInt(temp.substring(1)));
    	} catch (NumberFormatException e) {
    		//hmm. any ideas?
    	}
    }
    mTitle=temp;
    if (!isPersistent() || !hasKey()) throw new IllegalArgumentException("Non-persistent attribute "+mTitle);
    mSuffix = attrs.getAttributeValue(androidns,"text");
    Elp_parameters param = (Elp_parameters)EParameters.BY_NAME.get(getKey());
    lDef = (param==null) ? attrs.getAttributeIntValue(androidns,"defaultValue", 0) : param.defaultVal;
    lMax = attrs.getAttributeIntValue(androidns,"max", 100);
    lMin = attrs.getAttributeIntValue(null, "min", 0);
    lDiv = attrs.getAttributeIntValue(null, "divisor", 1);
  }
  
  @Override public void onAttachedToHierarchy(PreferenceManager pm) {
	  super.onAttachedToHierarchy(pm);
	  lValue = getPersistedLong(lDef);
	  updateTitle();
  }
  @Override 
  protected View onCreateDialogView() {
    LinearLayout layout = new LinearLayout(getContext());
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(6,6,6,6);

    mValueText = new TextView(getContext());
    mValueText.setGravity(Gravity.CENTER_HORIZONTAL);
    mValueText.setTextSize(32);
    
    mSeekBar = new SeekBar(getContext());
    mSeekBar.setOnSeekBarChangeListener(this);
    layout.addView(mSeekBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

    mSeekBar.setMax(lMax-lMin);
    //onBindDialogView sets current value (=>calls onProgressChanged =>sets mValueText)
    
    layout.addView(mValueText, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.FILL_PARENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT));
    return layout;
  }
  
  @Override 
  protected void onBindDialogView(View v) {
    super.onBindDialogView(v);
    mSeekBar.setProgress((int)(lValue-lMin));
  }
  
  @Override protected void onSetInitialValue(boolean restore, Object defaultValue) {
    super.onSetInitialValue(restore, defaultValue);
    if (restore)  //"use settings store" - defaultValue==null !
      lValue = getPersistedLong(lDef);
    else  //"don't use settings store" - there's a default here instead
      lValue = ((Number)defaultValue).longValue();
  }
  
  public void onProgressChanged(SeekBar seek, int value, boolean fromTouch) {
    lValue = value+lMin;
    callChangeListener(new Integer(value));
    String t=valueText();
    mValueText.setText(mSuffix==null ? t : t.concat(mSuffix));
  }
  
  private String valueText() {
	  return (lDiv==1) ? String.valueOf(lValue) : String.valueOf(lValue/(double)lDiv);
  }
  
  private void updateTitle() {
	  setTitle(mTitle+": "+valueText());
  }
  @Override public void onClick(DialogInterface dialog, int button) {
	  if (button==DialogInterface.BUTTON1) {
		  //ok button. Store result of sliding, and set the title text
		  persistLong(lValue);
		  updateTitle();
	  } else {
		  //cancel button. Restore old value, leave title text alone.
		  lValue = getPersistedLong(lDef);
	  }
  }

  public void onStartTrackingTouch(SeekBar seekBar) {}

  public void onStopTrackingTouch(SeekBar seekBar) {}

}