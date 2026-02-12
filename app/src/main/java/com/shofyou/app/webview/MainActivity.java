package com.shofyou.app.webview;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.splashscreen.SplashScreenViewProvider;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.CookieHandler;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Observable;
import java.util.Observer;
import java.util.Stack;
import java.util.UUID;
import java.util.regex.Pattern;

import co.median.android.widget.GoNativeSwipeRefreshLayout;
import co.median.android.widget.MedianProgressView;
import co.median.android.widget.SwipeHistoryNavigationLayout;
import co.median.android.widget.WebViewContainerView;
import co.median.median_core.AppConfig;
import co.median.median_core.ConfigListenerManager;
import co.median.median_core.Bridge;
import co.median.median_core.GNLog;
import co.median.median_core.GoNativeActivity;
import co.median.median_core.GoNativeWebviewInterface;
import co.median.median_core.LeanUtils;
import co.median.median_core.animations.MedianProgressViewItem;
import co.median.median_core.dto.ContextMenuConfig;

public class MainActivity extends AppCompatActivity implements Observer,
        GoNativeActivity,
        GoNativeSwipeRefreshLayout.OnRefreshListener {
    private static final String webviewDatabaseSubdir = "webviewDatabase";
    private static final String TAG = MainActivity.class.getName();
    public static final String INTENT_TARGET_URL = "targetUrl";
    public static final String EXTRA_WEBVIEW_WINDOW_OPEN = "io.gonative.android.MainActivity.Extra.WEBVIEW_WINDOW_OPEN";
    public static final String EXTRA_IGNORE_INTERCEPT_MAXWINDOWS = "ignoreInterceptMaxWindows";
    private static final int REQUEST_PERMISSION_GENERIC = 199;
    private static final int REQUEST_WEBFORM = 300;
    public static final int REQUEST_WEB_ACTIVITY = 400;
    private static final String ON_RESUME_CALLBACK = "median_app_resumed";
    private static final String ON_RESUME_CALLBACK_GN = "gonative_app_resumed";
    private static final String ON_RESUME_CALLBACK_NPM = "_median_app_resumed";
    private static final String CALLBACK_APP_BROWSER_CLOSED = "median_appbrowser_closed";

    private static final String CONFIGURATION_CHANGED = "configurationChanged";
    private static final String SAVED_STATE_ACTIVITY_ID = "activityId";
    private static final String SAVED_STATE_IS_ROOT = "isRoot";
    private static final String SAVED_STATE_URL_LEVEL = "urlLevel";
    private static final String SAVED_STATE_PARENT_URL_LEVEL = "parentUrlLevel";
    private static final String SAVED_STATE_SCROLL_X = "scrollX";
    private static final String SAVED_STATE_SCROLL_Y = "scrollY";
    private static final String SAVED_STATE_WEBVIEW_STATE = "webViewState";
    private static final String SAVED_STATE_IGNORE_THEME_SETUP = "ignoreThemeSetup";

    private static final int CONTEXT_MENU_ID_COPY = 1;
    private static final int CONTEXT_MENU_ID_OPEN = 2;

    private boolean isActivityPaused = false;

    private WebViewContainerView mWebviewContainer;

    private GoNativeWebviewInterface mWebview;
    boolean isPoolWebview = false;
    private Stack<String> backHistory = new Stack<>();

    private View webviewOverlay;
    private String initialUrl;

    private MedianProgressView mProgress;
    private MySwipeRefreshLayout swipeRefreshLayout;
    private SwipeHistoryNavigationLayout swipeNavLayout;
    private RelativeLayout fullScreenLayout;
    private ConnectivityManager cm = null;
    private TabManager tabManager;
    private ActionManager actionManager;
    private SideNavManager sideNavManager;
    private boolean isRoot;
    private boolean webviewIsHidden = false;
    private Handler handler = new Handler();
    private float hideWebviewAlpha = 0.0f;
    private boolean isFirstHideWebview = false;
    private String activityId;

    private final Runnable statusChecker = new Runnable() {
        @Override
        public void run() {
            runOnUiThread(() -> checkReadyStatus());
            handler.postDelayed(statusChecker, 100); // 0.1 sec
        }
    };
    private FileDownloader fileDownloader;
    private FileWriterSharer fileWriterSharer;
    private LoginManager loginManager;
    private RegistrationManager registrationManager;
    private ConnectivityChangeReceiver connectivityReceiver;
    private KeyboardManager keyboardManager;
    private BroadcastReceiver webviewLimitReachedReceiver;
    private boolean startedLoading = false; // document readystate checke
    protected String postLoadJavascript;
    protected String postLoadJavascriptForRefresh;
    private Stack<Bundle>previousWebviewStates;
    private LocationServiceHelper locationServiceHelper;
    private ArrayList<PermissionsCallbackPair> pendingPermissionRequests = new ArrayList<>();
    private ArrayList<Intent> pendingStartActivityAfterPermissions = new ArrayList<>();
    private String connectivityCallback;
    private String connectivityOnceCallback;
    private PhoneStateListener phoneStateListener;
    private SignalStrength latestSignalStrength;
    private boolean restoreBrightnessOnNavigation = false;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> appBrowserActivityLauncher;
    private String deviceInfoCallback = "";
    private boolean flagThemeConfigurationChange = false;
    private boolean isContentReady;
    private SplashScreenViewProvider splashScreenViewProvider;
    private String launchSource;
    private MedianEventsManager eventsManager;
    private String appTheme;
    private String contextMenuUrl;
    private UrlLoader urlLoader;
    private boolean shouldRemoveSplash = false;
    private SystemBarManager systemBarManager;
    private float baseZoomScale = 3f;
    private float currentWebViewZoomScale = -1f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        final AppConfig appConfig = AppConfig.getInstance(this);
        GoNativeApplication application = getGNApplication();
        GoNativeWindowManager windowManager = application.getWindowManager();
        this.isRoot = getIntent().getBooleanExtra("isRoot", true);
        this.launchSource = getIntent().getStringExtra("source");
        this.launchSource = TextUtils.isEmpty(this.launchSource) ? "default" : this.launchSource;

        // Splash events
        if (this.isRoot) {

            // always install splash to prevent theme-related crashes, even on configuration changes
            SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
            boolean configChanged = savedInstanceState != null && savedInstanceState.getBoolean(CONFIGURATION_CHANGED, false);

            if (appConfig.splashScreen.getIsAnimated() && !configChanged) {
                splashScreen.setOnExitAnimationListener(provider -> {
                    this.splashScreenViewProvider = provider;

                    application.mBridge.animatedSplashScreen(this, provider, () -> {
                        if (this.isContentReady) {
                            this.removeSplashWithAnimation();
                        } else {
                            this.shouldRemoveSplash = true;
                        }
                    });

                    new Handler(Looper.getMainLooper()).postDelayed(this::removeSplashWithAnimation, 7000);
                });
            }else {
                splashScreen.setKeepOnScreenCondition(() -> !isContentReady);
                new Handler(Looper.getMainLooper()).postDelayed(this::contentReady, 7000);
            }
        }

        this.systemBarManager = new SystemBarManager(this);

        // Enable edge-to-edge. Must be called after splash setup
        this.systemBarManager.applyEdgeToEdge();

        if (appConfig.keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        this.hideWebviewAlpha  = appConfig.hideWebviewAlpha;

        this.appTheme = ThemeUtils.getConfigAppTheme(this);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            // App theme setup for API 30 and below
            boolean ignoreThemeUpdate = false;
            if (savedInstanceState != null) {
                ignoreThemeUpdate = savedInstanceState.getBoolean(SAVED_STATE_IGNORE_THEME_SETUP, false);
            }

            if (ignoreThemeUpdate) {
                // Ignore app theme setup cause its already called from function setupAppTheme()
                Log.d(TAG, "onCreate: configuration change from setupAppTheme(), ignoring theme setup");
            } else {
                ThemeUtils.setAppThemeApi30AndBelow(appTheme);
            }
        }

        super.onCreate(savedInstanceState);

        this.activityId = UUID.randomUUID().toString();
        int urlLevel = getIntent().getIntExtra("urlLevel", -1);
        int parentUrlLevel = getIntent().getIntExtra("parentUrlLevel", -1);

        if (savedInstanceState != null) {
            this.activityId = savedInstanceState.getString(SAVED_STATE_ACTIVITY_ID, activityId);
            this.isRoot = savedInstanceState.getBoolean(SAVED_STATE_IS_ROOT, isRoot);
            urlLevel = savedInstanceState.getInt(SAVED_STATE_URL_LEVEL, urlLevel);
            parentUrlLevel = savedInstanceState.getInt(SAVED_STATE_PARENT_URL_LEVEL, parentUrlLevel);
        }

        windowManager.addNewWindow(activityId, isRoot);
        windowManager.setUrlLevels(activityId, urlLevel, parentUrlLevel);

        if (appConfig.maxWindowsEnabled) {
            windowManager.setIgnoreInterceptMaxWindows(activityId, getIntent().getBooleanExtra(EXTRA_IGNORE_INTERCEPT_MAXWINDOWS, false));
        }

        if (isRoot) {
            initialRootSetup();
        }

        this.loginManager = application.getLoginManager();

        this.fileWriterSharer = new FileWriterSharer(this);
        this.fileDownloader = new FileDownloader(this);
        this.eventsManager = new MedianEventsManager(this);

        // register launchers
        this.requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            runGonativeDeviceInfo(deviceInfoCallback, false);
        });
        this.appBrowserActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    String callback = LeanUtils.createJsForCallback(CALLBACK_APP_BROWSER_CLOSED, null);
                    runJavascript(callback);
                });

        this.locationServiceHelper = new LocationServiceHelper(this);

        // webview pools
        application.getWebViewPool().init(this);

        cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        setContentView(R.layout.activity_median);
        application.mBridge.onActivityCreate(this, isRoot);

        final ViewGroup content = findViewById(android.R.id.content);

        this.systemBarManager.setupWindowInsetsListener(content);

        if(appConfig.androidFullScreen){
            toggleFullscreen(true);
        }
        // must be done AFTER toggleFullScreen to force screen orientation
        setScreenOrientationPreference();

        this.fullScreenLayout = findViewById(R.id.fullscreen);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        swipeRefreshLayout.setEnabled(appConfig.pullToRefresh);
        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.setCanChildScrollUpCallback(() -> mWebview.getWebViewScrollY() > 10);

        if (appConfig.isAndroidGestureEnabled()) {
            appConfig.swipeGestures = false;
        }
        swipeNavLayout = findViewById(R.id.swipe_history_nav);
        swipeNavLayout.setEnabled(appConfig.swipeGestures);
        swipeNavLayout.setSwipeNavListener(new SwipeHistoryNavigationLayout.OnSwipeNavListener() {
            @Override
            public boolean canSwipeLeftEdge() {
                if (mWebview.getMaxHorizontalScroll() > 0) {
                    if (mWebview.getScrollX() > 0) return false;
                }
                return canGoBack();
            }

            @Override
            public boolean canSwipeRightEdge() {
                if (mWebview.getMaxHorizontalScroll() > 0) {
                    if (mWebview.getScrollX() < mWebview.getMaxHorizontalScroll()) return false;
                }
                return canGoForward();
            }

            @NonNull
            @Override
            public String getGoBackLabel() {
                return "";
            }

            @Override
            public boolean navigateBack() {
                if (appConfig.swipeGestures && canGoBack()) {
                    goBack();
                    return true;
                }
                return false;
            }

            @Override
            public boolean navigateForward() {
                if (appConfig.swipeGestures && canGoForward()) {
                    goForward();
                    return true;
                }
                return false;
            }

            @Override
            public void leftSwipeReachesLimit() {

            }

            @Override
            public void rightSwipeReachesLimit() {

            }

            @Override
            public boolean isSwipeEnabled() {
                return appConfig.swipeGestures;
            }
        });

        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.pull_to_refresh_color));
        swipeNavLayout.setActiveColor(ContextCompat.getColor(this, R.color.pull_to_refresh_color));
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.swipe_nav_background));
        swipeNavLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.swipe_nav_background));

        // Progress indicator setup
        // use custom progress view from plugins if available; otherwise, use default.
        this.mProgress = findViewById(R.id.progress);
        MedianProgressViewItem progressItem = application.mBridge.getProgressView(this);
        if (progressItem != null) {
            this.mProgress.setProgressView(progressItem);
        } else {
            this.mProgress.setupDefaultProgress();
        }

        // proxy cookie manager for httpUrlConnection (syncs to webview cookies)
        CookieHandler.setDefault(new WebkitCookieManagerProxy());


        this.postLoadJavascript = getIntent().getStringExtra("postLoadJavascript");
        this.postLoadJavascriptForRefresh = this.postLoadJavascript;

        this.previousWebviewStates = new Stack<>();

        // tab navigation
        this.tabManager = new TabManager(this, findViewById(R.id.bottom_navigation));
        tabManager.showTabs(false);

        // actions in action bar
        this.actionManager = new ActionManager(this);
        this.actionManager.setupActionBar(isRoot);

        this.sideNavManager = new SideNavManager(this);
        this.sideNavManager.setupNavigationMenu(isRoot);

        // Hide action bar if showActionBar is FALSE and showNavigationMenu is FALSE
        if (!appConfig.showActionBar && !appConfig.showNavigationMenu) {
            Objects.requireNonNull(getSupportActionBar()).hide();
        }

        // WebView setup
        this.webviewOverlay = findViewById(R.id.webviewOverlay);
        this.mWebviewContainer = this.findViewById(R.id.webviewContainer);
        this.mWebview = this.mWebviewContainer.getWebview();

        this.urlLoader = new UrlLoader(this, !appConfig.injectMedianJS);

        this.mWebviewContainer.setupWebview(this, isRoot);
        setupWebviewTheme(appTheme);

        boolean isWebViewStateRestored = false;
        if (savedInstanceState != null) {
            Bundle webViewStateBundle = savedInstanceState.getBundle(SAVED_STATE_WEBVIEW_STATE);
            if (webViewStateBundle != null) {
                // Restore page and history
                mWebview.restoreStateFromBundle(webViewStateBundle);
                isWebViewStateRestored = true;
            }

            // Restore scroll state
            int scrollX = savedInstanceState.getInt(SAVED_STATE_SCROLL_X, 0);
            int scrollY = savedInstanceState.getInt(SAVED_STATE_SCROLL_Y, 0);
            mWebview.scrollTo(scrollX, scrollY);
        }

        // load url
        String url;

        if (isWebViewStateRestored && !TextUtils.isEmpty(mWebview.getUrl())) {
            // WebView already has loaded URL when function mWebview.restoreStateFromBundle() was called
            url = mWebview.getUrl();
        } else {
            Intent intent = getIntent();
            url = getUrlFromIntent(intent);

            if (url == null && isRoot) url = appConfig.getInitialUrl();
            // url from intent (hub and spoke nav)
            if (url == null) url = intent.getStringExtra("url");

            if (url != null) {

                // let plugins add query params to url before loading to WebView
                Map<String, String> queries = application.mBridge.getInitialUrlQueryItems(this, isRoot);
                if (queries != null && !queries.isEmpty()) {
                    Uri.Builder builder = Uri.parse(url).buildUpon();
                    for (Map.Entry<String, String> entry : queries.entrySet()) {
                        builder.appendQueryParameter(entry.getKey(), entry.getValue());
                    }
                    url = builder.build().toString();
                }

                this.initialUrl = url;
                this.mWebview.loadUrl(url);
            } else if (isFromWindowOpenRequest()) {
                // no worries, loadimport android.webkit.GeolocationPermissions;
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
