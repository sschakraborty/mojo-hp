package com.mojo.verticles;

import com.mojo.handlers.accounts.GetAccountHandler;
import com.mojo.handlers.accounts.PostAccountHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

public class HttpVerticle extends AbstractVerticle {
    @Override
    public void start() {
        Router router = Router.router(vertx);
        
        router.route().handler(BodyHandler.create());
        router.route("/*").handler(StaticHandler.create("./static"));
        
        // Accounts API
        // GET /api/Accounts -> GET Account Related Resources
        // POST /api/Accounts -> Create a new account
        
        router.route(HttpMethod.POST, "/api/Accounts").handler(new PostAccountHandler());
        router.route(HttpMethod.GET, "/api/Accounts").handler(new GetAccountHandler());
        
        try {
            vertx.createHttpServer().requestHandler(router::accept).listen(12400);
        } catch(Exception e) {
            System.err.println("[Error in HttpServer]: Could not deploy http interface.");
        }
    }
}