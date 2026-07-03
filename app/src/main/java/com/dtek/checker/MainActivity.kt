package com.dtek.checker

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var tvIcon: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvDetails: TextView
    private lateinit var tvTime: TextView
    private lateinit var btnRefresh: Button
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor     = Color.parseColor("#0F0F1A")
        window.navigationBarColor = Color.parseColor("#0F0F1A")
        buildUI()
        setupWebView()
        loadData()
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F0F1A"))
        }

        swipe = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(Color.parseColor("#F5C518"))
            setProgressBackgroundColorSchemeColor(Color.parseColor("#1A1A2E"))
        }

        val scroll = ScrollView(this)
        val inner  = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(40))
        }
        scroll.addView(inner, mpWrap())
        swipe.addView(scroll)
        root.addView(swipe, mpMp())
        swipe.setOnRefreshListener { loadData() }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
        }
        header.addView(tv("⚡ DTEK", 22f, "#F5C518", bold = true), wrapW(weight = 1f))
        header.addView(tv("Українка · СТ Мрія · 25", 12f, "#6666AA"))
        inner.addView(header, mbLp(dp(28)))

        // Icon
        tvIcon = tv("⏳", 52f, "#FFFFFF").also { it.gravity = Gravity.CENTER }
        inner.addView(tvIcon, mbLp(dp(8)))

        // Title
        tvTitle = tv("Завантаження...", 22f, "#F5C518", bold = true).also {
            it.gravity = Gravity.CENTER
        }
        inner.addView(tvTitle, mbLp(dp(16)))

        // Details card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        tvDetails = tv("", 14f, "#AAAACC").also { it.setLineSpacing(0f, 1.6f) }
        card.addView(tvDetails)
        inner.addView(card, mbLp(dp(16)))

        // Time
        tvTime = tv("", 12f, "#555577").also { it.gravity = Gravity.CENTER }
        inner.addView(tvTime, mbLp(dp(20)))

        // Refresh button
        btnRefresh = Button(this).apply {
            text = "🔄 Оновити"
            textSize = 16f
            setTextColor(Color.parseColor("#0F0F1A"))
            setBackgroundColor(Color.parseColor("#F5C518"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setOnClickListener { loadData() }
        }
        inner.addView(btnRefresh, mbLp(dp(8)))

        // Open site
        inner.addView(Button(this).apply {
            text = "Відкрити сайт DTEK ↗"
            textSize = 13f
            setTextColor(Color.parseColor("#8888CC"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.dtek-krem.com.ua/ua/shutdowns")))
            }
        }, mbLp(0))

        setContentView(root)
    }

    // ── WebView ───────────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode         = WebSettings.LOAD_NO_CACHE
                userAgentString   =
                    "Mozilla/5.0 (Linux; Android 13; SM-N770F) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Mobile Safari/537.36"
            }
            addJavascriptInterface(Bridge(), "Android")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript(JS_SCRIPT, null)
                }
                override fun onReceivedError(
                    view: WebView?, req: WebResourceRequest?, err: WebResourceError?
                ) {
                    if (req?.isForMainFrame == true)
                        runOnUiThread { showError("Немає з'єднання з інтернетом") }
                }
            }
        }
    }

    private fun loadData() {
        swipe.isRefreshing   = false
        btnRefresh.isEnabled = false
        tvIcon.text          = "⏳"
        tvTitle.text         = "Перевіряємо..."
        tvTitle.setTextColor(Color.parseColor("#F5C518"))
        tvDetails.text       = "Завантажуємо дані з сайту DTEK…"
        tvTime.text          = ""
        webView.loadUrl("https://www.dtek-krem.com.ua/ua/shutdowns")
    }

    // ── JS Bridge ─────────────────────────────────────────────────────────────

    inner class Bridge {
        @JavascriptInterface
        fun onResult(json: String) = runOnUiThread {
            btnRefresh.isEnabled = true
            try {
                val d         = JSONObject(json)
                val hasOutage = d.optBoolean("has_outage", false)
                val reason    = d.optString("reason").ifEmpty { null }
                val start     = d.optString("start_time").ifEmpty { null }
                val end       = d.optString("estimated_end").ifEmpty { null }
                val updated   = d.optString("updated_at").ifEmpty { null }
                val queue     = d.optString("queue").ifEmpty { null }
                val raw       = d.optString("raw_text").ifEmpty { null }

                if (hasOutage) {
                    tvIcon.text = "🔴"
                    tvTitle.text = "СВІТЛА НЕМАЄ"
                    tvTitle.setTextColor(Color.parseColor("#FF5C5C"))
                    val sb = StringBuilder()
                    reason?.let  { sb.appendLine("⚠️ Причина: $it") }
                    start?.let   { sb.appendLine("🕐 Початок: $it") }
                    end?.let     { sb.appendLine("🕓 Очікуване відновлення: $it") }
                    queue?.let   { sb.appendLine("📋 $it") }
                    updated?.let { sb.appendLine("\nОновлено на сайті: $it") }
                    if (sb.isEmpty()) raw?.take(300)?.let { sb.append(it) }
                    tvDetails.text = sb.toString().trim()
                } else {
                    tvIcon.text = "🟢"
                    tvTitle.text = "СВІТЛО Є"
                    tvTitle.setTextColor(Color.parseColor("#4CDE80"))
                    tvDetails.text = "Аварійних відключень за вашою адресою не зафіксовано."
                }
                tvTime.text = "Перевірено: " +
                    SimpleDateFormat("HH:mm  dd.MM.yyyy", Locale.getDefault()).format(Date())
            } catch (e: Exception) { showError("Помилка: ${e.message}") }
        }

        @JavascriptInterface
        fun onError(msg: String) = runOnUiThread {
            btnRefresh.isEnabled = true
            showError(msg)
        }
    }

    private fun showError(msg: String) {
        tvIcon.text = "⚠️"
        tvTitle.text = "Помилка"
        tvTitle.setTextColor(Color.parseColor("#FF8888"))
        tvDetails.text = msg
        btnRefresh.isEnabled = true
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun mpMp() = LinearLayout.LayoutParams(-1, -1)
    private fun mpWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun wrapW(weight: Float = 0f) =
        LinearLayout.LayoutParams(-2, -2, weight)
    private fun mbLp(mb: Int = 0) =
        LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = mb }

    private fun tv(
        text: String, size: Float, color: String, bold: Boolean = false
    ) = TextView(this).apply {
        this.text  = text
        textSize   = size
        setTextColor(Color.parseColor(color))
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    // ── JS ────────────────────────────────────────────────────────────────────

    companion object {
        private val JS_SCRIPT = """
(function() {
  var CITY   = 'м. Українка';
  var STREET = 'СТ Мрія';
  var HOUSE  = '25';

  function selectOption(value, done) {
    var opts = document.querySelectorAll('[class*="option"]');
    for (var i = 0; i < opts.length; i++) {
      var t = opts[i].textContent.trim();
      if (t === value || t.indexOf(value) === 0) {
        opts[i].dispatchEvent(new MouseEvent('mousedown', {bubbles:true}));
        opts[i].click();
        if (done) setTimeout(done, 1000);
        return true;
      }
    }
    return false;
  }

  function openDropdown(idx, done) {
    var ctrls = document.querySelectorAll('[class*="control"]');
    if (ctrls[idx]) {
      ctrls[idx].dispatchEvent(new MouseEvent('mousedown', {bubbles:true}));
      ctrls[idx].click();
      setTimeout(done, 800);
    }
  }

  function extract() {
    var body = document.body ? (document.body.innerText || '') : '';
    var result = { has_outage: false, reason: null, start_time: null,
                   estimated_end: null, updated_at: null, queue: null, raw_text: null };

    result.has_outage = body.toLowerCase().indexOf('відсутня електроенергія') >= 0;

    var elems = document.querySelectorAll('div, p, section');
    for (var i = 0; i < elems.length; i++) {
      var t = elems[i].innerText || '';
      if (t.indexOf('Причина') >= 0 || t.toLowerCase().indexOf('відсутня') >= 0) {
        result.raw_text = t.substring(0, 500);
        var lines = t.split('\n');
        for (var j = 0; j < lines.length; j++) {
          var l = lines[j].trim(); var ll = l.toLowerCase();
          if (ll.indexOf('причина') >= 0)
            result.reason = l.split(':').slice(1).join(':').trim();
          else if (ll.indexOf('початок') >= 0 || ll.indexOf('початку') >= 0)
            result.start_time = l.split('–').slice(1).join('–').trim();
          else if (ll.indexOf('відновлення') >= 0)
            result.estimated_end = l.split('–').slice(1).join('–').trim();
          else if (ll.indexOf('оновлення') >= 0)
            result.updated_at = l.split('–').slice(1).join('–').trim();
        }
        break;
      }
    }
    var qm = body.match(/Черга\s+[\d.]+/);
    if (qm) result.queue = qm[0];
    Android.onResult(JSON.stringify(result));
  }

  setTimeout(function() {
    openDropdown(0, function() {
      setTimeout(function() {
        selectOption(CITY, function() {
          setTimeout(function() {
            openDropdown(1, function() {
              setTimeout(function() {
                selectOption(STREET, function() {
                  setTimeout(function() {
                    openDropdown(2, function() {
                      setTimeout(function() {
                        selectOption(HOUSE, function() {
                          setTimeout(extract, 2500);
                        });
                      }, 500);
                    });
                  }, 500);
                });
              }, 500);
            });
          }, 500);
        });
      }, 500);
    });
  }, 2000);
})();
""".trimIndent()
    }
}
