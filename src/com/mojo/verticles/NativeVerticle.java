package com.mojo.verticles;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLConnection;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NativeVerticle extends AbstractVerticle {
    private static final ExecutorService pool = Executors.newFixedThreadPool(Utility.getPoolSize());

    public static String mapExtension(String lang) {
        if(lang.equals("java8")) return ".java";
        if(lang.equals("py2") || lang.equals("py3")) return ".py";
        if(lang.equals("c99")) return ".c";
        if(lang.equals("cpp14")) return ".cpp";
        return ".generic";
    }

    public static String mapCoreExtension(String lang) {
        if(lang.equals("py2") || lang.equals("py3")) return lang;
        else if(lang.equals("c99")) return "c";
        else if(lang.equals("cpp14")) return "cpp";
        else if(lang.equals("java8")) return "java";
        else return "generic";
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
                String msg = "ACC";
                for(JsonObject testcase : testcases) {
                    ProcessBuilder processBuilder = new ProcessBuilder("core/obj/judge",
                            "-f", filePath,
                            "-i", testcase.getString("in_path"),
                            "-o", testcase.getString("out_path"),
                            "-l", mapCoreExtension(lang),
                            "-d", dirPath,
                            "-r", (testcase.getInteger("tl") / 1000) + "",
                            "-c", "4"
                    );

                    processBuilder.redirectErrorStream(true);
                    Process p = processBuilder.start();
                    p.waitFor(10000, TimeUnit.MILLISECONDS);

                    BufferedReader inStream = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String temp;
                    while((temp = inStream.readLine()) != null) {
                        if(temp.startsWith("[+] Status : ")) {
                            temp = temp.substring("[+] Status : ".length());
                            if(!temp.equals("ACC")) {
                                msg = temp;
                            }
                        }
                    }
                }

                final String resultMsg = msg;
                Database.getClient().getConnection(conn -> {
                    if(conn.succeeded()) {
                        SQLConnection connection = conn.result();

                        StringBuilder sql = new StringBuilder();
                        sql.append("update Solve_log set status = \"");
                        sql.append(resultMsg);
                        sql.append("\" where log_id = \"");
                        sql.append(id);
                        sql.append("\";");

                        connection.update(sql.toString(), result -> {
                            if(result.failed()) {
                                System.err.println(result.cause().getMessage());
                            }
                        });

                        connection.close();
                    } else {
                        System.err.println("Database error: " + conn.cause().getMessage());
                    }
                });
            } catch(FileNotFoundException e) {
                System.err.println("File was not found");
            } catch(IOException e) {
                System.err.println("IO error");
                e.printStackTrace();
            } catch(InterruptedException e) {
                System.err.println("Internal error");
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
