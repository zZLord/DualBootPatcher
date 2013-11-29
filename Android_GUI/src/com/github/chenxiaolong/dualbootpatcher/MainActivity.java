package com.github.chenxiaolong.dualbootpatcher;

import java.lang.ref.WeakReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedState.mActivity = new WeakReference<MainActivity>(this);

        SharedState.mPartitionConfigSpinner = new WeakReference<Spinner>(
                (Spinner) findViewById(R.id.choose_partition_config));

        Button chooseFileButton = (Button) findViewById(R.id.choose_file);
        chooseFileButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
                if (Build.VERSION.SDK_INT < 19) {
                    // Intent fileIntent = new
                    // Intent(Intent.ACTION_OPEN_DOCUMENT);
                    fileIntent.addCategory(Intent.CATEGORY_OPENABLE);
                }
                fileIntent.setType("application/zip");
                startActivityForResult(fileIntent, SharedState.REQUEST_FILE);
            }
        });

        if (SharedState.mPatcherFileVer.equals("")) {
            try {
                SharedState.mPatcherFileVer = getPackageManager()
                        .getPackageInfo(getPackageName(), 0).versionName;
                SharedState.mPatcherFileName = SharedState.mPatcherFileBase
                        + SharedState.mPatcherFileVer.replace("-DEBUG", "")
                        + SharedState.mPatcherFileExt;
            } catch (NameNotFoundException e) {
                e.printStackTrace();
            }
        }

        /* Include version in action bar */
        setTitle(getTitle() + " (v" + SharedState.mPatcherFileVer + ")");

        SharedState.mProgressDialog = new ProgressDialog(this);
        SharedState.mProgressDialog.setCancelable(false);
        SharedState.mProgressDialog
                .setProgressStyle(ProgressDialog.STYLE_SPINNER);
        SharedState.mProgressDialog.setIcon(0);

        SharedState.mConfirmDialogBuilder = new AlertDialog.Builder(this);
        SharedState.mConfirmDialogBuilder.setNegativeButton(
                SharedState.mConfirmDialogNegativeText,
                SharedState.mConfirmDialogNegative);
        SharedState.mConfirmDialogBuilder.setPositiveButton(
                SharedState.mConfirmDialogPositiveText,
                SharedState.mConfirmDialogPositive);

        updatePatcher();
        tryShowDialogs();

        TextView partitionConfigDesc =
                (TextView) findViewById(R.id.partition_config_desc);
        partitionConfigDesc.setText(SharedState.mPartitionConfigText);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (SharedState.mProgressDialog != null) {
            SharedState.mProgressDialog.dismiss();
            SharedState.mProgressDialog = null;
        }
        if (SharedState.mConfirmDialog != null) {
            SharedState.mConfirmDialog.dismiss();
            SharedState.mConfirmDialog = null;
        }
        if (SharedState.mConfirmDialogBuilder != null) {
            SharedState.mConfirmDialogBuilder = null;
        }
        SharedState.mPartitionConfigSpinner = null;
        SharedState.mActivity = null;
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        switch (request) {
        case SharedState.REQUEST_FILE:
            if (data != null && result == RESULT_OK) {
                SharedState.zipFile = getPathFromUri(data.getData());

                SharedState.mConfirmDialogNegative = new SharedState.ConfirmDialogNegative();
                SharedState.mConfirmDialogPositive = new SharedState.ConfirmDialogPositive();
                SharedState.mConfirmDialogNegativeText = getString(R.string.dialog_cancel);
                SharedState.mConfirmDialogPositiveText = getString(R.string.dialog_continue);
                SharedState.mConfirmDialogBuilder.setNegativeButton(
                        SharedState.mConfirmDialogNegativeText,
                        SharedState.mConfirmDialogNegative);
                SharedState.mConfirmDialogBuilder.setPositiveButton(
                        SharedState.mConfirmDialogPositiveText,
                        SharedState.mConfirmDialogPositive);

                SharedState.mConfirmDialogTitle = getString(R.string.dialog_patch_zip_title);
                SharedState.mConfirmDialogText = getString(R.string.dialog_patch_zip_msg)
                        + "\n\n" + SharedState.zipFile;
                SharedState.mConfirmDialogVisible = true;
                tryShowDialogs();
            }
            break;
        }

        super.onActivityResult(request, result, data);
    }

    private String getPathFromUri(Uri uri) {
        // Incredibly ugly hack to get full path
        Pattern p = Pattern.compile("^(.*):(.*)$");
        Matcher m = p.matcher(uri.getPath());
        if (m.find()) {
            String dir = m.group(1);
            String file = m.group(2);
            if (dir.equals("/document/primary")) {
                dir = Environment.getExternalStorageDirectory().getPath();
            }
            return dir + "/" + file;
        } else {
            return uri.getPath();
        }
    }

    private synchronized void updatePatcher() {
        // Thread needs this
        final boolean updatedPatcher = SharedState.updatedPatcher;

        if (!updatedPatcher) {
            findViewById(R.id.choose_file).setEnabled(false);
            SharedState.mPartitionConfigSpinner.get().setEnabled(false);

            /* Show progress dialog */
            SharedState.mProgressDialogTitle =
                    getString(R.string.progress_title_patching_files);
            SharedState.mProgressDialogText =
                    getString(R.string.progress_text);
            SharedState.mProgressDialog
                    .setTitle(SharedState.mProgressDialogTitle);
            SharedState.mProgressDialog
                    .setMessage(SharedState.mProgressDialogText);
            SharedState.mProgressDialog.show();
            SharedState.mProgressDialogVisible = true;
        }

        new Thread() {
            @Override
            public void run() {
                if (!updatedPatcher) {
                    SharedState.extract_patcher();
                }

                SharedState.get_partition_configs();

                SharedState.mHandler.obtainMessage(
                        SharedState.EVENT_POPULATE_PARTITION_CONFIG_SPINNER)
                        .sendToTarget();

                if (!updatedPatcher) {
                    SharedState.mHandler.obtainMessage(
                            SharedState.EVENT_CLOSE_PROGRESS_DIALOG)
                            .sendToTarget();

                    SharedState.mHandler.obtainMessage(
                            SharedState.EVENT_ENABLE_GUI_WIDGETS)
                            .sendToTarget();
                }
            }
        }.start();

        SharedState.updatedPatcher = true;
    }

    private void tryShowDialogs() {
        if (SharedState.mProgressDialogVisible) {
            SharedState.mProgressDialog
                    .setMessage(SharedState.mProgressDialogText);
            SharedState.mProgressDialog
                    .setTitle(SharedState.mProgressDialogTitle);
            SharedState.mProgressDialog.show();
        }
        if (SharedState.mConfirmDialogVisible) {
            SharedState.mConfirmDialogBuilder
                    .setTitle(SharedState.mConfirmDialogTitle);
            SharedState.mConfirmDialogBuilder
                    .setMessage(SharedState.mConfirmDialogText);
            SharedState.mConfirmDialog = SharedState.mConfirmDialogBuilder
                    .create();
            SharedState.mConfirmDialog.show();
        }
    }
}
