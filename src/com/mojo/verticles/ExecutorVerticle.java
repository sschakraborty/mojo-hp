package com.mojo.verticles;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

        public Job(String id, String problemCode, String code, String lang) {
            this.code = Utility.decode(code);
            this.problemCode = problemCode;
            this.lang = Utility.decode(lang);
            this.id = id;
        }

        @Override
        public void run() {
            // Write to a file in Judge Test Folder
            String dirPath = Utility.getJudgeFolderPath() + "/" + id;
            String testPath = dirPath + "/" + id + mapExtension(lang);

            try {
                File file = new File(dirPath);
                file.mkdirs();
                FileOutputStream fout = new FileOutputStream(testPath);
                PrintWriter writer = new PrintWriter(fout);
                writer.print(code);
                writer.close();
                fout.close();

                // Fetch all the testcases
                Database.getClient().getConnection(conn -> {
                    if(conn.succeeded()) {
                        SQLConnection connection = conn.result();

                        StringBuilder b = new StringBuilder("select * from Testcases where Problems_code");
                        b.append(" = \"").append(problemCode).append("\" limit 250;");

                        connection.query(b.toString(), result -> {
                            if(result.succeeded()) {
                                ResultSet resultSet = result.result();

                                try {

                                    // Beginning of Python 2 judge
                                    if (lang.equals("py2")) {
                                        for(JsonObject test : resultSet.getRows()) {
                                            ProcessBuilder builder = new ProcessBuilder("python", testPath, "<", test.getString("in_path"), ">", dirPath + "/OUTPUT.txt");
                                            Process p = builder.start();
                                            p.waitFor(test.getInteger("tl"), TimeUnit.MILLISECONDS);

                                            if(p.isAlive()) {
                                                p.destroyForcibly();

                                                StringBuilder sql = new StringBuilder();
                                                sql.append("update Solve_log set status = \"TLE\" where log_id = \"").append(id);
                                                sql.append("\";");

                                                connection.update(sql.toString(), result2 -> {
                                                    if(result2.failed()) {
                                                        System.err.println("Error in SQL Query 1");
                                                    }
                                                });
                                                connection.close();
                                            } else {
                                                builder = new ProcessBuilder("diff", dirPath + "/OUTPUT.txt", test.getString("out_path"));
                                                builder.redirectErrorStream(true);
                                                p = builder.start();
                                                p.waitFor();
                                                if(p.getInputStream().available() == 0) {
                                                    StringBuilder sql = new StringBuilder();
                                                    sql.append("update Solve_log set status = \"ACC\" where log_id = \"").append(id);
                                                    sql.append("\";");

                                                    connection.update(sql.toString(), result2 -> {
                                                        if(result2.failed()) {
                                                            System.err.println("Error in SQL Query 2");
                                                            System.err.println(result2.cause().getMessage());
                                                        }
                                                    });
                                                    connection.close();
                                                } else {
                                                    StringBuilder sql = new StringBuilder();
                                                    sql.append("update Solve_log set status = \"WA\" where log_id = \"").append(id);
                                                    sql.append("\";");

                                                    connection.update(sql.toString(), result2 -> {
                                                        if(result2.failed()) {
                                                            System.err.println("Error in SQL Query 3");
                                                        }
                                                    });
                                                    connection.close();
                                                }
                                            }
                                        }
                                    }
                                    // End of Python 2 judge

                                } catch(Exception e) {
                                    System.err.println("[Error]: " + e.getMessage());
                                }
                            } else {
                                // Error
                                System.err.println("Database Error!");
                            }
                        });
                    } else {
                        // Error
                        System.err.println("Database Error!");
                    }
                });
            } catch (java.io.IOException e) {
                System.out.println("Error: " + e.getMessage());
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

            pool.submit(new Job(id, problemCode, code, lang));
        });
    }
}
