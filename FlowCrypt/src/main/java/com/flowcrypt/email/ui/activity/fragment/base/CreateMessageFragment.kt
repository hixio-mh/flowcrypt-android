/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity.fragment.base

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.format.Formatter
import android.util.Log
import android.view.ContextMenu
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FilterQueryProvider
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.viewModels
import com.flowcrypt.email.Constants
import com.flowcrypt.email.R
import com.flowcrypt.email.api.email.EmailUtil
import com.flowcrypt.email.api.email.FoldersManager
import com.flowcrypt.email.api.email.model.AttachmentInfo
import com.flowcrypt.email.api.email.model.ExtraActionInfo
import com.flowcrypt.email.api.email.model.IncomingMessageInfo
import com.flowcrypt.email.api.email.model.OutgoingMessageInfo
import com.flowcrypt.email.api.email.model.ServiceInfo
import com.flowcrypt.email.api.retrofit.response.base.Result
import com.flowcrypt.email.database.FlowCryptRoomDatabase
import com.flowcrypt.email.database.entity.AccountEntity
import com.flowcrypt.email.database.entity.ContactEntity
import com.flowcrypt.email.extensions.decrementSafely
import com.flowcrypt.email.extensions.incrementSafely
import com.flowcrypt.email.extensions.org.bouncycastle.openpgp.toPgpKeyDetails
import com.flowcrypt.email.extensions.showInfoDialog
import com.flowcrypt.email.extensions.showKeyboard
import com.flowcrypt.email.extensions.showNeedPassphraseDialog
import com.flowcrypt.email.jetpack.viewmodel.AccountAliasesViewModel
import com.flowcrypt.email.jetpack.viewmodel.ContactsViewModel
import com.flowcrypt.email.model.MessageEncryptionType
import com.flowcrypt.email.model.MessageType
import com.flowcrypt.email.model.PgpContact
import com.flowcrypt.email.security.KeysStorageImpl
import com.flowcrypt.email.security.model.PgpKeyDetails
import com.flowcrypt.email.ui.activity.CreateMessageActivity
import com.flowcrypt.email.ui.activity.ImportPublicKeyActivity
import com.flowcrypt.email.ui.activity.SelectContactsActivity
import com.flowcrypt.email.ui.activity.fragment.dialog.ChoosePublicKeyDialogFragment
import com.flowcrypt.email.ui.activity.fragment.dialog.FixNeedPassphraseIssueDialogFragment
import com.flowcrypt.email.ui.activity.fragment.dialog.NoPgpFoundDialogFragment
import com.flowcrypt.email.ui.activity.listeners.OnChangeMessageEncryptionTypeListener
import com.flowcrypt.email.ui.adapter.FromAddressesAdapter
import com.flowcrypt.email.ui.adapter.PgpContactAdapter
import com.flowcrypt.email.ui.widget.CustomChipSpanChipCreator
import com.flowcrypt.email.ui.widget.PGPContactChipSpan
import com.flowcrypt.email.ui.widget.PgpContactsNachoTextView
import com.flowcrypt.email.ui.widget.SingleCharacterSpanChipTokenizer
import com.flowcrypt.email.util.FileAndDirectoryUtils
import com.flowcrypt.email.util.GeneralUtil
import com.flowcrypt.email.util.UIUtil
import com.flowcrypt.email.util.exception.ExceptionUtil
import com.google.android.gms.common.util.CollectionUtils
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.hootsuite.nachos.NachoTextView
import com.hootsuite.nachos.chip.Chip
import com.hootsuite.nachos.terminator.ChipTerminatorHandler
import com.hootsuite.nachos.validator.ChipifyingNachoValidator
import org.apache.commons.io.FileUtils
import org.pgpainless.key.OpenPgpV4Fingerprint
import java.io.File
import java.io.IOException
import java.util.regex.Pattern
import javax.mail.internet.InternetAddress

/**
 * This fragment describe a logic of sent an encrypted or standard message.
 *
 * @author DenBond7
 * Date: 10.05.2017
 * Time: 11:27
 * E-mail: DenBond7@gmail.com
 */
