package com.tunjid.rcswitchcontrol.nsd.nsdprotocols;

import java.io.Closeable;

/**
 * Interface for Server communication with input from client
 * <p>
 * Created by tj.dahunsi on 2/6/17.
 */

public interface CommsProtocol extends Closeable {

    Data processInput(String input);

}
