package com.tunjid.rcswitchcontrol.nsd.nsdprotocols;


import java.io.IOException;

/**
 * A protocol for communicating with RF 433 MhZ devices
 * <p>
 * Created by tj.dahunsi on 2/11/17.
 */

class BleRcProtocol implements CommsProtocol {

    private static final String ENABLE_SNIFFER = "Enable Sniffer";
    private static final String SNIFF = "Sniff";


    BleRcProtocol() {
    }

    @Override
    public Data processInput(String input) {
        Data output = new Data();

        if (input != null) {
            switch (input) {
                case ENABLE_SNIFFER:
                    output.getCommands().add(SNIFF);
                    return output;
                case SNIFF:
                    output.getCommands().add(SNIFF);
                    return output;
            }
        }
        if (input == null) {
            output.response = "Welcome!";
            output.commands.add(ENABLE_SNIFFER);
        }
        else output.response = "¯\\_(ツ)_/¯";
        return output;
    }

    @Override
    public void close() throws IOException {
    }
}
