package com.tunjid.rcswitchcontrol.nsd.abstractclasses;

import android.app.Service;
import android.support.annotation.CallSuper;

import com.tunjid.rcswitchcontrol.nsd.NsdHelper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Base service with rx operators
 * <p>
 * Created by tj.dahunsi on 2/5/17.
 */

public abstract class BaseNsdService extends Service {

    protected NsdHelper nsdHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        nsdHelper = new NsdHelper(this);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        tearDown();
    }

    @CallSuper
    protected void tearDown() {
        nsdHelper.tearDown();
    }

    protected static PrintWriter createPrintWriter(Socket socket) throws IOException {
        return new PrintWriter(
                new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream())), true);
    }

    protected static BufferedReader createBufferedReader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }
}
