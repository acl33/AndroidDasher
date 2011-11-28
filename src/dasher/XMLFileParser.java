package dasher;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Abstract superclass for XML file parsing (colours, alphabets).
 * Exists for two reasons:
 * <UL>
 * <LI> To provide a common (abstract) entry point for parsing files
 * found by implementations of {@link CDasherInterfaceBase#ScanXMLFiles(XMLFileParser, String)}
 * <LI> Provides a common implementation of {@link #getStream(String)}, used
 * to try to locate DTDs (differently by subclasses).
 * @author acl33
 */
public abstract class XMLFileParser extends DefaultHandler {
	protected final CDasherInterfaceBase m_Interface;
	private final SAXParser parser;
	public XMLFileParser(CDasherInterfaceBase intf) {
		this.m_Interface = intf;
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser p;
		try {
			p = factory.newSAXParser();
		} catch(Exception e) {
			//Report error, but continue - and don't parse files -
			// so user gets something minimal rather than nothing
			System.out.printf("Exception creating XML parser: %s%n", e);
			p=null;
		}
		parser=p;
	}
	
	/**
	 * Process the provided XML file!
	 * @param in InputStream allowing the file to be read
	 * @param bLoadMutable True if the file should be allowed to be edited (e.g.
	 * per-user files), false if not (e.g. for system files). TODO, editing is
	 * not actually implemented at this point; so either implement it, or remove
	 * the API...
	 * @throws SAXException If there was an error during parsing - XML malformed?
	 * @throws IOException If the file/stream could not be properly read
	 */
	public void ParseFile(InputStream in, boolean bLoadMutable) throws SAXException, IOException {
		if (parser!=null)
			parser.parse(new InputSource(in),this);
	}
	
	/**
	 * Method to lookup resources e.g. DTD files. Provided to ease
	 * implementation of DefaultHandler#resolveEntity(String,String).
	 * Looks up the filename using {@link CDasherInterfaceBase#GetStreams(String, java.util.Collection)},
	 * and returns an InputSource constructed from the first stream found, or else null.
	 * 
	 * @param name Filename needed (e.g. "alphabet.dtd")
	 * @return InputSource for the resource/entity, if possible, else null.
	 */
	protected final InputSource getStream(String name) {
		List<InputStream> streams = new ArrayList<InputStream>();
		m_Interface.GetStreams(name, streams);
		if (!streams.isEmpty()) return new InputSource(streams.get(0));
		return null;
	}

}
