package com.kecong.opentcs;

import java.util.HashMap;
import java.util.Map;
import org.opentcs.data.model.Vehicle;
import org.opentcs.drivers.vehicle.VehicleProcessModel;

/**
 * Extended vehicle process model for Kecong AGVs.
 * Tracks Kecong-specific state alongside standard openTCS vehicle attributes.
 */
public class KecongVehicleProcessModel extends VehicleProcessModel {

    private final Map<String, Object> kecongProps = new HashMap<>();

    public static final String ATTRIBUTE_LOCALIZATION_STATUS = "kecong:localizationStatus";
    public static final String ATTRIBUTE_CONFIDENCE = "kecong:confidence";
    public static final String ATTRIBUTE_WORK_MODE = "kecong:workMode";
    public static final String ATTRIBUTE_AGV_STATE = "kecong:agvState";
    public static final String ATTRIBUTE_BATTERY_PERCENT = "kecong:batteryPercent";
    public static final String ATTRIBUTE_CHARGE_STATUS = "kecong:chargeStatus";
    public static final String ATTRIBUTE_ERROR_CODES = "kecong:errorCodes";
    public static final String ATTRIBUTE_CMD_SEQUENCE = "kecong:cmdSequence";
    public static final String ATTRIBUTE_AUTO_READY = "kecong:autoReady";
    public static final String ATTRIBUTE_VEHICLE_REF = "kecong:vehicleRef";

    public KecongVehicleProcessModel(Vehicle attachedVehicle) {
        super(attachedVehicle);
    }

    private Object getKecongProp(String key) { return kecongProps.get(key); }
    private void setKecongProp(String key, Object val) { kecongProps.put(key, val); }

    public int getLocalizationStatus() {
        Object val = getKecongProp(ATTRIBUTE_LOCALIZATION_STATUS);
        return val instanceof Integer ? (Integer) val : 0;
    }
    public void setLocalizationStatus(int status) { setKecongProp(ATTRIBUTE_LOCALIZATION_STATUS, status); }

    public int getConfidence() {
        Object val = getKecongProp(ATTRIBUTE_CONFIDENCE);
        return val instanceof Integer ? (Integer) val : 0;
    }
    public void setConfidence(int confidence) { setKecongProp(ATTRIBUTE_CONFIDENCE, confidence); }

    public int getKecongWorkMode() {
        Object val = getKecongProp(ATTRIBUTE_WORK_MODE);
        return val instanceof Integer ? (Integer) val : 0;
    }
    public void setKecongWorkMode(int mode) { setKecongProp(ATTRIBUTE_WORK_MODE, mode); }

    public int getKecongAgvState() {
        Object val = getKecongProp(ATTRIBUTE_AGV_STATE);
        return val instanceof Integer ? (Integer) val : 0;
    }
    public void setKecongAgvState(int state) { setKecongProp(ATTRIBUTE_AGV_STATE, state); }

    public float getBatteryPercent() {
        Object val = getKecongProp(ATTRIBUTE_BATTERY_PERCENT);
        return val instanceof Float ? (Float) val : 0f;
    }
    public void setBatteryPercent(float percent) { setKecongProp(ATTRIBUTE_BATTERY_PERCENT, percent); }

    public int getChargeStatus() {
        Object val = getKecongProp(ATTRIBUTE_CHARGE_STATUS);
        return val instanceof Integer ? (Integer) val : 0;
    }
    public void setChargeStatus(int status) { setKecongProp(ATTRIBUTE_CHARGE_STATUS, status); }

    public String getErrorCodes() {
        Object val = getKecongProp(ATTRIBUTE_ERROR_CODES);
        return val instanceof String ? (String) val : "";
    }
    public void setErrorCodes(String codes) { setKecongProp(ATTRIBUTE_ERROR_CODES, codes); }

    public int getCmdSequence() {
        Object val = getKecongProp(ATTRIBUTE_CMD_SEQUENCE);
        return val instanceof Integer ? (Integer) val : 0;
    }
    public void setCmdSequence(int seq) { setKecongProp(ATTRIBUTE_CMD_SEQUENCE, seq); }

    public boolean isAutoReady() {
        Object val = getKecongProp(ATTRIBUTE_AUTO_READY);
        return val instanceof Boolean && (Boolean) val;
    }
    public void setAutoReady(boolean ready) { setKecongProp(ATTRIBUTE_AUTO_READY, ready); }
}
