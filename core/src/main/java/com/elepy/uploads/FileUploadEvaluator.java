package com.elepy.uploads;

import com.elepy.exceptions.ElepyException;
import com.elepy.http.Request;

public class FileUploadEvaluator {

    private static final long KILOBYTE_SIZE = 1024;
    private static final long MEGABYTE_SIZE = KILOBYTE_SIZE * 1024;
    public static final long DEFAULT_MAX_FILE_SIZE = 10 * MEGABYTE_SIZE;

    private final long maximumFileSize;
    private final String allowedMimeType;

    public FileUploadEvaluator(Long maximumFileSize, String allowedMimeType) {
        this.maximumFileSize = maximumFileSize == null ? DEFAULT_MAX_FILE_SIZE : maximumFileSize;
        this.allowedMimeType = allowedMimeType == null ? "*/*" : allowedMimeType;
    }

    public static FileUploadEvaluator fromRequest(Request request) {
        try {
            final String maximumFileSize = request.queryParams("maximumFileSize");
            return new FileUploadEvaluator(maximumFileSize == null ? null : Long.parseLong(maximumFileSize), request.queryParams("allowedMimeType"));
        } catch (NumberFormatException e) {
            throw new ElepyException("maximumFileSize must be a number");
        }
    }

    public FileReference evaluate(FileUpload file) {
        if (!file.contentTypeMatches(allowedMimeType)) {
            throw new ElepyException(String.format("Content type must match %s, was %s", allowedMimeType, file.getContentType()), 400);
        }

        if (file.getSize() > maximumFileSize) {
            throw new ElepyException(String.format("File size can't exceed %s, was %s",
                    translateToRepresentableString(maximumFileSize),
                    translateToRepresentableString(file.getSize())));
        }
        return FileReference.newFileReference(file);
    }

    private String translateToRepresentableString(long bytes) {
        if (maximumFileSize > MEGABYTE_SIZE) {
            return String.format("%d MB", bytes / MEGABYTE_SIZE);
        }
        if (maximumFileSize > KILOBYTE_SIZE) {
            return String.format("%d KB", bytes / KILOBYTE_SIZE);
        } else {
            return String.format("%d bytes", bytes);
        }
    }
}
