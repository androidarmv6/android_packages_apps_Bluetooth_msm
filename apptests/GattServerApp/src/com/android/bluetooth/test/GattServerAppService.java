/*
* Copyright (c) 2012, Code Aurora Forum. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are
* met:
*    * Redistributions of source code must retain the above copyright
*      notice, this list of conditions and the following disclaimer.
*    * Redistributions in binary form must reproduce the above
*      copyright notice, this list of conditions and the following
*      disclaimer in the documentation and/or other materials provided
*      with the distribution.
*    * Neither the name of Code Aurora Forum, Inc. nor the names of its
*      contributors may be used to endorse or promote products derived
*       from this software without specific prior written permission.

* THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
* ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
* BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
* BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
* WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
* OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
* IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.android.bluetooth.test;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattAppConfiguration;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGattCallback;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import java.util.Date;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

/**
* This class is the service which runs in the background when the
* Gatt Server app is started. This service maintains the attribute
* data in the data structures and processes the different API requests
* from the client device and generates responses to be sent to client device.
*/
public class GattServerAppService extends Service {
    private static final String TAG = "GattServerAppService";

    // Message codes received from the UI client.
    // Register GATT server configuration.
    public static final int MSG_REG_GATT_SERVER_CONFIG = 300;
    // Unregister GATT server configuration.
    public static final int MSG_UNREG_GATT_SERVER_CONFIG = 301;

    public static final int MSG_REG_GATT_SERVER_SUCCESS = 400;
    public static final int MSG_REG_GATT_SERVER_FAILURE = 401;
    public static final int MSG_UNREG_GATT_SERVER_SUCCESS = 500;
    public static final int MSG_UNREG_GATT_SERVER_FAILURE = 501;

    // Message codes received from the UI client.
    // Register client with this service.
    public static final int MSG_REG_CLIENT = 200;

    // Status codes sent back to the UI client.
    // Application registration complete.
    public static final int STATUS_GATT_SERVER_REG = 100;
    // Application unregistration complete.
    public static final int STATUS_GATT_SERVER_UNREG = 101;

    private BluetoothAdapter mBluetoothAdapter;
    private Messenger mClient;

    InputStream raw = null;

    public static ArrayList<Attribute> gattHandleToAttributes;

    public static HashMap<String, Attribute> includedServiceMap = new HashMap<String, Attribute>();

    public static HashMap<String, List<Integer>> gattAttribTypeToHandle =
                new HashMap<String, List<Integer>>();

    public static int serverMinHandle = 0;

    public static int serverMaxHandle = -1;

    private GattServiceParser gattServiceParser = null;

    private BluetoothAdapter bluetoothAdapter = null;

    public Context mContext = null;

    private BluetoothGattAppConfiguration serverConfiguration = null;

    private BluetoothGatt gattProfile = null;

    public static final String BLUETOOTH_BASE_UUID = "0000xxxx00001000800000805f9b34fb";

