package com.dtek.checker

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dtek.checker.databinding.ActivityMainBinding
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupSwipeRefresh()
        binding.btnOpenDtek.setOnClickListener { openDtekInWebView() }
        loadData()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-N970U) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/120.0.0.0 Mobile Safari/537.36"
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            addJavascriptInterface(DtekBridge(), "Android")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript(injectionScript(), null)
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?,
                                             error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        runOnUiThread { showError("Немає інтернет-з'єднання") }
                    }
                }
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.apply {
            setColorSchemeColors(Color.parseColor("#F5C518"))
            setBackgroundColor(Color.parseColor("#0F0F1A"))
            setOnRefreshListener { loadData() }
        }
    }

    private fun loadData() {
        showLoading()
        webView.loadUrl("https://www.dtek-krem.com.ua/ua/shutdowns")
    }

    private fun openDtekInWebView() {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse("https://www.dtek-krem.com.ua/ua/shutdowns"))
        startActivity(intent)
    }

    private fun showLoading() {
        binding.apply {
            progressBar.visibility = View.VISIBLE
            cardStatus.visibility = View.GONE
            cardError.visibility = View.GONE
            tvLastUpdated.visibility = View.GONE
            swipeRefresh.isRefreshing = false
        }
    }

    private fun showError(msg: String) {
        binding.apply {
            progressBar.visibility = View.GONE
            cardStatus.visibility = View.GONE
            cardError.visibility = View.VISIBLE
            tvError.text = msg
            swipeRefresh.isRefreshing = false
        }
    }

    private fun displayResult(data: JSONObject) {
        val hasOutage = data.optBoolean("has_outage", false)
        val reason = data.optString("reason", "").ifEmpty { null }
        val startTime = data.optString("start_time", "").ifEmpty { null }
        val estimatedEnd = data.optString("estimated_end", "").ifEmpty { null }
        val updatedAt = data.optString("updated_at", "").ifEmpty { null }
        val queue = data.optString("queue", "").ifEmpty { null }
        val rawText = data.optString("raw_text", "").ifEmpty { null }

        binding.apply {
            progressBar.visibility = View.GONE
            cardStatus.visibility = View.VISIBLE
            cardError.visibility = View.GONE
            swipeRefresh.isRefreshing = false

            if (hasOutage) {
                tvStatusIcon.text = "🔴"
                tvStatusTitle.text = "Світла немає"
                tvStatusTitle.setTextColor(Color.parseColor("#FF5C5C"))
                tvStatusSubtitle.text = reason ?: "Аварійне відключення"
                cardStatus.setCardBackgroundColor(Color.parseColor("#2A1A1A"))
            } else {
                tvStatusIcon.text = "🟢"
                tvStatusTitle.text = "Світло є"
                tvStatusTitle.setTextColor(Color.parseColor("#4CDE80"))
                tvStatusSubtitle.text = "Електропостачання в нормі"
                cardStatus.setCardBackgroundColor(Color.parseColor("#1A2A1A"))
            }

            // Detail rows
            rowReason.visibility = if (reason != null) View.VISIBLE else View.GONE
            tvReason.text = reason

            rowStart.visibility = if (startTime != null) View.VISIBLE else View.GONE
            tvStart.text = startTime

            rowEnd.visibility = if (estimatedEnd != null) View.VISIBLE else View.GONE
            tvEnd.text = estimatedEnd
            if (estimatedEnd != null) {
                tvEnd.setTextColor(Color.parseColor("#F5C518"))
            }

            rowSiteUpdated.visibility = if (updatedAt != null) View.VISIBLE else View.GONE
            tvSiteUpdated.text = updatedAt

            tvQueue.visibility = if (queue != null) View.VISIBLE else View.GONE
            tvQueue.text = queue

            // Fallback raw text
            if (!hasOutage && reason == null && rawText != null) {
                rowReason.visibility = View.VISIBLE
                tvReason.text = rawText
            }

            val now = SimpleDateFormat("HH:mm dd.MM.yyyy", Locale.getDefault()).format(Date())
            tvLastUpdated.text = "Оновлено: $now"
            tvLastUpdated.visibility = View.VISIBLE
        }
    }

    inner class DtekBridge {
        @JavascriptInterface
        fun onResult(json: String) {
            runOnUiThread {
                try {
                    displayResult(JSONObject(json))
                } catch (e: Exception) {
                    showError("Помилка обробки даних: ${e.message}")
                }
            }
        }

        @JavascriptInterface
        fun onError(msg: String) {
            runOnUiThread { showError(msg) }
        }

        @JavascriptInterface
        fun onLog(msg: String) {
            // debug log from JS
        }
    }

    private fun injectionScript() = """
(function() {
    function log(m) { try { Android.onLog(m); } catch(e){} }

    function clickOption(value, cb) {
        var opts = document.querySelectorAll('[class*="option"]');
        for (var i = 0; i < opts.length; i++) {
            var t = opts[i].textContent.trim();
            if (t === value || t.includes(value)) {
                opts[i].dispatchEvent(new MouseEvent('mousedown', {bubbles:true}));
                opts[i].click();
                log('Selected: ' + value);
                if (cb) setTimeout(cb, 900);
                return true;
            }
        }
        log('Not found: ' + value);
        return false;
    }

    function openDropdown(idx, cb) {
        var controls = document.querySelectorAll('[class*="select__control"],[class*="Select__control"]');
        log('Dropdowns found: ' + controls.length);
        if (controls[idx]) {
            controls[idx].dispatchEvent(new MouseEvent('mousedown', {bubbles:true}));
            controls[idx].click();
            setTimeout(cb, 700);
        } else {
            Android.onError('Не знайдено dropdown #' + idx + ' (всього: ' + controls.length + ')');
        }
    }

    function extractResult() {
        var result = {
            has_outage: false,
            reason: null,
            start_time: null,
            estimated_end: null,
            updated_at: null,
            queue: null,
            raw_text: null
        };

        var body = document.body ? document.body.innerText : '';
        result.has_outage = body.toLowerCase().includes('відсутня електроенергія');

        // Try to find the status block
        var candidates = document.querySelectorAll(
            '[class*="alert"],[class*="notice"],[class*="shutdown"],[class*="status-block"],[class*="info-box"],[class*="infoBlock"]'
        );
        var statusEl = null;
        for (var i = 0; i < candidates.length; i++) {
            var t = candidates[i].innerText || '';
            if (t.length > 30 && (t.includes('електроенергія') || t.includes('відключення') || t.includes('Причина'))) {
                statusEl = candidates[i];
                break;
            }
        }

        if (!statusEl) {
            // scan all divs for the yellow box
            var divs = document.querySelectorAll('div, p, section');
            for (var j = 0; j < divs.length; j++) {
                var dt = divs[j].innerText || '';
                if (dt.includes('Причина') && dt.includes('електроенергія')) {
                    statusEl = divs[j];
                    break;
                }
            }
        }

        if (statusEl) {
            var text = statusEl.innerText;
            result.raw_text = text.trim().substring(0, 500);
            var lines = text.split('\n').map(function(l){ return l.trim(); }).filter(function(l){ return l.length > 0; });
            lines.forEach(function(line) {
                var ll = line.toLowerCase();
                if (ll.includes('причина')) result.reason = line.split(':').slice(1).join(':').trim();
                else if (ll.includes('час початку') || ll.includes('початок')) result.start_time = line.split('–').slice(1).join('–').trim();
                else if (ll.includes('відновлення')) result.estimated_end = line.split('–').slice(1).join('–').trim();
                else if (ll.includes('оновлення')) result.updated_at = line.split('–').slice(1).join('–').trim();
            });
        }

        var qm = body.match(/Черга\s+[\d.]+/);
        if (qm) result.queue = qm[0];

        log('Result: ' + JSON.stringify(result));
        Android.onResult(JSON.stringify(result));
    }

    log('Script injected, starting selection...');
    setTimeout(function() {
        // Step 1: city
        openDropdown(0, function() {
            setTimeout(function() {
                if (!clickOption('м. Українка', function() {
                    setTimeout(function() {
                        // Step 2: street
                        openDropdown(1, function() {
                            setTimeout(function() {
                                if (!clickOption('СТ Мрія', function() {
                                    setTimeout(function() {
                                        // Step 3: house
                                        openDropdown(2, function() {
                                            setTimeout(function() {
                                                if (!clickOption('25', function() {
                                                    setTimeout(extractResult, 2500);
                                                })) {
                                                    // try numeric
                                                    if (!clickOption('25', null)) {
                                                        setTimeout(extractResult, 1000);
                                                    }
                                                }
                                            }, 400);
                                        });
                                    }, 400);
                                })) {
                                    Android.onError('Вулицю СТ Мрія не знайдено у списку');
                                }
                            }, 400);
                        });
                    }, 400);
                })) {
                    Android.onError('Місто Українка не знайдено у списку');
                }
            }, 400);
        });
    }, 2000);
})();
""".trimIndent()
}
