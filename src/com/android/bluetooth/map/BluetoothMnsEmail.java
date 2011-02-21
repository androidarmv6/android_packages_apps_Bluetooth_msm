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

import com.android.bluetooth.map.MapUtils.EmailUtils;
import com.android.bluetooth.map.MapUtils.MapUtilsConsts;

import java.util.ArrayList;
import java.util.List;

import static com.android.bluetooth.map.IBluetoothMasApp.EMAIL_HDLR_CONSTANT;

/**
 * This class run an MNS session.
 */
public class BluetoothMnsEmail {
    private static final String TAG = "BluetoothMnsEmail";

    private static final boolean V = BluetoothMasService.VERBOSE;

    public static final int MNS_SEND_EVENT = 15;

    public static final String NEW_MESSAGE = "NewMessage";

    public static final String DELIVERY_SUCCESS = "DeliverySuccess";

    public static final String SENDING_SUCCESS = "SendingSuccess";

    public static final String DELIVERY_FAILURE = "DeliveryFailure";

    public static final String SENDING_FAILURE = "SendingFailure";

    public static final String MEMORY_FULL = "MemoryFull";

    public static final String MEMORY_AVAILABLE = "MemoryAvailable";

    public static final String MESSAGE_DELETED = "MessageDeleted";

    public static final String MESSAGE_SHIFT = "MessageShift";

    private Context mContext;
    private ArrayList<EmailFolderContentObserverClass> mFolderObserverList
            = new ArrayList<EmailFolderContentObserverClass>();

    BluetoothMns btMns = null;

    private boolean mIsRegistered = false;

    public BluetoothMnsEmail(Context context, BluetoothMns mnsObj) {
        /* check Bluetooth enable status */
        /*
         * normally it's impossible to reach here if BT is disabled. Just check
         * for safety
         */

        mContext = context;
        btMns = mnsObj;
    }

    public boolean isRegistered() {
        return mIsRegistered;
    }

    /**
     * Register with content provider to receive updates
     * of change on cursor.
     */
    public void register() {
        if (V) Log.v(TAG, "REGISTERING EMAIL MNS UPDATES");
        if (!mIsRegistered) {
            Uri emailUri = Uri.parse("content://com.android.email.provider/message");
            crEmailA = mContext.getContentResolver().query(emailUri,
                    new String[] { "_id", "mailboxkey"}, null, null, "_id asc");
            crEmailB = mContext.getContentResolver().query(emailUri,
                    new String[] { "_id", "mailboxkey"}, null, null, "_id asc");

            Uri emailObserverUri = Uri.parse("content://com.android.email.provider/message");
            mContext.getContentResolver().registerContentObserver(emailObserverUri,
                    true, emailContentObserver);

            // TODO get default email account for now, need to get pre-selected email account later
            long id = EmailUtils.getDefaultEmailAccountId(mContext);
            List<String> folderList = EmailUtils.getEmailFolderList(mContext, id);
            Uri emailFolderObserverUri = Uri.parse("content://com.android.email.provider/message");
            for (String folderName : folderList) {
                String emailFolderCondition = EmailUtils.getWhereIsQueryForTypeEmail(folderName, mContext);
                Cursor crEmailFolderA = mContext.getContentResolver().query(emailUri,
                        new String[] {  "_id", "mailboxkey"}, emailFolderCondition, null, "_id asc");
                Cursor crEmailFolderB = mContext.getContentResolver().query(emailUri,
                        new String[] {"_id", "mailboxkey"}, emailFolderCondition, null, "_id asc");
                EmailFolderContentObserverClass observer =
                        new EmailFolderContentObserverClass(folderName,
                        crEmailFolderA, crEmailFolderB, CR_EMAIL_FOLDER_A);
                mContext.getContentResolver().registerContentObserver(
                        emailFolderObserverUri, true, observer);
                mFolderObserverList.add(observer);
            }
            mIsRegistered = true;
            if (V) Log.v(TAG, "REGISTERING EMAIL MNS UPDATES DONE");
        }
    }

