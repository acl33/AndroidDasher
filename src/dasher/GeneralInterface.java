package dasher;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Code excised from DasherInterfaceBase to make it more general, but that
 * might nonetheless be required by implementations on other (non-Android)
 * platforms (i.e., these should subclass GeneralInterface rather than D.I.B.)
 */
public abstract class GeneralInterface extends CDasherInterfaceBase {

	protected void train(String T) {
		int iTotalBytes = 0;
		iTotalBytes += GetFileSize(GetStringParameter(Esp_parameters.SP_SYSTEM_LOC) + T);
		iTotalBytes += GetFileSize(GetStringParameter(Esp_parameters.SP_USER_LOC) + T);
		
		if(iTotalBytes > 0) {
			int iOffset;
			iOffset = TrainFile(GetStringParameter(Esp_parameters.SP_SYSTEM_LOC) + T, iTotalBytes, 0);
			TrainFile(GetStringParameter(Esp_parameters.SP_USER_LOC) + T, iTotalBytes, iOffset);
		}
		else {
			CMessageEvent oEvent = new CMessageEvent("No training text is avilable for the selected alphabet. Dasher will function, but it may be difficult to enter text.\nPlease see http://www.dasher.org.uk/alphabets/ for more information.", 0, 0);
			InsertEvent(oEvent);
		}
	}
	/**
	 * Trains our Model from the text found in a given file,
	 * which must be UTF-8 encoded.
	 * <p>
	 * The actual training is done by TrainStream; this just
	 * creates a FileInputStream and passes it into TrainStream.
	 * 
	 * @param Filename Path (absolute or relative) to file containing training text.
	 * @param iTotalBytes Number of bytes to read
	 * @param iOffset Offset at which to start reading
	 * @return Number of bytes read
	 */
	public int TrainFile(String Filename, int iTotalBytes, int iOffset) {
		
		/* CSFS: This has now been split into two parts in order to accomodate
		 * training from streams which aren't files. This is especially
		 * relevant using an Applet. This method is overridden in JDasher to
		 * permit the use of JAR resources instead. The bulk of the functionality
		 * is now in TrainStream, which takes an InputStream as an argument.
		 */
		
		if(Filename == "")
			return 0;
		
		InputStream FileIn;
		try {
			FileIn = new BufferedInputStream(new FileInputStream(Filename));
		}
		catch (Exception e){
			return 0;
		}

		return TrainStream(FileIn, iTotalBytes, iOffset);
		
	}

	/**
	 * Should return the size of a given file, or 0 if the
	 * file does not exist or cannot be accessed.
	 * 
	 * @param strFileName File to be read
	 * @return Size of this file, or 0 on error.
	 */
	public abstract int GetFileSize(String strFileName);
	
}
