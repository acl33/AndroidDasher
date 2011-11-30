/*
  This file is part of JDasher.

  JDasher is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  JDasher is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with JDasher; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

  Copyright (C) 2006      Christopher Smowton <cs448@cam.ac.uk>

  JDasher is a port derived from the Dasher project; for information on
  the project see www.dasher.org.uk; for information on JDasher itself
  and related projects see www.smowton.net/chris

*/

package dasher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * 
 * Responsible for reading a given list of XML files, extracting
 * colour scheme information, and creating a list of ColourInfo objects
 * for each.
 * <p>
 * Further, after doing so, responsible for returning a ColourInfo
 * object corresponding to a given alphabet name, and of giving
 * a full list of available colour schemes.
 *
 */
public class CColourIO extends XMLFileParser {

	/**
	 * Map from colour scheme names to their ColourInfo objects.
	 */
	protected HashMap<String, ColourInfo> Colours = new HashMap<String,ColourInfo>(); // map short names (file names) to descriptions
		
	/**
	 * Simple struct which represents a colour scheme.
	 */
	public static class ColourInfo {
	    // Basic information
		/**
		 * Friendly name of this colour-scheme.
		 */
	    String ColourID;
	    
	    // Complete description of the colour:
	    /**
	     * Array of scheme's defined colours' red values.
	     */
	    ArrayList<Integer> Reds = new ArrayList<Integer>();
	    
	    /**
	     * Array of scheme's defined colours' green values.
	     */
	    ArrayList<Integer> Greens = new ArrayList<Integer>();
	    
	    /**
	     * Array of scheme's defined colours' blue values.
	     */
	    ArrayList<Integer> Blues = new ArrayList<Integer>();
	}
	
	/**
	 * Sole constructor. Parses a given list of XML files; once
	 * the constructor terminates, the class is ready to give
	 * a list of available colour schemes and return ColourInfo
	 * objects requested by name.
	 * 
	 * @param SysLoc System data location to search for DTD files. 
	 * @param UserLoc User data location to search for DTD files.
	 * @param dib Interface against which we can perform applet-style IO. Optional; if null, applet-style IO will not be attempted.
	 */
	public CColourIO(CDasherInterfaceBase dib) {
		super(dib);
		CreateDefault();
	}
	
	protected CColourIO.ColourInfo currentColour;
				
	@Override public void startElement(String namespaceURI, String simpleName, String qualName, Attributes tagAttributes) {
		
		String tagName = (simpleName.equals("") ? qualName : simpleName);
		
		if(tagName.equals("palette")) {
			currentColour = new CColourIO.ColourInfo();
			currentColour.ColourID = tagAttributes.getValue("name");
		}
		else if(tagName.equals("colour")) {
			currentColour.Reds.add(Integer.parseInt(tagAttributes.getValue("r")));
			currentColour.Greens.add(Integer.parseInt(tagAttributes.getValue("g")));
			currentColour.Blues.add(Integer.parseInt(tagAttributes.getValue("b")));
		}
	}
	
	@Override public void endElement(String namespaceURI, String simpleName, String qualName) {
		String tagName = (simpleName.equals("") ? qualName : simpleName);
		
		if(tagName.equals("palette")) {
			Colours.put(currentColour.ColourID, currentColour);
		}
	
	}
	
	@Override public InputSource resolveEntity(String publicName, String systemName) throws IOException, SAXException {
		/* CSFS: This is here because SAX will by default look in a system location
		 * first, which throws a security exception when running as an Applet.
		 */
		return systemName.contains("colour.dtd") ? getStream("colour.dtd") : null;
	}		
	
	/**
	 * Fills a given Collection with the names of all available
	 * colour schemes.
	 * <p>
	 * Colour schemes can be made available by being parsed from
	 * an XML file by way of the ParseFile method (or through
	 * the constructor), or by the manual creation of a ColourInfo
	 * object which must then be saved using SetInfo.
	 * 
	 * @param ColourList Collection to be filled with available colours.
	 */
	public void GetColours(Collection<String> ColourList) {
		ColourList.clear();
		
		/* CSFS: Rewritten from the old version which used
		 * C++ list-iterators.
		 */
		
		for(Map.Entry<String, ColourInfo> m : Colours.entrySet()) {
			ColourList.add(m.getValue().ColourID);
		}
		
	}
	
	/**
	 * Retrieves the ColourInfo object with the given name, if any.
	 * <p>
	 * If the specified name cannot be found, then we return null.
	 * This means the user's specified name cannot be found; in that case,
	 * the client will fall back to getting a palette name from the
	 * alphabet; and if that is not found either, then will fall back to
	 * GetDefault().
	 * 
	 * @param ColourID Name of the colour scheme to retrieve.
	 * @return ColourInfo object representing the named scheme, or null
	 * if no scheme has the specified name.
	 */
	public ColourInfo getByName(String ColourID) {
		return Colours.get(ColourID);
	}
	
	public ColourInfo getDefault() {
		assert Colours.containsKey("Default");
		return Colours.get("Default");
	}
	
	/**
	 * Adds a ColourInfo object to the list of those available.
	 * At present, schemes cannot be manually stored in Dasher,
	 * but this may change in the future if user-defined schemes
	 * become a possibility.
	 * 
	 * @param NewInfo Colour scheme to save. Must not be null.
	 */
	public void SetInfo(ColourInfo NewInfo) {
		Colours.put(NewInfo.ColourID, NewInfo);
		Save(NewInfo.ColourID);
	}
	
