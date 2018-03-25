package com.mojo.handlers.comments;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

public class PostCommentsHandler implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext ctx) {
        ctx.response().putHeader("Content-type", "application/json");

        try {
            // Get all parameters
            JsonObject body = ctx.getBodyAsJson();

            String problemsCode = body.getString("problemsCode");
            int accountsId = body.getInteger("accountsId");
            String comment = body.getString("comment");

            String email = body.getString("email");
            String key = body.getString("key");

            if(problemsCode != null && comment != null && email != null && key != null &&
                    Utility.encrypt(email).equals(key)) {
                problemsCode = Utility.encode(problemsCode);
                comment = Utility.encode(comment);

                // Create a SQL insert statement

                StringBuilder query = new StringBuilder();
                query.append("insert into Comments (Problems_code, Accounts_id, comment) values (");
                query.append("\"").append(problemsCode).append("\", ");
                query.append(accountsId).append(", ");
                query.append("\"").append(comment).append("\");");

                Database.getClient().getConnection(conn -> {
                    if(conn.succeeded()) {
                        SQLConnection connection = conn.result();
                        connection.query(query.toString(), res -> {
                            if(res.succeeded()) {
                                ctx.response().end(Utility.getSuccessMsg());
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
        } catch(DecodeException e) {
            // Some error occurred
            ctx.response().end(Utility.getErrorMsg());
        } catch(Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            ctx.response().end(Utility.getErrorMsg());
        }
    }
}