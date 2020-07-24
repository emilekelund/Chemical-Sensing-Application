package com.example.chemicalsensingapplication.services;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.example.chemicalsensingapplication.utilities.BitConverter;

import java.util.Arrays;
import java.util.List;

import static com.example.chemicalsensingapplication.services.ChemicalSensingBoardUUIDs.MULTICHANNEL_MEASUREMENT;
import static com.example.chemicalsensingapplication.services.ChemicalSensingBoardUUIDs.MULTICHANNEL_NO_OF_ACTIVE_CHANNELS;
import static com.example.chemicalsensingapplication.services.ChemicalSensingBoardUUIDs.MULTICHANNEL_SERVICE;
import static com.example.chemicalsensingapplication.services.ChemicalSensingBoardUUIDs.POTENTIOMETRIC_SERVICE;
import static com.example.chemicalsensingapplication.services.GattActions.ACTION_GATT_CHEMICAL_SENSING_EVENTS;
import static com.example.chemicalsensingapplication.services.GattActions.EVENT;
import static com.example.chemicalsensingapplication.services.GattActions.Event;
import static com.example.chemicalsensingapplication.services.GattActions.MULTICHANNEL_DATA;
import static com.example.chemicalsensingapplication.services.GattActions.POTENTIOMETRIC_DATA;
import static com.example.chemicalsensingapplication.services.GattActions.TEMPERATURE_DATA;
import static com.example.chemicalsensingapplication.services.ChemicalSensingBoardUUIDs.CLIENT_CHARACTERISTIC_CONFIG;
import static com.example.chemicalsensingapplication.services.ChemicalSensingBoardUUIDs.POTENTIOMETRIC_MEASUREMENT;
import static com.example.chemicalsensingapplication.services.ChemicalSensingBoardUUIDs.TEMPERATURE_MEASUREMENT;
import static com.example.chemicalsensingapplication.services.ChemicalSensingBoardUUIDs.TEMPERATURE_SERVICE;

public class BleService extends Service {
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int activeChannels = 1;

    private BluetoothGattService mBleTemperatureService = null;
    private BluetoothGattService mBlePotentiometricService = null;
    private BluetoothGattService mBleMultiChannelService = null;

    private boolean isMultiChannel = false;

    public int onStartCommand(Intent intent, int flags, int noOfChannels) {

        activeChannels = intent.getIntExtra("ActiveChannels", 0);

        Log.i(TAG, "ActiveChannels: " + activeChannels);

        setNoOfChannels(activeChannels);

        return START_STICKY;

    }

