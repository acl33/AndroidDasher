package dasher.android;

import java.lang.reflect.Field;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

/** Utility class for interpreting the inputType field of an EditorInfo */
public class InputTypes {
	/** The class names are the fields TYPE_CLASS_XYZ of
	 * android.text,InputType; among thosefields, this routine
	 * identifies that with the value corresponding to the inputType
	 * parameter (masked with TYPE_MASK_CLASS) 
	 * @param inputType value of field of same name of EditorInfo
	 * @return the XYZ part of the name of the identified field
	 * @throws IllegalArgumentException if no appropriately-named field
	 * with the correct value could be found.
	 */
	public static String getClassName(EditorInfo info) {
		if (info.inputType==InputType.TYPE_NULL) return "NULL"; //inputType==0 is a common case...
		int i = info.inputType & InputType.TYPE_MASK_CLASS;
		for (Field f : InputType.class.getFields())
			try {
				if (f.getName().startsWith("TYPE_CLASS_")
						&& f.getInt(null)==i)
					return f.getName().substring("TYPE_CLASS_".length());
			} catch (IllegalAccessException e) {
				//Shouldn't happen, but hope some other field matches!
				android.util.Log.d("DasherIME","Couldn't read field "+f.getName()+", skipping...", e);
			}
		throw new IllegalArgumentException("InputType class "+info.inputType+" not found");
	}
	
	/**
	 * The variations of classname ABC (see {@link #getClassName(int)} 
	 * are stored in the fields TYPE_ABC_VARIATION_XYZ of android.text.InputType;
	 * this routine identifies the field with value corresponding to the
	 * inputType parameter (masked with TYPE_MASK_VARIATION)
	 * @param inputType the value of the inputType field of the EditorInfo object
	 * (both classname and variation parts are used)
	 * @return the XYZ part of the name of the identified field
	 * @throws IllegalArgumentException if no appropriately-named field
	 * with the correct value could be found, or similarly, if the
	 * classname of the provided inputType could not be identified.
	 */
	public static String getVariationName(EditorInfo info) {
		if (info.inputType == InputType.TYPE_NULL) return "NULL";
		String s = getDesc(info);
		return s.substring(s.indexOf(".")+1);
	}
	
	public static String getDesc(EditorInfo info) {
		String className = getClassName(info);
		String varPrefix = "TYPE_"+className+"_VARIATION_";
		if (info.inputType==InputType.TYPE_NULL) return "NULL";
		int i = info.inputType & InputType.TYPE_MASK_VARIATION;
		for (Field f : InputType.class.getFields())
			try {
				if (f.getName().startsWith(varPrefix)
						&& f.getInt(null)==i)
					return className+"."+f.getName().substring(varPrefix.length());
			} catch (IllegalAccessException e) {
				//Shouldn't happen, but hope some other field matches!
				android.util.Log.d("DasherIME","Couldn't read field "+f.getName()+", skipping...", e);
			}
		return className+".NORMAL";
	}

	/** Determines whether the specified inputType indicates a hidden
	 * (password) field. Checks against known values (at present only
	 * TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_PASSWORD, as only these are
	 * present in earlier versions of Android - we could hardcode in others,
	 * but then would no longer compile for Android 1.6), and then also
	 * looks for any variation of any class whose name (obtained by
	 * {@link #getVariationName(int)}) contains the string "PASSWORD".
	 * @param inputType value of the inputType field of the EditorInfo object
	 * @return true if this indicates a password.
	 */
	public static boolean isPassword(EditorInfo info) {
		final int inputType = info.inputType;
		if ((inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
				&& (inputType & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD)
			return true;
		return getVariationName(info).contains("PASSWORD");
	}
}
