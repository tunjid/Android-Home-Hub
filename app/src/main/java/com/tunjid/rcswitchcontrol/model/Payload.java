package com.tunjid.rcswitchcontrol.model;

import com.google.gson.Gson;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Payload class
 * <p>
 * Created by tj.dahunsi on 2/11/17.
 */

public class Payload implements Serializable {
    private static final Gson GSON = new Gson();

    private String key;
    private String data;
    private String action;
    private String response;
    private Set<String> commands = new LinkedHashSet<>();

    public String getKey() {
        return key;
    }

    public String getData() {
        return data;
    }

    public String getAction() {
        return action;
    }

    public String getResponse() {
        return response;
    }

    public Set<String> getCommands() {
        return commands;
    }

    public String serialize() {
        return GSON.toJson(this);
    }

    public static Payload deserialize(String input) {
        return GSON.fromJson(input, Payload.class);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        Payload payload = new Payload();

        private Builder() {

        }

        public Builder setKey(String key) {
            payload.key = key;
            return this;
        }

        public Builder setData(String data) {
            payload.data = data;
            return this;
        }

        public Builder setAction(String action) {
            payload.action = action;
            return this;
        }

        public Builder setResponse(String response) {
            payload.response = response;
            return this;
        }

        public Builder addCommand(String command) {
            payload.commands.add(command);
            return this;
        }

        public Payload build() {
            return payload;
        }
    }

}
