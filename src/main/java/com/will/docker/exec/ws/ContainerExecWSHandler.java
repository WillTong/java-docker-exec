package com.will.docker.exec.ws;

import com.alibaba.fastjson.JSONObject;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.messages.ExecCreation;
import com.will.docker.exec.util.DockerHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by will on 2017/9/14.
 */
@Component
public class ContainerExecWSHandler extends TextWebSocketHandler {
    private Map<String,ExecSession> execSessionMap=new HashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String ip=session.getAttributes().get("ip").toString();
        String containerId=session.getAttributes().get("containerId").toString();
        String width=session.getAttributes().get("width").toString();
        String height=session.getAttributes().get("height").toString();
        String execId= DockerHelper.query(ip, docker->{
            ExecCreation execCreation=docker.execCreate(containerId,new String[]{"/bin/bash"},
                    DockerClient.ExecCreateParam.attachStdin(), DockerClient.ExecCreateParam.attachStdout(), DockerClient.ExecCreateParam.attachStderr(),
                    DockerClient.ExecCreateParam.tty(true));
            return execCreation.id();
        });
        Socket socket=new Socket(ip,2375);
        socket.setKeepAlive(true);
        OutputStream out = socket.getOutputStream();
        StringBuffer pw = new StringBuffer();
        pw.append("POST /exec/"+execId+"/start HTTP/1.1\r\n");  // 请求的第一行Request-Line，需要写请求的URL(/Test/test.jsp)
        pw.append("Host: "+ip+":2375\r\n");
        pw.append("User-Agent: Docker-Client\r\n");
        pw.append("Content-Type: application/json\r\n");
        pw.append("Connection: Upgrade\r\n");
        JSONObject obj = new JSONObject();
        obj.put("Detach",false);
        obj.put("Tty",true);
        String json=obj.toJSONString();
        pw.append("Content-Length: "+json.length()+"\r\n");
        pw.append("Upgrade: tcp\r\n");
        pw.append("\r\n");
        pw.append(json);
        out.write(pw.toString().getBytes("UTF-8"));
        out.flush();
        InputStream inputStream=socket.getInputStream();
        byte[] bytes=new byte[1024];
        StringBuffer returnMsg=new StringBuffer();
        while(true){
            int n = inputStream.read(bytes);
            String msg=new String(bytes,0,n);
            returnMsg.append(msg);
            bytes=new byte[10240];
            if(returnMsg.indexOf("\r\n\r\n")!=-1){
                session.sendMessage(new TextMessage(returnMsg.substring(returnMsg.indexOf("\r\n\r\n")+4,returnMsg.length())));
                break;
            }
        }
        OutPutThread outPutThread=new OutPutThread(inputStream,session);
        outPutThread.start();
        execSessionMap.put(containerId,new ExecSession(ip,containerId,socket,outPutThread));
        DockerHelper.execute(ip,docker->{
            docker.execResizeTty(execId,Integer.parseInt(height),Integer.parseInt(width));
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        String containerId=session.getAttributes().get("containerId").toString();
        ExecSession execSession=execSessionMap.get(containerId);
        if(execSession!=null){
            execSession.getOutPutThread().interrupt();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String containerId=session.getAttributes().get("containerId").toString();
        ExecSession execSession=execSessionMap.get(containerId);
        OutputStream out = execSession.getSocket().getOutputStream();
        out.write(message.asBytes());
        out.flush();
    }
}
