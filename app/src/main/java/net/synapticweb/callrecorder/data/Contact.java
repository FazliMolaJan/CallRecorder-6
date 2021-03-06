/*
 * Copyright (C) 2019 Eugen Rădulescu <synapticwebb@gmail.com> - All rights reserved.
 *
 * You may use, distribute and modify this code only under the conditions
 * stated in the Synaptic Call Recorder license. You should have received a copy of the
 * Synaptic Call Recorder license along with this file. If not, please write to <synapticwebb@gmail.com>.
 */

package net.synapticweb.callrecorder.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.google.i18n.phonenumbers.PhoneNumberUtil;

import net.synapticweb.callrecorder.Config;
import net.synapticweb.callrecorder.CrApp;
import net.synapticweb.callrecorder.CrLog;
import net.synapticweb.callrecorder.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;


public class Contact implements Comparable<Contact>, Parcelable {
    private Long id;
    private String phoneNumber = null;
    private int phoneType = CrApp.UNKNOWN_TYPE_PHONE_CODE;
    private String contactName = null;
    private Uri photoUri = null;
    private boolean privateNumber = false;
    private Integer color = null;

    public Contact(){
    }

    public Contact(Long id, String phoneNumber, String contactName, String photoUriStr, int phoneTypeCode) {
        setId(id);
        setPhoneNumber(phoneNumber);
        setContactName(contactName);
        setPhotoUri(photoUriStr);
        setPhoneType(phoneTypeCode);
    }

