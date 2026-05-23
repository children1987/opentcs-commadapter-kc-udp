package com.kecong.opentcs;

import org.opentcs.data.model.Vehicle;
import org.opentcs.drivers.vehicle.VehicleProcessModel;

import java.beans.PropertyChangeListener;

/**
 * Extended vehicle process model for Kecong AGVs.
 * Tracks Kecong-specific state in addition to standard openTCS vehicle attributes.
 */
public class KecongVehicleProcessModel extends VehicleProcessModel {

    /** Property key for localization status */
    public static final String ATTRIBUTE_LOCALIZATION_STATUS = "kecong:localizationStatus";
    /** Property key for confidence level */
    public static final String ATTRIBUTE_CONFIDENCE = "kecong:confidence";
    /** Property key for Kecong work mode */
    public static final String ATTRIBUTE_WORK_MODE = "kecong:workMode";
    /** Property key for Kecong AGV state */
    public static final String ATTRIBUTE_AGV_STATE = "kecong:agvState";
    /** Property key for battery percent (0-1) */
    public static final String ATTRIBUTE_BATTERY_PERCENT = "kecong:batteryPercent";
    /** Property key for charge status */
    public static final String ATTRIBUTE_CHARGE_STATUS = "kecong:chargeStatus";
    /** Property key for last error codes */
    public static final String ATTRIBUTE_ERROR_CODES = "kecong:errorCodes";
    /** Property key for Kecong command sequence */
    public static final String ATTRIBUTE_CMD_SEQUENCE = "kecong:cmdSequence";
    /** Whether auto mode initialization is complete */
    public static final String ATTRIBUTE_AUTO_READY = "kecong:autoReady";
    /** Kecong-specific vehicle reference (for factory) */
    public static final String ATTRIBUTE_VEHICLE_REF = "kecong:vehicleRef";

    public KecongVehicleProcessModel(Vehicle attachedVehicle) {
        super(attachedVehicle);
    }

    // Convenience methods

    public int getLocalizationStatus() {
        Object val = getProperty(ATTRIBUTE_LOCALIZATION_STATUS);
        return val instanceof Integer ? (Integer) val : 0;
    }

    public void setLocalizationStatus(int status) {
        setProperty(ATTRIBUTE_LOCALIZATION_STATUS, status);
    }

    public int getConfidence() {
        Object val = getProperty(ATTRIBUTE_CONFIDENCE);
        return val instanceof Integer ? (Integer) val : 0;
    }

    public void setConfidence(int confidence) {
        setProperty(ATTRIBUTE_CONFIDENCE, confidence);
    }

    public int getKecongWorkMode() {
        Object val = getProperty(ATTRIBUTE_WORK_MODE);
        return val instanceof Integer ? (Integer) val : 0;
    }

    public void setKecongWorkMode(int mode) {
        setProperty(ATTRIBUTE_WORK_MODE, mode);
    }

    public int getKecongAgvState() {
        Object val = getProperty(ATTRIBUTE_AGV_STATE);
        return val instanceof Integer ? (Integer) val : 0;
    }

    public void setKecongAgvState(int state) {
        setProperty(ATTRIBUTE_AGV_STATE, state);
    }

    public float getBatteryPercent() {
        Object val = getProperty(ATTRIBUTE_BATTERY_PERCENT);
        return val instanceof Float ? (Float) val : 0f;
    }

    public void setBatteryPercent(float percent) {
        setProperty(ATTRIBUTE_BATTERY_PERCENT, percent);
    }

    public int getChargeStatus() {
        Object val = getProperty(ATTRIBUTE_CHARGE_STATUS);
        return val instanceof Integer ? (Integer) val : 0;
    }

    public void setChargeStatus(int status) {
        setProperty(ATTRIBUTE_CHARGE_STATUS, status);
    }

    public String getErrorCodes() {
        Object val = getProperty(ATTRIBUTE_ERROR_CODES);
        return val instanceof String ? (String) val : "";
    }

    public void setErrorCodes(String codes) {
        setProperty(ATTRIBUTE_ERROR_CODES, codes);
    }

    public int getCmdSequence() {
        Object val = getProperty(ATTRIBUTE_CMD_SEQUENCE);
        return val instanceof Integer ? (Integer) val : 0;
    }

    public void setCmdSequence(int seq) {
        setProperty(ATTRIBUTE_CMD_SEQUENCE, seq);
    }

    public boolean isAutoReady() {
        Object val = getProperty(ATTRIBUTE_AUTO_READY);
        return val instanceof Boolean && (Boolean) val;
    }

    public void setAutoReady(boolean ready) {
        setProperty(ATTRIBUTE_AUTO_READY, ready);
    }
}
