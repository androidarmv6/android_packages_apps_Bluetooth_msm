/*
 *  Copyright (c) 2011-12, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *        * Redistributions of source code must retain the above copyright
 *          notice, this list of conditions and the following disclaimer.
 *        * Redistributions in binary form must reproduce the above copyright
 *          notice, this list of conditions and the following disclaimer in the
 *          documentation and/or other materials provided with the distribution.
 *        * Neither the name of The Linux Foundation nor
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

package com.android.le.GattClientTestApp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.IBluetoothGattProfile;
import android.content.Context;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.util.Log;

public class DeviceInfo {
	private final String TAG = "DeviceInfo";
	public BluetoothDevice BDevice = null;

	public ParcelUuid SelectedServiceUUID = null;
	public String SelectedServiceObjPath = null;

	public String[] CharObjPathArray = null;
	public ParcelUuid[] CharUUIDArray = null;

	// public ParcelUuid[] ServiceUUIDArray = null;
	// public String[] ServiceObjPathArray = null;

	public HashMap<ParcelUuid, List<String>> uuidObjPathMap = new HashMap<ParcelUuid, List<String>>();

	public ParcelUuid SelectedCharUUID = null;

	public String SelectedCharObjPath = null;

	public ArrayList<String> NotificationIndications;

	// public BluetoothGattService gattService = null;

	public HashMap<String, BluetoothGattService> objPathGattSrvMap = null;

	private final GattProfile gattProfile;

	Context mainContext;
	public DeviceInfo(Context context) {
		gattProfile = new GattProfile();
		mainContext = context;
		if (objPathGattSrvMap == null) {
			objPathGattSrvMap = new HashMap<String, BluetoothGattService>();
		}
	}

	public void createBluetoothGattService() {
		gattProfile.createBluetoothGattService();
	}

	public void registerWatcher() {
		gattProfile.register();
	}
    public void deregisterWatcher() {
    	gattProfile.deregister();
    }
    public void clearNotificationIndication() {
		if (NotificationIndications != null) {
			NotificationIndications.clear();
		}
    }

    public void disconnectService() {

		BluetoothGattService gattService = objPathGattSrvMap
				.get(SelectedServiceObjPath);
		try {
			/*for (BluetoothGattService gattSrv : objPathGattSrvMap.values()) {
				Log.d(TAG, "Closing the gattSrv  : " + gattSrv);
				gattSrv.close();
			}
			Log.d(TAG, "Clear the objPathGattSrvMap");
			objPathGattSrvMap.clear();*/

			if (gattService != null) {
				Log.d(TAG, "gattService.close()");
				gattService.close();
				objPathGattSrvMap.remove(SelectedServiceObjPath);
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    /**
     * Helper to perform Characteristic discovery
     */
    private class GattProfile extends IBluetoothGattProfile.Stub {

        public synchronized void createBluetoothGattService() {
			if (!(objPathGattSrvMap.containsKey(SelectedServiceObjPath))) {
				Log.d(TAG, "Create new Gatt service for obj : "
						+ SelectedServiceObjPath);
				BluetoothGattService gattService = new BluetoothGattService(
						BDevice,
						SelectedServiceUUID, SelectedServiceObjPath, this);
				objPathGattSrvMap.put(SelectedServiceObjPath, gattService);
			} else {
				Log.d(TAG, "Gattservice exist for the objPath : "
						+ SelectedServiceObjPath);
				BluetoothGattService gattsrv = objPathGattSrvMap
						.get(SelectedServiceObjPath);
					Log.d(TAG, "Characteristics have been already discovered");
					servicesScreen.onCharacterisitcsDiscovered();
			}
		}


        public synchronized void onValueChanged(String path, String value)
        {
            if (path == null) {
                return;
            }
            Log.d(TAG, "WatcherValueChanged, Path = " + path );
            if ( value != null ) {
            	Log.d(TAG, "WatcherValueChanged, Value = " + value );
            } else {
            	Log.d(TAG, "WatcherValueChanged, Value = NULL");
            }
            if (NotificationIndications == null) {
            	NotificationIndications = new ArrayList<String>();
            }
            NotificationIndications.add(path + " : " + value);

            //String toastMsg;
            //toastMsg = " Characteristic Notification/Indication " + path + " : " + value;
            //Toast.makeText(mainContext, toastMsg, Toast.LENGTH_SHORT).show();

        }
        public synchronized void register() {
        	boolean ret = false;
        	Log.d(TAG, "Registering watcher");
        	try {
				BluetoothGattService gattService = objPathGattSrvMap
						.get(SelectedServiceObjPath);
				ret = gattService.registerWatcher();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	Log.d(TAG, "Registering watcher returns " + ret);
        }
        public synchronized void deregister() {
        	boolean ret = false;
        	Log.d(TAG, "Deregistering watcher");
        	try {
				BluetoothGattService gattService = objPathGattSrvMap
						.get(SelectedServiceObjPath);
				ret = gattService.deregisterWatcher();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	Log.d(TAG, "Deregistering watcher returns " + ret);
        }
		public void onSetCharacteristicCliConfResult(String path, boolean result)
				throws RemoteException {
			// TODO Auto-generated method stub
			Log.d(TAG, "onSetCharacteristicCliConfResult " + path + " : " + result);
		}
		public void onSetCharacteristicValueResult(String path, boolean result)
				throws RemoteException {
			// TODO Auto-generated method stub
			Log.d(TAG, "onSetCharacteristicValueResult " + path + " : " + result);
		}

		public void onUpdateCharacteristicValueResult(String path, boolean result)
				throws RemoteException {
			// TODO Auto-generated method stub
			Log.d(TAG, "onUpdateCharacteristicValueResult " + path + " : " + result);
		}

		public void onDiscoverCharacteristicsResult(String arg0, boolean arg1)
				throws RemoteException {
			Log.d(TAG, "IN onDiscoverCharacteristicsResult : " + arg1);
			if (arg1) {
				Log.d(TAG, "calling  onCharacterisitcsDiscovered: " + arg1);
				servicesScreen.onCharacterisitcsDiscovered();

			} else {
				Log.e(TAG, "Characteristics discovery failed : " + arg1);
				Log.e(TAG, "Disconnect the Gatt service");
				disconnectService();
			}

		}
    }

}
