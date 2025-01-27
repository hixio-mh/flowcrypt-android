/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.retrofit.node

import com.flowcrypt.email.api.email.MsgsCacheManager
import com.flowcrypt.email.api.retrofit.request.node.NodeRequest
import com.flowcrypt.email.security.KeyStoreCryptoManager
import com.flowcrypt.email.util.exception.CorruptedMsgInCacheException
import com.flowcrypt.email.util.exception.ExceptionUtil
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.internal.closeQuietly
import okio.BufferedSink
import okio.ByteString
import okio.Source
import okio.source
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream

/**
 * This is a custom realization of [RequestBody] which will be used by [NodeRequestBodyConverter].
 *
 *
 * Every request body will have the next structure: "endpoint\njson\nbytes"( where "endpoint" is a required parameter):
 *
 *  * UTF8-encoded request name before the first LF (ASCII code 10)
 *  * UTF8-encoded request JSON metadata before the second LF (ASCII code 10)
 *  * binary data afterwards until the end of stream
 *
 *
 * @author Denis Bondarenko
 * Date: 1/10/19
 * Time: 5:54 PM
 * E-mail: DenBond7@gmail.com
 */
class NodeRequestBody constructor(
  private val nodeRequest: NodeRequest,
  private val json: ByteString
) : RequestBody() {

  override fun contentType(): MediaType? {
    return null
  }

  override fun writeTo(sink: BufferedSink) {
    sink.writeUtf8(nodeRequest.endpoint)
    sink.writeByte('\n'.toInt())
    sink.write(json)
    sink.writeByte('\n'.toInt())
    var dataSource: Source? = null

    try {
      dataSource = ByteArrayInputStream(nodeRequest.data).source()
      sink.writeAll(dataSource)
    } finally {
      dataSource?.closeQuietly()
    }

    nodeRequest.uri?.let { uri ->
      var uriSource: Source? = null
      try {
        nodeRequest.context?.contentResolver?.openInputStream(uri)?.let { inputStream ->
          uriSource = if (nodeRequest.hasEncryptedDataInUri) {
            try {
              KeyStoreCryptoManager.getCipherInputStream(inputStream).source()
            } catch (e: Exception) {
              e.printStackTrace()
              ExceptionUtil.handleError(e)
              MsgsCacheManager.evictAll(nodeRequest.context)
              throw CorruptedMsgInCacheException()
            }
          } else {
            BufferedInputStream(inputStream).source()
          }

          uriSource?.let {
            sink.writeAll(it)
          }
        }
      } finally {
        uriSource?.closeQuietly()
      }
    }
  }
}
