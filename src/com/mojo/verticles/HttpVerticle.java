package com.mojo.verticles;

import com.mojo.handlers.accounts.GetAccountHandler;
import com.mojo.handlers.accounts.LoginHandler;
import com.mojo.handlers.accounts.PostAccountHandler;
import com.mojo.handlers.accounts.PutAccountHandler;
import com.mojo.handlers.comments.GetCommentsHandler;
import com.mojo.handlers.comments.PostCommentsHandler;
import com.mojo.handlers.leaderboard.GetProblemWiseHandler;
import com.mojo.handlers.leaderboard.GetRankHandler;
import com.mojo.handlers.problems.GetProblemsHandler;
import com.mojo.handlers.problems.PostProblemsHandler;
import com.mojo.handlers.problems.PostTestcaseHandler;
import com.mojo.handlers.solutions.GetSolutionsHandler;
import com.mojo.handlers.solutions.PostSolutionsHandler;
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
        router.route(HttpMethod.POST, "/api/accounts/update").handler(new PutAccountHandler());
        router.route(HttpMethod.POST, "/api/accounts/login").handler(new LoginHandler());

        // Problems API
        // GET /api/problems -> GET all problems if auth
        // POST /api/problems -> Create a new problem if auth

        router.route(HttpMethod.POST, "/api/problems").handler(new PostProblemsHandler());
        router.route(HttpMethod.GET, "/api/problems").handler(new GetProblemsHandler());
        router.route(HttpMethod.POST, "/api/problems/testcases").handler(new PostTestcaseHandler());

        // Solutions API
        // GET /api/submissions -> GET all submissions
        // GET /api/submissions ? id = <?id?> GET all submissions of a certain id
        // POST /api/submissions -> Post a solution to Judge

        router.route(HttpMethod.GET, "/api/submissions").handler(new GetSolutionsHandler());
        router.route(HttpMethod.POST, "/api/submissions").handler(new PostSolutionsHandler(vertx.eventBus()));

        // Leaderboard API
        // GET /api/leaderboard/score
        // GET /api/leaderboard/rank

        router.route(HttpMethod.GET, "/api/leaderboard/score").handler(new GetProblemWiseHandler());
        router.route(HttpMethod.GET, "/api/leaderboard/rank").handler(new GetRankHandler());

        // Comments Section API
        // GET /api/comments
        // POST /api/comments
        router.route(HttpMethod.POST, "/api/comments").handler(new PostCommentsHandler());
        router.route(HttpMethod.GET, "/api/comments").handler(new GetCommentsHandler());
        
        try {
            vertx.createHttpServer().requestHandler(router::accept).listen(12400);
        } catch(Exception e) {
            System.err.println("[Error in HttpServer]: Could not deploy http interface.");
        }
    }
}