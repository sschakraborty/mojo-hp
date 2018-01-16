package com.mojo.handlers.problems;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

public class GetProblemsHandler implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext context) {
        context.response().putHeader("Content-type", "application/json");

        String email = context.request().getParam("email");
        String key = context.request().getParam("key");

        if(email != null && key != null && Utility.encrypt(email).equals(key)) {
            StringBuilder sql = new StringBuilder();
            sql.append("select * from Problems limit 100;");

            Database.getClient().getConnection(conn -> {
                if (conn.succeeded()) {
                    SQLConnection connection = conn.result();

                    connection.query(sql.toString(), result -> {
                        if (result.succeeded()) {
                            ResultSet rs = result.result();
                            JsonObject re = rs.toJson();
                            re.put("msg", "success");

                            context.response().end(re.encodePrettily());
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
    }
}
