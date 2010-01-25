package dasher;

import java.io.IOException;
import java.io.InputStream;

import org.xml.sax.SAXException;

public interface XMLFileParser {
	public void ParseFile(InputStream in, boolean bLoadMutable) throws SAXException, IOException;

}
