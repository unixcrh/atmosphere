/*
 * Copyright 2012 Jeanfrancois Arcand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.container;

import org.atmosphere.container.version.JSR356WebSocket;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.websocket.WebSocket;
import org.atmosphere.websocket.WebSocketEventListener;
import org.atmosphere.websocket.WebSocketProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.atmosphere.cpr.ApplicationConfig.ALLOW_QUERYSTRING_AS_REQUEST;

public class JSR356Endpoint extends Endpoint {

    private static final Logger logger = LoggerFactory.getLogger(JSR356Endpoint.class);

    private final WebSocketProcessor webSocketProcessor;
    private AtmosphereRequest request;
    private final AtmosphereFramework framework;
    private WebSocket webSocket;
    private final int webSocketWriteTimeout;

    public JSR356Endpoint(AtmosphereFramework framework, WebSocketProcessor webSocketProcessor) {
        this.framework = framework;
        this.webSocketProcessor = webSocketProcessor;

        String s = framework.getAtmosphereConfig().getInitParameter(ApplicationConfig.WEBSOCKET_IDLETIME);
        if (s != null) {
            webSocketWriteTimeout = Integer.valueOf(1);
        } else {
            webSocketWriteTimeout = -1;
        }

        s = framework.getAtmosphereConfig().getInitParameter(ApplicationConfig.WEBSOCKET_BUFFER_SIZE);
        if (s != null) {
            //TODO
        }
    }

    @Override
    public void onOpen(Session session, EndpointConfig endpointConfig) {
        webSocket = new JSR356WebSocket(session, framework.getAtmosphereConfig());

        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Connection", "Upgrade");
        headers.put("Upgrade", "websocket");

        StringBuffer pathInfo = new StringBuffer("/");
        for (Map.Entry<String, String> e : session.getPathParameters().entrySet()) {
            pathInfo.append(e.getValue());
        }

        try {

            request = new AtmosphereRequest.Builder()
                    .requestURI(session.getRequestURI().toString())
                    .headers(headers)
                    .contextPath(framework.getServletContext().getContextPath())
                    .pathInfo(pathInfo.toString())
                    .build()
                    .queryString(session.getQueryString());


            // TODO: Fix this crazy code.
            framework.addInitParameter(ALLOW_QUERYSTRING_AS_REQUEST, "false");

            webSocketProcessor.open(webSocket, request, AtmosphereResponse.newInstance(framework.getAtmosphereConfig(), request, webSocket));

            framework.addInitParameter(ALLOW_QUERYSTRING_AS_REQUEST, "true");

        } catch (Throwable e) {
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, e.getMessage()));
            } catch (IOException e1) {
                logger.trace("", e);
            }
            logger.error("", e);
            return;
        }

        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String s) {
                webSocketProcessor.invokeWebSocketProtocol(webSocket, s);
            }
        });

        session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer bb) {
                byte[] b = new byte[bb.limit()];
                bb.get(b);
                webSocketProcessor.invokeWebSocketProtocol(webSocket, b, 0, b.length);
            }
        });
    }

    @Override
    public void onClose(javax.websocket.Session session, javax.websocket.CloseReason closeCode) {
        request.destroy();
        webSocketProcessor.close(webSocket, closeCode.getCloseCode().getCode());
    }

    @Override
    public void onError(javax.websocket.Session session, java.lang.Throwable t) {
        logger.error("", t);
        webSocketProcessor.notifyListener(webSocket,
                new WebSocketEventListener.WebSocketEvent<Throwable>(t, WebSocketEventListener.WebSocketEvent.TYPE.EXCEPTION, webSocket));
    }
}
