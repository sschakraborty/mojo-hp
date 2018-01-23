package com.mojo.handlers.leaderboard;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

public class GetRankHandler implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext context) {
        context.response().putHeader("Content-type", "application/json");

        String email = context.request().getParam("email");
        String key = context.request().getParam("key");

        if(email != null && key != null && Utility.encrypt(email).equals(key)) {
            // Authenticated
            Database.getClient().getConnection(conn -> {
                if(conn.succeeded()) {
                    SQLConnection connection = conn.result();

                    StringBuilder builder = new StringBuilder();
                    builder.append("select name, email, roll, sum(Score) as Score from (select Accounts_id, ");
                    builder.append("(100 / count(log_id)) as Score from Solve_log where \"ACC\" in (select ");
                    builder.append("status from Solve_log where Accounts_id = Solve_log.Accounts_id and Problems_code ");
                    builder.append("= Solve_log.Problems_code) group by Problems_code, Accounts_id) as A join ");
                    builder.append("Accounts on Accounts.id = Accounts_id group by Accounts_id order by Score desc;");

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
