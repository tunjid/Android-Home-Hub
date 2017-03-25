package com.tunjid.rcswitchcontrol.nsd.nsdprotocols;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/**
 * Data class
 * <p>
 * Created by tj.dahunsi on 2/11/17.
 */

public class Data {
    private static final Gson GSON = new Gson();

    String response;
    List<String> commands = new ArrayList<>();

    public String getResponse() {
        return response;
    }

    public List<String> getCommands() {
        return commands;
    }

    public String serialize() {
        return GSON.toJson(this);
    }

    public static Data deserialize(String input) {
        return GSON.fromJson(input, Data.class);
    }

}
