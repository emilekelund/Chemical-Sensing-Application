package com.example.chemicalsensingapplication.utilities;

public class BitConverter {

    public static double bytesToDouble(byte[] bytes) {
        return (bytes[3] & 0xFF) << 24 | (bytes[2] & 0xFF) << 16 | (bytes[1] & 0xFF) << 8 | (bytes[0] & 0xFF);
    }

    public static int[] bytesToDoubleArr(byte[] bytes) {
        int[] multiChannelValues = new int[7];
        int k = 0;

        for (int i = 0; i < bytes.length - 1; i += 2) {
            multiChannelValues[k] = (bytes[i+1])  << 8 | (bytes[i] & 0xFF);
            k++;
        }

        return multiChannelValues;
    }

    public static int[] potentiometerRtdToInt(byte[] bytes) {
        int[] potentiometerRtdValues = new int[2];
        potentiometerRtdValues[0] = (bytes[3] << 8) | (bytes[2] & 0xFF);
        potentiometerRtdValues[1] = (bytes[1] << 8) | (bytes[0] & 0xFF);
        return potentiometerRtdValues;
    }
}