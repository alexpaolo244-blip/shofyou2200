package com.shofyou.app.webview;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ValueCallback<Uri[]> filePathCallback;

    private final String WEBSITE = "https://shofyou.com";

    private final ActivityResultLauncher<String> fileChooser =
            registerForActivityResult(new ActivityResultContracts.GetMultipleContents(),
                    uris -> {
                        if (filePathCallback != null) {
                            Uri[] results = uris.toArray(new Uri[0]);
                            filePathCallback.onReceiveValue(results);
                            filePathCallback = null;
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        swipeRefreshLayout = new SwipeRefreshLayout(this);
        webView = new WebView(this);

        swipeRefreshLayout.addView(webView);
        setContentView(swipeRefreshLayout);

        setupWebView();

        swipeRefreshLayout.setOnRefreshListener(() -> webView.reload());

        webView.loadUrl(WEBSITE);
    }

    private void setupWebView() {

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                swipeRefreshLayout.setRefreshing(true);

                // تعطيل السحب في صفحة الريلز
                if (url.contains("reels")) {
                    swipeRefreshLayout.setEnabled(false);
                } else {
                    swipeRefreshLayout.setEnabled(true);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {

                String url = request.getUrl().toString();

                if (url.startsWith(WEBSITE)) {
                    return false;
                } else {
                    openPopup(url);
                    return true;
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {

                MainActivity.this.filePathCallback = filePathCallback;
                fileChooser.launch("*/*"); // يفتح المعرض مباشرة
                return true;
            }
        });
    }

    private void openPopup(String url) {

        Dialog dialog = new Dialog(this);
        WebView popupWebView = new WebView(this);

        popupWebView.getSettings().setJavaScriptEnabled(true);
        popupWebView.loadUrl(url);

        dialog.setContentView(popupWebView);
        dialog.getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );

        dialog.show();
    }

    @Override
    public void onBackPressed() {

        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
