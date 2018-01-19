/*
 *  This is a mutiple utility class that sums up the 
 *  general function definitions that are going to be used thorughout the application
*/

package com.mojo.resources;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Random;

public class Utility {
    private static final String ERR_MSG = "{ \"msg\":\"error\" }";
    private static final String SCC_MSG = "{ \"msg\":\"success\" }";
    private static final String SECRET_KEY = "FTgf29TgDFg^%$3$%^fcDFG";
    private static final Random random = new Random();

    private static final String JUDGE_FOLDER_PATH = "/home/sschakraborty/Documents/mojo-hp/Test";
    
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
        msg += SECRET_KEY;

        try {
            MessageDigest mDigest = MessageDigest.getInstance("SHA1");
            byte[] result = mDigest.digest(msg.getBytes());
            StringBuffer sb = new StringBuffer();
            for(int i = 0; i < result.length; i++) {
                sb.append(Integer.toString((result[i] & 0xff) + 0x100, 16).substring(1));
            }
            return sb.toString();
        } catch(NoSuchAlgorithmException e) {
            System.err.print("Encryption Algorithm Not Supported");
        }

        return null;
    }

    public static long getRandomLong() {
        return random.nextLong();
    }
    public static String getJudgeFolderPath() { return JUDGE_FOLDER_PATH; }
}
