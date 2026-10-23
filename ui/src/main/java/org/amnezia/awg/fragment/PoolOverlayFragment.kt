package org.amnezia.awg.fragment

import android.graphics.drawable.VectorDrawable
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.remotepool.exceptions.InvalidDsnException
import com.remotepool.exceptions.InvalidPoolNameException
import com.remotepool.exceptions.PoolAlreadyExistsException
import com.remotepool.PoolDsn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.amnezia.awg.Application
import org.amnezia.awg.R
import org.amnezia.awg.util.ErrorMessages
import org.amnezia.awg.widget.NameInputFilter

/**
+ * Dialog for adding or editing a remote pool: a name and a DSN (`selectel;<project>;<uid>;<user>;<password>`).
 * The pool shows up in the tunnel list as a regular tunnel, its config is fetched on connect.
 *
 * In edit mode (started with a pool name) the name is prefilled and an empty DSN keeps the current credentials.
 * PoolManager refuses edits of a connected pool, the error is shown in the dialog.
 */
class PoolOverlayFragment(

    private val overlay: View,
    private val onCreated: () -> Unit
    ) {
    /** Name of the pool being edited, null when adding. Follows a successful rename. */

    private var editedPool: String? = null
    private val titleView = overlay.findViewById<TextView>(R.id.pool_overlay_title)
    private val nameLayout = overlay.findViewById<TextInputLayout>(R.id.pool_name_layout)
    private val nameInput = overlay.findViewById<TextInputEditText>(R.id.pool_name_input)
    private val dsnLayout = overlay.findViewById<TextInputLayout>(R.id.dsn_layout)
    private val dsnInput = overlay.findViewById<TextInputEditText>(R.id.dsn_input)
    private val errorText = overlay.findViewById<TextView>(R.id.error_text)
    private val progress = overlay.findViewById<LinearProgressIndicator>(R.id.progress)
    private val createBtn = overlay.findViewById<MaterialButton>(R.id.pool_create)
    private val cancelBtn = overlay.findViewById<MaterialButton>(R.id.pool_cancel)
    private val removeBtn = overlay.findViewById<MaterialButton>(R.id.pool_remove)

    private val scope = overlay.findViewTreeLifecycleOwner()!!.lifecycleScope

    init {
        nameInput.filters = arrayOf(NameInputFilter.newInstance())
        cancelBtn.setOnClickListener { close() }
        createBtn.setOnClickListener { submit() }
        removeBtn.setOnClickListener { remove() }
    }

    fun isOpen(): Boolean = overlay.visibility == View.VISIBLE

    fun open(poolName: String? = null) {
        editedPool = poolName
        clearErrors()
        overlay.visibility = View.VISIBLE

        val editing = editedPool != null
        if (editing) {
            nameInput.setText(editedPool)
            createBtn.setText(R.string.save)
            titleView.setText(R.string.pool_edit_title)
            dsnLayout.helperText = overlay.context.getString(R.string.pool_dsn_keep_hint)
        } else {
            nameInput.setText("")
            removeBtn.visibility = View.GONE
            titleView.setText(R.string.dialog_title_pool_connection)
            createBtn.setText(R.string.create_tunnel)
            dsnLayout.helperText = null
        }
        // заголовок/кнопка — см. пункт 3
        nameInput.requestFocus()
    }

    fun close() {
        overlay.visibility = View.GONE
        // по желанию — очистить поля:
        nameInput.setText("")
        dsnInput.setText("")
        dsnLayout.helperText = null
        editedPool = null
        clearErrors()
    }

    private fun remove() = scope.launch {
        try {
            val pools = Application.getPoolManager()
            pools.delete(editedPool!!)
            close()
        } catch (e: Exception) {
            Log.e("PoolManager", "Undefined exception: ${e}")
            showError(e)
        }
    }

    private fun submit() {
        clearErrors()
        val name = nameInput.text.toString().trim()
        val dsn = if (editedPool != null && dsnInput.text.toString().trim() == "") null
        else try {
            PoolDsn.parse(dsnInput.text.toString())
        } catch (e: InvalidDsnException) {
            showError(e); return
        }
        setBusy(true)
        scope.launch {
            try {
                val pools = Application.getPoolManager()
                val current = editedPool
                if (current == null) {
                    pools.create(name, dsn!!.user, dsn.password)
                } else {
                    if (name != current) editedPool = pools.setPoolName(current, name)
                    if (dsn != null) pools.setCredentials(editedPool!!, dsn.user, dsn.password)
                }
                setBusy(false)
                close()
                onCreated()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e("PoolOverlay", "Unable to add pool", e)
                setBusy(false)
                showError(e)
            }
        }
    }

    fun show() = open(null)

    fun showEdit(poolName: String) = open(poolName)

    private fun showError(e: Throwable) {
        val message = ErrorMessages[e]
        when (e) {
            is InvalidPoolNameException, is PoolAlreadyExistsException -> nameLayout.error = message
            is InvalidDsnException -> dsnLayout.error = message
            else -> errorText.apply { text = message; visibility = View.VISIBLE }
        }
    }

    private fun clearErrors() {
        nameLayout.error = null
        dsnLayout.error = null
        errorText.visibility = View.GONE
    }

    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        nameInput.isEnabled = !busy
        dsnInput.isEnabled = !busy
        createBtn.isEnabled = !busy
        cancelBtn.isEnabled = !busy
    }
}
