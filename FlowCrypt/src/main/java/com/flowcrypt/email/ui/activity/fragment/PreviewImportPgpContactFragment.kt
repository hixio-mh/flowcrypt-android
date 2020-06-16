/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity.fragment

import android.app.Activity
import android.content.Intent
import android.content.OperationApplicationException
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Parcelable
import android.os.RemoteException
import android.text.TextUtils
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flowcrypt.email.R
import com.flowcrypt.email.api.retrofit.response.base.Result
import com.flowcrypt.email.database.FlowCryptRoomDatabase
import com.flowcrypt.email.database.entity.ContactEntity
import com.flowcrypt.email.extensions.showInfoDialogFragment
import com.flowcrypt.email.jetpack.viewmodel.ContactsViewModel
import com.flowcrypt.email.jetpack.viewmodel.ParseKeysViewModel
import com.flowcrypt.email.model.PgpContact
import com.flowcrypt.email.model.PublicKeyInfo
import com.flowcrypt.email.ui.activity.fragment.base.BaseFragment
import com.flowcrypt.email.ui.activity.fragment.base.ListProgressBehaviour
import com.flowcrypt.email.ui.adapter.ImportPgpContactsRecyclerViewAdapter
import com.flowcrypt.email.util.GeneralUtil
import java.io.FileNotFoundException
import java.lang.ref.WeakReference

/**
 * This fragment displays information about public keys owners and information about keys.
 *
 * @author Denis Bondarenko
 * Date: 05.06.2018
 * Time: 14:15
 * E-mail: DenBond7@gmail.com
 */