    // Callback method for the BluetoothGatt
    // From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service with modifications
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(
                BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server.");

                broadcastChemicalSensingUpdate(Event.GATT_CONNECTED);
                // attempt to discover services
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.");

                broadcastChemicalSensingUpdate(Event.GATT_DISCONNECTED);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {

                broadcastChemicalSensingUpdate(Event.GATT_SERVICES_DISCOVERED);
                logServices(gatt); // debug

                // get the relevant service
                mBleTemperatureService = gatt.getService(TEMPERATURE_SERVICE);
                mBlePotentiometricService = gatt.getService(POTENTIOMETRIC_SERVICE);
                mBleMultiChannelService = gatt.getService(MULTICHANNEL_SERVICE);

                if (mBleTemperatureService != null) {
                    broadcastChemicalSensingUpdate(Event.TEMPERATURE_SERVICE_DISCOVERED);
                    logCharacteristics(mBleTemperatureService); // debug

                    // enable notifications on temperature measurement
                    BluetoothGattCharacteristic temperatureData =
                            mBleTemperatureService.getCharacteristic(TEMPERATURE_MEASUREMENT);
                    boolean result = setCharacteristicNotification(
                            temperatureData, true);
                    Log.i(TAG, "setCharacteristicNotification: " + result);

                } else if (mBlePotentiometricService != null) {
                    broadcastChemicalSensingUpdate(Event.POTENTIOMETRIC_SERVICE_DISCOVERED);
                    logCharacteristics(mBlePotentiometricService); // For debugging

                    // Enable notifications on the potentiometric measurements
                    BluetoothGattCharacteristic potentiometricData =
                            mBlePotentiometricService.getCharacteristic(POTENTIOMETRIC_MEASUREMENT);
                    boolean result = setCharacteristicNotification(
                            potentiometricData, true);
                    Log.i(TAG, "setCharacteristicNotification: " + result);

                } else if (mBleMultiChannelService != null) {
                    broadcastChemicalSensingUpdate(Event.MULTICHANNEL_SERVICE_DISCOVERED);
                    logCharacteristics(mBleMultiChannelService);

                    // Enable notifications on the multichannel measurements
                    BluetoothGattCharacteristic multiChannelData =
                            mBleMultiChannelService.getCharacteristic(MULTICHANNEL_MEASUREMENT);
                    boolean result = setCharacteristicNotification(
                            multiChannelData, true);
                    Log.i(TAG, "setCharacteristicNotification" + result);

                    isMultiChannel = true;

                } else {
                    broadcastChemicalSensingUpdate(Event.TEMPERATURE_SERVICE_NOT_AVAILABLE);
                    Log.i(TAG, "No relevant service available");
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            if (TEMPERATURE_MEASUREMENT.equals(characteristic.getUuid())) {
                // Copy the received byte array so we have a threadsafe copy
                byte[] rawData = new byte[characteristic.getValue().length];
                System.arraycopy(characteristic.getValue(), 0, rawData, 0,
                        characteristic.getValue().length);

                double resistance = BitConverter.bytesToDouble(rawData);
                broadcastResistanceUpdate(resistance);
            } else if (POTENTIOMETRIC_MEASUREMENT.equals(characteristic.getUuid())) {
                // Copy the received byte array so we have a threadsafe copy
                byte[] rawData = new byte[characteristic.getValue().length];
                System.arraycopy(characteristic.getValue(), 0, rawData, 0,
                        characteristic.getValue().length);

                double potential = BitConverter.bytesToDouble(rawData);
                broadcastPotentiometricUpdate(potential);
            } else if (MULTICHANNEL_MEASUREMENT.equals(characteristic.getUuid())) {
                // Copy the received byte array so we have a threadsafe copy
                byte[] rawData = new byte[characteristic.getValue().length];
                System.arraycopy(characteristic.getValue(), 0, rawData, 0,
                        characteristic.getValue().length);

                int[] multiChannelMeasurements = BitConverter.bytesToDoubleArr(rawData);


                broadcastMultiChannelUpdate(multiChannelMeasurements);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            // Implement a callback for descriptor write. When the descriptor is written to
            // and the gatt service is ready to receive commands again we set the measurement interval
            if (isMultiChannel) {
                boolean setChannels = setNoOfChannels(activeChannels);
                Log.i(TAG, "changeMeasurementInterval: " + setChannels);
            }
        }

    };

    /**
     * From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager =
                    (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
     * After using a given BLE device, the app must call this method to ensure resources
     * are released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection
     * result is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(...)} callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device - try to reconnect
        if (address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            return mBluetoothGatt.connect();
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        return true;
    }

    /**
     * From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
     * Enables or disables notification on a given characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    public boolean setCharacteristicNotification(
            BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.i(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        // first: call setCharacteristicNotification (client side)
        boolean result = mBluetoothGatt.setCharacteristicNotification(
                characteristic, enabled);

        // second: set enable notification server side (sensor)
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                CLIENT_CHARACTERISTIC_CONFIG);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mBluetoothGatt.writeDescriptor(descriptor);
        return result;
    }

    public boolean setNoOfChannels (int channels) {
        if (mBluetoothAdapter == null && mBluetoothGatt == null) {
            Log.i(TAG, "BluetoothAdapter not initialized");
            return false;
        }
        BluetoothGattCharacteristic noOfChannels =
                mBleMultiChannelService.getCharacteristic(MULTICHANNEL_NO_OF_ACTIVE_CHANNELS);
        boolean setVal = noOfChannels.setValue(channels, BluetoothGattCharacteristic.FORMAT_UINT32,0);
        Log.i(TAG,"SetValue: " + setVal);

        return mBluetoothGatt.writeCharacteristic(noOfChannels);
    }

    /*
    From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
    Broadcast methods for events and data
     */
    private void broadcastChemicalSensingUpdate(final Event event) {
        final Intent intent = new Intent(ACTION_GATT_CHEMICAL_SENSING_EVENTS);
        intent.putExtra(EVENT, event);
        sendBroadcast(intent);
    }

    // Broadcast the new Resistance data to our Intent, in this case the TemperatureReadActivity
    // Based on https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
    private void broadcastResistanceUpdate(final double resistance) {
        final Intent intent = new Intent(ACTION_GATT_CHEMICAL_SENSING_EVENTS);
        intent.putExtra(EVENT, Event.DATA_AVAILABLE);
        intent.putExtra(TEMPERATURE_DATA, resistance);
        sendBroadcast(intent);
    }

    private void broadcastPotentiometricUpdate(final double potential) {
        final Intent intent = new Intent(ACTION_GATT_CHEMICAL_SENSING_EVENTS);
        intent.putExtra(EVENT, Event.DATA_AVAILABLE);
        intent.putExtra(POTENTIOMETRIC_DATA, potential);
        sendBroadcast(intent);
    }

    private void broadcastMultiChannelUpdate (final int[] multiChannelData) {
        final Intent intent = new Intent(ACTION_GATT_CHEMICAL_SENSING_EVENTS);
        intent.putExtra(EVENT, Event.DATA_AVAILABLE);
        intent.putExtra(MULTICHANNEL_DATA, multiChannelData);
        sendBroadcast(intent);
    }

    /*
    From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
    Android Service specific code for binding and unbinding to this Android service
     */
    public class LocalBinder extends Binder {
        public BleService getService() {

            return BleService.this;
        }
    }


    // From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close()
        // is called such that resources are cleaned up properly.  In this particular
        // example, close() is invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }


    private final IBinder mBinder = new LocalBinder();

    /*
    From https://gits-15.sys.kth.se/anderslm/Ble-Gatt-with-Service
    logging and debugging
     */
    private final static String TAG = BleService.class.getSimpleName();

    private void logServices(BluetoothGatt gatt) {
        List<BluetoothGattService> services = gatt.getServices();
        for (BluetoothGattService service : services) {
            String uuid = service.getUuid().toString();
            Log.i(TAG, "service: " + uuid);
        }
    }

    private void logCharacteristics(BluetoothGattService gattService) {
        List<BluetoothGattCharacteristic> characteristics =
                gattService.getCharacteristics();
        for (BluetoothGattCharacteristic chara : characteristics) {
            String uuid = chara.getUuid().toString();
            Log.i(TAG, "characteristic: " + uuid);
        }
    }
}
