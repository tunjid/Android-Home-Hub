package com.tunjid.rcswitchcontrol.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.Gson;
import com.tunjid.rcswitchcontrol.Application;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;

/**
 * A model representing an RF switch
 * <p>
 * Created by tj.dahunsi on 3/11/17.
 */

public class RcSwitch implements Parcelable {

    // Shared preference key
    public static final String SWITCH_PREFS = "SwitchPrefs";
    private static final String SWITCHES_KEY = "Switches";

    private static final Gson gson = new Gson();

    private String name;

    private byte bitLength;
    private byte pulseLength;
    private byte protocol;

    private byte[] onCode = new byte[4];
    private byte[] offCode = new byte[4];

    private RcSwitch() {

    }

    public static String serializedSavedSwitches(){
        return Application.getInstance().getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE)
                .getString(SWITCHES_KEY, "");
    }

    public static ArrayList<RcSwitch> deserialize(String serialized) {
        RcSwitch[] array = gson.fromJson(serialized, RcSwitch[].class);
        return array == null ? new ArrayList<RcSwitch>() : new ArrayList<>(Arrays.asList(array));

    }

    public static ArrayList<RcSwitch> getSavedSwitches(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE);
        String jsonString = sharedPreferences.getString(SWITCHES_KEY, "");
        RcSwitch[] array = gson.fromJson(jsonString, RcSwitch[].class);

        return array == null ? new ArrayList<RcSwitch>() : new ArrayList<>(Arrays.asList(array));

    }

    public static void saveSwitches(Context context, List<RcSwitch> switches) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE);
        sharedPreferences.edit().putString(SWITCHES_KEY, gson.toJson(switches)).apply();
    }

    public String getName() {
        return name;
    }

    public byte[] getOnCode() {
        return onCode;
    }

    public byte[] getOffCode() {
        return offCode;
    }

    public byte getBitLength() {
        return bitLength;
    }

    public byte getPulseLength() {
        return pulseLength;
    }

    public byte getProtocol() {
        return protocol;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RcSwitch rcSwitch = (RcSwitch) o;

        return Arrays.equals(onCode, rcSwitch.onCode) && Arrays.equals(offCode, rcSwitch.offCode);

    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(onCode);
        result = 31 * result + Arrays.hashCode(offCode);
        return result;
    }

    private RcSwitch(Parcel in) {
        name = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
    }

    public static final Parcelable.Creator<RcSwitch> CREATOR = new Parcelable.Creator<RcSwitch>() {
        @Override
        public RcSwitch createFromParcel(Parcel in) {
            return new RcSwitch(in);
        }

        @Override
        public RcSwitch[] newArray(int size) {
            return new RcSwitch[size];
        }
    };

    public static final class SwitchCreator {
        State state;
        RcSwitch rcSwitch;

        public SwitchCreator() {
            state = State.ON_CODE;
        }

        public void withOnCode(byte[] code) {
            state = State.OFF_CODE;

            rcSwitch = new RcSwitch();
            rcSwitch.pulseLength = code[4];
            rcSwitch.bitLength = code[5];
            rcSwitch.protocol = code[6];

            System.arraycopy(code, 0, rcSwitch.onCode, 0, 4);
        }

        public RcSwitch withOffCode(byte[] code) {
            state = State.ON_CODE;

            int pulseLength = rcSwitch.pulseLength;
            pulseLength += (int) code[4];
            pulseLength /= 2;
            rcSwitch.pulseLength = (byte) pulseLength;

            System.arraycopy(code, 0, rcSwitch.offCode, 0, 4);
            return rcSwitch;
        }


        public State getState() {
            return state;
        }
    }

    public enum State {
        ON_CODE, OFF_CODE
    }
}
