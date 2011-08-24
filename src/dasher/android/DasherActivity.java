package dasher.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class DasherActivity extends Activity implements OnClickListener, Runnable {

	private TextView enabledLbl;
	private Button imeSettingsBtn;
	private TextView selectedLbl;
	private EditText longPress;
	private Button dasherSettingsButton;
	
	private String m_sID;
	private boolean bShownDasherSets;
	private Handler handler;
	
	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.install);
		enabledLbl = (TextView)findViewById(R.id.enable);
		imeSettingsBtn = (Button)findViewById(R.id.sys_sets_btn);
		imeSettingsBtn.setOnClickListener(this);
		
		selectedLbl = (TextView)findViewById(R.id.select);
		longPress = (EditText)findViewById(R.id.long_press);
		dasherSettingsButton = (Button)findViewById(R.id.dasher_sets_btn);
		dasherSettingsButton.setOnClickListener(this);

		handler = new Handler();
	}
	
	@Override public void onResume() {
		super.onResume();
		//1. is Dasher enabled?
		m_sID = null;
		InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
		for (InputMethodInfo inf : imm.getEnabledInputMethodList()) {
			if (inf.getPackageName().equals(this.getPackageName())
					&& inf.getServiceName().equals(DasherInputMethod.class.getName())) {
				m_sID=inf.getId(); break;
			}
		}
		
		if (m_sID==null) {
			enabledLbl.setText(R.string.DasherNeedsEnable);
			imeSettingsBtn.setEnabled(true);
		} else {
			enabledLbl.setText(R.string.DasherEnabledOk);
			imeSettingsBtn.setEnabled(false);
		}
		run();
	}
	
	/** Executed on UI Thread, either directly from onResume()
		or as a callback via handler.postDelayed(). */
	public void run() {
		//2. is Dasher the active input method?
		if (m_sID!=null && 
				Settings.Secure.getString(getContentResolver(),
Settings.Secure.DEFAULT_INPUT_METHOD).equals(m_sID)) {
			
			selectedLbl.setText(R.string.DasherSelectedOk);
			longPress.setEnabled(false);
			//go straight to settings, once
			if (!bShownDasherSets) {
				bShownDasherSets=true;
				onClick(dasherSettingsButton);
			}
		} else {
			selectedLbl.setText(R.string.DasherNeedsSelect);
			longPress.setEnabled(true);
		}
		handler.postDelayed(this,500);
	}
		
	@Override public void onPause() {
		handler.removeCallbacks(this);
		super.onPause();
	}
	
	public void onClick(View v) {
		if (v == imeSettingsBtn)
			startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
		else if (v==dasherSettingsButton)
			startActivity(new Intent(this,SettingsActivity.class));
	}

}