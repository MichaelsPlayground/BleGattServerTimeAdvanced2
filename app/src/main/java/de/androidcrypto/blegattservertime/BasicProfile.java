package de.androidcrypto.blegattservertime;

/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Implementation of the Bluetooth GATT Basic Profile.
 * https://www.bluetooth.com/specifications/adopted-specifications
 */
public class BasicProfile {
    private static final String TAG = BasicProfile.class.getSimpleName();

    /* Minimal GATT Profile */
    public static UUID DEVICE_INFO_SERVICE = UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb");
    public static UUID MANUFACTURER_NAME    = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb");
    public static UUID DEVICE_NAME    = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb");
    public static UUID MODEL_NUMBER   = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb");
    public static UUID SERIAL_NUMBER  = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb");
    /* Mandatory Client Characteristic Config Descriptor */
    public static UUID CLIENT_CONFIG   = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /* static data */
    public static final String MANUFACTURER_NAME_VALUE = "Androidcrypto";
    public static final String DEVICE_NAME_VALUE = "GATT Example 1";
    public static final String MODEL_NUMBER_VALUE = "1234";
    public static final String SERIAL_NUMBER_VALUE = "5678";

    /**
     * Return a configured {@link BluetoothGattService} instance for the
     * Basic GATT Service.
     */
    public static BluetoothGattService createBasicGattService() {
        BluetoothGattService service = new BluetoothGattService(DEVICE_INFO_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // Basic characteristic
        BluetoothGattCharacteristic manufacturerName = new BluetoothGattCharacteristic(MANUFACTURER_NAME,
                //Read-only characteristic
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattCharacteristic deviceName = new BluetoothGattCharacteristic(DEVICE_NAME,
                //Read-only characteristic
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattDescriptor configDescriptor = new BluetoothGattDescriptor(CLIENT_CONFIG,
                //Read/write descriptor
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        deviceName.addDescriptor(configDescriptor);

        // Model number characteristic
        BluetoothGattCharacteristic modelNumber = new BluetoothGattCharacteristic(MODEL_NUMBER,
                //Read-only characteristic
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);

        // Serial number characteristic
        BluetoothGattCharacteristic serialNumber = new BluetoothGattCharacteristic(SERIAL_NUMBER,
                //Read-only characteristic
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);

        service.addCharacteristic(manufacturerName);
        service.addCharacteristic(deviceName);
        service.addCharacteristic(modelNumber);
        service.addCharacteristic(serialNumber);
        return service;
    }

    /**
     * Construct the field values for a Manufacturer Name characteristic
     *
     */
    public static byte[] getManufacturerName() {
        return MANUFACTURER_NAME_VALUE.getBytes(StandardCharsets.UTF_8);
    }


    /**
     * Construct the field values for a Device Name characteristic
     *
     */
    public static byte[] getDeviceName() {
        return DEVICE_NAME_VALUE.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Construct the field values for a Model Number characteristic
     *
     */
    public static byte[] getModelNumber() {
        return MODEL_NUMBER_VALUE.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Construct the field values for a Serial Number characteristic
     *
     */
    public static byte[] getSerialNumber() {
        return SERIAL_NUMBER_VALUE.getBytes(StandardCharsets.UTF_8);
    }

}