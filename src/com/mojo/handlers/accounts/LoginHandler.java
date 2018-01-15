package com.mojo.handlers.accounts;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

public class LoginHandler implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext context) {
        context.response().putHeader("Content-type", "application/json");
        
        try {
            JsonObject body = context.getBodyAsJson();
            
            String email = body.getString("email");
            String pwd = body.getString("pwd");
            
            if(email != null && pwd != null) {
                if(email.trim().length() > 0 && pwd.trim().length() > 0) {
                    email = Utility.encode(email);
                    pwd = Utility.encode(pwd);
                    
                    StringBuilder sql = new StringBuilder("");
                    sql.append("select name from Accounts where ");
                    sql.append("email = \"").append(email).append("\" and ");
                    sql.append("pwd = \"").append(pwd).append("\" limit 2;");
                    
                    Database.getClient().getConnection(conn -> {
                        if(conn.succeeded()) {
                            SQLConnection connection = conn.result();

                            connection.query(sql.toString(), result -> {

                            });
                        }
                    });
                } else {
                    context.response().end(Utility.getErrorMsg());
                }
            } else {
                context.response().end(Utility.getErrorMsg());
            }
        } catch(Exception e) {
            // Do something on error condition
        }
    }
}
