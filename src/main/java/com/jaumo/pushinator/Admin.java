package com.jaumo.pushinator;

import com.corundumstudio.socketio.parser.Packet;
import com.corundumstudio.socketio.parser.PacketType;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class Admin {

    Storage storage;
    Config config;
    HttpServer server;

    Admin(Storage storage, Config config) {
        this.storage = storage;
        this.config = config;
    }

    public void run() throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.adminAddress, config.adminPort), 0);
        server.createContext("/user/register", new UserRegister());
        server.createContext("/user/send", new UserSend());
        server.setExecutor(null); // creates a default executor
        server.start();
    }

    public void stop() {
        server.stop(0);
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
                    String response = "OK. Registered User.";
                    t.sendResponseHeaders(200, response.length());
                    OutputStream os = t.getResponseBody();
                    os.write(response.getBytes());
                    os.close();

                    User user = storage.getUser(data.userId);
                    if (user != null) {
                        user.setHash(data.hash);
                    }
                    else {
                        storage.addUser(data.userId, new User(data.hash));
                    }
                }
                else {
                    error(t);
                }
            }
            catch (JsonSyntaxException e) {
                error(t);
            }
        }
    }

    class SendRequest {
        Integer userId;
        String message;
    }

    class UserSend implements HttpHandler {

        private void error(HttpExchange t) throws IOException {
            String response = "Missing userId or message in data";
            t.sendResponseHeaders(400, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

        public void handle(HttpExchange t) throws IOException {
            try {
                t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                SendRequest data = new Gson().fromJson(new InputStreamReader(t.getRequestBody()), SendRequest.class);
                if (data.userId != null && data.userId > 0 && data.message != null && data.message.length() > 0) {
                    String response = "OK. Sent message.";
                    t.sendResponseHeaders(200, response.length());
                    OutputStream os = t.getResponseBody();
                    os.write(response.getBytes());
                    os.close();

                    User user = storage.getUser(data.userId);
                    if (user != null) {
                        Packet packet = new Packet(PacketType.MESSAGE);
                        packet.setData(data.message);
                        user.send(packet);
                    }
                }
                else {
                    error(t);
                }
            }
            catch (JsonSyntaxException e) {
                error(t);
            }
        }
    }

}
