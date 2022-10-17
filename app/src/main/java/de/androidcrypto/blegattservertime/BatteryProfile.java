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

import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT8;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import java.util.UUID;

/**
 * Implementation of the Bluetooth Battery Service Profile.
 * https://www.bluetooth.com/specifications/adopted-specifications
 */
public class BatteryProfile {
    private static final String TAG = BatteryProfile.class.getSimpleName();

    /* Battery Service UUID */
    public static UUID BATTERY_SERVICE    = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    public static UUID BATTERY_LEVEL      = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    public static UUID BATTERY_LEVEL_WARN = UUID.fromString("0000fffe-0000-1000-8000-00805f9b34fb");
    // 2901 is used when data is 8 bit
    public static UUID BATTERY_LEVEL_WARN_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    /* Mandatory Client Characteristic Config Descriptor */
    public static UUID CLIENT_CONFIG   = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /**
     * Return a configured {@link BluetoothGattService} instance for the
     * Battery Service.
     */
    public static BluetoothGattService createBatteryService() {
        BluetoothGattService service = new BluetoothGattService(BATTERY_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);
        // Battery characteristic
        BluetoothGattCharacteristic batteryLevel = new BluetoothGattCharacteristic(BATTERY_LEVEL,
                //Read-only characteristic, supports notifications
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattDescriptor configDescriptor = new BluetoothGattDescriptor(CLIENT_CONFIG,
                //Read/write descriptor
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        batteryLevel.addDescriptor(configDescriptor);

        // Battery Level warn characteristic
        BluetoothGattCharacteristic batteryLevelWarn = new BluetoothGattCharacteristic(BATTERY_LEVEL_WARN,
                //Read and write characteristic
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        /*
        BluetoothGattDescriptor batteryLevelWarnDescriptor = new BluetoothGattDescriptor(BATTERY_LEVEL_WARN_DESCRIPTOR,
                //Read/write descriptor
                // this is to set that this value is a 8bit = int8 value
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        batteryLevelWarn.addDescriptor(batteryLevelWarnDescriptor);
        */

        batteryLevelWarn.setValue(0, FORMAT_UINT8, 0);
        service.addCharacteristic(batteryLevel);
        service.addCharacteristic(batteryLevelWarn);
        return service;
    }

    /**
     * Construct the field values for a Battery Level characteristic
     * from the given value.
     */
    public static byte[] getBatteryLevel(int data) {
        byte[] returnValue = new byte[1];
        if (data < 0) {
            data = 49;
        }
        if (data > 100) {
            data = 51;
        }
        returnValue[0] = (byte) data;
        //return new byte[data % 0xff];
        return returnValue;
    }

    /**
     * Construct the field values for a Battery Level warn characteristic
     * from the given value.
     */
    public static byte[] getBatteryLevelWarn(int data) {
        byte[] returnValue = new byte[1];
        if (data < 0) {
            data = 49;
        }
        if (data > 100) {
            data = 51;
        }
        returnValue[0] = (byte) data;
        return returnValue;
    }

}