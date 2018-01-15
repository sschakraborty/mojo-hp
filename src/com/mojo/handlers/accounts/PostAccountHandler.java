package com.mojo.handlers.accounts;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.DecodeException;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

public class PostAccountHandler implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext context) {
        try {
            context.response().putHeader("Content-type", "application/json");
            JsonObject body = context.getBodyAsJson();
            
            String name = body.getString("name");
            String email = body.getString("email");
            String roll = body.getString("roll");
            String phone_no = body.getString("phone_no");
            String pwd = body.getString("pwd");
            
            if(name != null && email != null && roll != null && phone_no != null && pwd != null) {
                if(name.trim().length() > 0 && email.trim().length() > 0 && roll.trim().length() > 0 &&
                        phone_no.trim().length() > 0 && pwd.trim().length() > 0) {
                    name = Utility.encode(name);
                    email = Utility.encode(email);
                    roll = Utility.encode(roll);
                    phone_no = Utility.encode(phone_no);
                    pwd = Utility.encode(pwd);

                    StringBuilder sql = new StringBuilder();

                    sql.append("insert into Accounts (name, email, roll, phone_no, pwd) values (");
                    sql.append("\"").append(name).append("\", ");
                    sql.append("\"").append(email).append("\", ");
                    sql.append("\"").append(roll).append("\", ");
                    sql.append("\"").append(phone_no).append("\", ");
                    sql.append("\"").append(pwd).append("\");");

                    Database.getClient().getConnection(connection -> {
                        if(connection.succeeded()) {
                            SQLConnection conn = connection.result();

                            conn.query(sql.toString(), result -> {
                                if(result.succeeded()) {
                                    context.response().end(Utility.getSuccessMsg());
                                } else {
                                    context.response().end(Utility.getErrorMsg());
                                }
                            });

                            conn.close();
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
        } catch(DecodeException e) {
            // Do nothing there's a wrongly formed request
        } catch(Exception e) {
            System.err.println("[Error in POST /api/Accounts]: Handler Error");
        }
    }
}
