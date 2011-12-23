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

import java.util.HashMap;
import java.util.Map;

/**
 * Interface implemented by all parameter enumerations, allowing
 * an EParameters to be passed as a generic parameter of indeterminate
 * type.
 * All C++-style integer based enums have now been replaced by three Enum types
 * which implement EParameters, meaning one can pass both a generic parameter AND
 * a specialised parameter. For references into the tables, the .ordinal() of
 * a specialised parameter is used. All switch() statements should now check
 * the parameter's type, cast it to the appropriate one, and then switch on
 * the relevant enum. Alternatively it may be possible to have cases of a
 * child-type, I've yet to check this.
 * 
 * 14/07: The whole codebase is now converted to use the new parameter scheme.
 * It's broadly very solid; everything is passed around as enum types until the actual
 * load/store instructions in CSettingsStore, whereupon ordinals are taken.
 * 
 * The only weakness is that one CANNOT in fact switch on an EParameters, since
 * there is no way for the compiler to know that all its children are Enums.
 * There may be some way around this -- some sort of enum-interface -- but
 * I haven't found it yet. This can be solved by splitting any switch
 * into three, type-checking, casting, and then switching in a type-specific
 * manner.
 */
public interface EParameters {

	public int key();
	public String regName();
	public void reset(CSettingsStore ss);
	public static final Map<String,EParameters> BY_NAME=new HashMap<String,EParameters>();
	
	/* CSFS: This space intentionally left blank.
	 * 
	 * This is the parent class for the enumerations of all three types of parameters.
	 * Ebp_parameters houses the boolean parameters,
	 * Elp_parameters the longs,
	 * and Esp_parameters the strings.
	 */
	
}
