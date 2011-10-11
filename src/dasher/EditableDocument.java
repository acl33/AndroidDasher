package dasher;

public interface EditableDocument extends Document {

	/** Call to output/write text at a given position
	 * (when a symbol node is entered).
	 * @param ch String representation of <em>a single symbol</em> (i.e. one unicode character point)
	 * @param offset index where character should be afterwards. (e.g. 0=this is first character)
	 */
	public abstract void outputText(String ch, int offset);
	
	/** Call to delete text at a given position
	 * In other words, this performs a single backspace operation - when the user leaves a symbol node.
	 * @param ch String representation of <em>a single symbol</em> (i.e. one unicode character point)
	 * @param offset index where character should previously have been (e.g. 0=delete first character)
	 */
	public abstract void deleteText(String ch, int offset);
	
}
