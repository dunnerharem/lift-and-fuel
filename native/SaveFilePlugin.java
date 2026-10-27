package com.liftfuel.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Opens Android's own "Save as" screen so the user picks the folder and file name,
 * then writes text to the chosen file in pieces (so large backups do not choke the bridge).
 */
@CapacitorPlugin(name = "SaveFile")
public class SaveFilePlugin extends Plugin {

    private OutputStream out;

    @PluginMethod
    public void begin(PluginCall call) {
        closeQuietly();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(call.getString("mime", "application/json"));
        intent.putExtra(Intent.EXTRA_TITLE, call.getString("name", "backup.json"));
        startActivityForResult(call, intent, "beginResult");
    }

    @ActivityCallback
    private void beginResult(PluginCall call, ActivityResult result) {
        if (call == null) {
            return;
        }
        if (result.getResultCode() != Activity.RESULT_OK
                || result.getData() == null
                || result.getData().getData() == null) {
            call.reject("cancelled");
            return;
        }
        try {
            Uri uri = result.getData().getData();
            out = getContext().getContentResolver().openOutputStream(uri, "wt");
            if (out == null) {
                call.reject("Could not open the file.");
                return;
            }
            JSObject ret = new JSObject();
            ret.put("uri", uri.toString());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Could not open the file: " + e.getMessage());
        }
    }

    @PluginMethod
    public void write(PluginCall call) {
        if (out == null) {
            call.reject("No file is open.");
            return;
        }
        try {
            String chunk = call.getString("chunk", "");
            out.write(chunk.getBytes(StandardCharsets.UTF_8));
            call.resolve();
        } catch (Exception e) {
            call.reject("Write failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void finish(PluginCall call) {
        try {
            if (out != null) {
                out.flush();
            }
        } catch (Exception e) {
            closeQuietly();
            call.reject("Could not finish the file: " + e.getMessage());
            return;
        }
        closeQuietly();
        call.resolve();
    }

    private void closeQuietly() {
        try {
            if (out != null) {
                out.close();
            }
        } catch (Exception ignored) {
        }
        out = null;
    }
}
