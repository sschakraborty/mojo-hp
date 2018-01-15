package com.mojo.handlers.accounts;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;
import java.util.List;

public class GetAccountHandler implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext context) {
        context.response().putHeader("Content-type", "application/json");
        
        String id = context.request().getParam("id");
        
        StringBuilder sql = new StringBuilder();
        
        sql.append("select name, email, roll, phone_no from Accounts");
        
        if(id != null) {
            id = id.trim();
        }
        
        if(id != null && id.length() > 0) {
            sql.append(" where id = \"").append(id).append("\"");
        }
        
        sql.append(" limit 250;");
        
        Database.getClient().getConnection(conn -> {
            if(conn.succeeded()) {
                SQLConnection connection = conn.result();
                
                connection.query(sql.toString(), result -> {
                    if(result.succeeded()) {
                        ResultSet set = result.result();
                        context.response().end(set.toJson().encodePrettily());
                    } else {
                        context.response().end(Utility.getErrorMsg());
                    }
                });
                
                connection.close();
            } else {
                context.response().end(Utility.getErrorMsg());
            }
        });
    }
}
