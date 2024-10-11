package azkaban.jobtype.commons;

public class MaskCheckNotExistException extends Exception {

    public MaskCheckNotExistException(final String message) {
        super(message);
    }
}
