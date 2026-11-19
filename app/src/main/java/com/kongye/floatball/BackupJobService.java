package com.kongye.floatball;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class BackupJobService extends JobService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Integer, Thread> running = new HashMap<>();

    @Override public boolean onStartJob(JobParameters params) {
        int id = params.getJobId();
        if (id != BackupSchedule.LOCAL_JOB && id != BackupSchedule.WEBDAV_JOB) return false;
        boolean remote = id == BackupSchedule.WEBDAV_JOB;
        if (BackupSchedule.frequency(this, remote) == 0) return false;
        var context = getApplicationContext();
        Thread worker = new Thread(() -> {
            boolean success = false;
            try {
                boolean notes = BackupSchedule.notes(context, remote);
                if (remote) {
                    WebDavBackup client = new WebDavBackup(WebDavSettings.load(context),
                            android.os.Build.VERSION.SDK_INT >= 28 ? params.getNetwork() : null);
                    client.prepareDirectory();
                    BackupSchedule.upload(context, client, notes);
                } else {
                    new LocalBackupFolder(context, BackupSchedule.folder(context)).save(context, notes);
                }
                success = true;
            } catch (IOException | RuntimeException ignored) { }
            boolean done = success;
            Thread current = Thread.currentThread();
            main.post(() -> {
                if (running.get(id) != current) return;
                running.remove(id);
                BackupSchedule.result(context, remote, done);
                jobFinished(params, false);
            });
        }, "Kongye-Automatic-Backup");
        Thread previous = running.put(id, worker);
        if (previous != null) previous.interrupt();
        worker.start(); return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        Thread worker = running.remove(params.getJobId());
        if (worker != null) worker.interrupt();
        return BackupSchedule.frequency(this, params.getJobId() == BackupSchedule.WEBDAV_JOB) != 0;
    }

    @Override public void onDestroy() {
        running.values().forEach(Thread::interrupt);
        running.clear();
        super.onDestroy();
    }
}
