/*
 * Copyright (c) 2012, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *        * Redistributions of source code must retain the above copyright
 *          notice, this list of conditions and the following disclaimer.
 *        * Redistributions in binary form must reproduce the above copyright
 *          notice, this list of conditions and the following disclaimer in the
 *          documentation and/or other materials provided with the distribution.
 *        * Neither the name of Linux Foundation nor
 *          the names of its contributors may be used to endorse or promote
 *          products derived from this software without specific prior written
 *          permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.    IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.findme;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.IBluetoothGattProfile;
import android.bluetooth.IBluetoothLEFindMeServices;
import android.bluetooth.IBluetoothThermometerCallBack;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.util.Log;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGatt;

public class LEFindMeServices extends Service {

    private static final String TAG = "LEFindMeServices";

    private BluetoothAdapter mAdapter;

    private IBluetoothThermometerCallBack srvCallBack = null;

    private boolean mHasStarted = false;

    public static LEFindMeDevice mDevice;

    public static final int GATT_SERVICE_STARTED_OBJ = 1;

    public static final int GATT_DEVICE_CONNECTED = 2;

    public static final int GATT_DEVICE_DISCONNECTED = 3;

    public static final String ACTION_GATT_SERVICE_EXTRA_OBJ = "ACTION_GATT_SERVICE_EXTRA_OBJ";

    public static final String IMMEDIATE_ALERT_SERVICE_UUID = "0000180200001000800000805f9b34fb";

    public static final String ALERT_LEVEL_UUID = "00002a0600001000800000805f9b34fb";

    public static final String FINDME_SERVICE_OPERATION = "FINDME_SERVICE_OPERATION";

    public static final String FINDME_SERVICE_OP_SERVICE_READY = "FINDME_SERVICE_OP_SERVICE_READY";

    public static final String FINDME_SERVICE_OP_STATUS = "FINDME_SERVICE_OP_STATUS";

    public static final String FINDME_SERVICE_OP_VALUE = "FINDME_SERVICE_OP_VALUE";

    public static final String FINDME_SERVICE_UUID = "FINDME_SERVICE_UUID";

    public static final String FINDME_CHAR_UUID = "FINDME_CHAR_UUID";

    public static final String REMOTE_DEVICE = "RemoteDevice";

    private static final byte PROHIBIT_REMOTE_CHG_FALSE = 0;

    private static final byte FILTER_POLICY = 0;

    private static final int PREFERRED_SCAN_INTERVAL = 4;

    private static final int PREFERRED_SCAN_WINDOW = 4;

    private static final int CONNECTION_INTERVAL_MIN = 50;

    private static final int CONNECTION_INTERVAL_MAX = 70;

    private static final int SUPERVISION_TIMEOUT = 192;

    private static final int MIN_CE_LEN = 1;

    private static final int MAX_CE_LEN = 1;

    private static final int CONNECTION_ATTEMPT_TIMEOUT = 30;

    private static final int LATENCY = 0;

    public static BluetoothDevice remoteDevice = null;

    private IntentFilter inFilter = null;

    private LEFindMeReceiver receiver = null;

    public static BluetoothGatt gattProfile = null;

    public final Handler msgHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case GATT_SERVICE_STARTED_OBJ:
                Log.d(TAG, "Received GATT_SERVICE_STARTED_OBJ message");
                ArrayList<String> gattDataList = msg.getData()
                        .getStringArrayList(ACTION_GATT_SERVICE_EXTRA_OBJ);
                int size = gattDataList.size();
                Log.d(TAG, "GATT Service data list len : " + size);
                String selectedServiceObjPath = gattDataList.get(0);
                Log.d(TAG, "GATT Service path array obj : "
                        + selectedServiceObjPath);
                String uuidStr = gattDataList.get(size - 1);
                Log.d(TAG, "GATT Service uuidStr : " + uuidStr);
                ParcelUuid selectedUUID = ParcelUuid.fromString(uuidStr);
                Log.d(TAG, "ParcelUUID rep of selectedUUID : " + selectedUUID);

                if(isFindMeProfileService(selectedUUID)) {
                    Log.d(TAG, "Proceed to creating find me profile gatt service");
                    mDevice.uuidObjPathMap.put(uuidStr + ":" + uuidStr,
                            selectedServiceObjPath);
                    mDevice.objPathUuidMap.put(selectedServiceObjPath, uuidStr
                            + ":" + uuidStr);
                    Log.d(TAG, "getBluetoothGattService");
                    getBluetoothGattService(selectedServiceObjPath, selectedUUID);
                }
                break;
            case GATT_DEVICE_CONNECTED:
                remoteDevice = (BluetoothDevice) msg.getData().getParcelable(REMOTE_DEVICE);
                callFindMeFunctions(convertStrToParcelUUID(IMMEDIATE_ALERT_SERVICE_UUID), remoteDevice);
                break;
            case GATT_DEVICE_DISCONNECTED:
                Log.d(TAG, "Received GATT_SERVICE_DISCONNECTED message");
                remoteDevice = (BluetoothDevice) msg.getData().getParcelable(REMOTE_DEVICE);

                if (mDevice.BDevice.getAddress().equals(remoteDevice.getAddress())) {
                    Log.d(TAG,
                          " received  GATT_SERVICE_DISCONNECTED for device : "
                          + remoteDevice.getAddress());

                    if(mDevice.BDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
                        Log.d(TAG, "Unbonded device. Clear the cache");
                        clearProfileCache();
                    }
                }
                break;
            default:
                break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate FindMe service");
        mAdapter = BluetoothAdapter.getDefaultAdapter();

        if (!mAdapter.getProfileProxy(this, mBluetoothServiceListener,
                BluetoothProfile.GATT)) {
            stopSelf();
            return;
        }

        if (!mHasStarted) {
            mHasStarted = true;
            Log.e(TAG, "Creating find me service");
            int state = mAdapter.getState();
            if (state == BluetoothAdapter.STATE_ON) {
                Log.d(TAG, "Bluetooth is on");
                mDevice = new LEFindMeDevice();
                mDevice.uuidObjPathMap = new HashMap<String, String>();
                mDevice.objPathUuidMap = new HashMap<String, String>();
                mDevice.uuidGattSrvMap = new HashMap<ParcelUuid, BluetoothGattService>();

                Log.d(TAG, "registering receiver handler");
                LEFindMeReceiver.registerHandler(msgHandler);

                inFilter = new IntentFilter();
                inFilter.addAction("android.bluetooth.device.action.GATT");
                inFilter.addAction("android.bluetooth.device.action.ACL_DISCONNECTED");
                inFilter.addAction("android.bluetooth.device.action.ACL_CONNECTED");
                inFilter.addAction("android.bluetooth.device.action.RSSI_UPDATE");

                this.receiver = new LEFindMeReceiver();
                Log.d(TAG, "Registering the receiver");
                this.registerReceiver(this.receiver, inFilter);
            } else {
                Log.d(TAG, "Bluetooth is not on");
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind FindMe service");
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "onRebind FindMe service");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind FindMe service");
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy FindMe service");

        //cancel gatt reconnect request if pending.
        boolean res = gattReconnectCancel();
        Log.d(TAG, "Gatt reconnect cancel : " + res);

        closeFindMeService();
        Log.d(TAG, "Unregistering the receiver");
        if (this.receiver != null) {
            try {
                this.unregisterReceiver(this.receiver);
            } catch (Exception e) {
                Log.e(TAG, "Error while unregistering the receiver");
            }
        }
        mDevice = null;
    }

    private void removeServiceFromCache(ParcelUuid srvUuid) {
        Log.d(TAG, "removing Gatt service for UUID : " + srvUuid);
        mDevice.uuidGattSrvMap.remove(srvUuid);
        String srvUuidStr = srvUuid + ":" + srvUuid;
        Log.d(TAG, "find objPath from uuidObjPathMap key: " + srvUuidStr);
        String objPath = mDevice.uuidObjPathMap.get(srvUuidStr);
        Log.d(TAG, "removing objPath from uuidObjPathMap : " + objPath);
        mDevice.objPathUuidMap.remove(objPath);
        mDevice.uuidObjPathMap.remove(srvUuidStr);
    }

    private void clearProfileCache() {
        Log.d(TAG, "Clearing profile cache");
        mDevice.uuidGattSrvMap.clear();
        mDevice.objPathUuidMap.clear();
        mDevice.uuidObjPathMap.clear();
    }

    private boolean closeFindMeService() {
        boolean closeImmAlert = closeService(convertStrToParcelUUID(IMMEDIATE_ALERT_SERVICE_UUID));
        Log.d(TAG, "Closing the Immediate Alert  service : " + closeImmAlert);
        if(closeImmAlert) {
            clearProfileCache();
        }
        return closeImmAlert;
    }

    private boolean closeService(ParcelUuid srvUuid) {
        if (mDevice != null) {
            BluetoothGattService gattService = mDevice.uuidGattSrvMap.get(srvUuid);
            if (gattService != null) {
                try {
                    Log.d(TAG, "Calling gattService.close()");
                    gattService.close();
                    Log.d(TAG, "removing Gatt service for UUID : " + srvUuid);
                    removeServiceFromCache(srvUuid);
                    return true;
                } catch (Exception e) {
                    Log.e(TAG,
                          "Error while closing the Gatt Service");
                    e.printStackTrace();
                    return false;
                }
            }
        }
        return false;
    }

    // Callbacks to handle connection set up and disconnection clean up.
    private final BluetoothProfile.ServiceListener mBluetoothServiceListener =
            new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.GATT) {
                gattProfile = (BluetoothGatt) proxy;
                if (Log.isLoggable(TAG, Log.DEBUG))
                    Log.d(TAG, "onServiceConnected to profile: " + profile);
            }
        }

        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.GATT) {
                Log.d(TAG, "onServiceDisconnected to profile: " + profile);
                gattProfile = null;
            }
        }
    };

    private final IBluetoothGattProfile.Stub btGattCallback = new IBluetoothGattProfile.Stub() {
        public void onDiscoverCharacteristicsResult(String path, boolean result) {
            Log.d(TAG, "onDiscoverCharacteristicsResult : " + "path : " + path
                    + "result : " + result);
            String srvCharUuid = mDevice.objPathUuidMap.get(path);
            String[] uuids = srvCharUuid.split(":");
            ParcelUuid srvUUID = ParcelUuid.fromString(uuids[0]);
            BluetoothGattService gattService = mDevice.uuidGattSrvMap.get(srvUUID);
            if (result) {
                if (gattService != null) {
                    Log.d(TAG, "gattService.getServiceUuid() ======= "
                            + srvUUID.toString());
                    try {
                        discoverCharacteristics(srvUUID);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.e(TAG, " gattService is null");
                }
            } else {
                Log.e(TAG, "Discover characterisitcs failed ");
            }

        }

        public void onSetCharacteristicValueResult(String path, boolean result) {
        }

        public void onSetCharacteristicCliConfResult(String path, boolean result) {
        }

        public void onValueChanged(String path, String value) {
        }

        public void onUpdateCharacteristicValueResult(String arg0, boolean arg1) {
        }
    };

    private final IBluetoothLEFindMeServices.Stub mBinder =
            new IBluetoothLEFindMeServices.Stub() {
        public synchronized boolean startFindMeService(BluetoothDevice btDevice, ParcelUuid uuid,
                IBluetoothThermometerCallBack callBack)  throws RemoteException {
            int status = -1;
            Log.d(TAG, "Inside startGattService: ");
            if (mDevice == null) {
                Log.e(TAG, "mDevice is null");
                return false;
            }
            if (gattProfile != null && btDevice != null) {
                Log.d(TAG, " Calling connect API with device");
                status = gattProfile.gattConnectLe(btDevice.getAddress(),(byte)PROHIBIT_REMOTE_CHG_FALSE
                        ,(byte)FILTER_POLICY, PREFERRED_SCAN_INTERVAL, PREFERRED_SCAN_WINDOW, CONNECTION_INTERVAL_MIN
                        , CONNECTION_INTERVAL_MAX, LATENCY, SUPERVISION_TIMEOUT, MIN_CE_LEN, MAX_CE_LEN,
                        CONNECTION_ATTEMPT_TIMEOUT);
                Log.d(TAG, "status of connect request::"+status);
                while(status == BluetoothDevice.GATT_RESULT_BUSY) {
                    try {
                        Thread.sleep(3000L);// 3 seconds
                        status = gattProfile.gattConnectLe(btDevice.getAddress(),(byte)PROHIBIT_REMOTE_CHG_FALSE
                                ,(byte)FILTER_POLICY, PREFERRED_SCAN_INTERVAL, PREFERRED_SCAN_WINDOW, CONNECTION_INTERVAL_MIN
                                , CONNECTION_INTERVAL_MAX, LATENCY, SUPERVISION_TIMEOUT, MIN_CE_LEN, MAX_CE_LEN,
                                CONNECTION_ATTEMPT_TIMEOUT);
                    }
                    catch (Exception e) {}
                }
            }
            if (!(mDevice.uuidGattSrvMap.containsKey(uuid))) {
                Log.d(TAG, "Creating new GATT service for UUID : " + uuid);
                srvCallBack = callBack;
            }
            return true;
        }

        public synchronized boolean writeCharacteristicsValue(ParcelUuid uuid,
                ParcelUuid srvUuid, String value)  throws RemoteException {
            Log.d(TAG, "Inside FindMe writeCharacteristics");
            Log.d(TAG, "Compare " + srvUuid + ":" + uuid);
            for (String key : mDevice.uuidObjPathMap.keySet()) {
                Log.d(TAG, "key : " + key);
            }
            return writeFindMeCharValue(uuid, srvUuid, value);
        }

        public synchronized boolean closeFindMeService(ParcelUuid srvUuid)
                throws RemoteException {
            return closeService(srvUuid);
        }
        public synchronized boolean gattConnectCancel() {
            Log.d(TAG, "Find Me gattConnectCancel is called");
            ParcelUuid srvUuid = convertStrToParcelUUID(IMMEDIATE_ALERT_SERVICE_UUID);
            BluetoothGattService gattService = mDevice.uuidGattSrvMap.get(srvUuid);
            if(gattService != null) {
                return gattService.gattConnectCancel();
            }
            Log.d(TAG, "Gatt service does not exist");
            return false;
        }
    };

    private boolean writeFindMeCharValue(ParcelUuid uuid,
            ParcelUuid srvUuid, String value) {
        boolean result = false;
        String writeCharUUID = uuid.toString();
        if (!mDevice.uuidObjPathMap.containsKey(srvUuid + ":" + uuid)) {
            Log.e(TAG, "The character parcel uuid is invalid");
            return false;
        }
        if (convertStrToParcelUUID(ALERT_LEVEL_UUID).toString().equals(
                writeCharUUID)) {
            result = writeAlertLevel(uuid, srvUuid, value, false);
        }
        return result;
    }

    private boolean isFindMeProfileService(ParcelUuid uuid) {
        if(convertStrToParcelUUID(IMMEDIATE_ALERT_SERVICE_UUID).toString().
                equals(uuid.toString())) {
            return true;
        }
        return false;
    }

    private boolean getGattServices(ParcelUuid uuid, BluetoothDevice btDevice) {
        mDevice.BDevice = btDevice;
        Log.d(TAG, "GATT Extra Bt Device : " + mDevice.BDevice);
        Log.d(TAG, "GATT UUID : " + uuid);
        Log.d(TAG, "Calling  btDevice.getGattServices");
        return btDevice.getGattServices(uuid.getUuid());
    }

    private void bundleAndSendResultToApp(ParcelUuid srvUuid, ParcelUuid charUuid, String operation,
            boolean result, ArrayList<String> values) {
        Log.d(TAG, "Inside bundleAndSendResultToApp");
        Log.d(TAG, "find me srvice uuid::"+srvUuid);
        Log.d(TAG, "find me char uuid::"+charUuid);
        Bundle bundle = new Bundle();
        bundle.putParcelable(FINDME_SERVICE_UUID, srvUuid);
        bundle.putParcelable(FINDME_CHAR_UUID, charUuid);
        bundle.putString(FINDME_SERVICE_OPERATION, operation);
        bundle.putBoolean(FINDME_SERVICE_OP_STATUS, result);
        bundle.putStringArrayList(FINDME_SERVICE_OP_VALUE, values);
        try {
            Log.d(TAG, "findMeSrvCallBack.sendResult");
            srvCallBack.sendResult(bundle);
        } catch (RemoteException e) {
            Log.e(TAG, "findMeSrvCallBack.sendResult failed");
            e.printStackTrace();
        }
    }


    private ParcelUuid convertStrToParcelUUID(String uuidStr) {
        return new ParcelUuid(convertUUIDStringToUUID(uuidStr));
    }

    private void getBluetoothGattService(String objPath, ParcelUuid uuid) {
        if ((mDevice != null) && (mDevice.BDevice != null)) {
            Log.d(TAG, " Creating BluetoothGattService with device = "
                    + mDevice.BDevice.getAddress() + " uuid " + uuid.toString()
                    + " objPath = " + objPath);

            BluetoothGattService gattService =
                    new BluetoothGattService(mDevice.BDevice, uuid, objPath, btGattCallback);

            if (gattService != null) {
                mDevice.uuidGattSrvMap.put(uuid, gattService);
                Log.d(TAG, "Adding gatt service to map for : " + uuid
                        + "size :" + mDevice.uuidGattSrvMap.size());
            } else {
                Log.e(TAG, "Gatt service is null for UUID : " + uuid.toString());
            }
        } else {
            Log.e(TAG, " mDevice is null");
        }
    }

    private void discoverCharacteristics(ParcelUuid srvUUID) {
        Log.d(TAG, "Calling gattService.getCharacteristics()");
        ParcelUuid parcelUUID = null;
        BluetoothGattService gattService = mDevice.uuidGattSrvMap.get(srvUUID);
        if (gattService != null) {
            String[] charObjPathArray = gattService.getCharacteristics();
            if (charObjPathArray != null) {
                Log.d(TAG, " charObjPath length " + charObjPathArray.length);
                for (String objPath : Arrays.asList(charObjPathArray)) {
                    parcelUUID = gattService.getCharacteristicUuid(objPath);
                    mDevice.uuidObjPathMap.put(srvUUID + ":" + parcelUUID, objPath);
                    mDevice.objPathUuidMap.put(objPath, srvUUID + ":" + parcelUUID);
                    Log.d(TAG, " Map with key UUID : " + parcelUUID + " value : " + objPath);
                }
                Log.d(TAG, "Created map with size : "+ mDevice.uuidObjPathMap.size());
                bundleAndSendResultToApp(srvUUID, parcelUUID,
                        LEFindMeServices.FINDME_SERVICE_OP_SERVICE_READY,
                        true, new ArrayList<String>());
            } else {
                Log.e(TAG, " gattService.getCharacteristics() returned null");
            }
        } else {
            Log.e(TAG, "Gatt service is null for UUID :" + srvUUID);
        }

    }

    private boolean gattReconnectCancel() {
        ParcelUuid srvUuid = convertStrToParcelUUID(IMMEDIATE_ALERT_SERVICE_UUID);
        BluetoothGattService gattService = mDevice.uuidGattSrvMap.get(srvUuid);
        if(gattService != null) {
            return gattService.gattConnectCancel();
        }
        Log.d(TAG, "Gatt service does not exist");
        return false;
    }

    private boolean writeCharacteristic(ParcelUuid charUUID,
                                        ParcelUuid srvUUID, byte[] data,
                                        boolean writeWithResponse) {
        String objPath = mDevice.uuidObjPathMap.get(srvUUID + ":" + charUUID);
        BluetoothGattService gattService = mDevice.uuidGattSrvMap.get(srvUUID);
        Boolean result;

        if ((objPath == null) || (gattService == null)) {
            Log.e(TAG, "Object is null objPath : " + objPath + " gattService: "
                    + gattService);
            return false;
        }

        Log.d(TAG, "Writing characterisitcs with uuid : " + charUUID
                + " and objPath : " + objPath + "write response : " + writeWithResponse);
        for (int i = 0; i < data.length; i++) {
            Log.d(TAG, "data : " + Integer.toHexString(0xFF & data[i]));
        }
        try {
            result = gattService.writeCharacteristicRaw(objPath, data, writeWithResponse);
            Log.d(TAG, "gattService.writeCharacteristicRaw : " + result);
        } catch (Exception e) {
            result = false;
            e.printStackTrace();
        }
        return result;
    }

    private boolean writeAlertLevel(ParcelUuid uuid, ParcelUuid srvUuid,
                                    String value, boolean writeWithResp) {
        boolean result;
        int intVal;
        try {
            intVal = Integer.parseInt(value);
        } catch (Exception ex) {
            Log.e(TAG, "Invalid value for alert level");
            return false;
        }
        if ((intVal < 0) || (intVal > 2)) {
            Log.d(TAG, "Invalid Alert Value");
            return false;
        }
        byte[] valBytes = new byte[1];
        valBytes[0] = (byte) intVal;
        result = writeCharacteristic(uuid, srvUuid, valBytes, writeWithResp);
        return result;
    }

    private UUID convertUUIDStringToUUID(String UUIDStr) {
        if (UUIDStr.length() != 32) {
            return null;
        }
        String uuidMsB = UUIDStr.substring(0, 16);
        String uuidLsB = UUIDStr.substring(16, 32);

        if (uuidLsB.equals("800000805f9b34fb")) {
            UUID uuid = new UUID(Long.valueOf(uuidMsB, 16), 0x800000805f9b34fbL);
            return uuid;
        } else {
            UUID uuid = new UUID(Long.valueOf(uuidMsB, 16),
                                 Long.valueOf(uuidLsB));
            return uuid;
        }
    }

    private boolean callFindMeFunctions(ParcelUuid uuid, BluetoothDevice btDevice) {
        Log.d(TAG, "callFindMeFunctions");
        if (!(mDevice.uuidGattSrvMap.containsKey(uuid))) {
            Log.d(TAG, "Creating new GATT service for UUID : " + uuid);
            if ((mDevice.BDevice != null)
                && (mDevice.BDevice.getAddress().equals(btDevice.getAddress()))) {
                Log.d(TAG,
                        "services have already been discovered. Create Gatt service");
                String objPath = mDevice.uuidObjPathMap.get(uuid + ":"+ uuid);
                if (objPath != null) {
                    Log.d(TAG, "GET GATT SERVICE for : " + uuid);
                    getBluetoothGattService(objPath, uuid);
                } else {
                    Log.d(TAG, "action GATT has not been received for uuid : " + uuid);
                    return getGattServices(uuid, btDevice);
                }
            } else {
                Log.d(TAG, "Primary services need to be discovered");
                return getGattServices(uuid, btDevice);
            }
        } else {
            Log.d(TAG, "Gatt service and UUID mapping already exists for UUID : " + uuid);
            if(mDevice.uuidGattSrvMap.containsKey(uuid)) {
                BluetoothGattService gattService = mDevice.uuidGattSrvMap.get(uuid);
                if(gattService != null) {
                    boolean isDiscovered = gattService.isDiscoveryDone();
                    Log.d(TAG, "isDiscovered returned : " + isDiscovered);
                    if (isDiscovered) {
                        discoverCharacteristics(uuid);
                    }
                }
                else {
                    Log.d(TAG,"gatt service is null in the hash map");
                }
            }
        }
        return true;
    }
}