    public static Contact queryNumberInAppContacts(String receivedPhoneNumber, Context context) {
        CallRecorderDbHelper mDbHelper = new CallRecorderDbHelper(context);
        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        List<Contact> contacts = new ArrayList<>();
        String[] projection = {ContactsContract.Contacts._ID,
                ContactsContract.Contacts.COLUMN_NAME_NUMBER,
                ContactsContract.Contacts.COLUMN_NAME_CONTACT_NAME };
        Cursor cursor = db.query(
                ContactsContract.Contacts.TABLE_NAME, projection, null, null, null, null, null);

        while(cursor.moveToNext())
        {
            Long id = cursor.getLong(cursor.getColumnIndex(ContactsContract.Contacts._ID));
            String number = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.COLUMN_NAME_NUMBER));
            String contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.COLUMN_NAME_CONTACT_NAME));
            Contact contact = new Contact(id, number, contactName, null, CrApp.UNKNOWN_TYPE_PHONE_CODE);
            contacts.add(contact);
        }
        cursor.close();

        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        for (Contact contact : contacts) {
            String dbNumPhone = contact.getPhoneNumber();
            PhoneNumberUtil.MatchType matchType = phoneUtil.isNumberMatch(receivedPhoneNumber, dbNumPhone);
            if (matchType != PhoneNumberUtil.MatchType.NO_MATCH && matchType != PhoneNumberUtil.MatchType.NOT_A_NUMBER)
                return contact;
        }
        return null;
    }

    public void updateContact(Context context, boolean byNumber) {
        CallRecorderDbHelper mDbHelper = new CallRecorderDbHelper(context);
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(ContactsContract.Contacts.COLUMN_NAME_NUMBER, getPhoneNumber());
        values.put(ContactsContract.Contacts.COLUMN_NAME_CONTACT_NAME, getContactName());
        values.put(ContactsContract.Contacts.COLUMN_NAME_PHONE_TYPE, getPhoneTypeCode());
        values.put(ContactsContract.Contacts.COLUMN_NAME_PHOTO_URI,
                (getPhotoUri() == null) ? null : getPhotoUri().toString());
        values.put(ContactsContract.Contacts.COLUMN_NAME_PRIVATE_NUMBER, isPrivateNumber());

        try {
            if(byNumber)
                db.update(ContactsContract.Contacts.TABLE_NAME, values,
                    ContactsContract.Contacts.COLUMN_NAME_NUMBER + "='" + getPhoneNumber() + "'", null);
            else
                db.update(ContactsContract.Contacts.TABLE_NAME, values,
                        ContactsContract.Contacts._ID + "=" + getId(), null);
        }
        catch (SQLException exception) {
            CrLog.log(CrLog.ERROR, "Error updating the contact: " + exception.getMessage());
        }
    }

    public void insertInDatabase(Context context) throws SQLException {
        CallRecorderDbHelper mDbHelper = new CallRecorderDbHelper(context);
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();

        values.put(ContactsContract.Contacts.COLUMN_NAME_NUMBER, phoneNumber);
        values.put(ContactsContract.Contacts.COLUMN_NAME_CONTACT_NAME, contactName);
        values.put(ContactsContract.Contacts.COLUMN_NAME_PHOTO_URI, photoUri == null ? null : photoUri.toString());
        values.put(ContactsContract.Contacts.COLUMN_NAME_PHONE_TYPE, phoneType);
        values.put(ContactsContract.Contacts.COLUMN_NAME_PRIVATE_NUMBER, privateNumber);

        setId(db.insertOrThrow(ContactsContract.Contacts.TABLE_NAME, null, values));
    }

    public void delete(Context context) throws SQLException {
        CallRecorderDbHelper mDbHelper = new CallRecorderDbHelper(context);
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        Cursor cursor = db.query(RecordingsContract.Recordings.TABLE_NAME,
                new String[]{RecordingsContract.Recordings._ID, RecordingsContract.Recordings.COLUMN_NAME_PATH},
                RecordingsContract.Recordings.COLUMN_NAME_CONTACT_ID + "=" + getId(), null, null, null, null);

        while(cursor.moveToNext()) {
           Recording recording =
                    new Recording(cursor.getLong(cursor.getColumnIndex(RecordingsContract.Recordings._ID)), null,
                            cursor.getString(cursor.getColumnIndex(RecordingsContract.Recordings.COLUMN_NAME_PATH)),
                            null, null, null, null, null, null, null);
           recording.delete(context);
        }

        cursor.close();
        if(getPhotoUri() != null ) //întotdeauna este poza noastră.
            context.getContentResolver().delete(getPhotoUri(), null, null);
        //dacă foloseam nr pentru a identifica contactul crăpa la numerele private
     if((db.delete(ContactsContract.Contacts.TABLE_NAME, ContactsContract.Contacts._ID
                + "=" + getId(), null)) == 0)
         throw new SQLException("This Contacts row was not deleted");
    }

  @Nullable
  static public Contact queryNumberInPhoneContacts(final String number, @NonNull final Context context) {
        //implementare probabil mai eficientă decît ce aveam eu:
      //https://stackoverflow.com/questions/3505865/android-check-phone-number-present-in-contact-list-phone-number-retrieve-fr
      Uri lookupUri = Uri.withAppendedPath(
              //e atît de lung pentru că am și eu ContactsContract.
              android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
              Uri.encode(number));
      String[] projection = {android.provider.ContactsContract.PhoneLookup.NUMBER,
              android.provider.ContactsContract.PhoneLookup.TYPE,
              android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME,
              android.provider.ContactsContract.PhoneLookup.PHOTO_URI };
      Cursor cursor = context.getContentResolver()
              .query(lookupUri, projection, null, null, null);

          if(cursor != null && cursor.moveToFirst()) {
              Contact contact = new Contact();
              contact.setPhoneType(cursor.getInt(cursor.getColumnIndex(android.provider.ContactsContract.PhoneLookup.TYPE)));
              contact.setContactName(cursor.getString(cursor.getColumnIndex(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)));
              contact.setPhotoUri(cursor.getString(cursor.getColumnIndex(android.provider.ContactsContract.PhoneLookup.PHOTO_URI)));
              contact.setPhoneNumber(cursor.getString(cursor.getColumnIndex(android.provider.ContactsContract.PhoneLookup.NUMBER)));

              cursor.close();
              return contact;
          }
      return null;
    }


    public boolean isPrivateNumber() {
        return privateNumber;
    }

    public void setPrivateNumber(boolean privateNumber) {
        this.privateNumber = privateNumber;
    }


    public int compareTo(@NonNull Contact numberToCompare)
    {
        return this.contactName.compareTo(numberToCompare.getContactName());
    }


    public String getPhoneNumber() {

        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        if(phoneNumber == null)
            this.phoneNumber = CrApp.getInstance().getResources().getString(R.string.private_number_name);
        else
            this.phoneNumber = phoneNumber;
    }

    public int getPhoneTypeCode() {
        return phoneType;
    }

    public String getPhoneTypeName(){
        for(CrApp.PhoneTypeContainer typeContainer : CrApp.PHONE_TYPES)
            if(typeContainer.getTypeCode() == this.phoneType)
                return typeContainer.getTypeName();

        return null;
    }

    public void setPhoneType(String phoneType)
    {
        for(CrApp.PhoneTypeContainer typeContainer : CrApp.PHONE_TYPES)
            if(typeContainer.getTypeName().equals(phoneType)) {
                this.phoneType = typeContainer.getTypeCode();
                break;
            }

    }

    public void setPhoneType(int phoneTypeCode) {
        for(CrApp.PhoneTypeContainer type : CrApp.PHONE_TYPES) {
            if (phoneTypeCode == type.getTypeCode()) {
                this.phoneType = phoneTypeCode;
                return;
            }
        }
        this.phoneType = CrApp.UNKNOWN_TYPE_PHONE_CODE;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        if(contactName == null) {
            if(isPrivateNumber())
                this.contactName = CrApp.getInstance().getResources().getString(R.string.private_number_name);
            else
                this.contactName = CrApp.getInstance().getResources().getString(R.string.unkown_contact);
        }
        else
            this.contactName = contactName;
    }

    public Uri getPhotoUri() {
        return photoUri;
    }

    public void setPhotoUri(String photoUriStr) {
        if(photoUriStr != null) {
            this.photoUri = Uri.parse(photoUriStr);
            String authority = photoUri.getAuthority();
            if(authority != null && !authority.equals(Config.FILE_PROVIDER)) {
                Context context = CrApp.getInstance();
                try {
                    Bitmap originalPhotoBitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), photoUri);
                    //am adăugat System.currentTimeMillis() pentru consistență cu EditContactActivity.setPhotoPath().
                    File copiedPhotoFile = new File(context.getFilesDir(), getPhoneNumber() + System.currentTimeMillis() + ".jpg");
                    OutputStream os = new FileOutputStream(copiedPhotoFile);
                    originalPhotoBitmap.compress(Bitmap.CompressFormat.JPEG, 70, os);
                    setPhotoUri(FileProvider.getUriForFile(context, Config.FILE_PROVIDER, copiedPhotoFile));
                }
                catch(IOException exception) {
                   this.photoUri = null;
                }
            }
        }
        else
            this.photoUri = null;
    }

    public void setPhotoUri(Uri photoUri) {
        this.photoUri = photoUri;
    }

    public Long getId() { //Trebuie Long ptr că id poate să fie null
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getColor() {
        return color;
    }

    public void setColor(Integer color) {
        this.color = color;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(this.id);
        dest.writeString(this.phoneNumber);
        dest.writeInt(this.phoneType);
        dest.writeString(this.contactName);
        dest.writeParcelable(this.photoUri, flags);
        dest.writeByte(this.privateNumber ? (byte) 1 : (byte) 0);
        dest.writeValue(this.color);
    }

    protected Contact(Parcel in) {
        this.id = (Long) in.readValue(Long.class.getClassLoader());
        this.phoneNumber = in.readString();
        this.phoneType = in.readInt();
        this.contactName = in.readString();
        this.photoUri = in.readParcelable(Uri.class.getClassLoader());
        this.privateNumber = in.readByte() != 0;
        this.color = (Integer) in.readValue(Integer.class.getClassLoader());
    }

    public static final Creator<Contact> CREATOR = new Creator<Contact>() {
        @Override
        public Contact createFromParcel(Parcel source) {
            return new Contact(source);
        }

        @Override
        public Contact[] newArray(int size) {
            return new Contact[size];
        }
    };
}
