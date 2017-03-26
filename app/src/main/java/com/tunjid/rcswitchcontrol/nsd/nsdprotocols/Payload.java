package com.tunjid.rcswitchcontrol.nsd.nsdprotocols;

import com.google.gson.Gson;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Payload class
 * <p>
 * Created by tj.dahunsi on 2/11/17.
 */

public class Payload implements Serializable {
    private static final Gson GSON = new Gson();

    String response;
    String data;
    List<String> commands = new ArrayList<>();

    public String getResponse() {
        return response;
    }

    public String getData() {
        return data;
    }

    public List<String> getCommands() {
        return commands;
    }

    public String serialize() {
        return GSON.toJson(this);
    }

    public static Payload deserialize(String input) {
        return GSON.fromJson(input, Payload.class);
    }

}
