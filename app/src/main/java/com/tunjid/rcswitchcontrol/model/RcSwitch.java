package com.tunjid.rcswitchcontrol.model;

import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.StringDef;

import com.google.gson.Gson;
import com.tunjid.rcswitchcontrol.Application;

import java.lang.annotation.Retention;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * A model representing an RF switch
 * <p>
 * Created by tj.dahunsi on 3/11/17.
 */

public class RcSwitch implements Parcelable {

    private static final String SWITCHES_KEY = "Switches";

    // Shared preference key
    public static final String SWITCH_PREFS = "SwitchPrefs";

    public static final String ON_CODE = "on";
    public static final String OFF_CODE = "off";

    @Retention(SOURCE)
    @StringDef({ON_CODE, OFF_CODE})
    @interface SwitchCode {
    }

    private static final Gson gson = new Gson();

    private String name;

    private byte bitLength;
    private byte protocol;

    private byte[] pulseLength = new byte[4];
    private byte[] onCode = new byte[4];
    private byte[] offCode = new byte[4];

    private RcSwitch() {

    }

    public static String serializedSavedSwitches() {
        return Application.getInstance().getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE)
                .getString(SWITCHES_KEY, "");
    }

    public static ArrayList<RcSwitch> deserialize(String serialized) {
        RcSwitch[] array = gson.fromJson(serialized, RcSwitch[].class);
        return array == null ? new ArrayList<RcSwitch>() : new ArrayList<>(Arrays.asList(array));
    }

    public static ArrayList<RcSwitch> getSavedSwitches() {
        return deserialize(serializedSavedSwitches());
    }

    public static void saveSwitches(List<RcSwitch> switches) {
        SharedPreferences preferences = Application.getInstance().getSharedPreferences(SWITCH_PREFS, MODE_PRIVATE);
        preferences.edit().putString(SWITCHES_KEY, gson.toJson(switches)).apply();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public byte[] getTransmission(boolean state) {
        byte[] transmission = new byte[10];

        System.arraycopy(state ? onCode : offCode, 0, transmission, 0, onCode.length);
        System.arraycopy(pulseLength, 0, transmission, 4, pulseLength.length);
        transmission[8] = bitLength;
        transmission[9] = protocol;

        return transmission;
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

    protected RcSwitch(Parcel in) {
        name = in.readString();
        protocol = in.readByte();
        bitLength = in.readByte();
        onCode = in.createByteArray();
        offCode = in.createByteArray();
        pulseLength = in.createByteArray();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeByte(protocol);
        dest.writeByte(bitLength);
        dest.writeByteArray(onCode);
        dest.writeByteArray(offCode);
        dest.writeByteArray(pulseLength);
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
        @SwitchCode String state;
        RcSwitch rcSwitch;

        public SwitchCreator() {
            state = ON_CODE;
        }

        public void withOnCode(byte[] code) {
            state = OFF_CODE;

            rcSwitch = new RcSwitch();

            rcSwitch.bitLength = code[8];
            rcSwitch.protocol = code[9];

            System.arraycopy(code, 0, rcSwitch.onCode, 0, 4);
            System.arraycopy(code, 4, rcSwitch.pulseLength, 0, 4);
        }

        public RcSwitch withOffCode(byte[] code) {
            state = ON_CODE;
            System.arraycopy(code, 0, rcSwitch.offCode, 0, 4);
            return rcSwitch;
        }


        @SwitchCode
        public String getState() {
            return state;
        }
    }
}
