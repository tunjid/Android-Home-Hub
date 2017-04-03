package com.tunjid.rcswitchcontrol.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.nsd.NsdServiceInfo;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.tunjid.rcswitchcontrol.ServiceConnection;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        serverThread.close();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(nsdUpdateReceiver);
    }

    public void restart() {
        serverThread.close();
        tearDown();
        startUp();
    }

    /**
     * {@link android.os.Binder} for {@link ServerNsdService}
     */
    private class Binder extends ServiceConnection.Binder<ServerNsdService> {
        public ServerNsdService getService() {
            return ServerNsdService.this;
        }
    }

    /**
     * Thread for communications between {@link ServerNsdService} and it's clients
     */
    private static class ServerThread extends Thread implements Closeable {

        volatile boolean isRunning;

        String serviceName;
        private ServerSocket serverSocket;
        private Map<Long, Connection> connectionsMap = new ConcurrentHashMap<>();

        private final RegistrationListener registrationListener = new RegistrationListener() {

            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                super.onServiceRegistered(serviceInfo);
                ServerThread.this.serviceName = serviceInfo.getServiceName();
            }
        };

        ServerThread(NsdHelper helper, String serviceName) {

            // Since discovery will happen via Nsd, we don't need to care which port is
            // used, just grab an avaialable one and advertise it via Nsd.
            try {
                serverSocket = new ServerSocket(0);
                helper.initializeRegistrationListener(registrationListener);
                helper.registerService(serverSocket.getLocalPort(), serviceName);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            isRunning = true;

            Log.d(TAG, "ServerSocket Created, awaiting connection.");

            while (isRunning) {
                try {
                    // Create new connection for every new client
                    Connection connection = new Connection(serverSocket.accept(), connectionsMap);
                    connection.start();
                    connectionsMap.put(connection.getId(), connection);

                    Log.d(TAG, "Client connected. Number of clients: " + connectionsMap.size());
                }
                catch (Exception e) {
                    Log.e(TAG, "Error creating client connection: ", e);
                }
            }
        }

        @Override
        public void close() {
            isRunning = false;

            for (Long key : connectionsMap.keySet()) {
                try {
                    Log.d(TAG, "Attempting to close server connection with id " + key);
                    connectionsMap.get(key).close();
                }
                catch (Exception e) {
                    Log.e(TAG, "Error closing ServerSocket: ", e);
                }
            }

            connectionsMap.clear();

            try {
                Log.d(TAG, "Attempting to close server socket.");
                if (serverSocket != null) serverSocket.close();
            }
            catch (Exception e) {
                Log.e(TAG, "Error closing ServerSocket: ", e);
            }
        }
    }

    /**
     * Connection between {@link ServerNsdService} and it's clients
     */
    private static class Connection extends Thread implements Closeable {

        private Socket socket;
        private Map<Long, Connection> connectionMap;

        Connection(Socket socket, Map<Long, Connection> connectionMap) {
            Log.d(TAG, "Connected to new client");
            this.socket = socket;
            this.connectionMap = connectionMap;
        }

        @Override
        public void run() {
            if (socket != null && socket.isConnected()) {
                CommsProtocol commsProtocol = null;
                try {
                    commsProtocol = new ProxyProtocol();
                    BufferedReader in = createBufferedReader(socket);
                    PrintWriter out = createPrintWriter(socket);

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
                finally {
                    try {
                        if (commsProtocol != null) try {
                            commsProtocol.close();
                        }
                        catch (IOException e) {
                            e.printStackTrace();
                        }
                        close();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        public void close() throws IOException {
            connectionMap.remove(getId());
            socket.close();
        }
    }
}
