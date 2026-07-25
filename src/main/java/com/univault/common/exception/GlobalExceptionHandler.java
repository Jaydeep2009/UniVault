package com.univault.common.exception;

import com.univault.download.exception.ChunkRetrievalException;
import com.univault.download.exception.FileNotReadyException;
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
 * Keeps services and controllers free of try/catch-and-build-ResponseEntity boilerplate.
 *
 * Exception Mapping:
 *   UPLOAD:
 *   - InsufficientStorageException -> 507 Insufficient Storage
 *   - IllegalArgumentException     -> 400 Bad Request
 *   - IllegalStateException        -> 409 Conflict
 *   - ChunkUploadFailedException   -> 502 Bad Gateway (retries exhausted)
 *
 *   DOWNLOAD:
 *   - FileNotReadyException        -> 409 Conflict (file not in READY status)
 *   - ChunkRetrievalException      -> 500 Internal Server Error (chunk download failed)
 *
 *   GENERAL:
 *   - ResourceNotFoundException    -> 404 Not Found
 *   - Exception (catch-all)        -> 500 Internal Server Error
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ============ UPLOAD EXCEPTIONS ============

    @ExceptionHandler(InsufficientStorageException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStorage(
            InsufficientStorageException ex, HttpServletRequest request) {
        log.warn("Insufficient storage: {}", ex.getMessage());
        return build(HttpStatus.INSUFFICIENT_STORAGE, ex, request);
    }

    @ExceptionHandler(ChunkUploadFailedException.class)
    public ResponseEntity<ErrorResponse> handleChunkUploadFailed(
            ChunkUploadFailedException ex, HttpServletRequest request) {
        log.error("Chunk upload failed after {} attempt(s): {}",
                ex.getAttemptsMade(), ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, ex, request);
    }

    // ============ DOWNLOAD EXCEPTIONS ============

    @ExceptionHandler(FileNotReadyException.class)
    public ResponseEntity<ErrorResponse> handleFileNotReady(
            FileNotReadyException ex, HttpServletRequest request) {
        log.warn("File not ready for download - File: {}, Status: {}",
                ex.getFileId(), ex.getCurrentStatus());
        return build(HttpStatus.CONFLICT, ex, request);
    }

    @ExceptionHandler(ChunkRetrievalException.class)
    public ResponseEntity<ErrorResponse> handleChunkRetrieval(
            ChunkRetrievalException ex, HttpServletRequest request) {
        log.error("Chunk retrieval failed after {} attempts for chunk: {}",
                ex.getAttemptsMade(), ex.getProviderFileId(), ex);
        ErrorResponse body = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Failed to retrieve file chunks. Please try again later.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    // ============ COMMON EXCEPTIONS ============

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Bad request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex, request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex, HttpServletRequest request) {
        log.warn("Conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, ex, request);
    }

    // ============ CATCH-ALL ============

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

    // ============ HELPER METHOD ============

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, Exception ex, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                status.value(), status.getReasonPhrase(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
