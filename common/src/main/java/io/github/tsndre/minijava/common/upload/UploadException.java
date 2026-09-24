package io.github.tsndre.minijava.common.upload;

/** An upload was rejected or could not be stored; the message is safe to show to the client. */
public class UploadException extends Exception {

    public UploadException(String message) {
        super(message);
    }
}
