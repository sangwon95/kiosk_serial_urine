/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.onwards.kiosk.bluetooth;

import android.annotation.SuppressLint;
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
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with
 * a GATT server hosted on a
 * given Bluetooth LE device.
 */

public class BluetoothLeService extends Service {

    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    public final static String ACTION_GATT_CONNECTED           = "com.health.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED        = "com.health.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.health.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE           = "com.health.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA                      = "com.health.bluetooth.le.EXTRA_DATA";
    public final static String EXTRA_DATA_TIME_OUT             = "com.health.bluetooth.le.EXTRA_DATA_TIME_OUT";

    public final static UUID UUID_SERVICE                  =  UUID.fromString(BluetoothGattAttributes.UUID_SERVICE);                 // 유린기 Service UUID
    public final static UUID UUID_NOTIFICATION             =  UUID.fromString(BluetoothGattAttributes.UUID_NOTIFICATION);            // 유린기 NOTIFICATION
    public final static UUID UUID_URINE_ANALYZER           =  UUID.fromString(BluetoothGattAttributes.UUID_URINE_ANALYZER);          // 유린기  측정 UUID
    public final static UUID CLIENT_CHARACTERISTIC_CONFIG  =  UUID.fromString(BluetoothGattAttributes.CLIENT_CHARACTERISTIC_CONFIG); // 유린기  Descriptor

    private String mBluetoothDeviceAddress;
    private StringBuffer stb = new StringBuffer();
    private String buffer;

    private int COUNT_EXECUTION = 0;

    /**
     * App 이 갖는 GATT 이벤트에 대한 콜백 메서드를 구현
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback()
    {
        final Handler handler = new Handler(Looper.getMainLooper());

        /**
         * 연결 상태가 바뀌면 호출됨
         */
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
        {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED)
            {
                intentAction = ACTION_GATT_CONNECTED;
                broadcastUpdate(intentAction);
                Log.d(TAG, "Connected to GATT server.");
                Log.d(TAG, "Attempting to start service discovery:" +  mBluetoothGatt.discoverServices());

            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                Log.d(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }


        /**
         * //BLE 장치에서 GATT 서비스들이 발견되면 호출된다.
         */
        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status)
        {
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                Log.d(TAG, "onServicesDiscovered");

                BluetoothGattService openService       = gatt.getService(UUID_SERVICE);
                BluetoothGattCharacteristic openTxChar = openService.getCharacteristic(UUID_NOTIFICATION); // 데이터 수신 UUID
                gatt.setCharacteristicNotification(openTxChar, true); // Notification 활성화 및 데이터 receive

                BluetoothGattDescriptor descriptor = openTxChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG); //데이터 수신 UUID 에 특성을 설명하는 메타데이타
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); // write했다가 read 할 떄 데이터를 읽기 위한 작업 그래서 descriptor가 필요
                mBluetoothGatt.writeDescriptor(descriptor); // 변경된 UUID descriptor: 설명된 데이타를 Gatt서버에 write한다.
//
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            }
        }


        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
        {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d(TAG, "onCharacteristicWrite");


        }


        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "onCharacteristicRead Execution"); //특성의 결과를 읽어옴
            }
        }

        /**
         * 특성의 값이 바뀔 때
         */
        @SuppressLint("SuspiciousIndentation")
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicChanged Excution");

           // handler.post(() -> {
                Log.d(TAG, "onCharacteristicChanged run Excution");
                try {
                    buffer = new String(characteristic.getValue(), "UTF-8"); // ASCII code 로 데이터 수신
                        //@ 20230403 추가
                        stb.append(buffer.replace("\n", ""));
                        Log.d(TAG,"onCharacteristicChanged:"+stb.toString());

                        if(buffer.contains("#A11")){
                            stb.toString().replaceAll("\n", "");
                            Log.i("replaceAll", stb.toString());
                            //Log.i("urineModel",urineModel.toString());

                            Intent intent = new Intent(ACTION_DATA_AVAILABLE);
                            intent.putExtra(EXTRA_DATA, stb.toString());
                            sendBroadcast(intent); //MainActivity intent

                            stb.delete(0, stb.length());
                            buffer = null;
                    }
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
        }
    };


    /**
     * 인자가 하나인 action DeviceControlActivity 로 Message 날린다.
     */
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    /**
     * 연결된 디바이스에 데이터 쓰기
     * @param characteristic
     * @param data
     * @return
     */
    @SuppressLint("MissingPermission")
    public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] data) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "Bluetooth Adapter not initialized");
            return false;
        }
        characteristic.setValue(data);
        return mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * DeviceControlActivity, BluetoothLeService binding
     */
    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    /**
     * MainActivity에서 BindService로 서비스를 연결하면 onBind 함수가 호출
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    /**
     * 바인딩 해체
     */
    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
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
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    @SuppressLint("MissingPermission")
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) { //null 검사
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection."); //연결을 위해 기존 Gatt사용 시도
            if (mBluetoothGatt.connect()) {
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback); //디바이스와 연결 시도
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    @SuppressLint("MissingPermission")
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    @SuppressLint("MissingPermission")
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    @SuppressLint("MissingPermission")
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) { //연결된 BLE 장치의 특성을 읽어오라는 명령을 내린다.
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
        Log.d(TAG, "readCharacteristic Execution");
    }

    /**
     * 제공 특성에 대한 알림을 활성화하거나 비활성화
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    @SuppressLint("MissingPermission")
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, //BLE 장치가 데이터를 보낼 때를 기다려, 보내면 받아오도록 리스너를 설정한다.
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);/*ENABLE_NOTIFICATION_VALUE*/
        if (UUID_URINE_ANALYZER.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(BluetoothGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() { //BLE 장치에서 제공되는 서비스들을 받아올수 있도록 해준다.
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }


}
