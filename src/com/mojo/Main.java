/**
 *  This class is the main launcher class
 *  We are going to define a vertx instance in this class
 *  and deploy our required verticles (HttpVerticle) from here
 */

package com.mojo;

import com.mojo.resources.Database;
import com.mojo.resources.Utility;
import com.mojo.verticles.ExecutorVerticle;
import com.mojo.verticles.HttpVerticle;
import com.mojo.verticles.NativeVerticle;
import io.vertx.core.Vertx;

public class Main {
    public static void main(String[] args) throws Exception {
        if(true) {
            Vertx v = Vertx.vertx();
            Database.setVertxInstance(v);

            v.deployVerticle(new HttpVerticle(), (e) -> {
                if (e.succeeded()) {
                    System.out.println("Successfully deployed HTTP Interface");
                } else {
                    System.err.println(e.cause().getMessage());
                }
            });

            if(Utility.getArchitecture().equalsIgnoreCase("native")) {
                v.deployVerticle(new NativeVerticle(), (e) -> {
                    if (e.succeeded()) {
                        System.out.println("Successfully deployed native Judge");
                    } else {
                        System.err.println(e.cause().getMessage());
                    }
                });
            } else if(Utility.getArchitecture().equalsIgnoreCase("vanilla")) {
                v.deployVerticle(new ExecutorVerticle(), (e) -> {
                    if (e.succeeded()) {
                        System.out.println("Successfully deployed vanilla Judge");
                    } else {
                        System.err.println(e.cause().getMessage());
                    }
                });
            } else {
                System.err.println("Architecture not recognized");
                System.exit(0);
            }
        } else {
            // Verticle deployment errors
            System.err.println("Error in Main class: Verticles could not be deployed");
        }
    }
}
