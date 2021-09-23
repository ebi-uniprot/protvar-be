package uk.ac.ebi.pepvep.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Invalid input")
public class InvalidInputException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1308009929595550576L;

	public InvalidInputException(String message) {
		super();
		this.message = message;
	}

	private final String message;

	public String getMessage() {
		return message;
	}
}
