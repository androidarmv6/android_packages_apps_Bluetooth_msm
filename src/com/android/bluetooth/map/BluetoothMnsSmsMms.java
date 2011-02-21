/*
 * Copyright (c) 2011, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *        * Redistributions of source code must retain the above copyright
 *          notice, this list of conditions and the following disclaimer.
 *        * Redistributions in binary form must reproduce the above copyright
 *          notice, this list of conditions and the following disclaimer in the
 *          documentation and/or other materials provided with the distribution.
 *        * Neither the name of Code Aurora nor
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

package com.android.bluetooth.map;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.CursorJoiner;
import android.net.Uri;
import android.util.Log;

import com.android.bluetooth.map.MapUtils.MapUtilsConsts;
import com.android.bluetooth.map.MapUtils.SmsMmsUtils;

import java.util.List;

import static com.android.bluetooth.map.IBluetoothMasApp.MMS_HDLR_CONSTANT;

/**
 * This class run an MNS session.
 */
public class BluetoothMnsSmsMms {
    private static final String TAG = "BluetoothMnsSmsMms";

    private static final boolean V = BluetoothMasService.VERBOSE;

    public static final String NEW_MESSAGE = "NewMessage";

    public static final String DELIVERY_SUCCESS = "DeliverySuccess";

    public static final String SENDING_SUCCESS = "SendingSuccess";

    public static final String DELIVERY_FAILURE = "DeliveryFailure";

    public static final String SENDING_FAILURE = "SendingFailure";

    public static final String MEMORY_FULL = "MemoryFull";

    public static final String MEMORY_AVAILABLE = "MemoryAvailable";

    public static final String MESSAGE_DELETED = "MessageDeleted";

    public static final String MESSAGE_SHIFT = "MessageShift";

    private static final int MSG_CP_FAILED_TYPE = 5;

    private static final int MSG_CP_QUEUED_TYPE = 6;

    private static final int MSG_META_DATA_TYPE = 130;

    private static final int MSG_DELIVERY_RPT_TYPE = 134;

    private Context mContext;

    private SmsMmsFolderContentObserverClass[] arrObjSmsMms;

    BluetoothMns btMns;

    private boolean mIsRegistered = false;

    public BluetoothMnsSmsMms(Context context, BluetoothMns mnsObj) {
        /* check Bluetooth enable status */
        /*
         * normally it's impossible to reach here if BT is disabled. Just check
         * for safety
         */
        mContext = context;
        btMns = mnsObj;

        final int size = SmsMmsUtils.FORLDER_LIST_SMS_MMS_MNS.size();
        arrObjSmsMms = new SmsMmsFolderContentObserverClass[size];
    }

    public boolean isRegistered() {
        return mIsRegistered;
    }

    /**
     * Register with content provider to receive updates
     * of change on cursor.
     */
    public void register() {
        if (V) Log.v(TAG, "REGISTERING SMS and MMS MNS UPDATES");
        if (!mIsRegistered) {
            Uri smsUri = Uri.parse("content://sms/");
            crSmsA = mContext.getContentResolver().query(smsUri,
                    new String[] { "_id", "body", "type"}, null, null, "_id asc");
            crSmsB = mContext.getContentResolver().query(smsUri,
                    new String[] { "_id", "body", "type"}, null, null, "_id asc");

            Uri smsObserverUri = Uri.parse("content://mms-sms/");
            mContext.getContentResolver().registerContentObserver(smsObserverUri,
                    true, smsContentObserver);

            Uri mmsUri = Uri.parse("content://mms/");
            crMmsA = mContext.getContentResolver()
                    .query(mmsUri, new String[] { "_id", "read", "m_type", "m_id" }, null,
                            null, "_id asc");
            crMmsB = mContext.getContentResolver()
                    .query(mmsUri, new String[] { "_id", "read", "m_type", "m_id" }, null,
                            null, "_id asc");

            final List<String> list = SmsMmsUtils.FORLDER_LIST_SMS_MMS_MNS;
            final int size = list.size();
            for (int i=0; i < size; i++){
                folderNameSmsMms = list.get(i);
                Uri smsFolderUri =  Uri.parse("content://sms/"+folderNameSmsMms.trim()+"/");
                crSmsFolderA = mContext.getContentResolver().query(smsFolderUri,
                        new String[] { "_id", "body", "type"}, null, null, "_id asc");
                crSmsFolderB = mContext.getContentResolver().query(smsFolderUri,
                        new String[] { "_id", "body", "type"}, null, null, "_id asc");
                Uri mmsFolderUri = null;
                if (folderNameSmsMms != null
                        && folderNameSmsMms.equalsIgnoreCase(MapUtilsConsts.Draft)){
                    mmsFolderUri = Uri.parse("content://mms/"+MapUtilsConsts.Drafts+"/");
                }
                else if(folderNameSmsMms != null
                                && !folderNameSmsMms.equalsIgnoreCase(MapUtilsConsts.Failed)
                                && !folderNameSmsMms.equalsIgnoreCase(MapUtilsConsts.Queued)){
                    mmsFolderUri = Uri.parse("content://mms/"+folderNameSmsMms.trim()+"/");
                }
                if(folderNameSmsMms !=null
                                && !folderNameSmsMms.equalsIgnoreCase(MapUtilsConsts.Failed)
                                && !folderNameSmsMms.equalsIgnoreCase(MapUtilsConsts.Queued)){
                        crMmsFolderA = mContext.getContentResolver()
                        .query(mmsFolderUri, new String[] { "_id", "read", "m_type", "m_id"},
                                null, null, "_id asc");
                        crMmsFolderB = mContext.getContentResolver()
                        .query(mmsFolderUri, new String[] { "_id", "read", "m_type", "m_id"},
                                null, null, "_id asc");
                }
                arrObjSmsMms[i] = new SmsMmsFolderContentObserverClass(folderNameSmsMms,
                                crSmsFolderA, crSmsFolderB, crMmsFolderA, crMmsFolderB,
                                CR_SMS_FOLDER_A, CR_MMS_FOLDER_A);
                Uri smsFolderObserverUri = Uri.parse("content://mms-sms/"+folderNameSmsMms.trim());
                    mContext.getContentResolver().registerContentObserver(
                                    smsFolderObserverUri, true, arrObjSmsMms[i]);
            }

            mIsRegistered = true;
            if (V) Log.v(TAG, "REGISTERING SMS and MMS MNS UPDATES DONE");
        }
    }

