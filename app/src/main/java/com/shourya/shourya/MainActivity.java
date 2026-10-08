package com.shourya.shourya;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.net.Uri;
import android.content.Intent;
import android.webkit.GeolocationPermissions;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import android.webkit.WebChromeClient.FileChooserParams;
import android.widget.Toast;
import android.webkit.PermissionRequest;
import android.app.DownloadManager;
import android.os.Environment;
import android.webkit.DownloadListener;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.util.Base64;
import java.io.File;
import java.io.FileOutputStream;
import android.webkit.JavascriptInterface;
import android.content.ContentValues;
import android.os.Build;
import android.provider.MediaStore;
import java.io.OutputStream;
import android.media.MediaScannerConnection;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST_CODE = 1;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private long backPressedTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);

        // Add JavascriptInterface for Blob downloads
        webView.addJavascriptInterface(new JavaScriptInterface(), "AndroidDownloader");

        // Enable JavaScript
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        // Disable Zoom
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);

        // Enable Geolocation (Location)
        webSettings.setGeolocationEnabled(true);

        // Enable File Access for Camera
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);

        // WebViewClient - ONLY for URL loading
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        // WebChromeClient - for Geolocation, File Chooser, etc.
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                MainActivity.this.filePathCallback = filePathCallback;

                // Check permissions before opening chooser
                if (checkAndRequestPermissions()) {
                    openFileChooser(fileChooserParams);
                }
                return true;
            }
        });

        // Download Listener to handle file downloads from WebView
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {
                try {
                    // DownloadManager cannot handle blob or data URLs directly, use JavascriptInterface
                    if (url.startsWith("blob:")) {
                        String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
                        String js = "javascript: " +
                            "var xhr = new XMLHttpRequest();" +
                            "xhr.open('GET', '" + url + "', true);" +
                            "xhr.responseType = 'blob';" +
                            "xhr.onload = function(e) {" +
                            "    if (this.status == 200) {" +
                            "        var blob = this.response;" +
                            "        var reader = new FileReader();" +
                            "        reader.readAsDataURL(blob);" +
                            "        reader.onloadend = function() {" +
                            "            var base64data = reader.result;" +
                            "            AndroidDownloader.getBase64FromBlobData(base64data, '" + fileName + "');" +
                            "        }" +
                            "    }" +
                            "};" +
                            "xhr.send();";
                        webView.evaluateJavascript(js, null);
                        Toast.makeText(MainActivity.this, "Downloading File...", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    if (url.startsWith("data:")) {
                        String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
                        new JavaScriptInterface().getBase64FromBlobData(url, fileName);
                        Toast.makeText(MainActivity.this, "Downloading File...", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

                    request.setMimeType(mimetype);
                    String cookies = CookieManager.getInstance().getCookie(url);
                    request.addRequestHeader("cookie", cookies);
                    request.addRequestHeader("User-Agent", userAgent);
                    request.setDescription("Downloading file...");
                    request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype));
                    request.allowScanningByMediaScanner();
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype));

                    DownloadManager dm = (DownloadManager) getSystemService(android.content.Context.DOWNLOAD_SERVICE);
                    if (dm != null) {
                        dm.enqueue(request);
                        Toast.makeText(getApplicationContext(), "Downloading File...", Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            }
        });

        // IMPROVED BACK BUTTON HANDLER
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Check if WebView can go back
                if (webView.canGoBack()) {
                    // Go to previous page in WebView
                    webView.goBack();
                } else {
                    // No more pages in WebView history
                    // Double tap to exit app
                    if (backPressedTime + 2000 > System.currentTimeMillis()) {
                        finish();
                    } else {
                        Toast.makeText(MainActivity.this, "Press back again to exit", Toast.LENGTH_SHORT).show();
                        backPressedTime = System.currentTimeMillis();
                    }
                }
            }
        });

        // Check and request permissions on start
        checkAndRequestPermissions();

        // ✅ CHANGE THIS URL - New Website Link
        webView.loadUrl("https://e-gatepass-eight.vercel.app/");
    }

    // ===== PERMISSION CHECKING METHOD =====
    private boolean checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        // Camera Permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }

        // Location Permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        // Storage Permissions (for profile photo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            // Android 12 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
            return false;
        }

        return true;
    }

    // ===== PERMISSION RESULT HANDLER =====
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                webView.reload();
            }
        }
    }

    // ===== FILE CHOOSER METHOD =====
    private void openFileChooser(WebChromeClient.FileChooserParams params) {
        Intent intent = params.createIntent();
        try {
            startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
        } catch (Exception e) {
            filePathCallback = null;
        }
    }

    // ===== ACTIVITY RESULT HANDLER =====
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback == null) return;

            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                String dataString = data.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }

            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    // ===== JAVASCRIPT INTERFACE FOR BLOB DOWNLOADS =====
    public class JavaScriptInterface {
        @JavascriptInterface
        public void getBase64FromBlobData(String base64Data, String fileName) {
            try {
                String base64 = base64Data;
                String mimeType = "application/octet-stream";
                String extension = "";

                // Extract mime type from base64Data (format: "data:text/csv;base64,....")
                if (base64.startsWith("data:")) {
                    int commaIndex = base64.indexOf(",");
                    int semicolonIndex = base64.indexOf(";");
                    if (semicolonIndex > 5 && semicolonIndex < commaIndex) {
                        mimeType = base64.substring(5, semicolonIndex);
                        extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                    }
                    if (commaIndex > 0) {
                        base64 = base64.substring(commaIndex + 1);
                    }
                }
                
                // Fallback extensions if MimeTypeMap fails
                if (extension == null || extension.isEmpty()) {
                    if (mimeType.contains("csv")) extension = "csv";
                    else if (mimeType.contains("pdf")) extension = "pdf";
                    else if (mimeType.contains("png")) extension = "png";
                    else if (mimeType.contains("jpeg") || mimeType.contains("jpg")) extension = "jpg";
                    else if (mimeType.contains("excel") || mimeType.contains("spreadsheet")) extension = "xlsx";
                    else extension = "bin"; // default generic extension
                }
                
                // Fix the file name if it has a wrong extension like .bin or is a Blob UUID
                if (fileName == null || fileName.isEmpty() || fileName.equals("null")) {
                    fileName = "download_" + System.currentTimeMillis() + "." + extension;
                } else {
                    // Remove generic .bin extension added by Android's URLUtil
                    if (fileName.toLowerCase().endsWith(".bin")) {
                        fileName = fileName.substring(0, fileName.length() - 4);
                    }
                    
                    // If no extension exists now, add the correct one
                    if (!fileName.contains(".")) {
                        fileName = fileName + "." + extension;
                    } else if (!fileName.toLowerCase().endsWith("." + extension.toLowerCase())) {
                        // If it has a different wrong extension, replace it
                        fileName = fileName.substring(0, fileName.lastIndexOf(".")) + "." + extension;
                    }
                    
                    // If the filename is still a long ugly UUID, simplify it
                    if (fileName.length() > 30 && fileName.matches(".*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-.*")) {
                        fileName = "file_" + System.currentTimeMillis() + "." + extension;
                    }
                }

                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                final String finalFileName = fileName;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10+ (API 29+) - Use MediaStore
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                    android.net.Uri uri = MainActivity.this.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        OutputStream os = MainActivity.this.getContentResolver().openOutputStream(uri);
                        if (os != null) {
                            os.write(bytes);
                            os.close();
                        }
                    }
                } else {
                    // Below Android 10
                    File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File file = new File(path, fileName);
                    
                    // Ensure unique name
                    int count = 1;
                    while (file.exists()) {
                        String nameWithoutExtension = fileName;
                        String ext = "";
                        int dotIndex = fileName.lastIndexOf(".");
                        if(dotIndex > 0) {
                            nameWithoutExtension = fileName.substring(0, dotIndex);
                            ext = fileName.substring(dotIndex);
                        }
                        file = new File(path, nameWithoutExtension + "_" + count + ext);
                        count++;
                    }

                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(bytes);
                    fos.close();
                    
                    // Scan file so it appears in file manager immediately
                    MediaScannerConnection.scanFile(MainActivity.this,
                            new String[]{file.getAbsolutePath()}, null, null);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "File Saved to Downloads: " + finalFileName, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                final String errorMsg = e.getMessage();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Error saving: " + errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    }
}