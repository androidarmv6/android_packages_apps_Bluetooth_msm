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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class servicesScreen extends Activity {

	public final String TAG = "servicesScreen";

	private static ArrayAdapter<String> adapter = null;

	private static Context servicesContext = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.listofconnecteddevices);

		Log.d(TAG, "Getting Text View : servicesScreen");

		TextView tv = (TextView) findViewById(R.id.textViewHeader);
		tv.setText("List Of available service uuid");

		Log.d(TAG, "Setting List View");

		ListView mListView = (ListView) findViewById(R.id.RegularList);

		adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1);
		servicesContext = this.getApplicationContext();

		Log.d(TAG, "Lets add ObjPath");

		addAvailableServices();
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
				String selectedText = tvSelectedObj.getText().toString();
				Log.d(TAG, " Object Selected = "
						+ selectedText);

				String [] textArr = selectedText.split(":");
				if(textArr != null) {
					Log.d(TAG, "Selected UUID : " + textArr[0]);
					mainScreen.selectedLEDevice.SelectedServiceUUID = new ParcelUuid(
							UUID.fromString(textArr[0]));
					Log.d(TAG, "Selected Parcel UUID : "
									+ mainScreen.selectedLEDevice.SelectedServiceUUID
											.toString());
					mainScreen.selectedLEDevice.SelectedServiceObjPath = textArr[1];
					Log.d(TAG,
							"Selected obj path : "
									+ mainScreen.selectedLEDevice.SelectedServiceObjPath);

					mainScreen.selectedLEDevice.createBluetoothGattService();

				} else {
					Log.e(TAG, "Error is selected text");
				}
			}
		});
	}

	private void addAvailableServices() {
		for (Map.Entry<ParcelUuid, List<String>> entry : mainScreen.selectedLEDevice.uuidObjPathMap
				.entrySet()) {
			ParcelUuid key = entry.getKey();
			Log.d(TAG, " Key : " + key);
			List<String> values = entry.getValue();
			for (String value : values) {
				Log.d(TAG, "Adding ObjPath" + value);
				adapter.add(key.toString() + ":" + value);
			}
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.d(TAG, "*********** on resume ***********");
		adapter.clear();
		addAvailableServices();
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	public static void onCharacterisitcsDiscovered() {
		Intent in = new Intent();
		in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		in.setClass(mainScreen.mainContext,
				com.android.le.GattClientTestApp.CharacteristicsListScreen.class);
		servicesContext.startActivity(in);
	}

}