    /**
     * Stop listening to changes in cursor
     */
    public void deregister() {
        Log.d(TAG, "DEREGISTER MNS SMS UPDATES");
        if (mIsRegistered) {
            mIsRegistered = false;
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.unregisterContentObserver(smsContentObserver);
            crSmsA.close();
            crSmsB.close();
            currentCRSms = CR_SMS_A;

            crMmsA.close();
            crMmsB.close();
            currentCRMms = CR_MMS_A;
            if (arrObjSmsMms != null && arrObjSmsMms.length > 0){
                for (int i=0; i < arrObjSmsMms.length; i++){
                    arrObjSmsMms[i].crSmsFolderA.close();
                    arrObjSmsMms[i].crSmsFolderB.close();
                    arrObjSmsMms[i].currentCRSmsFolder = CR_SMS_FOLDER_A;
                    if(arrObjSmsMms[i].crMmsFolderA != null){
                            arrObjSmsMms[i].crMmsFolderA.close();
                                arrObjSmsMms[i].crMmsFolderB.close();
                                arrObjSmsMms[i].currentCRMmsFolder = CR_MMS_FOLDER_A;
                    }
                    resolver.unregisterContentObserver(arrObjSmsMms[i]);
                }
            }
            Log.d(TAG, "DEREGISTER MNS SMS UPDATES DONE");
        }
    }

    private SmsContentObserverClass smsContentObserver = new SmsContentObserverClass();

    private Cursor crSmsA = null;
    private Cursor crSmsB = null;
    private Cursor crSmsFolderA = null;
    private Cursor crSmsFolderB = null;

    private Cursor crMmsA = null;
    private Cursor crMmsB = null;
    private Cursor crMmsFolderA = null;
    private Cursor crMmsFolderB = null;

    private final int CR_SMS_A = 1;
    private final int CR_SMS_B = 2;
    private int currentCRSms = CR_SMS_A;
    private final int CR_SMS_FOLDER_A = 1;
    private final int CR_SMS_FOLDER_B = 2;

    private final int CR_MMS_A = 1;
    private final int CR_MMS_B = 2;
    private int currentCRMms = CR_MMS_A;
    private final int CR_MMS_FOLDER_A = 1;
    private final int CR_MMS_FOLDER_B = 2;

    public String folderName = "";
    public String folderNameSmsMms = "";


    /**
     * Get the folder name (MAP representation) based on the
     * folder type value in SMS database
     */
    private String getMAPFolder(int type) {
        String folder = null;
        switch (type) {
        case 1:
            folder = "inbox";
            break;
        case 2:
            folder = "sent";
            break;
        case 3:
            folder = "draft";
            break;
        case 4:
        case 5:
        case 6:
            folder = "outbox";
            break;
        default:
            break;
        }
        return folder;
    }

    /**
     * Gets the table type (as in Sms Content Provider) for the
     * given id
     */
    private int getMessageType(String id) {
        Cursor cr = mContext.getContentResolver().query(
                Uri.parse("content://sms/" + id),
                new String[] { "_id", "type"}, null, null, null);
        if (cr.moveToFirst()) {
            return cr.getInt(cr.getColumnIndex("type"));
        }
        cr.close();
        return -1;
    }

    /**
     * This class listens for changes in Sms MMs Content Provider's folders
     * It acts, only when an entry gets removed from the table
     */
    private class SmsMmsFolderContentObserverClass extends ContentObserver {
        private String folder;
        private Cursor crSmsFolderA;
        private Cursor crSmsFolderB;
        private int currentCRSmsFolder;
        private Cursor crMmsFolderA;
        private Cursor crMmsFolderB;
        private int currentCRMmsFolder;

        public SmsMmsFolderContentObserverClass(String folderNameSmsMms,
                Cursor crSmsFolderA, Cursor crSmsFolderB, Cursor crMmsFolderA,
                Cursor crMmsFolderB, int currentCRSmsFolder, int currentCRMmsFolder) {
            super(null);
            this.folder = folderNameSmsMms;
            this.crSmsFolderA = crSmsFolderA;
            this.crSmsFolderB = crSmsFolderB;
            this.currentCRSmsFolder = currentCRSmsFolder;
            this.crMmsFolderA = crMmsFolderA;
            this.crMmsFolderB = crMmsFolderB;
            this.currentCRMmsFolder = currentCRMmsFolder;
        }

        @Override
                public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            int currentItemCount = 0;
            int newItemCount = 0;

            if (folder != null
                    && !folder.equalsIgnoreCase(MapUtilsConsts.Failed)
                    && !folder.equalsIgnoreCase(MapUtilsConsts.Queued)){
                currentCRMmsFolder = checkMmsFolder(folder, crMmsFolderA, crMmsFolderB, currentCRMmsFolder);
            }

            if (currentCRSmsFolder == CR_SMS_FOLDER_A) {
                currentItemCount = crSmsFolderA.getCount();
                crSmsFolderB.requery();
                newItemCount = crSmsFolderB.getCount();
            } else {
                currentItemCount = crSmsFolderB.getCount();
                crSmsFolderA.requery();
                newItemCount = crSmsFolderA.getCount();
            }

            if (V){
                Log.v(TAG, "SMS FOLDER current " + currentItemCount + " new "
                        + newItemCount);
            }

