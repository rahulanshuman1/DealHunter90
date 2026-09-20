package app.linkharvest

import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.linkharvest.ui.LinkHarvestApp
import app.linkharvest.ui.LinkHarvestTheme
import app.linkharvest.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        webView = WebView(this)
        viewModel.bind(webView)
        setContent {
            LinkHarvestTheme {
                LinkHarvestApp(webView = webView, vm = viewModel)
            }
        }
    }

    override fun onDestroy() {
        viewModel.unbind()
        webView.stopLoading()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }
}
