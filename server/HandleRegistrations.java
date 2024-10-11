package com.server;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class HandleRegistrations implements HttpHandler {

    private final UserAuthenticator userAuthenticator;

    /**
     * Constructor for HandleRegistrations
     * 
     * @param userAuthenticator
     */

    HandleRegistrations(UserAuthenticator userAuthenticator) {
        this.userAuthenticator = userAuthenticator;
    }

    /**
     * Method to handle the http request
     * 
     * @param exchange
     * @throws IOException
     */

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        if ("POST".equals(exchange.getRequestMethod())) {

            handlePostRequest(exchange);

        } else if ("GET".equals(exchange.getRequestMethod())) {

            handleNotSupportedResponse(exchange, "Not Supported");

        } else {

            handleNotSupportedResponse(exchange, "Not Supported");
        }

    }

    /**
     * Method to handle the not supported request
     * 
     * @param exchange
     * @param respondString response string for the http request
     * @throws IOException
     */

    private void handleNotSupportedResponse(HttpExchange exchange, String respondString) throws IOException {

        byte[] bytes = respondString.getBytes("UTF-8");
        exchange.sendResponseHeaders(400, bytes.length);

        OutputStream outputStream = exchange.getResponseBody();

        outputStream.write(respondString.toString().getBytes());
        outputStream.flush();
        outputStream.close();
    }

    /**
     * Method to handle the post request
     * 
     * @param exchange
     * @throws IOException
     */

    private void handlePostRequest(HttpExchange exchange) throws IOException {

        Headers headers = exchange.getRequestHeaders();
        String contentType = "";
        String respondString = "";
        int code = 400;
        InputStream stream = exchange.getRequestBody();
        String newUser = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines()
                .collect(Collectors.joining("\n"));
        stream.close();
        JSONObject user = null;

        if (headers.containsKey("Content-Type")) {
            contentType = headers.get("Content-Type").get(0);

            if (contentType.equals("application/json")) {

                if (newUser == null || newUser.length() == 0) {
                    code = 412;
                    respondString = "no user credentials";
                } else {

                    try {
                        user = new JSONObject(newUser);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        System.out.println("json parse error, faulty user json");
                    }

                    if (user.getString("username").length() == 0 || user.getString("password").length() == 0
                            || user.getString("email").length() == 0 || user.getString("userNickname").length() == 0) {
                        code = 413;
                        respondString = "no proper user credentials";
                    } else if (userAuthenticator.addUser(user.getString("username"), user.getString("password"),
                            user.getString("email"), user.getString("userNickname"))) {
                        code = 200;
                        respondString = "User registered";
                    } else {
                        code = 405;
                        respondString = "user aldready exist";
                    }
                }
            } else {
                code = 407;
                respondString = "content type is not application/json";
            }

        } else {
            code = 411;
            respondString = "No content type";
        }

        handlePostResponse(exchange, respondString, code);
    }

    /**
     * Method to handle the post response
     * 
     * @param exchange
     * @param respondString response string for the http request
     * @param code          response code for the http request
     * @throws IOException
     */

    private void handlePostResponse(HttpExchange exchange, String respondString, int code) throws IOException {

        byte[] bytes = respondString.getBytes("UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);

        OutputStream outputStream = exchange.getResponseBody();

        outputStream.write(respondString.toString().getBytes());
        outputStream.flush();
        outputStream.close();
    }

}
