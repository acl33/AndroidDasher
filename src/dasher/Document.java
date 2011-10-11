package dasher;

/**
 * <p>Representation of text in terms of java characters, i.e. unicode values 0-65535.
 * Unicode characters above this (i.e. 32-bits) are represented as TWO such characters,
 * at distinct (adjacent) offsets.</p>
 * <p>IOW, much as Java Strings.</p>
 * @author acl33
 *
 */
public interface Document {
	/** Get the character at the specified index, or null if the document does not contain
	 * such an index. (Note, it is suggested to use <code>Character.valueOf(...)</code> to
	 * reduce allocation.)
	 * @param pos Position in the document (0=first). Note, if this method returns null for
	 *  some value of pos, it should also return null for all indices greater than that value.
	 * @return Character object for the char at the specified index, or null if that's beyond
	 * the end of the Document.
	 */
	public Character getCharAt(int pos);
}
