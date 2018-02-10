package com.mojo.handlers.accounts;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

public class PutAccountHandler implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext context) {
        context.response().putHeader("Content-type", "application/json");

        JsonObject body = context.getBodyAsJson();
        String email = body.getString("email");
        String name = body.getString("name");
        String roll = body.getString("roll");
        String phone_no = body.getString("phone_no");
        String key = body.getString("key");

        if(email != null && name != null && roll != null && phone_no != null && key != null) {
            name = Utility.encode(name);
            roll = Utility.encode(roll);
            phone_no = Utility.encode(phone_no);

            final StringBuilder query = new StringBuilder();
            query.append("update Accounts set name = \"").append(name).append("\", ");
            query.append("roll = \"").append(roll).append("\", ");
            query.append("phone_no = \"").append(phone_no).append("\";");

            if(Utility.encrypt(email).equals(key)) {
                Database.getClient().getConnection(conn -> {
                    if(conn.succeeded()) {
                        SQLConnection connection = conn.result();

                        connection.update(query.toString(), result -> {
                            if(result.succeeded()) {
                                context.response().end(Utility.getSuccessMsg());
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
    }
}
