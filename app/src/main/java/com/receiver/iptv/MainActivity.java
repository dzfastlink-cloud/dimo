package com.receiver.iptv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView w;
    private ValueCallback<Uri[]> fcb;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        w = new WebView(this);
        w.setBackgroundColor(Color.BLACK);
        setContentView(w);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        w.setWebViewClient(new WebViewClient());
        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fcb != null) fcb.onReceiveValue(null);
                fcb = cb;
                try {
                    startActivityForResult(p.createIntent(), 1);
                } catch (Exception e) {
                    fcb = null;
                    cb.onReceiveValue(null);
                    w.evaluateJavascript("window.wToast&&wToast('لا يوجد مدير ملفات على الجهاز')", null);
                }
                return true;
            }
        });
        w.addJavascriptInterface(new Bridge(), "Android");
        w.loadUrl("file:///android_asset/index.html");
        w.requestFocus();
    }

    private class Bridge {
        @android.webkit.JavascriptInterface
        public void openUrl(String u) {
            if (u == null || !(u.startsWith("http://") || u.startsWith("https://"))) return;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
            } catch (Exception e) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        w.evaluateJavascript("window.wToast&&wToast('لا يوجد متصفح على الجهاز')", null);
                    }
                });
            }
        }
    }

    @Override
    protected void onActivityResult(int rq, int rs, Intent d) {
        if (rq == 1 && fcb != null) {
            fcb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(rs, d));
            fcb = null;
        } else super.onActivityResult(rq, rs, d);
    }

    @Override
    public void onBackPressed() {
        w.evaluateJavascript("window.onBack?window.onBack():false", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String r) { if (!"true".equals(r)) finish(); }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected void onDestroy() { if (w != null) w.destroy(); super.onDestroy(); }
}
