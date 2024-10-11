package com.server;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;

import java.time.*;
import java.time.format.DateTimeFormatter;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.Crypt;
import org.json.JSONArray;
import org.json.JSONObject;

public class MessagesUsersDB {

    private Connection dbConnection = null;
    private static MessagesUsersDB dbInstance = null;
    private final String database = "jdbc:sqlite:";
    private URL url;

    /**
     * Singleton pattern to ensure only one instance of the database is created
     * 
     * @return instance of the database
     */

    public static synchronized MessagesUsersDB getInstance() {
        if (null == dbInstance) {
            dbInstance = new MessagesUsersDB();
        }
        return dbInstance;
    }

    /**
     * Constructor for the database
     */

    private MessagesUsersDB() {

        try {
            initializeDB();
        } catch (SQLException e) {
            e.getSQLState();
        }
    }

    /**
     * Opens the database
     * 
     * @param dbName name of the database
     * @throws SQLException
     */

    public void open(String dbName) throws SQLException {

        File dbFile = new File(dbName);
        boolean fileExists = dbFile.exists() && !dbFile.isDirectory();
        String databaseURl = database + dbName;
        dbConnection = DriverManager.getConnection(databaseURl);

        try {
            dbConnection = DriverManager.getConnection(databaseURl);

            if (!fileExists) {
                initializeDB();
            }
        } catch (SQLException e) {
            System.err.println("Error establishing connection: " + e.getMessage());
        }
    }

    /**
     * Initializes the database
     * 
     * @return true if the database is created, false if not
     * @throws SQLException
     */

