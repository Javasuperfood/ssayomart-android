package com.javasuperfood.ssayomart

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.webkit.*
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.onesignal.OneSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Bitmap


const val ONESIGNAL_APP_ID = "1191e933-e52c-466f-946f-e8cab83009d7" //Penting

class MainActivity : ComponentActivity() {
    lateinit var loading: ProgressBar
    lateinit var progressBar: ProgressBar
    lateinit var icon_init: ImageView
    lateinit var bg_splash: TextView
    lateinit var text_noinet: TextView
    private lateinit var url: String
    private lateinit var webView: WebView
    private var savedCookies: String? = null

    //Web error
    lateinit var icon_404: ImageView
    lateinit var text_404: TextView
    lateinit var button_refresh: Button
    lateinit var bg_noint: TextView

    //for attach files
    private var mCameraPhotoPath: String? = null
    private var mFilePathCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var okayText: TextView
    private lateinit var cancelText: TextView
    val INPUT_FILE_REQUEST_CODE = 1
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    var mGeoLocationRequestOrigin: String? = null
    var mGeoLocationCallback: GeolocationPermissions.Callback? = null

    private lateinit var appUpdateManager: AppUpdateManager
    private val MY_REQUEST_CODE = 42

    //Inisiasi DEV Or Prod
    private var env: String? = "production"
    private var home: String? = null

    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Get the URL from the intent
        val uri: Uri? = intent.data
        url = uri.toString()
        init()
        // setupUI() //go to animatedZoomOut()
        onSignalInit()
        appUpdateManager = AppUpdateManagerFactory.create(this)
        // Periksa ketersediaan update dan tampilkan notifikasi jika diperlukan
        checkForUpdates()


