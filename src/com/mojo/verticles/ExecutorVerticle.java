package com.mojo.verticles;

import com.mojo.resources.Utility;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import java.io.FileOutputStream;
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

        public Job(String id, String problemCode, String code, String lang) {
            this.code = Utility.decode(code);
            this.problemCode = problemCode;
            this.lang = Utility.decode(lang);
            this.id = id;
        }

        @Override
        public void run() {
            // Write to a file in Judge Test Folder
            try {
                FileOutputStream fout = new FileOutputStream(
                        Utility.getJudgeFolderPath() + "/" + id + mapExtension(lang));
                PrintWriter writer = new PrintWriter(fout);
                writer.print(code);
                writer.close();
                fout.close();
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
