/*
 *  This class holds the database connection informations.
 *  SQLCLient is created once as a static resource here.
*/

package com.mojo.resources;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLClient;

import java.io.*;

public class Database {
    private static SQLClient client;
    private static final JsonObject dbc = new JsonObject();
    private static String configPath;

    static {
        client = null;

        try {
            // Make IO to get the path for a valid config file
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            System.out.print("[Config file path]: ");
            configPath = in.readLine();
            in.close();

            FileInputStream fileInputStream = new FileInputStream(configPath);
            StringBuffer fileContent = new StringBuffer();
            BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream));
            String readContent;
            while((readContent = reader.readLine()) != null) {
                fileContent.append(readContent);
            }
            reader.close();
            fileInputStream.close();

            JsonObject config = new JsonObject(fileContent.toString());

            dbc.put("debug", false);
            dbc.put("url", "jdbc:mysql://" + config.getJsonObject("database").getString("host")
                    + ":" + config.getJsonObject("database").getInteger("port") + "/"
                    + config.getJsonObject("database").getString("db_name"));
            dbc.put("driver_class", "com.mysql.jdbc.Driver");
            dbc.put("user", config.getJsonObject("database").getString("username"));
            dbc.put("password", config.getJsonObject("database").getString("password"));
        } catch(FileNotFoundException e) {
            System.err.println("Config file not found");
            System.exit(0);
        } catch(IOException e) {
            System.err.println("Config file IO error");
            System.exit(0);
        } catch(Exception e) {
            System.err.println("Malformed config file");
            System.err.println(e.getMessage());
            System.exit(0);
        }
    }
    
    public static void setVertxInstance(Vertx vertx) {
        client = JDBCClient.createShared(vertx, dbc);
    }
    
    public static SQLClient getClient() {
        return client;
    }
}
