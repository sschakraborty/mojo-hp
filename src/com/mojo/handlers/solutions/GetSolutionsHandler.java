package com.mojo.handlers.solutions;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

public class GetSolutionsHandler implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext context) {
        context.response().putHeader("Content-type", "application/json");

        String email = context.request().getParam("email");
        String key = context.request().getParam("key");

        if(email != null && key != null) {
            // Verify login in constant time
            if(Utility.encrypt(email).equals(key)) {
                email = Utility.encode(email);

                StringBuilder sql = new StringBuilder();
                sql.append("select name, roll, log_t, Problems_code, status ");
                sql.append("from Solve_log join Accounts on Accounts_id = id where email = \"");
                sql.append(email).append("\" order by log_t desc limit 100;");

                Database.getClient().getConnection(conn -> {
                    if (conn.succeeded()) {
                        SQLConnection connection = conn.result();

                        connection.query(sql.toString(), result -> {
                            if (result.succeeded()) {
                                ResultSet resultSet = result.result();
                                JsonObject res = resultSet.toJson();
                                res.put("msg", "success");
                                context.response().end(res.encodePrettily());
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
