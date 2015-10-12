/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.vcard4android;

import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract.Groups;

import java.io.FileNotFoundException;

import lombok.Cleanup;

public class AndroidGroup {

	protected final AndroidAddressBook addressBook;

	protected Long id;
	protected Contact contact;


	protected AndroidGroup(AndroidAddressBook addressBook, long id) {
		this.addressBook = addressBook;
		this.id = id;
	}

	protected AndroidGroup(AndroidAddressBook addressBook, Contact contact) {
		this.addressBook = addressBook;
		this.contact = contact;
	}


	public Contact getContact() throws FileNotFoundException, ContactsStorageException {
		if (contact != null)
			return contact;

		try {
			@Cleanup Cursor cursor = addressBook.provider.query(addressBook.syncAdapterURI(ContentUris.withAppendedId(Groups.CONTENT_URI, id)),
					new String[] { Groups.TITLE, Groups.NOTES }, null, null, null);
			if (cursor == null || !cursor.moveToNext())
				throw new FileNotFoundException("Contact group not found");

			contact = new Contact();
			contact.displayName = cursor.getString(0);
			contact.note = cursor.getString(1);

			return contact;
		} catch (RemoteException e) {
			throw new ContactsStorageException("Couldn't read contact group", e);
		}
	}


	public Uri create() throws ContactsStorageException {
		ContentValues values = new ContentValues();
		values.put(Groups.ACCOUNT_TYPE, addressBook.account.type);
		values.put(Groups.ACCOUNT_NAME, addressBook.account.name);
		values.put(Groups.TITLE, contact.displayName);
		values.put(Groups.NOTES, contact.note);
		try {
			Uri uri = addressBook.provider.insert(addressBook.syncAdapterURI(Groups.CONTENT_URI), values);
			id = ContentUris.parseId(uri);
			return uri;
		} catch (RemoteException e) {
			throw new ContactsStorageException("Couldn't create contact group", e);
		}
	}

	public int delete() throws ContactsStorageException {
		try {
			return addressBook.provider.delete(addressBook.syncAdapterURI(ContentUris.withAppendedId(Groups.CONTENT_URI, id)), null, null);
		} catch (RemoteException e) {
			throw new ContactsStorageException("Couldn't delete contact group", e);
		}
	}

}
