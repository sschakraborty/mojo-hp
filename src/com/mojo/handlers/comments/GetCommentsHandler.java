package com.mojo.handlers.comments;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

// The query goes like this
// select comment, c_time, name, email from Comments inner join Accounts on
// Comments.Accounts_id = Accounts.id where Comments.Problems_code = "SomeCode"
// order by c_time desc limit 100

public class GetCommentsHandler implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext ctx) {
        ctx.response().putHeader("Content-type", "application/json");

        try {
            String problemsCode = ctx.request().getParam("problemsCode");
            String email = ctx.request().getParam("email");
            String key = ctx.request().getParam("key");

            if(problemsCode != null && email != null && key != null &&
                    Utility.encrypt(email).equals(key)) {
                problemsCode = Utility.encode(problemsCode);

                StringBuilder query = new StringBuilder();
                query.append("select comment, c_time, name, email from Comments inner join Accounts");
                query.append(" on Comments.Accounts_id = Accounts.id where Comments.Problems_code = ");
                query.append("\"").append(problemsCode).append("\" order by c_time desc limit 20");

                Database.getClient().getConnection(conn -> {
                    if(conn.succeeded()) {
                        SQLConnection connection = conn.result();

                        connection.query(query.toString(), res -> {
                            if(res.succeeded()) {
                                JsonObject obj = res.result().toJson();
                                ctx.response().end(obj.encodePrettily());
                            } else {
                                ctx.response().end(Utility.getErrorMsg());
                            }
                        });

                        connection.close();
                    } else {
                        ctx.response().end(Utility.getErrorMsg());
                    }
                });
            } else {
                ctx.response().end(Utility.getErrorMsg());
            }
        } catch(Exception e) {
            ctx.response().end(Utility.getErrorMsg());
        }
    }
}