    /**
     * Stop listening to changes in cursor
     */
    public void deregister() {
        if (V) Log.v(TAG, "DEREGISTER EMAIL UPDATES");
        if (mIsRegistered) {
            mIsRegistered = false;
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.unregisterContentObserver(emailContentObserver);
            crEmailA.close();
            crEmailB.close();
            currentCREmail = CR_EMAIL_A;
            for (EmailFolderContentObserverClass observer : mFolderObserverList) {
                Cursor a = observer.crEmailFolderA;
                Cursor b = observer.crEmailFolderB;
                if (a != null) a.close();
                if (b != null) b.close();
                resolver.unregisterContentObserver(observer);
            }
            mFolderObserverList.clear();
        }
    }

    private EmailContentObserverClass emailContentObserver = new EmailContentObserverClass();

    private Cursor crEmailA = null;
    private Cursor crEmailB = null;

    private final int CR_EMAIL_A = 1;
    private final int CR_EMAIL_B = 2;
    private int currentCREmail = CR_EMAIL_A;

    private final int CR_EMAIL_FOLDER_A = 1;
    private final int CR_EMAIL_FOLDER_B = 2;

    /**
     * Gets the table type (as in Email Content Provider) for the
     * given id
     */
    private int getDeletedFlagEmail(String id) {
        int deletedFlag =0;
        Cursor cr = mContext.getContentResolver().query(
                Uri.parse("content://com.android.email.provider/message/" + id),
                new String[] { "_id", "mailboxKey"}, null, null, null);
        int folderId = -1;
        if (cr.moveToFirst()) {
            folderId = cr.getInt(cr.getColumnIndex("mailboxKey"));
        }

        Cursor cr1 = mContext.getContentResolver().query(
                Uri.parse("content://com.android.email.provider/mailbox"),
                new String[] { "_id", "displayName"}, "_id ="+ folderId, null, null);
        String folderName = null;
        if (cr1.moveToFirst()) {
            folderName = cr1.getString(cr1.getColumnIndex("displayName"));
        }
        if (folderName !=null && (folderName.equalsIgnoreCase("Trash") ||
                folderName.toUpperCase().contains("TRASH"))){
            deletedFlag = 1;
        }
        cr.close();
        cr1.close();
        return deletedFlag;
    }

    /**
     * This class listens for changes in Email Content Provider tables
     * It acts, only when an entry gets removed from the table
     */
    private class EmailFolderContentObserverClass extends ContentObserver {
        private String folder;
        private Cursor crEmailFolderA;
        private Cursor crEmailFolderB;
        private int currentCREmailFolder;

