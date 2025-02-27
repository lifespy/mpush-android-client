package com.kooritea.mpush;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import org.java_websocket.client.WebSocketClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MsgNotificationService extends Service {
    private IBinder iBinder;
    private SocketClient client;
    private boolean mainIsPull = false; //ui线程是否已经拉取通知
    public ExecutorService cachedThreadPool;
//    private List<String> notifIds = new ArrayList<>();//储存通知的id
    private String toastData;
    private Handler handler;
    private SettingManager settingManager = new SettingManager(this);
    public LocalMsgManager localMsgManager = new LocalMsgManager(this);
    public class MsgNotifBinder extends MsgNotifService.Stub {

        //推送一条通知到通知栏
//        public void pushMsg(String title, String content, String time){
//            MsgNotificationService.this.pushMsg(title,content,time);
//        }
        public void cancelNotif(){
            MsgNotificationService.this.cancelNotif();
        }
        public void reConnection(){
            MsgNotificationService.this.reConnection();
        }
//        public boolean pullMsg(){
//            if(MsgNotificationService.this.mainIsPull){
//                //不需要拉取
//                return true;
//            }
//            else{
//                //需要拉取
//                MsgNotificationService.this.mainIsPull = true;
//                return false;
//            }
//        }
        public void exit(int status){
            System.exit(status);
        }

        @Override
        public void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat, double aDouble, String aString) {
            // Does nothing
        }
    }

    public MsgNotificationService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if(settingManager.getSetting("URL") != null){
            client = new SocketClient(this, settingManager.getSetting("URL"), settingManager.getSetting("TOKEN") ,settingManager.getSetting("DEVICE"), false);
        }
        Log.e("service","create");
        cachedThreadPool = Executors.newCachedThreadPool();
        AlarmManager aManager=(AlarmManager)getSystemService(Service.ALARM_SERVICE);
        Intent intent=new Intent(MsgNotificationService.this,CheckLiveReceiver.class);
        intent.setAction("com.kooritea.mpush.LIVE");
        PendingIntent pi=PendingIntent.getBroadcast(MsgNotificationService.this,0,intent,0);
        aManager.setRepeating(AlarmManager.RTC_WAKEUP,System.currentTimeMillis(),30000,pi);
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        int retVal = super.onStartCommand(intent, flags, startId);
        Log.e("service","onStartCommand");
        return retVal;
    }
    @Override
    public IBinder onBind(Intent intent) {
        if(iBinder == null){
            iBinder = new MsgNotifBinder();
        }
        return iBinder;
    }
    public void pushMsg(Message msg){
        NotificationCompat.Builder builder =  new NotificationCompat.Builder(this);
        builder.setSmallIcon(R.mipmap.ic_launcher);
        switch(msg.getStatus()){
            case 1:
                builder.setContentTitle(msg.getTitle());
                break;
            case 2:
                builder.setContentTitle("消息通知");
                builder.setContentText(msg.getContent());
                break;
            case 3:
                builder.setContentTitle(msg.getTitle());
                builder.setContentText(msg.getContent());
                break;
        }
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("title", msg.getTitle());
        intent.putExtra("content",msg.getContent());
        intent.putExtra("time",msg.getTime());
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(intent);
        PendingIntent pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        builder.setContentIntent(pendingIntent);

        //悬浮窗
        builder.setDefaults(~0);
        builder.setPriority(Notification.PRIORITY_HIGH);

        NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        int id = (int) (System.currentTimeMillis());
//        notifIds.add(String.valueOf(id));
        nm.notify(id,notification);
    }
    public void cancelNotif(){
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//        for(int i=0;i<notifIds.size();i++){
//            mNotificationManager.cancel(Integer.parseInt(notifIds.get(i)));
//        }
//        notifIds.clear();
        mNotificationManager.cancelAll();

    }
    public void onSocketClose(SocketClient ws){
        final SocketClient wws = ws;
        try{
            ws.close();
            if(ws.getNeedReload()){
                Log.e("websocket",ws.getReConnectionSleepTime() + "s后重连" + ws.url);
            }
            cachedThreadPool.execute(new Runnable() {
                public void run() {
                    try{
                        Thread.sleep(wws.getReConnectionSleepTime());
                    }catch (Exception e ){
                        Log.e("SocketService",e.toString());
                    }
//                    Looper.prepare();
                    if(wws.getNeedReload()){
                        wws.reconnect();
                    }
//                    Looper.loop();
                }
            });
        }catch (Exception e ){

        }
    }
    public void reConnection(){
        Log.e("reConnection","重新设置");
        Log.e("URL",settingManager.getSetting("URL"));
        Log.e("TOKEN",settingManager.getSetting("TOKEN"));
        Log.e("DEVICE",settingManager.getSetting("DEVICE"));
        client.close(false);
        client = new SocketClient(MsgNotificationService.this, settingManager.getSetting("URL"), settingManager.getSetting("TOKEN") ,settingManager.getSetting("DEVICE"), false);
        Runtime.getRuntime().gc();

    }
    public void setMainIsPull(boolean status){
        mainIsPull = status;
    }
    private void toast(String text){
        toastData = text;
        handler=new Handler(Looper.getMainLooper());
        handler.post(new Runnable(){
            public void run(){
                Toast toast = Toast.makeText(MsgNotificationService.this, null, Toast.LENGTH_LONG);
                toast.setText(toastData);
                toast.show();
            }
        });
    }

//    public void pushMsg(String title, String content){
//        HashMap<String,String> map = new HashMap<>();
//        map.put("title",title);
//        map.put("content",content);
//        msgList.add(map);
//
//        refresh();
//    }
//    private void refresh(){
//        if(msgList.size() == 0)return;
//        NotificationCompat.Builder builder =  new NotificationCompat.Builder(this);
//        builder.setSmallIcon(R.mipmap.ic_launcher);
//        if(msgList.size() > 1){
//            NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
//            String msg="";
//            for(int i=0;i<msgList.size();i++){
//                msg += msgList.get(i).get("title") + "\n";
//            }
//            bigTextStyle.setBigContentTitle(msgList.size() + " new message");
//            Log.e("test",msg);
//            bigTextStyle.bigText(msg);
//            bigTextStyle.setSummaryText("SummaryText");
//            builder.setStyle(bigTextStyle);
//        }else{
//            builder.setContentTitle(msgList.get(0).get("title"));
//            builder.setContentText(msgList.get(0).get("content"));
//        }
//        Intent intent = new Intent(this, MainActivity.class);
//        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
//        stackBuilder.addParentStack(MainActivity.class);
//        stackBuilder.addNextIntent(intent);
//        PendingIntent pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
//
//        builder.setContentIntent(pendingIntent);
//        NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
//        nm.notify(0,builder.build());
//    }
}
