/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.jaumo.pushinator.httpclient;

import com.jaumo.pushinator.User;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;

public class HttpClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    User user;
    Logger logger;
    String url;

    HttpClientHandler(User user, Logger logger, String url) {
        this.user = user;
        this.logger = logger;
        this.url = url;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
        if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;

            HttpResponseStatus status = response.getStatus();
            if (status.code() >= 200 && status.code() < 300) {
                logger.debug("Connect callback success {}", url);
                user.setCallbackSent();
            }
            else {
                logger.error("Connect callback error {} {}", url, status.code());
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error(cause.getMessage());
        ctx.close();
    }
}
