package com.jaumo.pushinator;

import com.corundumstudio.socketio.*;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.corundumstudio.socketio.listener.MultiTypeEventListener;
import com.jaumo.pushinator.httpclient.HttpClient;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URLEncoder;
import java.util.Date;

public class Server {

    class StartUpException extends Exception {

    }

    Logger logger;
    SocketIOServer server;
    NettyAdmin adminServer;
    HttpClient httpClient;
    Config config;

    Server() {
        config = new Config();
    }

    public void setCommandLine(String[] args) throws StartUpException {
        final CommandLineParser cmdLineGnuParser = new PosixParser();

        final Options options = Config.buildOptions();
        try {
            CommandLine commandLine = cmdLineGnuParser.parse(options, args);
            if (commandLine.hasOption("help")) {
                printUsage(options);
                throw new StartUpException();
            } else {
                config = new Config(commandLine);
            }
        } catch (ParseException parseException) {
            logger.error("Command line option: " + parseException.getMessage());
            throw new StartUpException();
        }
    }

    public void printUsage(Options options) {
        final PrintWriter writer = new PrintWriter(System.out);
        final HelpFormatter usageFormatter = new HelpFormatter();
        usageFormatter.printUsage(writer, 80, "pushinator", options);
        writer.close();
    }



    private void sendCallback(Integer userId, final User user, String hash) {
        if (config.callbackUrl != null) {
            long timestamp = new Date().getTime() - config.callbackUrlCapping * 1000;
            if (!user.didSendCallbackAfter(timestamp)) {
                try {
                    final String url = config.callbackUrl
                            .replace(":userId:", URLEncoder.encode(userId.toString(), "UTF-8"))
                            .replace(":hash:", URLEncoder.encode(hash, "UTF-8"));
                    httpClient.sendCallback(url, user);
                }
                catch (UnsupportedEncodingException e) {
                    //
                }
            }
        }
    }

    private SocketIOServer setUpSocketServer(final Storage storage) throws StartUpException {
        Configuration socketConfig = new Configuration();
        socketConfig.getSocketConfig().setReuseAddress(true);
        socketConfig.getSocketConfig().setAcceptBackLog(config.backlogQueueSize);

        logger.info("Socket Option backlog: " + socketConfig.getSocketConfig().getAcceptBackLog());
        logger.info("Socket Option SO_REUSEADDRESS: " + socketConfig.getSocketConfig().isReuseAddress());
        logger.info("Socket Option TCP_NODELAY: " + socketConfig.getSocketConfig().isTcpNoDelay());
        logger.info("Socket Option SO_LINGER: " + socketConfig.getSocketConfig().getSoLinger());
        socketConfig.setHostname(this.config.clientAddress);
        socketConfig.setPort(this.config.clientPort);
        if (this.config.useSSL) {
            socketConfig.setKeyStorePassword(this.config.sslKeyStorePassword);
            try {
                InputStream stream = new FileInputStream(this.config.sslKeyStore);
                socketConfig.setKeyStore(stream);
            } catch (FileNotFoundException e) {
                logger.error("Keystore file not found: " + this.config.sslKeyStore);
                throw new StartUpException();
            }
        }

        final SocketIOServer server = new SocketIOServer(socketConfig);
        server.addMultiTypeEventListener("auth", new MultiTypeEventListener() {
            @Override
            public void onData(SocketIOClient client, MultiTypeArgs data, AckRequest ackSender) throws Exception {
                Integer userId = data.get(0);
                String hash = data.get(1);
                if (storage.authClient(userId, hash, client)) {
                    User user = storage.getUser(userId);
                    sendCallback(userId, user, hash);
                }
            }
        }, Integer.class, String.class);
        server.addDisconnectListener(new DisconnectListener() {
            @Override
            public void onDisconnect(SocketIOClient client) {
                storage.removeClient(client);
            }
        });

        try {
            logger.info("Start client server on " + this.config.clientAddress + ":" + this.config.clientPort);
            server.start();
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new StartUpException();
        }

        return server;
    }

    public void setupAdminServer(final Storage storage) throws StartUpException {
        try {
            logger.info("Start admin server on " + this.config.adminAddress + ":" + this.config.adminPort);
            adminServer = new NettyAdmin(storage, config);
            adminServer.run();
            logger.info("Admin server started");
        } catch (IOException e) {
            logger.error(e.getMessage());
            throw new StartUpException();
        }
    }

    public void run() throws InterruptedException, StartUpException {
        try {
            logger = LoggerFactory.getLogger(Server.class);
            final Storage storage = new Storage();
            storage.setStaleUserExpireTime(config.staleUserExpireTime);
            server = setUpSocketServer(storage);
            setupAdminServer(storage);
            httpClient = new HttpClient(config);
            logger.info("Startup finished");
        } catch (StartUpException e) {
            logger.error("Server did not start");
            throw e;
        }
    }

    public void stop() {
        server.stop();
        adminServer.stop();
        httpClient.stop();
        logger.info("Shutting down");
    }

    public static void main(String[] args) throws InterruptedException {
        try {
            final Server server = new Server();
            server.setCommandLine(args);
            server.run();
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    server.stop();
                }
            });
        } catch (StartUpException e) {
            //
            System.exit(1);
        }
    }
}