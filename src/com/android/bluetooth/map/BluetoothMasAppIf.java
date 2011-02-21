/*
 * Copyright (c) 2010-2011, Code Aurora Forum. All rights reserved.
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

import android.bluetooth.BluetoothDevice;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.Time;
import android.util.Log;
import android.util.TimeFormatException;

import com.android.bluetooth.map.MapUtils.BmessageConsts;
import com.android.bluetooth.map.MapUtils.CommonUtils;
import com.android.bluetooth.map.MapUtils.EmailUtils;
import com.android.bluetooth.map.MapUtils.MapUtils;
import com.android.bluetooth.map.MapUtils.MsgListingConsts;
import com.android.bluetooth.map.MapUtils.SmsMmsUtils;
import com.android.bluetooth.map.MapUtils.SortMsgListByDate;
import com.android.bluetooth.map.MapUtils.CommonUtils.BluetoothMasMessageListingRsp;
import com.android.bluetooth.map.MapUtils.CommonUtils.BluetoothMasMessageRsp;
import com.android.bluetooth.map.MapUtils.CommonUtils.BluetoothMasPushMsgRsp;
import com.android.bluetooth.map.MapUtils.CommonUtils.BluetoothMsgListRsp;
import com.android.bluetooth.map.MapUtils.MapUtils.BadRequestException;
import com.android.bluetooth.map.MapUtils.SmsMmsUtils.VcardContent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;

import javax.obex.ResponseCodes;

import static com.android.bluetooth.map.MapUtils.SmsMmsUtils.DELETED;
import static com.android.bluetooth.map.MapUtils.SmsMmsUtils.DRAFT;
import static com.android.bluetooth.map.MapUtils.SmsMmsUtils.DRAFTS;
import static com.android.bluetooth.map.MapUtils.SmsMmsUtils.FAILED;
import static com.android.bluetooth.map.MapUtils.SmsMmsUtils.INBOX;
import static com.android.bluetooth.map.MapUtils.SmsMmsUtils.OUTBOX;
import static com.android.bluetooth.map.MapUtils.SmsMmsUtils.QUEUED;
import static com.android.bluetooth.map.MapUtils.SmsMmsUtils.SENT;

/**
 * This class provides the application interface for MAS Server It interacts
 * with the SMS repository using Sms Content Provider to service the MAS
 * requests. It also initializes BluetoothMns thread which is used for MNS
 * connection.
 */

public abstract class BluetoothMasAppIf implements IBluetoothMasApp {
    public static final String TAG = "BluetoothMasAppIf";
    public static final boolean D = BluetoothMasService.DEBUG;
    public static final boolean V = BluetoothMasService.VERBOSE;

    private static final String INTERNAL_ERROR = "ERROR";

    protected Context mContext;
    protected int mSupportedMessageTypes;
    protected int mMasId;

    protected String mCurrentPath = null;
    protected BluetoothMns mnsClient;
    protected Handler mHandler = null;

    public BluetoothMasAppIf(Context context, Handler handler, int supportedMessageTypes,
            int masId) {
        mContext = context;
        mSupportedMessageTypes = supportedMessageTypes;
        mMasId = masId;
        mHandler = handler;

        if (V) Log.v(TAG, "Constructor called");
    }

    /**
     * This must be overridden
     * @return folder list for corresponding MAS
     */
    protected abstract List<String> getCompleteFolderList();

    /**
     * Check the path to a given folder. If setPathFlag is set,
     * set the path to the new value. Else, just check if the path
     * exists and don't change the current path.
     *
     * @return true if the path exists, and could be accessed.
     */
    public boolean checkPath(boolean up, String name, boolean setPathFlag) {
        if (V) Log.v(TAG, "setPath called");
        if (V) {
            Log.v(TAG, "mCurrentPath::"+mCurrentPath);
            Log.v(TAG, "name::"+name);
        }
        if (up == false) {
            if(name == null || name.length() == 0) {
                mCurrentPath = (setPathFlag) ? null : mCurrentPath;
                return true;
            }
        } else {
            if (mCurrentPath == null) {
                // Can't go above root
                return false;
            } else {
                int LastIndex;
                if (mCurrentPath.toUpperCase().contains("GMAIL")) {
                    LastIndex = mCurrentPath.lastIndexOf('/');
                    mCurrentPath = mCurrentPath.substring(0, LastIndex);
                    LastIndex = mCurrentPath.lastIndexOf('/');
                } else {
                    LastIndex = mCurrentPath.lastIndexOf('/');
                }
                if (LastIndex < 0) {
                    // Reaches root
                    mCurrentPath = null;
                } else {
                    mCurrentPath = mCurrentPath.substring(0, LastIndex);
                }
            }
            if (name == null) {
                // Only going up by one
                return true;
            }
        }

        if (mCurrentPath == null) {
            if (TELECOM.equals(name)) {
                mCurrentPath = (setPathFlag) ? TELECOM : mCurrentPath;
                return true;
            } else {
                return false;
            }
        }

        String splitStrings[] = mCurrentPath.split("/");

        boolean Result = false;
        switch (splitStrings.length) {
        case 1:
            if (name.equals(MSG)) {
                mCurrentPath += (setPathFlag) ? ("/" + name) : "";
                Result = true;
            }
            break;
        case 2:
            List<String> completeFolderList = getCompleteFolderList();
            for (String FolderName : completeFolderList) {
                //added second condition for gmail sent folder
                if (FolderName.equalsIgnoreCase(name)) {
                    mCurrentPath += (setPathFlag) ? ("/" + name) : "";
                    Result = true;
                    break;
                }
            }
            break;
            // TODO SUBFOLDERS: Add check for sub-folders (add more cases)

        default:
            Result = false;
            break;
        }
        return Result;
    }

    /**
     * Set the path to a given folder.
     *
     * @return true if the path exists, and could be accessed.
     */
    public boolean setPath(boolean up, String name) {
        return checkPath(up, name, true);
    }

    /**
     * Get the number of messages in the folder
     *
     * @return number of messages; -1 if error
     */
    public int folderListingSize() {
        if (V) Log.v(TAG, "folderListingSize called, current path " + mCurrentPath);

        if (mCurrentPath == null) {
            // at root, only telecom folder should be present
            return 1;
        }

        if (mCurrentPath.equals(TELECOM)) {
            // at root -> telecom, only msg folder should be present
            return 1;
        }

        if (mCurrentPath.equals(TELECOM + "/" + MSG)) {
            // at root -> telecom -> msg, FolderList should be present
            List<String> completeFolderList = getCompleteFolderList();
            return completeFolderList.size();
        }
        // TODO SUBFOLDERS: Add check for sub-folders

        return 0;
    }

    /**
     * Get the XML listing of the folders at CurrenthPath
     *
     * @return XML listing of the folders
     */
    public String folderListing(BluetoothMasAppParams appParam) {
        if (V) Log.v(TAG, "folderListing called, current path " + mCurrentPath);

        List<String> list = new ArrayList<String>();

        if (mCurrentPath == null) {
            // at root, only telecom folder should be present
            if (appParam.ListStartOffset == 0) {
                list.add(TELECOM);
            }
            return MapUtils.folderListingXML(list);
        }

        if (mCurrentPath.equals(TELECOM)) {
            // at root -> telecom, only msg folder should be present
            if (appParam.ListStartOffset == 0) {
                list.add(MSG);
            }
            return MapUtils.folderListingXML(list);
        }

        if (mCurrentPath.equals(TELECOM + "/" + MSG)) {
            int offset = 0;
            int added = 0;
            // at root -> telecom -> msg, FolderList should be present
            List<String> completeFolderList = getCompleteFolderList();
            for (String Folder : completeFolderList) {
                offset++;
                if ((offset > appParam.ListStartOffset)
                        && (added < appParam.MaxListCount)) {
                    list.add(Folder);
                    added++;
                }
            }
            return MapUtils.folderListingXML(list);
        }

        if (mCurrentPath.equals(TELECOM + "/" + MSG + "/" + INBOX) ||
                mCurrentPath.equals(TELECOM + "/" + MSG + "/" + OUTBOX) ||
                mCurrentPath.equals(TELECOM + "/" + MSG + "/" + DRAFT) ||
                mCurrentPath.equals(TELECOM + "/" + MSG + "/" + DELETED) ||
                mCurrentPath.equals(TELECOM + "/" + MSG + "/" + SENT)
        ) {
            return MapUtils.folderListingXML(list);
        } else {
            List<String> completeFolderList = getCompleteFolderList();
            for (String Folder : completeFolderList) {
                if (mCurrentPath.equalsIgnoreCase(TELECOM + "/" + MSG + "/" + Folder)) {
                    return MapUtils.folderListingXML(list);
                }
            }
        }

        return null;
    }

    static final int PHONELOOKUP_ID_COLUMN_INDEX = 0;
    static final int PHONELOOKUP_LOOKUP_KEY_COLUMN_INDEX = 1;
    static final int PHONELOOKUP_DISPLAY_NAME_COLUMN_INDEX = 2;

    static final int EMAIL_DATA_COLUMN_INDEX = 0;

    private List<VcardContent> list;

