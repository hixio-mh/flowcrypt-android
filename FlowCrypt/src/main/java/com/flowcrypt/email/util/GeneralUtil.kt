/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.util

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.TextUtils
import android.webkit.MimeTypeMap
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.flowcrypt.email.BuildConfig
import com.flowcrypt.email.Constants
import com.flowcrypt.email.R
import com.flowcrypt.email.api.retrofit.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.apache.commons.io.IOUtils
import retrofit2.Retrofit
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * General util methods.
 *
 * @author DenBond7
 * Date: 31.03.2017
 * Time: 16:55
 * E-mail: DenBond7@gmail.com
 */
class GeneralUtil {
  companion object {
    /**
     * Check is the app foregrounded or visible.
     *
     * @return true if the app is foregrounded or visible.
     */
    fun isAppForegrounded(): Boolean {
      val appProcessInfo = ActivityManager.RunningAppProcessInfo()
      ActivityManager.getMyMemoryState(appProcessInfo)
      return appProcessInfo.importance == IMPORTANCE_FOREGROUND || appProcessInfo.importance == IMPORTANCE_VISIBLE
    }

    /**
     * This method checks is it a debug build.
     *
     * @return true - if the current build is a debug build.
     */
    fun isDebugBuild(): Boolean = "debug" == BuildConfig.BUILD_TYPE

    /**
     * Checking for an Internet connection.
     * See https://developer.android.com/training/monitoring-device-state/connectivity-monitoring#DetermineConnection.
     * Because [NetworkInfo.isConnectedOrConnecting] is deprecated we will use [NetworkInfo.isConnected]
     *
     * @param context Interface to global information about an application environment.
     * @return <tt>boolean</tt> true - a connection available, false if otherwise.
     */
    //todo-denbond7 Need to fix deprecation
    fun isConnected(context: Context?): Boolean {
      val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      val activeNetwork = cm?.activeNetworkInfo
      return activeNetwork?.isConnected ?: false
    }

    suspend fun hasInternetAccess(): Boolean = withContext(Dispatchers.IO) {
      val url = "https://www.google.com"
      val connectionTimeoutInMilliseconds = 2000L
      val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectionTimeoutInMilliseconds, TimeUnit.MILLISECONDS)
        .writeTimeout(connectionTimeoutInMilliseconds, TimeUnit.MILLISECONDS)
        .readTimeout(connectionTimeoutInMilliseconds, TimeUnit.MILLISECONDS)
        .build()

      val retrofit = Retrofit.Builder()
        .baseUrl(url)
        .client(okHttpClient)
        .build()
      val apiService = retrofit.create(ApiService::class.java)
      try {
        apiService.isAvailable(url)
        return@withContext true
      } catch (e: Exception) {
        return@withContext false
      }
    }

    /**
     * Show the application system settings screen.
     *
     * @param context Interface to global information about an application environment.
     */
    fun showAppSettingScreen(context: Context) {
      val intent = Intent()
      intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
      val uri = Uri.fromParts("package", context.packageName, null)
      intent.data = uri
      context.startActivity(intent)
    }

    /**
     * Read a file by his Uri and return him as [String].
     *
     * @param uri The [Uri] of the file.
     * @return <tt>[String]</tt> which contains a file.
     * @throws IOException will thrown for example if the file not found
     */
    fun readFileFromUriToString(context: Context, uri: Uri): String? {
      val inputStream = context.contentResolver.openInputStream(uri)
      return if (inputStream != null) {
        IOUtils.toString(inputStream, StandardCharsets.UTF_8)
      } else null
    }

    /**
     * Remove all comments from the given HTML [String].
     *
     * @param text The given string.
     * @return <tt>[String]</tt> which doesn't contain HTML comments.
     */
    fun removeAllComments(text: String): String {
      return if (TextUtils.isEmpty(text)) text else text.replace("<!--[\\s\\S]*?-->".toRegex(), "")
    }

    /**
     * Get a file size from his Uri.
     *
     * @param fileUri The [Uri] of the file.
     * @return The size of the file in bytes.
     */
    fun getFileSizeFromUri(context: Context, fileUri: Uri?): Long {
      var fileSize: Long = -1

      fileUri?.let {
        if (ContentResolver.SCHEME_FILE.equals(fileUri.scheme!!, ignoreCase = true)) {
          val path = it.path ?: return@let
          fileSize = File(path).length()
        } else {
          val returnCursor = context.contentResolver.query(
            fileUri,
            arrayOf(OpenableColumns.SIZE), null, null, null
          )

          if (returnCursor != null) {
            if (returnCursor.moveToFirst()) {
              val index = returnCursor.getColumnIndex(OpenableColumns.SIZE)
              if (index >= 0) {
                fileSize = returnCursor.getLong(index)
              }
            }

            returnCursor.close()
          }
        }
      }

      return fileSize
    }

    /**
     * Get a file name from his Uri.
     *
     * @param fileUri The [Uri] of the file.
     * @return The file name.
     */
    fun getFileNameFromUri(context: Context, fileUri: Uri?): String? {
      var fileName: String? = null
      fileUri?.let {
        if (ContentResolver.SCHEME_FILE.equals(fileUri.scheme!!, ignoreCase = true)) {
          val path = it.path ?: return@let
          fileName = File(path).name
        } else {
          val returnCursor = context.contentResolver.query(
            fileUri,
            arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
          )

          if (returnCursor != null) {
            if (returnCursor.moveToFirst()) {
              val index = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
              if (index >= 0) {
                fileName = returnCursor.getString(index)
              }
            }

            returnCursor.close()
          }
        }
      }

      return fileName
    }

