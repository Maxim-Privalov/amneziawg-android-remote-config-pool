package org.amnezia.awg.fragment

import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.remotepool.InvalidDsnException
import com.remotepool.InvalidPoolNameException
import com.remotepool.PoolAlreadyExistsException
import com.remotepool.PoolDsn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.amnezia.awg.Application
import org.amnezia.awg.R
import org.amnezia.awg.util.ErrorMessages
import org.amnezia.awg.widget.NameInputFilter

/**
 * Dialog for adding a remote pool: a name and a DSN (`selectel;<project>;<uid>;<user>;<password>`).
 * The pool shows up in the tunnel list as a regular tunnel, its config is fetched on connect.
 */
class PoolOverlayFragment(
    /*
    private lateinit var nameLayout: TextInputLayout
    private lateinit var nameInput: TextInputEditText
    private lateinit var dsnLayout: TextInputLayout
    private lateinit var dsnInput: TextInputEditText
    private lateinit var errorText: TextView
    private lateinit var progress: LinearProgressIndicator

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        val content = activity.layoutInflater.inflate(R.layout.dialog_input, null)
        nameLayout = content.findViewById(R.id.pool_name_layout)
        nameInput = content.findViewById(R.id.pool_name_input)
        dsnLayout = content.findViewById(R.id.dsn_layout)
        dsnInput = content.findViewById(R.id.dsn_input)
        errorText = content.findViewById(R.id.error_text)
        progress = content.findViewById(R.id.progress)
        nameInput.filters = arrayOf(NameInputFilter.newInstance())

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.dialog_title_pool_connection)
            .setView(content)
            .setPositiveButton(R.string.create_tunnel, null) // overridden in onStart: errors must keep the dialog open
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener { submit() }
    }

    private fun submit() {
        clearErrors()
        val name = nameInput.text.toString().trim()
        val dsn = try {
            PoolDsn.parse(dsnInput.text.toString())
        } catch (e: InvalidDsnException) {
            showError(e)
            return
        }
        setBusy(true)
        lifecycleScope.launch {
            try {
                Application.getPoolManager().create(name, dsn.user, dsn.password)
                dismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Unable to add pool", e)
                setBusy(false)
                showError(e)
            }
        }
    }

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

    /** While a request is in flight nothing can be edited or pressed twice. */
    private fun setBusy(busy: Boolean) {
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        nameInput.isEnabled = !busy
        dsnInput.isEnabled = !busy
        (dialog as? AlertDialog)?.apply {
            getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !busy
            getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = !busy
        }
        isCancelable = !busy
    }
     */
    private val overlay: View,
    private val onCreated: () -> Unit
    ) {
        private val nameLayout = overlay.findViewById<TextInputLayout>(R.id.pool_name_layout)
        private val nameInput = overlay.findViewById<TextInputEditText>(R.id.pool_name_input)
        private val dsnLayout = overlay.findViewById<TextInputLayout>(R.id.dsn_layout)
        private val dsnInput = overlay.findViewById<TextInputEditText>(R.id.dsn_input)
        private val errorText = overlay.findViewById<TextView>(R.id.error_text)
        private val progress = overlay.findViewById<LinearProgressIndicator>(R.id.progress)
        private val createBtn = overlay.findViewById<MaterialButton>(R.id.pool_create)
        private val cancelBtn = overlay.findViewById<MaterialButton>(R.id.pool_cancel)

        private val scope = overlay.findViewTreeLifecycleOwner()!!.lifecycleScope

        init {
            nameInput.filters = arrayOf(NameInputFilter.newInstance())
            cancelBtn.setOnClickListener { close() }
            createBtn.setOnClickListener { submit() }
        }

        fun isOpen(): Boolean = overlay.visibility == View.VISIBLE

        fun open() {
            clearErrors()
            overlay.visibility = View.VISIBLE
            nameInput.requestFocus()
        }

        fun close() {
            overlay.visibility = View.GONE
            // по желанию — очистить поля:
            nameInput.setText("")
            dsnInput.setText("")
            clearErrors()
        }

        private fun submit() {
            clearErrors()
            val name = nameInput.text.toString().trim()
            val dsn = try {
                PoolDsn.parse(dsnInput.text.toString())
            } catch (e: InvalidDsnException) {
                showError(e); return
            }
            setBusy(true)
            scope.launch {
                try {
                    Application.getPoolManager().create(name, dsn.user, dsn.password)
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
