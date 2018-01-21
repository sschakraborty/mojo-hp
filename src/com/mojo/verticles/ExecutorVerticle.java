package com.mojo.verticles;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLConnection;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExecutorVerticle extends AbstractVerticle {
    private final ExecutorService pool;

    public ExecutorVerticle() {
        pool = Executors.newFixedThreadPool(4);
    }

    public static String mapExtension(String lang) {
        if(lang.equals("java8")) return ".java";
        if(lang.equals("py2") || lang.equals("py3")) return ".py";
        if(lang.equals("c99")) return ".c";
        if(lang.equals("cpp14")) return ".cpp";
        return ".generic";
    }

    private static final class Job implements Runnable {
        private final String id, problemCode, code, lang;
        private final JsonObject testcases;

        public Job(String id, String problemCode, String code, String lang, JsonObject testcases) {
            this.code = Utility.decode(code);
            this.problemCode = problemCode;
            this.lang = Utility.decode(lang);
            this.id = id;
            this.testcases = testcases;
        }

        @Override
        public void run() {
            // Write to a file in Judge Test Folder
            String dirPath = Utility.getJudgeFolderPath() + "/" + id;
            String filePath = dirPath + "/" + id + mapExtension(lang);

            try {
                File file = new File(dirPath);
                file.mkdirs();
                FileOutputStream fout = new FileOutputStream(filePath);
                PrintWriter writer = new PrintWriter(fout);
                writer.print(code);
                writer.close();
                fout.close();
            } catch(IOException e) {
                System.err.println(e.getLocalizedMessage());
            }
        }
    }

    @Override
    public void start() {
        vertx.eventBus().consumer("code-pipeline", message -> {
            JsonObject body = (JsonObject) message.body();
            String id = body.getString("id");
            String code = body.getString("code");
            String lang = body.getString("lang");
            String problemCode = body.getString("problemCode");

            // Fetch testcases as JsonObject and submit Job to pool
            // Along with the JsonObject testcase

            Database.getClient().getConnection(conn -> {
                if(conn.succeeded()) {
                    SQLConnection connection = conn.result();

                    StringBuilder sql = new StringBuilder("select * from Testcases where Problems_code");
                    sql.append(" = \"").append(problemCode).append("\" limit 250;");

                    connection.query(sql.toString(), result -> {
                        if(result.succeeded()) {
                            JsonObject testcases = result.result().toJson();
                            pool.submit(new Job(id, problemCode, code, lang, testcases));
                        } else {
                            System.err.println("Query failure while fetching testcases: "
                                    + conn.cause().getMessage());
                        }
                    });

                    connection.close();
                } else {
                    System.err.println("Database failure while judging: " + conn.cause().getMessage());
                }
            });
        });
    }
}
