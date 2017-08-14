package com.example.zte.rabbitmqdemo;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MainActivity extends AppCompatActivity {
    private NotificationManager manager;
    private static final String TAG = "111";
    private static final String QUEUE_NAME = "admin";
    public static final String ROUTING_NAME = "admin";
    public static final String EXCHANGE_NAME = "exchange_message";
    ConnectionFactory  factory = new ConnectionFactory();
    private EditText editText;
    private ExecutorService executorService = Executors.newCachedThreadPool();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupConnectionFactory();
        editText = (EditText) findViewById(R.id.edittext);
        publishToAMQP();
//        manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
//        factory.setHost("192.168.1.6");
//        factory.setPort(5672);
//        factory.setUsername("ztemq");
//        factory.setPassword("1234qwer");
//        factory.setVirtualHost("/");
//
//
//        final Handler handler = new Handler(){
//            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
//            @Override
//            public void handleMessage(Message msg) {
//                String message = msg.getData().getString("msg");
//                TextView tv = (TextView) findViewById(R.id.textView);
//                Date now = new Date();
//                Notification.Builder builder = new Notification.Builder(MainActivity.this);
//                builder.setSmallIcon(R.mipmap.icon_logo)
//                        .setContentTitle("有新的警情")
//                        .setDefaults(Notification.DEFAULT_VIBRATE)
//                        .setContentText(message);
//                Notification notification = builder.build();
//                notification.flags = Notification.FLAG_NO_CLEAR;
//                manager.notify(0,notification);
//                SimpleDateFormat ft = new SimpleDateFormat("hh:mm:ss");
//                tv.append(ft.format(now) + ' ' + message + '\n');
//                Log.e("111", "msg:" + message);
//            }
//        };
//        subscribe(handler);
        Intent intent = new Intent(this,MyService.class);
        startService(intent);
    }
    private void setupConnectionFactory() {
//      String uri = "ws://192.168.1.6:15674/ws";
        factory.setHost("192.168.1.6");
        factory.setPort(5672);
        factory.setUsername("ztemq");
        factory.setPassword("1234qwer");
        factory.setVirtualHost("/");

//            factory.setAutomaticRecoveryEnabled(false);
//        try {
////            factory.setUri(uri);
//        } catch (URISyntaxException e) {
//            e.printStackTrace();
//        } catch (NoSuchAlgorithmException e) {
//            e.printStackTrace();
//        } catch (KeyManagementException e) {
//            e.printStackTrace();
//        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        publishThread.interrupt();
    }
    private Thread publishThread;


    public void sendMessage(View view) {
        publishToAMQP();
    }
    public void publishToAMQP() {
        publishThread = new Thread(new Runnable() {
            @Override
            public void run() {
                    try {
                        Connection connection = factory.newConnection();
                        Channel ch = connection.createChannel();
                        Log.e(TAG, "run: 连接成功" );
                        ch.exchangeDeclare(EXCHANGE_NAME,"direct",true);
                        ch.queueDeclare(QUEUE_NAME,true,false,false,null);
                        ch.queueBind(QUEUE_NAME,"exchange_message",ROUTING_NAME);
                        try {
                            Log.e(TAG, "run: 开始发送消息" );
                            ch.basicPublish(EXCHANGE_NAME,ROUTING_NAME, null, editText.getText().toString().getBytes());
                            ch.waitForConfirmsOrDie();
                            ch.close();
                            connection.close();
                        } catch (Exception e) {
                            throw e;
                        }
                    } catch (InterruptedException e) {
                    } catch (Exception e) {
                        Log.d("", "Connection broken: " + e.getClass().getName());

                    }
            }
        });
        executorService.execute(publishThread);
    }
}
