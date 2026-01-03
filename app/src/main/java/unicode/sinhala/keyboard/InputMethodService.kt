package unicode.sinhala.keyboard

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import unicode.sinhala.com.R
import unicode.sinhala.keyboard.Maps.keyLabelsLettersEnglish
import unicode.sinhala.keyboard.Maps.keyLabelsLettersEnglishShifted
import unicode.sinhala.keyboard.Maps.keyLabelsNumbers
import unicode.sinhala.keyboard.Maps.keyLabelsSpecialEnglish
import unicode.sinhala.keyboard.Maps.keyLabelsLettersWijesekara
import unicode.sinhala.keyboard.Maps.keyLabelsLettersWijesekaraShifted
import unicode.sinhala.keyboard.Maps.keyLabelsNumbersWijesekara
import unicode.sinhala.keyboard.Maps.keyLabelsSpecialWijesekaraSinhala
import unicode.sinhala.keyboard.Maps.keyLabelsSpecialWijesekaraSinhalaShifted
import unicode.sinhala.keyboard.Maps.singlishMap
import unicode.sinhala.keyboard.swaraSignMap
import unicode.sinhala.keyboard.Maps.symbolsMap
import unicode.sinhala.keyboard.Maps.symbolsMapShifted
import ime.suggest.SuggestionEngine
import ime.suggest.LanguageDetector
import ime.imeui.DebouncedInputHandler
import ime.imeui.TopBarController
import android.widget.TextView
import androidx.compose.ui.semantics.text
import java.text.Normalizer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

