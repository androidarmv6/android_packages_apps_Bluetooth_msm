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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDevicePicker;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

public class LEThermometerReceiver extends BroadcastReceiver {

	private final String TAG = "LEThermoBCastRecv";

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub

		String action = intent.getAction();

		if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
			if (BluetoothAdapter.STATE_ON == intent.getIntExtra(
					BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
				Log.d(TAG,
						"Received BLUETOOTH_STATE_CHANGED_ACTION, BLUETOOTH_STATE_ON");
			} else if (BluetoothAdapter.STATE_OFF == intent.getIntExtra(
					BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
				Log.d(TAG,
						"!!!!!!!!!Received BLUETOOTH_STATE_CHANGED_ACTION, STATE_OFF");

				if (ListOfConnectedDevices.deviceUUIDPair != null) {
					ListOfConnectedDevices.deviceUUIDPair.clear();
				}

				if (mainScreen.addrConnectedDeviceMap != null) {
					for (Map.Entry<String, DeviceInfo> entry : mainScreen.addrConnectedDeviceMap
							.entrySet()) {
						Log.d(TAG,
								"cleaning device info for addr : "
										+ entry.getKey());
						DeviceInfo disconnDevInfo = entry.getValue();
						if (disconnDevInfo != null) {
							Log.d(TAG,
									"Cleaning the device information of the disconnected device");
							disconnDevInfo.uuidObjPathMap.clear();
							// close the gatt service before removing?
							disconnDevInfo.objPathGattSrvMap.clear();
						}
					}
				}
				Log.d(TAG, "Clear the connected devices map");
				if (mainScreen.addrConnectedDeviceMap != null) {
					mainScreen.addrConnectedDeviceMap.clear();
				}
			}
		} else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
			Log.d(TAG, "!!!!!!!!!!Received ACTION_ACL_DISCONNECTED intent");
			BluetoothDevice remoteDevice = intent
					.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

			String disconnDevAddr = remoteDevice.getAddress();
			Log.d(TAG,
					"!!!!!!!!!!Received ACTION_ACL_DISCONNECTED, bt device: "
							+ disconnDevAddr);
			DeviceInfo disconnDevInfo = null;
			if (mainScreen.addrConnectedDeviceMap != null) {
				disconnDevInfo = mainScreen.addrConnectedDeviceMap
						.get(disconnDevAddr);
			}
			// If its the ACL disconnect after discovering primary services,
			// don't clean up.
			if ((disconnDevInfo != null)
					&& (disconnDevInfo.CharObjPathArray != null)
					&& (disconnDevInfo.CharUUIDArray != null)) {
				Log.d(TAG, "Beginning to clear the cache on disconnect");
				clearDisconnectedDeviceInfo(disconnDevAddr, disconnDevInfo);
				clearDeviceFromConnectedDevList(disconnDevAddr);
			}

		} else if (action.equals(BluetoothDevicePicker.ACTION_DEVICE_SELECTED)) {

			BluetoothDevice remoteDevice = intent
					.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

			Log.d(TAG, "Received BT device selected intent, bt device: "
					+ remoteDevice.getAddress());

			// Display toast message on the UI
			String deviceName = remoteDevice.getName();
			String toastMsg;
			toastMsg = " The user selected the device named " + deviceName;
			Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show();

			if (mainScreen.addrConnectedDeviceMap == null) {
				mainScreen.addrConnectedDeviceMap = new HashMap<String, DeviceInfo>();
			}

			if (!(mainScreen.addrConnectedDeviceMap.containsKey(remoteDevice
					.getAddress()))) {
				DeviceInfo device = new DeviceInfo(context);
				device.BDevice = remoteDevice;
				mainScreen.addrConnectedDeviceMap.put(
						remoteDevice.getAddress(), device);
			}

			Intent in = new Intent();
			in.setClass(mainScreen.mainContext,
					com.android.le.GattClientTestApp.ListOfConnectedDevices.class);
			in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			mainScreen.mainContext.startActivity(in);

		} else if (action.equals(BluetoothDevice.ACTION_GATT)) {

			Log.d(TAG,
					" ACTION GATT INTENT RECVD as a result of gatGattService");

			BluetoothDevice remoteDevice = intent
					.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			Log.d(TAG, "Remote Device: " + remoteDevice.getAddress());

			ParcelUuid uuid = (ParcelUuid) intent
					.getExtra(BluetoothDevice.EXTRA_UUID);
			Log.d(TAG, " UUID: " + uuid);


			String[] ObjectPathArray = (String[]) intent
					.getExtra(BluetoothDevice.EXTRA_GATT);

			if (ObjectPathArray == null) {
				Log.d(TAG, " +++  ERROR NO OBJECT PATH HANDLE FOUND +++++++");
				String pair = remoteDevice.getAddress() + "/" + uuid.toString();
				Log.d(TAG, " Removing the device addr uuid pair : " + pair);
				ListOfConnectedDevices.deviceUUIDPair.remove(pair);
				String deviceName = remoteDevice.getName();
				String toastMsg;
				toastMsg = " ERROR: The user selected the device named "
						+ deviceName + " doesnt have the service ";
				Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show();
				return;
			} else {
				Log.d(TAG, " Object Path (length): " + ObjectPathArray.length);
				mainScreen.selectedLEDevice.uuidObjPathMap.put(uuid,
						Arrays.asList(ObjectPathArray));
			}

			for (Map.Entry<ParcelUuid, List<String>> entry : mainScreen.selectedLEDevice.uuidObjPathMap
					.entrySet()) {
				ParcelUuid key = entry.getKey();
				Log.d(TAG, " Key : " + key);
				List<String> values = entry.getValue();
				for (String value : values) {
					Log.d(TAG, " Values : " + value);
				}

			}

			Intent in = new Intent();
			in.setClass(mainScreen.mainContext,
					com.android.le.GattClientTestApp.servicesScreen.class);
			in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			mainScreen.mainContext.startActivity(in);

		} else if (action.equals(BluetoothDevice.ACTION_GATT_SERVICE_CHANGED)) {

			BluetoothDevice remoteDevice = intent
					.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			String disconnDevAddr = remoteDevice.getAddress();
			Log.d(TAG,
					"!!!! Received ACTION_GATT_SERVICE_CHANGED intent for device : "
							+ disconnDevAddr);

			int res = intent.getIntExtra(BluetoothDevice.EXTRA_GATT_RESULT, -1);
			Log.d(TAG, "Service change Gatt result : " + res);

			DeviceInfo disconnDevInfo = null;
			if (mainScreen.addrConnectedDeviceMap != null) {
				disconnDevInfo = mainScreen.addrConnectedDeviceMap
						.get(disconnDevAddr);
			}

			if (disconnDevInfo != null) {
				Log.d(TAG, "Beginning to clear the cache on disconnect");
				clearDisconnectedDeviceInfo(disconnDevAddr, disconnDevInfo);
				clearDeviceFromConnectedDevList(disconnDevAddr);
			}

		} else if (action.equals("BluetoothGattCharacteristicValueChanged")) {
			String CharPath = (String) intent
					.getExtra("BluetoothGattCharacteristicPath");
			String CharValue = (String) intent
					.getExtra("BluetoothGattCharacteristicValue");
			Log.d(TAG, "Char path UPDATED = " + CharPath);
			Log.d(TAG, "Char value UPDATED = " + CharValue);
		}
	}

	private void clearDeviceFromConnectedDevList(String disconnDevAddr) {
		if (ListOfConnectedDevices.deviceUUIDPair != null) {
			int size = ListOfConnectedDevices.deviceUUIDPair.size();
			Log.d(TAG, "ListOfConnectedDevices.deviceUUIDPair size : "
					+ size);
			for (int i = 0; i < size; i++) {
				String devUuidPair = ListOfConnectedDevices.deviceUUIDPair
						.get(i);
				Log.d(TAG, "devUuidPair : " + devUuidPair);
				if (devUuidPair.startsWith(disconnDevAddr)) {
					Log.d(TAG,
							"Removing the disconnected device from ListOfConnectedDevices : "
									+ devUuidPair);
					ListOfConnectedDevices.deviceUUIDPair
							.remove(devUuidPair);
					i--;
					size--;
				}
			}

		}
	}

	private void clearDisconnectedDeviceInfo(String disconnDevAddr,
			DeviceInfo disconnDevInfo) {
		Log.d(TAG, "Cleaning the device information of the disconnected device");
		if (disconnDevInfo.uuidObjPathMap != null) {
			disconnDevInfo.uuidObjPathMap.clear();
		}
		// close the gatt service before removing?

		if (disconnDevInfo.objPathGattSrvMap != null) {
			for (BluetoothGattService gattSrv : disconnDevInfo.objPathGattSrvMap
					.values()) {
				Log.d(TAG, "Closing the gattSrv  : " + gattSrv);
				try {
					gattSrv.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			Log.d(TAG, "clean the objPathGattSrvMap");
			disconnDevInfo.objPathGattSrvMap.clear();
		}
		if (mainScreen.addrConnectedDeviceMap != null) {
			mainScreen.addrConnectedDeviceMap.remove(disconnDevAddr);
		}
	}
}
