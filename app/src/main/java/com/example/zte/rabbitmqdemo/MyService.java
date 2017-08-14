package com.example.zte.rabbitmqdemo;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v7.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.QueueingConsumer;

import junit.framework.Test;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by Administrator on 2017-05-24.
 */

public class MyService extends Service{
    private NotificationManager manager;
    private static final String TAG = "111";
    private static String QUEUE_NAME = "861614030247171";
    ConnectionFactory factory = new ConnectionFactory();
    Thread thread;
    Thread singleThread;
    private ThreadPoolExecutor threadPoolExecutor;
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    final Handler handler = new Handler(){
        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case 1:
                    String message = msg.getData().getString("msg");
                    Date now = new Date();
                    Intent intent = new Intent(MyService.this, TestActivity.class);
                    PendingIntent pendingIntent = PendingIntent.getActivity(MyService.this,
                            1, intent, PendingIntent.FLAG_ONE_SHOT);
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(MyService.this);
                    builder.setSmallIcon(R.mipmap.icon_logo)
                            .setContentTitle("有新的警情")
                            .setDefaults(Notification.DEFAULT_ALL)
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                            .setContentText(message);
                    Notification notification = builder.build();
                    //与上面的setAutoCancel方法重合
                    notification.flags = Notification.FLAG_AUTO_CANCEL;
                    manager.notify(0,notification);
                    SimpleDateFormat ft = new SimpleDateFormat("hh:mm:ss");
                    Log.e("111", "msg:" + message);
                    break;
                case 2:
                    String singleMessage = msg.getData().getString("singleMessage");
                    Intent intent2 = new Intent(MyService.this, TestActivity2.class);
                    PendingIntent pendingIntent2 = PendingIntent.getActivity(MyService.this,
                            1, intent2, PendingIntent.FLAG_ONE_SHOT);
                    NotificationCompat.Builder builder2 = new NotificationCompat.Builder(MyService.this);
                    builder2.setSmallIcon(R.mipmap.icon_logo)
                            .setContentTitle("点对点通信")
                            .setDefaults(Notification.DEFAULT_ALL)
                            .setContentIntent(pendingIntent2)
                            .setAutoCancel(true)
                            .setContentText(singleMessage);
                    Notification notification2 = builder2.build();
                    //与上面的setAutoCancel方法重合
                    notification2.flags = Notification.FLAG_AUTO_CANCEL;
                    manager.notify(1,notification2);
                    Log.e("111", "msg:" + singleMessage);
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        threadPoolExecutor = new ThreadPoolExecutor(2, 3,
                1, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>(128));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        QUEUE_NAME = getDeviceId(MyService.this);
        Log.e(TAG, "onStartCommand: "+getDeviceId(MyService.this) );
        manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        factory.setHost("192.168.1.6");
        factory.setPort(5672);
        factory.setUsername("ztemq");
        factory.setPassword("1234qwer");
        factory.setVirtualHost("/");
        subscribe(handler);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        thread.interrupt();
        singleThread.interrupt();
    }



    void subscribe(final Handler handler){
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "run: " );
                try {
                    Log.e(TAG, "run:准备连接 " );
                    Connection connection = factory.newConnection();
                    Log.e(TAG, "run: 创建channel" );
                    Channel channel = connection.createChannel();
//                        channel.queueDeclare(QUEUE_NAME, true, false, false, null);
                    //一次只发送一个，处理完成一个再获取下一个
                    channel.basicQos(1);
                    AMQP.Queue.DeclareOk q = channel.queueDeclare();
                    //将队列绑定到消息交换机exchange上，
                    //参数一：队列名称，参数二：路由名称，参数三：字符串key
                    //   queue   exchangeroutingKey路由关键字，exchange根据这个关键字进行消息投递。
                    channel.queueBind(q.getQueue(), "exchange_notice", "861614030247171");

                    Log.e(TAG, "run: 等待消息" );
                    QueueingConsumer consumer = new QueueingConsumer(channel);
                    //设置消息应答，表示消费者已经消费了任务，给服务器一个回响
                    channel.basicConsume(q.getQueue(), true, consumer);
                    while (true) {
                        Log.e(TAG, "run: 获取消息" );
                        QueueingConsumer.Delivery delivery = consumer.nextDelivery();
                        String message = new String(delivery.getBody());
                        Log.e("111", "[r] " + message);
                        //从message池中获取msg对象更高效
                        Message msg = handler.obtainMessage();
                        msg.what = 1;
                        Bundle bundle = new Bundle();
                        bundle.putString("msg", message);
                        msg.setData(bundle);
                        handler.sendMessage(msg);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (TimeoutException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        singleThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.e(TAG, "run2: " );
                try {
                    Log.e(TAG, "run2:准备连接 " );
                    Connection connection = factory.newConnection();
                    Log.e(TAG, "run2: 创建channel" );
                    final Channel channel = connection.createChannel();
                    channel.queueDeclare(QUEUE_NAME, true, false, false, null);
                    Log.e(TAG, "run2: 等待消息" );
                    channel.queueBind("861614030247171", "exchange_message", "861614030247171");
//                    QueueingConsumer consumer = new QueueingConsumer(channel);
                    final Consumer consumer = new DefaultConsumer(channel){
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                            String message = new String(body,"UTF-8");
                            Message msg = handler.obtainMessage();
                            msg.what = 2;
                            Bundle bundle = new Bundle();
                            bundle.putString("singleMessage", message);
                            msg.setData(bundle);
                            handler.sendMessage(msg);
                        }
                    };
                    channel.basicConsume(QUEUE_NAME, true, consumer);
//                    while (true) {
//                        Log.e(TAG, "run2: 获取消息" );
//                        QueueingConsumer.Delivery delivery = consumer.nextDelivery();
//                        String message = new String(delivery.getBody());
//                        Log.e("111", "[r]2 " + message);
//                        //从message池中获取msg对象更高效
//                        Message msg = handler.obtainMessage();
//                        msg.what = 2;
//                        Bundle bundle = new Bundle();
//                        bundle.putString("singleMessage", message);
//                        msg.setData(bundle);
//                        handler.sendMessage(msg);
//                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (TimeoutException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
        singleThread.start();
    }
    public static String getDeviceId(Context context){
        String deviceId = "";
        TelephonyManager tm = (TelephonyManager) context.getSystemService(TELEPHONY_SERVICE);
        if(!TextUtils.isEmpty(tm.getDeviceId())){
            deviceId = tm.getDeviceId();
        }
        return deviceId;
    }
}