class PreviewImportPgpContactFragment : BaseFragment(), ListProgressBehaviour,
    ImportPgpContactsRecyclerViewAdapter.ContactActionsListener {

  override val emptyView: View?
    get() = view?.findViewById(R.id.empty)
  override val progressView: View?
    get() = view?.findViewById(R.id.progress)
  override val contentView: View?
    get() = view?.findViewById(R.id.content)
  override val statusView: View?
    get() = view?.findViewById(R.id.status)

  override val contentResourceId: Int = R.layout.fragment_preview_import_pgp_contact

  private var recyclerView: RecyclerView? = null
  private var btnImportAll: TextView? = null
  private var textViewProgressTitle: TextView? = null
  private var progressBar: ProgressBar? = null

  private var publicKeysString: String? = null
  private var publicKeysFileUri: Uri? = null

  private val contactsViewModel: ContactsViewModel by viewModels()
  private val parseKeysViewModel: ParseKeysViewModel by viewModels()
  private val adapter: ImportPgpContactsRecyclerViewAdapter = ImportPgpContactsRecyclerViewAdapter(this)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    publicKeysString = arguments?.getString(KEY_EXTRA_PUBLIC_KEY_STRING)
    publicKeysFileUri = arguments?.getParcelable(KEY_EXTRA_PUBLIC_KEYS_FILE_URI)

    if (publicKeysString.isNullOrEmpty()) {
      parseKeysViewModel.parseKeys(publicKeysFileUri)
    } else {
      parseKeysViewModel.parseKeys(publicKeysString)
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    initViews(view)
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    if (TextUtils.isEmpty(publicKeysString) && publicKeysFileUri == null) {
      requireActivity().setResult(Activity.RESULT_CANCELED)
      requireActivity().finish()
    }

    setupParseKeysViewModel()
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    when (requestCode) {
      REQUEST_CODE_CONFIRM_EMPTY_RESULTS -> {
        if (resultCode == Activity.RESULT_OK) {
          activity?.finish()
        }
      }

      else -> {
        super.onActivityResult(requestCode, resultCode, data)
      }
    }
  }

  private fun setupParseKeysViewModel() {
    parseKeysViewModel.parsedKeysFromSourceLiveData.observe(viewLifecycleOwner, Observer {
      when (it.status) {
        Result.Status.LOADING -> {
          showProgress(getString(R.string.parsing_public_keys))
          if (it.progress > 0) {
            progressBar?.progress = it.progress
          }
        }

        Result.Status.SUCCESS -> {
          val list = it.data
          if (list?.isNotEmpty() == true) {
            showContent()
            adapter.swap(list)
            btnImportAll?.visibility = if (list.size > 1) View.VISIBLE else View.GONE
          } else {
            showEmptyView(msg = getString(R.string.no_public_key_found))
          }
        }

        Result.Status.ERROR, Result.Status.EXCEPTION -> {
          var msg = it.exception?.message ?: it.exception?.javaClass?.simpleName
          ?: getString(R.string.unknown_error)

          if (it.exception is FileNotFoundException) {
            msg = getString(R.string.file_not_found)
          }

          showInfoDialogFragment(dialogMsg = msg, targetFragment = this, requestCode = REQUEST_CODE_CONFIRM_EMPTY_RESULTS)
        }
      }
    })
  }

  override fun onError(loaderId: Int, e: Exception?) {
    when (loaderId) {
      R.id.loader_id_parse_public_keys -> if (activity != null) {
        requireActivity().setResult(Activity.RESULT_CANCELED)
        Toast.makeText(context, if (e?.message.isNullOrEmpty()) {
          e?.javaClass?.simpleName ?: getString(R.string.unknown_error)
        } else
          e?.message, Toast.LENGTH_SHORT).show()
        requireActivity().finish()
      }

      else -> super.onError(loaderId, e)
    }
  }

  override fun onSaveContactClick(publicKeyInfo: PublicKeyInfo) {
    contactsViewModel.addContact(publicKeyInfo.toPgpContact())
  }

  override fun onUpdateContactClick(publicKeyInfo: PublicKeyInfo) {
    contactsViewModel.updateContact(publicKeyInfo.toPgpContact())
  }

  private fun initViews(root: View) {
    textViewProgressTitle = root.findViewById(R.id.textViewProgressTitle)
    progressBar = root.findViewById(R.id.progressBar)
    recyclerView = root.findViewById(R.id.recyclerViewContacts)
    recyclerView?.setHasFixedSize(true)
    recyclerView?.layoutManager = LinearLayoutManager(context)
    recyclerView?.adapter = adapter

    btnImportAll = root.findViewById(R.id.buttonImportAll)
    btnImportAll?.setOnClickListener {
      SaveAllContactsAsyncTask(this, adapter.publicKeys).execute()
    }
  }

  private fun handleImportAllResult(result: Boolean?) {
    if (isAdded) {
      if (result == true) {
        Toast.makeText(context, R.string.success, Toast.LENGTH_SHORT).show()
        if (activity != null) {
          requireActivity().setResult(Activity.RESULT_OK)
          requireActivity().finish()
        }
      } else {
        showContent()
        Toast.makeText(context, getString(R.string.could_not_import_data), Toast.LENGTH_SHORT).show()
      }
    }
  }

  private class SaveAllContactsAsyncTask internal constructor(
      fragment: PreviewImportPgpContactFragment,
      private val publicKeyInfoList: List<PublicKeyInfo>) : BaseAsyncTask<Void, Int, Boolean>(fragment) {

    override val progressTitleResourcesId: Int
      get() = R.string.importing_public_keys

    override fun doInBackground(vararg uris: Void): Boolean? {
      val newCandidates = ArrayList<PgpContact>()
      val updateCandidates = ArrayList<PgpContact>()

      for (publicKeyInfo in publicKeyInfoList) {
        val pgpContact = PgpContact(publicKeyInfo.keyOwner, null, publicKeyInfo.publicKey,
            true, null, publicKeyInfo.fingerprint, publicKeyInfo.longId, publicKeyInfo.keyWords, 0)

        if (publicKeyInfo.hasPgpContact()) {
          if (publicKeyInfo.isUpdateEnabled) {
            updateCandidates.add(pgpContact)
          }
        } else {
          newCandidates.add(pgpContact)
        }
      }

      try {
        var progress: Float
        var lastProgress = 0f
        val totalOperationsCount = newCandidates.size + updateCandidates.size

        run {
          var i = 0
          while (i < newCandidates.size) {
            val start = i
            val end = if (newCandidates.size - i > STEP_AMOUNT) i + STEP_AMOUNT else newCandidates.size

            if (weakRef.get() != null) {
              FlowCryptRoomDatabase.getDatabase(weakRef.get()?.requireContext()!!)
                  .contactsDao().insert(newCandidates.subList(start, end).map { it.toContactEntity() })
            }
            i = end

            progress = i * 100f / totalOperationsCount
            if (progress - lastProgress >= 1) {
              publishProgress(progress.toInt())
              lastProgress = progress
            }

            i--
            i++
          }
        }

        var i = 0
        while (i < updateCandidates.size) {
          val start = i
          val end = if (updateCandidates.size - i > STEP_AMOUNT) i + STEP_AMOUNT else updateCandidates.size - 1

          if (weakRef.get() != null) {
            val contacts = mutableListOf<ContactEntity>()
            val list = updateCandidates.subList(start, end + 1)

            list.forEach { pgpContact ->
              val foundContactEntity = FlowCryptRoomDatabase.getDatabase(weakRef.get()?.requireContext()!!)
                  .contactsDao().getContactByEmails(pgpContact.email)
              foundContactEntity?.let { entity -> contacts.add(pgpContact.toContactEntity().copy(id = entity.id)) }
            }

            FlowCryptRoomDatabase.getDatabase(weakRef.get()?.requireContext()!!).contactsDao().update(contacts)
          }
          i = end + 1

          progress = i * 100f / totalOperationsCount
          if (progress - lastProgress >= 1) {
            publishProgress(progress.toInt())
            lastProgress = progress
          }
          i++
        }

      } catch (e: RemoteException) {
        e.printStackTrace()
        return false
      } catch (e: OperationApplicationException) {
        e.printStackTrace()
        return false
      }

      publishProgress(100)
      return true
    }


    override fun onPostExecute(b: Boolean?) {
      super.onPostExecute(b)
      if (b != null && weakRef.get() != null) {
        weakRef.get()?.handleImportAllResult(b)
      }
    }

    override fun updateProgress(progress: Int) {
      if (weakRef.get() != null) {
        weakRef.get()?.progressBar!!.progress = progress
      }
    }

    companion object {
      private const val STEP_AMOUNT = 50
    }
  }

  private abstract class BaseAsyncTask<Params, Progress, Result>
  internal constructor(previewImportPgpContactFragment: PreviewImportPgpContactFragment)
    : AsyncTask<Params, Progress, Result>() {
    internal val weakRef: WeakReference<PreviewImportPgpContactFragment> =
        WeakReference(previewImportPgpContactFragment)

    abstract val progressTitleResourcesId: Int

    abstract fun updateProgress(progress: Progress)

    override fun onPreExecute() {
      super.onPreExecute()
      if (weakRef.get() != null) {
        weakRef.get()?.progressBar!!.isIndeterminate = true
        weakRef.get()?.textViewProgressTitle!!.setText(progressTitleResourcesId)
        weakRef.get()?.showProgress()
      }
    }

    @SafeVarargs
    override fun onProgressUpdate(vararg values: Progress) {
      super.onProgressUpdate(*values)
      if (weakRef.get() != null) {
        if (weakRef.get()?.progressBar!!.isIndeterminate) {
          weakRef.get()?.progressBar!!.isIndeterminate = false
        }
        updateProgress(values[0])
      }
    }
  }

  companion object {
    private val KEY_EXTRA_PUBLIC_KEY_STRING =
        GeneralUtil.generateUniqueExtraKey("KEY_EXTRA_PUBLIC_KEY_STRING",
            PreviewImportPgpContactFragment::class.java)

    private val KEY_EXTRA_PUBLIC_KEYS_FILE_URI =
        GeneralUtil.generateUniqueExtraKey("KEY_EXTRA_PUBLIC_KEYS_FILE_URI",
            PreviewImportPgpContactFragment::class.java)

    private const val REQUEST_CODE_CONFIRM_EMPTY_RESULTS = 100

    fun newInstance(stringExtra: String?, fileUri: Parcelable?): PreviewImportPgpContactFragment {
      val args = Bundle()
      args.putString(KEY_EXTRA_PUBLIC_KEY_STRING, stringExtra)
      args.putParcelable(KEY_EXTRA_PUBLIC_KEYS_FILE_URI, fileUri)

      val previewImportPgpContactFragment = PreviewImportPgpContactFragment()
      previewImportPgpContactFragment.arguments = args
      return previewImportPgpContactFragment
    }
  }
}
