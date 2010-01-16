package dasher.android;

import java.io.File;
import java.util.Collection;

import android.content.SharedPreferences;

import dasher.CDasherInterfaceBase;
import dasher.CEventHandler;
import dasher.CSettingsStore;

public abstract class AndroidDasherInterface extends CDasherInterfaceBase {
	@Override
	public int GetFileSize(String strFileName) {
		return (int)new File(strFileName).length();
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

}
