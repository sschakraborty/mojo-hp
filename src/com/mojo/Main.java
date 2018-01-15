/**
 *  This class is the main launcher class
 *  We are going to define a vertx instance in this class
 *  and deploy our required verticles (HttpVerticle) from here
 */

package com.mojo;

import com.mojo.resources.Database;
import io.vertx.core.Vertx;

public class Main {
    public static void main(String[] args) throws Exception {
        Vertx v = Vertx.vertx();
        Database.setVertxInstance(v);
        v.deployVerticle("com.mojo.verticles.HttpVerticle", (e) -> {
            if(e.succeeded()) {
                System.out.println("Successfully deployed HttpVerticle");
            } else {
                System.err.println(e.cause().getMessage());
            }
        });
    }
}
