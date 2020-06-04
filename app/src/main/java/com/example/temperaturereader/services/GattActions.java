package com.example.temperaturereader.services;

public class GattActions {

    /**
     * The action corresponding to IMU events from BleImuService.
     * Intended for IntentFilters for a BroadcastReceiver.
     */
    public final static String ACTION_GATT_RESISTANCE_EVENTS =
            "com.example.temperaturereader.services.ACTION_GATT_IMU_EVENTS";

    /**
     * A flag for event info in intents (via intent.putExtra)
     */
    public final static String EVENT =
            "com.example.temperaturereader.services.EVENT";


    /**
     * A flag for IMU data in intent (via intent.putExtra)
     */
    public final static String RESISTANCE_DATA =
            "com.example.temperaturereader.services.IMU_DATA";


    /**
     * Events corresponding to Gatt status/events
     */
    public enum Event {
        GATT_CONNECTED("Connected"),
        GATT_DISCONNECTED("Disconnected"),
        GATT_SERVICES_DISCOVERED("Services discovered"),
        RESISTANCE_SERVICE_DISCOVERED("Temperature Service"),
        RESISTANCE_SERVICE_NOT_AVAILABLE("Temperature service unavailable"),
        DATA_AVAILABLE("Data available");

        @Override
        public String toString() {
            return text;
        }

        private final String text;

        Event(String text) {
            this.text = text;
        }
    }
}
