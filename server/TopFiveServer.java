package com.server;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;

import org.json.JSONArray;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class TopFiveServer implements HttpHandler{

       /**
     * Method to handle the http request
     * 
     * @param exchange
     * @throws IOException
     */
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        
         if ("POST".equals(exchange.getRequestMethod())) {

            handleResponse(exchange, "Not Supported", 505);

        } else if ("GET".equals(exchange.getRequestMethod())) {
            
            JSONArray responseMessages = new JSONArray();
            responseMessages = handleGetRequest(exchange);
            handleResponse(exchange, responseMessages.toString(), 200);
        
        } else {

            handleResponse(exchange, "Not Supported", 505);
            
        }
    }

     /**
     * Method to handle the get request
     * 
     * @param httpExchange
     * @return JSONArray
     * @throws IOException
     * @throws SQLException
     */
    
    private JSONArray handleGetRequest(HttpExchange exchange) {
        
        MessagesUsersDB dbInstance = MessagesUsersDB.getInstance();
        JSONArray responseMessages = new JSONArray();
        responseMessages = dbInstance.getTopFive();
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
