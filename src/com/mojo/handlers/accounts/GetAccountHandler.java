package com.mojo.handlers.accounts;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import io.vertx.core.Handler;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

public class GetAccountHandler implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext context) {
        context.response().putHeader("Content-type", "application/json");
        
        String email = context.request().getParam("email");
        
        StringBuilder sql = new StringBuilder();
        
        sql.append("select name, email, roll, phone_no from Accounts");
        
        if(email != null) {
            email = email.trim();
        }
        
        if(email != null && email.length() > 0) {
            sql.append(" where email = \"").append(Utility.encode(email)).append("\"");
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
