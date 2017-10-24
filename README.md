# 使用xtermjs和java模拟terminal连接docker容器

官方的客户端是用go写的，这里使用java实现，底层使用docker-client连接docker的remote api。

## 介绍
前端用xtermjs模仿terminal，用websocket和后端保持长连接通信。后端使用spring websocket处理websocket请求。与docker部分调用docker remote api的/exec/continerId/start打开bash。

## xtermjs
- 下载  

https://xtermjs.org/docs/guides/download

- 加载xtermjs
```html
    <link rel="stylesheet" href="/webjars/xterm/2.9.2/dist/xterm.css" />
    <script type="application/javascript" src="/webjars/xterm/2.9.2/dist/xterm.js"></script>
    <script type="application/javascript" src="/webjars/xterm/2.9.2/dist/addons/attach/attach.js"></script>
```
- 声明div
```html
<div style="width:1000px;" id="xterm"></div>
```

- 执行xtermjs
```javascript
    var term = new Terminal({
        cursorBlink: false,
        cols: 100,
        rows: 50
    });
    term.open(document.getElementById('xterm'));
    var socket = new WebSocket('ws://localhost:8080/ws/container/exec?width=100&height=50&ip=192.168.93.129&containerId=5f045d86d0f9b5b0ba6d747b82d688b939a87ae85e71e9ed947dcb37a6f34dfc');
    term.attach(socket);
    term.focus();
```
这里通过websocket连接8080端口。192.168.93.129是docker的宿主机，5f045d86d0f9b5b0ba6d747b82d688b939a87ae85e71e9ed947dcb37a6f34dfc是容器的长id。

## docker宿主机开启remote api端口
这里使用centos7.2
```cmd
vim /usr/lib/systemd/system/docker.service
```
修改执行命令
```text
ExecStart=/usr/bin/dockerd -H unix:///var/run/docker.sock -H 0.0.0.0:2375
```
重新加载服务
```cmd
systemctl daemon-reload
```

## spring加载websocket
- 注册websocket，这里注册了ContainerExecWSHandler处理类，ContainerExecHandshakeInterceptor拦截器
```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Bean
    public ServerEndpointExporter serverEndpointExporter(ApplicationContext context) {
        return new ServerEndpointExporter();
    }

    @Bean
    public ContainerExecWSHandler containerExecWSHandler(){
        return new ContainerExecWSHandler();
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(containerExecWSHandler(), "/ws/container/exec").addInterceptors(new ContainerExecHandshakeInterceptor()).setAllowedOrigins("*");
    }
}
```

- websocket拦截器负责接收传参
```java
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
```
## websocket处理类
负责建立前端与remote api的通信
- 创建一个exec命令
```java
ExecCreation execCreation=docker.execCreate(containerId,new String[]{"/bin/bash"},
        DockerClient.ExecCreateParam.attachStdin(), DockerClient.ExecCreateParam.attachStdout(), DockerClient.ExecCreateParam.attachStderr(),
        DockerClient.ExecCreateParam.tty(true));
return execCreation.id();
```
- 通过tcp连接docker remote api
```java
Socket socket=new Socket(ip,2375);
socket.setKeepAlive(true);
OutputStream out = socket.getOutputStream();
StringBuffer pw = new StringBuffer();
pw.append("POST /exec/"+execId+"/start HTTP/1.1\r\n");
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
```
socket模拟http发送请求。header中有Connection: Upgrade和Upgrade: tcp，告诉docker remote api这是一个长连接，这样就可以通过socket对象获得输入输出流，从而保持通信。
- 过滤docker remote api的返回信息

握手成功的信息不需要返回给xtermjs中显示，所以这段代码用来过滤掉返回值
```java
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
```
当监听到连续两个\r\n则证明header头传输成功。由于每次返回的内容都是片段，所以需要returnMsg来把每次返回的信息收集起来。  

- 信息交互

获得session中的socket，发送信息
```java
ExecSession execSession=execSessionMap.get(containerId);
OutputStream out = execSession.getSocket().getOutputStream();
out.write(message.asBytes());
out.flush();
```
接收信息
```java
byte[] bytes=new byte[1024];
while(!this.isInterrupted()){
    int n=inputStream.read(bytes);
    String msg=new String(bytes,0,n);
    session.sendMessage(new TextMessage(msg));
    bytes=new byte[1024];
}
```