class InputMethodService : android.inputmethodservice.InputMethodService(),
    KeyboardView.ClickListener, KeyboardView.SwipeListener, LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboardLayout: KeyboardLayout

    private var caps = false
    private var shift = false


    private var keyboardSymbolsActive = false

    private var mComposing = ""
    private var tComposing = ""


    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)


    private var userInvokedInputMethodPicker = false

    private var suggestionEngine: SuggestionEngine? = null
    private var debouncer: DebouncedInputHandler? = null
    private var topBarController: TopBarController? = null
    private var suggestionTextViews: List<TextView> = emptyList()
    private var suggestionsEnabled = true

    // Lifecycle and SavedStateRegistry support
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        Log.d("IME", "onCreate called")
        suggestionEngine = SuggestionEngine(this)
        // Initialize engine asynchronously
        serviceScope.launch {
            suggestionEngine?.initializeIfNeeded()
        }
        debouncer = DebouncedInputHandler(serviceScope, 700L)

        EmojiData.loadRecentEmojis(this)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
        serviceJob.cancel()
        Log.d("IME", "onDestroy called")
    }

    private fun commitWijesekaraChar(char: String) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""

        val composed = when (before + char) {
            "අැ" -> "ඇ"
            "අා" -> "ආ"
            "එ්" -> "ඒ"
            "එෙ" -> "ඓ"
            "ෙඑ" -> "ඓ"
            "ඔ්" -> "ඕ"
            "උ්" -> "ඌ"


            // Consonant + 'e' sign combinations


            else -> null
        }

        if (composed != null) {
            // If we have a composition, delete the previous character and commit the new one.
            ic.deleteSurroundingText(1, 0)
            ic.commitText(composed, 1)
        } else {
            // Otherwise, just commit the character the user typed.
            ic.commitText(char, 1)
        }
    }



    override fun onCreateInputView(): View {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        if (::keyboardView.isInitialized) return keyboardView

        try {
            keyboardView = KeyboardView(
                this,
                this,
                this,
                Prefs.getRowHeight(this),
                Prefs.getDarkTheme(this),
                Prefs.getKeyBorders(this),
                Prefs.getSwipeToErase(this),
                Prefs.getSwipeToMoveCursor(this),
                Prefs.getTextSize(this)
            )

            keyboardLayout = Prefs.getSelectedLayout(this)
            setKeyboardLayout(keyboardLayout)

            // Setup top bar controller with views from keyboardView and pass dark theme preference
            topBarController = TopBarController(
                keyboardView.suggestionContainerView,
                keyboardView.emojiButtonView,
                Prefs.getDarkTheme(this)
            )
            suggestionTextViews = keyboardView.getSuggestionTextViews()

            return keyboardView
        } catch (t: Throwable) {
            Log.e("IME", "Keyboard view creation failed, providing safe fallback view", t)

            // Provide a safe, minimal fallback view so IME does not crash.
            val fallback = View(this)
            try {
                // Attempt to set a sensible background color from theme attr if available, else default to white/black depending on night mode
                val typedValue = android.util.TypedValue()
                val theme = theme
                // First try app-specific fox_background (safe, non-colliding), then fall back to platform background attr
                var got = theme.resolveAttribute(R.attr.fox_background, typedValue, true)
                if (!got) {
                    try {
                        got = theme.resolveAttribute(android.R.attr.background, typedValue, true)
                    } catch (_: Exception) {
                        // some devices/themes might not expose android attr; ignore and fallback below
                        got = false
                    }
                }
                if (got) {
                    if (typedValue.resourceId != 0) {
                        fallback.setBackgroundResource(typedValue.resourceId)
                    } else {
                        try {
                            fallback.setBackgroundColor(typedValue.data)
                        } catch (e: Exception) {
                            fallback.setBackgroundColor(if (Prefs.getDarkTheme(this)) 0xFF263238.toInt() else 0xFFECEFF1.toInt())
                        }
                    }
                } else {
                    fallback.setBackgroundColor(if (Prefs.getDarkTheme(this)) 0xFF263238.toInt() else 0xFFECEFF1.toInt())
                }
            } catch (inner: Throwable) {
                Log.e("IME", "Failed to set fallback background", inner)
            }

            // Return fallback view to avoid crashing the IME.
            return fallback
        }
    }

     override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
         super.onStartInputView(info, restarting)
         lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
         Log.d("IME", "onStartInputView called restarting=$restarting info=")

         val desired = Prefs.getSelectedLayout(this)
         if (!::keyboardView.isInitialized) {

             onCreateInputView()
         }


        if (userInvokedInputMethodPicker) {

            userInvokedInputMethodPicker = false
            Log.d("IME", "Skipping automatic keyboard layout change after input method picker")
        } else {
            setKeyboardLayout(desired)
        }


        try {
            updateKeyboard()
        } catch (t: Throwable) {
            Log.e("IME", "updateKeyboard failed in onStartInputView", t)
        }


        if (restarting || info == null || currentInputConnection == null) {
            resetKeyboardState()
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Log.d("IME", "onStartInput called restarting=$restarting")

        if (currentInputConnection == null && restarting) {
            resetKeyboardState()
        }


        try {
            updateKeyboard()
        } catch (t: Throwable) {
            Log.e("IME", "updateKeyboard failed in onStartInput", t)
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        Log.d("IME", "onFinishInputView called finishingInput=$finishingInput")

        if (finishingInput) {
            resetKeyboardState()
        }
    }


    private fun resetKeyboardState() {
        mComposing = ""
        tComposing = ""
    }

    override fun onEvaluateFullscreenMode(): Boolean {

        return false
    }

    // Helper to request suggestions for a token
    private fun requestSuggestionsForToken(token: String) {
        // Do not auto-hide suggestions once opened. Only disable suggestions for password fields or when disabled in prefs.
        if (!suggestionsEnabled || isInPasswordField()) {
            topBarController?.showNormal()
            return
        }
        // Do not auto-hide suggestions on idle - only hide on explicit actions (space/action)
        debouncer?.onTyping(token, onTypingImmediate = { t ->
            serviceScope.launch {
                try {
                    val sList = suggestionEngine?.suggest(Normalizer.normalize(t, Normalizer.Form.NFC), 3) ?: emptyList<String>()
                    if (sList.isNotEmpty()) {
                        topBarController?.showSuggestions(sList, suggestionTextViews) { suggestion ->
                            onSuggestionClicked(suggestion)
                        }
                    } else {
                        // No suggestions found for this prefix. Do not automatically hide the suggestion bar per requirements.
                        // Keep current suggestions visible.
                    }
                } catch (e: Exception) {
                    topBarController?.showNormal()
                }
            }
        }, onIdle = null)
    }

    override fun letterOrSymbolClick(tag: String) {
        when {
            keyboardLayout == KeyboardLayout.SINGLISH && !keyboardSymbolsActive -> {
                singlishInput(tag)
            }

            keyboardLayout == KeyboardLayout.WIJESEKARA && !keyboardSymbolsActive -> {
                commitWijesekaraChar(tag)
            }

            else -> {
                currentInputConnection?.commitText(tag, 1)
            }
        }

        vibrate()

        try {
            val textBefore = currentInputConnection
                ?.getTextBeforeCursor(50, 0)
                ?.toString() ?: ""
            val token = textBefore.takeLastWhile { !it.isWhitespace() }
            requestSuggestionsForToken(token)
        } catch (_: Throwable) {}

        checkAutoUnshift()
    }

    private fun checkAutoUnshift() {
        if (caps && !shift) {
            caps = false
            updateKeyboard()
        }
    }

    private fun isInPasswordField(): Boolean {
        val t = currentInputEditorInfo
        return t != null && (t.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                t != null && (t.inputType and InputType.TYPE_NUMBER_VARIATION_PASSWORD) == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun onSuggestionClicked(suggestion: String) {
        val ic = currentInputConnection ?: return
        // Replace current token with suggestion
        val before = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
        val after = ic.getTextAfterCursor(100, 0)?.toString() ?: ""
        val tokenStart = before.lastIndexOfAny(charArrayOf(' ', '\n', '\t')).let { if (it < 0) 0 else it + 1 }
        val token = before.substring(tokenStart)
        // delete token
        for (i in 0 until token.codePointCount(0, token.length)) {
            ic.deleteSurroundingTextInCodePoints(1, 0)
        }
        // commit suggestion
        ic.commitText(suggestion, 1)
        // append a space? Do not append automatically — keep behavior stable

        // record acceptance
        serviceScope.launch {
            val lang = LanguageDetector.detectLanguage(suggestion)
            suggestionEngine?.recordAccepted(suggestion, lang)
        }

        // Do NOT hide suggestions here; per user request keep suggestion bar open until explicit Action or Space is pressed.
        // Resetting debounce is optional; keep it running so suggestions update if user continues typing.
    }

    private var lastChar: CHAR? = null
    private var lastLetter: CHAR? = null
    private var positionFlag = ""

    private fun hasPositionChanged(): Boolean =
        currentInputConnection.getTextBeforeCursor(5, 0)?.toString() != positionFlag

    private fun singlishInput(input: String) {
        var output = ""
        var erasePreviousChars = 0
        var mLastChar: CHAR? = null
        var mLastLetter: CHAR? = null
        var tLastChar: CHAR? = null
        var tLastLetter: CHAR? = null

        if (!hasPositionChanged()) {
            mLastChar = lastChar
            mLastLetter = lastLetter
        }

        lastChar = null
        lastLetter = null

        var singlishChar: CHAR = getSinglishChars(input) ?: CHAR.EMPTY

        fun newLetter() {
            output = singlishChar.text
            if (singlishChar.type == CharType.WYANJANA) {
                output += CHAR.SIGN_AL_LAKUNA.text
                tLastChar = CHAR.SIGN_AL_LAKUNA
            }
        }

        if (mLastChar == null || mLastChar == CHAR.EMPTY) {
            if (input == "z" || input == "Z") tLastChar = CHAR.MARK_SANYAKA
            else newLetter()
        } else {
            when {
                input == "z" || input == "Z" -> tLastChar = CHAR.MARK_SANYAKA
                mLastChar.type == CharType.WYANJANA ->
                    when (singlishChar.code) {
                        CHAR.AYANNA.code -> output = CHAR.AELA_PILLA.text
                        CHAR.IYANNA.code -> output = CHAR.KOMBU_DEKA.text
                        CHAR.UYANNA.code -> output = CHAR.KOMBUVA_HAA_GAYANUKITTA.text
                        else -> newLetter()
                    }

                mLastChar.code == CHAR.SIGN_AL_LAKUNA.code -> {
                    when (singlishChar.code) {
                        CHAR.AYANNA.code -> {
                            erasePreviousChars = 1
                            tLastChar = mLastLetter
                        }

                        CHAR.RAYANNA.code -> {
                            output =
                                CHAR.ZERO_WIDTH_JOINER.text + CHAR.RAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                            tLastChar = CHAR.SIGN_AL_LAKUNA
                        }

                        CHAR.YAYANNA.code -> {
                            output =
                                CHAR.ZERO_WIDTH_JOINER.text + CHAR.YAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                            tLastChar = CHAR.SIGN_AL_LAKUNA
                        }

                        CHAR.HAYANNA.code -> {
                            if (mLastLetter != null) {
                                when (mLastLetter.code) {
                                    CHAR.ALPAPRAANA_TTAYANNA.code -> {
                                        output =
                                            CHAR.ALPAPRAANA_TAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.ALPAPRAANA_TAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.MAHAAPRAANA_TTAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_TAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_TAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.ALPAPRAANA_DDAYANNA.code -> {
                                        output =
                                            CHAR.ALPAPRAANA_DAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.ALPAPRAANA_DAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.MAHAAPRAANA_DDAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_DAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_DAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.ALPAPRAANA_KAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_KAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_KAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.ALPAPRAANA_GAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_GAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_GAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.ALPAPRAANA_CAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_CAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_CAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.ALPAPRAANA_JAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_JAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_JAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.ALPAPRAANA_TAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_TAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_TAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.ALPAPRAANA_DAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_DAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_DAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.ALPAPRAANA_PAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_PAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_PAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.ALPAPRAANA_BAYANNA.code -> {
                                        output =
                                            CHAR.MAHAAPRAANA_BAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MAHAAPRAANA_BAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.DANTAJA_SAYANNA.code -> {
                                        output =
                                            CHAR.TAALUJA_SAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.TAALUJA_SAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.SANYAKA_DDAYANNA.code -> {
                                        output =
                                            CHAR.SANYAKA_DAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.SANYAKA_DAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    CHAR.MUURDHAJA_SAYANNA.code -> {
                                        output =
                                            CHAR.MUURDHAJA_SAYANNA.text + CHAR.SIGN_AL_LAKUNA.text
                                        erasePreviousChars = 2
                                        tLastLetter = CHAR.MUURDHAJA_SAYANNA
                                        tLastChar = CHAR.SIGN_AL_LAKUNA
                                    }

                                    else -> newLetter()
                                }
                            } else newLetter()
                        }

                        else -> {
                            when (singlishChar.type) {
                                CharType.SWARA -> {
                                    if (mLastLetter != null) {
                                        if (singlishChar.code == CHAR.AYANNA.code)
                                            erasePreviousChars = 1
                                        else {
                                            swaraSignMap[singlishChar.code].let { sign ->
                                                if (sign != null) {
                                                    output += sign.text
                                                    tLastChar = sign
                                                    erasePreviousChars = 1
                                                } else output = singlishChar.text
                                            }
                                        }
                                    } else output = singlishChar.text
                                }

                                else -> newLetter()
                            }
                        }
                    }
                }

                mLastChar.type == CharType.PILI -> {
                    when {
                        mLastChar.code == CHAR.KETTI_AEDA_PILLA.code && singlishChar.code == CHAR.AYANNA.code -> {
                            output = CHAR.DIGA_AEDA_PILLA.text
                            erasePreviousChars = 1
                            tLastChar = CHAR.DIGA_AEDA_PILLA
                        }

                        mLastChar.code == CHAR.KETTI_IS_PILLA.code && singlishChar.code == CHAR.IYANNA.code -> {
                            output = CHAR.DIGA_IS_PILLA.text
                            erasePreviousChars = 1
                            tLastChar = CHAR.DIGA_IS_PILLA
                        }

                        mLastChar.code == CHAR.KETTI_PAA_PILLA.code && singlishChar.code == CHAR.UYANNA.code -> {
                            output = CHAR.DIGA_PAA_PILLA.text
                            erasePreviousChars = 1
                            tLastChar = CHAR.DIGA_PAA_PILLA
                        }

                        mLastChar.code == CHAR.GAETTA_PILLA.code && singlishChar.code == CHAR.IYANNA.code -> {
                            output = CHAR.DIGA_GAETTA_PILLA.text
                            erasePreviousChars = 1
                            tLastChar = CHAR.DIGA_GAETTA_PILLA
                        }

                        mLastChar.code == CHAR.KOMBUVA.code && singlishChar.code == CHAR.EYANNA.code -> {
                            output = CHAR.DIGA_KOMBUVA.text
                            erasePreviousChars = 1
                            tLastChar = CHAR.DIGA_KOMBUVA
                        }

                        mLastChar.code == CHAR.KOMBUVA_HAA_AELA_PILLA.code && singlishChar.code == CHAR.OYANNA.code -> {
                            output = CHAR.KOMBUVA_HAA_DIGA_AELA_PILLA.text
                            erasePreviousChars = 1
                            tLastChar = CHAR.KOMBUVA_HAA_DIGA_AELA_PILLA
                        }

                        else -> newLetter()
                    }
                }

                mLastChar.code == CHAR.MARK_SANYAKA.code -> {
                    when (singlishChar.code) {
                        CHAR.ALPAPRAANA_KAYANNA.code -> singlishChar = CHAR.TAALUJA_NAASIKYAYA
                        CHAR.ALPAPRAANA_GAYANNA.code -> singlishChar = CHAR.SANYAKA_GAYANNA
                        CHAR.ALPAPRAANA_JAYANNA.code -> singlishChar = CHAR.SANYAKA_JAYANNA
                        CHAR.ALPAPRAANA_DDAYANNA.code -> singlishChar = CHAR.SANYAKA_DDAYANNA
                        CHAR.ALPAPRAANA_BAYANNA.code -> singlishChar = CHAR.AMBA_BAYANNA
                        CHAR.HAYANNA.code -> singlishChar = CHAR.TAALUJA_SANYOOGA_NAAKSIKYAYA
                    }
                    newLetter()
                }

                else -> {
                    if (mLastLetter != null) {
                        when (mLastLetter) {
                            CHAR.AYANNA -> {
                                when (singlishChar.code) {
                                    CHAR.AYANNA.code -> {
                                        output = CHAR.AAYANNA.text
                                        erasePreviousChars = 1
                                        tLastLetter = CHAR.AAYANNA
                                    }

                                    CHAR.IYANNA.code -> {
                                        output = CHAR.AIYANNA.text
                                        erasePreviousChars = 1
                                        tLastLetter = CHAR.AIYANNA
                                    }

                                    CHAR.UYANNA.code -> {
                                        output = CHAR.AUYANNA.text
                                        erasePreviousChars = 1
                                        tLastLetter = CHAR.AUYANNA
                                    }

                                    else -> newLetter()
                                }
                            }

                            CHAR.AEYANNA -> {
                                if (singlishChar.code == CHAR.AYANNA.code) {
                                    output = CHAR.AEEYANNA.text
                                    erasePreviousChars = 1
                                    tLastLetter = CHAR.AEEYANNA
                                } else newLetter()
                            }

                            CHAR.IYANNA -> {
                                if (singlishChar.code == CHAR.IYANNA.code) {
                                    output = CHAR.IIYANNA.text
                                    erasePreviousChars = 1
                                    tLastLetter = CHAR.IIYANNA
                                } else newLetter()
                            }

                            CHAR.UYANNA -> {
                                if (singlishChar.code == CHAR.UYANNA.code) {
                                    output = CHAR.UUYANNA.text
                                    erasePreviousChars = 1
                                    tLastLetter = CHAR.UUYANNA
                                } else newLetter()
                            }

                            CHAR.IRUYANNA -> {
                                if (singlishChar.code == CHAR.IYANNA.code) {
                                    output = CHAR.IRUUYANNA.text
                                    erasePreviousChars = 1
                                    tLastLetter = CHAR.IRUUYANNA
                                } else newLetter()
                            }

                            CHAR.EYANNA -> {
                                if (singlishChar.code == CHAR.EYANNA.code) {
                                    output = CHAR.EEYANNA.text
                                    erasePreviousChars = 1
                                    tLastLetter = CHAR.EEYANNA
                                } else newLetter()
                            }

                            CHAR.OYANNA -> {
                                if (singlishChar.code == CHAR.OYANNA.code) {
                                    output = CHAR.OOYANNA.text
                                    erasePreviousChars = 1
                                    tLastLetter = CHAR.OOYANNA
                                } else newLetter()
                            }

                            else -> newLetter()
                        }
                    } else newLetter()
                }
            }
        }

        if (erasePreviousChars > 0) erasePrevious(erasePreviousChars)

        val ic = currentInputConnection
        if (ic != null) {
            try {
                ic.commitText(output, 1)
            } catch (t: Throwable) {
                Log.e("IME", "singlishInput commit failed", t)
            }
        } else {
            Log.w("IME", "currentInputConnection is null in singlishInput")
        }

        lastChar = tLastChar ?: tLastLetter ?: singlishChar
        lastLetter = tLastLetter ?: singlishChar
        positionFlag = currentInputConnection.getTextBeforeCursor(5, 0)?.toString() ?: ""

        // After committing singlish output, request suggestions for updated token
        try {
            val textBefore2 = currentInputConnection.getTextBeforeCursor(50, 0)?.toString() ?: ""
            val token2 = textBefore2.takeLastWhile { !it.isWhitespace() }
            requestSuggestionsForToken(token2)
        } catch (t: Throwable) {
            Log.w("IME", "failed to update suggestions after singlishInput", t)
        }
    }

    private fun getSinglishChars(input: String): CHAR? = singlishMap[input]

    override fun emojiClick(tag: String) {
        val ic = currentInputConnection
        if (ic != null) {
            try {
                ic.commitText(tag, 1)
                EmojiData.addRecentEmoji(this, tag)
            } catch (t: Throwable) {
                Log.e("IME", "emoji commit failed", t)
            }
        } else {
            Log.w("IME", "currentInputConnection is null in emojiClick")
        }
        vibrate()
        checkAutoUnshift()
    }

    override fun numberClick(tag: String) {
        val ic = currentInputConnection
        if (ic != null) {
            try {

                val toCommit = when (keyboardLayout) {
                    KeyboardLayout.WIJESEKARA -> Maps.keyLabelsNumbersWijesekara[tag] ?: tag
                    else -> tag
                }
                ic.commitText(toCommit, 1)
            } catch (t: Throwable) {
                Log.e("IME", "number commit failed", t)
            }
        } else {
            Log.w("IME", "currentInputConnection is null in numberClick")
        }
        vibrate()
        checkAutoUnshift()
    }


    override fun functionClick(type: Function) {
        val ic = currentInputConnection
        when (type) {
            Function.ACTION -> {
                if (ic != null) {
                    try {
                        val editorInfo = currentInputEditorInfo
                        val actionId = editorInfo?.actionId ?: 0
                        if (actionId != 0) ic.performEditorAction(actionId)
                        else ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    } catch (t: Throwable) {
                        Log.e("IME", "performEditorAction/sendKeyEvent failed", t)
                    }
                } else {
                    Log.w("IME", "currentInputConnection is null in ACTION")
                }
                // Hide suggestions explicitly when user presses action
                topBarController?.showNormal()
                debouncer?.cancel()
            }

            Function.SHIFT -> {
                if (!caps) {
                    caps = true
                    shift = false
                } else if (!shift) {
                    shift = true
                } else {
                    caps = false
                    shift = false
                }
                updateKeyboard()
            }

            Function.LANG -> {
                try {
                    val enabled = Prefs.getEnabledLayouts(this)
                    val currentIndex = enabled.indexOf(keyboardLayout).let { if (it < 0) 0 else it }
                    val next = enabled[(currentIndex + 1) % enabled.size]
                    setKeyboardLayout(next)


                    mComposing = ""
                } catch (t: Throwable) {
                    Log.e("IME", "language switch failed", t)

                    setKeyboardLayout(if (keyboardLayout == KeyboardLayout.ENGLISH) Prefs.getKeyboardLayout(this) else KeyboardLayout.ENGLISH)
                }
            }

            Function.IME -> {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                if (imm != null) {
                    try {

                        userInvokedInputMethodPicker = true
                        imm.showInputMethodPicker()
                    } catch (t: Throwable) {
                        Log.e("IME", "showInputMethodPicker failed", t)
                        userInvokedInputMethodPicker = false
                    }
                } else {
                    Log.w("IME", "InputMethodManager is null in Function.IME")
                }
            }

            Function.BACKSPACE -> {
                if (ic != null) {
                    try {
                        if (ic.getSelectedText(0).isNullOrEmpty()) {
                            ic.deleteSurroundingTextInCodePoints(1, 0)
                        } else {
                            ic.commitText("", 1)


                        }
                    } catch (t: Throwable) {
                        Log.e("IME", "BACKSPACE operation failed", t)
                    }
                } else {
                    Log.w("IME", "currentInputConnection is null in BACKSPACE")
                }


                lastChar = null
                lastLetter = null
                positionFlag = ""
                mComposing = ""
            }
            Function.PANEL -> {

                keyboardSymbolsActive = !keyboardSymbolsActive
                updateKeyboard()
            }
        }
        vibrate()
    }

    override fun specialClick(tag: String) {
        val ic = currentInputConnection
        if (ic != null) {
            try {

                val toCommit = if (tag.isNotEmpty() && tag.all { it.isDigit() }) {
                    try {
                        val code = tag.toInt()
                        when (code) {
                            32 -> " "
                            else -> code.toChar().toString()
                        }
                    } catch (t: Throwable) {
                        tag
                    }
                } else tag

                ic.commitText(toCommit, 1)


            } catch (t: Throwable) {
                Log.e("IME", "specialClick commit failed", t)
            }
        } else {
            Log.w("IME", "currentInputConnection is null in specialClick")
        }
        vibrate()

        if (tag == " " || tag == "32") {

            lastChar = null
            lastLetter = null
            positionFlag = ""
            // hide suggestions on space/punctuation
            topBarController?.showNormal()
            debouncer?.cancel()
        }
        checkAutoUnshift()
    }

    override fun eraseDo() {
        val ic = currentInputConnection
        if (ic != null) {
            try {
                ic.deleteSurroundingTextInCodePoints(1, 0)
            } catch (t: Throwable) {
                Log.e("IME", "eraseDo failed", t)
            }
        } else {
            Log.w("IME", "currentInputConnection is null in eraseDo")
        }
    }

    override fun eraseUndo() {

    }

    override fun eraseDone() {

    }

    override fun moveRight() {
        val ic = currentInputConnection
        if (ic != null) {
            try {
                val newCursorPosition = (ic.getTextBeforeCursor(100, 0)?.length ?: 0) + 1
                ic.setSelection(newCursorPosition, newCursorPosition)
            } catch (t: Throwable) {
                Log.e("IME", "moveRight failed", t)
            }
        } else {
            Log.w("IME", "currentInputConnection is null in moveRight")
        }
    }

    override fun moveLeft() {
        val ic = currentInputConnection
        if (ic != null) {
            try {
                val currentCursorPosition = ic.getTextBeforeCursor(100, 0)?.length ?: 0
                if (currentCursorPosition > 0) {
                    ic.setSelection(currentCursorPosition - 1, currentCursorPosition - 1)
                }
            } catch (t: Throwable) {
                Log.e("IME", "moveLeft failed", t)
            }
        } else {
            Log.w("IME", "currentInputConnection is null in moveLeft")
        }
    }

    private fun setKeyboardLayout(layout: KeyboardLayout) {
        keyboardLayout = layout


      try {
          Prefs.setSelectedLayout(this, layout)
        } catch (t: Throwable) {
            Log.e("IME", "Failed to persist selected keyboard layout", t)
       }

         when (layout) {
             KeyboardLayout.ENGLISH -> {
                 keyboardView.setLangIndicator("EN")
                 keyboardView.setLangIndicatorIcon(R.drawable.ic_lang_en)
                 updateKeyboard()
             }
             KeyboardLayout.WIJESEKARA -> {
                 keyboardView.setLangIndicator("SI")
                 keyboardView.setLangIndicatorIcon(R.drawable.ic_lang_si)


                 updateKeyboard()


             }
             KeyboardLayout.SINGLISH -> {
                 keyboardView.setLangIndicator("SI")
                 keyboardView.setLangIndicatorIcon(R.drawable.ic_lang_si)
                 updateKeyboard()
             }
         }
     }

    private fun updateKeyboard() {

        if (keyboardSymbolsActive) {
            try {
                val symMap = if (caps) symbolsMapShifted else symbolsMap
                val letters = mutableMapOf<String, String>()
                for (c in 'a'..'z') {
                    val key = c.toString()
                    letters[key] = symMap[key] ?: ""
                }
                keyboardView.setLetterKeys(letters)

                keyboardView.setNumberKeys(keyLabelsNumbers)
                keyboardView.setSpecialKeys(keyLabelsSpecialEnglish)
                keyboardView.setSecondaryLabels(null)
                return
            } catch (t: Throwable) {
                Log.e("IME", "failed to render symbols keyboard", t)
            }
        }

        val keySet = if (caps) keyLabelsLettersEnglishShifted else keyLabelsLettersEnglish
        var secondaryLabels: Map<String, String>? = null

        when (keyboardLayout) {
            KeyboardLayout.ENGLISH -> {
                keyboardView.setLetterKeys(keySet)
                keyboardView.setNumberKeys(keyLabelsNumbers)
                keyboardView.setSpecialKeys(keyLabelsSpecialEnglish)
                keyboardView.setSecondaryLabels(null)
            }

            KeyboardLayout.WIJESEKARA -> {
                val sinhalaKeySet = if (caps) keyLabelsLettersWijesekaraShifted else keyLabelsLettersWijesekara
                keyboardView.setLetterKeys(sinhalaKeySet)
                keyboardView.setNumberKeys(keyLabelsNumbersWijesekara)
                val specialKeys = if (caps) keyLabelsSpecialWijesekaraSinhalaShifted else keyLabelsSpecialWijesekaraSinhala
                keyboardView.setSpecialKeys(specialKeys)
                keyboardView.setSecondaryLabels(null)


            }

            KeyboardLayout.SINGLISH -> {
                keyboardView.setLetterKeys(keySet)
                keyboardView.setNumberKeys(keyLabelsNumbers)
                keyboardView.setSpecialKeys(keyLabelsSpecialEnglish)


                val labels = mutableMapOf<String, String>()
                for ((k, v) in keySet) {
                    val key = k.lowercase()

                    val charMap = singlishMap[if (caps) key.uppercase() else key]
                    if (charMap != null && charMap != CHAR.EMPTY) {
                        labels[k] = charMap.text
                    }
                }
                keyboardView.setSecondaryLabels(labels)
            }
        }

        keyboardView.buttonActionShift.setImageResource(
            if (caps) R.drawable.ic_shift_pressed
            else R.drawable.ic_shift
        )


        val editorInfo = currentInputEditorInfo
        if (editorInfo != null) {
            val imeAction = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
            val (iconRes, desc) = when (imeAction) {
                EditorInfo.IME_ACTION_GO -> Pair(R.drawable.ic_keyboard_return, "Go")
                EditorInfo.IME_ACTION_SEARCH -> Pair(R.drawable.ic_search, "Search")
                EditorInfo.IME_ACTION_SEND -> Pair(R.drawable.ic_send, "Send")
                EditorInfo.IME_ACTION_NEXT -> Pair(R.drawable.ic_keyboard_arrow_right, "Next")
                EditorInfo.IME_ACTION_DONE -> Pair(R.drawable.ic_check, "Done")
                EditorInfo.IME_ACTION_NONE -> Pair(R.drawable.ic_keyboard_return, "Enter")
                else -> Pair(R.drawable.ic_keyboard_return, "Enter")
            }

            try {
                keyboardView.buttonActionAction.setImageResource(iconRes)
                keyboardView.buttonActionAction.contentDescription = desc
            } catch (t: Throwable) {

                Log.e("IME", "Failed to set action icon resource", t)
                keyboardView.buttonActionAction.setImageResource(R.drawable.ic_keyboard_return)
                keyboardView.buttonActionAction.contentDescription = "Enter"
            }
        } else {

            keyboardView.buttonActionAction.setImageResource(R.drawable.ic_keyboard_return)
            keyboardView.buttonActionAction.contentDescription = "Enter"
        }
    }

    private fun vibrate() {
        if (!Prefs.getVibration(this)) return
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        if (vibrator == null) {
            Log.w("IME", "Vibrator service not available")
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(20)
            }
        } catch (t: Throwable) {
            Log.e("IME", "vibrate failed", t)
        }
    }




    private fun erasePrevious(count: Int = 1) {
        val ic = currentInputConnection ?: return


        fun deleteUnits(units: Int) {
            try {
                ic.deleteSurroundingText(units, 0)
            } catch (t: Throwable) {
                Log.e("IME", "deleteSurroundingText failed in erasePrevious", t)
            }
        }

        if (count == 1) {

            val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (before.length >= 2) {
                val ch = before[before.length - 2]
                if (Character.isHighSurrogate(ch) || Character.isLowSurrogate(ch)) {
                    deleteUnits(2)
                } else {
                    deleteUnits(1)
                }
            } else {
                deleteUnits(1)
            }
        } else {
            deleteUnits(count)
        }


        try {
            val before = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
            if (before == CHAR.ZERO_WIDTH_JOINER.text) {

                deleteUnits(1)

                erasePrevious(1)
            }
        } catch (t: Throwable) {
            Log.e("IME", "post-delete ZWJ check failed", t)
        }
    }
}
