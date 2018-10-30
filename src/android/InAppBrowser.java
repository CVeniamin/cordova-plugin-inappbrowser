/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
*/
package org.apache.cordova.inappbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.annotation.SuppressLint;
import android.animation.LayoutTransition;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.BitmapFactory;

import android.Manifest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;

import android.provider.Browser;
import android.provider.Settings;
import android.provider.MediaStore;
import android.R.id;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import android.util.TypedValue;

import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.InputMethodManager;

import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.HttpAuthHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient.FileChooserParams;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient.CustomViewCallback;

import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.Config;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaHttpAuthHandler;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginManager;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.ArrayList;
import java.util.List;

@SuppressLint("SetJavaScriptEnabled")
public class InAppBrowser extends CordovaPlugin {

	private static final String NULL = "null";
	protected static final String LOG_TAG = "InAppBrowser";
	private static final String SELF = "_self";
	private static final String SYSTEM = "_system";
	private static final String EXIT_EVENT = "exit";
	private static final String LOCATION = "location";
	private static final String ZOOM = "zoom";
	private static final String HIDDEN = "hidden";
	private static final String LOAD_START_EVENT = "loadstart";
	private static final String LOAD_STOP_EVENT = "loadstop";
	private static final String LOAD_ERROR_EVENT = "loaderror";
	private static final String CLEAR_ALL_CACHE = "clearcache";
	private static final String CLEAR_SESSION_CACHE = "clearsessioncache";
	private static final String HARDWARE_BACK_BUTTON = "hardwareback";
	private static final String MEDIA_PLAYBACK_REQUIRES_USER_ACTION = "mediaPlaybackRequiresUserAction";
	private static final String SHOULD_PAUSE = "shouldPauseOnSuspend";
	private static final Boolean DEFAULT_HARDWARE_BACK = true;
	private static final String USER_WIDE_VIEW_PORT = "useWideViewPort";

	private InAppBrowserDialog dialog;
	private WebView inAppWebView;
	// private EditText edittext;
	
	private CallbackContext callbackContext;
	private boolean showLocationBar = true;
	private boolean showZoomControls = true;
	private boolean openWindowHidden = false;
	private boolean clearAllCache = false;
	private boolean clearSessionCache = false;
	private boolean hadwareBackButton = true;
	private boolean mediaPlaybackRequiresUserGesture = false;
	private boolean shouldPauseInAppBrowser = false;
	private boolean useWideViewPort = true;

	private final static int FILECHOOSER_REQUEST_CODE = 5372;

	private final int PERMISSIONS_CAMERA_AUDIO = 112;

	private PermissionRequest _permissionRequest;

	private FrameLayout mFullscreenContainer;
	private InAppChromeClient mFullScreenWebView;

	/** File upload callback for platform versions prior to Android 5.0 */
	protected ValueCallback<Uri> mFileUploadCallbackFirst;

	/** File upload callback for Android 5.0+ */
	protected ValueCallback<Uri[]> mFileUploadCallbackSecond;
	