        public EmailFolderContentObserverClass(String folderName, Cursor crEmailFolderA,
                Cursor crEmailFolderB, int currentCREmailFolder) {
            super(null);
            this.folder = folderName;
            this.crEmailFolderA = crEmailFolderA;
            this.crEmailFolderB = crEmailFolderB;
            this.currentCREmailFolder = currentCREmailFolder;
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            int currentItemCount = 0;
            int newItemCount = 0;
            if (V){
                Log.v(TAG,"Flag value name in Observer class ::"+currentCREmailFolder);
            }

            if (currentCREmailFolder == CR_EMAIL_FOLDER_A) {
                currentItemCount = crEmailFolderA.getCount();
                crEmailFolderB.requery();
                newItemCount = crEmailFolderB.getCount();
            } else {
                currentItemCount = crEmailFolderB.getCount();
                crEmailFolderA.requery();
                newItemCount = crEmailFolderA.getCount();
            }

            if (V){
                Log.v(TAG, "EMAIL Deleted folder current " + currentItemCount + " new "
                        + newItemCount);
            }
            if (currentItemCount > newItemCount) {
                crEmailFolderA.moveToFirst();
                crEmailFolderB.moveToFirst();

                CursorJoiner joiner = new CursorJoiner(crEmailFolderA,
                        new String[] { "_id"}, crEmailFolderB,
                        new String[] { "_id"});

                CursorJoiner.Result joinerResult;
                while (joiner.hasNext()) {
                    joinerResult = joiner.next();
                    switch (joinerResult) {
                    case LEFT:
                        // handle case where a row in cursor1 is unique
                        if (currentCREmailFolder == CR_EMAIL_FOLDER_A) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                            if (V){
                                Log.v(TAG, " EMAIL DELETED FROM FOLDER ");
                            }
                            String id = crEmailFolderA.getString(crEmailFolderA
                                    .getColumnIndex("_id"));
                            if (V){
                                Log.v(TAG, " DELETED EMAIL ID " + id);
                            }
                            int deletedFlag = getDeletedFlagEmail(id);
                            if (deletedFlag == 1){
                                id = Integer.toString(Integer.valueOf(id)
                                        + EMAIL_HDLR_CONSTANT);
                                btMns.sendMnsEvent(MESSAGE_DELETED, id,
                                        MapUtilsConsts.Telecom+"/"+
                                        MapUtilsConsts.Msg+"/"+
                                        folder, null, "EMAIL");
                            } else {
                                if (V){
                                    Log.v(TAG, "Shouldn't reach here as you cannot "
                                            + "move msg from Inbox to any other folder");
                                }
                                if (folder != null && folder.equalsIgnoreCase("outbox")){
                                    Cursor cr1 = null;
                                    int folderId;
                                    String containingFolder = null;
                                    Uri uri1 = Uri.parse("content://com.android.email.provider/message");
                                    if (Integer.valueOf(id) > 200000){
                                        id = Integer.toString(Integer.valueOf(id)
                                                - EMAIL_HDLR_CONSTANT);
                                    }
                                    String whereClause = " _id = " + id;
                                    cr1 = mContext.getContentResolver().query(uri1, null, whereClause, null,
                                            null);

                                    if (cr1.getCount() > 0) {
                                        cr1.moveToFirst();
                                        folderId = cr1.getInt(cr1.getColumnIndex("mailboxKey"));
                                        containingFolder = EmailUtils.getContainingFolderEmail(folderId, mContext);
                                    }
                                    cr1.close();
                                    String newFolder = containingFolder;
                                    id = Integer.toString(Integer.valueOf(id)
                                            + EMAIL_HDLR_CONSTANT);
                                    if ((newFolder != null)
                                            && (!newFolder
                                            .equalsIgnoreCase("outbox"))) {
                                        // The message has moved on MAP virtual
                                        // folder representation.
                                        btMns.sendMnsEvent(MESSAGE_SHIFT, id,
                                                MapUtilsConsts.Telecom+"/"+
                                                MapUtilsConsts.Msg + "/" + newFolder,
                                                MapUtilsConsts.Telecom + "/"+
                                                MapUtilsConsts.Msg + "/"+ MapUtilsConsts.Outbox, "EMAIL");
                                        if (newFolder.equalsIgnoreCase("sent")) {
                                            btMns.sendMnsEvent(SENDING_SUCCESS, id,
                                                    MapUtilsConsts.Telecom + "/"+
                                                    MapUtilsConsts.Msg + "/" + newFolder,
                                                    null, "EMAIL");
                                        }
                                    }
                                } else if (folder !=null){
                                    Cursor cr1 = null;
                                    int folderId;
                                    String containingFolder = null;
                                    Uri uri1 = Uri.parse("content://com.android.email.provider/message");
                                    String whereClause = " _id = " + id;
                                    cr1 = mContext.getContentResolver().query(uri1, null, whereClause, null,
                                            null);

                                    if (cr1.getCount() > 0) {
                                        cr1.moveToFirst();
                                        folderId = cr1.getInt(cr1.getColumnIndex("mailboxKey"));
                                        containingFolder = EmailUtils.getContainingFolderEmail(folderId, mContext);
                                    }
                                    cr1.close();
                                    String newFolder = containingFolder;
                                    id = Integer.toString(Integer.valueOf(id)
                                            + EMAIL_HDLR_CONSTANT);
                                    btMns.sendMnsEvent(MESSAGE_SHIFT, id,
                                            MapUtilsConsts.Telecom + "/"+
                                            MapUtilsConsts.Msg + "/"
                                            + newFolder, MapUtilsConsts.Telecom +
                                            "/"+MapUtilsConsts.Msg + "/"
                                            +folder,
                                            "EMAIL");
                                }
                            }
                        } else {
                            // TODO - The current(old) query doesn't have this row;
                            // implies it was added
                        }
                        break;
                    case RIGHT:
                        // handle case where a row in cursor2 is unique
                        if (currentCREmailFolder == CR_EMAIL_FOLDER_B) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                            if (V){
                                Log.v(TAG, " EMAIL DELETED FROM FOLDER ");
                            }
                            String id = crEmailFolderB.getString(crEmailFolderB
                                    .getColumnIndex("_id"));
                            if (V){
                                Log.v(TAG, " DELETED EMAIL ID " + id);
                            }
                            int deletedFlag = getDeletedFlagEmail(id); //TODO
                            if (deletedFlag == 1){
                                id = Integer.toString(Integer.valueOf(id)
                                        + EMAIL_HDLR_CONSTANT);
                                btMns.sendMnsEvent(MESSAGE_DELETED, id,
                                        MapUtilsConsts.Telecom + "/" +
                                        MapUtilsConsts.Msg + "/" +folder, null, "EMAIL");
                            } else {
                                if (V){
                                    Log.v(TAG, "Shouldn't reach here as you cannot "
                                            + "move msg from Inbox to any other folder");
                                }
                                if (folder != null && folder.equalsIgnoreCase("outbox")){
                                    Cursor cr1 = null;
                                    int folderId;
                                    String containingFolder = null;
                                    Uri uri1 = Uri.parse("content://com.android.email.provider/message");
                                    if (Integer.valueOf(id) > 200000){
                                        id = Integer.toString(Integer.valueOf(id)
                                                - EMAIL_HDLR_CONSTANT);
                                    }
                                    String whereClause = " _id = " + id;
                                    cr1 = mContext.getContentResolver().query(uri1, null, whereClause, null,
                                            null);

                                    if (cr1.getCount() > 0) {
                                        cr1.moveToFirst();
                                        folderId = cr1.getInt(cr1.getColumnIndex("mailboxKey"));
                                        containingFolder = EmailUtils.getContainingFolderEmail(folderId, mContext);
                                    }
                                    cr1.close();
                                    String newFolder = containingFolder;
                                    id = Integer.toString(Integer.valueOf(id)
                                            + EMAIL_HDLR_CONSTANT);
                                    if ((newFolder != null)
                                            && (!newFolder
                                            .equalsIgnoreCase("outbox"))) {
                                        // The message has moved on MAP virtual
                                        // folder representation.
                                        btMns.sendMnsEvent(MESSAGE_SHIFT, id,
                                                MapUtilsConsts.Telecom + "/"+
                                                MapUtilsConsts.Msg + "/" + newFolder,
                                                MapUtilsConsts.Telecom + "/"+
                                                MapUtilsConsts.Msg + "/"+
                                                MapUtilsConsts.Outbox, "EMAIL");
                                        if (newFolder.equalsIgnoreCase("sent")) {
                                            btMns.sendMnsEvent(SENDING_SUCCESS, id,
                                                    MapUtilsConsts.Telecom + "/"+
                                                    MapUtilsConsts.Msg + "/" + newFolder,
                                                    null, "EMAIL");
                                        }
                                    }
                                } else if (folder !=null){
                                    Cursor cr1 = null;
                                    int folderId;
                                    String containingFolder = null;
                                    Uri uri1 = Uri.parse("content://com.android.email.provider/message");
                                    String whereClause = " _id = " + id;
                                    cr1 = mContext.getContentResolver().query(uri1, null, whereClause, null,
                                            null);

                                    if (cr1.getCount() > 0) {
                                        cr1.moveToFirst();
                                        folderId = cr1.getInt(cr1.getColumnIndex("mailboxKey"));
                                        containingFolder = EmailUtils.getContainingFolderEmail(folderId, mContext);
                                    }
                                    cr1.close();
                                    String newFolder = containingFolder;
                                    id = Integer.toString(Integer.valueOf(id)
                                            + EMAIL_HDLR_CONSTANT);
                                    btMns.sendMnsEvent(MESSAGE_SHIFT, id,
                                            MapUtilsConsts.Telecom + "/"+
                                            MapUtilsConsts.Msg + "/"
                                            + newFolder, MapUtilsConsts.Telecom + "/"+
                                            MapUtilsConsts.Msg + "/"+ folder,
                                            "EMAIL");
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
            if (currentCREmailFolder == CR_EMAIL_FOLDER_A) {
                currentCREmailFolder = CR_EMAIL_FOLDER_B;
            } else {
                currentCREmailFolder = CR_EMAIL_FOLDER_A;
            }
        }
    }

    /**
     * This class listens for changes in Email Content Provider
     * It acts, only when a new entry gets added to database
     */
    private class EmailContentObserverClass extends ContentObserver {

        public EmailContentObserverClass() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            int currentItemCount = 0;
            int newItemCount = 0;
            String containingFolder = null;

            // Synchronize this
            if (currentCREmail == CR_EMAIL_A) {
                currentItemCount = crEmailA.getCount();
                crEmailB.requery();
                newItemCount = crEmailB.getCount();
            } else {
                currentItemCount = crEmailB.getCount();
                crEmailA.requery();
                newItemCount = crEmailA.getCount();
            }

            if (V){
                Log.v(TAG, "Email current " + currentItemCount + " new "
                        + newItemCount);
            }

            if (newItemCount > currentItemCount) {
                crEmailA.moveToFirst();
                crEmailB.moveToFirst();

                CursorJoiner joiner = new CursorJoiner(crEmailA,
                        new String[] { "_id"}, crEmailB, new String[] { "_id"});

                CursorJoiner.Result joinerResult;
                while (joiner.hasNext()) {
                    joinerResult = joiner.next();
                    switch (joinerResult) {
                    case LEFT:
                        // handle case where a row in cursor1 is unique
                        if (currentCREmail == CR_EMAIL_A) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                        } else {
                            // The current(old) query doesn't have this row;
                            // implies it was added
                            if (V){
                                Log.v(TAG, " EMAIL ADDED TO INBOX ");
                            }
                            String id1 = crEmailA.getString(crEmailA
                                    .getColumnIndex("_id"));
                            if (V){
                                Log.v(TAG, " ADDED EMAIL ID " + id1);
                            }
                            Cursor cr1 = null;
                            int folderId;
                            Uri uri1 = Uri.parse("content://com.android.email.provider/message");
                            String whereClause = " _id = " + id1;
                            cr1 = mContext.getContentResolver().query(uri1, null, whereClause, null,
                                    null);
                            if (cr1.moveToFirst()) {
                                do {
                                    for (int i=0;i<cr1.getColumnCount();i++){
                                        if (V){
                                            Log.v(TAG, " Column Name: "+ cr1.getColumnName(i) + " Value: " + cr1.getString(i));
                                        }
                                    }
                                } while (cr1.moveToNext());
                            }

                            if (cr1.getCount() > 0) {
                                cr1.moveToFirst();
                                folderId = cr1.getInt(cr1.getColumnIndex("mailboxKey"));
                                containingFolder = EmailUtils.getContainingFolderEmail(folderId, mContext);
                            }
                            cr1.close();
                            if (containingFolder != null
                                    && containingFolder.equalsIgnoreCase(MapUtilsConsts.Inbox)) {
                                if (V){
                                    Log.v(TAG, " containingFolder:: "+containingFolder);
                                }
                                id1 = Integer.toString(Integer.valueOf(id1)
                                        + EMAIL_HDLR_CONSTANT);
                                btMns.sendMnsEvent(NEW_MESSAGE, id1, MapUtilsConsts.Telecom
                                        + "/"+ MapUtilsConsts.Msg + "/"
                                        + containingFolder, null, "EMAIL");
                            } else if (containingFolder != null
                                    && !containingFolder.equalsIgnoreCase(MapUtilsConsts.Inbox)) {
                                if (V){
                                    Log.v(TAG, " containingFolder:: "+containingFolder);
                                }
                                id1 = Integer.toString(Integer.valueOf(id1)
                                        + EMAIL_HDLR_CONSTANT);
                                btMns.sendMnsEvent(MESSAGE_SHIFT, id1, MapUtilsConsts.Telecom
                                        + "/"+ MapUtilsConsts.Msg + "/"
                                        + containingFolder, null, "EMAIL");
                            } else {
                                if (V){
                                    Log.v(TAG, " ADDED TO UNKNOWN FOLDER");
                                }
                            }
                        }
                        break;
                    case RIGHT:
                        // handle case where a row in cursor2 is unique
                        if (currentCREmail == CR_EMAIL_B) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                        } else {
                            // The current(old) query doesn't have this row;
                            // implies it was added
                            if (V){
                                Log.v(TAG, " EMAIL ADDED ");
                            }
                            String id1 = crEmailB.getString(crEmailB
                                    .getColumnIndex("_id"));
                            if (V){
                                Log.v(TAG, " ADDED EMAIL ID " + id1);
                            }
                            Cursor cr1 = null;
                            int folderId;
                            Uri uri1 = Uri.parse("content://com.android.email.provider/message");
                            String whereClause = " _id = " + id1;
                            cr1 = mContext.getContentResolver().query(uri1, null, whereClause, null,
                                    null);

                            if (cr1.moveToFirst()) {
                                do {
                                    for (int i=0;i<cr1.getColumnCount();i++){
                                        if (V){
                                            Log.v(TAG, " Column Name: "+ cr1.getColumnName(i) +
                                                    " Value: " + cr1.getString(i));
                                        }
                                    }
                                } while (cr1.moveToNext());
                            }

                            if (cr1.getCount() > 0) {
                                cr1.moveToFirst();
                                folderId = cr1.getInt(cr1.getColumnIndex("mailboxKey"));
                                containingFolder = EmailUtils.getContainingFolderEmail(folderId, mContext);
                            }
                            cr1.close();
                            if (containingFolder != null
                                    && containingFolder.equalsIgnoreCase(MapUtilsConsts.Inbox)) {
                                if (V){
                                    Log.v(TAG, " containingFolder:: "+containingFolder);
                                }
                                id1 = Integer.toString(Integer.valueOf(id1)
                                        + EMAIL_HDLR_CONSTANT);
                                btMns.sendMnsEvent(NEW_MESSAGE, id1, MapUtilsConsts.Telecom
                                        + "/" + MapUtilsConsts.Msg + "/"
                                        + containingFolder, null, "EMAIL");
                            } else if (containingFolder != null
                                    && !containingFolder.equalsIgnoreCase(MapUtilsConsts.Inbox)) {
                                if (V){
                                    Log.v(TAG, " containingFolder:: "+containingFolder);
                                }
                                id1 = Integer.toString(Integer.valueOf(id1)
                                        + EMAIL_HDLR_CONSTANT);
                                btMns.sendMnsEvent(MESSAGE_SHIFT, id1, MapUtilsConsts.Telecom
                                        + "/" + MapUtilsConsts.Msg + "/"
                                        + containingFolder, null, "EMAIL");
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
            if (currentCREmail == CR_EMAIL_A) {
                currentCREmail = CR_EMAIL_B;
            } else {
                currentCREmail = CR_EMAIL_A;
            }
        }
    }

}