            if (currentItemCount > newItemCount) {
                crSmsFolderA.moveToFirst();
                crSmsFolderB.moveToFirst();

                CursorJoiner joiner = new CursorJoiner(crSmsFolderA,
                        new String[] { "_id"}, crSmsFolderB,
                        new String[] { "_id"});

                CursorJoiner.Result joinerResult;
                while (joiner.hasNext()) {
                    joinerResult = joiner.next();
                    switch (joinerResult) {
                    case LEFT:
                        // handle case where a row in cursor1 is unique
                        if (currentCRSmsFolder == CR_SMS_FOLDER_A) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                            if (V){
                                Log.v(TAG, " SMS DELETED FROM FOLDER ");
                            }
                            String body = crSmsFolderA.getString(crSmsFolderA
                                    .getColumnIndex("body"));
                            String id = crSmsFolderA.getString(crSmsFolderA
                                    .getColumnIndex("_id"));
                            if (V){
                                Log.v(TAG, " DELETED SMS ID " + id + " BODY "
                                        + body);
                            }
                            int msgType = getMessageType(id);
                            if (msgType == -1) {
                                btMns.sendMnsEvent(MESSAGE_DELETED, id,
                                        MapUtilsConsts.Telecom + "/" +
                                        MapUtilsConsts.Msg + "/" +
                                        folder, null, "SMS_GSM");
                            } else {
                                if (V){
                                    Log.v(TAG, "Shouldn't reach here as you cannot " +
                                            "move msg from this folder to any other folder");
                                }
                                if (folder != null &&
                                        folder.equalsIgnoreCase(MapUtilsConsts.Draft)){
                                    String newFolder = getMAPFolder(msgType);
                                    btMns.sendMnsEvent(MESSAGE_SHIFT, id, MapUtilsConsts.Telecom
                                            + "/" + MapUtilsConsts.Msg + "/"
                                            + newFolder, MapUtilsConsts.Telecom + "/" +
                                            MapUtilsConsts.Msg + "/" +
                                            MapUtilsConsts.Draft, "SMS_GSM");
                                    if (newFolder.equalsIgnoreCase("sent")) {
                                        btMns.sendMnsEvent(SENDING_SUCCESS, id,
                                                MapUtilsConsts.Telecom + "/"+
                                                MapUtilsConsts.Msg + "/" + newFolder,
                                                null, "SMS_GSM");
                                    }
                                } else if (folder != null &&
                                        folder.equalsIgnoreCase(MapUtilsConsts.Outbox)){
                                    String newFolder = getMAPFolder(msgType);
                                    if ((newFolder != null)
                                            && (!newFolder
                                            .equalsIgnoreCase(MapUtilsConsts.Outbox))) {
                                        // The message has moved on MAP virtual
                                        // folder representation.
                                        btMns.sendMnsEvent(MESSAGE_SHIFT, id,
                                                MapUtilsConsts.Telecom + "/" +
                                                MapUtilsConsts.Msg + "/" + newFolder,
                                                MapUtilsConsts.Telecom + "/" +
                                                MapUtilsConsts.Msg + "/" +
                                                MapUtilsConsts.Outbox, "SMS_GSM");
                                        if (newFolder.equalsIgnoreCase(MapUtilsConsts.Sent)) {
                                            btMns.sendMnsEvent(SENDING_SUCCESS, id,
                                                    MapUtilsConsts.Telecom + "/"+
                                                    MapUtilsConsts.Msg +"/" + newFolder,
                                                    null, "SMS_GSM");
                                        }
                                    }
                                    if ((msgType == MSG_CP_QUEUED_TYPE) ||
                                            (msgType == MSG_CP_FAILED_TYPE)) {
                                        // Message moved from outbox to queue or
                                        // failed folder
                                        btMns.sendMnsEvent(SENDING_FAILURE, id,
                                                MapUtilsConsts.Telecom + "/" +
                                                MapUtilsConsts.Msg + "/" +
                                                MapUtilsConsts.Outbox, null, "SMS_GSM");
                                    }
                                } else if (folder != null &&
                                        folder.equalsIgnoreCase(MapUtilsConsts.Failed)){
                                    String newFolder = getMAPFolder(msgType);
                                    if ((newFolder != null)
                                            && (!newFolder
                                            .equalsIgnoreCase(MapUtilsConsts.Outbox))) {
                                        // The message has moved on MAP virtual
                                        // folder representation.
                                        btMns.sendMnsEvent(MESSAGE_SHIFT, id,
                                                MapUtilsConsts.Telecom + "/"+
                                                MapUtilsConsts.Msg + "/" + newFolder,
                                                MapUtilsConsts.Telecom + "/"+
                                                MapUtilsConsts.Msg + "/"+
                                                MapUtilsConsts.Outbox, "SMS_GSM");
                                        if (newFolder.equalsIgnoreCase(MapUtilsConsts.Sent)) {
                                            btMns.sendMnsEvent(SENDING_SUCCESS, id,
                                                    MapUtilsConsts.Telecom + "/" +
                                                    MapUtilsConsts.Msg + "/" + newFolder,
                                                    null, "SMS_GSM");
                                        }
                                    }
                                } else if (folder != null &&
                                        folder.equalsIgnoreCase(MapUtilsConsts.Queued)){
                                    String newFolder = getMAPFolder(msgType);
                                    if ((newFolder != null)
                                            && (!newFolder
                                            .equalsIgnoreCase(MapUtilsConsts.Outbox))) {
                                        // The message has moved on MAP virtual
                                        // folder representation.
                                        btMns.sendMnsEvent(MESSAGE_SHIFT, id,
                                                MapUtilsConsts.Telecom + "/" +
                                                MapUtilsConsts.Msg + "/" + newFolder,
                                                MapUtilsConsts.Telecom + "/" +
                                                MapUtilsConsts.Msg + "/" +
                                                MapUtilsConsts.Outbox, "SMS_GSM");
                                        if (newFolder.equalsIgnoreCase(MapUtilsConsts.Sent)) {
                                            btMns.sendMnsEvent(SENDING_SUCCESS, id,
                                                    MapUtilsConsts.Telecom + "/" +
                                                    MapUtilsConsts.Msg + "/" + newFolder,
                                                    null, "SMS_GSM");
                                        }
                                    }
                                }

                            }
                        } else {
                            // TODO - The current(old) query doesn't have this row;
                            // implies it was added
                        }
                        break;
                    case RIGHT:
                        // handle case where a row in cursor2 is unique
                        if (currentCRSmsFolder == CR_SMS_FOLDER_B) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                            if (V){
                                Log.v(TAG, " SMS DELETED FROM FOLDER");
                            }
                            String body = crSmsFolderB.getString(crSmsFolderB
                                    .getColumnIndex("body"));
                            String id = crSmsFolderB.getString(crSmsFolderB
                                    .getColumnIndex("_id"));
                            if (V){
                                Log.v(TAG, " DELETED SMS ID " + id + " BODY "
                                        + body);
                            }
                            int msgType = getMessageType(id);
                            if (msgType == -1) {
                                btMns.sendMnsEvent(MESSAGE_DELETED, id,
                                        MapUtilsConsts.Telecom + "/"+
                                        MapUtilsConsts.Msg + "/"+
                                        folder, null, "SMS_GSM");
                            } else {
                                if (V){
                                    Log.v(TAG,"Shouldn't reach here as you cannot " +
                                            "move msg from this folder to any other folder");
                                }
                                if (folder != null &&
                                        folder.equalsIgnoreCase(MapUtilsConsts.Draft)){
                                    String newFolder = getMAPFolder(msgType);
                                    btMns.sendMnsEvent(MESSAGE_SHIFT, id, MapUtilsConsts.Telecom
                                            + "/" + MapUtilsConsts.Msg + "/"
                                            + newFolder, MapUtilsConsts.Telecom + "/" +
                                            MapUtilsConsts.Msg + "/" +
                                            MapUtilsConsts.Draft, "SMS_GSM");
                                    if (newFolder.equalsIgnoreCase("sent")) {
                                        btMns.sendMnsEvent(SENDING_SUCCESS, id,
                                                MapUtilsConsts.Telecom + "/"+
                                                MapUtilsConsts.Msg + "/" + newFolder,
                                                null, "SMS_GSM");
                                    }
                                } else if (folder != null &&
                                        folder.equalsIgnoreCase(MapUtilsConsts.Outbox)){
                                    String newFolder = getMAPFolder(msgType);
                                    if ((newFolder != null)
                                            && (!newFolder
                                            .equalsIgnoreCase(MapUtilsConsts.Outbox))) {
                                        // The message has moved on MAP virtual
                                        // folder representation.
                                        btMns.sendMnsEvent(MESSAGE_SHIFT, id,
                                                MapUtilsConsts.Telecom + "/" +
                                                MapUtilsConsts.Msg + "/" + newFolder,
                                                MapUtilsConsts.Telecom + "/" +
                                                MapUtilsConsts.Msg + "/" +
                                                MapUtilsConsts.Outbox, "SMS_GSM");
                                        if (newFolder.equalsIgnoreCase(MapUtilsConsts.Sent)) {
                                            btMns.sendMnsEvent(SENDING_SUCCESS, id,
                                                    MapUtilsConsts.Telecom + "/"+
                                                    MapUtilsConsts.Msg +"/" + newFolder,
                                                    null, "SMS_GSM");
                                        }
                                    }
                                    if ((msgType == MSG_CP_QUEUED_TYPE) ||
                                            (msgType == MSG_CP_FAILED_TYPE)) {
                                        // Message moved from outbox to queue or
                                        // failed folder
                                        btMns.sendMnsEvent(SENDING_FAILURE, id,
                                                MapUtilsConsts.Telecom + "/" +
                                                MapUtilsConsts.Msg + "/" +
                                                MapUtilsConsts.Outbox, null, "SMS_GSM");
                                    }
                                } else if (folder != null &&
                                        folder.equalsIgnoreCase(MapUtilsConsts.Failed)){
                                    String newFolder = getMAPFolder(msgType);
                                    if ((newFolder != null)
                                            && (!newFolder
                                            .equalsIgnoreCase(MapUtilsConsts.Outbox))) {
                                        // The message has moved on MAP virtual
                                        // folder representation.
                                        btMns.sendMnsEvent(MESSAGE_SHIFT, id,
                                                MapUtilsConsts.Telecom + "/" +
                                                MapUtilsConsts.Msg + "/" + newFolder,
                                                MapUtilsConsts.Telecom + "/" +
                                                MapUtilsConsts.Msg + "/" +
                                                MapUtilsConsts.Outbox, "SMS_GSM");
                                        if (newFolder.equalsIgnoreCase(MapUtilsConsts.Sent)) {
                                            btMns.sendMnsEvent(SENDING_SUCCESS, id,
                                                    MapUtilsConsts.Telecom + "/" +
                                                    MapUtilsConsts.Msg + "/" + newFolder,
                                                    null, "SMS_GSM");
                                        }
                                    }
                                } else if (folder != null &&
                                        folder.equalsIgnoreCase(MapUtilsConsts.Queued)){
                                    String newFolder = getMAPFolder(msgType);
                                    if ((newFolder != null)
                                            && (!newFolder
                                            .equalsIgnoreCase(MapUtilsConsts.Outbox))) {
                                        // The message has moved on MAP virtual
                                        // folder representation.
                                        btMns.sendMnsEvent(MESSAGE_SHIFT, id,
                                                MapUtilsConsts.Telecom + "/" +
                                                MapUtilsConsts.Msg + "/" + newFolder,
                                                MapUtilsConsts.Telecom + "/" +
                                                MapUtilsConsts.Msg + "/"+
                                                MapUtilsConsts.Outbox, "SMS_GSM");
                                        if (newFolder.equalsIgnoreCase(MapUtilsConsts.Sent)) {
                                            btMns.sendMnsEvent(SENDING_SUCCESS, id,
                                                    MapUtilsConsts.Telecom + "/" +
                                                    MapUtilsConsts.Msg + "/" + newFolder,
                                                    null, "SMS_GSM");
                                        }
                                    }
                                }
                            }
                        } else {
                            // The current(old) query doesn't have this row;
                            // implies it was added
                        }
                        break;
                    case BOTH:
                        // handle case where a row with the same key is in both
                        // cursors
                        break;
                    }
                }
            }
            if (currentCRSmsFolder == CR_SMS_FOLDER_A) {
                currentCRSmsFolder = CR_SMS_FOLDER_B;
            } else {
                currentCRSmsFolder = CR_SMS_FOLDER_A;
            }
        }
    }

    /**
     * This class listens for changes in Sms Content Provider
     * It acts, only when a new entry gets added to database
     */
    private class SmsContentObserverClass extends ContentObserver {

        public SmsContentObserverClass() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            int currentItemCount = 0;
            int newItemCount = 0;

            checkMmsAdded();

            // Synchronize this?
            if (currentCRSms == CR_SMS_A) {
                currentItemCount = crSmsA.getCount();
                crSmsB.requery();
                newItemCount = crSmsB.getCount();
            } else {
                currentItemCount = crSmsB.getCount();
                crSmsA.requery();
                newItemCount = crSmsA.getCount();
            }

            if (V){
                Log.v(TAG, "SMS current " + currentItemCount + " new "
                        + newItemCount);
            }
            if (newItemCount > currentItemCount) {
                crSmsA.moveToFirst();
                crSmsB.moveToFirst();

                CursorJoiner joiner = new CursorJoiner(crSmsA,
                        new String[] { "_id"}, crSmsB, new String[] { "_id"});

                CursorJoiner.Result joinerResult;
                while (joiner.hasNext()) {
                    joinerResult = joiner.next();
                    switch (joinerResult) {
                    case LEFT:
                        // handle case where a row in cursor1 is unique
                        if (currentCRSms == CR_SMS_A) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                        } else {
                            // The current(old) query doesn't have this row;
                            // implies it was added
                            if (V){
                                Log.v(TAG, " SMS ADDED TO INBOX ");
                            }
                            String body1 = crSmsA.getString(crSmsA
                                    .getColumnIndex("body"));
                            String id1 = crSmsA.getString(crSmsA
                                    .getColumnIndex("_id"));
                            if (V){
                                Log.v(TAG, " ADDED SMS ID " + id1 + " BODY "
                                        + body1);
                            }
                            String folder = getMAPFolder(crSmsA.getInt(crSmsA
                                    .getColumnIndex("type")));
                            if (folder != null &&
                                    folder.equalsIgnoreCase(MapUtilsConsts.Inbox)) {
                                btMns.sendMnsEvent(NEW_MESSAGE, id1, MapUtilsConsts.Telecom +
                                        "/" + MapUtilsConsts.Msg + "/"
                                        + folder, null, "SMS_GSM");
                            } else if (folder != null &&
                                    !folder.equalsIgnoreCase(MapUtilsConsts.Inbox)){
                                btMns.sendMnsEvent(MESSAGE_SHIFT, id1, MapUtilsConsts.Telecom +
                                        "/" + MapUtilsConsts.Msg + "/"
                                        + folder, null, "SMS_GSM");
                            } else {
                                if (V){
                                    Log.v(TAG, " ADDED TO UNKNOWN FOLDER");
                                }
                            }
                        }
                        break;
                    case RIGHT:
                        // handle case where a row in cursor2 is unique
                        if (currentCRSms == CR_SMS_B) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                        } else {
                            // The current(old) query doesn't have this row;
                            // implies it was added
                            if (V){
                                Log.v(TAG, " SMS ADDED ");
                            }
                            String body1 = crSmsB.getString(crSmsB
                                    .getColumnIndex("body"));
                            String id1 = crSmsB.getString(crSmsB
                                    .getColumnIndex("_id"));
                            if (V){
                                Log.v(TAG, " ADDED SMS ID " + id1 + " BODY "
                                        + body1);
                            }
                            String folder = getMAPFolder(crSmsB.getInt(crSmsB
                                    .getColumnIndex("type")));
                            if (folder != null &&
                                    folder.equalsIgnoreCase(MapUtilsConsts.Inbox)) {
                                btMns.sendMnsEvent(NEW_MESSAGE, id1, MapUtilsConsts.Telecom + "/"+
                                        MapUtilsConsts.Msg + "/"
                                        + folder, null, "SMS_GSM");
                            } else if (folder != null &&
                                    !folder.equalsIgnoreCase(MapUtilsConsts.Inbox)){
                                btMns.sendMnsEvent(MESSAGE_SHIFT, id1, MapUtilsConsts.Telecom +
                                        "/" + MapUtilsConsts.Msg + "/"
                                        + folder, null, "SMS_GSM");
                            } else {
                                if (V){
                                    Log.v(TAG, " ADDED TO UNKNOWN FOLDER");
                                }
                            }
                        }
                        break;
                    case BOTH:
                        // handle case where a row with the same key is in both
                        // cursors
                        break;
                    }
                }
            }
            if (currentCRSms == CR_SMS_A) {
                currentCRSms = CR_SMS_B;
            } else {
                currentCRSms = CR_SMS_A;
            }
        }
    }

    /**
     * Check for change in MMS folder and send a notification if there is
     * a change
     */
    private int checkMmsFolder(String folderMms, Cursor crMmsFolderA,
            Cursor crMmsFolderB, int currentCRMmsFolder) {

        int currentItemCount = 0;
        int newItemCount = 0;

        if (currentCRMmsFolder == CR_MMS_FOLDER_A) {
            currentItemCount = crMmsFolderA.getCount();
            crMmsFolderB.requery();
            newItemCount = crMmsFolderB.getCount();
        } else {
            currentItemCount = crMmsFolderB.getCount();
            crMmsFolderA.requery();
            newItemCount = crMmsFolderA.getCount();
        }

        if (V){
            Log.v(TAG, "FOLDER Name::" + folderMms);
            Log.v(TAG, "MMS FOLDER current " + currentItemCount + " new "
                    + newItemCount);
        }

        if (currentItemCount > newItemCount) {
            crMmsFolderA.moveToFirst();
            crMmsFolderB.moveToFirst();

            CursorJoiner joiner = new CursorJoiner(crMmsFolderA,
                    new String[] { "_id"}, crMmsFolderB, new String[] { "_id"});

            CursorJoiner.Result joinerResult;
            while (joiner.hasNext()) {
                joinerResult = joiner.next();
                switch (joinerResult) {
                case LEFT:
                    // handle case where a row in cursor1 is unique
                    if (currentCRMmsFolder == CR_MMS_FOLDER_A) {
                        // The new query doesn't have this row; implies it
                        // was deleted
                        if (V){
                            Log.v(TAG, " MMS DELETED FROM FOLDER ");
                        }
                        String id = crMmsFolderA.getString(crMmsFolderA
                                .getColumnIndex("_id"));
                        int msgInfo = 0;
                        msgInfo = crMmsFolderA.getInt(crMmsFolderA.getColumnIndex("m_type"));
                        String mId = crMmsFolderA.getString(crMmsFolderA.getColumnIndex("m_id"));
                        int msgType = getMmsContainingFolder(Integer
                                .parseInt(id));
                        if (msgType == -1) {
                            // Convert to virtual handle for MMS
                            id = Integer.toString(Integer.valueOf(id)
                                    + MMS_HDLR_CONSTANT);
                            if (V){
                                Log.v(TAG, " DELETED MMS ID " + id);
                            }
                            if (((msgInfo > 0) && (msgInfo != MSG_META_DATA_TYPE)
                                    && (msgInfo != MSG_DELIVERY_RPT_TYPE))
                                    && (mId != null && mId.length() > 0)){
                                btMns.sendMnsEvent(MESSAGE_DELETED, id,
                                        MapUtilsConsts.Telecom + "/" +
                                        MapUtilsConsts.Msg + "/" +
                                        folderMms, null, "MMS");
                            }
                        } else {
                            if (folderMms != null &&
                                    folderMms.equalsIgnoreCase(MapUtilsConsts.Draft)){
                                // Convert to virtual handle for MMS
                                id = Integer.toString(Integer.valueOf(id)
                                        + MMS_HDLR_CONSTANT);
                                if (V){
                                    Log.v(TAG, " DELETED MMS ID " + id);
                                }
                                String newFolder = getMAPFolder(msgType);
                                if ((newFolder != null)
                                        && (!newFolder.equalsIgnoreCase(MapUtilsConsts.Draft))) {
                                    // The message has moved on MAP virtual
                                    // folder representation.
                                    btMns.sendMnsEvent(MESSAGE_SHIFT, id,
                                            MapUtilsConsts.Telecom + "/" +
                                            MapUtilsConsts.Msg + "/"
                                            + newFolder, MapUtilsConsts.Telecom +
                                            "/" + MapUtilsConsts.Msg + "/" +
                                            MapUtilsConsts.Draft, "MMS");
                                    if (newFolder.equalsIgnoreCase("sent")) {
                                        btMns.sendMnsEvent(SENDING_SUCCESS, id,
                                                MapUtilsConsts.Telecom + "/" +
                                                MapUtilsConsts.Msg + "/" + newFolder,
                                                null, "MMS");
                                    }
                                }
                            } else if (folderMms != null &&
                                    folderMms.equalsIgnoreCase(MapUtilsConsts.Outbox)){
                                String newFolder = getMAPFolder(msgType);
                                // Convert to virtual handle for MMS
                                id = Integer.toString(Integer.valueOf(id)
                                        + MMS_HDLR_CONSTANT);

                                if (V){
                                    Log.v(TAG, " MESSAGE_SHIFT MMS ID " + id);
                                }
                                if ((newFolder != null)
                                        && (!newFolder.equalsIgnoreCase(MapUtilsConsts.Outbox))) {
                                    // The message has moved on MAP virtual
                                    // folder representation.
                                    btMns.sendMnsEvent(MESSAGE_SHIFT, id,
                                            MapUtilsConsts.Telecom + "/" +
                                            MapUtilsConsts.Msg + "/"
                                            + newFolder, MapUtilsConsts.Telecom + "/"+
                                            MapUtilsConsts.Msg + "/"+
                                            MapUtilsConsts.Outbox, "MMS");
                                    if (newFolder.equalsIgnoreCase(MapUtilsConsts.Sent)) {
                                        btMns.sendMnsEvent(SENDING_SUCCESS, id,
                                                MapUtilsConsts.Telecom + "/"+
                                                MapUtilsConsts.Msg + "/" + newFolder,
                                                null, "MMS");
                                    }
                                }
                                /* Mms doesn't have failed or queued type
                                 * Cannot send SENDING_FAILURE as there
                                 * is no indication if Sending failed
                                 */
                            }
                        }
                    } else {
                        // TODO - The current(old) query doesn't have this
                        // row;
                        // implies it was added
                    }
                    break;
                case RIGHT:
                    // handle case where a row in cursor2 is unique
                    if (currentCRMmsFolder == CR_MMS_FOLDER_B) {
                        // The new query doesn't have this row; implies it
                        // was deleted
                        if (V){
                            Log.v(TAG, " MMS DELETED FROM "+folderMms);
                        }
                        String id = crMmsFolderB.getString(crMmsFolderB
                                .getColumnIndex("_id"));
                        int msgInfo = 0;
                        msgInfo = crMmsFolderB.getInt(crMmsFolderB.getColumnIndex("m_type"));
                        String mId = crMmsFolderB.getString(crMmsFolderB.getColumnIndex("m_id"));
                        int msgType = getMmsContainingFolder(Integer
                                .parseInt(id));
                        if (msgType == -1) {
                            // Convert to virtual handle for MMS
                            id = Integer.toString(Integer.valueOf(id)
                                    + MMS_HDLR_CONSTANT);
                            if (V){
                                Log.v(TAG, " DELETED MMS ID " + id);
                            }
                            if (((msgInfo > 0) && (msgInfo != MSG_META_DATA_TYPE)
                                    && (msgInfo != MSG_DELIVERY_RPT_TYPE))
                                    && (mId != null && mId.length() > 0)){
                                btMns.sendMnsEvent(MESSAGE_DELETED, id,
                                        MapUtilsConsts.Telecom + "/" +
                                        MapUtilsConsts.Msg + "/" +
                                        folderMms, null, "MMS");
                            }
                        } else {
                            if (folderMms != null &&
                                    folderMms.equalsIgnoreCase(MapUtilsConsts.Draft)){
                                // Convert to virtual handle for MMS
                                id = Integer.toString(Integer.valueOf(id)
                                        + MMS_HDLR_CONSTANT);
                                if (V){
                                    Log.v(TAG, " DELETED MMS ID " + id);
                                }
                                String newFolder = getMAPFolder(msgType);
                                if ((newFolder != null)
                                        && (!newFolder.equalsIgnoreCase(MapUtilsConsts.Draft))) {
                                    // The message has moved on MAP virtual
                                    // folder representation.
                                    btMns.sendMnsEvent(MESSAGE_SHIFT, id,
                                            MapUtilsConsts.Telecom + "/" +
                                            MapUtilsConsts.Msg + "/"
                                            + newFolder, MapUtilsConsts.Telecom +
                                            "/" + MapUtilsConsts.Msg + "/" +
                                            MapUtilsConsts.Draft, "MMS");
                                    if (newFolder.equalsIgnoreCase("sent")) {
                                        btMns.sendMnsEvent(SENDING_SUCCESS, id,
                                                MapUtilsConsts.Telecom + "/" +
                                                MapUtilsConsts.Msg + "/" + newFolder,
                                                null, "MMS");
                                    }
                                }
                            } else if (folderMms != null &&
                                    folderMms.equalsIgnoreCase(MapUtilsConsts.Outbox)){
                                // Convert to virtual handle for MMS
                                id = Integer.toString(Integer.valueOf(id)
                                        + MMS_HDLR_CONSTANT);
                                if (V){
                                    Log.v(TAG, " DELETED MMS ID " + id);
                                }
                                String newFolder = getMAPFolder(msgType);
                                if ((newFolder != null)
                                        && (!newFolder.equalsIgnoreCase(MapUtilsConsts.Outbox))) {
                                    // The message has moved on MAP virtual
                                    // folder representation.
                                    btMns.sendMnsEvent(MESSAGE_SHIFT, id,
                                            MapUtilsConsts.Telecom + "/" +
                                            MapUtilsConsts.Msg + "/"
                                            + newFolder, MapUtilsConsts.Telecom +
                                            "/" + MapUtilsConsts.Msg + "/" +
                                            MapUtilsConsts.Outbox, "MMS");
                                    if (newFolder.equalsIgnoreCase(MapUtilsConsts.Sent)) {
                                        btMns.sendMnsEvent(SENDING_SUCCESS, id,
                                                MapUtilsConsts.Telecom + "/" +
                                                MapUtilsConsts.Msg + "/" + newFolder,
                                                null, "MMS");
                                    }
                                }
                                /* Mms doesn't have failed or queued type
                                 * Cannot send SENDING_FAILURE as there
                                 * is no indication if Sending failed
                                 */
                            }
                        }
                    } else {
                        // The current(old) query doesn't have this row;
                        // implies it was added
                    }
                    break;
                case BOTH:
                    // handle case where a row with the same key is in both
                    // cursors
                    break;
                }
            }
        }
        if (currentCRMmsFolder == CR_MMS_FOLDER_A) {
            currentCRMmsFolder = CR_MMS_FOLDER_B;
        } else {
            currentCRMmsFolder = CR_MMS_FOLDER_A;
        }
        return currentCRMmsFolder;
    }

    /**
     * Check for MMS message being added and send a notification if there is a
     * change
     */
    private void checkMmsAdded() {

        int currentItemCount = 0;
        int newItemCount = 0;

        if (currentCRMms == CR_MMS_A) {
            currentItemCount = crMmsA.getCount();
            crMmsB.requery();
            newItemCount = crMmsB.getCount();
        } else {
            currentItemCount = crMmsB.getCount();
            crMmsA.requery();
            newItemCount = crMmsA.getCount();
        }

        if (V){
            Log.v(TAG, "MMS current " + currentItemCount + " new " + newItemCount);
        }

        if (newItemCount > currentItemCount) {
            crMmsA.moveToFirst();
            crMmsB.moveToFirst();

            CursorJoiner joiner = new CursorJoiner(crMmsA,
                    new String[] { "_id"}, crMmsB, new String[] { "_id"});

            CursorJoiner.Result joinerResult;
            while (joiner.hasNext()) {
                joinerResult = joiner.next();
                switch (joinerResult) {
                case LEFT:
                    // handle case where a row in cursor1 is unique
                    if (currentCRMms == CR_MMS_A) {
                        // The new query doesn't have this row; implies it
                        // was deleted
                    } else {
                        // The current(old) query doesn't have this row;
                        // implies it was added
                        if (V){
                            Log.v(TAG, " MMS ADDED TO INBOX ");
                        }
                        String id1 = crMmsA.getString(crMmsA
                                .getColumnIndex("_id"));
                        int msgInfo = 0;
                        msgInfo = crMmsA.getInt(crMmsA.getColumnIndex("m_type"));
                        String mId = crMmsA.getString(crMmsA.getColumnIndex("m_id"));
                        int msgType = getMmsContainingFolder(Integer
                                .parseInt(id1));
                        String folder = getMAPFolder(msgType);
                        if (folder != null &&
                                folder.equalsIgnoreCase(MapUtilsConsts.Inbox)) {
                            // Convert to virtual handle for MMS
                            id1 = Integer.toString(Integer.valueOf(id1)
                                    + MMS_HDLR_CONSTANT);
                            if (V){
                                Log.v(TAG, " ADDED MMS ID " + id1);
                            }
                            if (((msgInfo > 0) && (msgInfo != MSG_META_DATA_TYPE)
                                    && (msgInfo != MSG_DELIVERY_RPT_TYPE))
                                    && (mId != null && mId.length() > 0)){
                                btMns.sendMnsEvent(NEW_MESSAGE, id1,
                                        MapUtilsConsts.Telecom + "/" +
                                        MapUtilsConsts.Msg + "/"
                                        + folder, null, "MMS");
                            }
                        } else if (folder != null &&
                                !folder.equalsIgnoreCase(MapUtilsConsts.Inbox)) {
                            // Convert to virtual handle for MMS
                            id1 = Integer.toString(Integer.valueOf(id1)
                                    + MMS_HDLR_CONSTANT);
                            if (V){
                                Log.v(TAG, " ADDED MMS ID " + id1);
                            }
                            if (((msgInfo > 0) && (msgInfo != MSG_META_DATA_TYPE)
                                    && (msgInfo != MSG_DELIVERY_RPT_TYPE))
                                    && (mId != null && mId.length() > 0)){
                                btMns.sendMnsEvent(MESSAGE_SHIFT, id1,
                                        MapUtilsConsts.Telecom + "/" +
                                        MapUtilsConsts.Msg + "/"
                                        + folder, null, "MMS");
                            }
                        }

                        else {
                            if (V){
                                Log.v(TAG, " ADDED TO UNKNOWN FOLDER");
                            }
                        }
                    }
                    break;
                case RIGHT:
                    // handle case where a row in cursor2 is unique
                    if (currentCRMms == CR_MMS_B) {
                        // The new query doesn't have this row; implies it
                        // was deleted
                    } else {
                        // The current(old) query doesn't have this row;
                        // implies it was added
                        if (V){
                            Log.v(TAG, " MMS ADDED ");
                        }
                        String id1 = crMmsB.getString(crMmsB
                                .getColumnIndex("_id"));
                        int msgInfo = 0;
                        msgInfo = crMmsB.getInt(crMmsB.getColumnIndex("m_type"));
                        String mId = crMmsB.getString(crMmsB.getColumnIndex("m_id"));
                        int msgType = getMmsContainingFolder(Integer
                                .parseInt(id1));
                        String folder = getMAPFolder(msgType);
                        if (folder != null &&
                                folder.equalsIgnoreCase(MapUtilsConsts.Inbox)) {
                            // Convert to virtual handle for MMS
                            id1 = Integer.toString(Integer.valueOf(id1)
                                    + MMS_HDLR_CONSTANT);

                            if (V){
                                Log.v(TAG, " ADDED MMS ID " + id1);
                            }
                            if (((msgInfo > 0) && (msgInfo != MSG_META_DATA_TYPE)
                                    && (msgInfo != MSG_DELIVERY_RPT_TYPE))
                                    && (mId != null && mId.length() > 0)){
                                btMns.sendMnsEvent(NEW_MESSAGE, id1,
                                        MapUtilsConsts.Telecom + "/" +
                                        MapUtilsConsts.Msg + "/"
                                        + folder, null, "MMS");
                            }
                        } else if (folder != null &&
                                !folder.equalsIgnoreCase(MapUtilsConsts.Inbox)) {
                            // Convert to virtual handle for MMS
                            id1 = Integer.toString(Integer.valueOf(id1)
                                    + MMS_HDLR_CONSTANT);

                            if (V){
                                Log.v(TAG, " ADDED MMS ID " + id1);
                            }
                            if (((msgInfo > 0) && (msgInfo != MSG_META_DATA_TYPE)
                                    && (msgInfo != MSG_DELIVERY_RPT_TYPE))
                                    && (mId != null && mId.length() > 0)){
                                btMns.sendMnsEvent(MESSAGE_SHIFT, id1,
                                        MapUtilsConsts.Telecom + "/" +
                                        MapUtilsConsts.Msg + "/"
                                        + folder, null, "MMS");
                            }
                        } else {
                            if (V){
                                Log.v(TAG, " ADDED TO UNKNOWN FOLDER");
                            }
                        }
                    }
                    break;
                case BOTH:
                    // handle case where a row with the same key is in both
                    // cursors
                    break;
                }
            }
        }
        if (currentCRMms == CR_MMS_A) {
            currentCRMms = CR_MMS_B;
        } else {
            currentCRMms = CR_MMS_A;
        }
    }
    /**
     * Get the folder name (MAP representation) based on the message Handle
     */
    private int getMmsContainingFolder(int msgID) {
        int folderNum = -1;
        String whereClause = " _id= " + msgID;
        Uri uri = Uri.parse("content://mms/");
        ContentResolver cr = mContext.getContentResolver();
        Cursor cursor = cr.query(uri, null, whereClause, null, null);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            int msgboxInd = cursor.getColumnIndex("msg_box");
            folderNum = cursor.getInt(msgboxInd);
        }
        cursor.close();
        return folderNum;
    }

}
