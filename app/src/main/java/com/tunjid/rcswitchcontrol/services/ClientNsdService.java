package com.tunjid.rcswitchcontrol.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.nsd.NsdServiceInfo;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.StringDef;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.tunjid.rcswitchcontrol.R;
import com.tunjid.rcswitchcontrol.activities.MainActivity;
import com.tunjid.rcswitchcontrol.interfaces.StartedBoundService;
import com.tunjid.rcswitchcontrol.nsd.abstractclasses.BaseNsdService;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.net.Socket;

import static com.tunjid.rcswitchcontrol.model.RcSwitch.SWITCH_PREFS;
import static java.lang.annotation.RetentionPolicy.SOURCE;


public class ClientNsdService extends BaseNsdService
        implements StartedBoundService {

    public static final int NOTIFICATION_ID = 2;
    private static final String TAG = ClientNsdService.class.getSimpleName();

    public static final String LAST_CONNECTED_SERVICE = ClientNsdService.class.getSimpleName() + "last connected service";
    public static final String NSD_SERVICE_INFO_KEY = "current Service key";
    public static final String ACTION_SOCKET_CONNECTED = "service_socket_connected";
    public static final String ACTION_SOCKET_DISCONNECTED = "service_socket_disconnected";
    public static final String ACTION_SERVER_RESPONSE = "service_response";

    public static final String DATA_SERVER_RESPONSE = "service_response";

    private boolean isUserInApp;
    private NsdServiceInfo currentService;

    @ConnectionState
    private String connectionState = ACTION_SOCKET_DISCONNECTED;
    private MessageThread messageThread;

    private final IBinder binder = new NsdClientBinder();

    @Retention(SOURCE)
    @StringDef({ACTION_SOCKET_CONNECTED, ACTION_SOCKET_DISCONNECTED})
    @interface ConnectionState {
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        initialize(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        onAppForeGround();
        initialize(intent);
        return binder;
    }

    @Override
    public void initialize(Intent intent) {
        if (isConnected() || intent == null || !intent.hasExtra(NSD_SERVICE_INFO_KEY)) return;

        currentService = intent.getParcelableExtra(NSD_SERVICE_INFO_KEY);
        connect(currentService);
    }

    @Override
    public boolean isConnected() {
        return connectionState.equals(ACTION_SOCKET_CONNECTED);
    }

    @Override
    public void onAppBackground() {
        isUserInApp = false;

        // Use a notification to tell the user the app is running
        if (isConnected()) startForeground(NOTIFICATION_ID, connectedNotification());
            // Otherwise, remove the notification and wait for a reconnect
        else stopForeground(true);
    }

    @Override
    public void onAppForeGround() {
        isUserInApp = true;
        stopForeground(true);
    }

    public String getConnectionState() {
        return connectionState;
    }

    public void connect(NsdServiceInfo serviceInfo) {

        // If we're already connected to this service, return
        if (isConnected()) return;

        // Initialize current service if we are starting up the first time
        if (messageThread == null) {
            messageThread = new MessageThread(serviceInfo, this);
        }

        // We're binding to an entirely new service. Tear down the current state
        else if (!serviceInfo.equals(messageThread.service)) {
            tearDown();
            messageThread = new MessageThread(serviceInfo, this);
        }

        messageThread.start();
    }

    private void setConnectionState(@ConnectionState String connectionState) {
        this.connectionState = connectionState;

        if (isConnected()) { // Update the notification
            if (!isUserInApp) startForeground(NOTIFICATION_ID, connectedNotification());
            else stopForeground(true);

            getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE).edit()
                    .putString(LAST_CONNECTED_SERVICE, currentService.getServiceName()).apply();
        }

        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(connectionState));
    }

    public void sendMessage(String message) {
        messageThread.send(message);
    }

    protected void tearDown() {
        super.tearDown();

        Log.e(TAG, "Tearing down ClientServer");

        if (messageThread != null) messageThread.exit();
    }

    private Notification connectedNotification() {

        final Intent resumeIntent = new Intent(this, MainActivity.class);

        resumeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        resumeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        resumeIntent.putExtra(NSD_SERVICE_INFO_KEY, currentService);

        PendingIntent activityPendingIntent = PendingIntent.getActivity(
                this, 0, resumeIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getText(R.string.connected))
                .setContentText(getText(R.string.connected_to_server))
                .setContentIntent(activityPendingIntent);

        return notificationBuilder.build();
    }

    public class NsdClientBinder extends Binder {
        // Binder impl
        public ClientNsdService getClientService() {
            return ClientNsdService.this;
        }
    }

    private static class MessageThread extends Thread {

        NsdServiceInfo service;

        Socket currentSocket;
        PrintWriter out;

        ClientNsdService clientNsdService;

        MessageThread(NsdServiceInfo serviceInfo, ClientNsdService clientNsdService) {
            this.service = serviceInfo;
            this.clientNsdService = clientNsdService;
        }

        @Override
        public void run() {
            try {
                Log.d(TAG, "Initializing client-side socket. Host: " + service.getHost() + ", Port: " + service.getPort());

                currentSocket = new Socket(service.getHost(), service.getPort());

                out = createPrintWriter(currentSocket);
                BufferedReader in = createBufferedReader(currentSocket);

                Log.d(TAG, "Connection-side socket initialized.");

                clientNsdService.setConnectionState(ACTION_SOCKET_CONNECTED);

                String fromServer;

                while ((fromServer = in.readLine()) != null) {
                    Log.i(TAG, "Server: " + fromServer);

                    Intent serverResponse = new Intent();
                    serverResponse.setAction(ACTION_SERVER_RESPONSE);
                    serverResponse.putExtra(DATA_SERVER_RESPONSE, fromServer);

                    LocalBroadcastManager.getInstance(clientNsdService).sendBroadcast(serverResponse);

                    if (fromServer.equals("Bye.")) {
                        clientNsdService.connectionState = ACTION_SOCKET_DISCONNECTED;
                        break;
                    }
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                clientNsdService.setConnectionState(ACTION_SOCKET_DISCONNECTED);
            }
        }

        void send(String message) {
            new MessageSender(message, this).start();
        }

        synchronized void exit() {
            try {
                Log.d(TAG, "Exiting message thread.");
                currentSocket.close();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            clientNsdService.setConnectionState(ACTION_SOCKET_DISCONNECTED);
        }
    }

    private static class MessageSender extends Thread {

        String message;
        MessageThread messageThread;

        MessageSender(String message, MessageThread messageThread) {
            this.message = message;
            this.messageThread = messageThread;
        }

        @Override
        public void run() {
            try {
                messageThread.out.println(message);
            }
            catch (Exception e) {
                Log.d(TAG, "Error3", e);
            }
            Log.d(TAG, "Connection sent message: " + message);
        }
    }
}
