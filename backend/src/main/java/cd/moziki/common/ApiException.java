package cd.moziki.common;

import org.springframework.http.HttpStatus;

/** Application error carrying a stable machine code and an i18n message key. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final transient Object[] messageArgs;

    public ApiException(HttpStatus status, String code, String messageKey, Object... messageArgs) {
        super(messageKey);
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
        this.messageArgs = messageArgs;
    }

    public static ApiException badRequest(String code, String messageKey, Object... args) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, messageKey, args);
    }

    public static ApiException unauthorized(String code, String messageKey, Object... args) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, messageKey, args);
    }

    public static ApiException forbidden(String code, String messageKey, Object... args) {
        return new ApiException(HttpStatus.FORBIDDEN, code, messageKey, args);
    }

    public static ApiException notFound(String code, String messageKey, Object... args) {
        return new ApiException(HttpStatus.NOT_FOUND, code, messageKey, args);
    }

    public static ApiException conflict(String code, String messageKey, Object... args) {
        return new ApiException(HttpStatus.CONFLICT, code, messageKey, args);
    }

    public static ApiException unprocessable(String code, String messageKey, Object... args) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, messageKey, args);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getMessageArgs() {
        return messageArgs;
    }
}
