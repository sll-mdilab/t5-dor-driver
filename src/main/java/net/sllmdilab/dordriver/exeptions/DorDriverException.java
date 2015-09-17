package net.sllmdilab.dordriver.exeptions;

public class DorDriverException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public DorDriverException() {
	}

	public DorDriverException(String message) {
		super(message);
	}

	public DorDriverException(Throwable cause) {
		super(cause);
	}

	public DorDriverException(String message, Throwable cause) {
		super(message, cause);
	}

	public DorDriverException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
