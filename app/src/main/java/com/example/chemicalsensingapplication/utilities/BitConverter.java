package com.example.chemicalsensingapplication.utilities;

public class BitConverter {

    public static double bytesToDouble(byte[] bytes) {
        return bytes[3] << 24 | (bytes[2] & 0xFF) << 16 | (bytes[1] & 0xFF) << 8 | (bytes[0] & 0xFF);
    }

    public static double[] bytesToDoubleArr(byte[] bytes) {
        double[] multiChannelValues = new double[8];

        for (int i = bytes.length - 1; i > 1; i -= 2) {
            multiChannelValues[i] = (bytes[i] & 0xFF)  << 8 | (bytes[i-1] & 0xFF);
        }

        return multiChannelValues;
    }
}
