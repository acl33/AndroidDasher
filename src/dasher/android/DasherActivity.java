package dasher.android;

import java.util.ListIterator;

import dasher.CEventHandler;
import dasher.CSettingsStore;
import dasher.CStylusFilter;
import dasher.android.DasherCanvas.TouchInput;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class DasherActivity extends Activity {
	private ADasherInterface intf;
	private DasherCanvas surf;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	Log.d("DasherIME","Activity onCreate");
        super.onCreate(savedInstanceState);
        intf = new ADasherInterface(this) {
        	private String allEntered = "";
        	
        	@Override
        	protected CSettingsStore createSettingsStore(CEventHandler handler) {
        		return new AndroidSettings(handler, getSharedPreferences("DasherPrefs", MODE_PRIVATE));
        	}

        	@Override
        	public ListIterator<Character> charactersEntered() {
        		return new ListIterator<Character>() {
    				private int index = allEntered.length();
			
					public boolean hasNext() {return index < allEntered.length();}

					public boolean hasPrevious() {return index > 0;}

					public Character next() {return allEntered.charAt(index++);}

					public int nextIndex() {return index;}

					public Character previous() {return allEntered.charAt(--index);}

					public int previousIndex() {return index-1;}

					public void add(Character object) {throw new UnsupportedOperationException("Immutable");}
					public void remove() {throw new UnsupportedOperationException("Immutable");}
					public void set(Character object) {throw new UnsupportedOperationException("Immutable");}
				};
        	}
        	@Override
        	public void Redraw(boolean bChanged) {
        		surf.requestRender();
        	}

			@Override
			public void CreateModules() {
				super.CreateModules();
				TouchInput touchIn = new TouchInput(this);
				touchIn.setCanvas(surf);
				RegisterModule(touchIn);
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
