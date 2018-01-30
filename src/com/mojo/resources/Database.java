/*
 *  This class holds the database connection informations.
 *  SQLCLient is created once as a static resource here.
*/

package com.mojo.resources;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLClient;

public class Database {
    private static SQLClient client;
    
    static {
        client = null;
    }
    
    public static void setVertxInstance(Vertx vertx) {
        JsonObject config = new JsonObject();

        config.put("debug", false);
        config.put("url", "jdbc:mysql://localhost:3306/mojo_hp_database");
        config.put("driver_class", "com.mysql.jdbc.Driver");
        config.put("user", "root");
        config.put("password", "2010ottawa");
        
        client = JDBCClient.createShared(vertx, config);
    }
    
    public static SQLClient getClient() {
        return client;
    }
}
