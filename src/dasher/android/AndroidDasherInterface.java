package dasher.android;

import java.io.File;
import java.util.Collection;

import android.content.SharedPreferences;

import dasher.CDasherInterfaceBase;
import dasher.CSettingsStore;

public class AndroidDasherInterface extends CDasherInterfaceBase {
	public interface Host {
		public void Redraw(boolean bChanged);
		public SharedPreferences getSharedPreferences();
	}
	private final Host host;
	AndroidDasherInterface(Host host) {
		this.host = host;
	}
	@Override
	public void CreateSettingsStore() {
		m_SettingsStore = new AndroidSettings(m_EventHandler,host.getSharedPreferences());
	}

	@Override
	public int GetFileSize(String strFileName) {
		return (int)new File(strFileName).length();
	}

	@Override
	public void Redraw(boolean bChanged) {
		host.Redraw(bChanged);
	}

	@Override
	public void ScanAlphabetFiles(Collection<String> vFileList) {
		// TODO Auto-generated method stub

	}

	@Override
	public void ScanColourFiles(Collection<String> vFileList) {
		// TODO Auto-generated method stub

	}

	@Override
	public void SetupPaths() {
		// TODO Auto-generated method stub

	}

	@Override
	public void SetupUI() {
		// TODO Auto-generated method stub

	}

}
