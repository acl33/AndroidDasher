package dasher.android;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import dasher.CAlphIO;
import dasher.CColourIO;
import dasher.CEventHandler;
import dasher.CSettingsStore;
import dasher.CStylusFilter;
import dasher.XMLFileParser;
import dasher.CLockEvent;
import android.app.Activity;
import android.content.res.AssetManager;
import android.os.Bundle;

public class DasherActivity extends Activity {
	private ADasherInterface intf;
	private DasherCanvas surf;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        intf = new ADasherInterface() {
        	@Override
        	public void Redraw(boolean bChanged) {
        		surf.requestRender();
        	}

			@Override
			protected CSettingsStore createSettingsStore(CEventHandler handler) {
				return new AndroidSettings(handler, getSharedPreferences("DasherPrefs", MODE_PRIVATE));
			}
			
			@Override
			protected void CreateModules() {
				RegisterModule(new CStylusFilter(this, m_SettingsStore));
				surf.CreateModules();
			}
			
			@Override
			public void ScanColourFiles(CColourIO colourIO) {
				ScanXMLFiles(colourIO, "colour");
			}
			
			@Override
			public void ScanAlphabetFiles(CAlphIO alphIO) {
				ScanXMLFiles(alphIO,"alphabet");
			}
			
			private void ScanXMLFiles(XMLFileParser parser, String prefix) {
				AssetManager assets = DasherActivity.this.getAssets();
				try {
					for (String aFile : assets.list("")) {//DasherActivity.this.fileList()) {
						if (aFile.contains(prefix) && aFile.endsWith(".xml"))
							try {parser.ParseFile(assets.open(aFile), false);}
							catch (Exception e) {
								System.err.println("Could not parse/read asset "+aFile+": "+e);
							}
					}
				} catch (IOException e) {
					System.err.println("Could not list assets: " + e.toString());
				}
				File userDir = DasherActivity.this.getDir("data", MODE_WORLD_WRITEABLE);
				
				for (String aFile : userDir.list()) {
					if (aFile.contains(prefix) && aFile.endsWith(".xml"))
						try {parser.ParseFile(new FileInputStream(new File(userDir,aFile)), true);}
						catch (Exception e) {
							System.err.println("Could not parse/read user file "+aFile+": "+e);
						}
				}
			}
			
			protected void train(String trainFileName, CLockEvent evt) {
				int iTotalBytes=0;
				List<InputStream> streams=new ArrayList<InputStream>();
				//1. system file...
				try {
					InputStream in = DasherActivity.this.getAssets().open(trainFileName);
					iTotalBytes+=in.available();
					streams.add(in);
					//AssetFileDescriptor fd = DasherActivity.this.getAssets().openFd(trainFileName);
					//iTotalBytes += fd.getLength();
					//streams.add(fd.createInputStream());
				} catch (IOException e) {
					//no system training file present. Which is fine; silently skip.
				}
				//2. user file
				File f=new File(trainFileName);
				if (f.exists()) {
					try {
					iTotalBytes += f.length();
						streams.add(new FileInputStream(f));
					} catch (FileNotFoundException fnf) {
						//we checked f.exists()...
						throw new AssertionError();
					}
				}
				
				int iRead = 0;
				for (InputStream in : streams) {
					try {
						iRead = TrainStream(in, iTotalBytes, iRead, evt);
					} catch (IOException e) {
						android.util.Log.e("dasher", "error in training - rest of text skipped", e);
					}
				}
			}
			
        };
        surf = new DasherCanvas(this,intf);
        //TextView text = new TextView(this);
        //text.setText("Hello!");
        //setContentView(text);
        intf.Realize();
        setContentView(surf);
        surf.startAnimating();
    }
}
