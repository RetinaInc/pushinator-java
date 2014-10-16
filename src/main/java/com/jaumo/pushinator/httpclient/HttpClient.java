package com.jaumo.pushinator.httpclient;

import com.jaumo.pushinator.Admin;
import com.jaumo.pushinator.Config;
import com.jaumo.pushinator.User;
import io.netty.handler.ssl.SslContext;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.net.URI;
import java.net.URISyntaxException;

public class HttpClient {

    Config config;
    Logger logger;
    EventLoopGroup workerGroup;

    public HttpClient(Config config) {
        this.config = config;
        logger = LoggerFactory.getLogger(Admin.class);
        workerGroup = new NioEventLoopGroup(0);
    }

    public void sendCallback(String url, User user) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
            String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
            int port = uri.getPort();
            if (port == -1) {
                if ("http".equalsIgnoreCase(scheme)) {
                    port = 80;
                } else if ("https".equalsIgnoreCase(scheme)) {
                    port = 443;
                }
            }

            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                logger.error("Only HTTP(S) is supported for callback {}", url);
                return;
            }

            // Configure SSL context if necessary.
            final boolean ssl = "https".equalsIgnoreCase(scheme);
            final SslContext sslCtx;
            if (ssl) {
                sslCtx = SslContext.newClientContext(InsecureTrustManagerFactory.INSTANCE);
            } else {
                sslCtx = null;
            }

            // Configure the client.
            Bootstrap b = new Bootstrap();
            b.group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new HttpClientInitializer(sslCtx, user, logger, url));

            // Make the connection attempt.
            Channel ch = b.connect(host, port).sync().channel();

            // Prepare the HTTP request.
            HttpRequest request = new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1, HttpMethod.GET, url);
            request.headers().set(HttpHeaders.Names.HOST, host);
            request.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
//            request.headers().set(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);

            // Send the HTTP request.
            ch.writeAndFlush(request);

            // Wait for the server to close the connection.
            ch.closeFuture().sync();
        } catch (URISyntaxException e) {
            logger.warn("Callback URL invalid {}", url);
        } catch (InterruptedException e) {
            logger.warn("Error executinbg callback {}", e.getMessage());
        } catch (SSLException e) {
            logger.warn("Error executinbg callback {}", e.getMessage());
        }
    }

    public void stop() {
        workerGroup.shutdownGracefully();
    }

}
