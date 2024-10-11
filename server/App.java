package com.server;

import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpContext;

public class App {

    /**
     * Main method to start the server
     * 
     * @param args keystore and password
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        final UserAuthenticator uAuthenticator = new UserAuthenticator("registration");

        try {

            HttpsServer server = HttpsServer.create(new InetSocketAddress(8001), 0);
            MessagesUsersDB db = MessagesUsersDB.getInstance();
            db.open("C:\\Users\\danim\\Documents\\Ohjelmointi3\\group-0102-project\\server\\finalDB.db");

            HttpContext context1 = server.createContext("/info", new Server());
            HttpContext context2 = server.createContext("/paths", new PathServer());
            HttpContext context3 = server.createContext("/topfive", new TopFiveServer());
            server.createContext("/registration", new HandleRegistrations(uAuthenticator));
            context1.setAuthenticator(uAuthenticator);
            context2.setAuthenticator(uAuthenticator);
            context3.setAuthenticator(uAuthenticator);

            SSLContext sslContext = myServerSSLContext(args[0], args[1]);

            server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                public void configure(HttpsParameters params) {
                    InetSocketAddress remote = params.getClientAddress();
                    SSLContext c = getSSLContext();
                    SSLParameters sslparams = c.getDefaultSSLParameters();

                    params.setSSLParameters(sslparams);
                }
            });

            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Method to create the SSL context
     * 
     * @param keystore keystore
     * @param password password
     * @return SSLContext
     * @throws Exception
     */

    private static SSLContext myServerSSLContext(String keystore, String password) throws Exception {

        SSLContext ctx = SSLContext.getInstance("TLS");

        char[] charPassword = password.toCharArray();
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(keystore), charPassword);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, charPassword);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return ctx;

    }

}
