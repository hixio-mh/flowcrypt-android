/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: ivan
 */

package com.flowcrypt.email.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.flowcrypt.email.BuildConfig
import com.flowcrypt.email.Constants
import com.flowcrypt.email.api.email.MsgsCacheManager
import com.flowcrypt.email.database.FlowCryptRoomDatabase
import com.flowcrypt.email.util.gson.GsonHelper
import com.google.gson.Gson
import org.apache.commons.io.IOUtils
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * @author Denis Bondarenko
 * Date: 18.01.2018
 * Time: 13:02
 * E-mail: DenBond7@gmail.com
 */
object TestGeneralUtil {
  fun <T> readObjectFromResources(path: String, aClass: Class<T>): T {
    val json =
      IOUtils.toString(aClass.classLoader!!.getResourceAsStream(path), StandardCharsets.UTF_8)
    return Gson().fromJson(json, aClass)
  }

  fun readResourceAsByteArray(path: String): ByteArray {
    return IOUtils.toByteArray(TestGeneralUtil::class.java.classLoader!!.getResourceAsStream(path))
  }

  fun readResourceAsString(path: String, charset: Charset = StandardCharsets.UTF_8): String {
    return IOUtils.toString(
      TestGeneralUtil::class.java.classLoader!!.getResourceAsStream(path),
      charset
    )
  }

  @Suppress("unused")
  fun readResourceAsStream(path: String): InputStream {
    return TestGeneralUtil::class.java.classLoader!!.getResourceAsStream(path)
  }

  fun readFileFromAssetsAsString(
    filePath: String,
    context: Context = InstrumentationRegistry.getInstrumentation().context
  ): String {
    return IOUtils.toString(context.assets.open(filePath), "UTF-8")
  }

  @Suppress("unused")
  fun readFileFromAssetsAsByteArray(
    filePath: String,
    context: Context = InstrumentationRegistry.getInstrumentation().context
  ): ByteArray {
    return context.assets.open(filePath).readBytes()
  }

  fun readFileFromAssetsAsStream(
    filePath: String,
    context: Context = InstrumentationRegistry.getInstrumentation().context
  ): InputStream {
    return context.assets.open(filePath)
  }

  fun deleteFiles(files: List<File>) {
    files.forEach { file ->
      if (!file.delete()) {
        println("Can't delete a file $file")
      }
    }
  }

  fun createFileAndFillWithContent(fileName: String, fileText: String): File {
    val file = File(
      InstrumentationRegistry.getInstrumentation().targetContext
        .getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName
    )
    try {
      FileOutputStream(file).use { outputStream -> outputStream.write(fileText.toByteArray()) }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return file
  }

  fun createFileAndFillWithContent(
    temporaryFolder: TemporaryFolder,
    fileName: String, fileText: String
  ): File {
    val file = temporaryFolder.newFile(fileName)
    try {
      FileOutputStream(file).use { outputStream -> outputStream.write(fileText.toByteArray()) }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return file
  }

  fun createFileWithGivenSize(
    fileSizeInBytes: Long, temporaryFolder: TemporaryFolder,
    fileName: String = UUID.randomUUID().toString()
  ): File {
    return temporaryFolder.newFile(fileName).apply {
      RandomAccessFile(this, "rw").apply {
        setLength(fileSizeInBytes)
      }
    }
  }

  fun <T> getObjectFromJson(jsonPathInAssets: String?, classOfT: Class<T>): T? {
    try {
      if (jsonPathInAssets != null) {
        val gson = GsonHelper.gson
        val json = readFileFromAssetsAsString(jsonPathInAssets)
        return gson.fromJson(json, classOfT)
      }
    } catch (e: IOException) {
      e.printStackTrace()
    }

    return null
  }

  fun replaceVersionInKey(key: String?): String {
    val regex = "Version: FlowCrypt \\d*.\\d*.\\d* Gmail".toRegex()
    val version = BuildConfig.VERSION_NAME.split("_").first()
    val replacement = "Version: FlowCrypt $version Gmail"
    key?.let {
      return key.replaceFirst(regex, replacement)
    }
    return ""
  }

  /**
   * Generate an [Intent] with [Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION]
   * and [Intent.FLAG_GRANT_READ_URI_PERMISSION]
   */
  fun genIntentWithPersistedReadPermissionForFile(file: File): Intent {
    return Intent().apply {
      val context: Context = ApplicationProvider.getApplicationContext()
      val uri = FileProvider.getUriForFile(context, Constants.FILE_PROVIDER_AUTHORITY, file)
      context.grantUriPermission(
        BuildConfig.APPLICATION_ID, uri,
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_READ_URI_PERMISSION
      )
      data = uri
      flags = Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
          Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
  }

  fun genIntentForNavigationComponent(uri: String, extras: Bundle? = null): Intent {
    return Intent(
      Intent.ACTION_VIEW,
      Uri.parse(uri)
    ).apply {
      extras?.let { putExtra("android-support-nav:controller:deepLinkExtras", it) }
    }
  }

  fun clearApp(context: Context) {
    SharedPreferencesHelper.clear(context)
    FileAndDirectoryUtils.cleanDir(context.cacheDir)
    FileAndDirectoryUtils.cleanDir(File(context.filesDir, MsgsCacheManager.CACHE_DIR_NAME))
    FlowCryptRoomDatabase.getDatabase(context).forceDatabaseCreationIfNeeded()
    FlowCryptRoomDatabase.getDatabase(context).clearAllTables()
  }
}
