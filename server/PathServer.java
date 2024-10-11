package com.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class PathServer implements HttpHandler{

    /**
     * Method to handle the http request
     * 
     * @param exchange
     * @throws IOException
     */

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        
        if ("POST".equals(exchange.getRequestMethod())) {

            try {
                handlePostRequest(exchange);
            } catch (IOException | JSONException | SQLException e) {
                handleResponse(exchange, "Error during POST", 400);
                e.printStackTrace();
            }
           
        } else if ("GET".equals(exchange.getRequestMethod())) {
            
            JSONArray responseMessages = new JSONArray();
            try {
                responseMessages = handleGetRequest(exchange);
            } catch (IOException | SQLException e) {
                handleResponse(exchange, "Error during GET", 400);
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
     * @throws SQLException
     */

    private void handlePostRequest(HttpExchange httpExchange) throws IOException, SQLException {
        Headers headers = httpExchange.getRequestHeaders();
        String contentType = "";
        String respondString = "";
        int code;
        InputStream stream = httpExchange.getRequestBody();
        String newMessage = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines()
                .collect(Collectors.joining("\n"));
        stream.close();
        JSONObject message = null;

        if (headers.containsKey("Content-Type")) {
            contentType = headers.get("Content-Type").get(0);

            if (contentType.equals("application/json")) {

                try {

                    message = new JSONObject(newMessage);
                } catch (JSONException e) {

                    e.printStackTrace();
                    System.out.println("json parse error, faulty user json");
                }

                if (!message.has("tour_name") || !message.has("tourDescription")) {

                    code = 414;
                    respondString = "no required message fields";

                } else {

                    String tourName = message.getString("tour_name");
                    String tourDescription = message.getString("tourDescription");
                    MessagesUsersDB dbInstance = MessagesUsersDB.getInstance();
                    JSONArray locationIDsArray = message.getJSONArray("locations");

                    try {
                        dbInstance.insertTour(tourName, tourDescription);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                    for (int i = 0; i < locationIDsArray.length(); i++) {
                        
                        JSONObject location = locationIDsArray.getJSONObject(i);
                        int locationID = location.getInt("locationID");
                        int tourID = dbInstance.getLastTourID();

                        try {
                            dbInstance.insertOnTour(tourID, locationID);
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }

                    }
                        code = 200;
                        respondString = "Message received";
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
     * @param httpExchange
     * @return JSONArray
     * @throws IOException
     * @throws SQLException
     */

    private JSONArray handleGetRequest(HttpExchange httpExchange) throws IOException, SQLException {
        
        MessagesUsersDB dbInstance = MessagesUsersDB.getInstance();
        JSONArray responseMessages = new JSONArray();
        responseMessages = dbInstance.getTours();
        
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
