package com.univault.common.exception;



import com.univault.upload.exception.ChunkUploadFailedException;
import com.univault.upload.exception.InsufficientStorageException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Single point of translation from thrown exceptions to HTTP responses.
 * Keeps UploadSessionService (and every other controller) free of
 * try/catch-and-build-ResponseEntity boilerplate.
 *
 * Mapping (per project doc §7):
 *   InsufficientStorageException -> 507 Insufficient Storage
 *   IllegalArgumentException     -> 400 Bad Request
 *   IllegalStateException        -> 409 Conflict
 *   ChunkUploadFailedException   -> 502 Bad Gateway (retries exhausted talking
 *                                    to the underlying storage provider)
 *   anything else                -> 500 Internal Server Error (generic message,
 *                                    no stack trace leaked to the client)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Thrown at /init when declared file size won't fit in the user's pooled
    // available space (StoragePoolManager check).
    @ExceptionHandler(InsufficientStorageException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStorage(
            InsufficientStorageException ex, HttpServletRequest request) {
        log.warn("Insufficient storage: {}", ex.getMessage());
        return build(HttpStatus.INSUFFICIENT_STORAGE, ex, request);
    }

    // Bad serialNumber (out of range), unknown fileId, malformed input.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Bad request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex, request);
    }

    // Duplicate upload of an already-COMPLETE chunk, declared-size ceiling
    // breach, or any other "valid request, wrong state" case.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {
        log.warn("Conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex, request);
    }

    // Retries exhausted while pushing a chunk to the underlying storage
    // provider (see ChunkUploadRetryHandler). Not the client's fault and not
    // really "our" server's fault either — 502 signals an upstream failure.
    // NOTE: adjust getAttemptsMade() below if the accessor is named
    // differently on your branch.
    @ExceptionHandler(ChunkUploadFailedException.class)
    public ResponseEntity<ErrorResponse> handleChunkUploadFailed(
            ChunkUploadFailedException ex, HttpServletRequest request) {
        log.error("Chunk upload failed after {} attempt(s): {}",
                ex.getAttemptsMade(), ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, ex, request);
    }

    // Catch-all so an unexpected exception never surfaces a stack trace or
    // internal detail to the client — logged in full server-side instead.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Something went wrong. Please try again.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, Exception ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                status.value(), status.getReasonPhrase(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}