package com.mojo.handlers.solutions;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

import java.util.Random;

public class PostSolutionsHandler implements Handler<RoutingContext> {
    private final EventBus eventBus;

    public PostSolutionsHandler(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void handle(RoutingContext context) {
        context.response().putHeader("Content-type", "application/json");

        JsonObject body = context.getBodyAsJson();
        String email = body.getString("email");
        String key = body.getString("key");

        if(email != null && key != null) {
            // Verify Authentication
            if(Utility.encrypt(email).equals(key)) {
                email = Utility.encode(email);
                String Problems_code = body.getString("Problems_code");
                String code = body.getString("code");
                String language = body.getString("language");

                if (Problems_code != null && code != null && language != null) {
                    Problems_code = Utility.encode(Problems_code);
                    code = Utility.encode(code);
                    language = Utility.encode(language);

                    // Generate a long unique id per submission
                    long random = Utility.getRandomLong();

                    StringBuilder sql = new StringBuilder();
                    sql.append("insert into Solve_log (log_id, Accounts_id, Problems_code, code, language, status) ");
                    sql.append("select \"").append(random).append("\", ");
                    sql.append("Accounts.id, \"").append(Problems_code).append("\", ");
                    sql.append("\"").append(code).append("\", ");
                    sql.append("\"").append(language).append("\", ");
                    sql.append("\"").append("EVAL").append("\" from Accounts where email = ");
                    sql.append("\"").append(email).append("\" limit 1;");

                    JsonObject submission = new JsonObject();
                    submission.put("id", random + "");
                    submission.put("problemCode", Problems_code);
                    submission.put("code", code);
                    submission.put("lang", language);

                    Database.getClient().getConnection(conn -> {
                        if (conn.succeeded()) {
                            SQLConnection connection = conn.result();

                            connection.query(sql.toString(), result -> {
                                if (result.succeeded()) {
                                    eventBus.send("code-pipeline", submission);
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
        } else {
            context.response().end(Utility.getErrorMsg());
        }
    }
}
