package com.radoslove.interslavic

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.inputmethod.InputMethodSubtype
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

    /** The script the current view was built with (key labels are drawn once). */
    private var builtScript: Script.Mode? = null

    /** The languages the current view was built with (the space bar names them). */
    private var builtLangs: List<String>? = null

    /** The theme choice the current view was built with. */
    private var builtTheme: String? = null

    override fun onCreateInputView(): View {
        builtScript = Script.mode(this)
        builtLangs = Lang.enabled(this) + Lang.current(this)
        builtTheme = Theme.choice(this)
        return KeyboardView(this, this).also { view = it }
    }

    /** A script or languages chosen in the settings take effect the next time the keyboard opens. */
    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        // The system remembers which language (subtype) is active; follow it.
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        Lang.of(imm.currentInputMethodSubtype)?.let { Lang.setCurrent(this, it) }
        Dictionary.ensureLoaded(this)
        val langs = Lang.enabled(this) + Lang.current(this)
        if (builtScript != null &&
            (Script.mode(this) != builtScript || langs != builtLangs || Theme.choice(this) != builtTheme)) {
            setInputView(onCreateInputView())
        }
        super.onStartInputView(info, restarting)
    }

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

    /** The system switched our language (🌐, the picker, or its settings). */
    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        val lang = Lang.of(newSubtype) ?: return
        if (lang == Lang.current(this) && view != null) return
        Lang.setCurrent(this, lang)
        Dictionary.use(this, lang)
        if (view != null) setInputView(onCreateInputView())
    }

    /**
     * 🌐 tap: the next ticked language of THIS keyboard, as the system rotates
     * them. Returns false when there is no other one to go to.
     */
    fun nextLanguage(): Boolean {
        if (Lang.enabled(this).size < 2) return false
        return if (Build.VERSION.SDK_INT >= 28) {
            switchToNextInputMethod(true)
        } else {
            val token = window?.window?.attributes?.token ?: return false
            @Suppress("DEPRECATION")
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).switchToNextInputMethod(token, true)
        }
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
