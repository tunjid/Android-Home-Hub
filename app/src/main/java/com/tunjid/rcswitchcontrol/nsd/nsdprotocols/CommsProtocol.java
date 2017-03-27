package com.tunjid.rcswitchcontrol.nsd.nsdprotocols;

import com.tunjid.rcswitchcontrol.model.Payload;

import java.io.Closeable;

/**
 * Interface for Server communication with input from client
 * <p>
 * Created by tj.dahunsi on 2/6/17.
 */

public interface CommsProtocol extends Closeable {

    String PING = "Ping";
    String RESET = "reset";

    Payload processInput(String input);

}
