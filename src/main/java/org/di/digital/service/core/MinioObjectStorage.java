package org.di.digital.service.core;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface MinioObjectStorage{

    String putObject(String objectName, InputStream stream, long size, String contentType);
    InputStream getObject(String objectName);
    void removeObject(String objectName);
    boolean exists(String objectName);
    List<String> listObjectNames(String prefix);
    void removeObjects(List<String> objectNames);
    String presignedGetUrl(String objectName, Map<String, String> headers);
    String extractObjectNameFromPath(String objectPath);


}