package com.tunjid.rcswitchcontrol.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.nsd.NsdServiceInfo;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.tunjid.rcswitchcontrol.nsd.NsdHelper;
import com.tunjid.rcswitchcontrol.nsd.abstractclasses.BaseNsdService;
import com.tunjid.rcswitchcontrol.nsd.abstractclasses.RegistrationListener;
import com.tunjid.rcswitchcontrol.nsd.nsdprotocols.CommsProtocol;
import com.tunjid.rcswitchcontrol.nsd.nsdprotocols.ProxyProtocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Service hosting a {@link CommsProtocol} on network service discovery
 */
public class ServerNsdService extends BaseNsdService {

    private static final String TAG = ServerNsdService.class.getSimpleName();
    public static final String SERVER_FLAG = "com.tunjid.rcswitchcontrol.ServerNsdService.services.server.flag";
    public static final String ACTION_STOP = "com.tunjid.rcswitchcontrol.ServerNsdService.services.server.stop";

    private String serviceName;
    private ServerThread serverThread;

    private final IntentFilter intentFilter = new IntentFilter();
    private final IBinder binder = new ServerServiceBinder();
    private final RegistrationListener registrationListener = new RegistrationListener() {

        @Override
        public void onServiceRegistered(NsdServiceInfo serviceInfo) {
            super.onServiceRegistered(serviceInfo);
            ServerNsdService.this.serviceName = serviceInfo.getServiceName();
        }
    };

    private final BroadcastReceiver nsdUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_STOP:
                    stopSelf();
            }
            Log.i(TAG, "Received data for: " + action);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        intentFilter.addAction(ACTION_STOP);
        LocalBroadcastManager.getInstance(this).registerReceiver(nsdUpdateReceiver, intentFilter);

        nsdHelper.initializeRegistrationListener(registrationListener);
        serverThread = new ServerThread(nsdHelper);
        serverThread.start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public String getServiceName() {
        return serviceName;
    }

    @Override
    protected void tearDown() {
        super.tearDown();
        serverThread.tearDown();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(nsdUpdateReceiver);
    }

    /**
     * {@link Binder} for {@link ServerNsdService}
     */
    public class ServerServiceBinder extends Binder {
        public ServerNsdService getServerService() {
            return ServerNsdService.this;
        }
    }

    /**
     * Thread for communications between {@link ServerNsdService} and it's clients
     */
    private static class ServerThread extends Thread {

        volatile boolean isRunning;

        private ServerSocket serverSocket;

        ServerThread(NsdHelper helper) {

            // Since discovery will happen via Nsd, we don't need to care which port is
            // used, just grab an isAvailable one and advertise it via Nsd.
            try {
                serverSocket = new ServerSocket(0);
                helper.registerService(serverSocket.getLocalPort());
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            isRunning = true;

            while (isRunning) {
                try {
                    Log.d(TAG, "ServerSocket Created, awaiting connection.");
                    // Create new clients for every connection received
                    new Connection(serverSocket.accept());
                }
                catch (Exception e) {
                    Log.e(TAG, "Error creating ServerSocket: ", e);
                    e.printStackTrace();
                }
            }
        }

        void tearDown() {
            isRunning = false;
            try {
                Log.d(TAG, "Attempting to close server socket.");
                serverSocket.close();
            }
            catch (Exception e) {
                Log.e(TAG, "Error closing ServerSocket: ", e);
                e.printStackTrace();
            }
        }
    }

    /**
     * Connection between {@link ServerNsdService} and it's clients
     */
    private static class Connection {

        Connection(Socket socket) {
            Log.d(TAG, "Connected to new client");

            if (socket != null && socket.isConnected()) {
                CommsProtocol commsProtocol = new ProxyProtocol();

                try {

                    PrintWriter out = createPrintWriter(socket);
                    BufferedReader in = createBufferedReader(socket);

                    String inputLine, outputLine;

                    // Initiate conversation with client
                    outputLine = commsProtocol.processInput(null).serialize();

                    out.println(outputLine);

                    while ((inputLine = in.readLine()) != null) {
                        outputLine = commsProtocol.processInput(inputLine).serialize();
                        out.println(outputLine);

                        Log.d(TAG, "Read from client stream: " + inputLine);

                        if (outputLine.equals("Bye.")) break;
                    }

                    // Close protocol
                    commsProtocol.close();
                    in.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                    // Try to close protocol if disconnected
                    try {
                        commsProtocol.close();
                    }
                    catch (IOException e2) {
                        e2.printStackTrace();
                    }
                }
            }
        }

    }
}
