package com.shofyou.app.webview;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.SslErrorHandler;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

import com.onesignal.OneSignal;
import com.onesignal.debug.LogLevel;
import com.onesignal.Continue;

public class MainActivity extends AppCompatActivity {

    static boolean SngineApp_JSCRIPT = SngineConfig.SngineApp_JSCRIPT;
    static boolean SngineApp_FUPLOAD = SngineConfig.SngineApp_FUPLOAD;
    static boolean SngineApp_CAMUPLOAD = SngineConfig.SngineApp_CAMUPLOAD;
    static boolean SngineApp_ONLYCAM = SngineConfig.SngineApp_ONLYCAM;
    static boolean SngineApp_MULFILE = SngineConfig.SngineApp_MULFILE;
    static boolean SngineApp_LOCATION = SngineConfig.SngineApp_LOCATION;
    static boolean SngineApp_RATINGS = SngineConfig.SngineApp_RATINGS;
    static boolean SngineApp_PULLFRESH = SngineConfig.SngineApp_PULLFRESH;
    static boolean SngineApp_PBAR = SngineConfig.SngineApp_PBAR;
    static boolean SngineApp_ZOOM = SngineConfig.SngineApp_ZOOM;
    static boolean SngineApp_SFORM = SngineConfig.SngineApp_SFORM;
    static boolean SngineApp_OFFLINE = SngineConfig.SngineApp_OFFLINE;
    static boolean SngineApp_EXTURL = SngineConfig.SngineApp_EXTURL;
    static boolean SngineApp_CERT_VERIFICATION = SngineConfig.SngineApp_CERT_VERIFICATION;

    private static String Sngine_URL = SngineConfig.Sngine_URL;
    private String CURR_URL = Sngine_URL;
    private String oneSignalUserID;
    private static String Sngine_F_TYPE = SngineConfig.Sngine_F_TYPE;
    private static String Sngine_ONESIGNAL_APP_ID = SngineConfig.Sngine_ONESIGNAL_APP_ID;
    public static String ASWV_HOST = aswm_host(Sngine_URL);

    WebView swvp_view;
    ProgressBar swvp_progress;
    TextView swvp_loading_text;
    NotificationManager swvp_notification;
    Notification swvp_notification_new;

    private String swvp_cam_message;
    private ValueCallback<Uri> swvp_file_message;
    private ValueCallback<Uri[]> swvp_file_path;
    private final static int swvp_file_req = 1;

    private final static int loc_perm = 1;
    private final static int file_perm = 2;
    private SecureRandom random = new SecureRandom();
    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == swvp_file_req) {
            if (swvp_file_path == null && swvp_file_message == null) return;

            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK) {
                if (intent == null || intent.getData() == null) {
                    if (swvp_cam_message != null) {
                        results = new Uri[]{Uri.parse(swvp_cam_message)};
                    }
                } else {
                    String dataString = intent.getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    } else if (SngineApp_MULFILE && intent.getClipData() != null) {
                        int count = intent.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = intent.getClipData().getItemAt(i).getUri();
                        }
                    }
                }
            }

            if (swvp_file_path != null) {
                swvp_file_path.onReceiveValue(results);
                swvp_file_path = null;
            } else if (swvp_file_message != null) {
                swvp_file_message.onReceiveValue(results != null ? results[0] : null);
                swvp_file_message = null;
            }
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "WrongViewCast"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // جعل شريط الحالة شفافاً ليشغل التطبيق كامل الشاشة (مثل فيسبوك)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        if (!Objects.equals(Sngine_ONESIGNAL_APP_ID, "")) {
            OneSignal.initWithContext(this, Sngine_ONESIGNAL_APP_ID);
            OneSignal.getNotifications().requestPermission(false, Continue.none());
            oneSignalUserID = OneSignal.getUser().getPushSubscription().getId();
        }

        setContentView(R.layout.activity_main);
        swvp_view = findViewById(R.id.msw_view);

        // تحسين الأداء (Speed Up)
        WebSettings webSettings = swvp_view.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setAppCacheEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        webSettings.setEnableSmoothTransition(true);
        
        // منع فتح الروابط في متصفح خارجي
        webSettings.setSupportMultipleWindows(false);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(true);

        final SwipeRefreshLayout pullfresh = findViewById(R.id.pullfresh);
        if (SngineApp_PULLFRESH) {
            pullfresh.setOnRefreshListener(() -> {
                pull_fresh();
                pullfresh.setRefreshing(false);
            });
        }

        swvp_view.setWebViewClient(new Callback());
        swvp_view.setWebChromeClient(new WebChromeClient() {
            // تحسين رفع الصور ليفتح المعرض مباشرة
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (swvp_file_path != null) swvp_file_path.onReceiveValue(null);
                swvp_file_path = filePathCallback;

                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, SngineApp_MULFILE);
                intent.setType("image/* video/*"); // يفتح الصور والفيديوهات معاً
                
                startActivityForResult(Intent.createChooser(intent, "اختر من المعرض"), swvp_file_req);
                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int p) {
                if (SngineApp_PBAR) {
                    swvp_progress = findViewById(R.id.msw_progress);
                    swvp_progress.setVisibility(p < 100 ? View.VISIBLE : View.GONE);
                    swvp_progress.setProgress(p);
                }
            }
        });

        aswm_view(Sngine_URL, false);
    }

    private class Callback extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            findViewById(R.id.msw_welcome).setVisibility(View.GONE);
            findViewById(R.id.msw_view).setVisibility(View.VISIBLE);
            if (oneSignalUserID != null) {
                swvp_view.loadUrl("javascript:saveAndroidOneSignalUserId('" + oneSignalUserID + "')");
            }
        }

        // إجبار كل الروابط على الفتح داخل التطبيق
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.contains(ASWV_HOST)) {
                view.loadUrl(url);
                return true;
            }
            return url_actions(view, url);
        }
    }

    // بقية الدوال (aswm_view, url_actions, etc.) تبقى كما هي مع التأكد من استدعاء aswm_view بشكل صحيح
    void aswm_view(String url, Boolean tab) {
        if (tab) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } else {
            swvp_view.loadUrl(url);
        }
    }

    public static String aswm_host(String url) {
        if (url == null || url.length() == 0) return "";
        return Uri.parse(url).getHost();
    }

    public void pull_fresh() {
        swvp_view.reload();
    }

    public boolean url_actions(WebView view, String url) {
        if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("whatsapp:")) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && swvp_view.canGoBack()) {
            swvp_view.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
