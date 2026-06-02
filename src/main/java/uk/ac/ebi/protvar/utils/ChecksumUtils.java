package uk.ac.ebi.protvar.utils;

import java.math.BigInteger;
import java.security.MessageDigest;

public class ChecksumUtils {
    /**
     * Generate checksum for the given data byte array.
     * @param data
     * @return
     */
    public static String checksum(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(data);
            String checksum = new BigInteger(1, hash).toString(16);
            return checksum;
        } catch (Exception e) {
            return null;
        }
    }
}