    private boolean initializeDB() throws SQLException {

        if (null != dbConnection) {

            String createUserTable = "CREATE TABLE users (username varchar(50) NOT NULL, password varchar(50) NOT NULL, email varchar(50), userNickname varchar(50), PRIMARY KEY(username))";
            String createMessageTable = "CREATE TABLE messages (postID INTEGER PRIMARY KEY AUTOINCREMENT, locationName varchar(50) NOT NULL, locationDescription varchar(50) NOT NULL, locationCity varchar(50) NOT NULL, locationCountry varchar(50) NOT NULL, locationStreetAddress varchar(50) NOT NULL, originalPoster varchar(50), originalPostingTime long, timeVisited integer)";
            // FOREIGN KEY (originalPoster) REFERENCES users(username))";
            String createLocationTable = "CREATE TABLE location (postID INTEGER, latitude double, longitude double, FOREIGN KEY (postID) REFERENCES messages(postID))";
            String createWeatherTable = "CREATE TABLE weather (postID INTEGER, weather double, FOREIGN KEY (postID) REFERENCES messages(postID))";
            String createTourTable = "CREATE TABLE tours (tourID INTEGER PRIMARY KEY AUTOINCREMENT, tour_name varchar(50) NOT NULL, tourDescription varchar(50) NOT NULL)";
            String createOnTourTable = "CREATE TABLE onTour (tourID INTEGER, postID INTEGER, FOREIGN KEY (tourID) REFERENCES tours(tourID), FOREIGN KEY (postID) REFERENCES messages(postID))";

            try (Statement createStatement = dbConnection.createStatement()) {
                createStatement.executeUpdate(createUserTable);
                createStatement.executeUpdate(createMessageTable);
                createStatement.executeUpdate(createLocationTable);
                createStatement.executeUpdate(createWeatherTable);
                createStatement.executeUpdate(createTourTable);
                createStatement.executeUpdate(createOnTourTable);
                System.out.println("databse created");
                return true;
            } catch (SQLException e) {
                System.out.println("database not created");
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * Closes the database
     * 
     * @throws SQLException
     */

    public void closeDB() throws SQLException {

        if (null != dbConnection) {
            dbConnection.close();
            dbConnection = null;
            System.out.println("database closed");
        }
    }

    /**
     * Inserts a user into the database
     * 
     * @param username     username of the user
     * @param password     password of the user
     * @param email        email of the user
     * @param userNickname nickname of the user
     * @throws SQLException
     */

    public void insertUser(String username, String password, String email, String userNickname) throws SQLException {

        StringBuilder insertUser = new StringBuilder();
        String gryptPassword = cryptPassword(password);

        if (isSQLinjection(email) || isSQLinjection(userNickname)) {
            return;
        }

        insertUser.append("INSERT INTO users VALUES ('");
        insertUser.append(username).append("', '").append(gryptPassword).append("', '").append(email).append("', '")
                .append(userNickname).append("')");

        try (Statement createStatement = dbConnection.createStatement()) {
            createStatement.executeUpdate(insertUser.toString());

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if the user exists in the database
     * 
     * @param username     username of the user
     * @param userNickname nickname of the user
     * @return true if the user exists, false if not
     * @throws SQLException
     */

    public boolean checkUser(String username, String userNickname) throws SQLException {

        if (isSQLinjection(username) || isSQLinjection(userNickname)) {
            return false;
        }

        String checkUser = "SELECT * FROM users WHERE username = '" + username + "'";
        // String checkNickname = "SELECT * FROM users WHERE userNickname = '" +
        // userNickname + "'";
        try {

            Statement createStatement = dbConnection.createStatement();
            ResultSet rsUsername = createStatement.executeQuery(checkUser);
            // ResultSet rsNickname = createStatement.executeQuery(checkNickname);
            if (rsUsername.next()) {
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    /**
     * Authenticates the user
     * 
     * @param givenUsername username of the user
     * @param givenPassword password of the user
     * @return true if the user is authenticated, false if not
     * @throws SQLException
     */

    public boolean authenticateUser(String givenUsername, String givenPassword) throws SQLException {

        if (isSQLinjection(givenUsername) || isSQLinjection(givenPassword)) {
            return false;
        }

        String checkUser = "SELECT password FROM users WHERE username = '" + givenUsername + "'";

        try {

            Statement createStatement = dbConnection.createStatement();
            ResultSet rsPassword = createStatement.executeQuery(checkUser);

            if (rsPassword.next()) {
                String password = rsPassword.getString("password");
                if (password.equals(Crypt.crypt(givenPassword, password))) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Inserts a message into the database
     * 
     * @param messages  message to be inserted
     * @param latitude  latitude of the message
     * @param longitude longitude of the message
     * @param unixTime  time of the message
     * @throws SQLException
     */

    public void insertMessage(JSONObject messages, Long unixTime)
            throws SQLException, IOException {

        if (isSQLinjection(messages.getString("locationName"))
                || isSQLinjection(messages.getString("locationDescription"))
                || isSQLinjection(messages.getString("locationCity"))
                || isSQLinjection(messages.getString("locationCountry"))
                || isSQLinjection(messages.getString("locationStreetAddress"))) {
            return;
        }

        insertPost(messages, unixTime);

        if (messages.has("latitude") && messages.has("longitude")) {
            insertCordication(messages.getDouble("latitude"), messages.getDouble("longitude"));

            if (messages.has("weather")) {
                Double weather = getWeather(messages.getDouble("latitude"), messages.getDouble("longitude"));
                insertWeather(weather);
            }
        }
    }

    /**
     * Inserts a post into the database
     * 
     * @param messages message to be inserted
     * @param unixTime time of the message
     */

    private void insertPost(JSONObject messages, Long unixTime) {

        String insertMessage = "INSERT INTO messages (locationName, locationDescription, locationCity, " +
                "locationCountry, locationStreetAddress, originalPoster, originalPostingTime)" +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement preparedStatement = dbConnection.prepareStatement(insertMessage)) {
            preparedStatement.setString(1, messages.getString("locationName"));
            preparedStatement.setString(2, messages.getString("locationDescription"));
            preparedStatement.setString(3, messages.getString("locationCity"));
            preparedStatement.setString(4, messages.getString("locationCountry"));
            preparedStatement.setString(5, messages.getString("locationStreetAddress"));
            preparedStatement.setString(6, messages.getString("originalPoster"));
            preparedStatement.setLong(7, unixTime);

            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Inserts the cordication into the database
     * 
     * @param latitude  latitude of the message
     * @param longitude longitude of the message
     * @throws SQLException
     */

    private void insertCordication(double latitude, double longitude) throws SQLException {

        String insertCordication = "INSERT INTO location (postID, latitude, longitude) VALUES (?, ?, ?)";

        try (PreparedStatement preparedStatement = dbConnection.prepareStatement(insertCordication)) {
            preparedStatement.setInt(1, getLastPostID());
            preparedStatement.setDouble(2, latitude);
            preparedStatement.setDouble(3, longitude);

            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    /**
     * Inserts the weather into the database
     * 
     * @param weather weather of the message
     * @throws SQLException
     */

    private void insertWeather(double weather) throws SQLException {

        String insertWeather = "INSERT INTO weather (postID, weather) VALUES (?, ?)";

        try (PreparedStatement preparedStatement = dbConnection.prepareStatement(insertWeather)) {
            preparedStatement.setInt(1, getLastPostID());
            preparedStatement.setDouble(2, weather);

            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the last post ID
     * 
     * @return last post ID
     * @throws SQLException
     */

    public int getLastPostID() throws SQLException {

        String getLastPostID = "SELECT postID FROM messages ORDER BY postID DESC LIMIT 1";
        int postID = 0;

        try {
            Statement createStatement = dbConnection.createStatement();
            ResultSet rsPostID = createStatement.executeQuery(getLastPostID);

            if (rsPostID.next()) {
                postID = rsPostID.getInt("postID");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return postID;
    }

    /**
     * Gets the weather for the message
     * 
     * @param latitude  latitude of the message
     * @param longitude longitude of the message
     * @return weather
     */

    @SuppressWarnings("deprecation")
    public double getWeather(Double latitude, Double longitude) throws IOException {

        StringBuilder xmlBuilder = new StringBuilder();
        xmlBuilder.append("<coordinates><latitude>");
        xmlBuilder.append(latitude);
        xmlBuilder.append("</latitude><longitude>");
        xmlBuilder.append(longitude);
        xmlBuilder.append("</longitude></coordinates>");

        HttpURLConnection urlConnection = null;

        try {
            this.url = new URL("http://localhost:4001/weather");
        } catch (MalformedURLException e) {

            e.printStackTrace();
        }

        urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setReadTimeout(10000);
        urlConnection.setConnectTimeout(20000);
        urlConnection.setRequestMethod("POST");
        urlConnection.setDoOutput(true);
        urlConnection.setDoInput(true);
        urlConnection.setRequestProperty("Content-Type", "application/xml");

        OutputStream stream = urlConnection.getOutputStream();

        stream.write(xmlBuilder.toString().getBytes("UTF-8"));
        stream.flush();
        stream.close();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(urlConnection.getInputStream(), StandardCharsets.UTF_8));

        String inputDump;
        StringBuilder xmlString = new StringBuilder();

        while ((inputDump = reader.readLine()) != null) {

            xmlString.append(inputDump);

        }

        reader.close();

        return findTempeture(xmlString.toString());
    }

    /**
     * Finds the temperature from the XML string
     * 
     * @param xmlString XML string
     * @return temperature
     */

    private double findTempeture(String xmlString) {

        Pattern pattern = Pattern.compile("<temperature>(\\d+)</temperature>");
        Matcher matcher = pattern.matcher(xmlString);
        double temperature = 0;

        if (matcher.find()) {
            String temperatureText = matcher.group(1);
            try {
                temperature = Double.parseDouble(temperatureText);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
            return temperature;
        } else {
            System.out.println("Temperature not found!");
        }

        return temperature;
    }

    /**
     * Inserts a tour into the database
     * 
     * @param tourName        name of the tour
     * @param tourDescription description of the tour
     * @throws SQLException
     */

    public void insertTour(String tourName, String tourDescription) throws SQLException {

        if (isSQLinjection(tourName) || isSQLinjection(tourDescription)) {
            return;
        }

        String insertTour = "INSERT INTO tours (tour_name, tourDescription) VALUES (?, ?)";

        try (PreparedStatement preparedStatement = dbConnection.prepareStatement(insertTour)) {
            preparedStatement.setString(1, tourName);
            preparedStatement.setString(2, tourDescription);

            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Inserts a message into a tour
     * 
     * @param tourID tour ID
     * @param postID post ID
     * @throws SQLException
     */

    public void insertOnTour(int tourID, int postID) throws SQLException {

        String insertOnTour = "INSERT INTO onTour (tourID, postID) VALUES (?, ?)";

        try (PreparedStatement preparedStatement = dbConnection.prepareStatement(insertOnTour)) {
            preparedStatement.setInt(1, tourID);
            preparedStatement.setInt(2, postID);

            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the last tour ID
     * 
     * @return last tour ID
     * @throws SQLException
     */

    public int getLastTourID() throws SQLException {

        String getLastTourID = "SELECT tourID FROM tours ORDER BY tourID DESC LIMIT 1";
        int tourID = 0;

        try {
            Statement createStatement = dbConnection.createStatement();
            ResultSet rsTourID = createStatement.executeQuery(getLastTourID);

            if (rsTourID.next()) {
                tourID = rsTourID.getInt("tourID");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tourID;
    }

    /**
     * Gets the messages from the database
     * 
     * @return messages from the database
     * @throws SQLException
     */

    public JSONArray getMessages() throws SQLException {

        String getMessagesQuery = "SELECT * FROM messages";
        String getCordinationsQuery = "SELECT * FROM location WHERE postID = ?";
        String getWeatherQueString = "SELECT * FROM weather WHERE postID = ?";
        JSONArray messages = new JSONArray();
        PreparedStatement preparedStatement = dbConnection.prepareStatement(getMessagesQuery);

        ResultSet rsMessages = preparedStatement.executeQuery();

        try {
            while (rsMessages.next()) {

                JSONObject message = new JSONObject();
                int postID = rsMessages.getInt("postID");
                message.put("locationID", postID);
                message.put("locationName", rsMessages.getString("locationName"));
                message.put("locationDescription", rsMessages.getString("locationDescription"));
                message.put("locationCity", rsMessages.getString("locationCity"));
                message.put("locationCountry", rsMessages.getString("locationCountry"));
                message.put("locationStreetAddress", rsMessages.getString("locationStreetAddress"));
                message.put("originalPoster", rsMessages.getString("originalPoster"));

                long originalPostingTime = rsMessages.getLong("originalPostingTime");
                String formattedTime = longToFormattedTime(originalPostingTime);
                message.put("originalPostingTime", formattedTime);

                PreparedStatement preparedStatementCordination = dbConnection.prepareStatement(getCordinationsQuery);
                preparedStatementCordination.setInt(1, postID);

                ResultSet rsCordinates = preparedStatementCordination.executeQuery();
                if (rsCordinates.next()) {
                    message.put("latitude", rsCordinates.getDouble("latitude"));
                    message.put("longitude", rsCordinates.getDouble("longitude"));
                }

                PreparedStatement preparedStatementWeather = dbConnection.prepareStatement(getWeatherQueString);
                preparedStatementWeather.setInt(1, postID);
                ResultSet rsWeather = preparedStatementWeather.executeQuery();

                if (rsWeather.next()) {
                    message.put("weather", rsWeather.getDouble("weather"));
                }

                messages.put(message);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return messages;
    }

    /**
     * Converts long to formatted time
     * 
     * @param unixTime time in long
     * @return formatted time
     */

    public String longToFormattedTime(long unixTime) {

        ZonedDateTime utcDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(unixTime), ZoneId.of("UTC"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
        String formattedTime = utcDateTime.format(formatter);

        return formattedTime;
    }

    /**
     * Gets the tours from the database
     * 
     * @return tours from the database
     */

    public JSONArray getTours() {

        String getToursQuery = "SELECT * FROM tours";
        String getPostsOnTourQuery = "SELECT postID FROM onTour WHERE tourID = ?";
        String getPostsQuery = "SELECT * FROM messages WHERE postID = ?";
        String getCordinationsQuery = "SELECT * FROM location WHERE postID = ?";
        String getWeatherQueString = "SELECT * FROM weather WHERE postID = ?";
        JSONArray tours = new JSONArray();

        try {
            PreparedStatement preparedStatement = dbConnection.prepareStatement(getToursQuery);
            ResultSet rsTours = preparedStatement.executeQuery();

            while (rsTours.next()) {

                JSONObject tour = new JSONObject();
                int tourID = rsTours.getInt("tourID");
                tour.put("tour_name", rsTours.getString("tour_name"));
                tour.put("tourDescription", rsTours.getString("tourDescription"));

                PreparedStatement preparedStatementPostsOnTour = dbConnection.prepareStatement(getPostsOnTourQuery);
                preparedStatementPostsOnTour.setInt(1, tourID);
                ResultSet rsPostsOnTour = preparedStatementPostsOnTour.executeQuery();

                JSONArray postsOnTour = new JSONArray();

                while (rsPostsOnTour.next()) {
                    JSONObject location = new JSONObject();
                    int postID = rsPostsOnTour.getInt("postID");

                    PreparedStatement preparedStatementPosts = dbConnection.prepareStatement(getPostsQuery);
                    preparedStatementPosts.setInt(1, postID);
                    ResultSet rsPosts = preparedStatementPosts.executeQuery();
                    location.put("locationID", postID);
                    location.put("locationName", rsPosts.getString("locationName"));
                    location.put("locationDescription", rsPosts.getString("locationDescription"));
                    location.put("locationCity", rsPosts.getString("locationCity"));
                    location.put("locationCountry", rsPosts.getString("locationCountry"));
                    location.put("locationStreetAddress", rsPosts.getString("locationStreetAddress"));
                    location.put("originalPoster", rsPosts.getString("originalPoster"));

                    long originalPostingTime = rsPosts.getLong("originalPostingTime");
                    String formattedTime = longToFormattedTime(originalPostingTime);
                    location.put("originalPostingTime", formattedTime);

                    PreparedStatement preparedStatementCordination = dbConnection
                            .prepareStatement(getCordinationsQuery);
                    preparedStatementCordination.setInt(1, postID);
                    ResultSet rsCordinates = preparedStatementCordination.executeQuery();

                    if (rsCordinates.next()) {
                        location.put("latitude", rsCordinates.getDouble("latitude"));
                        location.put("longitude", rsCordinates.getDouble("longitude"));
                    }

                    PreparedStatement preparedStatementWeather = dbConnection.prepareStatement(getWeatherQueString);
                    preparedStatementWeather.setInt(1, postID);
                    ResultSet rsWeather = preparedStatementWeather.executeQuery();

                    if (rsWeather.next()) {
                        location.put("weather", rsWeather.getDouble("weather"));
                    }

                    postsOnTour.put(location);
                }

                tour.put("locations", postsOnTour);
                tours.put(tour);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return tours;
    }

    /**
     * Update visit count in the post
     * 
     * @param message has the locationID and the visitor
     * @throws SQLException
     */

    public void updateVisit(JSONObject message) throws SQLException {

        String updateVisitor;
        int timeVisited = 0;
    
        String getTimeVisited = "SELECT timeVisited FROM messages WHERE postID = " + message.getInt("locationID");
        PreparedStatement preparedStatementPosts;
        try {
            preparedStatementPosts = dbConnection.prepareStatement(getTimeVisited);
            ResultSet rsPosts = preparedStatementPosts.executeQuery();
            timeVisited = rsPosts.getInt("timeVisited");
        } catch (SQLException e) {
            
            e.printStackTrace();
        }

        if (timeVisited > 0) {
            updateVisitor = "UPDATE messages SET timeVisited = timeVisited + 1 WHERE postID = ?";
        } else {
            updateVisitor = "UPDATE messages SET timeVisited = 1 WHERE postID = ?";
        }

        try (PreparedStatement preparedStatement = dbConnection.prepareStatement(updateVisitor)) {
            preparedStatement.setInt(1, message.getInt("locationID"));
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    
     /**
     * Gets the top five visited locations from the database
     * 
     * @return top five locations from the database
     */

    public JSONArray getTopFive() {

        String getTopFive = "SELECT postID, locationName, timeVisited FROM messages GROUP BY postID ORDER BY timeVisited DESC LIMIT 5";

        JSONArray topFive = new JSONArray();

        try {

            PreparedStatement preparedStatementPostsOnTour = dbConnection.prepareStatement(getTopFive);
            ResultSet rsTopFivePost = preparedStatementPostsOnTour.executeQuery();

            while (rsTopFivePost.next()) {

                JSONObject location = new JSONObject();
                location.put("locationID", rsTopFivePost.getInt("postID"));
                location.put("locationName", rsTopFivePost.getString("locationName"));
                location.put("timesVisited", rsTopFivePost.getInt("timeVisited"));

                topFive.put(location);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return topFive;
    }

    /**
     * Crypts the password
     * 
     * @param password password to be crypted
     * @return crypted password
     */

    public String cryptPassword(String password) {

        SecureRandom strongRandomNumberGenerator = new SecureRandom();

        byte b[] = new byte[13];
        strongRandomNumberGenerator.nextBytes(b);

        String saltedB = new String(Base64.getEncoder().encode(b));
        String salt = "$6$" + saltedB;

        String cryptPassword = Crypt.crypt(password, salt);

        return cryptPassword;
    }

    /**
     * Checks if there is SQL injection
     * 
     * @param userInput user input
     * @return true if there is SQL injection, false if not
     */

    public boolean isSQLinjection(String userInput) {

        String sqlPattern = "(?i)\\b(SELECT|UPDATE|DELETE|INSERT|DROP|ALTER)\\b";

        if (userInput.matches(sqlPattern)) {
            return true;
        }

        return false;
    }
}