//todo-denbond7 exlude from the base package
class CreateMessageFragment : BaseSyncFragment(), View.OnFocusChangeListener,
  AdapterView.OnItemSelectedListener,
  View.OnClickListener, PgpContactsNachoTextView.OnChipLongClickListener {

  private lateinit var onMsgSendListener: OnMessageSendListener
  private lateinit var listener: OnChangeMessageEncryptionTypeListener
  private lateinit var draftCacheDir: File

  private val accountAliasesViewModel: AccountAliasesViewModel by viewModels()
  private val contactsViewModel: ContactsViewModel by viewModels()

  private var pgpContactsTo: MutableList<PgpContact>? = null
  private var pgpContactsCc: MutableList<PgpContact>? = null
  private var pgpContactsBcc: MutableList<PgpContact>? = null
  private val atts: MutableList<AttachmentInfo>?
  private var folderType: FoldersManager.FolderType? = null
  private var msgInfo: IncomingMessageInfo? = null
  private var serviceInfo: ServiceInfo? = null
  private var fromAddrs: FromAddressesAdapter<String>? = null
  private var pgpContactWithNoPublicKey: PgpContact? = null
  private var extraActionInfo: ExtraActionInfo? = null
  private var messageType = MessageType.NEW

  private var layoutAtts: ViewGroup? = null
  private var editTextFrom: EditText? = null
  private var spinnerFrom: Spinner? = null
  private var recipientsTo: PgpContactsNachoTextView? = null
  private var recipientsCc: PgpContactsNachoTextView? = null
  private var recipientsBcc: PgpContactsNachoTextView? = null
  private var editTextEmailSubject: EditText? = null
  private var editTextEmailMsg: EditText? = null
  private var textInputLayoutMsg: TextInputLayout? = null
  private var layoutContent: ScrollView? = null
  private var progressBarTo: View? = null
  private var progressBarCc: View? = null
  private var progressBarBcc: View? = null
  private var layoutCc: View? = null
  private var layoutBcc: View? = null
  private var progressBarAndButtonLayout: LinearLayout? = null
  private var imageButtonAliases: ImageButton? = null
  private var imageButtonAdditionalRecipientsVisibility: View? = null
  private var iBShowQuotedText: View? = null

  private var isContactsUpdateEnabled = true
  private var isUpdateToCompleted = true
  private var isUpdateCcCompleted = true
  private var isUpdateBccCompleted = true
  private var isIncomingMsgInfoUsed: Boolean = false
  private var isMsgSentToQueue: Boolean = false
  private var originalColor: Int = 0

  private val hostActivity: CreateMessageActivity?
    get() {
      return activity as? CreateMessageActivity
    }

  override val contentResourceId: Int = R.layout.fragment_create_message

  override val contentView: View?
    get() = layoutContent

  /**
   * Generate an outgoing message info from entered information by user.
   *
   * @return <tt>OutgoingMessageInfo</tt> Return a created OutgoingMessageInfo object which
   * contains information about an outgoing message.
   */
  private fun getOutgoingMsgInfo(): OutgoingMessageInfo {
    var msg = editTextEmailMsg?.text.toString()
    if (messageType == MessageType.REPLY || messageType == MessageType.REPLY_ALL) {
      if (iBShowQuotedText?.visibility == View.VISIBLE) {
        msg += EmailUtil.genReplyContent(msgInfo)
      }
    }

    val attachments = atts?.minus(forwardedAtts)
    attachments?.forEachIndexed { index, attachmentInfo -> attachmentInfo.path = index.toString() }

    return OutgoingMessageInfo(
      account = accountViewModel.activeAccountLiveData.value?.email ?: "",
      subject = editTextEmailSubject?.text.toString(),
      msg = msg,
      toRecipients = recipientsTo?.chipValues?.map { InternetAddress(it) } ?: emptyList(),
      ccRecipients = recipientsCc?.chipValues?.map { InternetAddress(it) },
      bccRecipients = recipientsBcc?.chipValues?.map { InternetAddress(it) },
      from = editTextFrom?.text.toString(),
      atts = attachments,
      forwardedAtts = forwardedAtts,
      encryptionType = listener.msgEncryptionType,
      messageType = messageType,
      replyToMsgEntity = msgInfo?.msgEntity,
      uid = EmailUtil.genOutboxUID(context)
    )
  }

  /**
   * Do a lot of checks to validate an outgoing message info.
   *
   * @return <tt>Boolean</tt> true if all information is correct, false otherwise.
   */
  private val isDataCorrect: Boolean
    get() {
      recipientsTo?.chipifyAllUnterminatedTokens()
      recipientsCc?.chipifyAllUnterminatedTokens()
      recipientsBcc?.chipifyAllUnterminatedTokens()
      if (fromAddrs?.isEnabled(
          spinnerFrom?.selectedItemPosition
            ?: Spinner.INVALID_POSITION
        ) == false
      ) {
        showInfoSnackbar(recipientsTo!!, getString(R.string.no_key_available))
        return false
      }
      if (recipientsTo?.text?.isEmpty() == true) {
        showInfoSnackbar(
          recipientsTo!!, getString(
            R.string.text_must_not_be_empty,
            getString(R.string.prompt_recipients_to)
          )
        )
        recipientsTo?.requestFocus()
        return false
      }
      if (hasInvalidEmail(recipientsTo, recipientsCc, recipientsBcc)) {
        return false
      }
      if (listener.msgEncryptionType === MessageEncryptionType.ENCRYPTED) {
        if (recipientsTo?.text?.isNotEmpty() == true && pgpContactsTo?.isEmpty() == true) {
          fetchDetailsAboutContacts(ContactEntity.Type.TO)
          return false
        }
        if (recipientsCc?.text?.isNotEmpty() == true && pgpContactsCc?.isEmpty() == true) {
          fetchDetailsAboutContacts(ContactEntity.Type.CC)
          return false
        }
        if (recipientsBcc?.text?.isNotEmpty() == true && pgpContactsBcc?.isEmpty() == true) {
          fetchDetailsAboutContacts(ContactEntity.Type.BCC)
          return false
        }
        if (hasRecipientWithoutPgp(true, pgpContactsTo, pgpContactsCc, pgpContactsBcc)) {
          return false
        }
        if (hasRecipientWithExpiredPubKey(pgpContactsTo, pgpContactsCc, pgpContactsBcc)) {
          return false
        }
      }
      if (editTextEmailSubject?.text?.isEmpty() == true) {
        showInfoSnackbar(
          editTextEmailSubject, getString(
            R.string.text_must_not_be_empty,
            getString(R.string.prompt_subject)
          )
        )
        editTextEmailSubject?.requestFocus()
        return false
      }
      if (atts?.isEmpty() == true && editTextEmailMsg?.text?.isEmpty() == true) {
        showInfoSnackbar(editTextEmailMsg, getString(R.string.sending_message_must_not_be_empty))
        editTextEmailMsg?.requestFocus()
        return false
      }
      if (atts?.isEmpty() == true || !hasExternalStorageUris(this.atts)) {
        return true
      }
      return false
    }


  /**
   * Generate a forwarded attachments list.
   *
   * @return The generated list.
   */
  private val forwardedAtts: MutableList<AttachmentInfo>
    get() {
      val atts = mutableListOf<AttachmentInfo>()

      this.atts?.let {
        for (att in it) {
          if (att.id != null && att.isForwarded) {
            atts.add(att)
          }
        }
      }

      return atts
    }

  init {
    pgpContactsTo = mutableListOf()
    pgpContactsCc = mutableListOf()
    pgpContactsBcc = mutableListOf()
    atts = ArrayList()
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    if (context is OnMessageSendListener) {
      this.onMsgSendListener = context
    } else
      throw IllegalArgumentException(
        context.toString() + " must implement " +
            OnMessageSendListener::class.java.simpleName
      )

    if (context is OnChangeMessageEncryptionTypeListener) {
      this.listener = context
    } else
      throw IllegalArgumentException(
        context.toString() + " must implement " +
            OnChangeMessageEncryptionTypeListener::class.java.simpleName
      )

    initDraftCacheDir(context)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)

    context?.let {
      fromAddrs = FromAddressesAdapter(
        it, android.R.layout.simple_list_item_1, android.R.id
          .text1, ArrayList()
      )
    }
    fromAddrs?.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    fromAddrs?.setUseKeysInfo(listener.msgEncryptionType === MessageEncryptionType.ENCRYPTED)

    initExtras(activity?.intent)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    initViews(view)
    setupAccountAliasesViewModel()
    setupPrivateKeysViewModel()
    setupContactsViewModel()
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    val isEncryptedMode = listener.msgEncryptionType === MessageEncryptionType.ENCRYPTED
    if (msgInfo != null && GeneralUtil.isConnected(context) && isEncryptedMode) {
      updateRecipients()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    if (!isMsgSentToQueue) {
      atts?.let {
        for (att in it) {
          att.uri?.let { uri ->
            if (Constants.FILE_PROVIDER_AUTHORITY.equals(uri.authority, ignoreCase = true)) {
              context?.contentResolver?.delete(uri, null, null)
            }
          }
        }
      }
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    when (requestCode) {
      REQUEST_CODE_NO_PGP_FOUND_DIALOG -> when (resultCode) {
        NoPgpFoundDialogFragment.RESULT_CODE_SWITCH_TO_STANDARD_EMAIL ->
          listener.onMsgEncryptionTypeChanged(MessageEncryptionType.STANDARD)

        NoPgpFoundDialogFragment.RESULT_CODE_IMPORT_THEIR_PUBLIC_KEY -> if (data != null) {
          val pgpContact =
            data.getParcelableExtra<PgpContact>(NoPgpFoundDialogFragment.EXTRA_KEY_PGP_CONTACT)

          if (pgpContact != null) {
            startActivityForResult(
              ImportPublicKeyActivity.newIntent(
                context, account,
                getString(R.string.import_public_key), pgpContact
              ), REQUEST_CODE_IMPORT_PUBLIC_KEY
            )
          }
        }

        NoPgpFoundDialogFragment.RESULT_CODE_COPY_FROM_OTHER_CONTACT -> if (data != null) {
          pgpContactWithNoPublicKey =
            data.getParcelableExtra(NoPgpFoundDialogFragment.EXTRA_KEY_PGP_CONTACT)

          if (pgpContactWithNoPublicKey != null) {
            startActivityForResult(
              SelectContactsActivity.newIntent(
                context,
                getString(R.string.use_public_key_from), false
              ), REQUEST_CODE_COPY_PUBLIC_KEY_FROM_OTHER_CONTACT
            )
          }
        }

        NoPgpFoundDialogFragment.RESULT_CODE_REMOVE_CONTACT -> if (data != null) {
          val pgpContact =
            data.getParcelableExtra<PgpContact>(NoPgpFoundDialogFragment.EXTRA_KEY_PGP_CONTACT)

          if (pgpContact != null) {
            removePgpContact(pgpContact, recipientsTo, pgpContactsTo)
            removePgpContact(pgpContact, recipientsCc, pgpContactsCc)
            removePgpContact(pgpContact, recipientsBcc, pgpContactsBcc)
          }
        }
      }

      REQUEST_CODE_IMPORT_PUBLIC_KEY -> when (resultCode) {
        Activity.RESULT_OK -> {
          Toast.makeText(context, R.string.the_key_successfully_imported, Toast.LENGTH_SHORT).show()
          updateRecipients()
        }
      }

      REQUEST_CODE_COPY_PUBLIC_KEY_FROM_OTHER_CONTACT -> {
        when (resultCode) {
          Activity.RESULT_OK -> if (data != null) {
            val contactEntity =
              data.getParcelableExtra<ContactEntity>(SelectContactsActivity.KEY_EXTRA_PGP_CONTACT)
            contactEntity?.let {
              pgpContactWithNoPublicKey?.pubkey = String(contactEntity.publicKey ?: byteArrayOf())
              pgpContactWithNoPublicKey?.email?.let { email ->
                contactsViewModel.updateContactPgpInfo(
                  email,
                  contactEntity
                )
              }

              pgpContactsTo?.forEach {
                if (it.email.equals(pgpContactWithNoPublicKey?.email, true)) {
                  it.hasPgp = true
                }
              }

              pgpContactsCc?.forEach {
                if (it.email.equals(pgpContactWithNoPublicKey?.email, true)) {
                  it.hasPgp = true
                }
              }

              pgpContactsBcc?.forEach {
                if (it.email.equals(pgpContactWithNoPublicKey?.email, true)) {
                  it.hasPgp = true
                }
              }

              updateChips(recipientsTo, pgpContactsTo)
              updateChips(recipientsCc, pgpContactsCc)
              updateChips(recipientsBcc, pgpContactsBcc)

              Toast.makeText(context, R.string.key_successfully_copied, Toast.LENGTH_LONG).show()
            }
          }
        }

        pgpContactWithNoPublicKey = null
      }

      REQUEST_CODE_GET_CONTENT_FOR_SENDING -> when (resultCode) {
        Activity.RESULT_OK -> {
          addAttachmentInfoFromIntent(data)
        }
      }

      REQUEST_CODE_SHOW_PUB_KEY_DIALOG -> when (resultCode) {
        Activity.RESULT_OK -> {
          if (data != null) {
            val keyList: List<AttachmentInfo> =
              data.getParcelableArrayListExtra(ChoosePublicKeyDialogFragment.KEY_ATTACHMENT_INFO_LIST)
                ?: return
            val key = keyList.first()
            if (atts?.none { it.name == key.name && it.encodedSize == key.encodedSize } == true) {
              atts.add(key)
              showAtts()
            }
          }
        }
      }

      REQUEST_CODE_SHOW_FIX_EMPTY_PASSPHRASE_DIALOG -> when (resultCode) {
        FixNeedPassphraseIssueDialogFragment.RESULT_OK -> {
          sendMsg()
        }
      }

      else -> super.onActivityResult(requestCode, resultCode, data)
    }
  }

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    super.onCreateOptionsMenu(menu, inflater)
    inflater.inflate(R.menu.fragment_compose, menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.menuActionSend -> {
        snackBar?.dismiss()

        if (isUpdateToCompleted && isUpdateCcCompleted && isUpdateBccCompleted) {
          UIUtil.hideSoftInput(context, view)
          if (isDataCorrect) {
            if (listener.msgEncryptionType == MessageEncryptionType.ENCRYPTED) {
              val keysStorage = KeysStorageImpl.getInstance(requireContext())
              val senderEmail = editTextFrom?.text.toString()
              val keyRings = keysStorage.getPGPSecretKeyRingsByUserId(senderEmail)
              if (keyRings.isNotEmpty()) {
                val firstMatchedSecretKey = keyRings.first()
                val openPgpV4Fingerprint = OpenPgpV4Fingerprint(firstMatchedSecretKey)
                val fingerprint = openPgpV4Fingerprint.toString()
                val passphrase = keysStorage.getPassphraseByFingerprint(fingerprint)
                if (passphrase?.isEmpty == true) {
                  showNeedPassphraseDialog(
                    listOf(fingerprint),
                    REQUEST_CODE_SHOW_FIX_EMPTY_PASSPHRASE_DIALOG
                  )
                  return true
                }
              }
            }

            sendMsg()
            this.isMsgSentToQueue = true
          }
        } else {
          Toast.makeText(
            context, R.string.please_wait_while_information_about_contacts_will_be_updated,
            Toast.LENGTH_SHORT
          ).show()
        }
        return true
      }

      R.id.menuActionAttachFile -> {
        attachFile()
        return true
      }

      R.id.menuActionIncludePubKey -> {
        showPubKeyDialog()
        return true
      }

      else -> return super.onOptionsItemSelected(item)
    }
  }

  override fun onCreateContextMenu(
    menu: ContextMenu,
    v: View,
    menuInfo: ContextMenu.ContextMenuInfo?
  ) {
    super.onCreateContextMenu(menu, v, menuInfo)
    when (v.id) {
      R.id.iBShowQuotedText -> {
        val inflater: MenuInflater = requireActivity().menuInflater
        inflater.inflate(R.menu.context_menu_delete_quoted_text, menu)
      }
    }
  }

  override fun onContextItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.menuDeleteQuotedText -> {
        iBShowQuotedText?.visibility = View.GONE
        msgInfo?.text = ""
        true
      }

      else -> super.onContextItemSelected(item)
    }
  }

  override fun onFocusChange(v: View, hasFocus: Boolean) {
    when (v.id) {
      R.id.editTextRecipientTo -> runUpdatePgpContactsAction(
        pgpContactsTo, progressBarTo,
        ContactEntity.Type.TO, hasFocus
      )

      R.id.editTextRecipientCc -> runUpdatePgpContactsAction(
        pgpContactsCc, progressBarCc,
        ContactEntity.Type.CC, hasFocus
      )

      R.id.editTextRecipientBcc -> runUpdatePgpContactsAction(
        pgpContactsBcc, progressBarBcc,
        ContactEntity.Type.BCC, hasFocus
      )

      R.id.editTextEmailSubject, R.id.editTextEmailMessage -> if (hasFocus) {
        var isExpandButtonNeeded = false
        if (TextUtils.isEmpty(recipientsCc!!.text)) {
          layoutCc?.visibility = View.GONE
          isExpandButtonNeeded = true
        }

        if (TextUtils.isEmpty(recipientsBcc!!.text)) {
          layoutBcc?.visibility = View.GONE
          isExpandButtonNeeded = true
        }

        if (isExpandButtonNeeded) {
          imageButtonAdditionalRecipientsVisibility?.visibility = View.VISIBLE
          val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
          )
          layoutParams.gravity = Gravity.TOP or Gravity.END
          progressBarAndButtonLayout?.layoutParams = layoutParams
        }
      }
    }
  }

  override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
    when (parent?.id) {
      R.id.spinnerFrom -> {
        editTextFrom?.setText(parent.adapter.getItem(position) as CharSequence)
        if (listener.msgEncryptionType === MessageEncryptionType.ENCRYPTED) {
          val adapter = parent.adapter as ArrayAdapter<*>
          val colorGray = UIUtil.getColor(requireContext(), R.color.gray)
          editTextFrom?.setTextColor(if (adapter.isEnabled(position)) originalColor else colorGray)
        } else {
          editTextFrom?.setTextColor(originalColor)
        }
      }
    }
  }

  override fun onNothingSelected(parent: AdapterView<*>) {

  }

  override fun onClick(v: View) {
    when (v.id) {
      R.id.imageButtonAliases -> if (fromAddrs?.count != 1 && fromAddrs?.count != 0) {
        spinnerFrom?.performClick()
      }

      R.id.imageButtonAdditionalRecipientsVisibility -> {
        layoutCc?.visibility = View.VISIBLE
        layoutBcc?.visibility = View.VISIBLE
        val layoutParams = FrameLayout.LayoutParams(
          FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
        layoutParams.gravity = Gravity.TOP or Gravity.END

        progressBarAndButtonLayout?.layoutParams = layoutParams
        v.visibility = View.GONE
        recipientsCc?.requestFocus()
      }

      R.id.iBShowQuotedText -> {
        val currentCursorPosition = editTextEmailMsg?.selectionStart ?: 0
        if (editTextEmailMsg?.text?.isNotEmpty() == true) {
          editTextEmailMsg?.append("\n" + EmailUtil.genReplyContent(msgInfo))
        } else {
          editTextEmailMsg?.append(EmailUtil.genReplyContent(msgInfo))
        }
        editTextEmailMsg?.setSelection(currentCursorPosition)
        v.visibility = View.GONE
      }
    }
  }

  override fun onChipLongClick(nachoTextView: NachoTextView, chip: Chip, event: MotionEvent) {}

  override fun onAccountInfoRefreshed(accountEntity: AccountEntity?) {
    super.onAccountInfoRefreshed(accountEntity)
    accountEntity?.email?.let { email ->
      if (fromAddrs?.objects?.contains(email) == false) {
        fromAddrs?.add(email)
      }
    }
  }

  fun onMsgEncryptionTypeChange(messageEncryptionType: MessageEncryptionType?) {
    var emailMassageHint: String? = null
    if (messageEncryptionType != null) {
      when (messageEncryptionType) {
        MessageEncryptionType.ENCRYPTED -> {
          emailMassageHint = getString(R.string.prompt_compose_security_email)
          recipientsTo?.onFocusChangeListener?.onFocusChange(recipientsTo, false)
          recipientsCc?.onFocusChangeListener?.onFocusChange(recipientsCc, false)
          recipientsBcc?.onFocusChangeListener?.onFocusChange(recipientsBcc, false)
          fromAddrs?.setUseKeysInfo(true)

          val colorGray = UIUtil.getColor(requireContext(), R.color.gray)
          val selectedItemPosition = spinnerFrom?.selectedItemPosition
          if (selectedItemPosition != null && selectedItemPosition != AdapterView.INVALID_POSITION
            && spinnerFrom?.adapter?.count ?: 0 > selectedItemPosition
          ) {
            val isItemEnabled = fromAddrs?.isEnabled(selectedItemPosition) ?: true
            editTextFrom!!.setTextColor(if (isItemEnabled) originalColor else colorGray)
          }
        }

        MessageEncryptionType.STANDARD -> {
          emailMassageHint = getString(R.string.prompt_compose_standard_email)
          pgpContactsTo?.clear()
          pgpContactsCc?.clear()
          pgpContactsBcc?.clear()
          isUpdateToCompleted = true
          isUpdateCcCompleted = true
          isUpdateBccCompleted = true
          fromAddrs?.setUseKeysInfo(false)
          editTextFrom?.setTextColor(originalColor)
        }
      }
    }
    textInputLayoutMsg?.hint = emailMassageHint
  }

  private fun attachFile() {
    val intent = Intent()
    intent.action = Intent.ACTION_OPEN_DOCUMENT
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    intent.type = "*/*"
    startActivityForResult(
      Intent.createChooser(intent, getString(R.string.choose_attachment)),
      REQUEST_CODE_GET_CONTENT_FOR_SENDING
    )
  }

  private fun initExtras(intent: Intent?) {
    if (intent != null) {
      if (intent.hasExtra(CreateMessageActivity.EXTRA_KEY_MESSAGE_TYPE)) {
        this.messageType =
          intent.getSerializableExtra(CreateMessageActivity.EXTRA_KEY_MESSAGE_TYPE) as MessageType
      }

      if (!TextUtils.isEmpty(intent.action) && intent.action?.startsWith("android.intent.action") == true) {
        this.extraActionInfo = ExtraActionInfo.parseExtraActionInfo(requireContext(), intent)
        addAtts()
      } else {
        this.serviceInfo = intent.getParcelableExtra(CreateMessageActivity.EXTRA_KEY_SERVICE_INFO)
        this.msgInfo =
          intent.getParcelableExtra(CreateMessageActivity.EXTRA_KEY_INCOMING_MESSAGE_INFO)

        if (msgInfo != null && msgInfo!!.localFolder != null) {
          this.folderType = FoldersManager.getFolderType(msgInfo!!.localFolder)
        }

        if (this.serviceInfo != null && this.serviceInfo!!.atts != null) {
          atts?.addAll(this.serviceInfo!!.atts!!)
        }
      }
    }
  }

  private fun initDraftCacheDir(context: Context) {
    draftCacheDir = File(context.cacheDir, Constants.DRAFT_CACHE_DIR)

    if (draftCacheDir.exists()) {
      if (!draftCacheDir.mkdir()) {
        Log.e(TAG, "Create cache directory " + draftCacheDir.name + " filed!")
      }
    }
  }

  private fun addAtts() {
    val sizeWarningMsg = getString(
      R.string.template_warning_max_total_attachments_size,
      FileUtils.byteCountToDisplaySize(Constants.MAX_TOTAL_ATTACHMENT_SIZE_IN_BYTES)
    )

    extraActionInfo?.atts?.forEach { attachmentInfo ->
      if (ContentResolver.SCHEME_FILE.equals(attachmentInfo.uri?.scheme, ignoreCase = true)) {
        // we skip attachments that have SCHEME_FILE as deprecated
        return
      }

      if (hasAbilityToAddAtt(attachmentInfo)) {

        if (attachmentInfo.name.isNullOrEmpty()) {
          val msg = "attachmentInfo.getName() == null, uri = " + attachmentInfo.uri!!
          ExceptionUtil.handleError(NullPointerException(msg))
          return
        }

        val fileName = attachmentInfo.getSafeName()
        var draftAtt = File(draftCacheDir, fileName)

        draftAtt = if (draftAtt.exists()) {
          FileAndDirectoryUtils.createFileWithIncreasedIndex(draftCacheDir, fileName)
        } else {
          draftAtt
        }

        try {
          val inputStream = requireContext().contentResolver.openInputStream(attachmentInfo.uri!!)

          if (inputStream != null) {
            FileUtils.copyInputStreamToFile(inputStream, draftAtt)
            val uri = FileProvider.getUriForFile(
              requireContext(),
              Constants.FILE_PROVIDER_AUTHORITY,
              draftAtt
            )
            attachmentInfo.uri = uri
            atts?.add(attachmentInfo)
          }
        } catch (e: IOException) {
          e.printStackTrace()
          ExceptionUtil.handleError(e)

          if (!draftAtt.delete()) {
            Log.e(TAG, "Delete " + draftAtt.name + " filed!")
          }
        }

      } else {
        Toast.makeText(context, sizeWarningMsg, Toast.LENGTH_SHORT).show()
        return@forEach
      }
    }
  }

  private fun updateRecipients() {
    recipientsTo?.chipAndTokenValues?.let {
      contactsViewModel.fetchAndUpdateInfoAboutContacts(
        ContactEntity.Type.TO,
        it
      )
    }

    if (layoutCc?.visibility == View.VISIBLE) {
      recipientsCc?.chipAndTokenValues?.let {
        contactsViewModel.fetchAndUpdateInfoAboutContacts(
          ContactEntity.Type.CC,
          it
        )
      }
    } else {
      recipientsCc?.setText(null as CharSequence?)
      pgpContactsCc?.clear()
    }

    if (layoutBcc?.visibility == View.VISIBLE) {
      recipientsBcc?.chipAndTokenValues?.let {
        contactsViewModel.fetchAndUpdateInfoAboutContacts(
          ContactEntity.Type.BCC,
          it
        )
      }
    } else {
      recipientsBcc?.setText(null as CharSequence?)
      pgpContactsBcc?.clear()
    }
  }

  /**
   * Run an action to update information about some [PgpContact]s.
   *
   * @param pgpContacts Old [PgpContact]s
   * @param progressBar A [ProgressBar] which is showing an action progress.
   * @param type        A type of contacts
   * @param hasFocus    A value which indicates the view focus.
   * @return A modified contacts list.
   */
  private fun runUpdatePgpContactsAction(
    pgpContacts: MutableList<PgpContact>?, progressBar: View?,
    type: ContactEntity.Type, hasFocus: Boolean
  ): List<PgpContact>? {
    if (listener.msgEncryptionType === MessageEncryptionType.ENCRYPTED) {
      progressBar?.visibility = if (hasFocus) View.INVISIBLE else View.VISIBLE
      if (hasFocus) {
        pgpContacts?.clear()
      } else {
        if (isContactsUpdateEnabled) {
          if (isAdded) {
            fetchDetailsAboutContacts(type)
          }
        } else {
          progressBar?.visibility = View.INVISIBLE
        }
      }
    }

    return pgpContacts
  }

  private fun fetchDetailsAboutContacts(type: ContactEntity.Type) {
    when (type) {
      ContactEntity.Type.TO -> {
        recipientsTo?.chipAndTokenValues?.let {
          contactsViewModel.fetchAndUpdateInfoAboutContacts(ContactEntity.Type.TO, it)
        }
      }

      ContactEntity.Type.CC -> {
        recipientsCc?.chipAndTokenValues?.let {
          contactsViewModel.fetchAndUpdateInfoAboutContacts(ContactEntity.Type.CC, it)
        }
      }

      ContactEntity.Type.BCC -> {
        recipientsBcc?.chipAndTokenValues?.let {
          contactsViewModel.fetchAndUpdateInfoAboutContacts(ContactEntity.Type.BCC, it)
        }
      }
    }
  }

  /**
   * Prepare an alias for the reply. Will be used the email address that the email was received. Will be used the
   * first found matched email.
   *
   * @param aliases A list of Gmail aliases.
   */
  private fun prepareAliasForReplyIfNeeded(aliases: List<String>) {
    val messageEncryptionType = listener.msgEncryptionType

    val toAddresses: List<InternetAddress>? = if (folderType === FoldersManager.FolderType.SENT) {
      msgInfo?.getFrom()
    } else {
      msgInfo?.getTo()
    }

    if (!CollectionUtils.isEmpty(toAddresses)) {
      var firstFoundedAlias: String? = null
      for (toAddress in toAddresses!!) {
        if (firstFoundedAlias == null) {
          for (alias in aliases) {
            if (alias.equals(toAddress.address, ignoreCase = true)) {
              firstFoundedAlias = if (messageEncryptionType === MessageEncryptionType.ENCRYPTED
                && fromAddrs?.hasPrvKey(alias) == true
              ) {
                alias
              } else {
                alias
              }
              break
            }
          }
        } else {
          break
        }
      }

      if (firstFoundedAlias != null) {
        val position = fromAddrs?.getPosition(firstFoundedAlias) ?: Spinner.INVALID_POSITION
        if (position != Spinner.INVALID_POSITION) {
          spinnerFrom?.setSelection(position)
        }
      }
    }
  }

  private fun showFirstMatchedAliasWithPrvKey(aliases: List<String>) {
    var firstFoundedAliasWithPrvKey: String? = null
    for (alias in aliases) {
      if (fromAddrs?.hasPrvKey(alias) == true) {
        firstFoundedAliasWithPrvKey = alias
        break
      }
    }

    if (firstFoundedAliasWithPrvKey != null) {
      val position = fromAddrs?.getPosition(firstFoundedAliasWithPrvKey) ?: Spinner.INVALID_POSITION
      if (position != Spinner.INVALID_POSITION) {
        spinnerFrom?.setSelection(position)
      }
    }
  }

  /**
   * Check that all recipients have PGP.
   *
   * @return true if all recipients have PGP, other wise false.
   */
  private fun hasRecipientWithoutPgp(
    isRemoveActionEnabled: Boolean,
    vararg pgpContactsList: List<PgpContact>?
  ): Boolean {
    for (sublist in pgpContactsList) {
      sublist?.let {
        for (pgpContact in it) {
          if (!pgpContact.hasPgp) {
            showNoPgpFoundDialog(pgpContact, isRemoveActionEnabled)
            return true
          }
        }
      }
    }

    return false
  }

  /**
   * Check that all recipients have not-expired pub keys.
   *
   * @return true if all recipients have not-expired pub keys, other wise false.
   */
  private fun hasRecipientWithExpiredPubKey(vararg pgpContactsList: List<PgpContact>?): Boolean {
    for (sublist in pgpContactsList) {
      sublist?.let {
        for (pgpContact in it) {
          if (pgpContact.pgpKeyDetails?.isExpired == true) {
            showInfoDialog(dialogMsg = getString(R.string.warning_one_of_pub_keys_is_expired))
            return true
          }
        }
      }
    }

    return false
  }

  /**
   * This method does update chips in the recipients field.
   *
   * @param view        A view which contains input [PgpContact](s).
   * @param pgpContacts The input [PgpContact](s)
   */
  private fun updateChips(view: PgpContactsNachoTextView?, pgpContacts: List<PgpContact>?) {
    view ?: return
    val builder = SpannableStringBuilder(view.text)

    val pgpContactChipSpans = builder.getSpans(0, view.length(), PGPContactChipSpan::class.java)

    if (pgpContactChipSpans.isNotEmpty()) {
      for (pgpContact in pgpContacts ?: emptyList()) {
        for (pgpContactChipSpan in pgpContactChipSpans) {
          if (pgpContact.email.equals(pgpContactChipSpan.text.toString(), ignoreCase = true)) {
            pgpContactChipSpan.hasPgp = pgpContact.hasPgp
            pgpContactChipSpan.isExpired = pgpContact.pgpKeyDetails?.isExpired
            break
          }
        }
      }
      view.invalidateChips()
    }
  }

  /**
   * Init an input [NachoTextView] using custom settings.
   *
   * @param pgpContactsNachoTextView An input [NachoTextView]
   */
  private fun initChipsView(pgpContactsNachoTextView: PgpContactsNachoTextView?) {
    pgpContactsNachoTextView?.setNachoValidator(ChipifyingNachoValidator())
    pgpContactsNachoTextView?.setIllegalCharacterIdentifier { character -> character == ',' }
    pgpContactsNachoTextView?.addChipTerminator(
      ' ', ChipTerminatorHandler
        .BEHAVIOR_CHIPIFY_TO_TERMINATOR
    )
    pgpContactsNachoTextView?.chipTokenizer = SingleCharacterSpanChipTokenizer(
      requireContext(),
      CustomChipSpanChipCreator(requireContext()), PGPContactChipSpan::class.java
    )
    pgpContactsNachoTextView?.setAdapter(preparePgpContactAdapter())
    pgpContactsNachoTextView?.onFocusChangeListener = this
    pgpContactsNachoTextView?.setListener(this)
  }

  private fun hasExternalStorageUris(attachmentInfoList: List<AttachmentInfo>?): Boolean {
    attachmentInfoList?.let {
      for (att in it) {
        if (ContentResolver.SCHEME_FILE.equals(att.uri?.scheme, ignoreCase = true)) {
          return true
        }
      }
    }
    return false
  }

  /**
   * Remove the current [PgpContact] from recipients.
   *
   * @param pgpContact               The [PgpContact] which will be removed.
   * @param pgpContactsNachoTextView The [NachoTextView] which contains the delete candidate.
   * @param pgpContacts              The list which contains the delete candidate.
   */
  private fun removePgpContact(
    pgpContact: PgpContact, pgpContactsNachoTextView: PgpContactsNachoTextView?,
    pgpContacts: MutableList<PgpContact>?
  ) {
    val chipTokenizer = pgpContactsNachoTextView?.chipTokenizer
    pgpContactsNachoTextView?.allChips?.let {
      for (chip in it) {
        if (pgpContact.email.equals(
            chip.text.toString(),
            ignoreCase = true
          ) && chipTokenizer != null
        ) {
          chipTokenizer.deleteChip(chip, pgpContactsNachoTextView.text)
        }
      }
    }

    val iterator = pgpContacts?.iterator()
    while (iterator?.hasNext() == true) {
      val next = iterator.next()
      if (next.email.equals(next.email, ignoreCase = true)) {
        iterator.remove()
      }
    }
  }

  /**
   * Init fragment views
   *
   * @param view The root fragment view.
   */
  private fun initViews(view: View) {
    layoutContent = view.findViewById(R.id.scrollView)
    layoutAtts = view.findViewById(R.id.layoutAtts)
    layoutCc = view.findViewById(R.id.layoutCc)
    layoutBcc = view.findViewById(R.id.layoutBcc)
    progressBarAndButtonLayout = view.findViewById(R.id.progressBarAndButtonLayout)

    recipientsTo = view.findViewById(R.id.editTextRecipientTo)
    recipientsCc = view.findViewById(R.id.editTextRecipientCc)
    recipientsBcc = view.findViewById(R.id.editTextRecipientBcc)

    initChipsView(recipientsTo)
    initChipsView(recipientsCc)
    initChipsView(recipientsBcc)

    spinnerFrom = view.findViewById(R.id.spinnerFrom)
    spinnerFrom?.onItemSelectedListener = this
    spinnerFrom?.adapter = fromAddrs

    editTextFrom = view.findViewById(R.id.editTextFrom)
    originalColor = editTextFrom!!.currentTextColor

    imageButtonAliases = view.findViewById(R.id.imageButtonAliases)
    imageButtonAliases?.setOnClickListener(this)

    imageButtonAdditionalRecipientsVisibility =
      view.findViewById(R.id.imageButtonAdditionalRecipientsVisibility)
    imageButtonAdditionalRecipientsVisibility?.setOnClickListener(this)

    editTextEmailSubject = view.findViewById(R.id.editTextEmailSubject)
    editTextEmailSubject?.onFocusChangeListener = this
    editTextEmailMsg = view.findViewById(R.id.editTextEmailMessage)
    editTextEmailMsg?.onFocusChangeListener = this
    textInputLayoutMsg = view.findViewById(R.id.textInputLayoutEmailMessage)
    iBShowQuotedText = view.findViewById(R.id.iBShowQuotedText)
    iBShowQuotedText?.setOnClickListener(this)

    progressBarTo = view.findViewById(R.id.progressBarTo)
    progressBarCc = view.findViewById(R.id.progressBarCc)
    progressBarBcc = view.findViewById(R.id.progressBarBcc)
  }

  private fun showContent() {
    UIUtil.exchangeViewVisibility(false, progressView, contentView)

    if ((msgInfo != null || extraActionInfo != null) && !isIncomingMsgInfoUsed) {
      this.isIncomingMsgInfoUsed = true
      updateViews()
    }

    showAtts()
    hostActivity?.countingIdlingResource?.decrementSafely("showContent")
  }

  /**
   * Update views on the screen. This method can be called when we need to update the current
   * screen.
   */
  private fun updateViews() {
    onMsgEncryptionTypeChange(listener.msgEncryptionType)

    if (extraActionInfo != null) {
      updateViewsFromExtraActionInfo()
    } else {
      if (msgInfo != null) {
        updateViewsFromIncomingMsgInfo()
        recipientsTo?.chipifyAllUnterminatedTokens()
        recipientsCc?.chipifyAllUnterminatedTokens()
        editTextEmailSubject?.setText(prepareReplySubject(msgInfo?.getSubject() ?: ""))
      }

      if (serviceInfo != null) {
        updateViewsFromServiceInfo()
      }
    }
  }

  private fun updateViewsFromExtraActionInfo() {
    setupPgpFromExtraActionInfo(recipientsTo, extraActionInfo!!.toAddresses.toTypedArray())
    setupPgpFromExtraActionInfo(recipientsCc, extraActionInfo!!.ccAddresses.toTypedArray())
    setupPgpFromExtraActionInfo(recipientsBcc, extraActionInfo!!.bccAddresses.toTypedArray())

    editTextEmailSubject?.setText(extraActionInfo!!.subject)
    editTextEmailMsg?.setText(extraActionInfo!!.body)

    if (recipientsTo?.text?.isEmpty() == true) {
      recipientsTo?.requestFocus()
      return
    }

    if (editTextEmailSubject?.text?.isEmpty() == true) {
      editTextEmailSubject?.requestFocus()
      return
    }

    editTextEmailMsg?.requestFocus()
    editTextEmailMsg?.showKeyboard()
  }

  private fun updateViewsFromServiceInfo() {
    recipientsTo?.isFocusable = serviceInfo!!.isToFieldEditable
    recipientsTo?.isFocusableInTouchMode = serviceInfo!!.isToFieldEditable
    //todo-denbond7 Need to add a similar option for recipientsCc and recipientsBcc

    editTextEmailSubject?.isFocusable = serviceInfo?.isSubjectEditable ?: false
    editTextEmailSubject?.isFocusableInTouchMode = serviceInfo?.isSubjectEditable ?: false

    editTextEmailMsg?.isFocusable = serviceInfo?.isMsgEditable ?: false
    editTextEmailMsg?.isFocusableInTouchMode = serviceInfo?.isMsgEditable ?: false

    if (serviceInfo?.systemMsg?.isNotEmpty() == true) {
      editTextEmailMsg?.setText(serviceInfo?.systemMsg)
    }
  }

  private fun updateViewsFromIncomingMsgInfo() {
    iBShowQuotedText?.visibility = View.VISIBLE
    iBShowQuotedText?.let { registerForContextMenu(it) }

    when (messageType) {
      MessageType.REPLY -> updateViewsIfReplyMode()

      MessageType.REPLY_ALL -> updateViewsIfReplyAllMode()

      MessageType.FORWARD -> updateViewsIfFwdMode()

      else -> {
      }
    }
  }

  private fun updateViewsIfFwdMode() {
    val originalMsgInfo = msgInfo ?: return

    if (!CollectionUtils.isEmpty(originalMsgInfo.atts)) {
      for (att in originalMsgInfo.atts!!) {
        if (hasAbilityToAddAtt(att)) {
          atts?.add(att)
        } else {
          showInfoSnackbar(
            requireView(), getString(
              R.string.template_warning_max_total_attachments_size,
              FileUtils.byteCountToDisplaySize(Constants.MAX_TOTAL_ATTACHMENT_SIZE_IN_BYTES)
            ),
            Snackbar.LENGTH_LONG
          )
        }
      }
    }

    editTextEmailMsg?.setText(
      getString(
        R.string.forward_template,
        originalMsgInfo.getFrom().first().address ?: "",
        EmailUtil.genForwardedMsgDate(originalMsgInfo.getReceiveDate()),
        originalMsgInfo.getSubject(),
        prepareRecipientsLineForForwarding(originalMsgInfo.getTo())
      )
    )

    if (!CollectionUtils.isEmpty(originalMsgInfo.getCc())) {
      editTextEmailMsg?.append("Cc: ")
      editTextEmailMsg?.append(prepareRecipientsLineForForwarding(originalMsgInfo.getCc()))
      editTextEmailMsg?.append("\n\n")
    }

    editTextEmailMsg?.append("\n\n" + originalMsgInfo.text)
  }

  private fun updateViewsIfReplyAllMode() {
    if (folderType === FoldersManager.FolderType.SENT || folderType === FoldersManager.FolderType.OUTBOX) {
      recipientsTo?.setText(prepareRecipients(msgInfo?.getTo()))

      if (msgInfo?.getCc()?.isNotEmpty() == true) {
        layoutCc?.visibility = View.VISIBLE
        recipientsCc?.append(prepareRecipients(msgInfo?.getCc()))
      }
    } else {
      recipientsTo?.setText(
        prepareRecipients(
          if (msgInfo?.getReplyTo().isNullOrEmpty()) {
            msgInfo?.getFrom()
          } else {
            msgInfo?.getReplyTo()
          }
        )
      )

      val ccSet = HashSet<InternetAddress>()

      if (msgInfo?.getTo()?.isNotEmpty() == true) {
        for (address in msgInfo!!.getTo()) {
          if (!account?.email.equals(address.address, ignoreCase = true)) {
            ccSet.add(address)
          }
        }

        accountAliasesViewModel.accountAliasesLiveData.value?.let {
          for (alias in it) {
            val iterator = ccSet.iterator()

            while (iterator.hasNext()) {
              if (iterator.next().address.equals(alias.sendAsEmail, ignoreCase = true)) {
                iterator.remove()
              }
            }
          }
        }
      }

      if (msgInfo?.getCc()?.isNotEmpty() == true) {
        for (address in msgInfo!!.getCc()) {
          if (!account?.email.equals(address.address, ignoreCase = true)) {
            ccSet.add(address)
          }
        }
      }

      if (ccSet.isNotEmpty()) {
        layoutCc?.visibility = View.VISIBLE
        recipientsCc?.append(prepareRecipients(ccSet))
      }
    }

    if (recipientsTo?.text?.isNotEmpty() == true || recipientsCc?.text?.isNotEmpty() == true) {
      editTextEmailMsg?.requestFocus()
      editTextEmailMsg?.showKeyboard()
    }
  }

  private fun updateViewsIfReplyMode() {
    if (folderType != null) {
      when (folderType) {
        FoldersManager.FolderType.SENT,
        FoldersManager.FolderType.OUTBOX -> recipientsTo?.setText(prepareRecipients(msgInfo?.getTo()))

        else -> recipientsTo?.setText(
          prepareRecipients(
            if (msgInfo?.getReplyTo().isNullOrEmpty()) {
              msgInfo?.getFrom()
            } else {
              msgInfo?.getReplyTo()
            }
          )
        )
      }
    } else {
      recipientsTo?.setText(prepareRecipients(msgInfo?.getFrom()))
    }

    if (recipientsTo?.text?.isNotEmpty() == true) {
      editTextEmailMsg?.requestFocus()
      editTextEmailMsg?.showKeyboard()
    }
  }

  private fun setupPgpFromExtraActionInfo(
    pgpContactsNachoTextView: PgpContactsNachoTextView?,
    addresses: Array<String>?
  ) {
    if (addresses?.isNotEmpty() == true) {
      pgpContactsNachoTextView?.setText(prepareRecipients(addresses))
      pgpContactsNachoTextView?.chipifyAllUnterminatedTokens()
      pgpContactsNachoTextView?.onFocusChangeListener?.onFocusChange(
        pgpContactsNachoTextView,
        false
      )
    }
  }

  private fun prepareRecipientsLineForForwarding(recipients: List<InternetAddress>?): String {
    val stringBuilder = StringBuilder()
    return if (!CollectionUtils.isEmpty(recipients)) {
      stringBuilder.append(recipients!![0])

      if (recipients.size > 1) {
        for (i in 1 until recipients.size) {
          val recipient = recipients[0].address
          stringBuilder.append(", ")
          stringBuilder.append(recipient)
        }
      }

      stringBuilder.toString()
    } else
      ""
  }

  private fun prepareReplySubject(subject: String): String {
    val prefix = when (messageType) {
      MessageType.REPLY, MessageType.REPLY_ALL -> "Re"
      MessageType.FORWARD -> "Fwd"
      else -> return subject
    }
    val prefixMatcher = Pattern.compile("^($prefix: )", Pattern.CASE_INSENSITIVE).matcher(subject)
    return if (prefixMatcher.find()) subject else getString(
      R.string.template_reply_subject,
      prefix,
      subject
    )
  }

  private fun prepareRecipients(recipients: Array<String>?): String {
    val stringBuilder = StringBuilder()
    if (recipients != null && recipients.isNotEmpty()) {
      for (s in recipients) {
        stringBuilder.append(s).append(" ")
      }
    }

    return stringBuilder.toString()
  }

  private fun prepareRecipients(recipients: Collection<InternetAddress>?): String {
    val stringBuilder = StringBuilder()
    if (!CollectionUtils.isEmpty(recipients)) {
      for (s in recipients!!) {
        stringBuilder.append(s.address).append(" ")
      }
    }

    return stringBuilder.toString()
  }

  /**
   * Prepare a [PgpContactAdapter] for the [NachoTextView] object.
   *
   * @return <tt>[PgpContactAdapter]</tt>
   */
  @SuppressLint("Recycle")
  private fun preparePgpContactAdapter(): PgpContactAdapter {
    val pgpContactAdapter = PgpContactAdapter(requireContext(), null, true)
    //setup a search contacts logic in the database
    pgpContactAdapter.filterQueryProvider = FilterQueryProvider { constraint ->
      val dao = FlowCryptRoomDatabase.getDatabase(requireContext()).contactsDao()
      dao.getFilteredCursor("%$constraint%")
    }

    return pgpContactAdapter
  }

  /**
   * Check if the given [pgpContactsNachoTextViews] List has an invalid email.
   *
   * @return <tt>boolean</tt> true - if has, otherwise false..
   */
  private fun hasInvalidEmail(vararg pgpContactsNachoTextViews: PgpContactsNachoTextView?): Boolean {
    for (textView in pgpContactsNachoTextViews) {
      val emails = textView?.chipAndTokenValues
      if (emails != null) {
        for (email in emails) {
          if (!GeneralUtil.isEmailValid(email)) {
            showInfoSnackbar(textView, getString(R.string.error_some_email_is_not_valid, email))
            textView.requestFocus()
            return true
          }
        }
      }
    }
    return false
  }

  /**
   * Check is attachment can be added to the current message.
   *
   * @param newAttInfo The new attachment which will be maybe added.
   * @return true if the attachment can be added, otherwise false.
   */
  private fun hasAbilityToAddAtt(newAttInfo: AttachmentInfo?): Boolean {
    val existedAttsSize = (atts?.map { it.encodedSize.toInt() }?.sum() ?: 0)
    val newAttSize = newAttInfo?.encodedSize?.toInt() ?: 0
    return (existedAttsSize + newAttSize) < Constants.MAX_TOTAL_ATTACHMENT_SIZE_IN_BYTES
  }


  /**
   * Show a dialog where we can select different actions.
   *
   * @param pgpContact            The [PgpContact] which will be used when we select the remove action.
   * @param isRemoveActionEnabled true if we want to show the remove action, false otherwise.
   */
  private fun showNoPgpFoundDialog(pgpContact: PgpContact, isRemoveActionEnabled: Boolean) {
    val dialogFragment = NoPgpFoundDialogFragment.newInstance(pgpContact, isRemoveActionEnabled)
    dialogFragment.setTargetFragment(this, REQUEST_CODE_NO_PGP_FOUND_DIALOG)
    dialogFragment.show(parentFragmentManager, NoPgpFoundDialogFragment::class.java.simpleName)
  }

  /**
   * Send a message.
   */
  private fun sendMsg() {
    dismissCurrentSnackBar()

    isContactsUpdateEnabled = false
    onMsgSendListener.sendMsg(getOutgoingMsgInfo())
  }

  /**
   * Show attachments which were added.
   */
  private fun showAtts() {
    if (atts?.isNotEmpty() == true) {
      layoutAtts?.removeAllViews()
      val layoutInflater = LayoutInflater.from(context)
      for (att in atts) {
        val rootView = layoutInflater.inflate(R.layout.attachment_item, layoutAtts, false)

        val textViewAttName = rootView.findViewById<TextView>(R.id.textViewAttachmentName)
        textViewAttName.text = att.name

        val textViewAttSize = rootView.findViewById<TextView>(R.id.textViewAttSize)
        if (att.encodedSize > 0) {
          textViewAttSize.visibility = View.VISIBLE
          textViewAttSize.text = Formatter.formatFileSize(context, att.encodedSize)
        } else {
          textViewAttSize.visibility = View.GONE
        }

        val imageButtonDownloadAtt = rootView.findViewById<View>(R.id.imageButtonDownloadAtt)

        if (!att.isProtected) {
          imageButtonDownloadAtt.visibility = View.GONE
          val imageButtonClearAtt = rootView.findViewById<View>(R.id.imageButtonClearAtt)
          imageButtonClearAtt.visibility = View.VISIBLE
          imageButtonClearAtt.setOnClickListener {
            atts.remove(att)
            layoutAtts?.removeView(rootView)

            //Remove a temp file which was created by our app
            val uri = att.uri
            if (uri != null && Constants.FILE_PROVIDER_AUTHORITY.equals(
                uri.authority!!,
                ignoreCase = true
              )
            ) {
              context?.contentResolver?.delete(uri, null, null)
            }
          }
        } else {
          imageButtonDownloadAtt.visibility = View.INVISIBLE
        }
        layoutAtts?.addView(rootView)
      }
    } else {
      layoutAtts?.removeAllViews()
    }
  }

  /**
   * Show a dialog where the user can select a public key which will be attached to a message.
   */
  private fun showPubKeyDialog() {
    if (!account?.email.isNullOrEmpty()) {
      val fragment = ChoosePublicKeyDialogFragment.newInstance(
        account?.email!!, ListView.CHOICE_MODE_SINGLE, R.plurals.choose_pub_key, true
      )
      fragment.setTargetFragment(this@CreateMessageFragment, REQUEST_CODE_SHOW_PUB_KEY_DIALOG)
      fragment.show(parentFragmentManager, ChoosePublicKeyDialogFragment::class.java.simpleName)
    }
  }

  private fun setupAccountAliasesViewModel() {
    accountAliasesViewModel.fetchUpdates(viewLifecycleOwner)
    hostActivity?.countingIdlingResource?.incrementSafely()
    accountAliasesViewModel.accountAliasesLiveData.observe(viewLifecycleOwner, {
      val aliases = ArrayList<String>()
      accountAliasesViewModel.activeAccountLiveData.value?.let { accountEntity ->
        aliases.add(accountEntity.email)
      }

      for (accountAlias in it) {
        aliases.add(accountAlias.sendAsEmail)
      }

      fromAddrs?.clear()
      fromAddrs?.addAll(aliases)

      updateFromAddressAdapter(
        KeysStorageImpl.getInstance(requireContext()).getPGPSecretKeyRings().map { key ->
          key.toPgpKeyDetails()
        })

      if (msgInfo != null) {
        prepareAliasForReplyIfNeeded(aliases)
      } else if (listener.msgEncryptionType === MessageEncryptionType.ENCRYPTED) {
        showFirstMatchedAliasWithPrvKey(aliases)
      }

      if (serviceInfo != null) {
        serviceInfo?.let { serviceInfo ->
          if (serviceInfo.isFromFieldEditable) {
            imageButtonAliases?.visibility = View.VISIBLE
          } else {
            imageButtonAliases?.visibility = View.INVISIBLE
          }
        }
      } else {
        fromAddrs?.count?.let { count: Int ->
          if (count in 0..1) {
            if (imageButtonAliases?.visibility == View.VISIBLE) {
              imageButtonAliases?.visibility = View.INVISIBLE
            }
          } else {
            imageButtonAliases?.visibility = View.VISIBLE
          }
        }
      }

      showContent()
    })
  }

  private fun setupPrivateKeysViewModel() {
    KeysStorageImpl.getInstance(requireContext()).secretKeyRingsLiveData
      .observe(viewLifecycleOwner, { keys ->
        updateFromAddressAdapter(keys.map { key -> key.toPgpKeyDetails() })
      })
  }

  private fun updateFromAddressAdapter(list: List<PgpKeyDetails>) {
    val setOfUsers = list.map { nodeKeyDetails -> nodeKeyDetails.pgpContacts }
      .flatten()
      .map { contact -> contact.email }

    fromAddrs?.let { adapter ->
      for (email in adapter.objects) {
        adapter.updateKeyAvailability(email, setOfUsers.contains(email))
      }
    }
  }

  private fun setupContactsViewModel() {
    handleUpdatingToContacts()
    handleUpdatingCcContacts()
    handleUpdatingBccContacts()
  }

  private fun handleUpdatingToContacts() {
    contactsViewModel.contactsToLiveData.observe(viewLifecycleOwner, {
      when (it.status) {
        Result.Status.LOADING -> {
          hostActivity?.countingIdlingResource?.incrementSafely()
          pgpContactsTo?.clear()
          progressBarTo?.visibility = View.VISIBLE
          isUpdateToCompleted = false
        }

        Result.Status.SUCCESS -> {
          isUpdateToCompleted = true
          progressBarTo?.visibility = View.INVISIBLE

          pgpContactsTo =
            it.data?.map { contactEntity -> contactEntity.toPgpContact() }?.toMutableList()
          if (pgpContactsTo?.isNotEmpty() == true) {
            updateChips(recipientsTo, pgpContactsTo)
          }
          hostActivity?.countingIdlingResource?.decrementSafely()
        }

        Result.Status.ERROR, Result.Status.EXCEPTION -> {
          isUpdateToCompleted = true
          progressBarTo?.visibility = View.INVISIBLE
          showInfoSnackbar(view, it.exception?.message ?: getString(R.string.unknown_error))
          hostActivity?.countingIdlingResource?.decrementSafely()
        }
      }
    })
  }

  private fun handleUpdatingCcContacts() {
    contactsViewModel.contactsCcLiveData.observe(viewLifecycleOwner, {
      when (it.status) {
        Result.Status.LOADING -> {
          hostActivity?.countingIdlingResource?.incrementSafely()
          pgpContactsCc?.clear()
          progressBarCc?.visibility = View.VISIBLE
          isUpdateCcCompleted = false
        }

        Result.Status.SUCCESS -> {
          isUpdateCcCompleted = true
          progressBarCc?.visibility = View.INVISIBLE
          pgpContactsCc =
            it.data?.map { contactEntity -> contactEntity.toPgpContact() }?.toMutableList()

          if (pgpContactsCc?.isNotEmpty() == true) {
            updateChips(recipientsCc, pgpContactsCc)
          }
          hostActivity?.countingIdlingResource?.decrementSafely()
        }

        Result.Status.ERROR, Result.Status.EXCEPTION -> {
          isUpdateCcCompleted = true
          progressBarCc?.visibility = View.INVISIBLE
          showInfoSnackbar(view, it.exception?.message ?: getString(R.string.unknown_error))
          hostActivity?.countingIdlingResource?.decrementSafely()
        }
      }
    })
  }

  private fun handleUpdatingBccContacts() {
    contactsViewModel.contactsBccLiveData.observe(viewLifecycleOwner, {
      when (it.status) {
        Result.Status.LOADING -> {
          hostActivity?.countingIdlingResource?.incrementSafely()
          pgpContactsBcc?.clear()
          progressBarBcc?.visibility = View.VISIBLE
          isUpdateBccCompleted = false
        }

        Result.Status.SUCCESS -> {
          isUpdateBccCompleted = true
          progressBarBcc?.visibility = View.INVISIBLE
          pgpContactsBcc =
            it.data?.map { contactEntity -> contactEntity.toPgpContact() }?.toMutableList()

          if (pgpContactsBcc?.isNotEmpty() == true) {
            updateChips(recipientsBcc, pgpContactsBcc)
          }
          hostActivity?.countingIdlingResource?.decrementSafely()
        }

        Result.Status.ERROR, Result.Status.EXCEPTION -> {
          isUpdateBccCompleted = true
          progressBarBcc?.visibility = View.INVISIBLE
          showInfoSnackbar(view, it.exception?.message ?: getString(R.string.unknown_error))
          hostActivity?.countingIdlingResource?.decrementSafely()
        }
      }
    })
  }

  /**
   * Add [AttachmentInfo] that was created from the given [Uri]
   *
   * Due to accessing to the [Uri] out of the current [Activity] we should use
   * [ContentResolver.takePersistableUriPermission] in order to persist the
   * permission.
   *
   * Because access to the uri is granted until the receiving Activity is
   * finished. We can extend the lifetime of the permission grant by passing
   * it along to another Android component. This is done by including the uri
   * in the data field or the ClipData object of the Intent used to launch that
   * component. Additionally, we need to add [Intent.FLAG_GRANT_READ_URI_PERMISSION] to the Intent.
   */
  private fun addAttachmentInfoFromIntent(intent: Intent?) {
    val uri = intent?.data
    if (uri != null) {
      val attachmentInfo = EmailUtil.getAttInfoFromUri(context, uri)
      if (hasAbilityToAddAtt(attachmentInfo)) {
        try {
          context?.contentResolver?.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
          )
        } catch (e: Exception) {
          showInfoSnackbar(view, getString(R.string.can_not_attach_this_file), Snackbar.LENGTH_LONG)
          return
        }
        attachmentInfo?.let { atts?.add(it) }
        showAtts()
      } else {
        showInfoSnackbar(
          view, getString(
            R.string.template_warning_max_total_attachments_size,
            FileUtils.byteCountToDisplaySize(Constants.MAX_TOTAL_ATTACHMENT_SIZE_IN_BYTES)
          ),
          Snackbar.LENGTH_LONG
        )
      }
    } else {
      showInfoSnackbar(view, getString(R.string.can_not_attach_this_file), Snackbar.LENGTH_LONG)
    }
  }

  /**
   * This interface will be used when we send a message.
   */
  interface OnMessageSendListener {
    fun sendMsg(outgoingMsgInfo: OutgoingMessageInfo)
  }

  companion object {
    private const val REQUEST_CODE_NO_PGP_FOUND_DIALOG = 100
    private const val REQUEST_CODE_IMPORT_PUBLIC_KEY = 101
    private const val REQUEST_CODE_GET_CONTENT_FOR_SENDING = 102
    private const val REQUEST_CODE_COPY_PUBLIC_KEY_FROM_OTHER_CONTACT = 103
    private const val REQUEST_CODE_SHOW_PUB_KEY_DIALOG = 106
    private const val REQUEST_CODE_SHOW_FIX_EMPTY_PASSPHRASE_DIALOG = 107
    private val TAG = CreateMessageFragment::class.java.simpleName
  }
}
