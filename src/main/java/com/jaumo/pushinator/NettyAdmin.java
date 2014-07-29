package com.jaumo.pushinator;

import com.corundumstudio.socketio.parser.Packet;
import com.corundumstudio.socketio.parser.PacketType;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

import java.io.*;
import java.util.HashMap;

public class NettyAdmin {


    Storage storage;
    Config config;
    Logger logger;
    EventLoopGroup bossGroup;
    EventLoopGroup workerGroup;

    public class Initializer extends ChannelInitializer<SocketChannel> {

        @Override
        public void initChannel(SocketChannel ch) throws Exception {
            ServerHandler serverHandler = new ServerHandler();
            serverHandler.setLogger(logger);
            serverHandler.setStorage(storage);
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast(new HttpServerCodec());
            pipeline.addLast(new HttpObjectAggregator(65536));
            pipeline.addLast(serverHandler);
        }
    }

    NettyAdmin(Storage storage, Config config) {
        this.storage = storage;
        this.config = config;
    }

    public void run() throws IOException {
        logger = LoggerFactory.getLogger(Admin.class);
        bossGroup = new NioEventLoopGroup(0);
        workerGroup = new NioEventLoopGroup(0);
        ServerBootstrap b = new ServerBootstrap();

        boolean reuseAddress = true;
        boolean tcpNoDelay = true;
        int soLinger = 0;
        logger.info("Socket Option backlog: " + config.backlogQueueSize);
        logger.info("Socket Option SO_REUSEADDRESS: " + reuseAddress);
        logger.info("Socket Option TCP_NODELAY: " + tcpNoDelay);
        logger.info("Socket Option SO_LINGER: " + soLinger);

        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new Initializer())
                .option(ChannelOption.SO_BACKLOG, config.backlogQueueSize)
                .option(ChannelOption.SO_REUSEADDR, reuseAddress)
                .option(ChannelOption.TCP_NODELAY, tcpNoDelay)
                .localAddress(config.adminAddress, config.adminPort);

        b.bind().syncUninterruptibly();
    }

    public void stop() {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }

    /**
     * Handles handshakes and messages
     */
    public static class ServerHandler extends SimpleChannelInboundHandler<Object> {

        Logger logger;
        Storage storage;

        public void setStorage(Storage storage) {
            this.storage = storage;
        }

        public void setLogger(Logger logger) {
            this.logger = logger;
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof FullHttpRequest) {
                handleHttpRequest(ctx, (FullHttpRequest) msg);
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        class Stats {
            public int clients;
            public int users;

            public int getClients() {
                return clients;
            }

            public int getUsers() {
                return users;
            }
        }

        private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
            // Handle a bad request.
            if (!req.getDecoderResult().isSuccess()) {
                sendResponse(ctx, req, BAD_REQUEST);
                return;
            }

            // Allow only GET methods.
            if (req.getMethod() == GET) {
                // Send the demo page and favicon.ico
                if ("/".equals(req.getUri())) {
                    sendResponse(ctx, req, OK, "Foo");
                    return;
                }
                else if ("/favicon.ico".equals(req.getUri())) {
                    sendResponse(ctx, req, NOT_FOUND);
                    return;
                }
                else if ("/dude".equals(req.getUri())) {
                    sendResponse(ctx, req, OK, "Sweet.");
                    return;
                }
                else if ("/stats".equals(req.getUri())) {
                    Stats stats = new Stats();
                    stats.users = storage.users.size();
                    stats.clients = storage.clients.size();
                    Gson gson = new Gson();
                    sendResponse(ctx, req, OK, gson.toJson(stats));
                    return;
                }
                sendResponse(ctx, req, NOT_FOUND);
                return;
            }
            else if (req.getMethod() == POST) {
                if ("/user/register".equals(req.getUri())) {
                    handleUserRegister(ctx, req);
                    return;
                }
                else if ("/user/send".equals(req.getUri())) {
                    handleUserSend(ctx, req);
                    return;
                }
                else if ("/dude".equals(req.getUri())) {
                    sendResponse(ctx, req, OK, "Sweet.");
                    return;
                }
                sendResponse(ctx, req, NOT_FOUND);
                return;
            }
            sendResponse(ctx, req, FORBIDDEN);
        }

        private void sendResponse(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status) {
            sendResponse(ctx, req, status, null);
        }

        private void sendResponse(ChannelHandlerContext ctx, FullHttpRequest req, HttpResponseStatus status, String body) {
            FullHttpResponse res;
            if (body == null) {
                body = status.toString();
            }
            boolean isKeepAlive = HttpHeaders.isKeepAlive(req);
            ByteBuf content = Unpooled.copiedBuffer(body, CharsetUtil.UTF_8);
            res = new DefaultFullHttpResponse(HTTP_1_1, status, content);
            res.headers().add("Access-Control-Allow-Origin", "*");
            res.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");

            if (isKeepAlive) {
                // Add 'Content-Length' header only for a keep-alive connection.
                res.headers().set(CONTENT_LENGTH, res.content().readableBytes());
                // Add keep alive header as per:
                // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
                res.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
            }

            // Send the response and close the connection if necessary.
            ctx.write(res);
            if (!isKeepAlive) {
                ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.debug(cause.getMessage());
            ctx.close();
        }

        public void handleUserRegister(ChannelHandlerContext ctx, FullHttpRequest req) {
            try {
                String requestBody = req.content().toString(CharsetUtil.UTF_8);
                RegisterRequest data = new Gson().fromJson(requestBody, RegisterRequest.class);
                if (data.hash != null && data.userId != null && data.userId > 0) {
                    logger.debug("Register user {} with hash {}", data.userId, data.hash);
                    String response = "OK. Registered User.";
                    sendResponse(ctx, req, OK, response);

                    User user = storage.getUser(data.userId);
                    if (user != null) {
                        user.setHash(data.hash);
                    } else {
                        storage.addUser(data.userId, new User(data.hash));
                    }
                } else {
                    sendResponse(ctx, req, BAD_REQUEST, "Missing userId or hash");
                }
            } catch (JsonSyntaxException e) {
                sendResponse(ctx, req, BAD_REQUEST, "Error parsing request");
            }
        }


        private JSONObject getRequestObject(String body) {
            try {
                return new JSONObject(body);
            } catch (JSONException e) {
                //
                e.printStackTrace();
                logger.error(e.getMessage() + "; " + body);
            }

            return null;
        }

        public void handleUserSend(ChannelHandlerContext ctx, FullHttpRequest req) {
            String requestBody = req.content().toString(CharsetUtil.UTF_8);
            JSONObject json = getRequestObject(requestBody);
            if (json == null) {
                sendResponse(ctx, req, BAD_REQUEST, "Error parsing request");
                return;
            }

            int userId;
            try {
                userId = json.getInt("userId");
            } catch (JSONException e) {
                sendResponse(ctx, req, BAD_REQUEST, "Invalid userId");
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
                    sendResponse(ctx, req, BAD_REQUEST, "Message must be either a string or a JSON object");
                    return;
                }
            }

            if (userId > 0 && message != null) {
                String response = "OK. Sent message.";
                sendResponse(ctx, req, OK, response);

                User user = storage.getUser(userId);
                if (user != null) {
                    logger.debug("Sent message to user {}: {}", userId, message);
                    Packet packet = new Packet(PacketType.MESSAGE);
                    packet.setData(message);
                    user.send(packet);
                }
            } else {
                sendResponse(ctx, req, BAD_REQUEST, "UserId or message missing");
            }
        }
    }

    class RegisterRequest {
        Integer userId;
        String hash;
    }

}
