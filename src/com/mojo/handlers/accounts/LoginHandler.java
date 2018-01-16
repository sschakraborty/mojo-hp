package com.mojo.handlers.accounts;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
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
            final String keyToEncrypt = email;
            
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
                                if(result.succeeded()) {
                                    ResultSet resultSet = result.result();
                                    JsonObject object = new JsonObject();

                                    if(resultSet.getNumRows() == 1) {
                                        object.put("msg", "success");
                                        object.put("key", Utility.encrypt(keyToEncrypt));
                                    } else {
                                        object.put("msg", "failure");
                                    }

                                    context.response().end(object.encodePrettily());
                                } else {
                                    context.response().end(Utility.getErrorMsg());
                                }
                            });

                            connection.close();
                        } else {
                            context.response().end(Utility.getErrorMsg());
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
