package com.example.chemicalsensingapplication.services;

public class GattActions {

    /**
     * The action corresponding to Temperature events from BleService.
     * Intended for IntentFilters for a BroadcastReceiver.
     */
    public final static String ACTION_GATT_CHEMICAL_SENSING_EVENTS =
            "com.example.chemicalsensingapplication.services.ACTION_GATT_TEMPERATURE_EVENTS";

    /**
     * A flag for event info in intents (via intent.putExtra)
     */
    public final static String EVENT =
            "com.example.chemicalsensingapplication.services.EVENT";

    /**
     * A flag for Temperature data in intent (via intent.putExtra)
     */
    public final static String TEMPERATURE_DATA =
            "com.example.chemicalsensingapplication.services.TEMPERATURE_DATA";

    /**
     * A flag for Potentiometric data in intent (via intent.putExtra)
     */
    public final static String POTENTIOMETRIC_DATA =
            "com.example.chemicalsensingapplication.services.POTENTIOMETRIC_DATA";

    /**
     * A flag for MultiChannel data in intent (via intent.putExtra)
     */
    public final static String MULTICHANNEL_DATA =
            "com.example.chemicalsensingapplication.services.MULTICHANNEL_DATA";


    /**
     * Events corresponding to Gatt status/events
     */
    public enum Event {
        GATT_CONNECTED("Connected"),

        GATT_DISCONNECTED("Disconnected"),
        GATT_SERVICES_DISCOVERED("Services discovered"),
        TEMPERATURE_SERVICE_DISCOVERED("Temperature Service"),
        TEMPERATURE_SERVICE_NOT_AVAILABLE("Temperature service unavailable"),
        POTENTIOMETRIC_SERVICE_DISCOVERED("Potentiometric service"),
        POTENTIOMETRIC_SERVICE_NOT_AVAILABLE("Potentiometric service unavailable"),
        MULTICHANNEL_SERVICE_DISCOVERED("Potentiometric service"),
        MULTICHANNEL_SERVICE_NOT_AVAILABLE("Potentiometric service unavailable"),
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
