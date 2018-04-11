package com.mojo.verticles;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLConnection;

import java.io.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NativeVerticle extends AbstractVerticle {
    private ExecutorService pool = Executors.newFixedThreadPool(4);

    public static String mapExtension(String lang) {
        if(lang.equals("java8")) return ".java";
        if(lang.equals("py2") || lang.equals("py3")) return ".py";
        if(lang.equals("c99")) return ".c";
        if(lang.equals("cpp14")) return ".cpp";
        return ".generic";
    }

    public static String mapCoreExtension(String lang) {
        if(lang.equals("py2") || lang.equals("py3")) return lang;
        else return mapExtension(lang);
    }

    private class Job implements Runnable {
        private String id;
        private String problemCode;
        private String code;
        private String lang;
        private List<JsonObject> testcases;

        public Job(String id, String problemCode, String code, String lang, List<JsonObject> testcases) {
            this.id = id;
            this.problemCode = problemCode;
            this.code = Utility.decode(code);
            this.lang = Utility.decode(lang);
            this.testcases = testcases;
        }

        @Override
        public void run() {
            // Determine directories to store code in
            String dirPath = Utility.getJudgeFolderPath() + "/" + id;
            String fileName = id + mapExtension(lang);
            String filePath = dirPath + "/" + fileName;

            try {
                File dir = new File(dirPath);
                dir.mkdirs();
                FileOutputStream fout = new FileOutputStream(filePath);
                PrintWriter out = new PrintWriter(fout);
                out.write(code);
                out.close();
                fout.close();

                // Calling native core from Process API
                ProcessBuilder processBuilder = new ProcessBuilder("./core/obj/judge",
                        "-d", dirPath,
                        "-f", filePath,
                        "-l", mapCoreExtension(lang));
            } catch(FileNotFoundException e) {
                // System.err.println("File was not found");
            } catch(IOException e) {
                // System.err.println("IO error");
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
                            List<JsonObject> testcases = result.result().getRows();
                            pool.submit(new NativeVerticle.Job(id, problemCode, Utility.decode(code), lang, testcases));
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