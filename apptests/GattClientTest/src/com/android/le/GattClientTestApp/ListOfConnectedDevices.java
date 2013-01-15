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
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class ListOfConnectedDevices extends Activity {

	public final String TAG = "ListOfConnectedDevices";

	private static ArrayAdapter<String> adapter = null;

	public static List<String> deviceUUIDPair = new ArrayList<String>();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.listofconnecteddevices);

		// Add operation to the text in the header
		TextView t = new TextView(this);
		t = (TextView) findViewById(R.id.textViewHeader);

		Log.d(TAG, "Setting List View : ListOfConnectedDevices");

		ListView mListView = (ListView) findViewById(R.id.RegularList);

		adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1);

		Log.d(TAG, "Lets add ObjPath");

		addConnectedDevices();

		mListView.setAdapter(adapter);
		mListView.setItemsCanFocus(false);
		mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		mListView.setItemChecked(0, true);

		mListView.setOnItemClickListener(new ListView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				// TODO Auto-generated method stub
				Log.d(TAG, " device Selected at position " + position);
				TextView tvSelectedObj = (TextView) view;
				String selectedText = tvSelectedObj.getText().toString();
				Log.d(TAG, " device Selected = " + selectedText);

				mainScreen.selectedLEDevice = mainScreen.addrConnectedDeviceMap
						.get(selectedText);
				Log.d(TAG, "Selected Device : "
						+ mainScreen.selectedLEDevice.BDevice.getAddress());

				for (UUID uuid : mainScreen.selectedUUID) {
					Log.d(TAG, "Selected UUID : " + uuid);
					String pair = selectedText + "/" + uuid.toString();
					if (!(deviceUUIDPair.contains(pair))) {
						Log.d(TAG, "Get GATT service for uuid : " + uuid);
						boolean gattServices = mainScreen.selectedLEDevice.BDevice
								.getGattServices(uuid);
						Log.d(TAG, " -----------");
						Log.d(TAG, " Check VALUE OF GATT SERVICES"
								+ gattServices);
						if (gattServices) {
							deviceUUIDPair.add(pair);
						}
					} else {
						Log.d(TAG,
								"This device - uuid pair is already present : "
										+ pair);
						Intent in = new Intent();
						in.setClass(mainScreen.mainContext,
								com.android.le.GattClientTestApp.servicesScreen.class);
						in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						mainScreen.mainContext.startActivity(in);
					}

				}

			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.d(TAG, "*********** on resume ***********");
		adapter.clear();
		addConnectedDevices();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.connecteddevicesmenu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.ClearDevices:
			if (mainScreen.addrConnectedDeviceMap != null) {
				mainScreen.addrConnectedDeviceMap.clear();
			}
			if (mainScreen.selectedLEDevice != null) {
				mainScreen.selectedLEDevice = null;
			}
			if (deviceUUIDPair != null) {
				deviceUUIDPair.clear();
			}
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private void addConnectedDevices() {
		if (mainScreen.addrConnectedDeviceMap != null) {
			for (Entry<String, DeviceInfo> device : mainScreen.addrConnectedDeviceMap
					.entrySet()) {
				String addr = device.getValue().BDevice.getAddress();
				if (addr != null) {
					adapter.add(addr);
				} else {
					Log.e(TAG, "Selected device addr is null");
				}
			}
		}
	}

}
