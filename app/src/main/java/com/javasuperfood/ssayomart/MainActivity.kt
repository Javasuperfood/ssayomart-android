package com.javasuperfood.ssayomart

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.webkit.*
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.onesignal.OneSignal
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

const val ONESIGNAL_APP_ID = "1191e933-e52c-466f-946f-e8cab83009d7" //Penting

class MainActivity : ComponentActivity() {
    lateinit var loading: ProgressBar
    lateinit var icon_init: ImageView
    lateinit var bg_splash: TextView
    lateinit var text_noinet: TextView
    private lateinit var url: String
    private lateinit var webView: WebView

    //for attach files
    private var mCameraPhotoPath: String? = null
    private var mFilePathCallback: ValueCallback<Array<Uri>>? = null

    val INPUT_FILE_REQUEST_CODE = 1

    @Override
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        init()
        setupUI()
        onSignalInit()
    }

    private fun onSignalInit() {
        OneSignal.setLogLevel(OneSignal.LOG_LEVEL.VERBOSE, OneSignal.LOG_LEVEL.NONE)
        OneSignal.initWithContext(this)
        OneSignal.setAppId(ONESIGNAL_APP_ID)
        OneSignal.promptForPushNotifications();
    }

    private fun init() {
        url = "https://apps.ssayomart.com/"
        loading = findViewById<ProgressBar>(R.id.pb_loading)
        icon_init = findViewById<ImageView>(R.id.icon_init)
        text_noinet = findViewById<TextView>(R.id.text_noinet)
        bg_splash = findViewById<TextView>(R.id.bg_splash)
        webView = findViewById<WebView>(R.id.wv_page)
        val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.refreshLayout)
        swipeRefreshLayout.setOnRefreshListener {
            swipeRefreshLayout.isRefreshing = false
            text_noinet.visibility = View.GONE
        }

        val scaleAnimation = ScaleAnimation(
            1.0f, // Start scale factor (X-axis)
            2.0f, // End scale factor (X-axis) - You can adjust this value as needed
            1.0f, // Start scale factor (Y-axis)
            2.0f, // End scale factor (Y-axis) - You can adjust this value as needed
            Animation.RELATIVE_TO_SELF,
            0.5f, // X-axis pivot point (center)
            Animation.RELATIVE_TO_SELF,
            0.5f  // Y-axis pivot point (center)
        )
        scaleAnimation.duration = 1000 // Set the duration of the animation in milliseconds
        scaleAnimation.fillAfter = true // Keep the scaling after the animation finishes

        // Set an animation listener to handle any necessary logic after the animation
        scaleAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
                // Animation started
            }

            override fun onAnimationEnd(animation: Animation?) {
                // Animation ended, you can perform additional actions here

            }

            override fun onAnimationRepeat(animation: Animation?) {
                // Animation repeated
            }
        })

        // Start the animation on the icon_init ImageView
        icon_init.startAnimation(scaleAnimation)
    }

    private fun setupUI() {
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
        val AndroidVersion = Build.VERSION.SDK_INT
        val BuildTagetc = Build.MODEL
        val WebKitRev = Build.VERSION.RELEASE
        val appName = "Ssayomart"
        val appVersion = "1.0.3"
        val customUserAgent =
            "Mozilla/5.0 (Linux; Android $AndroidVersion; $BuildTagetc) AppleWebKit/$WebKitRev (KHTML, like Gecko) Chrome/88.0.4324.181 Mobile Safari/535.19 $appName/$appVersion"
        webView.webViewClient = myWebclient()
        webView.settings.userAgentString = customUserAgent
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.defaultTextEncodingName = "utf-8"
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.setSupportMultipleWindows(false)
        webView.loadUrl(url)
        webView.webChromeClient = object : WebChromeClient() {
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
        if (!isOnline(this)) {
            webView.loadUrl("file:///android_asset/lost.html")
        }
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
        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            icon_init.clearAnimation()
            icon_init.visibility = View.GONE
            loading.visibility = View.GONE
            bg_splash.visibility = View.GONE
        }

        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            view.loadUrl(url)
            return true
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}