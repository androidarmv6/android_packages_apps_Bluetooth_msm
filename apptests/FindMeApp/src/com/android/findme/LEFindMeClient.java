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

package com.android.findme;

import java.util.ArrayList;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDevicePicker;
import android.bluetooth.IBluetoothLEFindMeServices;
import android.bluetooth.IBluetoothThermometerCallBack;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import android.widget.AdapterView.OnItemClickListener;

public class LEFindMeClient extends Activity {
    private static final String TAG = "LEFindMeClient";

    public static IBluetoothLEFindMeServices findMeService = null;

    public static UUID GATTServiceUUID = null;

    public static ParcelUuid GATTServiceParcelUUID = null;

    public static BluetoothDevice RemoteDevice = null;

    static final String REMOTE_DEVICE = "RemoteDevice";

    public static final String IMMEDIATE_ALERT_SERVICE_UUID = "0000180200001000800000805f9b34fb";

    protected static final int DEVICE_SELECTED = 0;

    public static final String FINDME_SERVICE_UUID = "FINDME_SERVICE_UUID";

    public static final String FINDME_CHAR_UUID = "FINDME_CHAR_UUID";

    public static final String FINDME_SERVICE_OPERATION = "FINDME_SERVICE_OPERATION";

    public static final String FINDME_SERVICE_OP_STATUS = "FINDME_SERVICE_OP_STATUS";

    public static final String FINDME_SERVICE_OP_VALUE = "FINDME_SERVICE_OP_VALUE";

    public static Context mainContext = null;

    public static ParcelUuid selectedCharUUID;

    public static String alertLevelValue = null;

    private IntentFilter inFilter = null;

