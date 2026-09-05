package com.radoslove.interslavic

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.InputMethodManager

/**
 * The Interslavic (medžuslovjansky) IME. M1 serves the real keyboard from
 * [KeyboardView]; the input view is rebuilt each time so it picks up a fresh
 * [getCurrentInputConnection].
 */
class ImeService : InputMethodService() {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)   // test builds only; see the class doc
        GestureLog.install(this)      // likewise: real glides, for tuning
        // Kick off the background load of the prediction model once.
        Dictionary.ensureLoaded(this)
    }

    private var view: KeyboardView? = null

    override fun onCreateInputView(): View = KeyboardView(this, this).also { view = it }

    /**
     * The editor tells us where the cursor and selection are. This is the only
     * RELIABLE way to know a selection exists: `getSelectedText` is optional and
     * plenty of editors answer null, which made the keyboard believe nothing was
     * selected and delete the wrong text.
     */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                                candidatesStart, candidatesEnd)
        view?.onSelectionChanged(newSelStart, newSelEnd)
    }

    /**
     * Globe key — show the system keyboard chooser. Earlier this cycled blindly
     * to the next IME (switchToNextInputMethod), which jumped straight into the
     * OEM keyboard with no way back; the picker lets the user pick and return.
     */
    fun switchIme() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }
}
