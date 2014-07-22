package com.jaumo.pushinator;

import com.corundumstudio.socketio.parser.Packet;
import com.corundumstudio.socketio.parser.PacketType;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;

public class Admin {

    Storage storage;
    Config config;
    HttpServer server;
    Logger logger;

    Admin(Storage storage, Config config) {
        this.storage = storage;
        this.config = config;
    }

    public void run() throws IOException {
        logger = LoggerFactory.getLogger(Admin.class);
        server = HttpServer.create(new InetSocketAddress(config.adminAddress, config.adminPort), 0);
        server.createContext("/user/register", new UserRegister());
        server.createContext("/user/send", new UserSend());
        server.createContext("/dude", new Ping());
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    class Ping implements HttpHandler {

        public void handle(HttpExchange t) throws IOException {
            logger.debug("Ping");
            String response = "Sweet.";
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    class RegisterRequest {
        Integer userId;
        String hash;
    }

    class UserRegister implements HttpHandler {

        private void error(HttpExchange t) throws IOException {
            String response = "Missing userId or hash";
            t.sendResponseHeaders(400, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

        public void handle(HttpExchange t) throws IOException {
            try {
                t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                RegisterRequest data = new Gson().fromJson(new InputStreamReader(t.getRequestBody()), RegisterRequest.class);
                if (data.hash != null && data.userId != null && data.userId > 0) {
                    logger.debug("Register user {} with hash {}", data.userId, data.hash);
                    String response = "OK. Registered User.";
                    t.sendResponseHeaders(200, response.length());
                    OutputStream os = t.getResponseBody();
                    os.write(response.getBytes());
                    os.close();

                    User user = storage.getUser(data.userId);
                    if (user != null) {
                        user.setHash(data.hash);
                    } else {
                        storage.addUser(data.userId, new User(data.hash));
                    }
                } else {
                    error(t);
                }
            } catch (JsonSyntaxException e) {
                error(t);
            }
        }
    }

    class UserSend implements HttpHandler {

        private void error(HttpExchange t, String response) throws IOException {
            t.sendResponseHeaders(400, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

        private JSONObject getRequestObject(InputStream body) {
            try {
                BufferedReader streamReader = new BufferedReader(new InputStreamReader(body, "UTF-8"));
                StringBuilder responseStrBuilder = new StringBuilder();

                String inputStr;
                while ((inputStr = streamReader.readLine()) != null)
                    responseStrBuilder.append(inputStr);

                return new JSONObject(responseStrBuilder.toString());

            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            return null;
        }

        public void handle(HttpExchange t) throws IOException {
            t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            JSONObject json = getRequestObject(t.getRequestBody());
            if (json == null) {
                error(t, "Error parsing request");
                return;
            }

            Integer userId;
            try {
                userId = json.getInt("userId");
            } catch (JSONException e) {
                error(t, "Invalid userId");
                return;
            }
            String message = "";

            try {
                JSONObject messageObject = json.getJSONObject("message");
                message = messageObject.toString();
            } catch (JSONException e) {
                try {
                    message = json.getString("message");
                } catch (JSONException e2) {
                    error(t, "Message must be either a string or a JSON object");
                }
            }

            if (userId != null && userId > 0 && message != null) {
                String response = "OK. Sent message.";
                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();

                User user = storage.getUser(userId);
                if (user != null) {
                    logger.debug("Sent message to user {}: {}", userId, message);
                    Packet packet = new Packet(PacketType.MESSAGE);
                    packet.setData(message);
                    user.send(packet);
                }
            } else {
                error(t, "UserId or message missing");
            }
        }
    }

}
