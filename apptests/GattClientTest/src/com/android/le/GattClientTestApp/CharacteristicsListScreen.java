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

import android.app.Activity;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class CharacteristicsListScreen extends Activity {

	public final String TAG = "characteristicsListScreen";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.listofconnecteddevices);

		Log.d(TAG, "Getting Text View CharacteristicsListScreen");

		TextView tv = (TextView) findViewById(R.id.textViewHeader);
		tv.setText("List of characteristics for "
				+ mainScreen.selectedLEDevice.SelectedServiceUUID.toString());

		Log.d(TAG, "Setting List View");

		ListView mListView = (ListView) findViewById(R.id.RegularList);

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1);

		BluetoothGattService gattService = mainScreen.selectedLEDevice.objPathGattSrvMap
				.get(mainScreen.selectedLEDevice.SelectedServiceObjPath);

		mainScreen.selectedLEDevice.CharObjPathArray = null;
		mainScreen.selectedLEDevice.CharUUIDArray = null;
		mainScreen.selectedLEDevice.SelectedCharObjPath = null;
		mainScreen.selectedLEDevice.SelectedCharUUID = null;

		Log.d(TAG, "Calling getCharacteristics");
		String[] charPathArray = gattService.getCharacteristics();
		ParcelUuid[] charUUIDArray = null;

		mainScreen.selectedLEDevice.registerWatcher();

		if (charPathArray != null && charPathArray.length > 0) {

			charUUIDArray = new ParcelUuid[charPathArray.length];

			for (int i = 0; i < charPathArray.length; i++) {

				Log.d(TAG, "Char path " + charPathArray[i]);
				// TEST
				String cliConf = gattService
						.getCharacteristicClientConf(charPathArray[i]);
				Log.d(TAG, "Client Conf = " + cliConf);
				// End
				try {
					Log.d(TAG,
							"Update char : "
									+ gattService
											.updateCharacteristicValue(charPathArray[i]));
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Log.d(TAG, "getCharacteristicUuid :");

				ParcelUuid uuid = gattService
						.getCharacteristicUuid(charPathArray[i]);
				if (uuid != null) {
					Log.d(TAG, "Add the UUID to adapter :");
					adapter.add(uuid.toString() + ":" + charPathArray[i]);
					charUUIDArray[i] = uuid;
				} else {
					Log.d(TAG,
							"Error for getCharacteristicUuid(Path) returns null for "
									+ charPathArray[i]);
					charUUIDArray[i] = null;
				}
			}
			mainScreen.selectedLEDevice.CharObjPathArray = charPathArray;
			mainScreen.selectedLEDevice.CharUUIDArray = charUUIDArray;
		} else {
			Log.d(TAG, "Error getCharacteristics returns null");
		}

		mListView.setAdapter(adapter);
		mListView.setItemsCanFocus(false);
		mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		mListView.setItemChecked(0, true);

		mListView.setOnItemClickListener(new ListView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				// TODO Auto-generated method stub
				Log.d(TAG, " Object Selected at position " + position);
				TextView tvSelectedObj = (TextView) view;
				Log.d(TAG, " Object Selected = "
						+ tvSelectedObj.getText().toString());

				mainScreen.selectedLEDevice.SelectedCharObjPath = mainScreen.selectedLEDevice.CharObjPathArray[position];
				mainScreen.selectedLEDevice.SelectedCharUUID = mainScreen.selectedLEDevice.CharUUIDArray[position];

				Intent in = new Intent();
				in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				in.setClass(mainScreen.mainContext,
						com.android.le.GattClientTestApp.CharacteristicScreen.class);
				getApplicationContext().startActivity(in);

			}
		});

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.optionsmenu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.CloseService:
			Log.d(TAG, "Calling close service");
			mainScreen.selectedLEDevice.disconnectService();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

}