    // Handles events sent by GattServerAppActivity.
    private class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
                Context context = getApplicationContext();
            CharSequence text = null;
            int duration = 0;
            Toast toast = null;
            switch (msg.what) {
                // Register Gatt Server configuration.
                case MSG_REG_GATT_SERVER_CONFIG:
                    registerApp();
                    break;
                // Unregister Gatt Server configuration.
                case MSG_UNREG_GATT_SERVER_CONFIG:
                    unregisterApp();
                    break;
                case MSG_REG_GATT_SERVER_SUCCESS:
                        text = "GATT Server registration was successful!";
                        duration = Toast.LENGTH_LONG;
                        toast = Toast.makeText(context, text, duration);
                        toast.show();
                        break;
                case MSG_REG_GATT_SERVER_FAILURE:
                        text = "GATT Server registration was not successful!";
                        duration = Toast.LENGTH_LONG;
                        toast = Toast.makeText(context, text, duration);
                        toast.show();
                        break;
                case MSG_UNREG_GATT_SERVER_SUCCESS:
                        text = "GATT Server Unregistration was successful!";
                        duration = Toast.LENGTH_LONG;
                        toast = Toast.makeText(context, text, duration);
                        toast.show();
                        break;
                case MSG_UNREG_GATT_SERVER_FAILURE:
                        text = "GATT Server Unregistration was not successful!";
                        duration = Toast.LENGTH_LONG;
                        toast = Toast.makeText(context, text, duration);
                        toast.show();
                        break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    final Messenger mMessenger = new Messenger(new IncomingHandler());

    /**
     * Make sure Bluetooth and Gatt profile are available on the Android device.  Stop service
     * if they are not available.
     */
    @Override
    public void onCreate() {
        String FILENAME = "genericservice.xml";
        super.onCreate();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            // Bluetooth adapter isn't available.  The client of the service is supposed to
            // verify that it is available and activate before invoking this service.
            stopSelf();
            return;
        }
        if (!mBluetoothAdapter.getProfileProxy(this, mBluetoothServiceListener,
                BluetoothProfile.GATT)) {
            stopSelf();
            return;
        }

        populateGattAttribTypeMap();

        gattServiceParser = new GattServiceParser();
        raw = getResources().openRawResource(R.raw.genericservice);

        if (raw != null) {
            Log.d(TAG, "Inside the Service.. XML is read");
            gattServiceParser.parse(raw);

            //update data structures from characteristic_values file
            updateDataStructuresFromFile();

            Log.d(TAG, "Attribute data list");
            Log.d(TAG, "Messages length : " + gattHandleToAttributes.size());
            for (int i = 0; i < gattHandleToAttributes.size(); i++) {
                Attribute attr = gattHandleToAttributes.get(i);
                Log.d(TAG, "Attirbute handle " + i);
                Log.d(TAG, "Attirbute name : " + attr.name);
                Log.d(TAG, " handle : " + attr.handle);
                Log.d(TAG, " type : " + attr.type);
                Log.d(TAG, " uuid : "+ attr.uuid);
                Log.d(TAG, " permission : " + attr.permission);
                Log.d(TAG, " Permission Bits: " + attr.permBits);
                Log.d(TAG, " properties : " + attr.properties);
                Log.d(TAG, " start handle : " + attr.startHandle);
                Log.d(TAG, " end handle : " + attr.endHandle);
                Log.d(TAG, " ref handle : "     + attr.referenceHandle);
                if (attr.value != null) {
                    Log.d(TAG, "The attribute value is ::");
                    for(int z=0; z < attr.value.length; z++) {
                        Log.d(TAG, ""+attr.value[z]);
                    }
                }
                Log.d(TAG, " min range : " + attr.min_range);
                Log.d(TAG, " max range : " + attr.max_range);
            }
            for(Map.Entry<String, List<Integer>> entry : gattAttribTypeToHandle.entrySet()) {
                Log.d(TAG,"gattAttribTypeToHandle KEY : " + entry.getKey());
                Log.d(TAG,"gattAttribTypeToHandle VALUE : " + entry.getValue());
            }

            Log.d(TAG, "Server MIN RANGE : " + serverMinHandle);
            Log.d(TAG, "Server MAX RANGE : " + serverMaxHandle);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStart Command of GattServerAppService called");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    };

    // Register Gatt server application through Bluetooth Gatt API.
    private void registerApp() {
        Log.d(TAG, "Register Server config called::");
        gattProfile.registerServerConfiguration(
                "GATTServerTest",
                serverMaxHandle+1,
                bluetoothGattCallBack);

    }

    // Unregister Gatt server application through Bluetooth Gatt API.
    private void unregisterApp() {
        Log.d(TAG, "Unregister Server config called::");
        gattProfile.unregisterServerConfiguration(serverConfiguration);
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

    /**
     * Callback to handle application registration, unregistration events and other
     * API requests coming from the client device.
    */
    private final BluetoothGattCallback bluetoothGattCallBack = new BluetoothGattCallback() {
        public void onGattAppConfigurationStatusChange(BluetoothGattAppConfiguration config,
                int status) {
            Log.d(TAG, "onGattAppConfigurationStatusChange: " + config + "Status: " + status);
            serverConfiguration = config;

            switch(status) {
                case BluetoothGatt.GATT_CONFIG_REGISTRATION_SUCCESS:
                        sendMessage(GattServerAppService.MSG_REG_GATT_SERVER_SUCCESS, 0);
                        break;
                case BluetoothGatt.GATT_CONFIG_REGISTRATION_FAILURE:
                        sendMessage(GattServerAppService.MSG_REG_GATT_SERVER_FAILURE, 0);
                        break;
                case BluetoothGatt.GATT_CONFIG_UNREGISTRATION_SUCCESS:
                        sendMessage(GattServerAppService.MSG_UNREG_GATT_SERVER_SUCCESS, 0);
                        break;
                case BluetoothGatt.GATT_CONFIG_UNREGISTRATION_FAILURE:
                        sendMessage(GattServerAppService.MSG_UNREG_GATT_SERVER_FAILURE, 0);
                        break;
            }
        }

        public void onGattActionComplete(String action, int status) {
            Log.d(TAG, "onGattActionComplete: " + action + "Status: " + status);
        }

        /**
         * Processes the Discover Primary Services Request from client and sends the response
         * to the client.
        */
        public void onGattDiscoverPrimaryServiceRequest(BluetoothGattAppConfiguration config,
                        int startHandle, int endHandle, int requestHandle) {
            int j, k, hdlFoundStatus =0;
            int startAttrHdl = 0, endAttrHdl = 0;
            int status = BluetoothGatt.ATT_ECODE_ATTR_NOT_FOUND;
            ParcelUuid uuid = null;
            String uuid1=null;
            boolean retVal;
            List<Integer> hndlList = null;
            if(gattAttribTypeToHandle != null) {
                for(Map.Entry<String, List<Integer>> entry : gattAttribTypeToHandle.entrySet()) {
                    if("00002800-0000-1000-8000-00805F9B34FB".
                                equalsIgnoreCase(entry.getKey().toString())) {
                        //List of primary service handles
                        hndlList = entry.getValue();
                    }
                }
            }
            if(hndlList != null) {
                Log.d(TAG, "Primary handles are there ::");
                for(j=0; j< hndlList.size(); j++) {
                    int handle = hndlList.get(j);
                    if(handle >= 0) {
                        if((handle >= startHandle) && (handle <= endHandle)){
                            hdlFoundStatus = 1;
                            Log.d(TAG, "Primary handle looking for Found ::");
                            if(gattHandleToAttributes != null) {
                                //To get the attribute values for the particular handle
                                for(k=0; k<gattHandleToAttributes.size(); k++) {
                                    if(handle == gattHandleToAttributes.get(k).handle) {
                                        Log.d(TAG, "Primary handle Attributes Found ::");
                                        Attribute attr = gattHandleToAttributes.get(k);
                                        startAttrHdl = attr.startHandle;
                                        endAttrHdl = attr.endHandle;
                                        uuid1 = attr.uuid;
                                        if(attr.uuid!=null) {
                                            uuid = ParcelUuid.fromString(attr.uuid);
                                        }
                                        status = BluetoothGatt.GATT_SUCCESS;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if(hdlFoundStatus == 1) {
                        Log.d(TAG, "Status success ::");
                        status = BluetoothGatt.GATT_SUCCESS;
                        break;
                    }
                    if(j == (hndlList.size()-1)) {
                        Log.d(TAG, "Status failure ::");
                        status = BluetoothGatt.ATT_ECODE_ATTR_NOT_FOUND;
                        break;
                    }
                }
            }
            Log.d(TAG, "Results of onGattDiscoverPrimaryServiceRequest ::");
            Log.d(TAG, "status ::"+status);
            Log.d(TAG, "startAttrHdl ::"+startAttrHdl);
            Log.d(TAG, "endAttrHdl ::"+endAttrHdl);
            Log.d(TAG, "Service uuid Parcel UUId::"+uuid);
            Log.d(TAG, "Service uuid String ::"+uuid1);

            retVal = gattProfile.discoverPrimaryServiceResponse(config, requestHandle, status,
                        startAttrHdl, endAttrHdl, uuid);
            Log.d(TAG, "onGattDiscoverPrimaryServiceRequest: " + retVal);
        }

        /**
         * Processes the Discover Primary Services by UUID Request from client and sends the
         * response to the client.
        */
        public void onGattDiscoverPrimaryServiceByUuidRequest(BluetoothGattAppConfiguration config,
                        int startHandle, int endHandle, ParcelUuid uuid, int requestHandle) {
            int j, k, hdlFoundStatus =0;
            int startAttrHdl = 0, endAttrHdl = 0;
            int status = BluetoothGatt.ATT_ECODE_ATTR_NOT_FOUND;
            boolean retVal;
            List<Integer> hndlList = null;
            if(gattAttribTypeToHandle != null) {
                Log.d(TAG, "Handles available ::");
                for(Map.Entry<String, List<Integer>> entry : gattAttribTypeToHandle.entrySet()) {
                    if("00002800-0000-1000-8000-00805F9B34FB".
                                equalsIgnoreCase(entry.getKey().toString())) {
                        //List of primary service handles
                        hndlList = entry.getValue();
                    }
                }
            }
            if(hndlList != null) {
                Log.d(TAG, "Primary Handles available ::");
                for(j=0; j< hndlList.size(); j++) {
                    int handle = hndlList.get(j);
                    if(handle >= 0) {
                        if((handle >= startHandle) && (handle <= endHandle)){
                            Log.d(TAG, "Primary Handle within range available ::");
                            if(gattHandleToAttributes != null) {
                                //To get the attribute values for the particular handle
                                for(k=0; k<gattHandleToAttributes.size(); k++) {
                                    if(handle == gattHandleToAttributes.get(k).handle) {
                                        Attribute attr = gattHandleToAttributes.get(k);
                                        startAttrHdl = attr.startHandle;
                                        endAttrHdl = attr.endHandle;
                                        if(attr.uuid != null &&
                                                        attr.uuid.equalsIgnoreCase(uuid.toString())) {
                                            Log.d(TAG, "Primary Handle with UUID available ::");
                                            hdlFoundStatus = 1;
                                            status = BluetoothGatt.GATT_SUCCESS;
                                            break;
                                        }

                                    }
                                }
                            }
                        }
                    }
                    if(hdlFoundStatus == 1) {
                        Log.d(TAG, "Primary Handle found, success ::");
                        status = BluetoothGatt.GATT_SUCCESS;
                        break;
                    }
                    if(j == (hndlList.size()-1)) {
                        Log.d(TAG, "Primary Handle not found, failure ::");
                        status = BluetoothGatt.ATT_ECODE_ATTR_NOT_FOUND;
                        break;
                    }
                }
            }
            Log.d(TAG, "results of onGattDiscoverPrimaryServiceByUuidRequest ::");
            Log.d(TAG, "status ::"+status);
            Log.d(TAG, "startAttrHdl ::"+startAttrHdl);
            Log.d(TAG, "endAttrHdl ::"+endAttrHdl);
            Log.d(TAG, "Service uuid Parcel UUId::"+uuid);

            retVal = gattProfile.discoverPrimaryServiceByUuidResponse(config, requestHandle, status,
                        startAttrHdl, endAttrHdl, uuid);
            Log.d(TAG, "onGattDiscoverPrimaryServiceByUuidRequest: " + retVal);
        }

        /**
         * Processes the Find Included Services Request from client and sends the response
         * to the client.
        */
        public void onGattFindIncludedServiceRequest(BluetoothGattAppConfiguration config,
                        int startHandle, int endHandle, int requestHandle) {
            int j, k, hdlFoundStatus =0;
            int inclSvcHdl = 0, startInclSvcHdl = 0, endInclSvcHdl = 0;
            int status = BluetoothGatt.ATT_ECODE_ATTR_NOT_FOUND;
            boolean retVal;
            String svcUuid, inclSvcUuid = null;
            ParcelUuid pInclSvcUuid = null;
            List<Integer> hndlList = null;
            if(gattAttribTypeToHandle != null) {
                for(Map.Entry<String, List<Integer>> entry : gattAttribTypeToHandle.entrySet()) {
                    if("00002802-0000-1000-8000-00805F9B34FB".
                                equalsIgnoreCase(entry.getKey().toString())) {
                        //List of included service handles
                        hndlList = entry.getValue();
                    }
                }
            }
            if(hndlList != null) {
                Log.d(TAG, "Included service handles available::");
                for(j=0; j< hndlList.size(); j++) {
                    int handle = hndlList.get(j);
                    if(handle >= 0) {
                        if((handle >= startHandle) && (handle <= endHandle)){
                            Log.d(TAG, "Included service handle within range available::");
                            hdlFoundStatus = 1;
                            if(gattHandleToAttributes != null) {
                                //To get the attribute values for the particular handle
                                for(k=0; k<gattHandleToAttributes.size(); k++) {
                                    if(handle == gattHandleToAttributes.get(k).handle) {
                                        Attribute attr = gattHandleToAttributes.get(k);
                                        svcUuid = attr.uuid;
                                        inclSvcHdl = attr.handle;
                                        startInclSvcHdl = attr.startHandle;
                                        endInclSvcHdl = attr.endHandle;
                                        inclSvcUuid = attr.uuid;
                                        pInclSvcUuid = ParcelUuid.fromString(inclSvcUuid);
                                        status = BluetoothGatt.GATT_SUCCESS;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if(hdlFoundStatus == 1) {
                        Log.d(TAG, "Included service handle found, success::");
                        status = BluetoothGatt.GATT_SUCCESS;
                        break;
                    }
                    if(j == (hndlList.size()-1)) {
                        Log.d(TAG, "Included service handle not found, failure::");
                        status = BluetoothGatt.ATT_ECODE_ATTR_NOT_FOUND;
                        break;
                    }
                }
            }
            Log.d(TAG, "results of onGattFindIncludedServiceRequest ::");
            Log.d(TAG, "status ::"+status);
            Log.d(TAG, "inclSvcHdl ::"+inclSvcHdl);
            Log.d(TAG, "startInclSvcHdl ::"+startInclSvcHdl);
            Log.d(TAG, "endInclSvcHdl ::"+endInclSvcHdl);
            Log.d(TAG, "Service uuid str::"+inclSvcUuid);
            Log.d(TAG, "Service uuid Parcel UUId::"+pInclSvcUuid);

            retVal = gattProfile.findIncludedServiceResponse(config, requestHandle, status,
                        inclSvcHdl, startInclSvcHdl, endInclSvcHdl, pInclSvcUuid);
            Log.d(TAG, "onGattFindIncludedServiceRequest: " + retVal);
        }

        /**
         * Processes the Discover Characteristic Descriptors Request from client and sends the
         * response to the client.
        */
        public void onGattDiscoverCharacteristicDescriptorRequest(BluetoothGattAppConfiguration
                        config, int startHandle, int endHandle, int requestHandle) {
            int k, hdlFoundStatus =0;
            int status = BluetoothGatt.ATT_ECODE_ATTR_NOT_FOUND;
            boolean retVal;
            ParcelUuid descUuid = null;
            int attrCurrHandle, attrNextHandle=0;
            int descHandle=0;
            Attribute attrNext = null;
            Attribute attrPrev = null;
            if(gattHandleToAttributes != null) {
                Log.d(TAG, "Inside gattHandleToAttributes ::");
                //To get the attribute values
                for(k=0; k<gattHandleToAttributes.size(); k++) {
                        attrNext = null;
                        attrPrev = null;
                    Attribute attrCurr = gattHandleToAttributes.get(k);
                    if(attrCurr.handle >= startHandle && attrCurr.handle <= endHandle) {
                        if(k > 0) {
                            attrPrev = gattHandleToAttributes.get(k-1);
                        }
                        if(k < (gattHandleToAttributes.size()-1)) {
                            attrNext = gattHandleToAttributes.get(k+1);
                            attrNextHandle = attrNext.handle;
                        }
                        attrCurrHandle = attrCurr.handle;

                        //If the previous attribute is a Characteristic definition which always
                        //has an UUID
                        if(attrPrev!= null && attrPrev.uuid != null &&
                                !attrPrev.uuid.equalsIgnoreCase("")
                                && attrPrev.uuid.length() > 0
                                && attrPrev.type.
                                equalsIgnoreCase("00002803-0000-1000-8000-00805F9B34FB")) {
                            Log.d(TAG, "Previous Attribute is a Characteristic ::");
                            if(attrNext != null && attrNextHandle >= startHandle && attrNextHandle <= endHandle &&
                                        (attrNext.uuid == null || attrNext.uuid.length() == 0
                                        || attrNext.uuid.equalsIgnoreCase(""))) {
                                Log.d(TAG, "Next attr handle"+attrNextHandle);
                                Log.d(TAG, "Next attr UUID"+attrNext.uuid);
                                Log.d(TAG, "Curr attr handle"+attrCurr.handle);
                                Log.d(TAG, "Curr attr UUID"+attrCurr.uuid);
                                Log.d(TAG, "Prev attr handle"+attrPrev.handle);
                                Log.d(TAG, "Prev attr UUID"+attrPrev.uuid);
                                descHandle = attrNextHandle;
                                descUuid = ParcelUuid.fromString(attrNext.type);
                                hdlFoundStatus = 1;
                                status = BluetoothGatt.GATT_SUCCESS;
                                Log.d(TAG, "Descriptor Handle after characteristic found ::");
                                break;
                            }
                        }
                        //If the previous attribute is a Descriptor which does not have an UUID
                        else if(attrPrev!=null && (attrPrev.uuid == null ||
                                attrPrev.uuid.equalsIgnoreCase("") ||
                                (attrPrev.uuid.length() == 0)) && (attrCurr!=null
                                && (attrCurr.uuid == null || attrCurr.uuid.equalsIgnoreCase("") ||
                                attrCurr.uuid.length() == 0))) {
                            Log.d(TAG, "Previous Attribute is a descriptor ::");
                            if(attrCurrHandle >= startHandle && attrCurrHandle <= endHandle) {
                                descHandle = attrCurrHandle;
                                descUuid = ParcelUuid.fromString(attrCurr.type);
                                hdlFoundStatus = 1;
                                status = BluetoothGatt.GATT_SUCCESS;
                                Log.d(TAG, "Descriptor Handle after another descriptor found ::");
                                break;
                            }
                        }
                    }
                    if(k == (gattHandleToAttributes.size()-1)) {
                        Log.d(TAG, "Descriptor handle not found, failure::");
                        status = BluetoothGatt.ATT_ECODE_ATTR_NOT_FOUND;
                        break;
                    }
                }
            }

            Log.d(TAG, "Results of onGattDiscoverCharacteristicDescriptorRequest ::");
            Log.d(TAG, "status ::"+status);
            Log.d(TAG, "Descriptor Handle ::"+descHandle);
            Log.d(TAG, "Descriptor UUID::"+descUuid);

            retVal = gattProfile.discoverCharacteristicDescriptorResponse(config, requestHandle,
                        status, descHandle, descUuid);
            Log.d(TAG, "onGattDiscoverCharacteristicDescriptorRequest: " + retVal);
        }

        /**
         * Processes the Discover Characteristics Request from client and sends the response
         * to the client.
        */
        public void onGattDiscoverCharacteristicRequest(BluetoothGattAppConfiguration config,
                        int startHandle, int endHandle, int requestHandle) {
            int j, k, hdlFoundStatus =0;
            int charHdl = 0, charValueHdl = 0;
            int status = BluetoothGatt.ATT_ECODE_ATTR_NOT_FOUND;
            ParcelUuid charUuid = null;
            byte charProperty = 0;
            boolean retVal;
            List<Integer> hndlList = null;
            if(gattAttribTypeToHandle != null) {
                Log.d(TAG, "Inside gattAttributeTypeToHandle ::");
                for(Map.Entry<String, List<Integer>> entry : gattAttribTypeToHandle.entrySet()) {
                    if("00002803-0000-1000-8000-00805F9B34FB".
                                equalsIgnoreCase(entry.getKey().toString())) {
                        //List of characteristic handles
                        hndlList = entry.getValue();
                    }
                }
            }
            if(hndlList != null) {
                Log.d(TAG, "Characteristic handles are there ::");
                for(j=0; j< hndlList.size(); j++) {
                    int handle = hndlList.get(j);
                    if(handle >= 0) {
                        if((handle >= startHandle) && (handle <= endHandle)){
                            hdlFoundStatus = 1;
                            Log.d(TAG, "Characteristic handle looking for Found ::");
                            if(gattHandleToAttributes != null) {
                                //To get the attribute values for the particular handle
                                for(k=0; k<gattHandleToAttributes.size(); k++) {
                                    if(handle == gattHandleToAttributes.get(k).handle) {
                                        Log.d(TAG, "Characteristic handle Attributes Found ::");
                                        Attribute attr = gattHandleToAttributes.get(k);
                                        charHdl = attr.handle;
                                        charProperty = (byte)attr.properties;
                                        if(attr.uuid!=null) {
                                            charUuid = ParcelUuid.fromString(attr.uuid);
                                        }
                                        if((k+1) < gattHandleToAttributes.size()) {
                                            charValueHdl = gattHandleToAttributes.get(k+1).handle;
                                        }
                                        status = BluetoothGatt.GATT_SUCCESS;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if(hdlFoundStatus == 1) {
                        Log.d(TAG, "Status success ::");
                        status = BluetoothGatt.GATT_SUCCESS;
                        break;
                    }
                    if(j == (hndlList.size()-1)) {
                        Log.d(TAG, "Status failure ::");
                        status = BluetoothGatt.ATT_ECODE_ATTR_NOT_FOUND;
                        break;
                    }
                }
            }
            Log.d(TAG, "Results of onGattDiscoverCharacteristicRequest ::");
            Log.d(TAG, "status ::"+status);
            Log.d(TAG, "Characteristic Handle ::"+charHdl);
            Log.d(TAG, "Characteristic Property ::"+charProperty);
            Log.d(TAG, "Characteristic Vlaue Handle::"+charValueHdl);
            Log.d(TAG, "Characteristic UUID ::"+charUuid);

            retVal = gattProfile.discoverCharacteristicResponse(config, status, requestHandle,
                        charHdl, charProperty, charValueHdl, charUuid);
            Log.d(TAG, "onGattDiscoverCharacteristicRequest: " + retVal);
        }

        /**
         * Processes the Read By Attribute Type Request from client and sends the response
         * to the client.
        */
        public void onGattReadByTypeRequest(BluetoothGattAppConfiguration config, ParcelUuid uuid,
                int startHandle, int endHandle, String authentication, int requestHandle) {
            int i, j, k, hdlFoundStatus=0, status = BluetoothGatt.ATT_ECODE_ATTR_NOT_FOUND;
            boolean retVal;

            String attrUuidStr;
            int attrHandle=-1, startAttrHdl =-1, endAttrHdl=-1, attrHandleNext =-1;
            String uuidStr=null;
            byte attrPermission=0;
            byte[] payload = null;
            byte attrProperties = 0;
            String attrUuidPrev = null;
            String attrTypePrev = null;
            byte[] attrValue = null;
            String attrTypeStr = null;

            List<Integer> hndlList = null;
            Log.d(TAG, "Parcel UUID passed ::"+uuid);
            String attributeType = uuid.toString();
            int charValueAttrType = 0;
            boolean is_permission_available = false;

            //update data structures from characteristic_values file
            updateDataStructuresFromFile();

            if(gattAttribTypeToHandle != null) {
                for(Map.Entry<String, List<Integer>> entry : gattAttribTypeToHandle.entrySet()) {
                    if(attributeType.equalsIgnoreCase(entry.getKey().toString())) {
                        //List of attribute type handles
                        hndlList = entry.getValue();
                    }
                }
            }
            if(hndlList != null) {
                for(j=0; j< hndlList.size(); j++) {
                    int handle = hndlList.get(j);
                    if(handle >= 0) {
                        if((handle >= startHandle) && (handle <= endHandle)){
                            hdlFoundStatus = 1;
                            Log.d(TAG, "Handle of the requested attr type Found ::");
                            if(gattHandleToAttributes != null) {
                                //To get the attribute values for the particular handle
                                for(k=0; k<gattHandleToAttributes.size(); k++) {
                                    if(handle == gattHandleToAttributes.get(k).handle) {
                                        Attribute attr = gattHandleToAttributes.get(k);
                                        attrTypeStr = attr.type;
                                        attrPermission = attr.permBits;

                                        //if the attribute value is authorized/authenticated
                                        if((attrPermission > 0) &&
                                                ((attrPermission & 0x01) == 0x01)) {
                                            if(authentication.equalsIgnoreCase("Authenticated") ||
                                                    authentication.equalsIgnoreCase("Authorized")) {
                                                is_permission_available = true;
                                                Log.d(TAG, "Inside read with authorization");
                                            }
                                        }
                                        else if((attrPermission > 0) &&
                                                ((attrPermission & 0x02) == 0x02)) {
                                            if(authentication.equalsIgnoreCase("Authenticated")) {
                                                is_permission_available = true;
                                                Log.d(TAG, "Inside read with authentication");
                                            }
                                        }
                                        else if(attrPermission == 0 ||
                                                attrPermission == 0x04 ||
                                                authentication.equalsIgnoreCase("None")) {
                                            is_permission_available = true;
                                            Log.d(TAG, "Read without auth/authorization");
                                        }
                                        if((k+1) < gattHandleToAttributes.size()) {
                                            attrHandleNext= gattHandleToAttributes.
                                                    get(k+1).handle;
                                        }
                                        if((k-1) >= 0) {
                                            attrUuidPrev = gattHandleToAttributes.get(k-1).uuid;
                                            attrTypePrev = gattHandleToAttributes.get(k-1).type;
                                            if(attrTypeStr.equalsIgnoreCase(attrUuidPrev) &&
                                                    attrTypePrev.
                                                    equalsIgnoreCase("00002803-0000-1000-8000-00805F9B34FB")) {
                                                charValueAttrType = 1;
                                                attrProperties = (byte)gattHandleToAttributes.get(k-1).properties;
                                            }
                                            else {
                                                attrProperties = (byte)attr.properties;
                                            }
                                        }
                                        if(is_permission_available) {
                                            if(attrTypeStr !=null &&
                                                    (attrTypeStr.equalsIgnoreCase("00002800-0000-1000-8000-00805F9B34FB") ||
                                                    attrTypeStr.equalsIgnoreCase("00002801-0000-1000-8000-00805F9B34FB") ||
                                                    attrTypeStr.equalsIgnoreCase("00002802-0000-1000-8000-00805F9B34FB"))) {
                                                attrHandle = attr.handle;
                                                startAttrHdl = attr.startHandle;
                                                endAttrHdl = attr.endHandle;
                                                attrValue = attr.value;
                                                uuidStr = attr.uuid;
                                                status = BluetoothGatt.GATT_SUCCESS;
                                            }
                                            else if(attrTypeStr !=null &&
                                                    !(attrTypeStr.equalsIgnoreCase("00002800-0000-1000-8000-00805F9B34FB") ||
                                                    attrTypeStr.equalsIgnoreCase("00002801-0000-1000-8000-00805F9B34FB") ||
                                                    attrTypeStr.equalsIgnoreCase("00002802-0000-1000-8000-00805F9B34FB"))){
                                                    //need to check whether attribute is readable before reading
                                                if((attrProperties > 0) && ((attrProperties & 0x02) == 0x02)) {
                                                    attrHandle = attr.handle;
                                                    startAttrHdl = attr.startHandle;
                                                    endAttrHdl = attr.endHandle;
                                                    attrValue = attr.value;
                                                    uuidStr = attr.uuid;
                                                    status = BluetoothGatt.GATT_SUCCESS;
                                                }
                                                else {
                                                    status = BluetoothGatt.ATT_ECODE_READ_NOT_PERM;
                                                }
                                            }
                                        }
                                        else {
                                            status = BluetoothGatt.ATT_ECODE_AUTHORIZATION;
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if(hdlFoundStatus == 1) {
                        //status should be success only when authentication levels are satisfied
                        if(is_permission_available) {
                            if(attrTypeStr !=null &&
                                    (attrTypeStr.equalsIgnoreCase("00002800-0000-1000-8000-00805F9B34FB") ||
                                    attrTypeStr.equalsIgnoreCase("00002801-0000-1000-8000-00805F9B34FB") ||
                                    attrTypeStr.equalsIgnoreCase("00002802-0000-1000-8000-00805F9B34FB"))) {
                                Log.d(TAG, "Status success ::");
                                status = BluetoothGatt.GATT_SUCCESS;
                            }
                            else if(attrTypeStr !=null &&
                                    !(attrTypeStr.equalsIgnoreCase("00002800-0000-1000-8000-00805F9B34FB") ||
                                    attrTypeStr.equalsIgnoreCase("00002801-0000-1000-8000-00805F9B34FB") ||
                                    attrTypeStr.equalsIgnoreCase("00002802-0000-1000-8000-00805F9B34FB"))){
                                    //need to check whether attribute is readable before reading
                                if((attrProperties > 0) && ((attrProperties & 0x02) == 0x02)) {
                                    Log.d(TAG, "Status success ::");
                                    status = BluetoothGatt.GATT_SUCCESS;
                                }
                            }
                        }
                        break;
                    }
                    else if(j == (hndlList.size()-1)) {
                        Log.d(TAG, "Status failure ::");
                        status = BluetoothGatt.ATT_ECODE_ATTR_NOT_FOUND;
                        break;
                    }
                }
            }

            if(is_permission_available) {
                if(attributeType != null && attributeType.length()>0) {
                    //Primary Service definition
                    if(attributeType.equalsIgnoreCase("00002800-0000-1000-8000-00805F9B34FB") ||
                            attributeType.equalsIgnoreCase("00002801-0000-1000-8000-00805F9B34FB")){
                        Log.d(TAG,"Inside Primary services payload creation::");
                        int cnt =0;
                        payload = new byte[16];
                        //Primary service UUID
                        uuidStr = removeChar(uuidStr, '-');
                        Log.d(TAG, "The replaced Str::"+uuidStr);
                        byte[] bytes = new byte[16];
                        bytes = hexStringToByteArray(uuidStr);

                        if(bytes!=null) {
                            for(i=((bytes.length)-1);i >= 0; i--) {
                                payload[cnt++] = bytes[i];
                            }
                        }
                        Log.d(TAG,"The payload data::");
                        if(payload != null) {
                            for(i=0; i < payload.length; i++) {
                                Log.d(TAG,"\n"+payload[i]);
                            }
                        }
                    }
                    //Included Service definition
                    else if(attributeType.equalsIgnoreCase("00002802-0000-1000-8000-00805F9B34FB")){
                        Log.d(TAG,"Inside Included services payload creation::");
                        int cnt =0;
                        payload = new byte[20];
                        //Included service attr handle
                        byte startHdlLsb = (byte)(startAttrHdl & 0x00FF);
                        byte startHdlMsb = (byte)((startAttrHdl & 0xFF00) >> 8);
                        Log.d(TAG, "The LSB value in included service ::"+startHdlLsb);
                        Log.d(TAG, "The MSB value in included service::"+startHdlMsb);
                        payload[cnt++] = startHdlLsb;
                        payload[cnt++] = startHdlMsb;

                        //End group handle
                        byte endHdlLsb = (byte)(endAttrHdl & 0x00FF);
                        byte endHdlMsb = (byte)((endAttrHdl & 0xFF00) >> 8);
                        Log.d(TAG, "The LSB value in included service ::"+endHdlLsb);
                        Log.d(TAG, "The MSB value in included service::"+endHdlMsb);
                        payload[cnt++] = endHdlLsb;
                        payload[cnt++] = endHdlMsb;

                        //service uuid
                        uuidStr = removeChar(uuidStr, '-');
                        Log.d(TAG, "The replaced Str::"+uuidStr);
                        byte[] bytes = new byte[16];
                        bytes = hexStringToByteArray(uuidStr);

                        if(bytes!=null) {
                            for(i=((bytes.length)-1);i >= 0; i--) {
                                payload[cnt++] = bytes[i];
                            }
                        }

                        Log.d(TAG,"The payload data::");
                        if(payload != null) {
                            for(i=0; i < payload.length; i++) {
                                Log.d(TAG,"\n"+payload[i]);
                            }
                        }
                    }
                    //Characteristic declaration
                    else if(attributeType.equalsIgnoreCase("00002803-0000-1000-8000-00805F9B34FB")){
                        Log.d(TAG,"Inside Characteristic payload creation::");
                        if(((attrProperties > 0) && ((attrProperties & 0x02) == 0x02))) {
                            int cnt = 0;
                            payload = new byte[19];

                            //Characteristic properties
                            payload[cnt++] = attrProperties;

                            //Characteristic value attribute handle
                            byte hdlLsb = (byte)(attrHandleNext & 0x00FF);
                            byte hdlMsb = (byte)((attrHandleNext & 0xFF00) >> 8);
                            Log.d(TAG, "The LSB value in included service ::"+hdlLsb);
                            Log.d(TAG, "The MSB value in included service::"+hdlMsb);
                            payload[cnt++] = hdlLsb;
                            payload[cnt++] = hdlMsb;

                            //Characteristic uuid
                            uuidStr = removeChar(uuidStr, '-');
                            Log.d(TAG, "The replaced Str::"+uuidStr);
                            byte[] bytes = new byte[16];
                            bytes = hexStringToByteArray(uuidStr);

                            if(bytes!=null) {
                                for(i=((bytes.length)-1);i >= 0; i--) {
                                payload[cnt++] = bytes[i];
                                }
                            }

                            Log.d(TAG,"The payload data::");
                            if(payload != null) {
                                for(i=0; i < payload.length; i++) {
                                    Log.d(TAG,"\n"+payload[i]);
                                }
                            }
                        }
                    }
                    //Characteristic Value declaration/ Descriptors
                    else if(charValueAttrType == 1 ||
                                attributeType.equalsIgnoreCase("00002902-0000-1000-8000-00805F9B34FB")
                                || attributeType.equalsIgnoreCase("00002900-0000-1000-8000-00805F9B34FB")) {
                        Log.d(TAG,"Inside characteristic Value payload creation::");
                        Log.d(TAG,"Inside Client Characteristic descriptor payload creation::");
                        Log.d(TAG,"Inside Characteristic Extended Properties payload creation::");
                        if(((attrProperties > 0) && ((attrProperties & 0x02) == 0x02))) {
                            List<Byte> byteArrList= new ArrayList<Byte>();
                            int cnt = 0;
                            //Characteristic Value
                            if(attrValue!=null && attrValue.length > 0) {
                                for(i=0; i< attrValue.length; i++) {
                                    byteArrList.add(attrValue[i]);
                                }
                            }
                            payload = new byte[byteArrList.size()];
                            //Transfer Arraylist contents to byte array
                            for(i=(byteArrList.size()-1); i >= 0; i--) {
                                    payload[cnt++] = byteArrList.get(i).byteValue();
                            }
                            Log.d(TAG,"The payload data::");
                            if(payload != null) {
                                for(i=0; i < payload.length; i++) {
                                    Log.d(TAG,"\n"+payload[i]);
                                }
                            }
                        }
                    }
                }
            }

            retVal = gattProfile.readByTypeResponse(config, requestHandle, status, uuid,
                        attrHandle, payload);
            Log.d(TAG, "onGattReadByTypeRequest: " + retVal);
        }

        /**
         * Processes the Read Request from client and sends the response
         * to the client.
        */
        public void onGattReadRequest(BluetoothGattAppConfiguration config, int handle,
                        String authentication, int requestHandle) {
            int i, k, hdlFoundStatus=0, status = BluetoothGatt.ATT_ECODE_ATTR_NOT_FOUND;
            boolean retVal;
            byte[] payload = null;
            byte[] attrValue = null;
            String attrTypeStr = null;
            ParcelUuid uuid = null;
            byte attrPermission = 0;
            boolean is_permission_available = false;
            String attrUuidPrev, attrTypePrev;
            byte attrProperties = 0;
            int charValueAttrType = 0;
            int startAttrHdl = -1;
            int endAttrHdl = -1;
            String uuidStr = null;
            int attrHandleNext = -1;

            //update data structures from characteristic_values file
            updateDataStructuresFromFile();

            if(handle >= 0) {
                if(gattHandleToAttributes != null) {
                    //To get the attribute values for the particular handle
                    for(k=0; k<gattHandleToAttributes.size(); k++) {
                        if(handle == (int)gattHandleToAttributes.get(k).handle) {
                            hdlFoundStatus = 1;
                            Attribute attr = gattHandleToAttributes.get(k);
                            attrPermission = attr.permBits;
                            attrTypeStr = attr.type;
                            //if the attribute value is authorized/authenticated
                            if((attrPermission > 0) && ((attrPermission & 0x01) == 0x01)) {
                                if(authentication.equalsIgnoreCase("Authenticated") ||
                                        authentication.equalsIgnoreCase("Authorized")) {
                                    is_permission_available = true;
                                    Log.d(TAG, "Inside read with authorization");
                                }
                            }
                            else if((attrPermission > 0) && ((attrPermission & 0x02) == 0x02)) {
                                if(authentication.equalsIgnoreCase("Authenticated")) {
                                    is_permission_available = true;
                                    Log.d(TAG, "Inside read with authentication");
                                }
                            }
                            else if(attrPermission == 0 || attrPermission == 0x04 ||
                                    authentication.equalsIgnoreCase("None")) {
                                is_permission_available = true;
                                Log.d(TAG, "Inside read without authentication and authorization");
                            }
                            if((k+1) < gattHandleToAttributes.size()) {
                                attrHandleNext= gattHandleToAttributes.
                                        get(k+1).handle;
                            }
                            if((k-1) >= 0) {
                                attrTypeStr = attr.type;
                                attrUuidPrev = gattHandleToAttributes.get(k-1).uuid;
                                attrTypePrev = gattHandleToAttributes.get(k-1).type;
                                if(attrTypeStr.equalsIgnoreCase(attrUuidPrev) &&
                                        attrTypePrev.
                                        equalsIgnoreCase("00002803-0000-1000-8000-00805F9B34FB")) {
                                    attrProperties = (byte)gattHandleToAttributes.get(k-1).properties;
                                    Log.d(TAG, "Attribute type set to Characteristic value");
                                    charValueAttrType = 1;
                                }
                                else {
                                    attrProperties = (byte)attr.properties;
                                }
                            }
                            Log.d(TAG, "Attribute properties::"+attrProperties);
                            if(is_permission_available) {
                                if(attrTypeStr !=null &&
                                        (attrTypeStr.equalsIgnoreCase("00002800-0000-1000-8000-00805F9B34FB") ||
                                        attrTypeStr.equalsIgnoreCase("00002801-0000-1000-8000-00805F9B34FB") ||
                                        attrTypeStr.equalsIgnoreCase("00002802-0000-1000-8000-00805F9B34FB"))) {
                                        uuid = ParcelUuid.fromString(attrTypeStr);
                                        attrValue = attr.value;
                                        startAttrHdl = attr.startHandle;
                                    endAttrHdl = attr.endHandle;
                                    uuidStr = attr.uuid;
                                        status = BluetoothGatt.GATT_SUCCESS;
                                }
                                else if(attrTypeStr !=null &&
                                        !(attrTypeStr.equalsIgnoreCase("00002800-0000-1000-8000-00805F9B34FB") ||
                                        attrTypeStr.equalsIgnoreCase("00002801-0000-1000-8000-00805F9B34FB") ||
                                        attrTypeStr.equalsIgnoreCase("00002802-0000-1000-8000-00805F9B34FB"))){
                                    //need to check whether attribute is readable before reading
                                    if((attrProperties > 0) && ((attrProperties & 0x02) == 0x02)) {
                                        uuid = ParcelUuid.fromString(attrTypeStr);
                                        attrValue = attr.value;
                                        startAttrHdl = attr.startHandle;
                                        endAttrHdl = attr.endHandle;
                                        uuidStr = attr.uuid;
                                        status = BluetoothGatt.GATT_SUCCESS;
                                    }
                                    else {
                                        status = BluetoothGatt.ATT_ECODE_READ_NOT_PERM;
                                    }
                                }
                            }
                            else {
                                status = BluetoothGatt.ATT_ECODE_AUTHORIZATION;
                            }
                            break;
                        }
                    }
                    if(hdlFoundStatus == 1) {
                        if(is_permission_available) {
                            if(attrTypeStr !=null &&
                                    (attrTypeStr.equalsIgnoreCase("00002800-0000-1000-8000-00805F9B34FB") ||
                                    attrTypeStr.equalsIgnoreCase("00002801-0000-1000-8000-00805F9B34FB") ||
                                    attrTypeStr.equalsIgnoreCase("00002802-0000-1000-8000-00805F9B34FB"))) {
                                Log.d(TAG, "Status success ::");
                                status = BluetoothGatt.GATT_SUCCESS;
                            }
                            else if(attrTypeStr !=null &&
                                    !(attrTypeStr.equalsIgnoreCase("00002800-0000-1000-8000-00805F9B34FB") ||
                                    attrTypeStr.equalsIgnoreCase("00002801-0000-1000-8000-00805F9B34FB") ||
                                    attrTypeStr.equalsIgnoreCase("00002802-0000-1000-8000-00805F9B34FB"))){
                                //need to check whether attribute is readable before reading
                                if((attrProperties > 0) && ((attrProperties & 0x02) == 0x02)) {
                                    Log.d(TAG, "Status success ::");
                                    status = BluetoothGatt.GATT_SUCCESS;
                                }
                            }
                        }
                    }
                    else if(k == (gattHandleToAttributes.size()-1)) {
                        Log.d(TAG, "Status failure ::");
                        status = BluetoothGatt.ATT_ECODE_ATTR_NOT_FOUND;
                    }
                }
            }
            Log.d(TAG, "Attribute type::"+attrTypeStr);

            if(is_permission_available) {
                if(attrTypeStr != null && attrTypeStr.length()>0) {
                    //Primary Service definition
                    if(attrTypeStr.equalsIgnoreCase("00002800-0000-1000-8000-00805F9B34FB") ||
                            attrTypeStr.equalsIgnoreCase("00002801-0000-1000-8000-00805F9B34FB")){
                        Log.d(TAG,"Inside Primary services payload creation::");
                        int cnt =0;
                        payload = new byte[16];
                        //Primary service UUID
                        uuidStr = removeChar(uuidStr, '-');
                        Log.d(TAG, "The replaced Str::"+uuidStr);
                        byte[] bytes = new byte[16];
                        bytes = hexStringToByteArray(uuidStr);

                        if(bytes!=null) {
                            for(i=((bytes.length)-1);i >= 0; i--) {
                                payload[cnt++] = bytes[i];
                            }
                        }
                        Log.d(TAG,"The payload data::");
                        if(payload != null) {
                            for(i=0; i < payload.length; i++) {
                                Log.d(TAG,"\n"+payload[i]);
                            }
                        }
                    }
                    //Included Service definition
                    else if(attrTypeStr.equalsIgnoreCase("00002802-0000-1000-8000-00805F9B34FB")){
                        Log.d(TAG,"Inside Included services payload creation::");
                        int cnt =0;
                        payload = new byte[20];
                        //Included service attr handle
                        byte startHdlLsb = (byte)(startAttrHdl & 0x00FF);
                        byte startHdlMsb = (byte)((startAttrHdl & 0xFF00) >> 8);
                        Log.d(TAG, "The LSB value in included service ::"+startHdlLsb);
                        Log.d(TAG, "The MSB value in included service::"+startHdlMsb);
                        payload[cnt++] = startHdlLsb;
                        payload[cnt++] = startHdlMsb;

                        //End group handle
                        byte endHdlLsb = (byte)(endAttrHdl & 0x00FF);
                        byte endHdlMsb = (byte)((endAttrHdl & 0xFF00) >> 8);
                        Log.d(TAG, "The LSB value in included service ::"+endHdlLsb);
                        Log.d(TAG, "The MSB value in included service::"+endHdlMsb);
                        payload[cnt++] = endHdlLsb;
                        payload[cnt++] = endHdlMsb;

                        //service uuid
                        uuidStr = removeChar(uuidStr, '-');
                        Log.d(TAG, "The replaced Str::"+uuidStr);
                        byte[] bytes = new byte[16];
                        bytes = hexStringToByteArray(uuidStr);

                        if(bytes!=null) {
                            for(i=((bytes.length)-1);i >= 0; i--) {
                                payload[cnt++] = bytes[i];
                            }
                        }

                        Log.d(TAG,"The payload data::");
                        if(payload != null) {
                            for(i=0; i < payload.length; i++) {
                                Log.d(TAG,"\n"+payload[i]);
                            }
                        }
                    }
                    //Characteristic declaration
                    else if(attrTypeStr.equalsIgnoreCase("00002803-0000-1000-8000-00805F9B34FB")){
                        Log.d(TAG,"Inside Characteristic payload creation::");
                        if(((attrProperties > 0) && ((attrProperties & 0x02) == 0x02))) {
                            int cnt = 0;
                            payload = new byte[19];

                            //Characteristic properties
                            payload[cnt++] = attrProperties;

                            //Characteristic value attribute handle
                            byte hdlLsb = (byte)(attrHandleNext & 0x00FF);
                            byte hdlMsb = (byte)((attrHandleNext & 0xFF00) >> 8);
                            Log.d(TAG, "The LSB value in included service ::"+hdlLsb);
                            Log.d(TAG, "The MSB value in included service::"+hdlMsb);
                            payload[cnt++] = hdlLsb;
                            payload[cnt++] = hdlMsb;

                            //Characteristic uuid
                            uuidStr = removeChar(uuidStr, '-');
                            Log.d(TAG, "The replaced Str::"+uuidStr);
                            byte[] bytes = new byte[16];
                            bytes = hexStringToByteArray(uuidStr);

                            if(bytes!=null) {
                                for(i=((bytes.length)-1);i >= 0; i--) {
                                    payload[cnt++] = bytes[i];
                                }
                            }

                            Log.d(TAG,"The payload data::");
                            if(payload != null) {
                                for(i=0; i < payload.length; i++) {
                                    Log.d(TAG,"\n"+payload[i]);
                                }
                            }
                        }
                    }
                    //Client characteristic configuration descriptor or Characteristic Value
                    else if(charValueAttrType == 1 ||
                            attrTypeStr.equalsIgnoreCase("00002902-0000-1000-8000-00805F9B34FB") ||
                            attrTypeStr.equalsIgnoreCase("00002900-0000-1000-8000-00805F9B34FB")) {
                        if(((attrProperties > 0) && ((attrProperties & 0x02) == 0x02))) {
	                        if(attrValue!=null && attrValue.length > 0) {
                                List<Byte> byteArrList= new ArrayList<Byte>();

                                //Characteristic Config bits Value
                                if(attrValue!=null && attrValue.length > 0) {
                                    for(i=0; i< attrValue.length; i++) {
                                        byteArrList.add(attrValue[i]);
                                    }
                                }

                                payload = new byte[byteArrList.size()];
                                //Transfer Arraylist contents to byte array
                                for(i=(byteArrList.size()-1); i >= 0; i--) {
                                    payload[i] = byteArrList.get(i).byteValue();
                                }
                                Log.d(TAG,"The payload data::");
                                if(payload != null) {
                                    for(i=0; i < payload.length; i++) {
                                        Log.d(TAG,""+payload[i]);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "The parcel uuid value::"+uuid);
            Log.d(TAG, "status::"+status);
            retVal = gattProfile.readResponse(config, requestHandle, status, uuid, payload);
            Log.d(TAG, "onGattReadRequest: " + retVal);
        }

        /**
         * Processes the Write Request from client and sends the response
         * to the client.
        */
        public void onGattReliableWriteRequest(BluetoothGattAppConfiguration config, int handle,
                        byte value[], String authentication, int sessionHandle,
                        int requestHandle) {
            Log.d(TAG,"Inside onGattReliableWriteRequest");
            Log.d(TAG," config:: "+config+" handle:: "+handle+" value::"+value+" authentication::"+authentication+
                    "sessionHandle::"+sessionHandle);
            Log.d(TAG,"The characteristic value::");
            for(int z=0; z < value.length; z++) {
                Log.d(TAG, ""+value[z]);
            }
            int i, k, hdlFoundStatus=0, status = BluetoothGatt.ATT_ECODE_ATTR_NOT_FOUND;
            boolean retVal = false;
            int attrHandleNext = 0;
            String attrUuidPrev, attrTypePrev, attrTypeStr;
            int charValueAttrType = 0;
            byte attrProperties =0;
            ParcelUuid uuid = null;
            boolean is_permission_available = false;
            if(handle >= 0) {
                Log.d(TAG,"Inside handle >= 0");
                if(gattHandleToAttributes != null) {
                        Log.d(TAG,"Inside gattHandleToAttributes not null");
                    //To get the attribute values for the particular handle
                    for(k=0; k<gattHandleToAttributes.size(); k++) {
                        if(handle == (int)gattHandleToAttributes.get(k).handle) {
                            Log.d(TAG, "Attribute match found");
                            hdlFoundStatus = 1;
                            Attribute attr = gattHandleToAttributes.get(k);
                            attrTypeStr = attr.type;
                            //need to check whether attribute is writable before writing
                            if((k-1) >= 0) {
                                attrUuidPrev = gattHandleToAttributes.get(k-1).uuid;
                                attrTypePrev = gattHandleToAttributes.get(k-1).type;
                                if(attrTypeStr.equalsIgnoreCase(attrUuidPrev) &&
                                        attrTypePrev.
                                        equalsIgnoreCase("00002803-0000-1000-8000-00805F9B34FB")) {
                                    charValueAttrType = 1;
                                    attrProperties = (byte)gattHandleToAttributes.get(k-1).properties;
                                    Log.d(TAG, "Attribute type set to Characteristic value");
                                }
                                else {
                                    attrProperties = (byte)attr.properties;
                                }
                            }
                            Log.d(TAG, "Attribute properties::"+attrProperties);
                            if((attrProperties > 0) && ((attrProperties & 0x04) == 0x04) ||
                                    ((attrProperties & 0x08) == 0x08)) {
                                //If the Attribute type is Characteristic value
                                if(charValueAttrType == 1) {
                                    Log.d(TAG, "Onwrite request: Attr type: characteristic value");
                                    String attrPermission = attr.permission;
                                    byte attrPermBits = attr.permBits;
                                    is_permission_available = false;
                                    Log.d(TAG, "Attribute permission bits::"+attrPermBits);
                                    //if the char value is authorized/authenticated
                                    if((attrPermBits > 0) && ((attrPermBits & 0x08) == 0x08)) {
                                        if(authentication.equalsIgnoreCase("Authenticated") ||
                                                authentication.equalsIgnoreCase("Authorized")) {
                                            is_permission_available = true;
                                            Log.d(TAG, "Inside write with authorization");
                                        }
                                    }
                                    else if((attrPermBits > 0) && ((attrPermBits & 0x10) == 0x10)) {
                                        if(authentication.equalsIgnoreCase("Authenticated")) {
                                            is_permission_available = true;
                                            Log.d(TAG, "Inside write with authentication");
                                        }
                                    }
                                    else if(attrPermBits == 0 || attrPermBits == 0x04 ||
                                            authentication.equalsIgnoreCase("None")) {
                                        is_permission_available = true;
                                        Log.d(TAG, "write without auth/authorization");
                                    }
                                    if(is_permission_available) {
                                        Log.d(TAG, "Authorized/Authenticated to write");
                                        attr.value = value;
                                        attr.sessionHandle = sessionHandle;
                                        uuid = ParcelUuid.fromString(attrTypeStr);
                                        status = BluetoothGatt.GATT_SUCCESS;
                                        //reading and checking whether the char value file exists
                                        //already
                                        byte[] buffer = new byte[255];
                                        List<Byte> byteArrList= new ArrayList<Byte>();
                                        List<Byte> hdlsArrList= new ArrayList<Byte>();
                                        HashMap<Integer, Integer> hdlIndexMap =
                                                new HashMap<Integer, Integer>();
                                        int isHdlInFile = 0;
                                        String FILENAME = "characteristic_values";
                                        Context context = getApplicationContext();
                                        try {
                                            Log.d(TAG,"File path ::"+
                                                    context.getFilesDir().getAbsolutePath());
                                            FileInputStream fis =
                                                    new FileInputStream(context.getFilesDir().getAbsolutePath()
                                                    + "/" + FILENAME);
                                            int bytesRead = fis.read(buffer);
                                            if(bytesRead > 0) {
                                                Log.d(TAG, "Onwrite request: Bytes read > 0");
                                                for(i=0; i< bytesRead;) {
                                                    hdlsArrList.add(buffer[i]);//handle msb
                                                    hdlsArrList.add(buffer[i+1]);//handle lsb
                                                    int hdlValue = (buffer[i] << 8) + (buffer[i+1]);
                                                    hdlIndexMap.put(hdlValue, (i+3));
                                                    i= i+4+buffer[i+2];
                                                }
                                                byte handleLSB = (byte)(handle & 0x00FF);
                                                byte handleMSB = (byte)((handle & 0xFF00) >> 8);

                                                //check if the char value handle is already
                                                //present in the File.
                                                //If present, get the handle and the index
                                                //and update the char value at the correct index
                                                if(hdlsArrList != null && hdlsArrList.size() >0) {
                                                    for(i=0;i<hdlsArrList.size();i++) {
                                                        if((hdlsArrList.get(i) == handleMSB) &&
                                                                    (hdlsArrList.get(i+1) == handleLSB)){
                                                          Log.d(TAG, "Onwrite request: Char value handle " +
                                                                    "already present in file");
                                                          isHdlInFile = 1;
                                                          int index = hdlIndexMap.get(handle);
                                                          byte[] bufferTemp = new byte[255];
                                                          int tmpIndex=0;
                                                          if(buffer != null) {
                                                              int z = 0;
                                                              //Get the index for '\n' after the handle
                                                              for(z=index; z < bytesRead; z++) {
                                                                  if(buffer[z] == ((byte)'\n')) {
                                                                          break;
                                                                  }
                                                              }
                                                              //Store the remaining byte array values in
                                                              //a temp byte array

                                                              for(tmpIndex=0; tmpIndex < (bytesRead-(z+1)); tmpIndex++) {
                                                                      bufferTemp[tmpIndex] = buffer[tmpIndex+z+1];
                                                              }
                                                          }
                                                          //Write the char value and update the length
                                                          buffer[index-1] = (byte)value.length;
                                                          for(int j=0; j < value.length; j++) {
                                                            buffer[index++] = value[j];
                                                          }
                                                          //append '\n' after every char value
                                                          buffer[index++] = (byte)'\n';
                                                          //append the remaining byte array elements again
                                                          for(int y=0; y < tmpIndex; y++) {
                                                              buffer[index++] = bufferTemp[y];
                                                          }
                                                          //For testing
                                                          Log.d(TAG, "buffer printed");
                                                          for(int r=0; r< index; r++) {
                                                              Log.d(TAG, ""+buffer[r]);
                                                          }
                                                          for(i=0; i< index; i++) {
                                                              byteArrList.add(buffer[i]);
                                                          }

                                                        }
                                                    }
                                                }
                                                Log.d(TAG, "buffer printed outside");
                                                for(int r=0; r< bytesRead; r++) {
                                                    Log.d(TAG, ""+buffer[r]);
                                                }
                                                //If char value handle is not already present in the
                                                //file, just store the values read from file into
                                                //the byte array list
                                                if(isHdlInFile == 0) {
                                                    for(i=0; i< bytesRead; i++) {
                                                        byteArrList.add(buffer[i]);
                                                    }
                                                }
                                            }
                                            fis.close();
                                            }
                                            catch (FileNotFoundException e) {
                                                e.printStackTrace();
                                            }
                                            catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        //write to a file
                                        Log.d(TAG, "Writing to Char values file::");

                                        if(isHdlInFile == 0) {
                                            //Creating the byte array for a char value
                                            //writing the new char value data not already in file
                                            //into arraylist
                                            //big endian
                                            byte handleLSB = (byte)(handle & 0x00FF);
                                            byte handleMSB = (byte)((handle & 0xFF00) >> 8);

                                            byteArrList.add(handleMSB);
                                            byteArrList.add(handleLSB);
                                            byteArrList.add((byte)value.length);

                                            if(value!=null && value.length > 0) {
                                                for(i=0; i< value.length; i++) {
                                                    byteArrList.add(value[i]);
                                                }
                                            }
                                            byteArrList.add((byte)'\n');
                                        }

                                        byte[] charValueBytes = new byte[byteArrList.size()];
                                        //Transfer Arraylist contents to byte array
                                        for(i=0; i < byteArrList.size(); i++) {
                                            charValueBytes[i] = byteArrList.get(i).byteValue();
                                        }
                                        Log.d(TAG, "Onwrite request: Char value bytes to " +
                                                "be written to file::"+charValueBytes);
                                        Log.d(TAG, "The data written to file onWriteRequest");
                                            for(i=0; i< charValueBytes.length; i++) {
                                                Log.d(TAG,""+charValueBytes[i]);
                                            }
                                            try {
                                                FileOutputStream fos =
                                                        openFileOutput(FILENAME, Context.MODE_PRIVATE);
                                                fos.write(charValueBytes);
                                                fos.close();
                                            }
                                            catch (FileNotFoundException e) {
                                                e.printStackTrace();
                                            }
                                            catch (IOException e) {
                                                 e.printStackTrace();
                                            }
                                    }
                                    else {
                                        //need to change to not authorized
                                        status = BluetoothGatt.ATT_ECODE_ATTR_NOT_FOUND;;
                                    }
                                }
                                //If the attribute type is client characteristic descriptor
                                else if(attrTypeStr.
                                        equalsIgnoreCase("00002902-0000-1000-8000-00805F9B34FB")) {
                                        Log.d(TAG, "Onwrite request: Attr type: client char desc value");
                                    String attrPermission = attr.permission;
                                    byte attrPermBits = attr.permBits;
                                    is_permission_available = false;
                                    //if the client char descriptor is authorized/authenticated
                                    if((attrPermBits > 0) && ((attrPermBits & 0x08) == 0x08)) {
                                        if(authentication.equalsIgnoreCase("Authenticated") ||
                                                authentication.equalsIgnoreCase("Authorized")) {
                                            is_permission_available = true;
                                        }
                                    }
                                    //if the client char config descriptor is authenticated
                                    else if((attrPermBits > 0) && ((attrPermBits & 0x10) == 0x10)) {
                                        if(authentication.equalsIgnoreCase("Authenticated")) {
                                            is_permission_available = true;
                                        }
                                    }
                                    else if(attrPermBits == 0 || attrPermBits == 0x04 ||
                                            authentication.equalsIgnoreCase("None")) {
                                        is_permission_available = true;
                                    }
                                    if(is_permission_available) {
                                        attr.value = value;
                                        attr.sessionHandle = sessionHandle;
                                        uuid = ParcelUuid.fromString(attrTypeStr);
                                        status = BluetoothGatt.GATT_SUCCESS;
                                    }
                                    else {
                                        //need to change to not authorized
                                        status = BluetoothGatt.ATT_ECODE_AUTHORIZATION;
                                    }
                                }
                                break;
                            }
                            else {
                                //need to change the error to NOT_AUTHORIZED
                                status = BluetoothGatt.ATT_ECODE_WRITE_NOT_PERM;
                            }
                        }
                    }
                    if(hdlFoundStatus == 1) {
                        if(is_permission_available && ((attrProperties > 0) && ((attrProperties & 0x04) == 0x04) ||
                                ((attrProperties & 0x08) == 0x08))) {
                            Log.d(TAG, "Status success ::");
                            status = BluetoothGatt.GATT_SUCCESS;
                        }
                    }
                    else if(k == (gattHandleToAttributes.size()-1)) {
                        Log.d(TAG, "Status failure ::");
                        status = BluetoothGatt.ATT_ECODE_ATTR_NOT_FOUND;
                    }
                }
            }
            Log.d(TAG,"Result of reliableWriteRequest");
            Log.d(TAG,"status"+status);
            Log.d(TAG,"uuid"+ uuid);
            retVal = gattProfile.writeResponse(config, requestHandle, status, uuid);
            Log.d(TAG, "onGattReliableWriteRequest: " + retVal);
            if(is_permission_available && hdlFoundStatus == 1) {
                //send notification/indication for the particular
                //client char config handle
                if((value != null) && (value.length > 0)) {
                    if(value[0] > 0x00) {
                        sendNotificationIndicationHandle(handle);
                    }
                }
            }
        }

        /**
         * Processes the Write Request from client and sends the response
         * to the client.
        */
        public void onGattWriteRequest(BluetoothGattAppConfiguration config, int handle,
                        byte value[], String authentication) {
                Log.d(TAG,"Inside onGattWriteRequest");
            int i, k, hdlFoundStatus=0, status = BluetoothGatt.ATT_ECODE_ATTR_NOT_FOUND;
            boolean retVal = false;
            int attrHandleNext = 0;
            String attrUuidPrev, attrTypePrev, attrTypeStr;
            int charValueAttrType = 0;
            byte attrProperties =0;
            ParcelUuid uuid = null;
            boolean is_permission_available = false;
            if(handle >= 0) {
                if(gattHandleToAttributes != null) {
                    //To get the attribute values for the particular handle
                    for(k=0; k<gattHandleToAttributes.size(); k++) {
                        if(handle == (int)gattHandleToAttributes.get(k).handle) {
                            hdlFoundStatus = 1;
                            Attribute attr = gattHandleToAttributes.get(k);
                            attrTypeStr = attr.type;
                            if((k-1) >= 0) {
                                attrUuidPrev = gattHandleToAttributes.get(k-1).uuid;
                                attrTypePrev = gattHandleToAttributes.get(k-1).type;
                                if(attrTypeStr.equalsIgnoreCase(attrUuidPrev) &&
                                            attrTypePrev.
                                            equalsIgnoreCase("00002803-0000-1000-8000-00805F9B34FB")) {
                                    charValueAttrType = 1;
                                    attrProperties = (byte)gattHandleToAttributes.get(k-1).properties;
                                    Log.d(TAG, "Attribute type set to Characteristic value");
                                }
                                else {
                                    attrProperties = (byte)attr.properties;
                                }
                            }
                            Log.d(TAG, "Attribute properties::"+attrProperties);
                            //need to check whether attribute is writable before writing
                            if((attrProperties > 0) && ((attrProperties & 0x04) == 0x04) ||
                                    ((attrProperties & 0x08) == 0x08)) {
                                //If the Attribute type is Characteristic value
                                if(charValueAttrType == 1) {
                                    Log.d(TAG, "Onwrite request: Attr type: characteristic value");
                                    String attrPermission = attr.permission;
                                    byte attrPermBits = attr.permBits;
                                    is_permission_available = false;
                                    //if the char value is authorized/authenticated
                                    if((attrPermBits > 0) && ((attrPermBits & 0x08) == 0x08)) {
                                        if(authentication.equalsIgnoreCase("Authenticated") ||
                                                        authentication.equalsIgnoreCase("Authorized")) {
                                            is_permission_available = true;
                                            Log.d(TAG, "Inside write with authorization");
                                        }
                                    }
                                    else if((attrPermBits > 0) && ((attrPermBits & 0x10) == 0x10)) {
                                        if(authentication.equalsIgnoreCase("Authenticated")) {
                                            is_permission_available = true;
                                            Log.d(TAG, "Inside write with authentication");
                                        }
                                    }
                                    else if(attrPermBits == 0 || attrPermBits == 0x04 ||
                                                authentication.equalsIgnoreCase("None")) {
                                        is_permission_available = true;
                                        Log.d(TAG, "write without auth/authorization");
                                    }
                                    if(is_permission_available) {
                                        Log.d(TAG, "Authorized/Authenticated to write");
                                        attr.value = value;
                                        //attr.sessionHandle = sessionHandle;
                                        uuid = ParcelUuid.fromString(attrTypeStr);
                                        status = BluetoothGatt.GATT_SUCCESS;
                                        //reading and checking whether the char value file exists
                                        //already
                                        byte[] buffer = new byte[255];
                                        List<Byte> byteArrList= new ArrayList<Byte>();
                                        List<Byte> hdlsArrList= new ArrayList<Byte>();
                                        HashMap<Integer, Integer> hdlIndexMap =
                                                        new HashMap<Integer, Integer>();
                                        int isHdlInFile = 0;
                                        String FILENAME = "characteristic_values";
                                        Context context = getApplicationContext();
                                        try {
                                            Log.d(TAG,"File path ::"+
                                                        context.getFilesDir().getAbsolutePath());
                                            FileInputStream fis =
                                                    new FileInputStream(context.getFilesDir().getAbsolutePath()
                                                    + "/" + FILENAME);
                                            int bytesRead = fis.read(buffer);
                                            if(bytesRead > 0) {
                                                Log.d(TAG, "Onwrite request: Bytes read > 0");
                                                for(i=0; i< bytesRead;) {
                                                    hdlsArrList.add(buffer[i]);//handle msb
                                                    hdlsArrList.add(buffer[i+1]);//handle lsb
                                                    int hdlValue = (buffer[i] << 8) + (buffer[i+1]);
                                                    hdlIndexMap.put(hdlValue, (i+3));
                                                    //4 below represents handle-2 bytes,
                                                    //length-1 byte,"\n"-1 byte
                                                    i= i+4+buffer[i+2];
                                                }
                                                byte handleLSB = (byte)(handle & 0x00FF);
                                                byte handleMSB = (byte)((handle & 0xFF00) >> 8);

                                                //check if the char value handle is already
                                                //present in the File.
                                                //If present, get the handle and the index
                                                //and update the char value at the correct index
                                                if(hdlsArrList != null && hdlsArrList.size() >0) {
                                                    for(i=0;i<hdlsArrList.size();i++) {
                                                        if((hdlsArrList.get(i) == handleMSB) &&
                                                                (hdlsArrList.get(i+1) == handleLSB)){
                                                          Log.d(TAG, "Onwrite request: Char value handle " +
                                                                    "already present in file");
                                                          isHdlInFile = 1;
                                                          int index = hdlIndexMap.get(handle);
                                                          byte[] bufferTemp = new byte[255];
                                                          int tmpIndex=0;
                                                          if(buffer != null) {
                                                              int z = 0;
                                                              //Get the index for '\n' after the handle
                                                              for(z=index; z < bytesRead; z++) {
                                                                  if(buffer[z] == ((byte)'\n')) {
                                                                      break;
                                                                  }
                                                              }
                                                              //Store the remaining byte array values in
                                                              //a temp byte array
                                                              for(tmpIndex=0; tmpIndex < (bytesRead-(z+1)); tmpIndex++) {
                                                                  bufferTemp[tmpIndex] = buffer[tmpIndex+z+1];
                                                              }
                                                          }
                                                          //Write the char value
                                                          buffer[index-1] = (byte)value.length;
                                                          for(int j=0; j < value.length; j++) {
                                                            buffer[index++] = value[j];
                                                          }
                                                          //append '\n' after every char value
                                                          buffer[index++] = (byte)'\n';
                                                          //append the remaining byte array elements again
                                                          for(int y=0; y < tmpIndex; y++) {
                                                                  buffer[index++] = bufferTemp[y];
                                                          }
                                                          for(i=0; i< index; i++) {
                                                              byteArrList.add(buffer[i]);
                                                          }
                                                        }
                                                    }
                                                }
                                                //If char value handle is not already present in the
                                                //file, just store the values read from file into
                                                //the byte array list
                                                if(isHdlInFile == 0) {
                                                    for(i=0; i< bytesRead; i++) {
                                                        byteArrList.add(buffer[i]);
                                                    }
                                                }
                                            }
                                            fis.close();
                                            }
                                            catch (FileNotFoundException e) {
                                                e.printStackTrace();
                                            }
                                            catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        //write to a file
                                        Log.d(TAG, "Writing to Char values file::");

                                        if(isHdlInFile == 0) {
                                            //Creating the byte array for a char value
                                            //writing the new char value data not already in file
                                            //into arraylist
                                            //big endian
                                            byte handleLSB = (byte)(handle & 0x00FF);
                                            byte handleMSB = (byte)((handle & 0xFF00) >> 8);

                                            byteArrList.add(handleMSB);
                                            byteArrList.add(handleLSB);
                                            byteArrList.add((byte)value.length);

                                            if(value!=null && value.length > 0) {
                                                for(i=0; i< value.length; i++) {
                                                    byteArrList.add(value[i]);
                                                }
                                            }
                                            byteArrList.add((byte)'\n');
                                        }

                                        byte[] charValueBytes = new byte[byteArrList.size()];
                                        //Transfer Arraylist contents to byte array
                                        for(i=0; i < byteArrList.size(); i++) {
                                            charValueBytes[i] = byteArrList.get(i).byteValue();
                                        }
                                        Log.d(TAG, "Onwrite request: Char value bytes to " +
                                                "be written to file::"+charValueBytes);
                                        Log.d(TAG, "The data written to file onWriteRequest");
                                            for(i=0; i< charValueBytes.length; i++) {
                                                Log.d(TAG,""+charValueBytes[i]);
                                            }
                                            try {
                                                FileOutputStream fos =
                                                        openFileOutput(FILENAME, Context.MODE_PRIVATE);
                                                fos.write(charValueBytes);
                                                fos.close();
                                            }
                                            catch (FileNotFoundException e) {
                                                e.printStackTrace();
                                            }
                                            catch (IOException e) {
                                                 e.printStackTrace();
                                            }
                                    }
                                    else {
                                        //need to change to not authorized
                                        status = BluetoothGatt.ATT_ECODE_AUTHORIZATION;
                                    }
                                }
                                //If the attribute type is client characteristic descriptor
                                else if(attrTypeStr.
                                        equalsIgnoreCase("00002902-0000-1000-8000-00805F9B34FB")) {
                                    String attrPermission = attr.permission;
                                    byte attrPermBits = attr.permBits;
                                    is_permission_available = false;
                                    //if the client char descriptor is authorized/authenticated
                                    if((attrPermBits > 0) && ((attrPermBits & 0x08) == 0x08)) {
                                        if(authentication.equalsIgnoreCase("Authenticated") ||
                                                authentication.equalsIgnoreCase("Authorized")) {
                                            is_permission_available = true;
                                        }
                                    }
                                    //if the client char config descriptor is authenticated
                                    else if((attrPermBits > 0) && ((attrPermBits & 0x10) == 0x10)) {
                                        if(authentication.equalsIgnoreCase("Authenticated")) {
                                            is_permission_available = true;
                                        }
                                    }
                                    else if(attrPermBits == 0 || attrPermBits == 0x04 ||
                                                authentication.equalsIgnoreCase("None")) {
                                        is_permission_available = true;
                                    }
                                    if(is_permission_available) {
                                        attr.value = value;
                                        //attr.sessionHandle = sessionHandle;
                                        uuid = ParcelUuid.fromString(attrTypeStr);
                                        status = BluetoothGatt.GATT_SUCCESS;
                                    }
                                    else {
                                        //need to change to not authorized
                                        status = BluetoothGatt.ATT_ECODE_AUTHORIZATION;
                                    }
                                }
                                break;
                            }
                            else {
                                //need to change the error to NOT_AUTHORIZED
                                status = BluetoothGatt.ATT_ECODE_WRITE_NOT_PERM;
                            }
                        }
                    }
                    if(hdlFoundStatus == 1) {
                        if(is_permission_available && ((attrProperties > 0) && ((attrProperties & 0x04) == 0x04) ||
                                ((attrProperties & 0x08) == 0x08))) {
                            Log.d(TAG, "Status success ::");
                            status = BluetoothGatt.GATT_SUCCESS;
                        }
                    }
                    else if(k == (gattHandleToAttributes.size()-1)) {
                        Log.d(TAG, "Status failure ::");
                        status = BluetoothGatt.ATT_ECODE_ATTR_NOT_FOUND;
                    }
                    if(is_permission_available && hdlFoundStatus == 1) {
                        //send notification/indication for the particular
                        //client char config handle
                        if((value != null) && (value.length > 0)) {
                            if(value[0] > 0x00) {
                                sendNotificationIndicationHandle(handle);
                            }
                        }
                    }
                }
            }
        }

        public void onGattSetClientConfigDescriptor(BluetoothGattAppConfiguration config,
                        int handle, byte[] value, int sessionHandle) {
            //send notification/indication for the particular client char config handle
            if((value != null) && (value.length > 0)) {
                if(value[0] > 0x00) {
                    sendNotificationIndicationHandle(handle);
                }
            }
        }
    };

    // Sends an update message to registered UI client.
    private void sendMessage(int what, int value) {
        //mClient
        if (mMessenger == null) {
            Log.d(TAG, "No clients registered.");
            return;
        }

        try {
                //mClient.
                mMessenger.send(Message.obtain(null, what, value, 0));
        } catch (RemoteException e) {
            // Unable to reach client.
            e.printStackTrace();
        }
    }
    /**
     * Populates the hash map with Attribute type and their correspondng attribute handles.
    */
    private void populateGattAttribTypeMap() {
        gattAttribTypeToHandle.put("00002800-0000-1000-8000-00805F9B34FB", new ArrayList<Integer>());
        gattAttribTypeToHandle.put("00002801-0000-1000-8000-00805F9B34FB", new ArrayList<Integer>());
        gattAttribTypeToHandle.put("00002802-0000-1000-8000-00805F9B34FB", new ArrayList<Integer>());
        gattAttribTypeToHandle.put("00002803-0000-1000-8000-00805F9B34FB", new ArrayList<Integer>());
    }

    /**
     * Removes a particular character from a String
     * @return the String with the character removed
    */
    public static String removeChar(String s, char c) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i ++) {
           char cur = s.charAt(i);
           if (cur != c) {
               sb.append(cur);
           }
        }
        return sb.toString();
    }

    /**
     * Converts a hexdecimal String into byte array
     * @return the byte array formed from hexadecimal String
    */
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    /**
     * Converts a String into byte array
     * @return the byte array formed from the input String
    */
    public static byte[] stringToByteArray(String s) {
        int len = s.length();
        int byteArrLen = 0, cnt =0;
        if(len%2 == 0) {
            byteArrLen = len/2;
        }
        else {
            byteArrLen = (len/2)+1;
        }
        byte[] data = new byte[byteArrLen];
        for (int i = 0; i < len; i += 2) {
            if(((i+1) < len)) {
                data[cnt++] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                + Character.digit(s.charAt(i+1), 16));
            }
            else if(((i+1) >= len)) {
                data[cnt++] = (byte) (Character.digit(s.charAt(i), 16));
            }
        }
        Log.d(TAG, "The String to byte array value::");
        for(int j=0; j< data.length;j++) {
            Log.d(TAG,""+data[j]);
        }
        return data;
    }
    /**
     * Sends notifications and indications to the client
     *
    */
    void sendNotificationIndication() {
        Calendar cal = Calendar.getInstance();
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int month = cal.get(Calendar.MONTH) + 1;
        int year = cal.get(Calendar.YEAR);
        String yearStr = Integer.toString(year);
        yearStr = yearStr.substring(2, 4);
        byte year1 = Byte.valueOf(yearStr);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int second = cal.get(Calendar.SECOND);
        Date today = new Date();
        String todaystring = today.toString();
        Log.d(TAG, "Today's date"+todaystring);

        int i=0;
        List<Integer> hndlList = null;
        int j=0;
        if(gattAttribTypeToHandle != null) {
            for(Map.Entry<String, List<Integer>> entry : gattAttribTypeToHandle.entrySet()) {
                if("00002902-0000-1000-8000-00805F9B34FB".
                                equalsIgnoreCase(entry.getKey().toString())) {
                    //List of client characteristic configuration descriptor handles
                    hndlList = entry.getValue();
                }
            }
        }
        if(hndlList!=null) {
            for(j=0; j< hndlList.size(); j++) {
                int handle = hndlList.get(j);
                if(handle >= 0) {
                    if(gattHandleToAttributes != null) {
                        //To get the attribute values for the particular handle
                        for(int k=0; k<gattHandleToAttributes.size(); k++) {
                            if(handle == gattHandleToAttributes.get(k).handle) {
                                Attribute attr = gattHandleToAttributes.get(k);
                                int sessionHdl = attr.sessionHandle;
                                if((attr.value != null) && (attr.value.length > 0)) {
                                    if(attr.value[0] > 0x00) {
                                        int charValueHdl = attr.referenceHandle;
                                        Attribute attrCharValue =
                                                        gattHandleToAttributes.get(charValueHdl);
                                        byte[] charValue = new byte[attrCharValue.value.length + 6];
                                        for(i=0; i < attrCharValue.value.length; i++) {
                                            charValue[i] = attrCharValue.value[i];
                                        }
                                        charValue[i++]= (byte)day;
                                        charValue[i++]= (byte)month;
                                        charValue[i++]= (byte)year1;
                                        charValue[i++]= (byte)hour;
                                        charValue[i++]= (byte)minute;
                                        charValue[i++]= (byte)second;

                                        boolean notify = false;
                                        if(attr.value[0] == 0x01) {
                                            notify = true;
                                        }
                                        else if(attr.value[0] == 0x02) {
                                            notify = false;
                                        }
                                        Log.d(TAG, "The client config handle is :"+handle);
                                        Log.d(TAG, "The characteristic values notified/indicated " +
                                                        "are as follows:");

                                        for(int z=0; z<i; z++) {
                                            Log.d(TAG, ""+charValue[z]);
                                        }

                                        boolean result = gattProfile.sendIndication(
                                                        serverConfiguration, charValueHdl,
                                                charValue, notify, sessionHdl);
                                        Log.d(TAG, "SendIndication result::"+result);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }

    /**
     * Sends notifications and indications for a specific attribute handle
     * to the client
     *
    */
    void sendNotificationIndicationHandle(int handle) {
        Calendar cal = Calendar.getInstance();
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int month = cal.get(Calendar.MONTH) + 1;
        int year = cal.get(Calendar.YEAR);
        String yearStr = Integer.toString(year);
        yearStr = yearStr.substring(2, 4);
        byte year1 = Byte.valueOf(yearStr);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int second = cal.get(Calendar.SECOND);
        int i =0;
        Date today = new Date();
        String todaystring = today.toString();
        Log.d(TAG, "Today's date"+todaystring);

        if(gattHandleToAttributes != null) {
            //To get the attribute values for the particular handle
            for(int k=0; k<gattHandleToAttributes.size(); k++) {
                if(handle == gattHandleToAttributes.get(k).handle) {
                    Attribute attr = gattHandleToAttributes.get(k);
                    int sessionHdl = attr.sessionHandle;
                    if((attr.value != null) && (attr.value.length > 0)) {
                        if(attr.value[0] > 0x00) {
                            int charValueHdl = attr.referenceHandle;
                            Attribute attrCharValue = gattHandleToAttributes.get(charValueHdl);
                            byte[] charValue = new byte[attrCharValue.value.length + 6];
                            for(i=0; i < attrCharValue.value.length; i++) {
                                charValue[i] = attrCharValue.value[i];
                            }
                            charValue[i++]= (byte)day;
                            charValue[i++]= (byte)month;
                            charValue[i++]= (byte)year1;
                            charValue[i++]= (byte)hour;
                            charValue[i++]= (byte)minute;
                            charValue[i++]= (byte)second;
                            boolean notify = false;
                            if(attr.value[0] == 0x01) {
                                notify = true;
                            }
                            else if(attr.value[0] == 0x02) {
                                notify = false;
                            }
                            Log.d(TAG, "The client config handle is :"+handle);
                            Log.d(TAG, "The characteristic values notified/indicated " +
                                        "are as follows:");

                            for(int z=0; z<i; z++) {
                                Log.d(TAG, ""+charValue[z]);
                            }
                            boolean result = gattProfile.sendIndication(serverConfiguration,
                                        charValueHdl, charValue, notify, sessionHdl);
                            Log.d(TAG, "SendIndication result::"+result);
                            break;
                        }
                    }
                }
            }
        }
    }
    /**
     * Updates the data structures in this service with the
     * Charactersitic values from the file "characteristic_values"
     *
    */
    void updateDataStructuresFromFile() {
        //logic for updating the Hashmap and  Arraylist from characteristic_values file
        Log.d(TAG, "Reading Characteristic value file from phone in readbyTypeRequest::");
        String FILENAME = "characteristic_values";
        byte[] buffer = new byte[255];
        Context context = getApplicationContext();
        try {
            Log.d(TAG,"File path::"+context.getFilesDir().getAbsolutePath());
            FileInputStream fis = new FileInputStream(context.getFilesDir().getAbsolutePath()
                        + "/" + FILENAME);

            int bytesRead = fis.read(buffer);
            Log.d(TAG,"Data read from file");
            int j=0;//index for the buffer byte array read from file
            while((bytesRead > 0) && (j < bytesRead)) {
                int charValueHandle = (buffer[j] << 8) + buffer[j+1];
                byte len = buffer[j+2];
                byte[] charValue = new byte[len];
                int index = j+3;
                for(int k=0; k < len; k++) {
                    charValue[k] = buffer[index++];
                }
                if(gattHandleToAttributes != null) {
                    for(int i=0; i < gattHandleToAttributes.size(); i++) {
                        Attribute attrPres = gattHandleToAttributes.get(i);
                        if(attrPres.handle == charValueHandle) {
                            attrPres.value = charValue;
                        }
                    }
                }
                //4 represents Handle-2 bytes, len-1 byte, "\n"-1 byte
                j=j+4+buffer[j+2];
            }
            fis.close();
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}