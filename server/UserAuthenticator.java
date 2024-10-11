package com.server;

import java.sql.SQLException;

import com.sun.net.httpserver.BasicAuthenticator;

public class UserAuthenticator extends BasicAuthenticator {

    /**
     * Constructor for UserAuthenticator
     * 
     * @param realm realm for the user authenticator 
     */

    public UserAuthenticator(String realm) {
        super(realm);
    }

    /**
     * Method to check the credentials
     * 
     * @param username username for the user
     * @param password password for the user
     * @return boolean returns true if the credentials are valid
     */

    @Override
    public boolean checkCredentials(String username, String password) {

        MessagesUsersDB dbInstance = MessagesUsersDB.getInstance();
        Boolean valid = false;

        try {
            valid = dbInstance.authenticateUser(username, password);
            
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return valid;
    }

    /**
     * Method to add a user
     * 
     * @param username username for the user
     * @param password password for the user
     * @param email email for the user
     * @param userNickname nickname for the user
     * @return boolean returns true if the user is added
     */

    public boolean addUser(String username, String password, String email, String userNickname) {

        boolean valid = false;

        MessagesUsersDB dbInstance = MessagesUsersDB.getInstance();

        try {
            valid = dbInstance.checkUser(username, userNickname);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (valid) {
            try {
                dbInstance.insertUser(username, password, email, userNickname);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return valid;
    }

}
