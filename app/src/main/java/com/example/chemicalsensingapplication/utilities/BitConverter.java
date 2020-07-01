package com.example.chemicalsensingapplication.utilities;

public class BitConverter {

    public static double bytesToDouble(byte[] bytes) {
        return bytes[3] << 24 | (bytes[2] & 0xFF) << 16 | (bytes[1] & 0xFF) << 8 | (bytes[0] & 0xFF);
    }
}
