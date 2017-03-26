package com.tunjid.rcswitchcontrol.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;

/**
 * A model representing an RF switch
 * <p>
 * Created by tj.dahunsi on 3/11/17.
 */

public class RfSwitch implements Parcelable {

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

    private RfSwitch() {

    }

    public static ArrayList<RfSwitch> getSavedSwitches(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE);
        String jsonString = sharedPreferences.getString(SWITCHES_KEY, "");
        RfSwitch[] array = gson.fromJson(jsonString, RfSwitch[].class);

        return array == null ? new ArrayList<RfSwitch>() : new ArrayList<>(Arrays.asList(array));

    }

    public static void saveSwitches(Context context, List<RfSwitch> switches) {
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

        RfSwitch rfSwitch = (RfSwitch) o;

        return Arrays.equals(onCode, rfSwitch.onCode) && Arrays.equals(offCode, rfSwitch.offCode);

    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(onCode);
        result = 31 * result + Arrays.hashCode(offCode);
        return result;
    }

    private RfSwitch(Parcel in) {
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

    public static final Parcelable.Creator<RfSwitch> CREATOR = new Parcelable.Creator<RfSwitch>() {
        @Override
        public RfSwitch createFromParcel(Parcel in) {
            return new RfSwitch(in);
        }

        @Override
        public RfSwitch[] newArray(int size) {
            return new RfSwitch[size];
        }
    };

    public static final class SwitchCreator {
        State state;
        RfSwitch rfSwitch;

        public SwitchCreator() {
            state = State.ON_CODE;
        }

        public void withOnCode(byte[] code) {
            state = State.OFF_CODE;

            rfSwitch = new RfSwitch();
            rfSwitch.pulseLength = code[4];
            rfSwitch.bitLength = code[5];
            rfSwitch.protocol = code[6];

            System.arraycopy(code, 0, rfSwitch.onCode, 0, 4);
        }

        public RfSwitch withOffCode(byte[] code) {
            state = State.ON_CODE;

            int pulseLength = rfSwitch.pulseLength;
            pulseLength += (int) code[4];
            pulseLength /= 2;
            rfSwitch.pulseLength = (byte) pulseLength;

            System.arraycopy(code, 0, rfSwitch.offCode, 0, 4);
            return rfSwitch;
        }


        public State getState() {
            return state;
        }
    }

    public enum State {
        ON_CODE, OFF_CODE
    }
}
