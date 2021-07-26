package com.tamilandroo.ytsaver;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.bumptech.glide.Glide;

import at.huber.youtubeExtractor.VideoMeta;
import at.huber.youtubeExtractor.YouTubeExtractor;
import at.huber.youtubeExtractor.YtFile;

public class MainActivity extends AppCompatActivity {
    EditText link;
    Button download;
    TextView vidName;
    ImageView ytImg;

    LinearLayout mainLayout, downloadPage;
    ProgressBar mainProgressBar;

    ProgressDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getSupportActionBar().setHomeAsUpIndicator(R.mipmap.ic_launcher_foreground);// set drawable icon
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        link = findViewById(R.id.link);
        download = findViewById(R.id.download);
        vidName = findViewById(R.id.vidName);
        ytImg = findViewById(R.id.ytImg);
        downloadPage = findViewById(R.id.downloadPage);
        mainLayout = (LinearLayout) findViewById(R.id.main_layout);
        mainProgressBar = (ProgressBar) findViewById(R.id.prgrBar);

        dialog = new ProgressDialog(this);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setCancelable(false);

        if (savedInstanceState == null && Intent.ACTION_SEND.equals(getIntent().getAction())
                && getIntent().getType() != null && "text/plain".equals(getIntent().getType())) {

            String ytLink = getIntent().getStringExtra(Intent.EXTRA_TEXT);
            if (isStoragePermissionGranted()) {
                if (!isInternetAvailable()) {
                    Toast.makeText(this, "No internet!", Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    if (ytLink != null
                            && (ytLink.contains("://youtu.be/") || ytLink.contains("youtube.com/watch?v="))) {
                        // We have a valid link
                        downloadPage.setVisibility(View.INVISIBLE);
                        getYoutubeDownloadUrl(ytLink);
                    } else {
                        Toast.makeText(this, "This is not a yt link!", Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
            }
        }

        download.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isStoragePermissionGranted()) {
                    if (!isInternetAvailable()) {
                        showInternetDialog();
                    } else {
                        String ytLink = link.getText().toString();
                        if (ytLink != null
                                && (ytLink.contains("://youtu.be/") || ytLink.contains("youtube.com/watch?v="))) {
                            mainProgressBar.setVisibility(View.VISIBLE);
                            getYoutubeDownloadUrl(ytLink);
                        } else {
                            Toast.makeText(MainActivity.this, "This is not a yt link!", Toast.LENGTH_LONG).show();
                        }
                    }
                }
            }
        });
    }

    @SuppressLint("StaticFieldLeak")
    private void getYoutubeDownloadUrl(final String youtubeLink) {
        new YouTubeExtractor(this) {
            @Override
            public void onExtractionComplete(SparseArray<YtFile> ytFiles, VideoMeta vMeta) {
                mainProgressBar.setVisibility(View.GONE);

                if (youtubeLink.contains("watch?v")) {
                    String videoId = Uri.parse(youtubeLink).getQueryParameter("v");
                    Uri thumbnailUri = Uri.parse("https://img.youtube.com/vi/" + videoId + "/0.jpg");
                    Glide.with(MainActivity.this).load(thumbnailUri).into(ytImg);
                } else {
                    String videoId = youtubeLink.substring(youtubeLink.lastIndexOf("/") + 1);
                    Uri thumbnailUri = Uri.parse("https://img.youtube.com/vi/" + videoId + "/0.jpg");
                    Glide.with(MainActivity.this).load(thumbnailUri).into(ytImg);
                }

                if (ytFiles == null) {
                    // Something went wrong we got no urls. Always check this.
                    return;
                }
                // Iterate over itags
                for (int i = 0, itag; i < ytFiles.size(); i++) {
                    itag = ytFiles.keyAt(i);
                    // ytFile represents one file with its url and meta data
                    YtFile ytFile = ytFiles.get(itag);

                    // Just add videos in a decent format => height -1 = audio
                    if (ytFile.getFormat().getHeight() == -1 || ytFile.getFormat().getHeight() >= 360) {
                        vidName.setText(vMeta.getTitle());
                        addButtonToMainLayout(vMeta.getTitle(), ytFile);
                    }
                }
            }
        }.extract(youtubeLink, true, false);
    }

    private void addButtonToMainLayout(final String videoTitle, final YtFile ytfile) {
        // Display some buttons and let the user choose the format

        String btnText = (ytfile.getFormat().getHeight() == -1) ? "Audio " +
                ytfile.getFormat().getAudioBitrate() + " kbit/s" :
                ytfile.getFormat().getHeight() + "p";
        btnText += (ytfile.getFormat().isDashContainer()) ? " dash" : "";
        Button btn = new Button(this);
        btn.setTextSize(18);
        btn.setTextColor(getResources().getColor(R.color.white));
        btn.setBackgroundResource(R.drawable.video_formats_style);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 15, 0, 0);
        btn.setLayoutParams(params);

        btn.setText(btnText);

        btn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                String filename;
                if (videoTitle.length() > 55) {
                    filename = videoTitle.substring(0, 55) + "." + ytfile.getFormat().getExt();
                } else {
                    filename = videoTitle + "." + ytfile.getFormat().getExt();
                }
                filename = filename.replaceAll("[\\\\><\"|*?%:#/]", "");
                downloadFromUrl(ytfile.getUrl(), videoTitle, filename);
            }
        });
        mainLayout.addView(btn);
    }

    private void downloadFromUrl(String youtubeDlUrl, String downloadTitle, String fileName) {
        Uri uri = Uri.parse(youtubeDlUrl);
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setTitle(downloadTitle);

        request.allowScanningByMediaScanner();
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS + "/Yt Saver", fileName);

        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        manager.enqueue(request);

        dialog.setTitle(fileName);
        dialog.setMessage("Downloading..");
        dialog.show();

        registerReceiver(onComplete,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    public boolean isInternetAvailable() {
        ConnectivityManager connectivityManager = ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE));
        return connectivityManager.getActiveNetworkInfo() != null && connectivityManager.getActiveNetworkInfo().isConnected();
    }

    private void showInternetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("No internet !")
                .setMessage("this app need internet for downloading.")
                .setPositiveButton("Try again", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        builder.create().show();
    }

    BroadcastReceiver onComplete = new BroadcastReceiver() {
        public void onReceive(Context ctxt, Intent intent) {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Download Complete");
            builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogg, int which) {
                    dialogg.dismiss();
                }
            });
            builder.show();
        }
    };

    public boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                return true;
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getApplicationContext(), "Click download again!", Toast.LENGTH_LONG).show();
            //resume tasks needing this permission
        } else {
            Toast.makeText(getApplicationContext(), "Storage need!", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.option, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.yt:
                Intent intent1 = new Intent(Intent.ACTION_VIEW);
                intent1.setData(Uri.parse("https://www.youtube.com/channel/UCTg0lctdkU0kyCulZrMxO1Q"));
                startActivity(intent1);
                break;
            case R.id.insta:
                Intent intent2 = new Intent(Intent.ACTION_VIEW);
                intent2.setData(Uri.parse("https://www.instagram.com/tamil_androo/"));
                startActivity(intent2);
                break;
            case R.id.share:
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, "Yt Saver");
                i.putExtra(Intent.EXTRA_TEXT, "https://tamilandroo.web.app/yt-saver");
                startActivity(Intent.createChooser(i, "Share YtSaver"));
                break;
            case R.id.moreapp:
                Intent intent3 = new Intent(Intent.ACTION_VIEW);
                intent3.setData(Uri.parse("https://tamilandroo.web.app"));
                startActivity(intent3);
                break;
            case R.id.exit:
                finish();
                break;
        }

        return true;
    }
}