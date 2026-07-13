package com.univault.common.util;

import java.net.URLConnection;

public class MimeTypeUtil {

    /**
     * Derives mimeType from the actual uploaded file's name, using the
     * JDK's built-in content-type map. No hardcoded type list — works
     * for any registered extension. Falls back to a generic binary type
     * for unrecognized extensions.
     */
    public static String resolve(String actualFileName) {
        if (actualFileName == null) {
            return "application/octet-stream";
        }
        String guessed = URLConnection.guessContentTypeFromName(actualFileName);
        return guessed != null ? guessed : "application/octet-stream";
    }
}