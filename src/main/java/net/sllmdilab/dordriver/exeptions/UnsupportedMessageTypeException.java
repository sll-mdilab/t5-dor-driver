package net.sllmdilab.dordriver.exeptions;

public class UnsupportedMessageTypeException extends Exception {
	
	private static final long serialVersionUID = 1L;

	public UnsupportedMessageTypeException(String message) {
		super(message);
	}
}