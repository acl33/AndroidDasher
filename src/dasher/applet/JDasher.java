package dasher.applet;

import dasher.CDasherInterfaceBase;
import dasher.CSettingsStore;

public abstract class JDasher extends CDasherInterfaceBase {
	
	public JDasher() {
		super(createSettingsStore());
	}
	
	/*A single public point-of-entry for setting up the interface */
	public void Realize() {
		LoadData();
		DoSetup();
	}
	
	/**
	 * Attempts to create a JSettings object; if a StoreUnavailableException
	 * is produced in the course of this, we fall back and produce a 
	 * standard CSettingsStore.
	 */
	private static CSettingsStore createSettingsStore() {
		CSettingsStore sets;
		try {
			sets = new JSettings();
		} catch (StoreUnavailableException e) {
			// We can't use the registry/config file due to security problems.
			sets = new CSettingsStore();
		}
		sets.LoadPersistent();
		return sets;
	}
}
