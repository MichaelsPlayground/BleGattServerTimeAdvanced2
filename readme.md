# Bluetooth GATT Server Sample

This application demonstrates accessing the `BluetoothGattServer` Android API
from within an Android Things application. The sample application advertises
the [Current Time Service](https://www.bluetooth.com/specifications/gatt/services),
and implements the server role of the GATT
[Time Profile](https://www.bluetooth.com/specifications/adopted-specifications).

> **Note:** The Android Things Console will be turned down for non-commercial
> use on January 5, 2022. For more details, see the
> [FAQ page](https://developer.android.com/things/faq).

Original source: https://github.com/androidthings/sample-bluetooth-le-gattserver

Note: this is a renewed version that runs on Android  devices up to Android 12 (SDK 32).

```plaintext
Typically, a GATT database has the services 0x1800 (Generic Access) and 
0x1801 (Generic Attribute) at least. The Generic Access service contains two 
mandatory characteristics: Device Name and Appearance. The Generic Attribute 
service should be empty.

Therefore, the minimal GATT database looks like this:

Handle  Description
 0000   Service: Generic Access (1800)
 0001   Characteristic: Device Name (2A00, readable)
 0002   Characteristic Value (string)
 0003   Characteristic: Appearance (2A01, readable)
 0004   Characteristic Value (16bit enum)
 0005   Service: Generic Attribute (1801) 
 
After these two services, you can add your own services. In your case, you don't 
seem to target a well-known service, so you'll create an own one.

Complete example: https://github.com/dbw9580/BLE-HID-Peripheral-for-Android/blob/14a791656b6b3ca310323a6443f057222ff38094/lib/src/main/java/jp/kshoji/blehid/HidPeripheral.java#L269
```

## Pre-requisites

- Android Things compatible board
- Android device running Android 4.3 (API 18) or later
- Android Studio 2.2+

## Getting Started

1.  Import the project using Android Studio and deploy it to your board.
    The sample will automatically enable the Bluetooth radio, start a GATT
    server, and begin advertising the Current Time Service.
2.  Install the [Android BluetoothLeGatt client](https://github.com/googlesamples/android-BluetoothLeGatt)
    sample on your Android mobile device.
3.  Use the client app to scan and connect to your Android Things board, and
    inspect the services and characteristics exposed by the GATT server.
5.  Read the value of the **Current Time** characteristic (`0x2A2B`).
6.  Register for notifications on the **Current Time** characteristic. The client
    receives an update once per minute with the latest time.
7.  Manually [set the time](#setting-the-time) on your board. The time change
    triggers a notification to the client.

## Setting the Time

You can set the system clock date/time manually on your Android Things board
with the `date` shell command over [ADB](https://developer.android.com/studio/command-line/adb.html).
By default, the command accepts a new date in the `MMddHHmmYYYY.ss` format:

```
# Reboot ADB into root mode
$ adb root

# Set the date to 2017/12/31 12:00:00
$ adb shell date 123112002017.00
```

## Setting the Time Zone

You can set the system time zone manually by updating the `persist.sys.timezone`
system property over [ADB](https://developer.android.com/studio/command-line/adb.html).

```
# Reboot ADB into root mode
$ adb root

# Set the time zone to US Mountain Time
$ adb shell setprop persist.sys.timezone "America/Denver"
```

## License

Copyright 2017 The Android Open Source Project, Inc.

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.
