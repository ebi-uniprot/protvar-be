package uk.ac.ebi.protvar.exception;

/** A browse query exceeded its time budget; returned to the client as 400 {"error": message}. */
public class QueryTooBroadException extends RuntimeException {
    public QueryTooBroadException(String message) {
        super(message);
    }
}
