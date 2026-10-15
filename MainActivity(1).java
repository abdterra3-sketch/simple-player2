package com.adjm.simpleplayer;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 1;
    private static final int REQ_PERM = 2;
    private static final int STEP_MS = 5000;
    private static final int MAX_SLOTS = 50;

    private static final int K_TEXT = 0;
    private static final int K_CROP = 1;
    private static final int K_PLAY = 2;
    private static final int K_PAUSE = 3;

    private FrameLayout stage, frame;
    private TextureView tv;
    private SeekBar seek;
    private TextView timeCur, timeTot;
    private IconButton btnPlay, btnRatio;

    private HorizontalScrollView hscroll;
    private LinearLayout strip;
    private final ArrayList<ImageView> slotImages = new ArrayList<ImageView>();
    private int slotW = 0;
    private int shotCount = 0;

    private MediaPlayer mp;
    private Surface surface;
    private Uri uri;
    private boolean prepared = false;
    private boolean completed = false;
    private boolean seeking = false;
    private boolean ratio916 = true;
    private int vw = 0, vh = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (mp != null && prepared && !seeking) {
                try {
                    if (mp.isPlaying()) {
                        int p = mp.getCurrentPosition();
                        seek.setProgress(p);
                        timeCur.setText(fmt(p));
                    }
                } catch (Exception ignored) { }
            }
            handler.postDelayed(this, 200);
        }
    };

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------- Tombol merah berbingkai biru muda (sesuai mockup) ----------

    private class IconButton extends View {
        private int kind;
        private String label;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        IconButton(Context c, int kind, String label) {
            super(c);
            this.kind = kind;
            this.label = label;
            setClickable(true);
        }

        void setKind(int kind, String label) {
            this.kind = kind;
            this.label = label;
            invalidate();
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            float b = Math.max(dp(3), w * 0.085f);
            boolean pr = isPressed();

            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF03A9F4);
            c.drawRect(0, 0, w, h, p);

            float l = b;
            float t = b;
            float r = w - b;
            float bt = h - b;
            float faceH = bt - t;
            float bevel = faceH * 0.10f;
            float hi = faceH * 0.07f;

            p.setColor(0xFF870000);
            c.drawRect(l, t, r, bt, p);
            p.setColor(pr ? 0xFFD40000 : 0xFFFF1212);
            c.drawRect(l, t, r, bt - bevel, p);
            if (!pr) {
                p.setColor(0xFFFF9A9A);
                c.drawRect(l, t, r, t + hi, p);
            }

            float fx0 = l;
            float fy0 = t + hi;
            float fx1 = r;
            float fy1 = bt - bevel;
            float fw = fx1 - fx0;
            float fh = fy1 - fy0;
            float cx = (fx0 + fx1) / 2f;
            float cy = (fy0 + fy1) / 2f;
            float off = pr ? dp(1) : 0f;

            p.setColor(Color.WHITE);

            if (kind == K_TEXT) {
                p.setStyle(Paint.Style.FILL);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextAlign(Paint.Align.CENTER);
                float size = fh * 0.50f;
                p.setTextSize(size);
                while (p.measureText(label) > fw * 0.84f && size > 8f) {
                    size -= 1f;
                    p.setTextSize(size);
                }
                float ty = cy + off - (p.ascent() + p.descent()) / 2f;
                c.drawText(label, cx, ty, p);
            } else if (kind == K_PLAY) {
                p.setStyle(Paint.Style.FILL);
                float s = Math.min(fw, fh) * 0.58f;
                path.reset();
                path.moveTo(cx - s * 0.38f, cy + off - s * 0.5f);
                path.lineTo(cx - s * 0.38f, cy + off + s * 0.5f);
                path.lineTo(cx + s * 0.55f, cy + off);
                path.close();
                c.drawPath(path, p);
            } else if (kind == K_PAUSE) {
                p.setStyle(Paint.Style.FILL);
                float s = Math.min(fw, fh) * 0.55f;
                float bw = s * 0.30f;
                float gap = s * 0.24f;
                c.drawRect(cx - gap / 2f - bw, cy + off - s / 2f,
                        cx - gap / 2f, cy + off + s / 2f, p);
                c.drawRect(cx + gap / 2f, cy + off - s / 2f,
                        cx + gap / 2f + bw, cy + off + s / 2f, p);
            } else if (kind == K_CROP) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(2f, fw * 0.075f));
                p.setStrokeCap(Paint.Cap.BUTT);
                p.setStrokeJoin(Paint.Join.MITER);
                path.reset();
                path.moveTo(fx0 + fw * 0.145f, fy0 + off + fh * 0.76f);
                path.lineTo(fx0 + fw * 0.145f, fy0 + off + fh * 0.25f);
                path.lineTo(fx0 + fw * 0.90f, fy0 + off + fh * 0.25f);
                c.drawPath(path, p);
                path.reset();
                path.moveTo(fx0 + fw * 0.79f, fy0 + off + fh * 0.16f);
                path.lineTo(fx0 + fw * 0.79f, fy0 + off + fh * 0.64f);
                path.lineTo(fx0 + fw * 0.05f, fy0 + off + fh * 0.64f);
                c.drawPath(path, p);
            }
        }
    }

    private IconButton mk(LinearLayout row, int kind, String label, View.OnClickListener l) {
        IconButton bt = new IconButton(this, kind, label);
        bt.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        row.addView(bt, lp);
        return bt;
    }

    // ---------- Layar ----------

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        final File errFile = new File(getFilesDir(), "crash.txt");
        if (errFile.exists()) {
            String txt = "";
            try {
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(errFile));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                br.close();
                txt = sb.toString();
            } catch (Exception ignored) { }
            errFile.delete();
            TextView tvErr = new TextView(this);
            tvErr.setText(txt);
            tvErr.setTextSize(11);
            tvErr.setPadding(20, 40, 20, 20);
            android.widget.ScrollView sv = new android.widget.ScrollView(this);
            sv.addView(tvErr);
            setContentView(sv);
            return;
        }
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    e.printStackTrace(new java.io.PrintWriter(sw));
                    java.io.FileWriter fw = new java.io.FileWriter(errFile);
                    fw.write(sw.toString());
                    fw.close();
                } catch (Exception ignored) { }
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        // 1. Judul ADJM MP4 Player
        int titleId = getResources().getIdentifier("judul", "drawable", getPackageName());
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                (int) (screenW * 0.66f), ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER_HORIZONTAL;
        tlp.setMargins(0, dp(12), 0, dp(8));
        if (titleId != 0) {
            ImageView title = new ImageView(this);
            title.setImageResource(titleId);
            title.setAdjustViewBounds(true);
            title.setScaleType(ImageView.ScaleType.FIT_CENTER);
            root.addView(title, tlp);
        } else {
            TextView title = new TextView(this);
            title.setText("ADJM MP4 Player");
            title.setTextColor(Color.WHITE);
            title.setTextSize(22);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setGravity(Gravity.CENTER);
            root.addView(title, tlp);
        }

        // 2. Layar penampil 9:16 / 16:9
        stage = new FrameLayout(this);
        root.addView(stage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.parseColor("#111111"));
        frame.setClipChildren(true);
        stage.addView(frame, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        tv = new TextureView(this);
        frame.addView(tv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        tv.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                surface = new Surface(st);
                if (mp != null) {
                    try { mp.setSurface(surface); } catch (Exception ignored) { }
                }
            }
            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { }
            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                if (mp != null) {
                    try { mp.setSurface(null); } catch (Exception ignored) { }
                }
                if (surface != null) { surface.release(); surface = null; }
                return true;
            }
            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture st) { }
        });

        stage.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int bt,
                                       int ol, int ot, int or, int ob) {
                if (r - l != or - ol || bt - t != ob - ot) updateLayout();
            }
        });

        // 3. Timeline
        LinearLayout seekRow = new LinearLayout(this);
        seekRow.setOrientation(LinearLayout.HORIZONTAL);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        seekRow.setPadding(dp(14), dp(8), dp(14), 0);
        timeCur = new TextView(this);
        timeCur.setTextColor(Color.WHITE);
        timeCur.setText("00:00");
        timeTot = new TextView(this);
        timeTot.setTextColor(Color.WHITE);
        timeTot.setText("00:00");
        seek = new SeekBar(this);
        seekRow.addView(timeCur);
        seekRow.addView(seek, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        seekRow.addView(timeTot);
        root.addView(seekRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser && mp != null && prepared) {
                    timeCur.setText(fmt(p));
                    mp.seekTo(p, MediaPlayer.SEEK_CLOSEST);
                    completed = false;
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar s) { seeking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar s) { seeking = false; }
        });

        // 4. Tombol: FILE, screenshot, -5s, play, +5s, rasio
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(11), dp(10), dp(11), dp(10));
        mk(row, K_TEXT, "FILE", new View.OnClickListener() {
            public void onClick(View v) { openPicker(); }
        });
        mk(row, K_CROP, "", new View.OnClickListener() {
            public void onClick(View v) { screenshot(); }
        });
        mk(row, K_TEXT, "-5s", new View.OnClickListener() {
            public void onClick(View v) { seekBy(-STEP_MS); }
        });
        btnPlay = mk(row, K_PLAY, "", new View.OnClickListener() {
            public void onClick(View v) { togglePlay(); }
        });
        mk(row, K_TEXT, "+5s", new View.OnClickListener() {
            public void onClick(View v) { seekBy(STEP_MS); }
        });
        btnRatio = mk(row, K_TEXT, "9:16", new View.OnClickListener() {
            public void onClick(View v) {
                ratio916 = !ratio916;
                btnRatio.setKind(K_TEXT, ratio916 ? "9:16" : "16:9");
                updateLayout();
            }
        });
        root.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 5. Layar penampil screenshot (berurutan 1, 2, 3, ...)
        slotW = (screenW - dp(40)) / 3 - dp(4);
        FrameLayout panel = new FrameLayout(this);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(0xFF0B0B0B);
        pbg.setStroke(dp(3), 0xFF03A9F4);
        panel.setBackground(pbg);
        panel.setPadding(dp(6), dp(6), dp(6), dp(6));

        hscroll = new HorizontalScrollView(this);
        hscroll.setHorizontalScrollBarEnabled(false);
        hscroll.setFillViewport(true);
        strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        hscroll.addView(strip, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panel.addView(hscroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        for (int i = 1; i <= 3; i++) addSlot(i);

        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (screenH * 0.19f));
        plp.setMargins(dp(14), 0, dp(14), dp(16));
        root.addView(panel, plp);

        setContentView(root);
        handler.post(ticker);
    }

    private void addSlot(int num) {
        FrameLayout slot = new FrameLayout(this);

        // Kotak gambar mengikuti ukuran gambar, nomor menempel di pojok kiri atas gambar
        FrameLayout box = new FrameLayout(this);

        ImageView iv = new ImageView(this);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        GradientDrawable ph = new GradientDrawable();
        ph.setColor(0xFF161616);
        ph.setStroke(dp(1), 0xFF2E2E2E);
        ph.setSize(dp(90), dp(160));
        iv.setImageDrawable(ph);
        box.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView badge = new TextView(this);
        badge.setText(String.valueOf(num));
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(14);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setBackgroundColor(0xCCFF1212);
        badge.setPadding(dp(7), dp(1), dp(7), dp(1));
        box.addView(badge, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START));

        slot.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                slotW, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(dp(2), 0, dp(2), 0);
        strip.addView(slot, lp);
        slotImages.add(iv);
    }

    // Menaruh hasil screenshot di slot 1, 2, 3, ... sesuai urutan pengambilan
    private int addShotThumb(Bitmap shot) {
        shotCount++;
        int mx = Math.max(shot.getWidth(), shot.getHeight());
        float sc = Math.min(1f, 480f / mx);
        Bitmap th = Bitmap.createScaledBitmap(shot,
                Math.max(1, Math.round(shot.getWidth() * sc)),
                Math.max(1, Math.round(shot.getHeight() * sc)), true);

        ImageView target;
        if (shotCount <= 3) {
            target = slotImages.get(shotCount - 1);
        } else {
            addSlot(shotCount);
            target = slotImages.get(slotImages.size() - 1);
            if (slotImages.size() > MAX_SLOTS) {
                ImageView old = slotImages.remove(0);
                old.setImageDrawable(null);
                strip.removeViewAt(0);
            }
            hscroll.post(new Runnable() {
                @Override
                public void run() { hscroll.fullScroll(View.FOCUS_RIGHT); }
            });
        }
        target.setImageBitmap(th);
        return shotCount;
    }

    // Kotak penampil dibuat tepat 9:16 atau 16:9; video mengisi penuh (center-crop)
    private void updateLayout() {
        int sw = stage.getWidth(), sh = stage.getHeight();
        if (sw == 0 || sh == 0) return;
        float r = ratio916 ? 9f / 16f : 16f / 9f;
        int fw, fh;
        if ((float) sw / sh > r) {
            fh = sh;
            fw = Math.round(sh * r);
        } else {
            fw = sw;
            fh = Math.round(sw / r);
        }
        frame.setLayoutParams(new FrameLayout.LayoutParams(fw, fh, Gravity.CENTER));
        if (vw > 0 && vh > 0) {
            float s = Math.max((float) fw / vw, (float) fh / vh);
            int tw = Math.round(vw * s), th = Math.round(vh * s);
            tv.setLayoutParams(new FrameLayout.LayoutParams(tw, th, Gravity.CENTER));
        }
    }

    // ---------- Pemutar ----------

    private void openPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
        startActivityForResult(i, REQ_FILE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE && res == RESULT_OK && data != null && data.getData() != null) {
            openVideo(data.getData());
        }
    }

    private void openVideo(Uri u) {
        releasePlayer();
        uri = u;
        vw = 0;
        vh = 0;
        seek.setProgress(0);
        timeCur.setText("00:00");
        try {
            mp = new MediaPlayer();
            mp.setDataSource(this, u);
            if (surface != null) mp.setSurface(surface);
            mp.setLooping(false);
            mp.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                @Override
                public void onVideoSizeChanged(MediaPlayer p, int w, int h) {
                    vw = w;
                    vh = h;
                    updateLayout();
                }
            });
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer p) {
                    prepared = true;
                    completed = false;
                    seek.setMax(p.getDuration());
                    timeTot.setText(fmt(p.getDuration()));
                    p.start();
                    btnPlay.setKind(K_PAUSE, "");
                }
            });
            // Selesai: berhenti di posisi akhir, TIDAK lanjut ke video lain
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer p) {
                    completed = true;
                    btnPlay.setKind(K_PLAY, "");
                    seek.setProgress(seek.getMax());
                    timeCur.setText(fmt(seek.getMax()));
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer p, int what, int extra) {
                    Toast.makeText(MainActivity.this, "Video tidak bisa diputar",
                            Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
            mp.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(this, "Gagal membuka video", Toast.LENGTH_SHORT).show();
            releasePlayer();
        }
    }

    private void releasePlayer() {
        if (mp != null) {
            try { mp.release(); } catch (Exception ignored) { }
            mp = null;
        }
        prepared = false;
        completed = false;
        btnPlay.setKind(K_PLAY, "");
    }

    private void togglePlay() {
        if (mp == null || !prepared) {
            openPicker();
            return;
        }
        if (mp.isPlaying()) {
            mp.pause();
            btnPlay.setKind(K_PLAY, "");
        } else {
            if (completed || mp.getCurrentPosition() >= mp.getDuration() - 100) {
                mp.seekTo(0);
                completed = false;
            }
            mp.start();
            btnPlay.setKind(K_PAUSE, "");
        }
    }

    private void seekBy(int delta) {
        if (mp == null || !prepared) return;
        int dur = mp.getDuration();
        int pos = Math.max(0, Math.min(dur, mp.getCurrentPosition() + delta));
        mp.seekTo(pos, MediaPlayer.SEEK_CLOSEST);
        if (pos < dur) completed = false;
        seek.setProgress(pos);
        timeCur.setText(fmt(pos));
    }

    // ---------- Screenshot ----------

    private void screenshot() {
        if (mp == null || !prepared || uri == null) {
            Toast.makeText(this, "Buka video dulu", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM);
            return;
        }
        final int pos = mp.getCurrentPosition();
        final int dur = mp.getDuration();
        final boolean r916 = ratio916;
        final Bitmap fallback = tv.getBitmap();
        final Uri src = uri;

        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap full = null;
                MediaMetadataRetriever mr = new MediaMetadataRetriever();
                try {
                    mr.setDataSource(MainActivity.this, src);
                    long t = (long) Math.max(0, Math.min(pos, dur - 100)) * 1000L;
                    full = mr.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST);
                } catch (Exception ignored) {
                } finally {
                    try { mr.release(); } catch (Exception ignored) { }
                }
                if (full == null) full = fallback;
                if (full == null) {
                    toast("Gagal mengambil gambar");
                    return;
                }
                final Bitmap shot = cropExact(full, r916);
                final boolean okf = saveToGallery(shot, r916);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (okf) {
                            int n = addShotThumb(shot);
                            Toast.makeText(MainActivity.this,
                                    "Screenshot " + n + " tersimpan di Galeri ("
                                            + shot.getWidth() + "x" + shot.getHeight() + ")",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Gagal menyimpan",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    // Potong tengah dengan rasio tepat 9:16 atau 16:9 (kelipatan 9 dan 16 piksel)
    private Bitmap cropExact(Bitmap b, boolean r916) {
        int w = b.getWidth(), h = b.getHeight();
        int cw, ch;
        if (r916) {
            int u = Math.min(w / 9, h / 16);
            cw = u * 9;
            ch = u * 16;
        } else {
            int u = Math.min(w / 16, h / 9);
            cw = u * 16;
            ch = u * 9;
        }
        if (cw <= 0 || ch <= 0) return b;
        return Bitmap.createBitmap(b, (w - cw) / 2, (h - ch) / 2, cw, ch);
    }

    private boolean saveToGallery(Bitmap bmp, boolean r916) {
        String name = "VID_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date()) + (r916 ? "_9x16" : "_16x9") + ".jpg";
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                cv.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Screenshot Video");
                cv.put(MediaStore.Images.Media.IS_PENDING, 1);
                Uri u = getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (u == null) return false;
                OutputStream os = getContentResolver().openOutputStream(u);
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, os);
                os.close();
                ContentValues done = new ContentValues();
                done.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(u, done, null, null);
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "Screenshot Video");
                if (!dir.exists() && !dir.mkdirs()) return false;
                File f = new File(dir, name);
                FileOutputStream os = new FileOutputStream(f);
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, os);
                os.close();
                MediaScannerConnection.scanFile(this, new String[]{f.getAbsolutePath()},
                        new String[]{"image/jpeg"}, null);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_PERM) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Izin diberikan, tekan tombol screenshot lagi",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Izin penyimpanan diperlukan", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void toast(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() { Toast.makeText(MainActivity.this, s, Toast.LENGTH_SHORT).show(); }
        });
    }

    private String fmt(int ms) {
        int s = ms / 1000;
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mp != null && prepared) {
            try {
                if (mp.isPlaying()) {
                    mp.pause();
                    btnPlay.setKind(K_PLAY, "");
                }
            } catch (Exception ignored) { }
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        super.onDestroy();
    }
}
