/*
 * Copyright 2012 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Helper class that will find the md5 hash for files.
 */
public class Md5Hasher {

  private static final int BYTE_BUFFER_SIZE = 1024;

  private static MessageDigest getMd5Digest() {
    MessageDigest digest = null;
    try {
      digest = MessageDigest.getInstance("MD5");
    } catch (final NoSuchAlgorithmException e) {
      // Should never get here.
    }

    return digest;
  }

  public static byte[] md5Hash(final File file) throws IOException {
    final MessageDigest digest = getMd5Digest();

    final FileInputStream fStream = new FileInputStream(file);
    final BufferedInputStream bStream = new BufferedInputStream(fStream);
    final DigestInputStream blobStream = new DigestInputStream(bStream, digest);

    final byte[] buffer = new byte[BYTE_BUFFER_SIZE];

    int num = 0;
    do {
      num = blobStream.read(buffer);
    } while (num > 0);

    blobStream.close();
    bStream.close();
    fStream.close();

    return digest.digest();
  }

}
