package com.tunjid.rcswitchcontrol.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.nsd.NsdServiceInfo;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.tunjid.rcswitchcontrol.nsd.NsdHelper;
import com.tunjid.rcswitchcontrol.nsd.abstractclasses.BaseNsdService;
import com.tunjid.rcswitchcontrol.nsd.abstractclasses.RegistrationListener;
import com.tunjid.rcswitchcontrol.nsd.nsdprotocols.CommsProtocol;
import com.tunjid.rcswitchcontrol.nsd.nsdprotocols.ProxyProtocol;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import static com.tunjid.rcswitchcontrol.model.RcSwitch.SWITCH_PREFS;

/**
 * Service hosting a {@link CommsProtocol} on network service discovery
 */
public class ServerNsdService extends BaseNsdService {

    private static final String TAG = ServerNsdService.class.getSimpleName();
    public static final String SERVER_FLAG = "com.tunjid.rcswitchcontrol.ServerNsdService.services.server.flag";
    public static final String ACTION_STOP = "com.tunjid.rcswitchcontrol.ServerNsdService.services.server.stop";
    public static final String SERVICE_NAME_KEY = "com.tunjid.rcswitchcontrol.ServerNsdService.services.server.serviceName";
    public static final String WIRELESS_SWITCH_SERVICE = "Wireless Switch Service";

    private ServerThread serverThread;

    private final IntentFilter intentFilter = new IntentFilter();
    private final IBinder binder = new Binder();

    private final BroadcastReceiver nsdUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_STOP:
                    tearDown();
                    stopSelf();
                    break;
            }
            Log.i(TAG, "Received data for: " + action);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        intentFilter.addAction(ACTION_STOP);
        LocalBroadcastManager.getInstance(this).registerReceiver(nsdUpdateReceiver, intentFilter);

        startUp();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void startUp() {
        String serviceName = getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE)
                .getString(SERVICE_NAME_KEY, WIRELESS_SWITCH_SERVICE);

        serverThread = new ServerThread(nsdHelper, serviceName);
        serverThread.start();
    }

//    public String getServiceName() {
//        return serverThread != null ? serverThread.serviceName : "";
//    }

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

    public void restart() {
        serverThread.tearDown();
        tearDown();
        startUp();
    }

    /**
     * {@link android.os.Binder} for {@link ServerNsdService}
     */
    public class Binder extends android.os.Binder {
        public ServerNsdService getService() {
            return ServerNsdService.this;
        }
    }

    /**
     * Thread for communications between {@link ServerNsdService} and it's clients
     */
    private static class ServerThread extends Thread {

        volatile boolean isRunning;

        String serviceName;
        private ServerSocket serverSocket;
        private Connection connection;

        private final RegistrationListener registrationListener = new RegistrationListener() {

            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                super.onServiceRegistered(serviceInfo);
                ServerThread.this.serviceName = serviceInfo.getServiceName();
            }
        };

        ServerThread(NsdHelper helper, String serviceName) {

            // Since discovery will happen via Nsd, we don't need to care which port is
            // used, just grab an isAvailable one and advertise it via Nsd.
            try {
                serverSocket = new ServerSocket(0);
                helper.initializeRegistrationListener(registrationListener);
                helper.registerService(serverSocket.getLocalPort(), serviceName);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
//            finally {
//                try {
//                    serverSocket.close();
//                }
//                catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
        }

        @Override
        public void run() {
            isRunning = true;
            while (isRunning) {
                try {
                    Log.d(TAG, "ServerSocket Created, awaiting connection.");
                    // Create new clients for every connection received
                    connection = new Connection(serverSocket.accept());
                    connection.start();
                }
                catch (Exception e) {
                    Log.e(TAG, "Error creating ServerSocket: ", e);
                }
//                finally {
//                    try {
//                        if (connection != null) connection.close();
//                    }
//                    catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
            }
        }

        void tearDown() {
            isRunning = false;
            try {
                Log.d(TAG, "Attempting to close server connection.");
                connection.close();
            }
            catch (Exception e) {
                Log.e(TAG, "Error closing ServerSocket: ", e);
            }
            try {
                Log.d(TAG, "Attempting to close server socket.");
                serverSocket.close();
            }
            catch (Exception e) {
                Log.e(TAG, "Error closing ServerSocket: ", e);
            }
        }
    }

    /**
     * Connection between {@link ServerNsdService} and it's clients
     */
    private static class Connection implements Closeable {

        private Socket socket;

        Connection(Socket socket) {
            Log.d(TAG, "Connected to new client");
            this.socket = socket;
        }

        void start() {
            if (socket != null && socket.isConnected()) {
                CommsProtocol commsProtocol;
                PrintWriter out;
                BufferedReader in;
                try {
                    commsProtocol = new ProxyProtocol();
                    in = createBufferedReader(socket);
                    out = createPrintWriter(socket);

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
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
//                finally {
//                    if (commsProtocol != null) try {
//                        commsProtocol.close();
//                    }
//                    catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                    if (in != null) try {
//                        in.close();
//                    }
//                    catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                    if (out != null) out.close();
//                }
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
