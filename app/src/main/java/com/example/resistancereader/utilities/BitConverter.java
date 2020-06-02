package com.example.resistancereader.utilities;

public class BitConverter {

    public static double bytesToResistance(byte[] bytes) {
        return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }
}
