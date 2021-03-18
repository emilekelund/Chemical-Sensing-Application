package com.example.chemicalsensingapplication.services;

import java.util.UUID;

public class ChemicalSensingBoardUUIDs {

    public static UUID TEMPERATURE_SERVICE =
            UUID.fromString("b067f00d-744d-8db5-9b42-aae2d7041e3c");

    public static UUID TEMPERATURE_MEASUREMENT =
            UUID.fromString("b067beef-744d-8db5-9b42-aae2d7041e3c");

    public static UUID POTENTIOMETRIC_SERVICE =
            UUID.fromString("0ee93a6f-af10-433b-b396-af3aecda5508");

    public static UUID POTENTIOMETRIC_MEASUREMENT =
            UUID.fromString("0ee9bffe-af10-433b-b396-af3aecda5508");

    public static UUID MULTICHANNEL_SERVICE =
            UUID.fromString("32733bcf-263f-459c-b375-466d7a0f64f7");

    public static UUID MULTICHANNEL_MEASUREMENT =
            UUID.fromString("3273bf1e-263f-459c-b375-466d7a0f64f7");

    public static UUID MULTICHANNEL_NO_OF_ACTIVE_CHANNELS =
            UUID.fromString("3273bef2-263f-459c-b375-466d7a0f64f7");

    public static UUID POTENTIOMETER_RTD_SERVICE =
            UUID.fromString("32733bef-263f-459c-b375-466d7a0f64f7");

    public static UUID POTENTIOMETER_RTD_MEASUREMENT =
            UUID.fromString("3273bf2e-263f-459c-b375-466d7a0f64f7");


    // UUID for the client characteristic which is necessary for notifications
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
}
