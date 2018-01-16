package com.mojo.verticles;

import com.mojo.handlers.accounts.GetAccountHandler;
import com.mojo.handlers.accounts.LoginHandler;
import com.mojo.handlers.accounts.PostAccountHandler;
import com.mojo.handlers.problems.GetProblemsHandler;
import com.mojo.handlers.problems.PostProblemsHandler;
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
        // GET /api/accounts -> GET Account Related Resources
        // POST /api/accounts -> Create a new account
        
        router.route(HttpMethod.POST, "/api/accounts").handler(new PostAccountHandler());
        router.route(HttpMethod.GET, "/api/accounts").handler(new GetAccountHandler());
        router.route(HttpMethod.POST, "/api/accounts/login").handler(new LoginHandler());

        // Problems API
        // GET /api/problems -> GET all problems if auth
        // POST /api/problems -> Create a new problem if auth

        router.route(HttpMethod.POST, "/api/problems").handler(new PostProblemsHandler());
        router.route(HttpMethod.GET, "/api/problems").handler(new GetProblemsHandler());
        
        try {
            vertx.createHttpServer().requestHandler(router::accept).listen(12400);
        } catch(Exception e) {
            System.err.println("[Error in HttpServer]: Could not deploy http interface.");
        }
    }
}