    private VcardContent getVcardContent(String phoneAddress) {
        VcardContent vCard = new VcardContent();
        vCard.tel = phoneAddress;

        Uri uriContacts = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneAddress));
        Cursor cursorContacts = mContext.getContentResolver().query(
                uriContacts,
                new String[] { PhoneLookup._ID, PhoneLookup.LOOKUP_KEY,
                        PhoneLookup.DISPLAY_NAME }, null, null, null);
        if (cursorContacts == null) {
            return vCard;
        }
        cursorContacts.moveToFirst();
        if (cursorContacts.getCount() > 0) {
            long contactId = cursorContacts
                    .getLong(PHONELOOKUP_ID_COLUMN_INDEX);
            String lookupKey = cursorContacts
                    .getString(PHONELOOKUP_LOOKUP_KEY_COLUMN_INDEX);

            Uri lookUpUri = Contacts.getLookupUri(contactId, lookupKey);
            String Id = lookUpUri.getLastPathSegment();

            Cursor crEm = mContext.getContentResolver().query(Email.CONTENT_URI,
                    new String[] { Email.DATA }, Email.CONTACT_ID + "=?",
                    new String[] { Id }, null);
            if (crEm != null) {
                if (crEm.moveToFirst()) {
                    vCard.name = cursorContacts
                            .getString(PHONELOOKUP_DISPLAY_NAME_COLUMN_INDEX);
                    vCard.email = "";
                    if (crEm.moveToFirst()) {
                        do {
                            vCard.email += crEm.getString(EMAIL_DATA_COLUMN_INDEX) + ";";
                        } while (crEm.moveToNext());
                    }
                }
                crEm.close();
            }
        }
        cursorContacts.close();
        return vCard;
    }

    /**
     * Check if the entry is not to be filtered out (allowed)
     */
    private boolean allowEntry(String phoneAddress, String filterString) {
        boolean found = false;
        VcardContent foundEntry = null;
        for (VcardContent elem : list) {
            if (elem.tel.contains(phoneAddress)) {
                found = true;
                foundEntry = elem;
            }
        }
        if (found == false) {
            VcardContent vCard = getVcardContent(phoneAddress);
            if (vCard != null) {
                list.add(vCard);
                found = true;
                foundEntry = vCard;
                if (V) {
                    Log.v(TAG, " NEW VCARD ADDED " + vCard.tel + vCard.name
                            + vCard.email);
                }
            } else {
                if (V) Log.v(TAG, "VCARD NOT FOUND ERROR");
            }
        }

        if (found == true) {
            String regExp = filterString.replace("*", ".*[0-9A-Za-z].*");
            if ((foundEntry.tel.matches(".*"+regExp+".*"))
                    || (foundEntry.name.matches(".*"+regExp+".*"))
                    || (foundEntry.email.matches(".*"+regExp+".*"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the contact name for the given phone number
     */
    private String getContactName(String phoneNumber) {
        // TODO Optimize this (get from list)

        boolean found = false;
        VcardContent foundEntry = null;
        if(phoneNumber == null){
            return null;
        }
        for (VcardContent elem : list) {
            if (elem.tel == null){
                continue;
            }
            if (elem.tel.contains(phoneNumber)) {
                found = true;
                foundEntry = elem;
                break;
            }
        }
        if (found == false) {
            foundEntry = getVcardContent(phoneNumber);
            if (foundEntry != null) {
                list.add(foundEntry);
                found = true;
            }
        }
        if (found == true) {
            return foundEntry.name;
        }

        return null;
    }

    private class OwnerInfo {
        public String Name;
        public String Number;
    }

    private OwnerInfo getOwnerInfo() {
        OwnerInfo info = new OwnerInfo();
        TelephonyManager tm = (TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) {
            String sLocalPhoneNum = tm.getLine1Number();
            String sLocalPhoneName = tm.getLine1AlphaTag();

            if (TextUtils.isEmpty(sLocalPhoneNum)) {
                sLocalPhoneNum = "0000000000";
            }
            if (TextUtils.isEmpty(sLocalPhoneName)) {
                sLocalPhoneName = "QCOM";
            }
            info.Name = sLocalPhoneName;
            info.Number = sLocalPhoneNum;
        }
        return info;
    }

    private OwnerInfo ownerInfo = null;

    /**
     * Get the owners name
     */
    public String getOwnerName() {
        // TODO
        if (ownerInfo == null) {
            ownerInfo = getOwnerInfo();
        }
        return ownerInfo.Name;
    }

    /**
     * Get the owners phone number
     */
    public String getOwnerNumber() {
        if (ownerInfo == null) {
            ownerInfo = getOwnerInfo();
        }
        return ownerInfo.Number;
    }

    private boolean isOutgoingSMSMessage(int type) {
        if (type == 1) {
            return false;
        }
        return true;
    }

    /**
     * Get the list of messages in the given folder
     *
     * @return Listing of messages in MAP-msg-listing format
     */
    public BluetoothMasMessageListingRsp msgListing(String name,
            BluetoothMasAppParams appParams) {
        BluetoothMasMessageListingRsp rsp = new BluetoothMasMessageListingRsp();
        boolean fileGenerated = false;

        final String FILENAME = "msglist" + getMasId();

        List<MsgListingConsts> msgList = new ArrayList<MsgListingConsts>();

        if (appParams == null) {
            return null;
        }

        String tempPath = null;
        if (name != null) {
            tempPath = CommonUtils.getFullPath(name, mContext, getCompleteFolderList(), mCurrentPath);
            if (tempPath == null) {
                // Child folder not present
                rsp.rsp = ResponseCodes.OBEX_HTTP_BAD_REQUEST;
                return rsp;
            }
        } else {
            tempPath = mCurrentPath;
        }

        if (V){
            Log.v(TAG, "appParams.FilterMessageType ::"+ appParams.FilterMessageType);
        }
        if (V){
            Log.v(TAG, "Condition result::"+ (appParams.FilterMessageType & 0x09));
        }
        // TODO: Take care of subfolders
        // TODO: Check only for SMS_GSM
        // Look for messages only if both SMS/MMS are not filtered out
        if ((tempPath != null) && (tempPath.split("/").length == 3 ||
                tempPath.split("/").length == 4)) {

            String splitStrings[] = tempPath.split("/");

            // TODO: Take care of subfolders

            if (V){
                Log.v(TAG, "splitString[2] = " + splitStrings[2]);
            }
            if (CommonUtils.validateFilterPeriods(appParams) == 0) {
                rsp.rsp = ResponseCodes.OBEX_HTTP_BAD_REQUEST;
                return rsp;
            }

            if (appParams.FilterReadStatus > 0x02) {
                rsp.rsp = ResponseCodes.OBEX_HTTP_BAD_REQUEST;
                return rsp;
            }

            // TODO Filter priority?
            /*
             * There is no support for FilterPriority in SMS/MMS. So, we will
             * assume Filter Priority is always non-high which makes sense for
             * SMS/MMS.We check the content provider only if the Filter Priority
             * is "unfiltered" or "non-high". Else, simply return an empty
             * string. If the Filter priority is greater than 2, return a bad
             * request.
             */
            if (appParams.FilterPriority == 0
                    || appParams.FilterPriority == 0x02) {
                if (supportSms(false)){
                    final int phoneType = TelephonyManager.getDefault().getPhoneType();
                    if ((appParams.FilterMessageType & 0x03) == 0 ||
                            ((appParams.FilterMessageType & 0x01) == 0 &&
                                    phoneType == TelephonyManager.PHONE_TYPE_GSM) ||
                            ((appParams.FilterMessageType & 0x02) == 0 &&
                                    phoneType == TelephonyManager.PHONE_TYPE_CDMA)) {
                        this.list = new ArrayList<VcardContent>();
                        BluetoothMsgListRsp bmlrSms = msgListSms(msgList, splitStrings[2],
                                rsp, appParams);
                        msgList = bmlrSms.msgList;
                        rsp = bmlrSms.rsp;
                    }
                }
                if (supportMms(false)) {
                    // Now that all of the SMS messages have been listed. Look for
                    // any
                    // MMS messages and provide them
                    if((appParams.FilterMessageType & 0x08) == 0) {
                        String splitStringsMms[] = tempPath.split("/");
                        name = splitStringsMms[2];

                        // MMS draft folder is called //mms/drafts not //mms/draft like
                        // SMS
                        if (name.equalsIgnoreCase("draft")) {
                            name = "drafts";
                        }
                        if(this.list == null){
                            this.list = new ArrayList<VcardContent>();
                        }
                        BluetoothMsgListRsp bmlrMms = msgListMms(msgList, name, rsp, appParams);
                        msgList = bmlrMms.msgList;
                        rsp = bmlrMms.rsp;
                    }
                }
                if (supportEmail(false)) {
                    //Email messages
                    if((appParams.FilterMessageType & 0x04) == 0){
                        BluetoothMsgListRsp bmlrEmail = msgListEmail(msgList, tempPath,
                                rsp, appParams);
                        msgList = bmlrEmail.msgList;
                        rsp = bmlrEmail.rsp;
                    }//end email Messages if
                }
            } else {
                if (appParams.FilterPriority > 0x02) {
                    rsp.rsp = ResponseCodes.OBEX_HTTP_BAD_REQUEST;
                }
            }
        }

        // Now that the message list exists, we can sort the list by date
        Collections.sort(msgList, new SortMsgListByDate());

        // Process the list based on MaxListCount and list offset
        String str = null;
        int numOfItems = msgList.size();
        int msgDelta = numOfItems - appParams.ListStartOffset;
        int startIdx = appParams.ListStartOffset;
        int stopIdx = 0;
        if (msgDelta <= 0) {
            List<MsgListingConsts> msgSubList = new ArrayList<MsgListingConsts>();;
            str = MapUtils.messageListingXML(msgSubList);
        } else {
            if (msgDelta <= appParams.MaxListCount) {
                stopIdx = startIdx + msgDelta;
            } else {
                stopIdx = startIdx + appParams.MaxListCount;
            }
            List<MsgListingConsts> msgSubList = msgList.subList(startIdx,
                    stopIdx);
            str = MapUtils.messageListingXML(msgSubList);
        }
        if (str == null) {
            rsp.rsp = ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            return rsp;
        }
        // TODO Undo the following check
        int pos = str.indexOf(" msg handle=\"");
        while (pos > 0) {
            if (V){
                Log.v(TAG, " Msg listing str modified");
            }
            String str2 = str.substring(0, pos);
            str2 += str.substring(pos + 1);
            str = str2;
            pos = str.indexOf(" msg handle=\"");
        }

        // String str = "this is a test for the data file";
        try {
            FileOutputStream bos = mContext.openFileOutput(FILENAME, Context.MODE_PRIVATE);
            bos.write(str.getBytes());
            bos.flush();
            bos.close();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        msgList.clear();

        if (V){
            Log.v(TAG, "");
            Log.v(TAG, " MESSAGE LISTING FULL ( total length)" + str.length());
            Log.v(TAG, str);
        }

        try {
            FileInputStream fis = new FileInputStream(mContext.getFilesDir()
                    + "/" + FILENAME);
            fis.close();
            fileGenerated = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (fileGenerated == true) {
            File file = new File(mContext.getFilesDir() + "/" + FILENAME);
            rsp.file = file;
        }
        rsp.rsp = ResponseCodes.OBEX_HTTP_OK;
        return rsp;
    }

    /**
     * Get the folder name (MAP representation) based on the folder type value
     * in SMS database
     */
    private String getMAPFolder(String type, String threadId) {
        String folder = null;
        if (type == null || threadId == null){
            if (V){
                Log.v(TAG, "getMapFolder cannot parse folder type");
            }
            return null;
        }

        if (Integer.valueOf(threadId) == DELETED_THREAD_ID){
            folder = DELETED;
        } else {
            switch (Integer.valueOf(type)) {
            case 1:
                folder = INBOX;
                break;
            case 2:
                folder = SENT;
                break;
            case 3:
                folder = DRAFT;
                break;
            case 4:
            case 5:
            case 6:
                folder = OUTBOX;
                break;
            default:
                break;
            }
        }
        return folder;
    }

    /**
     * Get the folder name (MAP representation) based on the message Handle
     */
    private String getContainingFolder(String msgHandle) {
        String folder = null;
        Cursor cr = mContext.getContentResolver().query(
                Uri.parse("content://sms/" + msgHandle),
                new String[] { "_id", "type", "thread_id"}, null, null, null);
        if (cr != null) {
            if (cr.getCount() > 0) {
                cr.moveToFirst();
                folder = getMAPFolder(cr.getString(cr.getColumnIndex("type")),
                        cr.getString(cr.getColumnIndex("thread_id")));
            }
            cr.close();
        }
        return folder;
    }

    /**
     * Get the SMS Deliver PDU for the given SMS
     */
    private String getSMSDeliverPdu(String smsBody, String dateTime, String address){
        Time time = new Time();
        time.set(Long.valueOf(dateTime));

        String timeStr = time.format3339(false);

        // Extract the YY, MM, DD, HH, MM, SS from time
        String tempTimeStr = timeStr.substring(2,4) + timeStr.substring(5, 7)
                + timeStr.substring(8, 10) + timeStr.substring(11, 13) +
                timeStr.substring(14, 16) + timeStr.substring(17, 19);

        /* Calculate the time zone offset
         * An offset of 1 indicates 15 min difference between local
         * time and GMT. MSB of 1 in offset indicates it is negative
         */
        String tZoneStr = timeStr.substring(timeStr.length()- 6);
        int tempInt = Integer.valueOf(tZoneStr.substring(tZoneStr.length()-2));
        int tZone15offset = tempInt / 15;

        tZone15offset += (Integer.valueOf(tZoneStr.substring(tZoneStr.length()-5, tZoneStr.length()-3)) * 4);
        if (timeStr.charAt(timeStr.length()-6) == '-'){
            tZone15offset = tZone15offset | 0x80;
        }

        String tZone15OffsetHexStr = "";

        // Add 0 as prefix for single digit offset
        if(((int) tZone15offset & 0xff) < 0x10){
            tZone15OffsetHexStr += "0";
        }
        tZone15OffsetHexStr += Integer.toHexString(tZone15offset);

        tempTimeStr += tZone15OffsetHexStr;

        // Swap the nibble
        String encodedTimeStr = "";
        for (int i=0; i<tempTimeStr.length(); i=i+2){
            encodedTimeStr += tempTimeStr.substring(i+1, i+2);
            encodedTimeStr += tempTimeStr.substring(i, i+1);
        }

        byte[] byteAddress = address.getBytes();

        // Let the service center number be 0000000000
        String smsPdu = "0681000000000004";

        // Extract only digits out of the phone address
        StringBuffer strbufAddress = new StringBuffer(address.length() + 1);
        for (int i=0; i<address.length(); i++){
            if (V){
                Log.v(TAG, " VAL " + address.substring(i, i+1));
            }
            if (byteAddress[i] >= 48 && byteAddress[i] <= 57){
                strbufAddress.append(Integer.parseInt(address.substring(i, i+1)));
            }
        }

        int addressLength = strbufAddress.length();

        String addressLengthStr = "";

        if(((int) addressLength & 0xff) < 0x10)
            addressLengthStr += "0";
        addressLengthStr += Integer.toHexString(addressLength);

        smsPdu = smsPdu + addressLengthStr;
        smsPdu = smsPdu + "81";

        String strAddress = new String(strbufAddress);

        // Use getSubmitPdu only to obtain the encoded msg and encoded address
        byte[] msg = SmsMessage.getSubmitPdu(null, strAddress, smsBody, false).encodedMessage;

        int addLength = Integer.valueOf(msg[2]);
        if (addLength %2 != 0){
            addLength++;
        }
        addLength = addLength / 2;

        // Extract the message from the SubmitPdu
        int msgOffset = 7 + addLength;
        int msgLength = msg.length - msgOffset;

        StringBuffer strbufMessage = new StringBuffer(msgLength * 2);

        // Convert from byte to Hex String
        for (int i=msgOffset; i<msgLength + msgOffset; i++) {
            if (((int) msg[i] & 0xff) < 0x10) {
                strbufMessage.append("0");
            }
            strbufMessage.append((Long.toString((int) msg[i] & 0xff, 16)));
        }

        int encodedAddressLength = strAddress.length() / 2;
        if (strAddress.length() % 2 != 0) {
            encodedAddressLength++;
        }

        StringBuffer strbufAddress1 = new StringBuffer(msgLength * 2);

        // Convert from byte to Hex String
        for(int i=4; i<encodedAddressLength + 4; i++)
        {
            if(((int) msg[i] & 0xff) < 0x10)
                strbufAddress1.append("0");
            strbufAddress1.append((Long.toString((int) msg[i] & 0xff, 16)));
        }

        smsPdu += strbufAddress1;
        smsPdu += "0000";
        smsPdu += encodedTimeStr;

        int smsBodyLength = smsBody.length();
        String smsMessageTextLengthStr = "";

        if(((int) smsBodyLength & 0xff) < 0x10){
            smsMessageTextLengthStr += "0";
        }
        smsMessageTextLengthStr += Integer.toHexString(smsBodyLength);

        smsPdu += smsMessageTextLengthStr;
        smsPdu += strbufMessage;
        smsPdu = smsPdu.toUpperCase();
        return smsPdu;
    }

    /**
     * Get the message for the given message handle
     *
     * @return BMSG object
     */
    public BluetoothMasMessageRsp msg(String msgHandle,
            BluetoothMasAppParams bluetoothMasAppParams) {
        BluetoothMasMessageRsp rsp = new BluetoothMasMessageRsp();
        if (msgHandle == null || msgHandle.length() == 0) {
            rsp.file = null;
            rsp.rsp = ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            return rsp;
        }
        if ((Integer.valueOf(msgHandle) > 0 &&
                Integer.valueOf(msgHandle) < 100000)){
            rsp = getMessageSms(msgHandle, mContext, rsp, bluetoothMasAppParams);
        } else if ((Integer.valueOf(msgHandle) > 100000 &&
                Integer.valueOf(msgHandle) < 200000)) {
            /*
            * Spec 5.6.4 says MSE shall reject request with value native
            * for MMS and Email
            */
            if ((int)bluetoothMasAppParams.Charset == 0) {
                rsp.rsp = ResponseCodes.OBEX_HTTP_BAD_REQUEST;
                return rsp;
            }
            rsp = getMessageMms(msgHandle, rsp);
        } else if ((Integer.valueOf(msgHandle) > 200000)) {
            // Email message
            rsp = getMessageEmail(msgHandle, rsp);
        }
        return rsp;
    }

    /**
     * Adds a SMS to the Sms ContentProvider
     */
    public String addToSmsFolder(String folder, String address, String text) {
        ContentValues values = new ContentValues();
        // thread_id is handled by SmsProvider
        values.put("body", text);
        values.put("address", address);
        values.put("read", 0);
        values.put("seen", 0);
        /*
         * status none -1 complete 0 pending 64 failed 128
         */
        values.put("status", -1);
        /*
         * outbox 4 queued 6
         */
        values.put("locked", 0);
        values.put("error_code", 0);
        Uri uri = mContext.getContentResolver().insert(
                Uri.parse("content://sms/" + folder), values);
        if (V){
            Log.v(TAG, " NEW URI " + ((uri == null) ? "null" : uri.toString()));
        }

        if (uri == null) {
            return INTERNAL_ERROR;
        }
        String str = uri.toString();
        String[] splitStr = str.split("/");
        if (splitStr.length < 4) {
            return INTERNAL_ERROR;
        }
        if (V){
            Log.v(TAG, " NEW HANDLE " + splitStr[3]);
        }
        return splitStr[3];
    }

    /**
     * Adds an Email to the Email ContentProvider
     */
    public String addToEmailFolder(String folder, String address, String text, String subject, String OrigEmail, String OrigName) {
        if (V){
            Log.v(TAG, "-------------");
            Log.v(TAG, "address " + address);
            Log.v(TAG, "TEXT " + text);
        }
        //TODO need to insert a row in the body table and update the mailbox table with the no of messages unread
        Cursor cr;
        int folderId = -1;
        long accountId = -1;
        int virtualMsgId;
        Time timeObj = new Time();
        timeObj.setToNow();

        Cursor cr1;
        String whereClause1 = "UPPER(emailAddress) LIKE  '"+OrigEmail.toUpperCase().trim()+"'";
        cr1 = mContext.getContentResolver().query(
                Uri.parse("content://com.android.email.provider/account"),
                null, whereClause1, null, null);
        if (cr1 != null) {
            if (cr1.getCount() > 0) {
                cr1.moveToFirst();
                accountId = cr1.getInt(cr1.getColumnIndex("_id"));
            }
            cr1.close();
        }
        if (accountId == -1) {
            accountId = EmailUtils.getDefaultEmailAccountId(mContext);
        }
        if (DRAFT.equalsIgnoreCase(folder)) {
            List<String> folders = EmailUtils.getFoldersForType(mContext, accountId,
                    EmailUtils.TYPE_DRAFT);
            if (V) Log.v(TAG, "DRAFT folders: " + folders.toString());
            if (folders.size() == 0) {
                // no draft folder
                return INTERNAL_ERROR;
            }
            if (folders.contains(DRAFTS)) {
                folder = DRAFTS;
            } else {
                folder = folders.get(0);
            }
        }

        String whereClause = "UPPER(displayName) = '"+folder.toUpperCase().trim()+"'";
        cr = mContext.getContentResolver().query(
                Uri.parse("content://com.android.email.provider/mailbox"),
                null, whereClause, null, null);
        if (cr != null) {
            if (cr.getCount() > 0) {
                cr.moveToFirst();
                folderId = cr.getInt(cr.getColumnIndex("_id"));
            }
            cr.close();
        }
        if (folderId == -1) {
            return INTERNAL_ERROR;
        }

        if (V){
            Log.v(TAG, "-------------");
            Log.v(TAG, "To address " + address);
            Log.v(TAG, "Text " + text);
            Log.v(TAG, "Originator email address:: " + OrigEmail);
            Log.v(TAG, "Originator email name:: " + OrigName);
            Log.v(TAG, "Time Stamp:: " + timeObj.toMillis(false));
            Log.v(TAG, "Account Key:: " + accountId);
            Log.v(TAG, "Folder Id:: " + folderId);
            Log.v(TAG, "Folder Name:: " + folder);
            Log.v(TAG, "Subject" + subject);
        }
        ContentValues values = new ContentValues();
        values.put("syncServerTimeStamp", 0);
        values.put("syncServerId", "5:65");
        values.put("displayName", OrigName.trim());
        values.put("timeStamp", timeObj.toMillis(false));
        values.put("subject", subject.trim());
        values.put("flagLoaded", "1");
        values.put("flagFavorite", "0");
        values.put("flagAttachment", "0");
        values.put("flags", "0");

        values.put("accountKey", accountId);
        values.put("fromList", OrigEmail.trim());

        values.put("mailboxKey", folderId);
        values.put("toList", address.trim());
        values.put("flagRead", 0);

        Uri uri = mContext.getContentResolver().insert(
                Uri.parse("content://com.android.email.provider/message"), values);
        if (V){
            Log.v(TAG, " NEW URI " + (uri == null ? "null" : uri.toString()));
        }

        if (uri == null) {
            return INTERNAL_ERROR;
        }
        String str = uri.toString();
        String[] splitStr = str.split("/");
        if (splitStr.length < 5) {
            return INTERNAL_ERROR;
        }
        if (V){
            Log.v(TAG, " NEW HANDLE " + splitStr[4]);
        }

        //TODO need to insert into the body table --seems like body table gets updated automatically
        ContentValues valuesBody = new ContentValues();
        valuesBody.put("messageKey", splitStr[4]);
        valuesBody.put("textContent", text);

        mContext.getContentResolver().insert(
                Uri.parse("content://com.android.email.provider/body"), valuesBody);
        virtualMsgId = Integer.valueOf(splitStr[4]) + EMAIL_HDLR_CONSTANT;
        return Integer.toString(virtualMsgId);
    }

    public long getEmailAccountId(String email) {
        Cursor cr1;
        long accountId = -1;
        String whereClause1 = "UPPER(emailAddress) LIKE  '"+email.toUpperCase().trim()+"'";
        cr1 = mContext.getContentResolver().query(
                Uri.parse("content://com.android.email.provider/account"),
                null, whereClause1, null, null);
        if (cr1 != null) {
            if (cr1.getCount() > 0) {
                cr1.moveToFirst();
                accountId = cr1.getInt(cr1.getColumnIndex("_id"));
            }
            cr1.close();
        }
        return accountId;
    }

    /**
     * Get the type (as in Sms ContentProvider) for the given table name
     */
    private int getSMSFolderType(String folder) {
        int type = 0;
        if (folder.equalsIgnoreCase(INBOX)) {
            type = 1;
        } else if (folder.equalsIgnoreCase(SENT)) {
            type = 2;
        } else if (folder.equalsIgnoreCase(DRAFT)) {
            type = 3;
        } else if (folder.equalsIgnoreCase(OUTBOX)) {
            type = 4;
        } else if (folder.equalsIgnoreCase(FAILED)) {
            type = 5;
        } else if (folder.equalsIgnoreCase(QUEUED)) {
            type = 6;
        }
        return type;
    }

    private String PhoneAddress;
    private String SmsText;
    private String SmsHandle;
    private String MmsHandle;

    private String EmailAddress;
    private String EmailText;
    private String EmailHandle;
    private String EmailSubject;
    private String EmailOriginator;
    private String EmailOrigName;

    /**
     * Push a outgoing message from MAS Client to the network
     *
     * @return Response to push command
     */
    public BluetoothMasPushMsgRsp pushMsg(String name, File file,
            BluetoothMasAppParams bluetoothMasAppParams) throws BadRequestException {
        BluetoothMasPushMsgRsp rsp = new BluetoothMasPushMsgRsp();
        rsp.response = ResponseCodes.OBEX_HTTP_UNAVAILABLE;
        rsp.msgHandle = null;

        if(!checkPath(false, name, false) ||
                mCurrentPath == null ||
                mCurrentPath.equals("telecom") ||
                (mCurrentPath.equals("telecom/msg") && (name == null))) {
            rsp.response = ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            return rsp;
        }
        byte[] readBytes = null;
        FileInputStream fis;
        try {
            fis = new FileInputStream(file);
            if(file.length() > EMAIL_MAX_PUSHMSG_SIZE){
                rsp.response = ResponseCodes.OBEX_HTTP_ENTITY_TOO_LARGE;
                rsp.msgHandle = null;
                Log.d(TAG,"Message body is larger than the max length allowed");
                return rsp;
            } else {
                readBytes = new byte[(int) file.length()];
                fis.read(readBytes);
            }
            fis.close();
        } catch (FileNotFoundException e) {
            Log.e(TAG, e.getMessage());
            return rsp;
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            return rsp;
        } catch (SecurityException e) {
            Log.e(TAG, e.getMessage());
            return rsp;
        }

        String readStr = "";
        String type = "";
        try {
            readStr = new String(readBytes);
            type = MapUtils.fetchType(readStr);
        } catch (Exception e) {
            throw new BadRequestException(e.getMessage());
        }
        if (type != null && (type.equalsIgnoreCase("SMS_GSM") || type.equalsIgnoreCase("SMS_CDMA")
                || type.equalsIgnoreCase("MMS"))) {
            String tmpPath = "";
            tmpPath = (name == null) ? mCurrentPath : mCurrentPath + "/" + name;

            if (type.equalsIgnoreCase("SMS_GSM") || type.equalsIgnoreCase("SMS_CDMA")) {
                BmessageConsts bMsg = MapUtils.fromBmessageSMS(readStr);
                PhoneAddress = bMsg.getRecipientVcard_phone_number();
                SmsText = bMsg.getBody_msg();
                rsp = pushMessageSms(rsp, tmpPath, name, bluetoothMasAppParams);
                return rsp;
            }

            // If the message to be pushed is an MMS message, extract any text,
            // discard
            // any attachments and convert the message to an SMS
            if (type.equalsIgnoreCase("MMS")) {
                /*
                 * The pair of calls below is used to send the MMS message out to
                 * the network.You need to first move the message to the drafts
                 * folder and then move the message from drafts to the outbox
                 * folder. This action causes the message to also be added to the
                 * pending_msgs table in the database. The transaction service will
                 * then send the message out to the network the next time it is
                 * scheduled to run
                 */
                rsp = pushMessageMms(rsp, tmpPath, readStr, name);
                return rsp;
            }
        } else if (type!=null && type.equalsIgnoreCase("EMAIL")) {
            rsp = pushMessageEmail(rsp, readStr, name);
            return rsp;
        }
        rsp.response = ResponseCodes.OBEX_HTTP_BAD_REQUEST;
        return rsp;
    }

    private void updateMMSThreadId(String msgHandle, int threadId){
        ContentValues values = new ContentValues();
        values.put("thread_id", threadId);
        mContext.getContentResolver().update( Uri.parse("content://mms/" + msgHandle), values, null, null);
    }

    private void deleteMMS(String msgHandle){
        Cursor cr = mContext.getContentResolver().query(Uri.parse("content://mms/" + msgHandle), null, null, null, null);
        if (cr != null && cr.moveToFirst()){
            int threadId = cr.getInt(cr.getColumnIndex(("thread_id")));
            if (threadId != DELETED_THREAD_ID){
                // Move to deleted folder
                updateMMSThreadId(msgHandle, Integer.valueOf(DELETED_THREAD_ID));
            } else {
                // Delete the message permanently
                int msgId = Integer.valueOf(msgHandle) + MMS_HDLR_CONSTANT;
                mnsClient.addMceInitiatedOperation(Integer.toString(msgId));
                mContext.getContentResolver().delete(Uri.parse("content://mms/" + msgHandle), null, null);
            }
        }
        if (cr != null) {
            cr.close();
        }
    }

    private void unDeleteMMS(String msgHandle) {
        Cursor cr = mContext.getContentResolver().query(Uri.parse("content://mms/" + msgHandle), null, null, null, null );
        if (cr == null) {
            if (V){
                Log.v(TAG, "unable to query content://mms/" + msgHandle);
            }
            return;
        }
        if (cr.moveToFirst()){

            // Make sure that the message is in delete folder
            String currentThreadId = cr.getString(cr.getColumnIndex("thread_id"));
            if (currentThreadId != null && Integer.valueOf(currentThreadId) != -1){
                if (V){
                    Log.v(TAG, " Not in delete folder");
                }
                return;
            }

            // Fetch the address of the deleted message

            String address = getMmsMsgAddress(Integer.valueOf(msgHandle));

            // Search the database for the given message ID
            Cursor crThreadId = mContext.getContentResolver().query(Uri.parse("content://mms/"), null,"_id = " + msgHandle + " AND thread_id != -1", null, null);
            if (crThreadId != null && crThreadId.moveToFirst()) {
                // A thread for the given message ID exists in the database
                String threadIdStr = crThreadId.getString(crThreadId.getColumnIndex("thread_id"));
                if (V){
                    Log.v(TAG, " THREAD ID " + threadIdStr);
                }
                updateMMSThreadId(msgHandle, Integer.valueOf(threadIdStr));
            } else {
                /* No thread for the given address
                 * Create a fake message to obtain the thread, use that thread_id
                 * and then delete the fake message
                 */
                ContentValues tempValue = new ContentValues();
                tempValue.put("address", address);
                tempValue.put("type", "20");
                Uri tempUri = mContext.getContentResolver().insert( Uri.parse("content://sms/"), tempValue);

                if (tempUri != null) {
                    Cursor tempCr = mContext.getContentResolver().query(tempUri, null, null, null, null);
                    if (tempCr != null) {
                        tempCr.moveToFirst();
                        String newThreadIdStr = tempCr.getString(tempCr.getColumnIndex("thread_id"));
                        tempCr.close();
                        updateMMSThreadId(msgHandle, Integer.valueOf(newThreadIdStr));
                    }
                    mContext.getContentResolver().delete(tempUri, null, null);
                } else {
                    Log.e(TAG, "Error in undelete");
                }
            }
            if (crThreadId != null) {
                crThreadId.close();
            }
        } else {
            if (V) {
                Log.v(TAG, "msgHandle not found");
            }
        }
        cr.close();
    }

    private void updateSMSThreadId(String msgHandle, int threadId){
        ContentValues values = new ContentValues();
        values.put("thread_id", threadId);
        mContext.getContentResolver().update( Uri.parse("content://sms/" + msgHandle), values, null, null);
    }

    private void deleteSMS(String msgHandle){
        Cursor cr = mContext.getContentResolver().query(Uri.parse("content://sms/" + msgHandle), null, null, null, null);
        if (cr != null && cr.moveToFirst()){
            int threadId = cr.getInt(cr.getColumnIndex(("thread_id")));
            if (threadId != DELETED_THREAD_ID){
                // Move to deleted folder
                updateSMSThreadId(msgHandle, Integer.valueOf(DELETED_THREAD_ID));
            } else {
                // Delete the message permanently
                mnsClient.addMceInitiatedOperation(msgHandle);
                mContext.getContentResolver().delete(Uri.parse("content://sms/" + msgHandle), null, null);
            }
        }
        if (cr != null) {
            cr.close();
        }
    }

    private void unDeleteSMS(String msgHandle){

        Cursor cr = mContext.getContentResolver().query(Uri.parse("content://sms/" + msgHandle), null, null, null, null );
        if (cr == null) {
            return;
        }

        if (cr.moveToFirst()){

            // Make sure that the message is in delete folder
            String currentThreadId = cr.getString(cr.getColumnIndex("thread_id"));
            if (currentThreadId != null && Integer.valueOf(currentThreadId) != -1){
                if (V){
                    Log.v(TAG, " Not in delete folder");
                }
                return;
            }

            // Fetch the address of the deleted message
            String address = cr.getString(cr.getColumnIndex("address"));

            // Search the database for the given address
            Cursor crThreadId = mContext.getContentResolver().query(Uri.parse("content://sms/"),
                    null, "address = " + address + " AND thread_id != -1", null, null);
            if (crThreadId != null && crThreadId.moveToFirst()) {
                // A thread for the given address exists in the database
                String threadIdStr = crThreadId.getString(crThreadId.getColumnIndex("thread_id"));
                if (V){
                    Log.v(TAG, " THREAD ID " + threadIdStr);
                }
                updateSMSThreadId(msgHandle, Integer.valueOf(threadIdStr));
            } else {
                /* No thread for the given address
                 * Create a fake message to obtain the thread, use that thread_id
                 * and then delete the fake message
                 */
                ContentValues tempValue = new ContentValues();
                tempValue.put("address", address);
                tempValue.put("type", "20");
                Uri tempUri = mContext.getContentResolver().insert( Uri.parse("content://sms/"), tempValue);

                if (tempUri != null) {
                    Cursor tempCr = mContext.getContentResolver().query(tempUri, null, null, null, null);
                    if (tempCr != null) {
                        tempCr.moveToFirst();
                        String newThreadIdStr = tempCr.getString(tempCr.getColumnIndex("thread_id"));
                        tempCr.close();
                        updateSMSThreadId(msgHandle, Integer.valueOf(newThreadIdStr));
                    }

                    mContext.getContentResolver().delete(tempUri, null, null);
                }
            }
            if (crThreadId != null) {
                crThreadId.close();
            }
        } else {
            if (V) {
                Log.v(TAG, "msgHandle not found");
            }
        }
        cr.close();
    }

    /**
     * Sets the message status (read/unread, delete)
     *
     * @return Obex response code
     */
    public int msgStatus(String name,
            BluetoothMasAppParams bluetoothMasAppParams) {

        if ((bluetoothMasAppParams.StatusIndicator != 0)
                && (bluetoothMasAppParams.StatusIndicator != 1)) {
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        }

        if ((bluetoothMasAppParams.StatusValue != 0)
                && (bluetoothMasAppParams.StatusValue != 1)) {
            return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
        }
        if(name != null && (Integer.valueOf(name) > 0 && 
                Integer.valueOf(name) < 100000)){
            return setMsgStatusSms(name, bluetoothMasAppParams);
        }
        // MMS MessageStatus processing begins here
        else if (name != null && (Integer.valueOf(name) > 100000 &&
                Integer.valueOf(name) < 200000)) {
            return setMsgStatusMms(name, bluetoothMasAppParams);
        }
        else if (name != null && (Integer.valueOf(name) > 200000)) {
            return setMsgStatusEmail(name, bluetoothMasAppParams);
        }
        return ResponseCodes.OBEX_HTTP_NOT_FOUND;
    }

    /**
     * Sets the message update
     *
     * @return Obex response code
     */
    public int msgUpdate(String name,
            BluetoothMasAppParams bluetoothMasAppParams) {
        if (V){
            Log.v(TAG, "Message Update");
        }

        long accountId = EmailUtils.getDefaultEmailAccountId(mContext);
        if (V){
            Log.v(TAG, " Account id for Inbox Update:: "+accountId);
        }

        Intent emailIn = new Intent();

        emailIn.setAction("com.android.email.intent.action.MAIL_SERVICE_WAKEUP");
        emailIn.putExtra("com.android.email.intent.extra.ACCOUNT", accountId);
        mContext.startService(emailIn);

        return ResponseCodes.OBEX_HTTP_OK;
    }

    /**
     * Enable/disable notification
     *
     * @return Obex response code
     */
    public int notification(BluetoothDevice remoteDevice,
            BluetoothMasAppParams bluetoothMasAppParams) {
        // TODO Auto-generated method stub

        if (bluetoothMasAppParams.Notification == 1) {
            startMnsSession(remoteDevice);
            return ResponseCodes.OBEX_HTTP_OK;
        } else if (bluetoothMasAppParams.Notification == 0) {
            stopMnsSession(remoteDevice);
            return ResponseCodes.OBEX_HTTP_OK;
        }

        return ResponseCodes.OBEX_HTTP_PRECON_FAILED;
    }

    /**
     * Start an MNS obex client session and push notification whenever available
     */
    public abstract void startMnsSession(BluetoothDevice remoteDevice);

    /**
     * Stop pushing notifications and disconnect MNS obex session
     */
    public abstract void stopMnsSession(BluetoothDevice remoteDevice);

    /**
     * Push an event over MNS client to MNS server
     */
    private void sendMnsEvent(String msg, String handle, String folder,
            String old_folder, String msgType) {
        if (V){
            Log.v(TAG, "Send MNS Event");
        }
        mnsClient.sendMnsEvent(msg, handle, folder, old_folder, msgType);
    }

    /**
     * Obtain the number of MMS messages
     */
    private int getNumMmsMsgs(String name) {
        int msgCount = 0;

        if ( name.equalsIgnoreCase(DELETED)){
            Uri uri = Uri.parse("content://mms/");
            ContentResolver cr = mContext.getContentResolver();
            Cursor cursor = cr.query(uri, null, "thread_id = " + DELETED_THREAD_ID, null, null);
            if(cursor != null){
                msgCount = cursor.getCount();
                cursor.close();
            }
        } else {
            Uri uri = Uri.parse("content://mms/" + name);
            ContentResolver cr = mContext.getContentResolver();
            Cursor cursor = cr.query(uri, null, "thread_id <> " + DELETED_THREAD_ID, null, null);
            if(cursor != null){
                msgCount = cursor.getCount();
                cursor.close();
            }
        }
        return msgCount;
    }

    /**
     * Obtain the MMS message Handle
     */
    private Integer getMmsMsgHnd(int msgID) {
        int handle = 0;
        String whereClause = " mid= " + msgID + " AND ct=\"text/plain\"";
        Uri uri = Uri.parse("content://mms/part");
        ContentResolver cr = mContext.getContentResolver();
        Cursor cursor = cr.query(uri, null, whereClause, null, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                int handleInd = cursor.getColumnIndex("_id");
                handle = cursor.getInt(handleInd);
            }
            cursor.close();
        }
        return handle;
    }

    /**
     * Obtain the MMS message ID from Handle
     */
    private Integer getMmsMsgHndToID(int msgHandle) {
        int msgID = -1;
        String whereClause = " mid= " + (msgHandle - MMS_HDLR_CONSTANT);
        Uri uri = Uri.parse("content://mms/part");
        ContentResolver cr = mContext.getContentResolver();
        Cursor cursor = cr.query(uri, null, whereClause, null, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                int handleInd = cursor.getColumnIndex("mid");
                msgID = cursor.getInt(handleInd);
            }
            cursor.close();
        }
        return msgID;
    }

    /**
     * Obtain the MMS message MID list
     */
    private List<Integer> getMmsMsgMIDs(String whereClause) {
        List<Integer> idList = new ArrayList<Integer>();
        Uri uri = Uri.parse("content://mms");
        ContentResolver cr = mContext.getContentResolver();
        Cursor crID = cr.query(uri, null, whereClause, null, null);
        if (crID != null) {
            int idInd = crID.getColumnIndex("_id");
            if (crID.getCount() != 0) {
                crID.moveToFirst();
                do {
                    idList.add(Integer.valueOf(crID.getInt(idInd)));
                } while (crID.moveToNext());
            }
            crID.close();
        }
        return idList;
    }

    /**
     * Build a whereclause for MMS filtering
     */
    private String bldMmsWhereClause(BluetoothMasAppParams appParams, int foldertype) {
        String whereClause = "";
        if ( foldertype != 0) {
            // Inbox, Outbox, Sent, Draft folders
            whereClause = "msg_box=" + foldertype + " AND thread_id <> " + DELETED_THREAD_ID;
        } else {
            // Deleted folder
            whereClause =  "thread_id = " + DELETED_THREAD_ID;
        }

        /* Filter readstatus: 0 no filtering, 0x01 get unread, 0x10 get read */
        if (appParams.FilterReadStatus != 0) {
            if ((appParams.FilterReadStatus & 0x1) != 0) {
                if (whereClause.length() != 0) {
                    whereClause += " AND ";
                }
                whereClause += " read=0 ";
            }
            if ((appParams.FilterReadStatus & 0x10) != 0) {
                if (whereClause.length() != 0) {
                    whereClause += " AND ";
                }
                whereClause += " read=1 ";
            }
        }

        /* Filter Period Begin */
        if ((appParams.FilterPeriodBegin != null)
                && (appParams.FilterPeriodBegin.length() > 0)) {
            Time time = new Time();
            try {
                time.parse(appParams.FilterPeriodBegin.trim());
                if (whereClause.length() != 0) {
                    whereClause += " AND ";
                }
                whereClause += "date >= " + (time.toMillis(false))/1000;
            } catch (TimeFormatException e) {
                Log.d(TAG, "Bad formatted FilterPeriodBegin "
                        + appParams.FilterPeriodBegin);
            }
        }

        /* Filter Period End */
        if ((appParams.FilterPeriodEnd != null)
                && (appParams.FilterPeriodEnd.length() > 0)) {
            Time time = new Time();
            try {
                time.parse(appParams.FilterPeriodEnd.trim());
                if (whereClause.length() != 0) {
                    whereClause += " AND ";
                }
                whereClause += "date < " + (time.toMillis(false))/1000;
            } catch (TimeFormatException e) {
                Log.d(TAG, "Bad formatted FilterPeriodEnd "
                        + appParams.FilterPeriodEnd);
            }
        }
        //Delivery report check
        if (whereClause.length() != 0) {
            whereClause += " AND ";
        }
        whereClause += "d_rpt > 0";

        return whereClause;
    }

    /**
     * Obtain the MMS msg_box id
     */
    private int getMmsMsgBox(int msgID) {
        int val = -1;
        String whereClause = " _id= " + msgID;
        Uri uri = Uri.parse("content://mms/");
        ContentResolver cr = mContext.getContentResolver();
        Cursor cursor = cr.query(uri, null, whereClause, null, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                int msgBoxInd = cursor.getColumnIndex("msg_box");
                val = cursor.getInt(msgBoxInd);
            }
            cursor.close();
        }
        return val;
    }

    /**
     * Obtain MMS message text
     */
    private String getMmsMsgTxt(int msgID) {
        String text = null;
        String whereClause = " mid= " + msgID + " AND ct=\"text/plain\"";
        Uri uri = Uri.parse("content://mms/part");
        ContentResolver cr = mContext.getContentResolver();
        Cursor cursor = cr.query(uri, null, whereClause, null, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                int textInd = cursor.getColumnIndex("text");
                text = cursor.getString(textInd);
            }
            cursor.close();
        }
        return text;
    }

    /**
     * Obtain the MMS message Subject
     */
    private String getMmsMsgSubject(int msgID) {
        String text = null;
        String whereClause = " _id= " + msgID;
        Uri uri = Uri.parse("content://mms/");
        ContentResolver cr = mContext.getContentResolver();
        Cursor cursor = cr.query(uri, null, whereClause, null, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                int subjectInd = cursor.getColumnIndex("sub");
                text = cursor.getString(subjectInd);
            }
            cursor.close();
        }
        return text;
    }

    /**
     * Obtain the MMS message Date
     */
    private String getMmsMsgDate(int msgID) {
        String text = "0";
        String whereClause = " _id= " + msgID;
        Uri uri = Uri.parse("content://mms/");
        ContentResolver cr = mContext.getContentResolver();
        Cursor cursor = cr.query(uri, null, whereClause, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                int dateInd = cursor.getColumnIndex("date");
                text = cursor.getString(dateInd);
            }
            cursor.close();
        }
        return text;

    }

    /**
     * Obtain the MMS attachment size
     */
    private int getMmsMsgAttachSize(int msgID) {
        int attachSize = 0;
        String whereClause = " _id= " + msgID;
        Uri uri = Uri.parse("content://mms/");
        ContentResolver cr = mContext.getContentResolver();
        Cursor cursor = cr.query(uri, null, whereClause, null, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                int sizeInd = cursor.getColumnIndex("m_size");
                attachSize = cursor.getInt(sizeInd);
            }
            cursor.close();
        }
        return attachSize;

    }

    /**
     * Obtain the MMS message read status
     */
    private String getMmsMsgReadStatus(int msgID) {
        String text = null;
        String whereClause = " _id= " + msgID;
        Uri uri = Uri.parse("content://mms/");
        ContentResolver cr = mContext.getContentResolver();
        Cursor cursor = cr.query(uri, null, whereClause, null, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                int readInd = cursor.getColumnIndex("read");
                if (cursor.getInt(readInd) == 0) {
                    text = "no";
                } else {
                    text = "yes";
                }
            }
            cursor.close();
        }
        return text;
    }

    /**
     * Obtain the MMS message read sent
     */
    private String getMmsMsgReadSent(int msgID) {
        String text = null;
        if ( getMmsMsgBox(msgID) == 2 ) {
            // Sent folder
            text = "yes";
        } else {
            text = "no";
        }
        return text;
    }

    /**
     * Obtain the MMS message priority
     */
    private String getMmsMsgPriority(int msgID) {
        final int PRIORITY_LOW = 0X80;
        final int PRIORITY_NORMAL = 0X81;
        final int PRIORITY_HIGH = 0X82;

        String text = null;
        String whereClause = " _id= " + msgID;
        Uri uri = Uri.parse("content://mms/");
        ContentResolver cr = mContext.getContentResolver();
        Cursor cursor = cr.query(uri, null, whereClause, null, null);
        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            int priInd = cursor.getColumnIndex("pri");

            switch (cursor.getInt(priInd)) {
            case PRIORITY_LOW:
                text = "no";
                break;
            case PRIORITY_NORMAL:
                text = "no";
                break;
            case PRIORITY_HIGH:
                text = "yes";
                break;

            default:
                text = "no";
                break;
            }
        }
        if (cursor != null) {
            cursor.close();
        }
        return text;
    }

    /**
     * Obtain the MMS message read protected
     */
    private String getMmsMsgProtected(int msgID) {
        String text = null;
        String whereClause = " _id= " + msgID;
        Uri uri = Uri.parse("content://mms/");
        ContentResolver cr = mContext.getContentResolver();
        Cursor cursor = cr.query(uri, null, whereClause, null, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                int readInd = cursor.getColumnIndex("locked");
                if (cursor.getInt(readInd) == 0) {
                    text = "no";
                } else {
                    text = "yes";
                }
            }
            cursor.close();
        }
        return text;

    }

    /**
     * Obtain MMS message address
     */
    private String getMmsMsgAddress(int msgID) {
        String text = null;
        String whereClause = " address != \"insert-address-token\"";
        Uri uri = Uri.parse("content://mms/" + msgID + "/addr");
        ContentResolver cr = mContext.getContentResolver();
        Cursor cursor = cr.query(uri, null, whereClause, null, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                int addressInd = cursor.getColumnIndex("address");
                text = cursor.getString(addressInd);
            }
            cursor.close();
        }
        return text;
    }

    /**
     * Get the folder name (MAP representation) based on the message Handle
     */
    private int getMmsContainingFolder(int msgID) {
        int folderNum = 0;
        String whereClause = " _id= " + msgID;
        Uri uri = Uri.parse("content://mms/");
        ContentResolver cr = mContext.getContentResolver();
        Cursor cursor = cr.query(uri, null, whereClause, null, null);
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                int msgboxInd = cursor.getColumnIndex("msg_box");
                String thread_id = cursor.getString(cursor.getColumnIndex("thread_id"));
                if ( Integer.valueOf(thread_id) == DELETED_THREAD_ID) {
                    // Deleted folder
                    folderNum = 0;
                } else {
                    folderNum = cursor.getInt(msgboxInd);
                }
            }
            cursor.close();
        }
        return folderNum;
    }

    /**
     * Get MMS folder name based on value Inbox = 1 Sent = 2 Drafts = 3 Outbox =
     * 4 Queued = 6
     *
     */
    private String getMmsMapVirtualFolderName(int type) {
        String folderName = null;

        switch (type) {
        case 0:
            folderName = DELETED;
            break;
        case 1:
            folderName = INBOX;
            break;
        case 2:
            folderName = SENT;
            break;
        case 3:
            folderName = DRAFT;
            break;
        case 4: // outbox
        case 5: // failed
        case 6: // queued
            folderName = OUTBOX;
            break;

        default:
            break;
        }
        return folderName;
    }

    /**
     * Build an MMS bMessage when given a message handle
     */
    private BluetoothMasMessageRsp bldMmsBmsg(int msgID, BluetoothMasMessageRsp rsp) {
        Cursor cr = null;
        Uri uri = Uri.parse("content://mms/");
        String whereClause = " _id = " + msgID;
        cr = mContext.getContentResolver().query(uri, null, whereClause, null,
                null);
        if (cr == null) {
            rsp.rsp = ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE;
            return rsp;
        }
        if (cr.getCount() > 0) {
            cr.moveToFirst();
            String containingFolder = getMmsMapVirtualFolderName((getMmsContainingFolder(msgID)));
            BmessageConsts bmsg = new BmessageConsts();

            // Create a bMessage

            // TODO Get Current type
            bmsg.setType("MMS");

            bmsg.setBmsg_version("1.0");
            if (cr.getString(cr.getColumnIndex("read")).equalsIgnoreCase("1")) {
                bmsg.setStatus("READ");
            } else {
                bmsg.setStatus("UNREAD");
            }

            bmsg.setFolder(TELECOM + "/" + MSG + "/" + containingFolder);

            bmsg.setVcard_version("2.1");
            VcardContent vcard = getVcardContent(getMmsMsgAddress(msgID));
            String type = cr.getString(cr.getColumnIndex("msg_box"));
            // Inbox is type 1.
            if (type.equalsIgnoreCase("1")) {
                bmsg.setOriginatorVcard_name(vcard.name);
                bmsg.setOriginatorVcard_phone_number(vcard.tel);
                bmsg.setRecipientVcard_name(getOwnerName());
                bmsg.setRecipientVcard_phone_number(getOwnerNumber());
            } else {
                bmsg.setRecipientVcard_name(vcard.name);
                bmsg.setRecipientVcard_phone_number(vcard.tel);
                bmsg.setOriginatorVcard_name(getOwnerName());
                bmsg.setOriginatorVcard_phone_number(getOwnerNumber());

            }

            StringBuilder sb = new StringBuilder();
            Date date = new Date(Integer.valueOf(getMmsMsgDate(msgID)));
            sb.append("Date: ").append(date.toString()).append("\r\n");

            boolean MIME = true;
            boolean msgFormat = MIME;
            sb.append(bldMMSBody(bmsg, msgFormat, msgID));
            bmsg.setBody_msg(sb.toString());
            bmsg.setBody_length(sb.length() + 22);
            bmsg.setBody_encoding("8BIT");
            // Send a bMessage
            String str = MapUtils.toBmessageMMS(bmsg);
            if (V){
                Log.v(TAG, str);
                Log.v(TAG, "\n\n");
            }
            if (str != null && (str.length() > 0)) {
                final String FILENAME = "message" + getMasId();
                FileOutputStream bos = null;
                File file = new File(mContext.getFilesDir() + "/" + FILENAME);
                file.delete();

                try {
                    bos = mContext
                            .openFileOutput(FILENAME, Context.MODE_PRIVATE);

                    bos.write(str.getBytes());
                    bos.flush();
                    bos.close();
                } catch (FileNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                File fileR = new File(mContext.getFilesDir() + "/" + FILENAME);
                if (fileR.exists() == true) {
                    rsp.file = fileR;
                    rsp.fractionDeliver = 1;
                }
            }
        }
        cr.close();
        return rsp;
    }

    private boolean isOutgoingMMSMessage( int mmsMsgID) {
        if ( getMmsMsgBox(mmsMsgID) == 1 ) {
            return false;
        }
        return true;
    }

    /**
     * This method constructs an MMS message that is added to the message list
     * which is used to construct a message listing
     */
    private MsgListingConsts bldMmsMsgLstItem(int mmsMsgID,
            BluetoothMasAppParams appParams, String folderName, String datetimeStr) {

        MsgListingConsts ml = new MsgListingConsts();

        // Set the message handle
        ml.setMsg_handle((mmsMsgID + MMS_HDLR_CONSTANT));

        // Set the message subject
        if ((appParams.ParameterMask & BIT_SUBJECT) != 0) {
            ml.setSubject(getMmsMsgSubject(mmsMsgID));
            ml.sendSubject = true;
        }

        // Construct datetime value
        if ((appParams.ParameterMask & BIT_DATETIME) != 0) {
            ml.setDatetime(datetimeStr);
        }

        // Construct msg body
        if ((appParams.ParameterMask & BIT_TEXT) != 0) {
            if ((getMmsMsgTxt(mmsMsgID) != null)) {
                ml.setContains_text("yes");
            } else {
                ml.setContains_text("no");
            }

        }

        // Set text size
        if ((appParams.ParameterMask & BIT_SIZE) != 0) {
            final String mmsMsgTxt = getMmsMsgTxt(mmsMsgID);
            ml.setSize(mmsMsgTxt == null ? 0 : mmsMsgTxt.length());
        }

        // Set message type
        if ((appParams.ParameterMask & BIT_TYPE) != 0) {
            ml.setType("MMS");
        }

        if ((appParams.ParameterMask & BIT_RECIPIENT_NAME) != 0) {
            // TODO "recipient_name" is the name of the
            // recipient of the message, when it is known
            // by the MSE device.
            String recipientName = null;
            if (isOutgoingMMSMessage(mmsMsgID) == false) {
                recipientName = getOwnerName();
            } else {
                recipientName = getContactName(getMmsMsgAddress(mmsMsgID));
            }
            ml.setRecepient_name(recipientName);
        }

        if ((appParams.ParameterMask & BIT_RECIPIENT_ADDRESSING) != 0) {
            // TODO In case of a SMS this is the recipient's
            // phone
            // number in canonical form (chapter 2.4.1 of [5])
            String recipientAddressing = null;
            if (isOutgoingMMSMessage(mmsMsgID) == false) {
                recipientAddressing = getOwnerNumber();
            } else {
                recipientAddressing = getMmsMsgAddress(mmsMsgID);
            }
            ml.setRecepient_addressing(recipientAddressing);
            ml.setSendRecipient_addressing(true);
        }

        if ((appParams.ParameterMask & BIT_SENDER_NAME) != 0) {
            String senderName = null;
            if (isOutgoingMMSMessage(mmsMsgID) == true) {
                senderName = getOwnerName();
            } else {
                senderName = getContactName(getMmsMsgAddress(mmsMsgID));
            }
            ml.setSender_name(senderName);
        }

        if ((appParams.ParameterMask & BIT_SENDER_ADDRESSING) != 0) {
            String senderAddressing = null;
            if (isOutgoingMMSMessage(mmsMsgID) == true) {
                senderAddressing = getOwnerNumber();
            } else {
                senderAddressing = getMmsMsgAddress(mmsMsgID);
            }
            ml.setSender_addressing(senderAddressing);
        }

        // Set read status
        if ((appParams.ParameterMask & BIT_READ) != 0) {
            final String mmsMsgStatus = getMmsMsgReadStatus(mmsMsgID);
            if (mmsMsgStatus != null && mmsMsgStatus.equalsIgnoreCase("yes")) {
                ml.setRead("yes");
            } else {
                ml.setRead("no");
            }
        }

        // Set priority
        if ((appParams.ParameterMask & BIT_PRIORITY) != 0) {
            ml.setPriority(getMmsMsgPriority(mmsMsgID));
        }

        // Set Protected
        if ((appParams.ParameterMask & BIT_PROTECTED) != 0) {
            ml.setMsg_protected(getMmsMsgProtected(mmsMsgID));
        }

        // Set sent
        if ((appParams.ParameterMask & BIT_SENT) != 0) {
            ml.setSent(getMmsMsgReadSent(mmsMsgID));
        }

        // Set reception status
        if ((appParams.ParameterMask & BIT_RECEPTION_STATUS) != 0) {
            ml.setReception_status("complete");
        }

        // Set attachment size
        if ((appParams.ParameterMask & BIT_SENT) != 0) {
            ml.setAttachment_size(getMmsMsgAttachSize(mmsMsgID));
        }

        return ml;
    }

    /**
     * Get the type (as in Mms ContentProvider) for the given table name
     */
    private int getMMSFolderType(String folder) {
        int type = 0;
        if (folder.equalsIgnoreCase(INBOX)) {
            type = 1;
        } else if (folder.equalsIgnoreCase(SENT)) {
            type = 2;
        } else if (folder.equalsIgnoreCase(DRAFTS)) {
            type = 3;
        } else if (folder.equalsIgnoreCase(OUTBOX)) {
            type = 4;
        } else if (folder.equalsIgnoreCase(FAILED)) {
            type = 5;
        } else if (folder.equalsIgnoreCase(QUEUED)) {
            type = 6;
        }
        // Deleted will be folder 0
        return type;
    }

    /**
     * This method is used to take an MMS in the drafts folder and move it to
     * the outbox This action is required to add the MMS to the pending_msgs
     * table which is used to send the MMS out to the network
     */
    private void moveMMSfromDraftstoOutbox() {

        String handle = null;

        // scan drafts folder for an MMS to send
        // fetch the message handle
        Uri uri = Uri.parse("content://mms/drafts");
        ContentResolver cr = mContext.getContentResolver();
        Cursor crID = cr.query(uri, null, null, null, null);
        if (crID != null) {
            if (crID.getCount() > 0) {
                crID.moveToFirst();
                int msgIDInd = crID.getColumnIndex("_id");
                handle = crID.getString(msgIDInd);
            }
            crID.close();
        }

        if (handle != null) {
            String whereClause = " _id= " + handle;
            uri = Uri.parse("content://mms");
            crID = cr.query(uri, null, whereClause, null, null);
            if (crID != null) {
                if (crID.getCount() > 0) {
                    crID.moveToFirst();
                    ContentValues values = new ContentValues();
                    values.put("msg_box", 4);
                    cr.update(uri, values, whereClause, null);
                }
                crID.close();
            }
        }
    }

    /**
     * This method is used to take a Bmessage that was pushed and move it to the
     * folder
     */
    private String moveMMStoDrafts(String mmsMsg) throws BadRequestException {
        String folder = "drafts";
        BmessageConsts bMsg = MapUtils.fromBmessageMMS(mmsMsg);
        String Address = bMsg.getRecipientVcard_phone_number();
        String MmsText = bMsg.getBody_msg();

        /**
         * The PTS tester does not contain the same message format as CE4A This
         * code /* looks at the pushed message and checks for the message boundary. If
         * it does not /* find it then it then it assumes PTS tester format
         */
        MmsText = MapUtils.fetchBodyEmail(MmsText);
        ContentValues values = new ContentValues();
        values.put("msg_box", 3);
        values.put("thread_id", createMMSThread(Address));
        // function that creates a thread ID
        values.put("read", 0);
        values.put("seen", 0);
        values.put("sub_cs", 106);
        values.put("ct_t", "application/vnd.wap.multipart.related");
        values.put("exp", 604800);
        values.put("m_cls", "personal");
        values.put("m_type", 128);
        values.put("v", 18);
        values.put("pri", 129);
        values.put("rr", 129);
        values.put("tr_id", "T12dc2e87182");
        values.put("d_rpt", 129);
        values.put("locked", 0);

        Uri uri = Uri.parse("content://mms/" + folder);
        ContentResolver cr = mContext.getContentResolver();
        uri = cr.insert(uri, values);

        if (uri == null) {
            // unable to insert MMS
            return null;
        }
        String msgNum = uri.getLastPathSegment();
        int msgID = Integer.parseInt(msgNum);
        if (V){
            Log.v(TAG, " NEW URI " + uri.toString());
        }
        int virtualMsgId = (msgID + MMS_HDLR_CONSTANT);

        // Build the \mms\part portion
        values.clear();

        values.put("seq", -1);
        values.put("ct", "application/smil");
        values.put("cid", "<smil>");
        values.put("cl", "smil.xml");
        values.put(
                "text",
                "<smil><head><layout><root-layout width=\"320px\" height=\"480px\"/><region id=\"Text\" left=\"0\" top=\"320\" width=\"320px\" height=\"160px\" fit=\"meet\"/></layout></head><body><par dur=\"5000ms\"><text src=\"text_0.txt\" region=\"Text\"/></par></body></smil>");

        uri = Uri.parse("content://mms/" + msgID + "/part");
        uri = cr.insert(uri, values);
        if (V){
            Log.v(TAG, " NEW URI " + ((uri == null) ? "failed" : uri.toString()));
        }

        values.clear();
        values.put("seq", 0);
        values.put("ct", "text/plain");
        values.put("name", "null");
        values.put("chset", 106);
        values.put("cd", "null");
        values.put("fn", "null");
        values.put("cid", "<smil>");
        values.put("cl", "text_0.txt");
        values.put("ctt_s", "null");
        values.put("ctt_t", "null");
        values.put("_data", "null");
        values.put("text", MmsText);

        uri = Uri.parse("content://mms/" + msgID + "/part");
        uri = cr.insert(uri, values);
        if (V){
            Log.v(TAG, " NEW URI " + ((uri == null) ? "failed" : uri.toString()));
        }

        values.clear();
        values.put("contact_id", "null");
        values.put("address", "insert-address-token");
        values.put("type", 137);
        values.put("charset", 106);

        uri = Uri.parse("content://mms/" + msgID + "/addr");
        uri = cr.insert(uri, values);
        if (V){
            Log.v(TAG, " NEW URI " + ((uri == null) ? "failed" : uri.toString()));
        }

        values.clear();
        values.put("contact_id", "null");
        values.put("address", Address);
        values.put("type", 151);
        values.put("charset", 106);

        uri = Uri.parse("content://mms/" + msgID + "/addr");
        uri = cr.insert(uri, values);
        if (V){
            Log.v(TAG, " NEW URI " + ((uri == null) ? "failed" : uri.toString()));
        }
        String whereClause = "address LIKE '" + Address + "' AND type = 125";
        mContext.getContentResolver().delete(Uri.parse("content://sms/"), whereClause, null);
        String virtualMsgIdStr = String.valueOf(virtualMsgId);
        return virtualMsgIdStr;

    }

    /**
     * This method is used to take a Bmessage that was pushed and move it to the
     * folder
     */
    private String addToMmsFolder(String folderName, String mmsMsg) throws BadRequestException {
        if (folderName == null) {
            return null;
        }
        if (folderName.equalsIgnoreCase(DRAFT)) {
            folderName = DRAFTS;
        }
        int folderType = SmsMmsUtils.getFolderTypeMms(folderName);
        BmessageConsts bMsg = MapUtils.fromBmessageMMS(mmsMsg);
        String Address = bMsg.getRecipientVcard_phone_number();
        String MmsText = bMsg.getBody_msg();

        /**
         * The PTS tester does not contain the same message format as CE4A This
         * code /* looks at the pushed message and checks for the message boundary. If
         * it does not /* find it then it then it assumes PTS tester format
         */
        
        MmsText = MapUtils.fetchBodyEmail(MmsText);
        ContentValues values = new ContentValues();
        values.put("msg_box", folderType);

        if (folderName.equalsIgnoreCase("deleted")) {
            values.put("thread_id", -1);
        } else {
            values.put("thread_id", createMMSThread(Address));
        }

        // function that creates a thread ID
        values.put("read", 0);
        values.put("seen", 0);
        values.put("sub_cs", 106);
        values.put("ct_t", "application/vnd.wap.multipart.related");
        values.put("exp", 604800);
        values.put("m_cls", "personal");
        values.put("m_type", 128);
        values.put("v", 18);
        values.put("pri", 129);
        values.put("rr", 129);
        values.put("tr_id", "T12dc2e87182");
        values.put("d_rpt", 129);
        values.put("locked", 0);

        Uri uri;
        if (folderName.equalsIgnoreCase("deleted")) {
            uri = Uri.parse("content://mms/inbox");
        } else {
            uri = Uri.parse("content://mms/" + folderName);
        }
        ContentResolver cr = mContext.getContentResolver();
        uri = cr.insert(uri, values);

        if (uri == null) {
            // unable to insert MMS
            Log.e(TAG, "Unabled to insert MMS " + values);
            return INTERNAL_ERROR;
        }
        String msgNum = uri.getLastPathSegment();
        int msgID = Integer.parseInt(msgNum);
        if (V){
            Log.v(TAG, " NEW URI " + uri.toString());
        }
        int virtualMsgId = (msgID + MMS_HDLR_CONSTANT);

        // Build the \mms\part portion
        values.clear();

        values.put("seq", -1);
        values.put("ct", "application/smil");
        values.put("cid", "<smil>");
        values.put("cl", "smil.xml");
        values.put(
                "text",
                "<smil><head><layout><root-layout width=\"320px\" height=\"480px\"/><region id=\"Text\" left=\"0\" top=\"320\" width=\"320px\" height=\"160px\" fit=\"meet\"/></layout></head><body><par dur=\"5000ms\"><text src=\"text_0.txt\" region=\"Text\"/></par></body></smil>");

        uri = Uri.parse("content://mms/" + msgID + "/part");
        uri = cr.insert(uri, values);
        if (uri != null && V){
            Log.v(TAG, " NEW URI " + uri.toString());
        }

        values.clear();
        values.put("seq", 0);
        values.put("ct", "text/plain");
        values.put("name", "null");
        values.put("chset", 106);
        values.put("cd", "null");
        values.put("fn", "null");
        values.put("cid", "<smil>");
        values.put("cl", "text_0.txt");
        values.put("ctt_s", "null");
        values.put("ctt_t", "null");
        values.put("_data", "null");
        values.put("text", MmsText);

        uri = Uri.parse("content://mms/" + msgID + "/part");
        uri = cr.insert(uri, values);
        if (uri != null && V){
            Log.v(TAG, " NEW URI " + uri.toString());
        }

        values.clear();
        values.put("contact_id", "null");
        values.put("address", "insert-address-token");
        values.put("type", 137);
        values.put("charset", 106);

        uri = Uri.parse("content://mms/" + msgID + "/addr");
        uri = cr.insert(uri, values);
        if (uri != null && V){
            Log.v(TAG, " NEW URI " + uri.toString());
        }

        values.clear();
        values.put("contact_id", "null");
        values.put("address", Address);
        values.put("type", 151);
        values.put("charset", 106);

        uri = Uri.parse("content://mms/" + msgID + "/addr");
        uri = cr.insert(uri, values);
        if (uri != null && V){
            Log.v(TAG, " NEW URI " + uri.toString());
        }

        String virtualMsgIdStr = String.valueOf(virtualMsgId);
        String whereClause = "address LIKE '" + Address + "' AND type = 125";
        if (!folderName.equalsIgnoreCase("deleted")) {
            mContext.getContentResolver().delete(Uri.parse("content://sms/"), whereClause, null);
        }
        return virtualMsgIdStr;
    }

    /**
     * Method to construct body of bmessage using either MIME or no MIME
     *
     */
    private String bldMMSBody(BmessageConsts bMsg, boolean msgType, int msgID) {
        boolean MIME = true;
        StringBuilder sb = new StringBuilder();

        if (msgType == MIME) {
            Random randomGenerator = new Random();
            int randomInt = randomGenerator.nextInt(1000);
            String boundary = "MessageBoundary."+randomInt;
            final String mmsMsgTxt = getMmsMsgTxt(msgID);
            if(mmsMsgTxt != null){
                while(mmsMsgTxt.contains(boundary)){
                    randomInt = randomGenerator.nextInt(1000);
                    boundary = "MessageBoundary."+randomInt;
                }
            }
            sb.append("To:").append(bMsg.recipient_vcard_phone_number)
                    .append("\r\n");
            sb.append("Mime-Version: 1.0").append("\r\n");
            sb.append(
                    "Content-Type: multipart/mixed; boundary=\""+boundary+"\"")
                    .append("\r\n");
            sb.append("Content-Transfer-Encoding: 7bit").append("\r\n")
                    .append("\r\n");
            sb.append("MIME Message").append("\r\n");
            sb.append("--"+boundary).append("\r\n");
            sb.append("Content-Type: text/plain").append("\r\n");
            sb.append("Content-Transfer-Encoding: 8bit").append("\r\n");
            sb.append("Content-Disposition:inline").append("\r\n")
                    .append("\r\n");
            sb.append(getMmsMsgTxt(msgID)).append("\r\n");
            sb.append("--"+boundary+"--").append("\r\n")
                    .append("\r\n");
        } else {
            sb.append("Subject:").append("Not Implemented").append("\r\n");
            sb.append("From:").append(bMsg.originator_vcard_phone_number)
                    .append("\r\n");
            sb.append(getMmsMsgTxt(msgID)).append("\r\n").append("\r\n");
        }
        return sb.toString();
    }

    /**
     * Method to create a thread for a pushed MMS message
     *
     */
    private int createMMSThread(String address) {
        int returnValue = 0;
        if (address != null) {
            if (V){
                Log.v(TAG, "Inside adress not null");
            }
            ContentValues tempValue = new ContentValues();
            tempValue.put("address", address);
            tempValue.put("type", 125);
            Uri tempUri = mContext.getContentResolver().insert(
                    Uri.parse("content://sms/"), tempValue);

            if (tempUri != null) {
                Cursor tempCr = mContext.getContentResolver().query(tempUri, null,
                        null, null, null);
                if (tempCr != null) {
                    tempCr.moveToFirst();
                    String newThreadIdStr = tempCr.getString(tempCr
                            .getColumnIndex("thread_id"));
                    tempCr.close();
                    returnValue = Integer.valueOf(newThreadIdStr);
                }
                if (V){
                    Log.v(TAG, "Thread ID::"+returnValue);
                }
            }
        }

        return returnValue;
    }

    private String bldSmsBmsg(String msgHandle, Context context, Cursor cr,
                BluetoothMasAppParams bluetoothMasAppParams) {
        String str = null;
        if (cr.getCount() > 0) {
            cr.moveToFirst();
            String containingFolder = getContainingFolder(msgHandle);
            BmessageConsts bmsg = new BmessageConsts();

            // Create a bMessage

            if (TelephonyManager.getDefault().getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
                bmsg.setType("SMS_CDMA");
            } else {
                bmsg.setType("SMS_GSM");
            }

            bmsg.setBmsg_version("1.0");
            if (cr.getString(cr.getColumnIndex("read")).equalsIgnoreCase("1")) {
                bmsg.setStatus("READ");
            } else {
                bmsg.setStatus("UNREAD");
            }

            bmsg.setFolder(TELECOM + "/" + MSG + "/" + containingFolder);

            bmsg.setVcard_version("2.1");
            VcardContent vcard = getVcardContent(cr.getString(cr
                    .getColumnIndex("address")));

            String type = cr.getString(cr.getColumnIndex("type"));
            if (type.equalsIgnoreCase("1")) {
                // The address in database is of originator
                bmsg.setOriginatorVcard_name(vcard.name);
                bmsg.setOriginatorVcard_phone_number(vcard.tel);
                bmsg.setRecipientVcard_name(getOwnerName());
                bmsg.setRecipientVcard_phone_number(getOwnerNumber());
            } else {
                bmsg.setRecipientVcard_name(vcard.name);
                bmsg.setRecipientVcard_phone_number(vcard.tel);
                bmsg.setOriginatorVcard_name(getOwnerName());
                bmsg.setOriginatorVcard_phone_number(getOwnerNumber());
            }

            String smsBody = " ";

            if ( (int)bluetoothMasAppParams.Charset == 1){
                bmsg.setBody_charset("UTF-8");
                smsBody = cr.getString(cr.getColumnIndex("body"));
            }

            if ( (int)bluetoothMasAppParams.Charset == 0){
                bmsg.setBody_encoding("G-7BIT");
                String smsBodyUnicode = cr.getString(cr.getColumnIndex("body"));
                smsBody = getSMSDeliverPdu(smsBodyUnicode, cr.getString(cr.getColumnIndex("date")), vcard.tel);
            }

            bmsg.setBody_length(22 + smsBody.length());

            bmsg.setBody_msg(smsBody);
            cr.close();

            // Send a bMessage
            if (V){
                Log.v(TAG, "bMessageSMS test\n");
                Log.v(TAG, "=======================\n\n");
            }
            str = MapUtils.toBmessageSMS(bmsg);
        }
        return str;
    }

    private MsgListingConsts bldSmsMsgLstItem(BluetoothMasAppParams appParams,
                String subject, String timestamp, String address, String msgId,
                String readStatus, int msgType){
        MsgListingConsts ml = new MsgListingConsts();
        ml.setMsg_handle(Integer.valueOf(msgId));

        Time time = new Time();
        time.set(Long.valueOf(timestamp));

        String datetimeStr = time.toString().substring(0, 15);

        ml.msgInfo.setDateTime(datetimeStr);

        if ((appParams.ParameterMask & BIT_SUBJECT) != 0) {
            /* SMS doesn't have subject. Append Body
             * so that remote client doesn't have to do
             * GetMessage
             */
            ml.setSendSubject(true);
            if (subject == null) {
                subject = "";
            } else if (subject != null && subject.length() > appParams.SubjectLength ) {
                subject = subject.substring(0,
                        appParams.SubjectLength);
            }
            ml.setSubject(subject);
        }

        if ((appParams.ParameterMask & BIT_DATETIME) != 0) {
            ml.setDatetime(datetimeStr);
        }

        if ((appParams.ParameterMask & BIT_SENDER_NAME) != 0) {
            // TODO Query the Contacts database
            String senderName = null;
            if (isOutgoingSMSMessage(msgType) == true) {
                senderName = getOwnerName();
            } else {
                senderName = getContactName(address);
            }
            ml.setSender_name(senderName);
        }

        if ((appParams.ParameterMask & BIT_SENDER_ADDRESSING) != 0) {
            // TODO In case of a SMS this is
            // the sender's phone number in canonical form
            // (chapter
            // 2.4.1 of [5]).
            String senderAddressing = null;
            if (isOutgoingSMSMessage(msgType) == true) {
                senderAddressing = getOwnerNumber();
            } else {
                senderAddressing = address;
            }
            ml.setSender_addressing(senderAddressing);
        }

        if ((appParams.ParameterMask & BIT_RECIPIENT_NAME) != 0) {
            // TODO "recipient_name" is the name of the
            // recipient of
            // the message, when it is known
            // by the MSE device.
            String recipientName = null;
            if (isOutgoingSMSMessage(msgType) == false) {
                recipientName = getOwnerName();
            } else {
                recipientName = getContactName(address);
            }
            ml.setRecepient_name(recipientName);
        }

        if ((appParams.ParameterMask & BIT_RECIPIENT_ADDRESSING) != 0) {
            // TODO In case of a SMS this is the recipient's
            // phone
            // number in canonical form (chapter 2.4.1 of [5])
            String recipientAddressing = null;
            if (isOutgoingSMSMessage(msgType) == false) {
                recipientAddressing = getOwnerNumber();
            } else {
                recipientAddressing = address;
            }
            ml.setRecepient_addressing(recipientAddressing);
            ml.setSendRecipient_addressing(true);
        }

        if ((appParams.ParameterMask & BIT_TYPE) != 0) {
            final int phoneType = TelephonyManager.getDefault().getPhoneType();
            if (phoneType == TelephonyManager.PHONE_TYPE_CDMA) {
                ml.setType("SMS_CDMA");
            } else {
                ml.setType("SMS_GSM");
            }
        }

        if ((appParams.ParameterMask & BIT_SIZE) != 0) {
            ml.setSize(subject.length());
        }

        if ((appParams.ParameterMask & BIT_RECEPTION_STATUS) != 0) {
            ml.setReception_status("complete");
        }

        if ((appParams.ParameterMask & BIT_TEXT) != 0) {
            // TODO Set text to "yes"
            ml.setContains_text("yes");
        }

        if ((appParams.ParameterMask & BIT_ATTACHMENT_SIZE) != 0) {
            ml.setAttachment_size(0);
        }

        if ((appParams.ParameterMask & BIT_PRIORITY) != 0) {
            // TODO Get correct priority
            ml.setPriority("no");
        }

        if ((appParams.ParameterMask & BIT_READ) != 0) {
            if (readStatus.equalsIgnoreCase("1")) {
                ml.setRead("yes");
            } else {
                ml.setRead("no");
            }
        }

        if ((appParams.ParameterMask & BIT_SENT) != 0) {
            // TODO Get sent status?
            if (msgType == 2) {
                ml.setSent("yes");
            } else {
                ml.setSent("no");
            }
        }

        if ((appParams.ParameterMask & BIT_PROTECTED) != 0) {
            ml.setMsg_protected("no");
        }

        return ml;
    }
    private BluetoothMsgListRsp msgListSms(List<MsgListingConsts> msgList,
                String folder, BluetoothMasMessageListingRsp rsp, BluetoothMasAppParams appParams){
        BluetoothMsgListRsp bmlr = new BluetoothMsgListRsp();
        String url = "content://sms/";
        Uri uri = Uri.parse(url);
        ContentResolver cr = mContext.getContentResolver();
        String whereClause  = SmsMmsUtils.getConditionStringSms(folder, appParams);

        Cursor cursor = cr.query(uri, null, whereClause, null,
                "date desc");

        if (cursor != null && V){
                Log.v(TAG, "move to First" + cursor.moveToFirst());
        }
        if (cursor != null && V){
                Log.v(TAG, "move to Liststartoffset"
                    + cursor.moveToPosition(appParams.ListStartOffset));
        }
        if (cursor != null && cursor.moveToFirst()) {
            int idInd = cursor.getColumnIndex("_id");
            int addressInd = cursor.getColumnIndex("address");
            int personInd = cursor.getColumnIndex("person");
            int dateInd = cursor.getColumnIndex("date");
            int readInd = cursor.getColumnIndex("read");
            int statusInd = cursor.getColumnIndex("status");
            int subjectInd = cursor.getColumnIndex("subject");
            int typeInd = cursor.getColumnIndex("type");
            int bodyInd = cursor.getColumnIndex("body");

            do {
                /*
                 * Apply remaining filters
                 */

                /*
                 * For incoming message, originator is the remote
                 * contact For outgoing message, originator is the
                 * owner.
                 */
                String filterString = null;
                String oname = getOwnerName();
                if (oname == null) {
                    oname = "";
                }
                String onumber = getOwnerNumber();
                if (onumber == null) {
                    onumber = "";
                }

                int msgType = cursor.getInt(typeInd);
                String regExpOrig = null;
                String regExpRecipient = null;
                if (appParams.FilterOriginator != null) {
                    regExpOrig = appParams.FilterOriginator.replace("*", ".*[0-9A-Za-z].*");
                }
                if (appParams.FilterRecipient != null) {
                    regExpRecipient = appParams.FilterRecipient.replace("*", ".*[0-9A-Za-z].*");
                }
                if (isOutgoingSMSMessage(msgType) == true) {
                    if ((appParams.FilterOriginator != null)
                            && (appParams.FilterOriginator.length() != 0)
                            && !(oname.matches(".*"+regExpOrig+".*"))
                            && !(onumber.matches(".*"+regExpOrig+".*"))) {
                        continue;
                    }
                    if ((appParams.FilterRecipient != null)
                            && (appParams.FilterRecipient.length() != 0)) {
                        filterString = appParams.FilterRecipient.trim();
                        if (V){
                                Log.v(TAG, "appParams.FilterRecipient"
                                    + appParams.FilterRecipient);
                        }
                    }
                }
                if (isOutgoingSMSMessage(msgType) == false) {
                    if ((appParams.FilterRecipient != null)
                            && (appParams.FilterRecipient.length() != 0)
                            && !(oname.matches(".*"+regExpRecipient+".*"))
                            && !(onumber.matches(".*"+regExpRecipient+".*"))) {
                        continue;
                    }
                    if ((appParams.FilterOriginator != null)
                            && (appParams.FilterOriginator.length() != 0)) {
                        filterString = appParams.FilterOriginator.trim();
                        if (V){
                                Log.v(TAG, "appParams.FilterOriginator"
                                    + appParams.FilterOriginator);
                        }
                    }
                }

                if (filterString != null) {
                    if (V){
                        Log.v(TAG, "filterString = " + filterString);
                    }
                    if (allowEntry(cursor.getString(addressInd),
                            filterString) == true) {
                        if (V){
                                Log.v(TAG,
                                    "+++ ALLOWED +++++++++ "
                                    + cursor.getString(addressInd)
                                    + " - " + cursor.getPosition());
                        }
                    } else {
                        if (V){
                                Log.v(TAG,
                                    "+++ DENIED +++++++++ "
                                    + cursor.getString(addressInd)
                                    + " - " + cursor.getPosition());
                        }
                        continue;
                    }
                }
                if (V) Log.v(TAG, " msgListSize " + rsp.msgListingSize);
                rsp.msgListingSize++;

                /*
                 * Don't want the listing; just send the listing size
                 * after applying all the filters.
                 */
                if (appParams.MaxListCount == 0) {
                    continue;
                }
                String msgIdSms = cursor.getString(idInd);
                String subjectSms = cursor.getString(bodyInd);
                String timestampSms = cursor.getString(dateInd);
                String addressSms = cursor.getString(addressInd);
                String readStatusSms = cursor.getString(readInd);

                MsgListingConsts ml = bldSmsMsgLstItem(appParams, subjectSms,
                                timestampSms, addressSms, msgIdSms,
                                readStatusSms, msgType);

                // New Message?
                if ((rsp.newMessage == 0)
                        && (cursor.getInt(readInd) == 0)) {
                    rsp.newMessage = 1;
                }

                msgList.add(ml);
            } while (cursor.moveToNext());
        }
        if (cursor != null) {
            cursor.close();
        }
        rsp.rsp = ResponseCodes.OBEX_HTTP_OK;
        bmlr.messageListingSize = rsp.msgListingSize;
        bmlr.rsp = rsp;
        bmlr.msgList = msgList;
        return bmlr;
    }

    private BluetoothMsgListRsp msgListMms(List<MsgListingConsts> msgList,
                String name, BluetoothMasMessageListingRsp rsp, BluetoothMasAppParams appParams){
        BluetoothMsgListRsp bmlr = new BluetoothMsgListRsp();

        if (getNumMmsMsgs(name) != 0) {
            List<Integer> list = getMmsMsgMIDs(bldMmsWhereClause(
                    appParams, getMMSFolderType(name)));
            for (int msgId : list) {
                if (V){
                        Log.v(TAG, "\n MMS Text message ==> "
                            + getMmsMsgTxt(msgId));
                }
                if (V){
                        Log.v(TAG, "\n MMS message subject ==> "
                            + getMmsMsgSubject(msgId));
                }

                String datetime = getMmsMsgDate(msgId);
                Time time = new Time();
                Date dt = new Date(Long.valueOf(datetime));
                time.set((dt.getTime() * 1000));

                String datetimeStr = time.toString().substring(0, 15);

                MsgListingConsts mmsl = bldMmsMsgLstItem(msgId, appParams, name, datetimeStr);
                mmsl.msgInfo.setDateTime(datetimeStr);

                if ((rsp.newMessage == 0)
                        && "no".equalsIgnoreCase(getMmsMsgReadStatus(msgId))) {
                    rsp.newMessage = 1;
                }

                msgList.add(mmsl);
                rsp.msgListingSize++;
            }
        }
        rsp.rsp = ResponseCodes.OBEX_HTTP_OK;
        bmlr.messageListingSize = rsp.msgListingSize;
        bmlr.rsp = rsp;
        bmlr.msgList = msgList;
        return bmlr;
    }

    private BluetoothMsgListRsp msgListEmail(List<MsgListingConsts> msgList,
            String tempPath, BluetoothMasMessageListingRsp rsp, BluetoothMasAppParams appParams){
        BluetoothMsgListRsp bmlr = new BluetoothMsgListRsp();
        String folderName;

        String splitStringsEmail[] = tempPath.split("/");
        if (splitStringsEmail.length < 3) {
            Log.e(TAG, "splitStringsEmail[].length is " + splitStringsEmail.length);
            rsp.rsp = ResponseCodes.OBEX_HTTP_BAD_REQUEST;;
            bmlr.rsp = rsp;
            return bmlr;
        }
        if (V){
                Log.v(TAG, "splitStringsEmail[2] = " + splitStringsEmail[2]);
        }
        // TODO: Take care of subfolders

        folderName = EmailUtils.getFolderName(splitStringsEmail);
        if (folderName == null) {
            Log.e(TAG, "folderName is null");
            rsp.rsp = ResponseCodes.OBEX_HTTP_BAD_REQUEST;;
            bmlr.rsp = rsp;
            return bmlr;
        }

        if (DRAFT.equalsIgnoreCase(folderName)) {
            long accountId = EmailUtils.getDefaultEmailAccountId(mContext);
            List<String> draftFolders = EmailUtils.getFoldersForType(mContext, accountId
                    , EmailUtils.TYPE_DRAFT);
            List<MsgListingConsts> list = null;
            for (String draft : draftFolders) {
                list = getListEmailFromFolder(draft, rsp, appParams);
                if (list.size() > 0) {
                    msgList.addAll(list);
                }
            }
        } else {
            msgList = getListEmailFromFolder(folderName, rsp, appParams);
        }

        rsp.rsp = ResponseCodes.OBEX_HTTP_OK;
        bmlr.messageListingSize = rsp.msgListingSize;
        bmlr.rsp = rsp;
        bmlr.msgList = msgList;
        return bmlr;
    }

    private List<MsgListingConsts> getListEmailFromFolder(String folderName,
            BluetoothMasMessageListingRsp rsp, BluetoothMasAppParams appParams) {
        List<MsgListingConsts> msgList = new ArrayList<MsgListingConsts>();
        String urlEmail = "content://com.android.email.provider/message";
        Uri uriEmail = Uri.parse(urlEmail);
        ContentResolver crEmail = mContext.getContentResolver();

        String whereClauseEmail  = EmailUtils.getConditionString(folderName, mContext, appParams);

        if (V){
                Log.v(TAG, "## whereClauseEmail ##:" + whereClauseEmail);
        }
        Cursor cursor = crEmail.query(uriEmail, null, whereClauseEmail, null, "timeStamp desc");

        if (cursor != null && V){
                Log.v(TAG, "move to First" + cursor.moveToFirst());
        }
        if (cursor != null && V){
                Log.v(TAG, "move to Liststartoffset"
                    + cursor.moveToPosition(appParams.ListStartOffset));
        }
        if (cursor != null && cursor.moveToFirst()) {
            int idInd = cursor.getColumnIndex("_id");
            int fromIndex = cursor.getColumnIndex("fromList");
            int toIndex = cursor.getColumnIndex("toList");
            int dateInd = cursor.getColumnIndex("timeStamp");
            int readInd = cursor.getColumnIndex("flagRead");
            int subjectInd = cursor.getColumnIndex("subject");
            int replyToInd = cursor.getColumnIndex("replyToList");

            do {
                /*
                 * Apply remaining filters
                 */

                if (V) Log.v(TAG, " msgListSize " + rsp.msgListingSize);
                rsp.msgListingSize++;

                String subject = cursor.getString(subjectInd);
                String timestamp = cursor.getString(dateInd);
                String senderName = cursor.getString(fromIndex);
                String senderAddressing = cursor.getString(fromIndex);
                String recipientName = cursor.getString(toIndex);
                String recipientAddressing = cursor.getString(toIndex);
                String msgId = cursor.getString(idInd);
                String readStatus = cursor.getString(readInd);
                String replyToStr = cursor.getString(replyToInd);

                /*
                 * Don't want the listing; just send the listing size after
                 * applying all the filters.
                 */

                /*
                 * TODO Skip the first ListStartOffset record(s). Don't write
                 * more than MaxListCount record(s).
                 */

                MsgListingConsts emailMsg = new MsgListingConsts();
                emailMsg = EmailUtils.bldEmailMsgLstItem(mContext, folderName, appParams,
                        subject, timestamp, senderName, senderAddressing,
                        recipientName, recipientAddressing,
                        msgId, readStatus, replyToStr);

                // New Message?
                if ((rsp.newMessage == 0) && (cursor.getInt(readInd) == 0)) {
                    rsp.newMessage = 1;
                }
                msgList.add(emailMsg);
            } while (cursor.moveToNext());
        }
        if (cursor != null) {
            cursor.close();
        }
        return msgList;
    }

    private BluetoothMasMessageRsp getMessageSms(String msgHandle, Context context,
                BluetoothMasMessageRsp rsp, BluetoothMasAppParams bluetoothMasAppParams){
        Cursor cr = null;
        Uri uri = Uri.parse("content://sms/");
        String whereClause = " _id = " + msgHandle;
        try {
            cr = context.getContentResolver().query(uri, null, whereClause, null,
                    null);
        } catch (Exception e){
            rsp.rsp = ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            return rsp;
        }
        if (cr == null) {
            rsp.rsp = ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            return rsp;
        }
        String strSms = bldSmsBmsg(msgHandle, context, cr, bluetoothMasAppParams);
        cr.close();
        if(strSms != null){
            if (V){
                Log.v(TAG, strSms);
            }
        }
        if (V){
                Log.v(TAG, "\n\n");
        }

        if (strSms != null && (strSms.length() > 0)) {
            final String FILENAME = "message" + getMasId();
            // BufferedOutputStream bos = null;
            FileOutputStream bos = null;
            File file = new File(context.getFilesDir() + "/" + FILENAME);
            file.delete();

            try {
                bos = context
                        .openFileOutput(FILENAME, Context.MODE_PRIVATE);
                bos.write(strSms.getBytes());
                bos.flush();
                bos.close();
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            File fileR = new File(context.getFilesDir() + "/" + FILENAME);
            if (fileR.exists() == true) {
                rsp.file = fileR;
                rsp.fractionDeliver = 1;
            }

        }
        return rsp;
    }
    private BluetoothMasMessageRsp getMessageMms(String msgHandle,
                BluetoothMasMessageRsp rsp){
        int mmsMsgID = 0;
        try {
            mmsMsgID = getMmsMsgHndToID(Integer.valueOf(msgHandle));
        } catch (Exception e) {
            rsp.rsp = ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            return rsp;
        }
        if (mmsMsgID > 0) {
            rsp = bldMmsBmsg(mmsMsgID, rsp);
        }
        return rsp;
    }

    private BluetoothMasMessageRsp getMessageEmail(String msgHandle,
                BluetoothMasMessageRsp rsp){
        int emailMsgID = 0;
        emailMsgID = (Integer.valueOf(msgHandle) - EMAIL_HDLR_CONSTANT);
        String str = EmailUtils.bldEmailBmsg(emailMsgID, rsp, mContext);

        if (V){
                Log.v(TAG, str);
            Log.v(TAG, "\n\n");
        }
        if (str != null && (str.length() > 0)) {
            final String FILENAME = "message" + getMasId();
            FileOutputStream bos = null;
            File file = new File(mContext.getFilesDir() + "/" + FILENAME);
            file.delete();

            try {
                bos = mContext.openFileOutput(FILENAME, Context.MODE_PRIVATE);
                bos.write(str.getBytes());
                bos.flush();
                bos.close();
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            File fileR = new File(mContext.getFilesDir() + "/" + FILENAME);
            if (fileR.exists() == true) {
                rsp.file = fileR;
                rsp.fractionDeliver = 1;
            } else {
                rsp.rsp = ResponseCodes.OBEX_HTTP_BAD_REQUEST;
            }

        }
        return rsp;
    }

    private BluetoothMasPushMsgRsp pushMessageMms(BluetoothMasPushMsgRsp rsp,
                String tmpPath, String readStr, String name) throws BadRequestException {
        if (tmpPath.equalsIgnoreCase("telecom/msg/outbox")) {
            MmsHandle = moveMMStoDrafts(readStr);
            if (MmsHandle == null) {
                rsp.response = ResponseCodes.OBEX_HTTP_NOT_FOUND;
                return rsp;
            }
            moveMMSfromDraftstoOutbox();

            if (V){
                Log.v(TAG, "\nBroadcasting Intent to MmsSystemEventReceiver\n ");
            }
            Intent sendIntent = new Intent("android.intent.action.MMS_PUSH");
            mContext.sendBroadcast(sendIntent);
            rsp.msgHandle = MmsHandle;
            rsp.response = ResponseCodes.OBEX_HTTP_OK;
            return rsp;
        } else {
            String splitStrings[] = mCurrentPath.split("/");
            mnsClient.addMceInitiatedOperation("+");
            int tmp = splitStrings.length;
            String folderName;
            if (name != null) {
                if (name.length() == 0) {
                    folderName = splitStrings[tmp - 1];
                } else {
                    folderName = name;
                }
            } else {
                folderName = splitStrings[tmp - 1];
            }
            if (folderName != null && folderName.equalsIgnoreCase(DRAFTS)){
                MmsHandle = addToMmsFolder(folderName, readStr);
                if (INTERNAL_ERROR == MmsHandle) {  // == comparison valid here
                    rsp.msgHandle = null;
                    rsp.response = ResponseCodes.OBEX_HTTP_NOT_FOUND;
                    return rsp;
                }
                rsp.msgHandle = MmsHandle;
                rsp.response = ResponseCodes.OBEX_HTTP_OK;
                return rsp;
            } else {
                rsp.msgHandle = null;
                rsp.response = ResponseCodes.OBEX_HTTP_FORBIDDEN;
                return rsp;
            }
        }
    }

    private BluetoothMasPushMsgRsp pushMessageSms(BluetoothMasPushMsgRsp rsp,
                String tmpPath, String name, BluetoothMasAppParams bluetoothMasAppParams){
        if(!tmpPath.equalsIgnoreCase("telecom/msg/outbox")) {
            String splitStrings[] = mCurrentPath.split("/");
            mnsClient.addMceInitiatedOperation("+");
            int tmp = splitStrings.length;
            String folderName;
            if (name != null) {
                if (name.length() == 0){
                    folderName = splitStrings[tmp - 1];
                } else {
                    folderName = name;
                }
            } else {
                folderName = splitStrings[tmp - 1];
            }
            if (folderName != null && folderName.equalsIgnoreCase(DRAFT)) {
                SmsHandle = addToSmsFolder(folderName, PhoneAddress, SmsText);
                if (INTERNAL_ERROR == SmsHandle) {  // == comparison valid here
                    rsp.msgHandle = null;
                    rsp.response = ResponseCodes.OBEX_HTTP_NOT_FOUND;
                    return rsp;
                }
                rsp.msgHandle = SmsHandle;
                rsp.response = ResponseCodes.OBEX_HTTP_OK;
                return rsp;
            } else {
                rsp.msgHandle = null;
                rsp.response = ResponseCodes.OBEX_HTTP_FORBIDDEN;
                return rsp;
            }
        }

        SmsHandle = null;
        rsp.msgHandle = "";

        if (bluetoothMasAppParams.Transparent == 0) {
            mnsClient.addMceInitiatedOperation("+");
            SmsHandle = addToSmsFolder("queued", PhoneAddress, SmsText);
            rsp.msgHandle = SmsHandle;
            rsp.response = ResponseCodes.OBEX_HTTP_OK;
        } else if (bluetoothMasAppParams.Transparent == 1) {
            ArrayList<String> parts = new ArrayList<String>();
            SmsManager sms = SmsManager.getDefault();
            parts = sms.divideMessage(SmsText);

            mnsClient.addMceInitiatedOperation("+");
            sms.sendMultipartTextMessage(PhoneAddress, null, parts, null, null);
            rsp.msgHandle = "-1";
            rsp.response = ResponseCodes.OBEX_HTTP_OK;
            return rsp;
        }

        if (V) {
            Log.v(TAG, " Trying to send SMS ");
            Log.v(TAG, " Text " + SmsText + " address " + PhoneAddress);
        }

        try {
            Intent sendIntentSms = new Intent("com.android.mms.transaction.SEND_MESSAGE");
            sendIntentSms.putExtra(android.content.Intent.EXTRA_PHONE_NUMBER, PhoneAddress);
            sendIntentSms.putExtra(android.content.Intent.EXTRA_TEXT, SmsText);
            mContext.sendBroadcast(sendIntentSms);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rsp;
    }

    private boolean isAllowedEmailFolderForPush(String folderName) {
        if (DRAFT.equalsIgnoreCase(folderName) || OUTBOX.equalsIgnoreCase(folderName)) {
            return true;
        }
        long id = EmailUtils.getDefaultEmailAccountId(mContext);
        int type = EmailUtils.getTypeForFolder(mContext, id, folderName);
        if (type == EmailUtils.TYPE_DRAFT || type == EmailUtils.TYPE_OUTBOX) {
            return true;
        } else {
            return false;
        }
    }

    private BluetoothMasPushMsgRsp pushMessageEmail(BluetoothMasPushMsgRsp rsp,
                String readStr, String name) throws BadRequestException {
        if (V) Log.v(TAG, " Before fromBmessageemail method:: "+readStr);

        BmessageConsts bMsg = MapUtils.fromBmessageEmail(readStr);
        EmailAddress = bMsg.getRecipientVcard_email();
        EmailText = bMsg.getBody_msg();
        EmailSubject = bMsg.getSubject();
        EmailOriginator = bMsg.getOriginatorVcard_email();
        EmailOrigName = bMsg.getOriginatorVcard_name();

        String tmpPath = "";
        tmpPath = (name == null) ? mCurrentPath : mCurrentPath + "/" + name;

        String splitStrings[] = mCurrentPath.split("/"); // not tmpPath instead of mCurrentPath?
        mnsClient.addMceInitiatedOperation("+");
        int tmp = splitStrings.length;
        String folderName;
        if (name != null) {
            if (name.length() == 0) {
                folderName = splitStrings[tmp - 1];
            } else {
                folderName = name;
            }
        } else {
            folderName = splitStrings[tmp - 1];
        }
        if (!isAllowedEmailFolderForPush(folderName)) {
            rsp.msgHandle = null;
            rsp.response = ResponseCodes.OBEX_HTTP_FORBIDDEN;
            return rsp;
        }
        EmailHandle = addToEmailFolder(folderName, EmailAddress, EmailText, EmailSubject,
                EmailOriginator, EmailOrigName);
        if (INTERNAL_ERROR == EmailHandle) { // == comparison valid here
            rsp.msgHandle = null;
            rsp.response = ResponseCodes.OBEX_HTTP_NOT_FOUND;
            return rsp;
        }
        rsp.msgHandle = EmailHandle;
        rsp.response = ResponseCodes.OBEX_HTTP_OK;

        long accountId = EmailUtils.getDefaultEmailAccountId(mContext);
        if (V){
                Log.v(TAG, " Account id before Mail service:: "+accountId);
        }

        Intent emailIn = new Intent();
        emailIn.setAction("com.android.email.intent.action.MAIL_SERVICE_WAKEUP");
        emailIn.putExtra("com.android.email.intent.extra.ACCOUNT", accountId);
        mContext.startService(emailIn);
        return rsp;
    }

    private int setMsgStatusSms(String name,
                BluetoothMasAppParams bluetoothMasAppParams){
        Uri uri = Uri.parse("content://sms/" + name);
        Cursor cr = mContext.getContentResolver().query(uri, null, null, null,
                null);
        if (cr != null && cr.moveToFirst()) {
            if (bluetoothMasAppParams.StatusIndicator == 0) {
                /* Read Status */
                ContentValues values = new ContentValues();
                values.put("read", bluetoothMasAppParams.StatusValue);
                mContext.getContentResolver().update(uri, values, null, null);
            } else {
                if (bluetoothMasAppParams.StatusValue == 1) {
                    deleteSMS(name);
                } else if (bluetoothMasAppParams.StatusValue == 0) {
                    unDeleteSMS(name);
                }
            }
        }
        if (cr != null) {
            cr.close();
        }
        // Do we need to return ResponseCodes.OBEX_HTTP_BAD_REQUEST when cr == null?
        return ResponseCodes.OBEX_HTTP_OK;
    }

    private int setMsgStatusMms(String name,
                BluetoothMasAppParams bluetoothMasAppParams){
        String whereClause = " _id= " + getMmsMsgHndToID(Integer.valueOf(name));
        Uri uri = Uri.parse("content://mms/");
        if(getMmsMsgHndToID(Integer.valueOf(name)) > 0){
            Cursor cr = mContext.getContentResolver().query(uri, null, null, null, null);
            if (cr != null) {
                if (cr.getCount() > 0) {
                    cr.moveToFirst();
                    if (bluetoothMasAppParams.StatusIndicator == 0) {
                        /* Read Status */
                        ContentValues values = new ContentValues();
                        values.put("read", bluetoothMasAppParams.StatusValue);
                        int rowUpdate = mContext.getContentResolver().update(uri,
                                values, whereClause, null);
                        if (V){
                                Log.v(TAG, "\nRows updated => " + Integer.toString(rowUpdate));
                        }
                        return ResponseCodes.OBEX_HTTP_OK;
                    } else {
                        if (bluetoothMasAppParams.StatusValue == 1) {
                            deleteMMS(Integer.toString(getMmsMsgHndToID(Integer.valueOf(name))));
                        } else if (bluetoothMasAppParams.StatusValue == 0) {
                            unDeleteMMS(Integer.toString(getMmsMsgHndToID(Integer.valueOf(name))));
                        }

                        return ResponseCodes.OBEX_HTTP_OK;
                    }
                }
                cr.close();
            }
        }
        return ResponseCodes.OBEX_HTTP_OK;
    }

    private int setMsgStatusEmail(String name,
                BluetoothMasAppParams bluetoothMasAppParams){
        //Query the mailbox table to get the id values for Inbox and Trash folder
        Uri uri1 = Uri.parse("content://com.android.email.provider/mailbox");

        Cursor cr1 = mContext.getContentResolver().query(uri1, null,
                "(UPPER(displayName) = 'INBOX' OR UPPER(displayName) LIKE '%TRASH%')", null, null);
        int inboxFolderId = 0;
        int deletedFolderId = 0;
        int msgFolderId = 0;
        String folderName;
        if (cr1 != null && cr1.moveToFirst()) {
            do {
                folderName = cr1.getString(cr1.getColumnIndex("displayName"));
                if(folderName.equalsIgnoreCase("INBOX")){
                    inboxFolderId = cr1.getInt(cr1.getColumnIndex("_id"));
                } else {
                    deletedFolderId = cr1.getInt(cr1.getColumnIndex("_id"));
                }
            } while (cr1.moveToNext());
        }
        if (cr1 != null) {
            cr1.close();
        }

        //Query the message table for the given message id
        int emailMsgId = 0;
        if (V){
                Log.v(TAG, "\n name ::=> " + (Integer.valueOf(name)));
        }
        emailMsgId = (Integer.valueOf(name) - EMAIL_HDLR_CONSTANT);

        Uri uri2 = Uri.parse("content://com.android.email.provider/message/"+emailMsgId);
        Cursor crEmail = mContext.getContentResolver().query(uri2, null, null, null,
                null);
        if (crEmail != null && crEmail.moveToFirst()) {

            if (bluetoothMasAppParams.StatusIndicator == 0) {
                /* Read Status */
                ContentValues values = new ContentValues();
                values.put("flagRead", bluetoothMasAppParams.StatusValue);
                mContext.getContentResolver().update(uri2, values, null, null);
            } else {
                if (bluetoothMasAppParams.StatusValue == 1) { //if the email is deleted
                    msgFolderId = crEmail.getInt(crEmail.getColumnIndex("mailboxKey"));
                    if(msgFolderId == deletedFolderId){
                        //TODO need to add notification for deleted email here
                        mnsClient.addMceInitiatedOperation(name);
                        mContext.getContentResolver().delete(
                                Uri.parse("content://com.android.email.provider/message/"
                                + emailMsgId), null, null);
                    } else {
                        ContentValues values = new ContentValues();
                        values.put("mailboxKey", deletedFolderId);
                        mContext.getContentResolver().update(uri2, values, null, null);
                    }
                } else { // if the email is undeleted
                    ContentValues values = new ContentValues();
                    values.put("mailboxKey", inboxFolderId);
                    mContext.getContentResolver().update(uri2, values, null, null);
                }
            }
        }
        if (crEmail != null) {
            crEmail.close();
        }
        return ResponseCodes.OBEX_HTTP_OK;
    }

    /**
     * Returns whether this MAS supports message type SMS
     * @param only condition whether if and only if SMS is supported
     * @return true if SMS is supported when only == false,
     *         if and only if SMS is supported when only == true
     */
    public boolean supportSms(boolean only) {
        if (only) {
            return ((mSupportedMessageTypes & ~MESSAGE_TYPE_SMS) == 0x0) &&
                    ((mSupportedMessageTypes & MESSAGE_TYPE_SMS) != 0x0);
        } else {
            return (mSupportedMessageTypes & MESSAGE_TYPE_SMS) != 0x0;
        }
    }

    /**
     * Returns whether this MAS supports message type MMS
     * @param only condition whether if and only if MMS is supported
     * @return true if MMS is supported when only == false,
     *         if and only if MMS is supported when only == true
     */
    public boolean supportMms(boolean only) {
        if (only) {
            return ((mSupportedMessageTypes & ~MESSAGE_TYPE_MMS) == 0x0) &&
                    ((mSupportedMessageTypes & MESSAGE_TYPE_MMS) != 0x0);
        } else {
            return (mSupportedMessageTypes & MESSAGE_TYPE_MMS) != 0x0;
        }
    }

    /**
     * Returns whether this MAS supports message type EMAIL
     * @param only condition whether if and only if EMAIL is supported
     * @return true if EMAIL is supported when only == false,
     *         if and only if EMAIL is supported when only == true
     */
    public boolean supportEmail(boolean only) {
        if (only) {
            return ((mSupportedMessageTypes & ~MESSAGE_TYPE_EMAIL) == 0x0) &&
                    ((mSupportedMessageTypes & MESSAGE_TYPE_EMAIL) != 0x0);
        } else {
            return (mSupportedMessageTypes & MESSAGE_TYPE_EMAIL) != 0x0;
        }
    }

    /**
     * Returns whether this MAS supports both SMS and MMS
     * @param only condition whether if and only if SMS/MMS is supported
     * @return true if SMS or MMS is supported when only == false,
     *         true if and only if both SMS and MMS is supported when only == true
     */
    public boolean supportSmsMms(boolean only) {
        if (only) {
            return ((mSupportedMessageTypes & ~MESSAGE_TYPE_SMS_MMS) == 0x00) &&
                    ((mSupportedMessageTypes & MESSAGE_TYPE_SMS) != 0x00) &&
                    ((mSupportedMessageTypes & MESSAGE_TYPE_MMS) != 0x00);
        } else {
            return (mSupportedMessageTypes & MESSAGE_TYPE_SMS_MMS) != 0x00;
        }
    }

    public int getMasId() {
        return mMasId;
    }
}
