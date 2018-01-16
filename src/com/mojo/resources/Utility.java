/*
 *  This is a mutiple utility class that sums up the 
 *  general function definitions that are going to be used thorughout the application
*/

package com.mojo.resources;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class Utility {
    private static final String ERR_MSG = "{ \"msg\":\"error\" }";
    private static final String SCC_MSG = "{ \"msg\":\"success\" }";
    private static final String SECRET_KEY = "FTgf29TgDFg^%$3$%^fcDFG";
    
    public static String encode(String s) {
        return new String(Base64.getEncoder().encode(s.getBytes()));
    }
    
    public static String decode(String s) {
        return new String(Base64.getDecoder().decode(s.getBytes()));
    }
    
    public static String getErrorMsg() {
        return ERR_MSG;
    }
    
    public static String getSuccessMsg() {
        return SCC_MSG;
    }
    
    public static String encrypt(String msg) {
        MessageDigest digest;
        msg = msg + SECRET_KEY;

        try {
            digest = MessageDigest.getInstance("SHA-1");
            return new String(digest.digest(msg.getBytes()));
        } catch(NoSuchAlgorithmException e) {
            System.err.println("SHA-1 Algorithm Not Supported!");
        }

        return null;
    }
}
