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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ExecutorVerticle extends AbstractVerticle {
    private final ExecutorService pool;

    public ExecutorVerticle() {
        pool = Executors.newFixedThreadPool(Utility.getPoolSize());
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
        private final List<JsonObject> testcases;

        public Job(String id, String problemCode, String code, String lang, List<JsonObject> testcases) {
            this.code = Utility.decode(code);
            this.problemCode = problemCode;
            this.lang = Utility.decode(lang);
            this.id = id;
            this.testcases = testcases;
        }

        private String python2Judge(File dirPath, String fileName) {
            try {
                ProcessBuilder builder = new ProcessBuilder("python", fileName);
                builder.directory(dirPath);

                builder.redirectErrorStream(true);
                File output = new File(builder.directory() + "/OUTPUT.txt");
                output.createNewFile();
                builder.redirectOutput(output);

                for(JsonObject testcase : testcases) {
                    builder.redirectInput(new File(testcase.getString("in_path")));
                    Process p = builder.start();
                    p.waitFor(testcase.getInteger("tl"), TimeUnit.MILLISECONDS);

                    if(p.isAlive()) {
                        // Time Limit Exceeded Error
                        p.destroyForcibly();
                        return "TLE";
                    } else {
                        // Python process terminated successfully
                        // judge by diff

                        ProcessBuilder diff = new ProcessBuilder("diff", "OUTPUT.txt",
                                testcase.getString("out_path"));
                        diff.directory(builder.directory());
                        Process px = diff.start();
                        px.waitFor();
                        if(px.getInputStream().available() != 0) {
                            return "WRA";
                        }
                    }
                }
            } catch(Exception e) {
                System.err.println("[Error]: " + e.getMessage());
                return "IERR";
            }

            return "ACC";
        }


        private String python3Judge(File dirPath, String fileName) {
            try {
                ProcessBuilder builder = new ProcessBuilder("python3", fileName);
                builder.directory(dirPath);

                builder.redirectErrorStream(true);
                File output = new File(builder.directory() + "/OUTPUT.txt");
                output.createNewFile();
                builder.redirectOutput(output);

                for(JsonObject testcase : testcases) {
                    builder.redirectInput(new File(testcase.getString("in_path")));
                    Process p = builder.start();
                    p.waitFor(testcase.getInteger("tl"), TimeUnit.MILLISECONDS);

                    if(p.isAlive()) {
                        // Time Limit Exceeded Error
                        p.destroyForcibly();
                        return "TLE";
                    } else {
                        // Python process terminated successfully
                        // judge by diff

                        ProcessBuilder diff = new ProcessBuilder("diff", "OUTPUT.txt",
                                testcase.getString("out_path"));
                        diff.directory(builder.directory());
                        Process px = diff.start();
                        px.waitFor();
                        if(px.getInputStream().available() != 0) {
                            return "WRA";
                        }
                    }
                }
            } catch(Exception e) {
                System.err.println("[Error]: " + e.getMessage());
                return "IERR";
            }

            return "ACC";
        }


        private String clangJudge(File dirPath, String fileName) {
            try {
                ProcessBuilder builder = new ProcessBuilder("gcc", fileName, "-w", "-O", "-lm", "-lpthread", "-o", "object");
                builder.directory(dirPath);
                builder.redirectErrorStream(true);

                Process compiler = builder.start();
                compiler.waitFor(3500, TimeUnit.MILLISECONDS);
                if(compiler.isAlive()) {
                    compiler.destroyForcibly();
                    return "CTLE";
                } else {
                    if(compiler.getInputStream().available() != 0) {
                        return "CERR";
                    }

                    builder = new ProcessBuilder("./object");
                    builder.directory(dirPath);
                    builder.redirectErrorStream(true);

                    File output = new File(builder.directory() + "/OUTPUT.txt");
                    output.createNewFile();
                    builder.redirectOutput(output);

                    for (JsonObject testcase : testcases) {
                        builder.redirectInput(new File(testcase.getString("in_path")));
                        Process p = builder.start();
                        p.waitFor(testcase.getInteger("tl"), TimeUnit.MILLISECONDS);

                        if (p.isAlive()) {
                            // Time Limit Exceeded Error
                            p.destroyForcibly();
                            return "TLE";
                        } else {
                            // Python process terminated successfully
                            // judge by diff

                            ProcessBuilder diff = new ProcessBuilder("diff", "OUTPUT.txt",
                                    testcase.getString("out_path"));
                            diff.directory(builder.directory());
                            Process px = diff.start();
                            px.waitFor();
                            if (px.getInputStream().available() != 0) {
                                return "WRA";
                            }
                        }
                    }

                }
            } catch(Exception e) {
                System.err.println("[Error]: " + e.getMessage());
                return "IERR";
            }

            return "ACC";
        }


        private String cpp14Judge(File dirPath, String fileName) {
            try {
                ProcessBuilder builder = new ProcessBuilder("g++", fileName, "-w", "-O", "-std=c++14", "-o", "object");
                builder.directory(dirPath);
                builder.redirectErrorStream(true);

                Process compiler = builder.start();
                compiler.waitFor(3500, TimeUnit.MILLISECONDS);
                if(compiler.isAlive()) {
                    compiler.destroyForcibly();
                    return "CTLE";
                } else {
                    if(compiler.getInputStream().available() != 0) {
                        return "CERR";
                    }

                    builder = new ProcessBuilder("./object");
                    builder.directory(dirPath);
                    builder.redirectErrorStream(true);

                    File output = new File(builder.directory() + "/OUTPUT.txt");
                    output.createNewFile();
                    builder.redirectOutput(output);

                    for (JsonObject testcase : testcases) {
                        builder.redirectInput(new File(testcase.getString("in_path")));
                        Process p = builder.start();
                        p.waitFor(testcase.getInteger("tl"), TimeUnit.MILLISECONDS);

                        if (p.isAlive()) {
                            // Time Limit Exceeded Error
                            p.destroyForcibly();
                            return "TLE";
                        } else {
                            // Python process terminated successfully
                            // judge by diff

                            ProcessBuilder diff = new ProcessBuilder("diff", "OUTPUT.txt",
                                    testcase.getString("out_path"));
                            diff.directory(builder.directory());
                            Process px = diff.start();
                            px.waitFor();
                            if (px.getInputStream().available() != 0) {
                                return "WRA";
                            }
                        }
                    }

                }
            } catch(Exception e) {
                System.err.println("[Error]: " + e.getMessage());
                return "IERR";
            }

            return "ACC";
        }


        private String javaJudge(File dirPath, String fileName) {
            try {
                ProcessBuilder builder = new ProcessBuilder("javac", "-O", fileName);
                builder.directory(dirPath);
                builder.redirectErrorStream(true);

                Process compiler = builder.start();
                compiler.waitFor(4500, TimeUnit.MILLISECONDS);
                if(compiler.isAlive()) {
                    compiler.destroyForcibly();
                    return "CTLE";
                } else {
                    if(compiler.getInputStream().available() != 0) {
                        return "CERR";
                    }
                    File[] fileList = dirPath.listFiles();
                    String classFileName = "Undefined";

                    for(File f : fileList) {
                        if(f.getName().contains(".class")) {
                            classFileName = f.getName().substring(0, f.getName().length() - 6);
                        }
                    }

                    builder = new ProcessBuilder("java", classFileName);
                    builder.directory(dirPath);
                    builder.redirectErrorStream(true);

                    File output = new File(builder.directory() + "/OUTPUT.txt");
                    output.createNewFile();
                    builder.redirectOutput(output);

                    for (JsonObject testcase : testcases) {
                        builder.redirectInput(new File(testcase.getString("in_path")));
                        Process p = builder.start();
                        p.waitFor(testcase.getInteger("tl"), TimeUnit.MILLISECONDS);

                        if (p.isAlive()) {
                            // Time Limit Exceeded Error
                            p.destroyForcibly();
                            return "TLE";
                        } else {
                            // Python process terminated successfully
                            // judge by diff

                            ProcessBuilder diff = new ProcessBuilder("diff", "OUTPUT.txt",
                                    testcase.getString("out_path"));
                            diff.directory(builder.directory());
                            Process px = diff.start();
                            px.waitFor();
                            if (px.getInputStream().available() != 0) {
                                return "WRA";
                            }
                        }
                    }

                }
            } catch(Exception e) {
                System.err.println("[Error]: " + e.getMessage());
                return "IERR";
            }

            return "ACC";
        }


        @Override
        public void run() {
            // Write to a file in Judge Test Folder
            String dirPath = Utility.getJudgeFolderPath() + "/" + id;
            String fileName = id + mapExtension(lang);
            String filePath = dirPath + "/" + fileName;

            try {
                File file = new File(dirPath);
                file.mkdirs();
                FileOutputStream fout = new FileOutputStream(filePath);
                PrintWriter writer = new PrintWriter(fout);
                writer.print(code);
                writer.close();
                fout.close();

                // Detect the language and route accordingly
                String s = "ULANG";

                if(lang.equals("py2")) {
                    s = python2Judge(file, fileName);
                }

                if(lang.equals("py3")) {
                    s = python3Judge(file, fileName);
                }

                if(lang.equals("c99")) {
                    s = clangJudge(file, fileName);
                }

                if(lang.equals("cpp14")) {
                    s = cpp14Judge(file, fileName);
                }

                if(lang.equals("java8")) {
                    s = javaJudge(file, fileName);
                }

                // Update into database
                final String resultMsg = s;
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
                            List<JsonObject> testcases = result.result().getRows();
                            pool.submit(new Job(id, problemCode, Utility.decode(code), lang, testcases));
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
