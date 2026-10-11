package ezbuild.service;

/** 404. Also used for "exists but isn't yours" so ids can't be probed. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
