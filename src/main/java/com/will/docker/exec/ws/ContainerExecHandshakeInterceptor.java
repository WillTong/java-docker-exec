package com.will.docker.exec.ws;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Map;

/**
 * Created by will on 2017/9/14.
 */
public class ContainerExecHandshakeInterceptor extends HttpSessionHandshakeInterceptor {
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) throws Exception {
        if (request.getHeaders().containsKey("Sec-WebSocket-Extensions")) {
            request.getHeaders().set("Sec-WebSocket-Extensions", "permessage-deflate");
        }
        String ip = ((ServletServerHttpRequest) request).getServletRequest().getParameter("ip");
        String containerId = ((ServletServerHttpRequest) request).getServletRequest().getParameter("containerId");
        String width = ((ServletServerHttpRequest) request).getServletRequest().getParameter("width");
        String height = ((ServletServerHttpRequest) request).getServletRequest().getParameter("height");
        attributes.put("ip",ip);
        attributes.put("containerId",containerId);
        attributes.put("width",width);
        attributes.put("height",height);
        return super.beforeHandshake(request, response, wsHandler, attributes);
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception ex) {
        super.afterHandshake(request, response, wsHandler, ex);
    }
}