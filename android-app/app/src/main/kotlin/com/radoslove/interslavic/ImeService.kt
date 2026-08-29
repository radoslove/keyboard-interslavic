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
        // Kick off the background load of the prediction model once.
        Dictionary.ensureLoaded(this)
    }

    override fun onCreateInputView(): View = KeyboardView(this, this)

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
