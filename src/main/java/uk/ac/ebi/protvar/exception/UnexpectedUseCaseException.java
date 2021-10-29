package uk.ac.ebi.protvar.exception;

public class UnexpectedUseCaseException extends RuntimeException {
  public UnexpectedUseCaseException(String message) {
    super(message);
  }

  public UnexpectedUseCaseException(String message, Throwable cause) {
    super(message, cause);
  }
}
