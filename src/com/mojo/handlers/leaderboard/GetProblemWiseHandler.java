package com.mojo.handlers.leaderboard;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

public class GetProblemWiseHandler implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext context) {
        context.response().putHeader("Content-type", "application/json");

        String email = context.request().getParam("email");
        String key = context.request().getParam("key");

        if(email != null && key != null && Utility.encrypt(email).equals(key)) {
            // Authenticated
            final String encodedEmail = Utility.encode(email);

            Database.getClient().getConnection(conn -> {
                if(conn.succeeded()) {
                    SQLConnection connection = conn.result();

                    StringBuilder builder = new StringBuilder();
                    builder.append("select Problems_code, name, count(log_id) as Attempts, (100 / count(log_id)) ");
                    builder.append("as Score from Solve_log join Problems on Solve_log.Problems_code = Problems.code");
                    builder.append(" where Accounts_id = (select id from Accounts where email = \"");
                    builder.append(encodedEmail);
                    builder.append("\") and \"ACC\" in (select status from Solve_log where Accounts_id = ");
                    builder.append("Solve_log.Accounts_id and Problems_code = Solve_log.Problems_code)");
                    builder.append(" group by Problems_code;");

                    connection.query(builder.toString(), result -> {
                        if(result.succeeded()) {
                            JsonObject res = result.result().toJson();
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
    }
}
