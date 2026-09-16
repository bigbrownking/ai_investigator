package org.di.digital.service.core;

import org.apache.tika.exception.TikaException;
import org.di.digital.model.file.FileAccessToken;

public interface FileTokenService {
    String generateToken(FileAccessToken token);
    FileAccessToken resolveToken(String tokenId);
}
