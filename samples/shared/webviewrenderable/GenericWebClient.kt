
import android.graphics.Bitmap
import android.util.Log
import android.webkit.*
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

data class WebViewError(
    val title: String,
    val errorMessage: String
)

/**
 * @author Senthil
 *
 * Custom web view client
 */
class GenericWebClient : WebViewClient() {

    companion object {
        val TAG = GenericWebClient::class.qualifiedName
    }

    private var mShowLoading = MutableLiveData(true)

    // Used to observer if progress bar needs to shown.
    val showLoading: LiveData<Boolean> get() = mShowLoading

    private var mError = MutableLiveData<WebViewError>()
    val showError: LiveData<WebViewError> get() = mError

    private var isPageLoaded = false


    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        mShowLoading.postValue(true)
        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Log.d(TAG, "WebView: onPageStarted")
        isPageLoaded = true
        mShowLoading.postValue(true)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        Log.d(TAG, "WebView: onPageFinished")
        if (isPageLoaded) {
            mShowLoading.postValue(false)
            isPageLoaded = false
        }
        super.onPageFinished(view, url)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        error?.errorCode?.let { code ->
            Log.d(TAG, "WebView: onReceivedError description: ${error.description}")
            when (code) {
                400 -> {
                    showError("Bad Request", error.description as String?)
                }
                ERROR_HOST_LOOKUP, ERROR_CONNECT, ERROR_FAILED_SSL_HANDSHAKE, ERROR_TIMEOUT -> {
                    showError("Connection Error ", error.description as String?)
                }
                else -> Unit
            }
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        errorResponse?.statusCode?.let { code ->
            Log.d(TAG, "WebView: onReceivedHttpError: ${errorResponse.reasonPhrase}")
            if (502 == code)
                showError("Bad Gateway", errorResponse.reasonPhrase)
        }
    }

    /**
     * Show error UI for page error loads
     */
    private fun showError(title: String, message: String?) {
        mError.postValue(message?.let { WebViewError(title, it) })
    }
}