    /**
     * Write to the file some data by his Uri and return him as [String].
     *
     * @param context Interface to global information about an application environment.
     * @param data    The data which will be written.
     * @param uri     The [Uri] of the file.
     * @return the number of bytes copied, or -1 if &gt; Integer.MAX_VALUE
     * @throws IOException if an I/O error occurs
     */
    fun writeFileFromStringToUri(context: Context, uri: Uri, data: String): Int {
      val inputStream = IOUtils.toInputStream(data, StandardCharsets.UTF_8)
      val outputStream = context.contentResolver.openOutputStream(uri)
      return IOUtils.copy(inputStream, outputStream)
    }

    /**
     * Generate an unique extra key using the application id and the class name.
     *
     * @param key The key of the new extra key.
     * @param c   The class where a new extra key will be created.
     * @return The new extra key.
     */
    fun generateUniqueExtraKey(key: String, c: Class<*>): String {
      return BuildConfig.APPLICATION_ID + "." + c.simpleName + "." + key
    }

    /**
     * Insert arbitrary string at regular interval into another string
     *
     * @param template       String template which will be inserted to the original string.
     * @param originalString The original string which will be formatted.
     * @param groupSize      Group size
     * @return The formatted string.
     */
    fun doSectionsInText(
      template: String? = " ",
      originalString: String?,
      groupSize: Int
    ): String? {

      if (template == null || originalString == null || groupSize <= 0 || originalString.length <= groupSize) {
        return originalString
      }

      val stringBuilder = StringBuilder(originalString)

      var i = stringBuilder.length
      while (i > 0) {
        stringBuilder.insert(i, template)
        i -= groupSize
      }
      return stringBuilder.toString()
    }

    /**
     * Check is current email valid.
     *
     * @param email The current email.
     * @return true if the email has valid format, otherwise false.
     */
    fun isEmailValid(email: CharSequence?): Boolean {
      return email?.isNotEmpty() == true && android.util.Patterns.EMAIL_ADDRESS.matcher(email)
        .matches()
    }

    /**
     * Get a mime type of the input [Uri]
     *
     * @param context Interface to global information about an application environment.
     * @param uri     The [Uri] of the file.
     * @return A mime type of of the [Uri].
     */
    fun getFileMimeTypeFromUri(context: Context, uri: Uri): String {
      return if (ContentResolver.SCHEME_CONTENT.equals(uri.scheme, ignoreCase = true)) {
        val contentResolver = context.contentResolver
        contentResolver.getType(uri) ?: Constants.MIME_TYPE_BINARY_DATA
      } else {
        val fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        MimeTypeMap.getSingleton()
          .getMimeTypeFromExtension(fileExtension.toLowerCase(Locale.getDefault()))
          ?: Constants.MIME_TYPE_BINARY_DATA
      }
    }

    /**
     * Generate a unique name for [androidx.test.espresso.IdlingResource]
     *
     * @param aClass The class where we will use [androidx.test.espresso.IdlingResource]
     * @return A generated name.
     */
    fun genIdlingResourcesName(aClass: Class<*>): String {
      return aClass.simpleName + "-" + UUID.randomUUID()
    }

    /**
     * Clear the [ClipData]
     *
     * @param context Interface to global information about an application environment.
     */
    @Suppress("unused")
    fun clearClipboard(context: Context) {
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      clipboard.setPrimaryClip(ClipData.newPlainText(null, ""))
    }

    /**
     * Generate order number for an attachment. This value will be used for the notifications ordering.
     *
     * @param context Interface to global information about an application environment.
     * @return The generated order number.
     */
    fun genAttOrderId(context: Context): Int {
      var lastId = SharedPreferencesHelper.getInt(
        PreferenceManager.getDefaultSharedPreferences(context),
        Constants.PREF_KEY_LAST_ATT_ORDER_ID, 0
      )

      lastId++

      SharedPreferencesHelper.setInt(
        PreferenceManager.getDefaultSharedPreferences(context),
        Constants.PREF_KEY_LAST_ATT_ORDER_ID, lastId
      )

      return lastId
    }

    /**
     * Open a Chrome Custom Tab with a predefined style.
     *
     * @param context Interface to global information about an application environment.
     * @param url The given url
     */
    fun openCustomTab(context: Context, url: String) {
      val builder = CustomTabsIntent.Builder()
      val customTabsIntent = builder.build()
      builder.setToolbarColor(ContextCompat.getColor(context, R.color.colorPrimary))

      val intent = Intent(Intent.ACTION_VIEW)
      intent.data = Uri.parse(url)
      if (intent.resolveActivity(context.packageManager) != null) {
        intent.data?.let {
          customTabsIntent.launchUrl(context, it)
        }
      }
    }

    /**
     * This function helps to get [Bitmap] from the given [Drawable]
     *
     * @param drawable The given drawable
     */
    fun drawableToBitmap(drawable: Drawable): Bitmap {
      if (drawable is BitmapDrawable) {
        if (drawable.bitmap != null) {
          return drawable.bitmap
        }
      }

      val bitmap: Bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
      } else {
        Bitmap.createBitmap(
          drawable.intrinsicWidth,
          drawable.intrinsicHeight,
          Bitmap.Config.ARGB_8888
        )
      }

      val canvas = Canvas(bitmap)
      drawable.setBounds(0, 0, canvas.width, canvas.height)
      drawable.draw(canvas)
      return bitmap
    }

    /**
     * Returns a language code of the current [Locale].
     */
    fun getLocaleLanguageCode(context: Context?): String {
      context ?: return "en"
      return if (context.resources.configuration.locales.isEmpty) {
        "en"
      } else context.resources.configuration.locales.get(0).language.toLowerCase(Locale.getDefault())
    }
  }
}
