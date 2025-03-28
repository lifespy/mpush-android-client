package com.kooritea.mpush;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import static com.kooritea.mpush.MainActivity.EXTRA_MESSAGE;

public class SocketClient extends WebSocketClient {
    private MsgNotificationService context;

    private static final String AUTH="AUTH";
    private static final String LOGIN="LOGIN";
    private static final String MESSAGE="MESSAGE";

    public boolean isAlive = false;

    private boolean isReload;

    private boolean needReload = true;

    private String toastData;
    public String url;
    public String token;
    public String device;

    private Handler handler;

    private int reConnectionSleepTime;

    private boolean isOpen=false;

    SocketClient(MsgNotificationService context, String serverUri, String token, String device,  boolean isReload) {
        super(URI.create(serverUri));
        this.context = context;
        this.isReload = isReload;
        this.token = token;
        this.device = device;
        this.url = serverUri;
        reConnectionSleepTime = 5000;
        setConnectionLostTimeout(300000);
        connect();
    }
    @Override
    public void onOpen(ServerHandshake handshakedata) {
        log( "opened connection" );
        isOpen = true;
        send(this.token);
        reConnectionSleepTime = 5000;
    }
    public void onMessage(String messageStr) {
        try{
            JSONObject message = new JSONObject(messageStr);
            switch(message.getString("cmd")){
                case SocketClient.AUTH:
                    JSONObject data = message.getJSONObject("data");
                    if(data.getInt("code") == 200){
                        log(  "token验证成功");
                        send(this.device);
                    }
                    else{
                        log(data.getString("msg"));
                        toast(data.getString("msg"));
                    }
                    break;
                case SocketClient.LOGIN:
                    log(  "登录成功： " + message.getJSONObject("data").getString("name"));
                    if(!isReload){
                        toast("消息服务已在后台运行");
                    }
                    sendHeart();
                    isAlive = true;
                    break;
                case SocketClient.MESSAGE:
                    log(  "收到消息");
                    JSONArray msgList = message.getJSONObject("data").getJSONArray("msgList");
                    JSONObject reply = new JSONObject();
                    reply.put("cmd","SENDMSG_CB");
                    JSONArray midList = new JSONArray();
                    for(int i=0;i<msgList.length();i++){
                        log(msgList.getJSONObject(i).toString());
                        String mid = msgList.getJSONObject(i).getString("mid");
                        midList.put(mid);
                        context.localMsgManager.saveLocalMsglist(msgList.getJSONObject(i).toString());
                        Message m = new Message(msgList.getJSONObject(i));
                        context.pushMsg(m);
                    }
                    JSONObject replyData = new JSONObject();
                    replyData.put("midList",midList);
                    reply.put("data",replyData);
                    log(reply.toString());
                    send(reply.toString());
                    context.setMainIsPull(false);
                    context.sendBroadcast(new Intent("com.kooritea.mpush.MESSAGE"));
                    //收到新消息，设置为拉取
                    break;
            }
        }catch(Exception e){chrome://vivaldi-webui/startpage?section=Speed-dials&activeSpeedDialIndex=0&background-color=#2e2e2e
            e.printStackTrace();
        }
    }


    private void sendHeart(){
        log("try send ping");
        if(!needReload) return;
        try{
            if(isAlive) {
                log("send ping");
                sendPing();
            }
            context.cachedThreadPool.execute(new Runnable() {
                public void run() {
                try{
                    Thread.sleep(60000);
                }catch (Exception e ){
                    Log.e("send Heart error",e.toString());
                }
//                Looper.prepare();
                sendHeart();
//                Looper.loop();
                }
            });

        }catch(Exception e){
            Log.e("send Heart error",e.toString());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        isAlive = false;
        log(  "Connection closed by " + ( remote ? "remote peer" : "us" ) );
        context.onSocketClose(this);
    }
    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
        if(!isOpen){//在打开前出错
            toast("连接websocket服务器出错\n" + ex.toString());
        }
        isAlive = false;
        if(reConnectionSleepTime < 60000){
            reConnectionSleepTime += 5000;
        }
    }
    public void close(boolean needReload) {
        this.needReload = needReload;
        close();
    }
    private void log(String text){
        if(BuildConfig.DEBUG){
            Log.e("websocket",text);
        }
    }
    public boolean getNeedReload(){
        return needReload;
    }
//    public String getUrl(){
//        return url;
//    }
//    public String getToken(){
//        return token;
//    }
//    public String getDevice(){
//        return device;
//    }
    public int getReConnectionSleepTime(){
        return reConnectionSleepTime;
    }
    private void toast(String text){
        toastData = text;
        handler=new Handler(Looper.getMainLooper());
        handler.post(new Runnable(){
            public void run(){
                Toast toast = Toast.makeText(context, null, Toast.LENGTH_LONG);
                toast.setText(toastData);
                toast.show();
            }
        });
    }
}
