package com.server;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

public class Server implements HttpHandler {

    /**
     * Method to handle the http request
     * 
     * @param exchange
     * @throws IOException
     */

    @SuppressWarnings("deprecation")
    @Override
    public void handle(HttpExchange exchange) throws IOException {

        System.out.println("Request handled in thread " + Thread.currentThread().getId());

        if ("POST".equals(exchange.getRequestMethod())) {

            try {
                handlePostRequest(exchange);
            } catch (JSONException | IOException | SQLException e) {
                e.printStackTrace();
                e.getMessage();
            }

        } else if ("GET".equals(exchange.getRequestMethod())) {

            JSONArray responseMessages = new JSONArray();
            try {
                responseMessages = handleGetRequest(exchange);
            } catch (IOException | SQLException e) {
                e.printStackTrace();
            }

            handleResponse(exchange, responseMessages.toString(), 200);

        } else {

            handleResponse(exchange, "Not Supported", 505);
        }
    }

    /**
     * Method to handle the post request
     * 
     * @param httpExchange
     * @throws IOException
     * @throws JSONException
     * @throws SQLException
     */

    private void handlePostRequest(HttpExchange httpExchange) throws IOException, JSONException, SQLException {

        Headers headers = httpExchange.getRequestHeaders();
        String contentType = "";
        String respondString = "";
        int code;
        InputStream stream = httpExchange.getRequestBody();
        String newMessage = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines()
                .collect(Collectors.joining("\n"));
        stream.close();
        JSONObject messages = null;

        if (headers.containsKey("Content-Type")) {
            contentType = headers.get("Content-Type").get(0);

            if (contentType.equals("application/json")) {

                try {

                    messages = new JSONObject(newMessage);
                } catch (JSONException e) {
                    code = 400;
                    respondString = "json parse error, faulty user json";
                    handleResponse(httpExchange, respondString, code);
                    e.printStackTrace();
                    System.out.println("json parse error, faulty user json");
                }

                if (messages.has("originalPostingTime") && messages.has("locationName")
                        && messages.has("locationDescription") && messages.has("locationCity")
                        && messages.has("locationCountry") && messages.has("locationStreetAddress")
                        && messages.has("originalPoster")) {

                    try {

                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
                        String time = messages.getString("originalPostingTime");
                        LocalDateTime dateTime = LocalDateTime.parse(time, formatter);
                        long unixTime = dateTime.toInstant(ZoneOffset.UTC).toEpochMilli();

                        MessagesUsersDB dbInstance = MessagesUsersDB.getInstance();

                        dbInstance.insertMessage(messages, unixTime);

                        code = 200;
                        respondString = "Message received";

                    } catch (Exception e) {
                        code = 415;
                        respondString = "originalPostingTime wrong format";
                        handleResponse(httpExchange, respondString, code);
                        e.printStackTrace();
                    }

                } else if (messages.has("locationID") && messages.has("locationVisitor")) {

                    MessagesUsersDB dbInstance = MessagesUsersDB.getInstance();
                    dbInstance.updateVisit(messages);
                    code = 200;

                } else {

                    code = 414;
                    respondString = "no required message fields";

                }

            } else {
                code = 407;
                respondString = "content type is not application/json";
            }

        } else {
            code = 411;
            respondString = "No content type";
        }

        handleResponse(httpExchange, respondString, code);
    }

    /**
     * Method to handle the get request
     * 
     * @param exchange
     * @return JSONArray that contains the messages in JSON format
     * @throws IOException
     * @throws SQLException
     */

    private JSONArray handleGetRequest(HttpExchange exchange) throws IOException, SQLException {

        MessagesUsersDB dbInstance = MessagesUsersDB.getInstance();
        JSONArray responseMessages = new JSONArray();
        responseMessages = dbInstance.getMessages();

        return responseMessages;
    }

    /**
     * Method to handle the response
     * 
     * @param exchange
     * @param respondString response string for the http request
     * @param code          response code for the http request
     * @throws IOException
     */

    private void handleResponse(HttpExchange exchange, String respondString, int code) throws IOException {

        byte[] bytes = respondString.getBytes("UTF-8");
        exchange.sendResponseHeaders(code, bytes.length);

        OutputStream outputStream = exchange.getResponseBody();

        outputStream.write(respondString.toString().getBytes());
        outputStream.flush();
        outputStream.close();
    }

}
