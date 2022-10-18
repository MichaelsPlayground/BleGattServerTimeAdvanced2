package de.androidcrypto.blegattservertime;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GattServerActivity extends AppCompatActivity {

    private static final String TAG = GattServerActivity.class.getSimpleName();

    /* Local UI */
    private TextView mLocalTimeView, gattLog;
    SwitchMaterial bluetoothEnabled, advertisingActive, deviceConnected;

    /* Bluetooth API */
    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mBluetoothGattServer;
    private static final String ADVERTISING_NAME = "TimeServer2";
    private BluetoothGattCharacteristic mDeviceNameCharacteristic;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    /* Collection of notification subscribers */
    private Set<BluetoothDevice> mRegisteredDevices = new HashSet<>();

    private int batteryLevelValue = 40;
    private int batteryLevelWarnValue = 0;

    // this is needed if you want to add more than one service to the server
    private Queue<BluetoothGattService> servicesToAdd = new LinkedBlockingQueue<>();

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gatt_server);

        bluetoothEnabled = findViewById(R.id.swGattBleEnabled);
        advertisingActive = findViewById(R.id.swGattAdvertisingActive);
        deviceConnected = findViewById(R.id.swGattDeviceConnected);
        mLocalTimeView = (TextView) findViewById(R.id.text_time);
        gattLog = findViewById(R.id.tvGattLog);

        // Devices with a display should not go to sleep
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        // We can't continue without proper Bluetooth support
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            finish();
        }

        // Register for system Bluetooth events
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBluetoothReceiver, filter);
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is currently disabled...enabling");
            addLog("Bluetooth is currently disabled...enabling");
            bluetoothAdapter.enable();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    bluetoothEnabled.setChecked(false);
                }
            });
        } else {
            Log.d(TAG, "Bluetooth enabled...starting services");
            addLog("Bluetooth enabled...starting services");
            startAdvertising();
            startServer();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    bluetoothEnabled.setChecked(true);
                }
            });
        }

        /**
         * The ScheduledExecutorService ist updating the value of batteryLevelValue every 10 seconds
         * and is giving a notification to subscribed devices
         * The batteryLevelValue is in the range of 0 .. 100
         */
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(randomBatteryValueRunnable, 0, 5, TimeUnit.SECONDS);
    }

    Runnable randomBatteryValueRunnable = new Runnable() {
        public void run() {
            //System.out.println("ScheduledTask");
            int rand = getRandomNumberInRange(-5, 5);
            batteryLevelValue = batteryLevelValue + rand;
            if (batteryLevelValue > 100) batteryLevelValue = 100;
            if (batteryLevelValue < 0) batteryLevelValue = 0;
            notifyRegisteredDevicesBattery(batteryLevelValue);
        }
    };

    private static int getRandomNumberInRange(int min, int max) {
        if (min >= max) {
            throw new IllegalArgumentException("max must be greater than min");
        }
        Random r = new Random();
        return r.nextInt((max - min) + 1) + min;
    }

    private void addLog(String message) {
        String newMessage = message + "\n" + gattLog.getText().toString();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                gattLog.setText(newMessage);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        addLog("register system clock events");
        // Register for system clock events
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(mTimeReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        addLog("unregister system clock events");
        unregisterReceiver(mTimeReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        addLog("onDestroy");
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        if (bluetoothAdapter.isEnabled()) {
            stopServer();
            stopAdvertising();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    advertisingActive.setChecked(false);
                }
            });

        }
        unregisterReceiver(mBluetoothReceiver);
    }

    /**
     * Verify the level of Bluetooth support provided by the hardware.
     *
     * @param bluetoothAdapter System {@link BluetoothAdapter}.
     * @return true if Bluetooth is properly supported, false otherwise.
     */
    private boolean checkBluetoothSupport(BluetoothAdapter bluetoothAdapter) {

        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth is not supported");
            addLog("Bluetooth is not supported");
            return false;
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Bluetooth LE is not supported");
            addLog("Bluetooth LE is not supported");
            return false;
        }

        return true;
    }

    /**
     * Listens for system time changes and triggers a notification to
     * Bluetooth subscribers.
     */
    private BroadcastReceiver mTimeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            byte adjustReason;
            switch (intent.getAction()) {
                case Intent.ACTION_TIME_CHANGED:
                    adjustReason = TimeProfile.ADJUST_MANUAL;
                    break;
                case Intent.ACTION_TIMEZONE_CHANGED:
                    adjustReason = TimeProfile.ADJUST_TIMEZONE;
                    break;
                default:
                case Intent.ACTION_TIME_TICK:
                    adjustReason = TimeProfile.ADJUST_NONE;
                    break;
            }
            long now = System.currentTimeMillis();
            notifyRegisteredDevices(now, adjustReason);
            updateLocalUi(now);
        }
    };

    /**
     * Listens for Bluetooth adapter events to enable/disable
     * advertising and server functionality.
     */
    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);

            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    addLog("BluetoothReceiver state: STATE_ON");
                    startAdvertising();
                    startServer();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            advertisingActive.setChecked(true);
                        }
                    });
                    break;
                case BluetoothAdapter.STATE_OFF:
                    addLog("BluetoothReceiver state: STATE_OFF");
                    stopServer();
                    stopAdvertising();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            advertisingActive.setChecked(false);
                        }
                    });
                    break;
                default:
                    // Do nothing
            }

        }
    };

    /**
     * Begin advertising over Bluetooth that this device is connectable
     * and supports the Current Time Service.
     */
    @SuppressLint("MissingPermission")
    private void startAdvertising() {
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        bluetoothAdapter.setName(ADVERTISING_NAME);
        mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (mBluetoothLeAdvertiser == null) {
            Log.w(TAG, "Failed to create advertiser");
            addLog("Failed to create BluetoothLeAdvertiser");
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                //.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setConnectable(true)
                .setTimeout(0)
                //.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(BasicProfile.DEVICE_INFO_SERVICE)) // new
                .addServiceUuid(new ParcelUuid(TimeProfile.TIME_SERVICE))
                .addServiceUuid(new ParcelUuid(BatteryProfile.BATTERY_SERVICE))
                .build();

        mBluetoothLeAdvertiser
                .startAdvertising(settings, data, mAdvertiseCallback);
    }

    /**
     * Stop Bluetooth advertisements.
     */
    @SuppressLint("MissingPermission")
    private void stopAdvertising() {
        if (mBluetoothLeAdvertiser == null) return;
        mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
    }

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Time Profile.
     */
    @SuppressLint("MissingPermission")
    private void startServer() {
        mBluetoothGattServer = mBluetoothManager.openGattServer(this, mGattServerCallback);
        if (mBluetoothGattServer == null) {
            Log.w(TAG, "Unable to create GATT server");
            return;
        }
        /**
         * Important: you cannot add services in a row, you need to wait until the previous
         * add call succeeded
         */
        //mBluetoothGattServer.addService(BasicProfile.createBasicGattService()); // new but fails
        servicesToAdd.add(TimeProfile.createTimeService()); // new
        servicesToAdd.add(BatteryProfile.createBatteryService()); // new
        mBluetoothGattServer.addService(BasicProfile.createBasicGattService());
        // Initialize the local UI
        updateLocalUi(System.currentTimeMillis());
    }

    /**
     * Shut down the GATT server.
     */
    @SuppressLint("MissingPermission")
    private void stopServer() {
        if (mBluetoothGattServer == null) return;

        mBluetoothGattServer.close();
    }

    /**
     * this part is for adding additional services to the server. If you add them without this part
     * you receive randomly an info
     * "BluetoothGattServer: onCharacteristicReadRequest() no char for handle 47"
     * and the new service is not added
     * see: https://stackoverflow.com/questions/49930014/android-peripheral-bluetoothgattservercallback-onserviceadded-not-getting-call
     */

    //private Queue<BluetoothGattService> servicesToAdd = new LinkedBlockingQueue<>();

    /**
     * Add GATT service to gattServer
     *
     * @param service the service
     */
    @SuppressLint("MissingPermission")
    private void addService(final BluetoothGattService service) {
        assert mBluetoothGattServer != null;
        boolean serviceAdded = false;
        while (!serviceAdded) {
            try {
                serviceAdded = mBluetoothGattServer.addService(service);
            } catch (final Exception e) {
                Log.d(TAG, "Adding Service failed", e);
            }
        }
        Log.d(TAG, "Service: " + service.getUuid() + " added.");
    }

    /**
     * Callback to receive information about the advertisement process.
     */
    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "LE Advertise Started.");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    advertisingActive.setChecked(true);
                }
            });
            addLog("LE Advertise Started.");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.w(TAG, "LE Advertise Failed: " + errorCode);
            addLog("LE Advertise Failed: " + errorCode);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    advertisingActive.setChecked(false);
                }
            });
        }
    };

    /**
     * Send a time service notification to any devices that are subscribed
     * to the characteristic.
     */
    @SuppressLint("MissingPermission")
    private void notifyRegisteredDevices(long timestamp, byte adjustReason) {
        if (mRegisteredDevices.isEmpty()) {
            Log.i(TAG, "No subscribers for time service registered");
            addLog("No subscribers registered for time service");
            return;
        }
        byte[] exactTime = TimeProfile.getExactTime(timestamp, adjustReason);

        Log.i(TAG, "Sending update to " + mRegisteredDevices.size() + " subscribers");
        addLog("Sending update to " + mRegisteredDevices.size() + " subscribers");
        for (BluetoothDevice device : mRegisteredDevices) {
            BluetoothGattCharacteristic timeCharacteristic = mBluetoothGattServer
                    .getService(TimeProfile.TIME_SERVICE)
                    .getCharacteristic(TimeProfile.CURRENT_TIME);
            timeCharacteristic.setValue(exactTime);
            mBluetoothGattServer.notifyCharacteristicChanged(device, timeCharacteristic, false);
        }
    }

    /**
     * Send a battery level notification to any devices that are subscribed
     * to the characteristic.
     */
    @SuppressLint("MissingPermission")
    private void notifyRegisteredDevicesBattery(int batteryLevel) {
        if (mRegisteredDevices.isEmpty()) {
            Log.i(TAG, "No subscribers for battery service registered");
            addLog("No subscribers registered for battery service");
            return;
        }

        Log.i(TAG, "Sending update to " + mRegisteredDevices.size() + " subscribers");
        addLog("Sending update to " + mRegisteredDevices.size() + " subscribers");
        for (BluetoothDevice device : mRegisteredDevices) {
            BluetoothGattCharacteristic batteryLevelCharacteristic = mBluetoothGattServer
                    .getService(BatteryProfile.BATTERY_SERVICE)
                    .getCharacteristic(BatteryProfile.BATTERY_LEVEL);
            batteryLevelCharacteristic.setValue(BatteryProfile.getBatteryLevel(batteryLevelValue));
            mBluetoothGattServer.notifyCharacteristicChanged(device, batteryLevelCharacteristic, false);
        }
    }

    /**
     * Update graphical UI on devices that support it with the current time.
     */
    private void updateLocalUi(long timestamp) {
        Date date = new Date(timestamp);
        String displayDate = DateFormat.getMediumDateFormat(this).format(date)
                + "\n"
                + DateFormat.getTimeFormat(this).format(date);
        mLocalTimeView.setText(displayDate);
    }

    /**
     * Callback to handle incoming requests to the GATT server.
     * All read/write requests for characteristics and descriptors are handled here.
     */
    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {

        /**
         * This is for adding additional services, new in Advanced1
         */

        @Override
        public void onServiceAdded(final int status, final BluetoothGattService service) {
            super.onServiceAdded(status, service);
            Log.d(TAG, "onServiceAdded status: " + status + ", service: " + service.getUuid());
            if (status != 0) {
                Log.d(TAG, "onServiceAdded Adding Service failed..");
            }
            if (servicesToAdd.peek() != null) {
                addService(servicesToAdd.remove());
            }
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "BluetoothDevice CONNECTED: " + device);
                addLog("BluetoothDevice CONNECTED: " + device);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        deviceConnected.setChecked(true);
                    }
                });
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "BluetoothDevice DISCONNECTED: " + device);
                addLog("BluetoothDevice DISCONNECTED: " + device);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        deviceConnected.setChecked(false);
                    }
                });
                //Remove device from any active subscriptions
                mRegisteredDevices.remove(device);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            long now = System.currentTimeMillis();

            /**
             * characteristics for Basic Services
             */
            if (BasicProfile.MANUFACTURER_NAME.equals(characteristic.getUuid())) {
                Log.i(TAG, "Read ManufacturerName");
                addLog("Read ManufacturerName");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        BasicProfile.getManufacturerName());
            } else if (BasicProfile.DEVICE_NAME.equals(characteristic.getUuid())) {
                Log.i(TAG, "Read DeviceName");
                addLog("Read DeviceName");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        BasicProfile.getDeviceName());
            } else if (BasicProfile.MODEL_NUMBER.equals(characteristic.getUuid())) {
                Log.i(TAG, "Read ModelNumber");
                addLog("Read ModelNumber");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        BasicProfile.getModelNumber());
            } else if (BasicProfile.SERIAL_NUMBER.equals(characteristic.getUuid())) {
                Log.i(TAG, "Read SerialNumber");
                addLog("Read SerialNumber");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        BasicProfile.getSerialNumber());
                /**
                 * characteristics for Battery Service
                 */
            } else if (BatteryProfile.BATTERY_LEVEL.equals(characteristic.getUuid())) {
                Log.i(TAG, "Read BatteryLevel");
                addLog("Read BatteryLevel");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        BatteryProfile.getBatteryLevel(batteryLevelValue));
            } else if (BatteryProfile.BATTERY_LEVEL_WARN.equals(characteristic.getUuid())) {
                Log.i(TAG, "Read BatteryLevelWarn");
                addLog("Read BatteryLevelWarn");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        BatteryProfile.getBatteryLevelWarn(batteryLevelWarnValue));
                /**
                 * characteristics for Time Server
                 */

            } else if (TimeProfile.CURRENT_TIME.equals(characteristic.getUuid())) {
                Log.i(TAG, "Read CurrentTime");
                addLog("Read CurrentTime");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        TimeProfile.getExactTime(now, TimeProfile.ADJUST_NONE));
            } else if (TimeProfile.LOCAL_TIME_INFO.equals(characteristic.getUuid())) {
                Log.i(TAG, "Read LocalTimeInfo");
                addLog("Read LocalTimeInfo");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        TimeProfile.getLocalTimeInfo(now));
            } else {
                // Invalid characteristic
                Log.w(TAG, "Invalid Characteristic Read: " + characteristic.getUuid());
                addLog("Invalid Characteristic Read: " + characteristic.getUuid());
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null);
            }
        }

        @SuppressLint("MissingPermission")
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {
            if (BatteryProfile.BATTERY_LEVEL_WARN.equals(characteristic.getUuid())) {
                Log.i(TAG, "Write BatteryLevelWarn");
                addLog("WriteRead BatteryLevelWarn");
                batteryLevelWarnValue = (value[0] &0xff);
                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            BatteryProfile.getBatteryLevelWarn(batteryLevelWarnValue));
                }

                /*
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        BatteryProfile.getBatteryLevelWarn(40));

                 */
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                                            BluetoothGattDescriptor descriptor) {
            addLog("onDescriptorReadRequest UUID: " + descriptor.getUuid() + " offset: " + offset);
            if (BatteryProfile.CLIENT_CONFIG.equals(descriptor.getUuid()) & BatteryProfile.BATTERY_LEVEL.equals(descriptor.getCharacteristic().getUuid())) {
                Log.d(TAG, "Config Battery descriptor read");
                addLog("Config Battery descriptor read");
                byte[] returnValue;
                if (mRegisteredDevices.contains(device)) {
                    returnValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                } else {
                    returnValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                }
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        // BluetoothGatt.GATT_FAILURE,
                        0,
                        returnValue);
            }
                else if (TimeProfile.CLIENT_CONFIG.equals(descriptor.getUuid()) & TimeProfile.CURRENT_TIME.equals(descriptor.getCharacteristic().getUuid())) {
                    Log.d(TAG, "Config Time descriptor read");
                    addLog("Config Time descriptor read");
                    byte[] returnValue;
                    if (mRegisteredDevices.contains(device)) {
                        returnValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                    } else {
                        returnValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                    }
                    mBluetoothGattServer.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            // BluetoothGatt.GATT_FAILURE,
                            0,
                            returnValue);
            } else {
                Log.w(TAG, "Unknown descriptor read request");
                addLog("Unknown descriptor read request");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattDescriptor descriptor,
                                             boolean preparedWrite, boolean responseNeeded,
                                             int offset, byte[] value) {

            if (BatteryProfile.CLIENT_CONFIG.equals(descriptor.getUuid()) & BatteryProfile.BATTERY_LEVEL.equals(descriptor.getCharacteristic().getUuid())) {
                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Subscribe device to battery level notifications: " + device);
                    addLog("Subscribe device to battery level notifications: " + device);
                    mRegisteredDevices.add(device);
                } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Unsubscribe device from battery level notifications: " + device);
                    addLog("Unsubscribe device from battery level notifications: " + device);
                    mRegisteredDevices.remove(device);
                }
                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            null);
                }

            } else if (TimeProfile.CLIENT_CONFIG.equals(descriptor.getUuid())  & TimeProfile.CURRENT_TIME.equals(descriptor.getCharacteristic().getUuid())) {
                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Subscribe device to time notifications: " + device);
                    addLog("Subscribe device to time notifications: " + device);
                    mRegisteredDevices.add(device);
                } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Unsubscribe device from time notifications: " + device);
                    addLog("Unsubscribe device from time notifications: " + device);
                    mRegisteredDevices.remove(device);
                }

                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            null);
                }
            } else {
                Log.w(TAG, "Unknown descriptor write request");
                addLog("Unknown descriptor write request");
                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0,
                            null);
                }
            }
        }
    };

}