        button_refresh.setOnClickListener {
            Toast.makeText(this, "Refreshing", Toast.LENGTH_SHORT).show()
            if (isOnline(this)) {
                webView.reload()
            } else {
                Toast.makeText(this, "Internet Error", Toast.LENGTH_SHORT).show()
            }

        }
    }

    private fun checkForUpdates() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                //(tampilkan notifikasi, dll.)
                startUpdate(appUpdateInfo)
            }
        }
    }

    private fun startUpdate(appUpdateInfo: AppUpdateInfo) {
        appUpdateManager.startUpdateFlowForResult(
            appUpdateInfo,
            AppUpdateType.IMMEDIATE,
            this,
            MY_REQUEST_CODE
        )
    }

    private fun  onSignalInit() {
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE)
        OneSignal.initWithContext(this)
        OneSignal.setAppId(ONESIGNAL_APP_ID)
        OneSignal.promptForPushNotifications();
    }

    private fun init() {
        if (url == "null") {
            url = "https://apps.ssayomart.com/"
        }
        home = "https://apps.ssayomart.com"
        if (env == "dev") {
            url = "http://192.168.15.181:8080/"
            home = "http://192.168.15.181:8080"
        }
        if (env == "devOnline") {
            url = "https://public-dev.ssayomart.com/"
            home = "https://public-dev.ssayomart.com"
        }
        loading = findViewById<ProgressBar>(R.id.pb_loading)
        progressBar  = findViewById<ProgressBar>(R.id.progressBarHorizontal)
        loading.visibility = View.GONE
        icon_init = findViewById<ImageView>(R.id.icon_init)
        text_noinet = findViewById<TextView>(R.id.text_noinet)
        bg_splash = findViewById<TextView>(R.id.bg_splash)
        icon_404 = findViewById<ImageView>(R.id.icon_404)
        text_404 = findViewById<TextView>(R.id.text_404)
        button_refresh = findViewById<Button>(R.id.button_refresh)
        bg_noint = findViewById<TextView>(R.id.bg_noint)


        swipeRefresh()
        animatedInit()

    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            //
        }
    }

    private fun swipeRefresh() {
        val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.refreshLayout)
        swipeRefreshLayout.setOnRefreshListener {
            sendUuid()
            if (webView.url == "$home" || webView.url == "$home/" || webView.url == "$home/index.php/" || webView.url == "$home/index.php") {
                webView.clearCache(true)
            }
            webView.reload()

            swipeRefreshLayout.isRefreshing = false
            text_noinet.visibility = View.GONE
        }
    }

    fun sendUuid() {
        Log.d("CookieA", "Cookie: $savedCookies")
        // Mengambil subscriberId dari OneSignal
        val subscriberId = OneSignal.getDeviceState()?.userId
        Log.d("subscriberId", "Subscriber ID: $subscriberId")

        // URL tempat Anda ingin mengirim permintaan POST
        val url = "$url/api/set-uuid"

        // Membuat body request
        val requestBody =
            "uuid=$subscriberId".toRequestBody("application/x-www-form-urlencoded".toMediaType())

        // Membuat request POST
        val request = Request.Builder()
            .url(url)
            .addHeader("Cookie", "$savedCookies")
            .post(requestBody)
            .build()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                // Melakukan permintaan HTTP POST di dalam coroutine IO
                val response: Response = OkHttpClient().newCall(request).execute()

                // Mengecek apakah permintaan sukses atau tidak
                if (response.isSuccessful) {
                    // Tindakan yang akan diambil jika permintaan sukses
                    Log.d("ResponseA", "Success: " + response.body?.string())
                } else {
                    // Tindakan yang akan diambil jika permintaan gagal
                    Log.e("ResponseA", "Error: " + response.body?.string())
                    Log.e("ResponseA", "Error: " + response.code)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("ResponseA", "Exception: " + e.message)
            }
        }
    }


    private fun animatedInit() {
        val timer = Timer()
        val delay = 1300 // Delay in milliseconds
        val period = 2000 // Interval in milliseconds
        val scaleAnimation = ScaleAnimation(
            0.0f, // Start scale factor (X-axis)
            2.1f, // End scale factor (X-axis) - You can adjust this value as needed
            0.0f, // Start scale factor (Y-axis)
            2.1f, // End scale factor (Y-axis) - You can adjust this value as needed
            Animation.RELATIVE_TO_SELF,
            0.5f, // X-axis pivot point (center)
            Animation.RELATIVE_TO_SELF,
            0.5f  // Y-axis pivot point (center)
        )
        scaleAnimation.duration = 400 // Set the duration of the animation in milliseconds
        scaleAnimation.fillAfter = true // Keep the scaling after the animation finishes

        // Set an animation listener to handle any necessary logic after the animation
        scaleAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
                // Animation started
            }

            override fun onAnimationEnd(animation: Animation?) {
                timer.cancel()
                animatedZoomOut()
            }

            override fun onAnimationRepeat(animation: Animation?) {
                // Animation repeated
            }
        })
        val task = object : TimerTask() {
            override fun run() {
                icon_init.startAnimation(scaleAnimation)
            }
        }
        timer.scheduleAtFixedRate(task, delay.toLong(), period.toLong())
    }

    private fun animatedZoomOut() {
        val scaleAnimation = ScaleAnimation(
            2.1f, // Start scale factor (X-axis)
            2.0f, // End scale factor (X-axis) - You can adjust this value as needed
            2.1f, // Start scale factor (Y-axis)
            2.0f, // End scale factor (Y-axis) - You can adjust this value as needed
            Animation.RELATIVE_TO_SELF,
            0.5f, // X-axis pivot point (center)
            Animation.RELATIVE_TO_SELF,
            0.5f  // Y-axis pivot point (center)
        )
        scaleAnimation.duration = 160 // Set the duration of the animation in milliseconds
        scaleAnimation.fillAfter = true // Keep the scaling after the animation finishes
        scaleAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
                // Animation started
            }

            override fun onAnimationEnd(animation: Animation?) {
                //end adnimation
//                spalsh_gone()
                setupUI()
            }

            override fun onAnimationRepeat(animation: Animation?) {
                // Animation repeated
            }
        })
        icon_init.startAnimation(scaleAnimation)
    }

    private fun setupUI() {
        Log.d("SetUIA", "this: $this")
        if (isOnline(this)) {
            loadWebview()
        } else {
            loadWebview()
            Toast.makeText(this, "No Internet Access", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("ServiceCast")
    fun isOnline(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnectedOrConnecting
    }

    private fun loadWebview() {
        webView = findViewById<WebView>(R.id.wv_page)
        val AndroidVersion = Build.VERSION.SDK_INT
        val BuildTagetc = Build.MODEL
        val WebKitRev = Build.VERSION.RELEASE
        val appName = "Ssayomart"
        val appVersion = "1.0.3"

        val customUserAgent =
            "Mozilla/5.0 (Linux; Android $AndroidVersion; $BuildTagetc) AppleWebKit/$WebKitRev (KHTML, like Gecko) Chrome/88.0.4324.181 Mobile Safari/535.19 $appName/$appVersion"
        progressBar.max = 100
        webView.webViewClient = myWebclient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                super.onProgressChanged(view, newProgress)
            }
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
                super.onGeolocationPermissionsShowPrompt(origin, callback)
            }

            override fun onShowFileChooser(
                webView: WebView, filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: WebChromeClient.FileChooserParams
            ): Boolean {
                if (mFilePathCallback != null) {
                    mFilePathCallback!!.onReceiveValue(null)
                }
                mFilePathCallback = filePathCallback

                var takePictureIntent: Intent? = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (takePictureIntent!!.resolveActivity(this@MainActivity.packageManager) != null) {
                    // Create the File where the photo should go
                    var photoFile: File? = null
                    try {
                        photoFile = createImageFile()
                        takePictureIntent.putExtra("PhotoPath", mCameraPhotoPath)
                    } catch (ex: IOException) {
                        // Error occurred while creating the File
                        Log.e(TAG, "Unable to create Image File", ex)
                    }

                    // Continue only if the File was successfully created
                    if (photoFile != null) {
                        mCameraPhotoPath = "file:" + photoFile.absolutePath
                        takePictureIntent.putExtra(
                            MediaStore.EXTRA_OUTPUT,
                            Uri.fromFile(photoFile)
                        )
                    } else {
                        takePictureIntent = null
                    }
                }

                val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
                contentSelectionIntent.type = "image/*"

                val intentArray: Array<Intent?>
                if (takePictureIntent != null) {
                    intentArray = arrayOf(takePictureIntent)
                } else {
                    intentArray = arrayOfNulls(0)
                }

                val chooserIntent = Intent(Intent.ACTION_CHOOSER)
                chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser")
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)

                startActivityForResult(chooserIntent, INPUT_FILE_REQUEST_CODE)

                return true
            }

        }

        webView.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setGeolocationEnabled(true)
            domStorageEnabled = true
            loadWithOverviewMode = true
            defaultTextEncodingName = "utf-8"
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            domStorageEnabled = true
            databaseEnabled = true
            setSupportMultipleWindows(false)
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = customUserAgent
        }
        webView.loadUrl(url)
    }


    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        )
        return File.createTempFile(
            imageFileName, /* prefix */
            ".jpg", /* suffix */
            storageDir      /* directory */
        )
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MY_REQUEST_CODE) {
            if (resultCode != RESULT_OK) {
                // Handle update failure
                return finish()
            } else {
                appUpdateManager.completeUpdate()
            }
        }
        if (requestCode != INPUT_FILE_REQUEST_CODE || mFilePathCallback == null) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }

        var results: Array<Uri>? = null

        // Check that the response is a good one
        if (resultCode == Activity.RESULT_OK) {
            if (data == null) {
                // If there is not data, then we may have taken a photo
                if (mCameraPhotoPath != null) {
                    results = arrayOf(Uri.parse(mCameraPhotoPath))
                }
            } else {
                val dataString = data.dataString
                if (dataString != null) {
                    results = arrayOf(Uri.parse(dataString))
                }
            }
        }

        mFilePathCallback!!.onReceiveValue(results)
        mFilePathCallback = null
        return
    }

    inner class myWebclient : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            progressBar.visibility = View.VISIBLE
        }
        override fun onPageFinished(view: WebView, url: String) {
            spalsh_gone()
            getCookie(url)
            if (url == "$home" || url == "$home/" || url == "$home/index.php/" || url == "$home/index.php") {
                Log.d("WebViewApp", "InHome: $url")
                sendUuid()
            }
            if (url == "$home/setting/alamat-list" || url == "$home/setting/create-alamat") {
                requestLocationPermission()
            }
            if (isOnline(this@MainActivity)) {
                bg_noint.visibility = View.INVISIBLE
                text_404.visibility = View.INVISIBLE
                icon_404.visibility = View.INVISIBLE
                button_refresh.visibility = View.INVISIBLE
                webView.visibility = View.VISIBLE
            } else {
                visible_view_web_error()
            }
            super.onPageFinished(view, url)
            progressBar.visibility = View.GONE
        }

        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            if (url.startsWith("market://") || url.startsWith("vnd:youtube") || url.startsWith("tel:") || url.startsWith(
                    "mailto:"
                )
            ) {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(url)
                startActivity(intent)
                return true
            }
            view.loadUrl(url)
            return true
        }
    }

    fun visible_view_web_error() {
        webView.visibility = View.GONE
        text_404.visibility = View.VISIBLE
        icon_404.visibility = View.VISIBLE
        button_refresh.visibility = View.VISIBLE
        bg_noint.visibility = View.VISIBLE
        Toast.makeText(this, "Internet Error", Toast.LENGTH_SHORT).show()
    }

    fun spalsh_gone() {
        icon_init.clearAnimation()
        icon_init.visibility = View.GONE
        bg_splash.visibility = View.GONE
        loading.visibility = View.GONE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
            webView.goBack()
            return true
        } else {
            // Jika pengguna berada di halaman utama, tampilkan dialog keluar
            val dialog = Dialog(this)
            dialog.setContentView(R.layout.dialog)
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            dialog.setCancelable(false)
            dialog.window?.attributes?.windowAnimations = R.style.animation

            okayText = dialog.findViewById(R.id.okay_text)
            cancelText = dialog.findViewById(R.id.cancel_text)

            okayText.setOnClickListener {
                finish()
            }

            cancelText.setOnClickListener {
                dialog.dismiss()
            }
            dialog.show()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    fun getCookie(url: String) {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(url)
        if (cookies != null) {
            savedCookies = cookies
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            1001 -> {
                //if permission is cancel result array would be empty
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //permission was granted
                    if (mGeoLocationCallback != null) {
                        mGeoLocationCallback!!.invoke(mGeoLocationRequestOrigin, true, true)
                    }
                } else {
                    //permission denied
                    if (mGeoLocationCallback != null) {
                        mGeoLocationCallback!!.invoke(mGeoLocationRequestOrigin, false, false)
                    }
                }
            }
        }
    }
}