	/**
	 * Removes a colour scheme by name. This removes the stated
	 * scheme from the list of those available; it will no longer
	 * be returned by GetColours or be retrievable by GetInfo.
	 * 
	 * @param ColourID Name of the scheme to remove. If the given name
	 * does not exist the method will return without error.
	 */
	public void Delete(String ColourID) {
		Colours.remove(ColourID);
		Save("");
	}
	
	/**
	 * Stub method. In the future, this may save a given scheme
	 * as an XML file, or in some other format. This should be
	 * implemented if user-defined or mutable colour schemes are
	 * implemented.
	 * <p>
	 * The class must already know about the scheme to be able
	 * to save it.
	 * 
	 * @param name Name of the scheme to save.
	 */
	public void Save(String name) {
		
		// Stub
		
	}
	
	/**
	 * Creates and stores a default colour scheme, named simply
	 * <i>Default</i>. This is the scheme which will be returned
	 * in the event of a non-existent scheme being requested,
	 * and can always be assumed to be available.
	 *
	 */
	protected void CreateDefault() {
		  
		ColourInfo Default = new ColourInfo();
		
		Default.ColourID = "Default";
		//0:
		Default.Reds.add(255); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(0); Default.Blues.add(0); 
		Default.Reds.add(0); Default.Greens.add(0); Default.Blues.add(0); 
		Default.Reds.add(218); Default.Greens.add(218); Default.Blues.add(218); 
		Default.Reds.add(0); Default.Greens.add(0); Default.Blues.add(0); 
		Default.Reds.add(0); Default.Greens.add(0); Default.Blues.add(0); 
		Default.Reds.add(0); Default.Greens.add(0); Default.Blues.add(0); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(80); Default.Greens.add(80); Default.Blues.add(80); 
		Default.Reds.add(235); Default.Greens.add(235); Default.Blues.add(235); 
		//10:
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		//20:
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		//30:
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		//40:
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		//50:
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		//60:
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		//70:
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		//80:
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		//90:
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		//100:
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		Default.Reds.add(180); Default.Greens.add(238); Default.Blues.add(180); 
		Default.Reds.add(155); Default.Greens.add(205); Default.Blues.add(155); 
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(255); 
		//110:
		Default.Reds.add(255); Default.Greens.add(0); Default.Blues.add(0); 
		Default.Reds.add(255); Default.Greens.add(255); Default.Blues.add(0); 
		Default.Reds.add(0); Default.Greens.add(200); Default.Blues.add(0); 
		Default.Reds.add(255); Default.Greens.add(0); Default.Blues.add(0); 
		Default.Reds.add(255); Default.Greens.add(255); Default.Blues.add(0); 
		Default.Reds.add(0); Default.Greens.add(200); Default.Blues.add(0); 
		Default.Reds.add(255); Default.Greens.add(0); Default.Blues.add(0); 
		Default.Reds.add(255); Default.Greens.add(255); Default.Blues.add(0); 
		Default.Reds.add(0); Default.Greens.add(200); Default.Blues.add(0); 
		Default.Reds.add(255); Default.Greens.add(0); Default.Blues.add(0); 
		//120:
		Default.Reds.add(255); Default.Greens.add(255); Default.Blues.add(0); 
		Default.Reds.add(0); Default.Greens.add(200); Default.Blues.add(0); 
		Default.Reds.add(255); Default.Greens.add(0); Default.Blues.add(0); 
		Default.Reds.add(255); Default.Greens.add(255); Default.Blues.add(0); 
		Default.Reds.add(0); Default.Greens.add(200); Default.Blues.add(0); 
		Default.Reds.add(255); Default.Greens.add(0); Default.Blues.add(0); 
		Default.Reds.add(255); Default.Greens.add(255); Default.Blues.add(0); 
		Default.Reds.add(0); Default.Greens.add(200); Default.Blues.add(0); 
		Default.Reds.add(255); Default.Greens.add(0); Default.Blues.add(0); 
		Default.Reds.add(255); Default.Greens.add(255); Default.Blues.add(0); 
		//130:
		Default.Reds.add(0); Default.Greens.add(0); Default.Blues.add(0); 
		Default.Reds.add(0); Default.Greens.add(0); Default.Blues.add(0); 
		Default.Reds.add(0); Default.Greens.add(0); Default.Blues.add(0); 
		Default.Reds.add(0); Default.Greens.add(0); Default.Blues.add(0); 
		Default.Reds.add(0); Default.Greens.add(0); Default.Blues.add(0); 
		Default.Reds.add(0); Default.Greens.add(0); Default.Blues.add(0); 
		Default.Reds.add(0); Default.Greens.add(0); Default.Blues.add(0); 
		Default.Reds.add(150); Default.Greens.add(255); Default.Blues.add(50); 
		Default.Reds.add(80); Default.Greens.add(80); Default.Blues.add(80); 
		Default.Reds.add(255); Default.Greens.add(255); Default.Blues.add(255); 
		//140:
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		//150:
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		//160:
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		//170:
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		//180:
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		//190:
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		//200:
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		//210:
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		//220:
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		//230:
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		Default.Reds.add(255); Default.Greens.add(174); Default.Blues.add(185); 
		Default.Reds.add(255); Default.Greens.add(187); Default.Blues.add(255); 
		Default.Reds.add(135); Default.Greens.add(206); Default.Blues.add(255); 
		//240:
		Default.Reds.add(0); Default.Greens.add(255); Default.Blues.add(0); 
		Default.Reds.add(240); Default.Greens.add(240); Default.Blues.add(0); 
		Default.Reds.add(255); Default.Greens.add(0); Default.Blues.add(0);		
		Colours.put("Default", Default);
	}
	
}