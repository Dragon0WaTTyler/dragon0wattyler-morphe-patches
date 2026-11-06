package retrofit2;

/** Test fixture for the verified native status field, not a network implementation. */
public final class HttpException extends RuntimeException {
    public final int a;
    public HttpException(int status, String message) { super(message); a = status; }
}
