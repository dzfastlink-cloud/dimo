package com.receiver.iptv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

public class MainActivity extends Activity {
    private WebView w;
    private PlayerView playerView;
    private ExoPlayer player;
    private ValueCallback<Uri[]> fcb;
    private volatile boolean nativeActive;
    private boolean zoomed;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        playerView = (PlayerView) LayoutInflater.from(this).inflate(
            R.layout.native_player_view, root, false);
        playerView.setVisibility(View.GONE);
        root.addView(playerView);

        w = new WebView(this);
        w.setBackgroundColor(Color.TRANSPARENT);
        root.addView(w, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    if (w != null) w.evaluateJavascript(
                        "window.onNativePlaybackStarted&&window.onNativePlaybackStarted()", null);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                fallbackFromNative();
            }
        });

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
        public boolean playNative(String u) {
            if (u == null || !(u.startsWith("http://") || u.startsWith("https://"))) return false;
            nativeActive = true;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        zoomed = false;
                        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                        playerView.setVisibility(View.VISIBLE);
                        player.setMediaItem(MediaItem.fromUri(u));
                        player.prepare();
                        player.play();
                    } catch (RuntimeException e) {
                        fallbackFromNative();
                    }
                }
            });
            return true;
        }

        @android.webkit.JavascriptInterface
        public void stopNative() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    nativeActive = false;
                    if (player != null) player.stop();
                    if (playerView != null) playerView.setVisibility(View.GONE);
                }
            });
        }

        @android.webkit.JavascriptInterface
        public boolean isNativePlayback() {
            return nativeActive;
        }

        @android.webkit.JavascriptInterface
        public void toggleNativePlayback() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (player == null || !nativeActive) return;
                    if (player.isPlaying()) player.pause();
                    else player.play();
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void seekNative(long offsetMs) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (player != null && nativeActive) player.seekTo(
                        Math.max(0, player.getCurrentPosition() + offsetMs));
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void toggleNativeZoom() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    toggleZoom();
                }
            });
        }

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

    private void toggleZoom() {
        if (!nativeActive || playerView == null) return;
        zoomed = !zoomed;
        playerView.setResizeMode(zoomed
            ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            : AspectRatioFrameLayout.RESIZE_MODE_FIT);
        if (w != null) w.evaluateJavascript(
            "window.wToast&&wToast('" + (zoomed ? "تكبير الصورة" : "ملاءمة الشاشة") + "')", null);
    }

    private void fallbackFromNative() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                nativeActive = false;
                if (player != null) player.stop();
                if (playerView != null) playerView.setVisibility(View.GONE);
                if (w != null) w.evaluateJavascript(
                    "window.onNativePlaybackError&&window.onNativePlaybackError()", null);
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (nativeActive && event.getRepeatCount() == 0) {
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (player.isPlaying()) player.pause();
                else player.play();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                if (player != null) player.play();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                if (player != null) player.pause();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                if (player != null) player.seekTo(player.getCurrentPosition() + 15000);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
                if (player != null) player.seekTo(
                    Math.max(0, player.getCurrentPosition() - 15000));
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
                toggleZoom();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
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
    protected void onDestroy() {
        nativeActive = false;
        if (playerView != null) playerView.setPlayer(null);
        if (player != null) player.release();
        if (w != null) w.destroy();
        super.onDestroy();
    }
}