    private LEFindMeClientReceiver receiver = null;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "**********onServiceConnected***************");
            findMeService = IBluetoothLEFindMeServices.Stub.asInterface(service);
        }

        public void onServiceDisconnected(ComponentName name) {
            Log.e(TAG, "*************onServiceDisconnected***********");
            onServiceDisconn();
        }
    };

    public final Handler msgHandler = new Handler() {
        public void handleMessage(Message msg) {
                switch (msg.what) {
                case DEVICE_SELECTED:
                    Log.d(TAG, "device selected");
                    RemoteDevice = (BluetoothDevice) msg.getData().getParcelable(REMOTE_DEVICE);
                    startGattService(IMMEDIATE_ALERT_SERVICE_UUID);
                    break;
                default:
                    break;
                }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Log.d(TAG, "****Set main content view*****");

        mainContext = this.getApplicationContext();
        LEFindMeClientReceiver.registerHandler(msgHandler);

        final String[] alertLevels = new String[3];
        alertLevels[0]="No Alert";
        alertLevels[1]="Medium Alert";
        alertLevels[2]="High Alert";

        ListView listView = (ListView) findViewById(R.id.mylist);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, android.R.id.text1, alertLevels);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new OnItemClickListener() {
        @Override
            public void onItemClick(AdapterView<?> parent, View view,
                  int position, long id) {
                Log.d(TAG, "position of address selected::"+position);
                alertLevelValue = String.valueOf(position);
                String text = "The Alert level chosen is :"+alertLevels[position];
                int duration = Toast.LENGTH_LONG;
                Toast toast = Toast.makeText(getApplicationContext(), text, duration);
                toast.show();
              }
        });

        Button buttonConnect = (Button) findViewById(R.id.buttonConnFindMe);
        buttonConnect.setOnClickListener(new View.OnClickListener() {
        @Override
            public void onClick(View v) {
                Log.d(TAG, "Button connect to bt devices clicked");
                Log.d(TAG, "alertLevelValue::"+alertLevelValue);
                if(alertLevelValue != null) {
                    bindToFindMeService();
                }
                else {
                    String text = "Select an alert level before connecting....";
                    int duration = Toast.LENGTH_LONG;
                    Toast toast = Toast.makeText(getApplicationContext(), text, duration);
                    toast.show();
                }
            }
        });
        Button buttonCancelConnect = (Button) findViewById(R.id.buttonCancelConnFindMe);
        buttonCancelConnect.setOnClickListener(new View.OnClickListener() {
        @Override
            public void onClick(View v) {
                Log.d(TAG, "Button cancel connect to bt devices clicked");
                try {
                    findMeService.gattConnectCancel();
                }
                catch(Exception e) {
                }
            }
        });
        inFilter = new IntentFilter();
        inFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        inFilter.addAction(BluetoothDevicePicker.ACTION_DEVICE_SELECTED);
        this.receiver = new LEFindMeClientReceiver();
        Log.d(TAG, "Registering the receiver");
        this.registerReceiver(this.receiver, inFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.e(TAG, "****the activity is paused*****");
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "****the Find me activity is destroyed*****");
        LEFindMeClientReceiver.unregisterHandler();
        if (this.receiver != null) {
            try {
                this.unregisterReceiver(this.receiver);
            } catch (Exception e) {
                Log.e(TAG, "Error while unregistering the receiver");
            }
        }
        close();
    }

    @Override
    public void onRestart() {
        super.onRestart();
        Log.e(TAG, "****the activity is restart*****");
    }

    public void onServiceConn() {
        Intent in1 = new Intent(BluetoothDevicePicker.ACTION_LAUNCH);
        in1.putExtra(BluetoothDevicePicker.EXTRA_NEED_AUTH, false);
        in1.putExtra(BluetoothDevicePicker.EXTRA_FILTER_TYPE,
                BluetoothDevicePicker.FILTER_TYPE_ALL);
        in1.putExtra(BluetoothDevicePicker.EXTRA_LAUNCH_PACKAGE,
                "com.android.findme");
        in1.putExtra(BluetoothDevicePicker.EXTRA_LAUNCH_CLASS,
                LEFindMeClientReceiver.class.getName());
        in1.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        this.startActivity(in1);
    }

    public void startGattService(String uuidString) {
        Log.d(TAG, "Inside startGattService for : " + uuidString);
        try {
            GATTServiceUUID = convertUUIDStringToUUID(uuidString);
            Log.d(TAG, " GATTServiceUUID = " + GATTServiceUUID);
            GATTServiceParcelUUID = new ParcelUuid(GATTServiceUUID);
            Log.d(TAG, " GATTServiceParcelUUID = " + GATTServiceParcelUUID);
            if (findMeService != null) {
                boolean isGattService = findMeService.startFindMeService(RemoteDevice,
                        GATTServiceParcelUUID,
                        LEFindMeClient.mCallback);
                if (!isGattService) {
                    Log.e(TAG, "FindMe service could not get GATT service");
                    Toast.makeText(getApplicationContext(),
                            "could not start FindMe service",
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getApplicationContext(),
                        "Not connected to service", Toast.LENGTH_SHORT).show();
            }
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void onServiceDisconn() {
        close();
    }

    public synchronized void close() {
        if (mConnection != null) {
            Log.e(TAG, "unbinding from FindMe service");
            mainContext.unbindService(mConnection);
        }
        mConnection = null;
        RemoteDevice = null;
        GATTServiceParcelUUID = null;
        GATTServiceUUID = null;
        mainContext = null;
    }

    public void bindToFindMeService() {
        String className = IBluetoothLEFindMeServices.class.getName();
        Log.d(TAG, "class name : " + className);
        Intent in = new Intent(className);
        if (!mainContext.bindService(in, mConnection, Context.BIND_AUTO_CREATE)) {
            Log.e(TAG, "Could not bind to Remote Service");
        } else {
            Log.e(TAG, "Succ bound to Remote Service");
            onServiceConn();
        }
    }

    private UUID convertUUIDStringToUUID(String UUIDStr) {
        if (UUIDStr.length() != 32) {
            return null;
        }
        String uuidMsB = UUIDStr.substring(0, 16);
        String uuidLsB = UUIDStr.substring(16, 32);

        if (uuidLsB.equals("800000805f9b34fb")) {
            // TODO Long is represented as two complement. Fix this later.
            UUID uuid = new UUID(Long.valueOf(uuidMsB, 16), 0x800000805f9b34fbL);
            return uuid;
        } else {
            UUID uuid = new UUID(Long.valueOf(uuidMsB, 16),
                    Long.valueOf(uuidLsB));
            return uuid;
        }
    }
    public static IBluetoothThermometerCallBack mCallback = new IBluetoothThermometerCallBack.Stub() {
    @Override
        public void sendResult(Bundle arg0) throws RemoteException {
            Log.d(TAG, "IRemoteCallback sendResult callback");
            selectedCharUUID = arg0.getParcelable(FINDME_CHAR_UUID);
            Log.d(TAG, "Characteristic Uuid in App::"+selectedCharUUID);
            ParcelUuid serviceUuid = arg0.getParcelable(FINDME_SERVICE_UUID);
            Log.d(TAG, "Service Uuid in App::"+serviceUuid);
            String op = arg0.getString(FINDME_SERVICE_OPERATION);
            Log.d(TAG, "Bundle arg2 : " + op);
            boolean status = arg0.getBoolean(FINDME_SERVICE_OP_STATUS);
            Log.d(TAG, "Bundle arg3 : " + status);
            ArrayList<String> values = arg0
                    .getStringArrayList(FINDME_SERVICE_OP_VALUE);
            String result = "";
            for (String value : values) {
                result = result + value + " ";
                Log.d(TAG, "Bundle arg4 : " + value);
            }
            if(selectedCharUUID != null) {
                Log.d(TAG, "Characteristic UUID not null");
                //Need to write to alert level characteristic to beep
                boolean result1 = findMeService.writeCharacteristicsValue(selectedCharUUID,
                        serviceUuid, alertLevelValue);
                Log.d(TAG, "Result of characteristic value write ::"+result1);
                boolean disconnectResult = findMeService.closeFindMeService(serviceUuid);
                Log.d(TAG, "disconnect result::"+disconnectResult);
            }
        }
    };
}
