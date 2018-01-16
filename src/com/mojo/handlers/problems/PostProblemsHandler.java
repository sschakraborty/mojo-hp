package com.mojo.handlers.problems;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

public class PostProblemsHandler implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext context) {
        context.response().putHeader("Content-type", "application/json");

        try {
            JsonObject body = context.getBodyAsJson();

            String email = body.getString("email");
            String key = body.getString("key");

            if(email != null && key != null && Utility.encrypt(email).equals(key)) {
                String code = body.getString("code");
                String name = body.getString("name");
                String question = body.getString("question");
                String tags = body.getString("tags");

                if(code != null && name != null && question != null && tags != null) {
                    code = Utility.encode(code);
                    name = Utility.encode(name);
                    question = Utility.encode(question);
                    tags = Utility.encode(tags);

                    StringBuilder sql = new StringBuilder();
                    sql.append("insert into Problems (code, name, question, tags) values ");
                    sql.append("(\"").append(code).append("\", ");
                    sql.append("\"").append(name).append("\", ");
                    sql.append("\"").append(question).append("\", ");
                    sql.append("\"").append(tags).append("\");");

                    Database.getClient().getConnection(conn -> {
                        if(conn.succeeded()) {
                            SQLConnection connection = conn.result();

                            connection.query(sql.toString(), result -> {
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
        } catch(DecodeException e) {
            context.response().end(Utility.getErrorMsg());
        } catch(Exception e) {
            System.out.println("[Fatal Error]: " + e.getMessage());
        }
    }
}
