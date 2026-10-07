package com.veritasvault.util;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/*
  Utility for computing cryptographic hashes from input streams.
  Used to establish forensic integrity and verify that evidence files
  have not been tampered with since intake.
 */
public final class ChecksumUtils {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int BUFFER_SIZE = 8192; // 8 KB chunks to keep memory usage low

    private ChecksumUtils() {
        // Prevent instantiation
    }

    /*
      Computes the SHA-256 hex digest for an input stream using streaming byte chunks.

     @param inputStream the data stream to hash
     @return 64-character lowercase hexadecimal hash string
      @throws IOException if an error occurs while reading the stream
     */
    public static String calculateSha256(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algorithm " + HASH_ALGORITHM + " is not available on this JVM", e);
        }
    }

    /*
      converts a raw byte array into a lowercase hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}