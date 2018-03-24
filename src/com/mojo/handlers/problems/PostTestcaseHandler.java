package com.mojo.handlers.problems;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;

import java.util.Iterator;

public class PostTestcaseHandler implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext context) {
        context.response().putHeader("Content-type", "application/json");

        JsonObject body = context.getBodyAsJson();

        String email = body.getString("email");
        String key = body.getString("key");

        if(email != null && key != null && Utility.encrypt(email).equals(key)) {
            JsonArray array = body.getJsonArray("TC");
            StringBuilder query = new StringBuilder();
            query.append("insert into Testcases (Problems_code, in_path, out_path, tl) values ");

            Iterator iterator = array.iterator();
            while (iterator.hasNext()) {
                JsonObject obj = (JsonObject) iterator.next();
                String problemCode = obj.getString("problem_code");
                String in_path = obj.getString("in_path");
                String out_path = obj.getString("out_path");
                String tl = obj.getString("tl");
                if (problemCode != null && in_path != null && out_path != null && tl != null) {
                    problemCode = Utility.encode(problemCode);

                    query.append("(\"").append(problemCode).append("\", ");
                    query.append("\"").append(in_path).append("\", ");
                    query.append("\"").append(out_path).append("\", ");
                    query.append(tl).append("),");
                }
            }
            ;

            query.setCharAt(query.length() - 1, ';');

            Database.getClient().getConnection(conn -> {
                if (conn.succeeded()) {
                    SQLConnection connection = conn.result();

                    connection.query(query.toString(), res -> {
                        if (res.succeeded()) {
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
    }
}
