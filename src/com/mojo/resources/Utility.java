/*
 *  This is a mutiple utility class that sums up the 
 *  general function definitions that are going to be used thorughout the application
*/

package com.mojo.resources;

import io.vertx.core.json.JsonObject;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Random;

public class Utility {
    private static final String ERR_MSG = "{ \"msg\":\"error\" }";
    private static final String SCC_MSG = "{ \"msg\":\"success\" }";
    private static String SECRET_KEY = "";
    private static final Random random = new Random();

    private static String JUDGE_FOLDER_PATH = "";

    static {
        try {
            FileInputStream fileInputStream = new FileInputStream("./config.json");
            StringBuffer fileContent = new StringBuffer();
            BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream));
            String readContent;
            while((readContent = reader.readLine()) != null) {
                fileContent.append(readContent);
            }
            reader.close();
            fileInputStream.close();

            JsonObject config = new JsonObject(fileContent.toString());
            SECRET_KEY = config.getJsonObject("security").getString("key");
            JUDGE_FOLDER_PATH = config.getJsonObject("judge").getString("test_folder");
        } catch(FileNotFoundException e) {
            System.err.println("Config file not found");
            System.exit(0);
        } catch(IOException e) {
            System.err.println("Config file IO error");
            System.exit(0);
        } catch(Exception e) {
            System.err.println("Malformed config file");
            System.exit(0);
        }
    }
    
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
        return Math.abs(random.nextLong());
    }
    public static String getJudgeFolderPath() { return JUDGE_FOLDER_PATH; }
}
