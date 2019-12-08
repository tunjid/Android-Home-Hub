package com.rcswitchcontrol.protocols

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri

class ContextProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        appContext = context!!.applicationContext
        return true
    }

    override fun attachInfo(context: Context?, info: ProviderInfo?) {
        if (info == null) {
            throw NullPointerException("YourLibraryInitProvider ProviderInfo cannot be null.")
        }
        // So if the authorities equal the library internal ones, the developer forgot to set his applicationId
        check("<your-library-applicationid>.contextProvider" != info.authority) {
            ("Incorrect provider authority in manifest. Most likely due to a "
                + "missing applicationId variable in application\'s build.gradle.") }

        super.attachInfo(context, info)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun getType(uri: Uri): String? = null

    companion object {
        lateinit var appContext: Context
    }
}