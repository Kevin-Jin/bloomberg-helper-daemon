package in.kevinj.bloomberghelper.daemon;

import javax.ws.rs.core.MediaType;

import org.glassfish.jersey.media.multipart.MultiPart;

public class PartialMultiPart extends MultiPart {
	private boolean isFirst, isDone;

	public PartialMultiPart(MediaType mediaType) {
		super(mediaType);
		isFirst = true;
	}

	public boolean isFirst() {
		return isFirst;
	}

	public void printed() {
		isFirst = false;
		getBodyParts().clear();
	}

	public boolean isDone() {
		return isDone;
	}

	public void finished() {
		isDone = true;
	}
}
