package com.example.resistancereader.BleServices;

import java.util.UUID;

public class ResistanceBoardUUIDs {

    public static UUID RESISTANCE_SERVICE =
            UUID.fromString("b067f00d-744d-8db5-9b42-aae2d7041e3c");

    public static UUID RESISTANCE_MEASUREMENT =
            UUID.fromString("b067beef-744d-8db5-9b42-aae2d7041e3c");

    // UUID for the client characteristic which is necessary for notifications
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
}
