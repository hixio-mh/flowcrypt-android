/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.api.email.sync.tasks

import com.flowcrypt.email.api.email.FoldersManager
import com.flowcrypt.email.api.email.JavaEmailConstants
import com.flowcrypt.email.api.email.sync.SyncListener
import com.flowcrypt.email.database.MessageState
import com.flowcrypt.email.database.dao.source.AccountDao
import com.flowcrypt.email.database.dao.source.imap.MessageDaoSource
import com.sun.mail.imap.IMAPFolder
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Session
import javax.mail.Store

/**
 * This task moves messages back to INBOX
 *
 * @author Denis Bondarenko
 *         Date: 10/18/19
 *         Time: 6:14 PM
 *         E-mail: DenBond7@gmail.com
 */
class MovingToInboxSyncTask(ownerKey: String, requestCode: Int) : BaseSyncTask(ownerKey, requestCode) {

  override fun runIMAPAction(account: AccountDao, session: Session, store: Store, listener: SyncListener) {
    val context = listener.context
    val foldersManager = FoldersManager.fromDatabase(context, account.email)
    val inboxFolder = foldersManager.findInboxFolder() ?: return
    val msgDaoSource = MessageDaoSource()

    while (true) {
      val candidatesForMovingToInbox = msgDaoSource.getMsgsWithState(context, account.email,
          MessageState.PENDING_MOVE_TO_INBOX)

      if (candidatesForMovingToInbox.isEmpty()) {
        break
      } else {
        val setOfFolders = candidatesForMovingToInbox.map { it.label }.toSet()

        for (folder in setOfFolders) {
          val filteredMsgs = candidatesForMovingToInbox.filter { it.label == folder }

          if (filteredMsgs.isEmpty() || JavaEmailConstants.FOLDER_OUTBOX.equals(folder, ignoreCase = true)) {
            continue
          }

          val uidList = filteredMsgs.map { it.uid.toLong() }
          val remoteSrcFolder = store.getFolder(folder) as IMAPFolder
          val remoteDestFolder = store.getFolder(inboxFolder.fullName) as IMAPFolder
          remoteSrcFolder.open(Folder.READ_WRITE)

          val msgs: List<Message> = remoteSrcFolder.getMessagesByUID(uidList.toLongArray()).filterNotNull()

          if (msgs.isNotEmpty()) {
            remoteSrcFolder.moveMessages(msgs.toTypedArray(), remoteDestFolder)
            msgDaoSource.deleteMsgsByUID(context, account.email, folder, uidList)
          }

          remoteSrcFolder.close(false)
        }
      }
    }
  }
}