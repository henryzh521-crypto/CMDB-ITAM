package com.dcim.common.file;

import java.io.InputStream;

public interface FileStorage {
    void store(String key, InputStream in, long size, String contentType) throws Exception;

    InputStream open(String key) throws Exception;

    void delete(String key) throws Exception;

    boolean exists(String key);
}