	/**
	* Executes the request and returns PluginResult.
	*
	* @param action the action to execute.
	* @param args JSONArry of arguments for the plugin.
	* @param callbackContext the callbackContext used when calling back into JavaScript.
	* @return A PluginResult object with a status and message.
	*/
	public boolean execute(String action, CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
	if (action.equals("open")) {
		this.callbackContext = callbackContext;
		final String url = args.getString(0);
		String t = args.optString(1);
		if (t == null || t.equals("") || t.equals(NULL)) {
			t = SELF;
		}
		final String target = t;
		final HashMap<String, Boolean> features = parseFeature(args.optString(2));

		LOG.d(LOG_TAG, "target = " + target);

		this.cordova.getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				String result = "";
				// SELF
				if (SELF.equals(target)) {
					LOG.d(LOG_TAG, "in self");
					/* This code exists for compatibility between 3.x and 4.x versions of Cordova.
					 * Previously the Config class had a static method, isUrlWhitelisted(). That
					 * responsibility has been moved to the plugins, with an aggregating method in
					 * PluginManager.
					 */
					Boolean shouldAllowNavigation = null;
					if (url.startsWith("javascript:")) {
						shouldAllowNavigation = true;
					}
					if (shouldAllowNavigation == null) {
						try {
							Method iuw = Config.class.getMethod("isUrlWhiteListed", String.class);
							shouldAllowNavigation = (Boolean)iuw.invoke(null, url);
						} catch (NoSuchMethodException e) {
							LOG.d(LOG_TAG, e.getLocalizedMessage());
						} catch (IllegalAccessException e) {
							LOG.d(LOG_TAG, e.getLocalizedMessage());
						} catch (InvocationTargetException e) {
							LOG.d(LOG_TAG, e.getLocalizedMessage());
						}
					}
					if (shouldAllowNavigation == null) {
						try {
							Method gpm = webView.getClass().getMethod("getPluginManager");
							PluginManager pm = (PluginManager)gpm.invoke(webView);
							Method san = pm.getClass().getMethod("shouldAllowNavigation", String.class);
							shouldAllowNavigation = (Boolean)san.invoke(pm, url);
						} catch (NoSuchMethodException e) {
							LOG.d(LOG_TAG, e.getLocalizedMessage());
						} catch (IllegalAccessException e) {
							LOG.d(LOG_TAG, e.getLocalizedMessage());
						} catch (InvocationTargetException e) {
							LOG.d(LOG_TAG, e.getLocalizedMessage());
						}
					}
					// load in webview
					if (Boolean.TRUE.equals(shouldAllowNavigation)) {
						LOG.d(LOG_TAG, "loading in webview");
						webView.loadUrl(url);
					}
					//Load the dialer
					else if (url.startsWith(WebView.SCHEME_TEL))
					{
						try {
							LOG.d(LOG_TAG, "loading in dialer");
							Intent intent = new Intent(Intent.ACTION_DIAL);
							intent.setData(Uri.parse(url));
							cordova.getActivity().startActivity(intent);
						} catch (android.content.ActivityNotFoundException e) {
							LOG.e(LOG_TAG, "Error dialing " + url + ": " + e.toString());
						}
					}
					// load in InAppBrowser
					else {
						LOG.d(LOG_TAG, "loading in InAppBrowser");
						result = showWebPage(url, features);
					}
				}
				// SYSTEM
				else if (SYSTEM.equals(target)) {
					LOG.d(LOG_TAG, "in system");
					result = openExternal(url);
				}
				// BLANK - or anything else
				else {
					LOG.d(LOG_TAG, "in blank");
					result = showWebPage(url, features);
				}

				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
				pluginResult.setKeepCallback(true);
				callbackContext.sendPluginResult(pluginResult);
			}
		});
	}
	else if (action.equals("close")) {
		closeDialog();
	}
	else if (action.equals("injectScriptCode")) {
		String jsWrapper = null;
		if (args.getBoolean(1)) {
			jsWrapper = String.format("(function(){prompt(JSON.stringify([eval(%%s)]), 'gap-iab://%s')})()", callbackContext.getCallbackId());
		}
		injectDeferredObject(args.getString(0), jsWrapper);
	}
	else if (action.equals("injectScriptFile")) {
		String jsWrapper;
		if (args.getBoolean(1)) {
			jsWrapper = String.format("(function(d) { var c = d.createElement('script'); c.src = %%s; c.onload = function() { prompt('', 'gap-iab://%s'); }; d.body.appendChild(c); })(document)", callbackContext.getCallbackId());
		} else {
			jsWrapper = "(function(d) { var c = d.createElement('script'); c.src = %s; d.body.appendChild(c); })(document)";
		}
		injectDeferredObject(args.getString(0), jsWrapper);
	}
	else if (action.equals("injectStyleCode")) {
		String jsWrapper;
		if (args.getBoolean(1)) {
			jsWrapper = String.format("(function(d) { var c = d.createElement('style'); c.innerHTML = %%s; d.body.appendChild(c); prompt('', 'gap-iab://%s');})(document)", callbackContext.getCallbackId());
		} else {
			jsWrapper = "(function(d) { var c = d.createElement('style'); c.innerHTML = %s; d.body.appendChild(c); })(document)";
		}
		injectDeferredObject(args.getString(0), jsWrapper);
	}
	else if (action.equals("injectStyleFile")) {
		String jsWrapper;
		if (args.getBoolean(1)) {
			jsWrapper = String.format("(function(d) { var c = d.createElement('link'); c.rel='stylesheet'; c.type='text/css'; c.href = %%s; d.head.appendChild(c); prompt('', 'gap-iab://%s');})(document)", callbackContext.getCallbackId());
		} else {
			jsWrapper = "(function(d) { var c = d.createElement('link'); c.rel='stylesheet'; c.type='text/css'; c.href = %s; d.head.appendChild(c); })(document)";
		}
		injectDeferredObject(args.getString(0), jsWrapper);
	}
	else if (action.equals("show")) {
		this.cordova.getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if(dialog != null){
					dialog.show();
				}
			}
		});
		PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
		pluginResult.setKeepCallback(true);
		this.callbackContext.sendPluginResult(pluginResult);
	}
	else if (action.equals("hide")) {
		this.cordova.getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if(dialog != null){
					dialog.hide();
				}
			}
		});
		PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
		pluginResult.setKeepCallback(true);
		this.callbackContext.sendPluginResult(pluginResult);
	}
	else if (action.equals("goToSettings")) {
		final Activity activity = this.cordova.getActivity();
		if(dontKeepActivitiesEnabled(activity)){
			this.cordova.getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					String title = "Developer Options Detected!";
					String message = "In order for GTribe to work properly, on your device, please uncheck the \"Don't keep activities\" option.";
					goToSettings(activity, title, message);
				}
			});
		}
	}
	else {
		return false;
	}
		return true;
	}

	private void goToSettings(final Activity activity, String title, String message){
		new AlertDialog.Builder(activity)
		.setTitle(title)
		.setMessage(message)
		.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface negativeDialog, int whichButton) {
				if(negativeDialog != null){
					negativeDialog.dismiss();
					negativeDialog = null;
				}
			}
		})
		.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface positiveDialog, int arg1) {
				Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
				intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
				if(activity != null){
					activity.startActivity(intent);
					activity.finish();
				}
				if(positiveDialog != null){
					positiveDialog.dismiss();
					positiveDialog = null;
				}
			}
		}).create().show();
	}

	private boolean dontKeepActivitiesEnabled(Activity activity) {
		return Settings.System.getInt(activity.getApplicationContext().getContentResolver(), Settings.Global.ALWAYS_FINISH_ACTIVITIES, 0) == 1;
	}

	/**
	* Called when the view navigates.
	*/
	@Override
	public void onReset() {
		closeDialog();
	}

	/**
	* Called when the system is about to start resuming a previous activity.
	*/
	@Override
	public void onPause(boolean multitasking) {
		if (shouldPauseInAppBrowser) {
			inAppWebView.onPause();
		}
	}

	/**
	* Called when the activity will start interacting with the user.
	*/
	@Override
	public void onResume(boolean multitasking) {
		if (shouldPauseInAppBrowser) {
			inAppWebView.onResume();
		}
	}

	/**
	* Called by AccelBroker when listener is to be shut down.
	* Stop listener.
	*/
	public void onDestroy() {
		closeDialog();
	}

	/**
	* Inject an object (script or style) into the InAppBrowser WebView.
	*
	* This is a helper method for the inject{Script|Style}{Code|File} API calls, which
	* provides a consistent method for injecting JavaScript code into the document.
	*
	* If a wrapper string is supplied, then the source string will be JSON-encoded (adding
	* quotes) and wrapped using string formatting. (The wrapper string should have a single
	* '%s' marker)
	*
	* @param source      The source object (filename or script/style text) to inject into
	*                    the document.
	* @param jsWrapper   A JavaScript string to wrap the source string in, so that the object
	*                    is properly injected, or null if the source string is JavaScript text
	*                    which should be executed directly.
	*/
	private void injectDeferredObject(String source, String jsWrapper) {
	if (inAppWebView!=null) {
		String scriptToInject;
		if (jsWrapper != null) {
			org.json.JSONArray jsonEsc = new org.json.JSONArray();
			jsonEsc.put(source);
			String jsonRepr = jsonEsc.toString();
			String jsonSourceString = jsonRepr.substring(1, jsonRepr.length()-1);
			scriptToInject = String.format(jsWrapper, jsonSourceString);
		} else {
			scriptToInject = source;
		}
		final String finalScriptToInject = scriptToInject;
		this.cordova.getActivity().runOnUiThread(new Runnable() {
			@SuppressLint("NewApi")
			@Override
			public void run() {
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
					// This action will have the side-effect of blurring the currently focused element
					inAppWebView.loadUrl("javascript:" + finalScriptToInject);
				} else {
					inAppWebView.evaluateJavascript(finalScriptToInject, null);
				}
			}
		});
	} else {
		LOG.d(LOG_TAG, "Can't inject code into the system browser");
	}
	}

	/**
	* Put the list of features into a hash map
	*
	* @param optString
	* @return
	*/
	private HashMap<String, Boolean> parseFeature(String optString) {
	if (optString.equals(NULL)) {
		return null;
	} else {
		HashMap<String, Boolean> map = new HashMap<String, Boolean>();
		StringTokenizer features = new StringTokenizer(optString, ",");
		StringTokenizer option;
		while(features.hasMoreElements()) {
			option = new StringTokenizer(features.nextToken(), "=");
			if (option.hasMoreElements()) {
				String key = option.nextToken();
				Boolean value = option.nextToken().equals("no") ? Boolean.FALSE : Boolean.TRUE;
				map.put(key, value);
			}
		}
		return map;
	}
	}

	/**
	* Display a new browser with the specified URL.
	*
	* @param url the url to load.
	* @return "" if ok, or error message.
	*/
	public String openExternal(String url) {
	try {
		Intent intent = null;
		intent = new Intent(Intent.ACTION_VIEW);
		// Omitting the MIME type for file: URLs causes "No Activity found to handle Intent".
		// Adding the MIME type to http: URLs causes them to not be handled by the downloader.
		Uri uri = Uri.parse(url);
		if ("file".equals(uri.getScheme())) {
			intent.setDataAndType(uri, webView.getResourceApi().getMimeType(uri));
		} else {
			intent.setData(uri);
		}
		intent.putExtra(Browser.EXTRA_APPLICATION_ID, cordova.getActivity().getPackageName());
		this.cordova.getActivity().startActivity(intent);
		return "";
	// not catching FileUriExposedException explicitly because buildtools<24 doesn't know about it
	} catch (java.lang.RuntimeException e) {
		LOG.d(LOG_TAG, "InAppBrowser: Error loading url "+url+":"+ e.toString());
		return e.toString();
	}
	}

	/**
	* Closes the dialog
	*/
	public void closeDialog() {
	this.cordova.getActivity().runOnUiThread(new Runnable() {
		@Override
		public void run() {
			final WebView childView = inAppWebView;
			// The JS protects against multiple calls, so this should happen only when
			// closeDialog() is called by other native code.
			if (childView == null) {
				return;
			}

			childView.setWebViewClient(new WebViewClient() {
				// NB: wait for about:blank before dismissing
				public void onPageFinished(WebView view, String url) {
					Context context = view.getContext();			    

					if(dialog == null || !dialog.isShowing()){
						return;
					}

					if(context instanceof Activity) {
						if(!((Activity)context).isFinishing() && dialog != null) {
							dialog.dismiss();
							dialog = null;
						}
					}else if(dialog.isShowing() && dialog != null) {
						dialog.dismiss();
						dialog = null;
					}
				}
			});
			// NB: From SDK 19: "If you call methods on WebView from any thread
			// other than your app's UI thread, it can cause unexpected results."
			// http://developer.android.com/guide/webapps/migrating.html#Threads
			childView.loadUrl("about:blank");

			try {
				JSONObject obj = new JSONObject();
				obj.put("type", EXIT_EVENT);
				sendUpdate(obj, false);
			} catch (JSONException ex) {
				LOG.d(LOG_TAG, "Should never happen");
			}
		}
	});
	}

	/**
	* Checks to see if it is possible to go back one page in history, then does so.
	*/
	public void goBack() {
	if (this.inAppWebView.canGoBack()) {
		this.inAppWebView.goBack();
	}
	}

	/**
	* Can the web browser go back?
	* @return boolean
	*/
	public boolean canGoBack() {
		return this.inAppWebView.canGoBack();
	}
	
	public void hideCustomView() {
		mFullScreenWebView.onHideCustomView();
	}

	/**
	* Has the user set the hardware back button to go back
	* @return boolean
	*/
	public boolean hardwareBack() {
		return hadwareBackButton;
	}

	/**
	* Checks to see if it is possible to go forward one page in history, then does so.
	*/
	private void goForward() {
	if (this.inAppWebView.canGoForward()) {
		this.inAppWebView.goForward();
	}
	}

	/**
	* Navigate to the new page
	*
	* @param url to load
	*/
	private void navigate(String url) {
		InputMethodManager imm = (InputMethodManager)this.cordova.getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(this.inAppWebView.getWindowToken(), 0);

		if (!url.startsWith("http") && !url.startsWith("file:")) {
			this.inAppWebView.loadUrl("http://" + url);
		} else {
			this.inAppWebView.loadUrl(url);
		}
		this.inAppWebView.requestFocus();
	}


	/**
	* Should we show the location bar?
	*
	* @return boolean
	*/
	private boolean getShowLocationBar() {
	return this.showLocationBar;
	}

	private InAppBrowser getInAppBrowser(){
		return this;
	}

	/**
	* Display a new browser with the specified URL.
	*
	* @param url the url to load.
	* @param features jsonObject
	*/
	public String showWebPage(final String url, HashMap<String, Boolean> features) {
		// Determine if we should hide the location bar.
		showLocationBar = false;
		showZoomControls = false;
		openWindowHidden = false;
		mediaPlaybackRequiresUserGesture = false;

		if (features != null) {
			Boolean show = features.get(LOCATION);
			if (show != null) {
				showLocationBar = show.booleanValue();
			}
			Boolean zoom = features.get(ZOOM);
			if (zoom != null) {
				showZoomControls = zoom.booleanValue();
			}
			Boolean hidden = features.get(HIDDEN);
			if (hidden != null) {
				openWindowHidden = hidden.booleanValue();
			}
			Boolean hardwareBack = features.get(HARDWARE_BACK_BUTTON);
			if (hardwareBack != null) {
				hadwareBackButton = hardwareBack.booleanValue();
			} else {
				hadwareBackButton = DEFAULT_HARDWARE_BACK;
			}
			Boolean mediaPlayback = features.get(MEDIA_PLAYBACK_REQUIRES_USER_ACTION);
			if (mediaPlayback != null) {
				mediaPlaybackRequiresUserGesture = mediaPlayback.booleanValue();
			}
			Boolean cache = features.get(CLEAR_ALL_CACHE);
			if (cache != null) {
				clearAllCache = cache.booleanValue();
			} else {
				cache = features.get(CLEAR_SESSION_CACHE);
				if (cache != null) {
					clearSessionCache = cache.booleanValue();
				}
			}
			Boolean shouldPause = features.get(SHOULD_PAUSE);
			if (shouldPause != null) {
				shouldPauseInAppBrowser = shouldPause.booleanValue();
			}
			Boolean wideViewPort = features.get(USER_WIDE_VIEW_PORT);
			if (wideViewPort != null ) {
					useWideViewPort = wideViewPort.booleanValue();
			}
		}

		final CordovaWebView thatWebView = this.webView;

		// Create dialog in new thread
		Runnable runnable = new Runnable() {
			/**
			 * Convert our DIP units to Pixels
			 *
			 * @return int
			 */
			private int dpToPixels(int dipValue) {
				int value = (int) TypedValue.applyDimension( TypedValue.COMPLEX_UNIT_DIP,
															(float) dipValue,
															cordova.getActivity().getResources().getDisplayMetrics()
				);

				return value;
			}

			@SuppressLint("NewApi")
			public void run() {

				// CB-6702 InAppBrowser hangs when opening more than one instance
				if (dialog != null) {
					dialog.dismiss();
				}
				
				Activity activity = cordova.getActivity();

				// Let's create the main dialog
				dialog = new InAppBrowserDialog(activity, android.R.style.Theme_NoTitleBar);
				dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
				dialog.setCancelable(true);
				dialog.setInAppBroswer(getInAppBrowser());

				activity.getWindow().setFlags(
					WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
					WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

				// WebView
				inAppWebView = new WebView(activity);
				inAppWebView.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
				inAppWebView.setId(Integer.valueOf(6));

				// WebViewClient client = new InAppBrowserClient(thatWebView, edittext);
				WebViewClient client = new InAppBrowserClient(thatWebView);
				inAppWebView.setWebViewClient(client);

				WebSettings settings = inAppWebView.getSettings();
				if(Build.VERSION.SDK_INT >= 21){
					settings.setMixedContentMode(0);
				}

				if (Build.VERSION.SDK_INT >= 19) {
					inAppWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
				} else {
					inAppWebView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
				}

				inAppWebView.setVerticalScrollBarEnabled(false);
				inAppWebView.setBackgroundColor(android.graphics.Color.BLACK);

				boolean isDebuggable = (0 != (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE));

				if (isDebuggable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
					// development mode
					inAppWebView.setWebContentsDebuggingEnabled(true);
				} else if(!isDebuggable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
					// release mode
					inAppWebView.setWebContentsDebuggingEnabled(false);
				}

				/* if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
					inAppWebView.setWebContentsDebuggingEnabled(true);
				} */
				
				settings.setJavaScriptEnabled(true);
				settings.setJavaScriptCanOpenWindowsAutomatically(true);
				settings.setAllowFileAccess(true);
				settings.setLoadsImagesAutomatically(true);
				settings.setAllowFileAccessFromFileURLs(true);
				settings.setAllowUniversalAccessFromFileURLs(true);
				settings.setBuiltInZoomControls(showZoomControls);
				settings.setPluginState(android.webkit.WebSettings.PluginState.ON);
				settings.setAppCacheEnabled(true);
				settings.setCacheMode(WebSettings.LOAD_DEFAULT);

				if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
					settings.setMediaPlaybackRequiresUserGesture(mediaPlaybackRequiresUserGesture);
				}

				String overrideUserAgent = preferences.getString("OverrideUserAgent", null);
				String appendUserAgent = preferences.getString("AppendUserAgent", null);

				if (overrideUserAgent != null) {
					settings.setUserAgentString(overrideUserAgent);
				}
				if (appendUserAgent != null) {
					settings.setUserAgentString(settings.getUserAgentString() + appendUserAgent);
				}

				//Toggle whether this is enabled or not!
				Bundle appSettings = activity.getIntent().getExtras();
				boolean enableDatabase = appSettings == null ? true : appSettings.getBoolean("InAppBrowserStorageEnabled", true);
				if (enableDatabase) {
					String databasePath = activity.getApplicationContext().getDir("inAppBrowserDB", Context.MODE_PRIVATE).getPath();
					settings.setDatabasePath(databasePath);
					settings.setDatabaseEnabled(true);
				}
				settings.setDomStorageEnabled(true);
				
				if (clearAllCache) {
					CookieManager.getInstance().removeAllCookie();
				} else if (clearSessionCache) {
					CookieManager.getInstance().removeSessionCookie();
				}

				inAppWebView.loadUrl(url);
				inAppWebView.setId(Integer.valueOf(6));
				inAppWebView.setOverScrollMode(View.OVER_SCROLL_NEVER);
				inAppWebView.getSettings().setLoadWithOverviewMode(true);
				inAppWebView.getSettings().setUseWideViewPort(useWideViewPort);
				inAppWebView.requestFocus();
				inAppWebView.requestFocusFromTouch();

				// Main container layout
				LinearLayout main = new LinearLayout(activity);

				LayoutTransition lt = new LayoutTransition();
				lt.disableTransitionType(LayoutTransition.DISAPPEARING);
				lt.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
				lt.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
				lt.disableTransitionType(LayoutTransition.CHANGING);

				lt.enableTransitionType(LayoutTransition.APPEARING);

				lt.setStartDelay(LayoutTransition.APPEARING, 0);
				lt.setDuration(LayoutTransition.APPEARING, 1000);

				main.setLayoutTransition(lt);
				main.setOrientation(LinearLayout.VERTICAL);
				
				mFullscreenContainer = new FrameLayout(activity);
				FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
				mFullscreenContainer.setLayoutParams(params);

				//hide this view until it goes into fullscreen
				mFullscreenContainer.setVisibility(View.GONE);
				mFullscreenContainer.setId(Integer.valueOf(10));
				main.addView(mFullscreenContainer);
				
				mFullScreenWebView = new InAppChromeClient(thatWebView) {

					View fullScreenView = null;
					CustomViewCallback mCustomViewCallback = null;
					Window window = activity.getWindow();
					
					@Override
					public void onPermissionRequest(final PermissionRequest request) {
						
						String originHost = request.getOrigin().getHost();
						String originProtocol = request.getOrigin().getScheme();

						String localHost = Uri.parse(url).getHost();
						String localProtocol = Uri.parse(url).getScheme();

						if(localHost.equals(originHost) && localProtocol.equals(originProtocol)) {
							// Android 5, 5.1 no need to ask for permissions
							if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
								request.grant(request.getResources());
							} else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
								// Android 6+ ask for permissions dynamically
								activity.runOnUiThread(new Runnable() {
									@Override
									public void run() {
										requestAVPermissions(activity, activity.getApplicationContext(), request);
									}
								});
							}
						} else {
							request.deny();
						}
					}

					@Override
					public void onProgressChanged(WebView view, int newProgress) {
						super.onProgressChanged(view, newProgress);
					}

					@Override
					public void onShowCustomView(View view, final CustomViewCallback callback) {
						
						if (fullScreenView != null) {
							callback.onCustomViewHidden();
							return;
						}
						
						if (!(view instanceof FrameLayout)) {
							LOG.d(LOG_TAG, "custom view wasn't a FrameLayout");
							return;
						}

						fullScreenView = view;

						if (fullScreenView == null) {
							return;
						}
						
						if (Build.VERSION.SDK_INT >= 19) {
							fullScreenView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
						} else {
							fullScreenView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
						}

						fullScreenView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
						mFullscreenContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT);
						inAppWebView.setVisibility(View.GONE);

						mFullscreenContainer.setVisibility(View.VISIBLE);
						fullScreenView.setVisibility(View.VISIBLE);
						mFullscreenContainer.addView(fullScreenView);

						mCustomViewCallback = callback;

						super.onShowCustomView(view, callback);

						enterFullScreen();
					}

					private void enterFullScreen() {
						dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
					}

					private void exitFullScreen() {
						dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

					}

					@Override
					public void onHideCustomView() {

						if (mFullscreenContainer == null || fullScreenView == null|| mCustomViewCallback == null) {
							return;
						}

						// fixes keyboard not showing up after returning from fullscreen
						// without it the keyboard doesn't show until app is put on pause
						fullScreenView.clearFocus();
						mFullscreenContainer.clearFocus();
						inAppWebView.setFocusableInTouchMode(true);
						inAppWebView.requestFocus();
						inAppWebView.requestFocusFromTouch();

						mFullscreenContainer.setVisibility(View.GONE);
						fullScreenView.setVisibility(View.GONE);
						mFullscreenContainer.removeView(fullScreenView);

						inAppWebView.setVisibility(View.VISIBLE);

						
						if ((mCustomViewCallback != null) && (! mCustomViewCallback.getClass().getName().contains(".chromium."))) {
							mCustomViewCallback.onCustomViewHidden();
						}
						
						fullScreenView = null;

						super.onHideCustomView();

						exitFullScreen();
					}

					// File Chooser Implemented ChromeClient
					// For Android 5.0+
					public boolean onShowFileChooser (WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
						Context context = activity.getApplicationContext();
						if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M 
							&& (!cordova.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE))) {

								cordova.requestPermissions(InAppBrowser.this, 111,
								new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE});
						}
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
							final boolean allowMultiple = fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;
		
							openFileInput(null, filePathCallback, allowMultiple);
							super.onShowFileChooser(webView, filePathCallback, fileChooserParams);
							return true;
						} else {
							super.onShowFileChooser(webView, filePathCallback, fileChooserParams);
							return false;
						}
					}

					// For Android 4.1+
					public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture){
						openFileInput(uploadMsg, null, false);
					}
				};
				
				inAppWebView.setWebChromeClient(mFullScreenWebView);

				// Add our webview to our main view/layout
				main.addView(inAppWebView);

				WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
				lp.copyFrom(dialog.getWindow().getAttributes());
				lp.width = WindowManager.LayoutParams.MATCH_PARENT;
				lp.height = WindowManager.LayoutParams.MATCH_PARENT;

				dialog.setContentView(main);
				if(dialog != null && !dialog.isShowing()){
					LOG.d(LOG_TAG, "called show method");
					dialog.show();
				}
				dialog.getWindow().setAttributes(lp);
				// the goal of openhidden is to load the url and not display it
				// Show() needs to be called to cause the URL to be loaded
				if(openWindowHidden) {
					dialog.hide();
				}
			}
		};
		this.cordova.getActivity().runOnUiThread(runnable);
		return "";
	}

	protected void openFileInput(final ValueCallback<Uri> fileUploadCallbackFirst, final ValueCallback<Uri[]> fileUploadCallbackSecond, final boolean allowMultiple) {
		if (mFileUploadCallbackFirst != null) {
			mFileUploadCallbackFirst.onReceiveValue(null);
		}
		mFileUploadCallbackFirst = fileUploadCallbackFirst;

		if (mFileUploadCallbackSecond != null) {
			mFileUploadCallbackSecond.onReceiveValue(null);
		}

		mFileUploadCallbackSecond = fileUploadCallbackSecond;

		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);

		if (allowMultiple) {
			if (Build.VERSION.SDK_INT >= 18) {
				intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
			}
		}

		intent.setType("image/*");

		cordova.startActivityForResult(InAppBrowser.this, Intent.createChooser(intent, "Add Image"), FILECHOOSER_REQUEST_CODE);
	}
	
	/**
	* Create a new plugin success result and send it back to JavaScript
	*
	* @param obj a JSONObject contain event payload information
	*/
	private void sendUpdate(JSONObject obj, boolean keepCallback) {
		sendUpdate(obj, keepCallback, PluginResult.Status.OK);
	}

	/**
	* Create a new plugin result and send it back to JavaScript
	*
	* @param obj a JSONObject contain event payload information
	* @param status the status code to return to the JavaScript environment
	*/
	private void sendUpdate(JSONObject obj, boolean keepCallback, PluginResult.Status status) {
		if (callbackContext != null) {
			PluginResult result = new PluginResult(status, obj);
			result.setKeepCallback(keepCallback);
			callbackContext.sendPluginResult(result);
			if (!keepCallback) {
				callbackContext = null;
			}
		}
	}

	private void requestAVPermissions(Activity activity, Context context, final PermissionRequest request) {
		_permissionRequest =  request;

		List<String> permissionList = new ArrayList<String>();
		PackageManager pm = activity.getApplicationContext().getPackageManager();

		final String[] requestedPermissions = request.getResources();
		for (String req : requestedPermissions) {
			
			if (req.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {

				if(!cordova.hasPermission(Manifest.permission.CAMERA) && pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)){
					permissionList.add(Manifest.permission.CAMERA);
				}

			} else if (req.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)){
				boolean canRecordAudio = cordova.hasPermission(Manifest.permission.RECORD_AUDIO);
				boolean canModifyAudio = cordova.hasPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS);

				boolean hasBluetooth = cordova.hasPermission(Manifest.permission.BLUETOOTH);

				if (!canRecordAudio && pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
					permissionList.add(Manifest.permission.RECORD_AUDIO);
				}

				if (!canModifyAudio){
					permissionList.add(Manifest.permission.MODIFY_AUDIO_SETTINGS);
				}
				
				if (!hasBluetooth && pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)){
					permissionList.add(Manifest.permission.BLUETOOTH);
				}
			}
		}

		String[] permissions = new String[permissionList.size()];
		permissionList.toArray(permissions);

		if (hasAllPermissions(permissions)){
			request.grant(request.getResources());
		} else {
			cordova.requestPermissions(InAppBrowser.this, PERMISSIONS_CAMERA_AUDIO, permissions);
		}
	}

	//Handling callback
	@Override
	public void onRequestPermissionResult(int requestCode,
									   String permissions[], int[] grantResults) {
		if(permissions.length == 0){
			return;
		}
		if(!allPermissionsAreGranted(grantResults)){
			if(somePermissionsDeniedForever(permissions)) {
				Activity activity = cordova.getActivity();

				new AlertDialog.Builder(activity)
				.setTitle("Permissions Required")
				.setMessage("You have forcefully denied some of the required permissions " +
						"for this action. Please open settings, go to permissions and allow them.")
				.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface positiveDialog, int which) {
						Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
								Uri.fromParts("package", cordova.getActivity().getPackageName(), null));
						intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

						if(activity != null){
							activity.startActivity(intent);
							activity.finish();
						}
						if(positiveDialog != null){
							positiveDialog.dismiss();
							positiveDialog = null;
						}
					}
				})
				.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface negativeDialog, int which) {
						if(negativeDialog != null){
							negativeDialog.dismiss();
							negativeDialog = null;
						}
					}
				})
				.setCancelable(false)
				.create()
				.show();
			}
		} else {
			switch (requestCode) {
				case PERMISSIONS_CAMERA_AUDIO:
					if (allPermissionsAreGranted(grantResults)) {
						if (_permissionRequest != null){
							_permissionRequest.grant(_permissionRequest.getResources());
						}
	
					} else {
						if (_permissionRequest != null){
							_permissionRequest.deny();
						}
					}
					_permissionRequest = null;
					break;
			}
		}
	}
	
	private boolean hasAllPermissions(String[] permissions) {

        for (String permission : permissions) {
            if(!cordova.hasPermission(permission)) {
                return false;
            }
        }

        return true;
	}

	private boolean somePermissionsDeniedForever(String[] permissions){
		Activity activity = cordova.getActivity();
		boolean somePermissionsForeverDenied = false;

		for(String permission: permissions){
			if(!ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)){
				if(ActivityCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED){
					somePermissionsForeverDenied = true;
				}
			}
		}

		return somePermissionsForeverDenied;
	}

	private boolean allPermissionsAreGranted(int[] grantResults){
		if (grantResults.length > 0){
			for(int i : grantResults){
				if (i != PackageManager.PERMISSION_GRANTED){
					return false;
				}
			}
			return true;
		}
		return false;
	}

	/**
	* Receive File Data from File Chooser
	*
	* @param requestCode the requested code from chromeclient
	* @param resultCode the result code returned from android system
	* @param intent the data from android file chooser
	*/

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {

		if (requestCode == FILECHOOSER_REQUEST_CODE) {

			if (resultCode == Activity.RESULT_OK) {
				if (intent != null) {
					// < Android 5.0
					if (mFileUploadCallbackFirst != null) {
						mFileUploadCallbackFirst.onReceiveValue(intent.getData());
						mFileUploadCallbackFirst = null;
					}
					// > Android 5.0
					else if (mFileUploadCallbackSecond != null) {
						Uri[] dataUris = null;

						try {
							if (intent.getDataString() != null) {
								dataUris = new Uri[] { Uri.parse(intent.getDataString()) };
							}
							else {
								if (Build.VERSION.SDK_INT >= 16) {
									if (intent.getClipData() != null) {
										final int numSelectedFiles = intent.getClipData().getItemCount();

										dataUris = new Uri[numSelectedFiles];

										for (int i = 0; i < numSelectedFiles; i++) {
											dataUris[i] = intent.getClipData().getItemAt(i).getUri();
										}
									}
								}
							}
						}
						catch (Exception ignored) { }

						mFileUploadCallbackSecond.onReceiveValue(dataUris);
						mFileUploadCallbackSecond = null;
					}
				}
			}
			else {
				if (mFileUploadCallbackFirst != null) {
					mFileUploadCallbackFirst.onReceiveValue(null);
					mFileUploadCallbackFirst = null;
				}
				else if (mFileUploadCallbackSecond != null) {
					mFileUploadCallbackSecond.onReceiveValue(null);
					mFileUploadCallbackSecond = null;
				}
			}
		}
	}

	/**
	* The webview client receives notifications about appView
	*/
	public class InAppBrowserClient extends WebViewClient {
		// EditText edittext;
		CordovaWebView webView;

		public InAppBrowserClient(CordovaWebView webView) {
			this.webView = webView;
		}

		/**
		 * Constructor.
		 *
		 * @param webView
		 * @param mEditText
		 */
		/* public InAppBrowserClient(CordovaWebView webView, EditText mEditText) {
			this.webView = webView;
			this.edittext = mEditText;
		} */

		/**
		 * Override the URL that should be loaded
		 *
		 * This handles a small subset of all the URIs that would be encountered.
		 *
		 * @param webView
		 * @param url
		 */
		@Override
		public boolean shouldOverrideUrlLoading(WebView webView, String url) {
			if (url.startsWith(WebView.SCHEME_TEL)) {
				try {
					Intent intent = new Intent(Intent.ACTION_DIAL);
					intent.setData(Uri.parse(url));
					cordova.getActivity().startActivity(intent);
					return true;
				} catch (android.content.ActivityNotFoundException e) {
					LOG.e(LOG_TAG, "Error dialing " + url + ": " + e.toString());
				}
			} else if (url.startsWith("geo:") || url.startsWith(WebView.SCHEME_MAILTO) || url.startsWith("market:") || url.startsWith("intent:")) {
				try {
					Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setData(Uri.parse(url));
					cordova.getActivity().startActivity(intent);
					return true;
				} catch (android.content.ActivityNotFoundException e) {
					LOG.e(LOG_TAG, "Error with " + url + ": " + e.toString());
				}
			}
			// If sms:5551212?body=This is the message
			else if (url.startsWith("sms:")) {
				try {
					Intent intent = new Intent(Intent.ACTION_VIEW);

					// Get address
					String address = null;
					int parmIndex = url.indexOf('?');
					if (parmIndex == -1) {
						address = url.substring(4);
					} else {
						address = url.substring(4, parmIndex);

						// If body, then set sms body
						Uri uri = Uri.parse(url);
						String query = uri.getQuery();
						if (query != null) {
							if (query.startsWith("body=")) {
								intent.putExtra("sms_body", query.substring(5));
							}
						}
					}
					intent.setData(Uri.parse("sms:" + address));
					intent.putExtra("address", address);
					intent.setType("vnd.android-dir/mms-sms");
					cordova.getActivity().startActivity(intent);
					return true;
				} catch (android.content.ActivityNotFoundException e) {
					LOG.e(LOG_TAG, "Error sending sms " + url + ":" + e.toString());
				}
			}
			return false;
		}


		/*
		* onPageStarted fires the LOAD_START_EVENT
		*
		* @param view
		* @param url
		* @param favicon
		*/
		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			super.onPageStarted(view, url, favicon);
			String newloc = "";
			if (url.startsWith("http:") || url.startsWith("https:") || url.startsWith("file:")) {
				newloc = url;
			}
			else
			{
				// Assume that everything is HTTP at this point, because if we don't specify,
				// it really should be.  Complain loudly about this!!!
				LOG.e(LOG_TAG, "Possible Uncaught/Unknown URI");
				newloc = "http://" + url;
			}

			/* // Update the UI if we haven't already
			if (!newloc.equals(edittext.getText().toString())) {
				edittext.setText(newloc);
			} */

			try {
				JSONObject obj = new JSONObject();
				obj.put("type", LOAD_START_EVENT);
				obj.put("url", newloc);
				sendUpdate(obj, true);
			} catch (JSONException ex) {
				LOG.e(LOG_TAG, "URI passed in has caused a JSON error.");
			}
		}

		public void onPageFinished(WebView view, String url) {
			super.onPageFinished(view, url);

			// CB-10395 InAppBrowser's WebView not storing cookies reliable to local device storage
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
				CookieManager.getInstance().flush();
			} else {
				CookieSyncManager.getInstance().sync();
			}

			if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.KITKAT){
					view.getSettings().setJavaScriptEnabled(true);
			}

			// https://issues.apache.org/jira/browse/CB-11248
			view.clearFocus();
			view.requestFocus();

			try {
				JSONObject obj = new JSONObject();
				obj.put("type", LOAD_STOP_EVENT);
				obj.put("url", url);

				sendUpdate(obj, true);
			} catch (JSONException ex) {
				LOG.d(LOG_TAG, "Should never happen");
			}
		}

		public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
			super.onReceivedError(view, errorCode, description, failingUrl);

			try {
				JSONObject obj = new JSONObject();
				obj.put("type", LOAD_ERROR_EVENT);
				obj.put("url", failingUrl);
				obj.put("code", errorCode);
				obj.put("message", description);

				sendUpdate(obj, true, PluginResult.Status.ERROR);
			} catch (JSONException ex) {
				LOG.d(LOG_TAG, "Should never happen");
			}
		}

		/**
		 * On received http auth request.
		 */
		@Override
		public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host, String realm) {

			// Check if there is some plugin which can resolve this auth challenge
			PluginManager pluginManager = null;
			try {
				Method gpm = webView.getClass().getMethod("getPluginManager");
				pluginManager = (PluginManager)gpm.invoke(webView);
			} catch (NoSuchMethodException e) {
				LOG.d(LOG_TAG, e.getLocalizedMessage());
			} catch (IllegalAccessException e) {
				LOG.d(LOG_TAG, e.getLocalizedMessage());
			} catch (InvocationTargetException e) {
				LOG.d(LOG_TAG, e.getLocalizedMessage());
			}

			if (pluginManager == null) {
				try {
					Field pmf = webView.getClass().getField("pluginManager");
					pluginManager = (PluginManager)pmf.get(webView);
				} catch (NoSuchFieldException e) {
					LOG.d(LOG_TAG, e.getLocalizedMessage());
				} catch (IllegalAccessException e) {
					LOG.d(LOG_TAG, e.getLocalizedMessage());
				}
			}

			if (pluginManager != null && pluginManager.onReceivedHttpAuthRequest(webView, new CordovaHttpAuthHandler(handler), host, realm)) {
				return;
			}

			// By default handle 401 like we'd normally do!
			super.onReceivedHttpAuthRequest(view, handler, host, realm);
		}
	}
}
