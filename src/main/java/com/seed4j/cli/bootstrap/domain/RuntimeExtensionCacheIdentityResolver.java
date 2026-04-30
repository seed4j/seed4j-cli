package com.seed4j.cli.bootstrap.domain;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class RuntimeExtensionCacheIdentityResolver {

  private static final String CACHE_LAYOUT_VERSION = "overlay-v1";

  RuntimeExtensionCacheIdentity resolve(Path extensionJarPath) {
    return new RuntimeExtensionCacheIdentity(CACHE_LAYOUT_VERSION + "-" + sha256(extensionJarPath));
  }

  private String sha256(Path extensionJarPath) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
      try (InputStream extensionJarInputStream = Files.newInputStream(extensionJarPath)) {
        byte[] chunk = new byte[8192];
        int readBytes;
        while ((readBytes = extensionJarInputStream.read(chunk)) != -1) {
          messageDigest.update(chunk, 0, readBytes);
        }
      }
      return toHex(messageDigest.digest());
    } catch (IOException ioException) {
      throw new InvalidRuntimeConfigurationException(
        "Could not calculate runtime extension cache identity for " + extensionJarPath + ": " + ioException.getMessage()
      );
    } catch (NoSuchAlgorithmException noSuchAlgorithmException) {
      throw new IllegalStateException("Missing SHA-256 algorithm support in current runtime.", noSuchAlgorithmException);
    }
  }

  private static String toHex(byte[] digest) {
    StringBuilder hex = new StringBuilder(digest.length * 2);
    for (byte digestByte : digest) {
      hex.append(String.format("%02x", digestByte));
    }
    return hex.toString();
  }
}
