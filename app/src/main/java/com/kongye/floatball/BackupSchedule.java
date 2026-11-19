package com.kongye.floatball;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

final class BackupSchedule {
    static final int LOCAL_JOB = 34601, WEBDAV_JOB = 34602;
    static final String[] LABELS = {"关闭", "每天", "每周", "每 30 天"};
    private static final int[] DAYS = {0, 1, 7, 30};

    private static SharedPreferences data(Context context) {
        return context.getSharedPreferences("backup_schedule", Context.MODE_PRIVATE);
    }

    private static String key(boolean remote, String name) { return (remote ? "remote_" : "local_") + name; }

    static int frequency(Context context, boolean remote) {
        int index = data(context).getInt(key(remote, "frequency"), 0);
        return index >= 0 && index < DAYS.length ? index : 0;
    }

    static boolean notes(Context context, boolean remote) { return data(context).getBoolean(key(remote, "notes"), true); }

    static void saveNotes(Context context, boolean remote, boolean includesNotes) {
        data(context).edit().putBoolean(key(remote, "notes"), includesNotes).apply();
    }

    static Uri folder(Context context) {
        String value = data(context).getString("local_folder", "");
        return value.isEmpty() ? null : Uri.parse(value);
    }

    static void saveFolder(Context context, Uri folder) { data(context).edit().putString("local_folder", folder.toString()).apply(); }

    static void configure(Context context, boolean remote, int index) throws IOException {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        int id = remote ? WEBDAV_JOB : LOCAL_JOB;
        if (DAYS[index] == 0) scheduler.cancel(id);
        else {
            JobInfo.Builder job = new JobInfo.Builder(id, new ComponentName(context, BackupJobService.class))
                    .setPeriodic(TimeUnit.DAYS.toMillis(DAYS[index])).setPersisted(true).setRequiresStorageNotLow(true);
            if (remote) job.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
            if (scheduler.schedule(job.build()) != JobScheduler.RESULT_SUCCESS) throw new IOException("无法设置自动备份，请稍后重试");
        }
        data(context).edit().putInt(key(remote, "frequency"), index).apply();
    }

    static String result(Context context, boolean remote) { return data(context).getString(key(remote, "result"), ""); }

    static void result(Context context, boolean remote, boolean success) {
        String time = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(new java.util.Date());
        data(context).edit().putString(key(remote, "result"), (success ? "最近自动备份成功 · " : "最近自动备份失败 · ") + time).apply();
    }

    static void upload(Context context, WebDavBackup client, boolean notes) throws IOException {
        File temporary = File.createTempFile("KongyeFloat-", ".zip", context.getCacheDir());
        try {
            BackupStore.export(context, Uri.fromFile(temporary), notes);
            if (Thread.currentThread().isInterrupted()) throw new IOException("备份已取消");
            String name = BackupStore.filename().replace(".zip", "_" + UUID.randomUUID().toString().substring(0, 8) + ".zip");
            client.upload(temporary, name);
        } finally { temporary.delete(); }
    }
}
