package com.tcc.servicedesk.storage;

public interface StorageService {
    void upload(String key, byte[] content, String contentType);

    byte[] download(String key);

    void delete(String key);
}
