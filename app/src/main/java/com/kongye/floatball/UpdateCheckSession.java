package com.kongye.floatball;

import android.os.*;
import java.util.concurrent.*;

/** Survives rotation without retaining an Activity or displaying an intermediate dialog. */
final class UpdateCheckSession {
    interface Listener {void complete(GitHubUpdate.Result result,String error);}
    interface Source {GitHubUpdate.Result check(String version)throws Exception;}
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Future<?> task;private Listener listener;private GitHubUpdate.Result result;private String error;private boolean ready,closed;boolean checking;
    Source source=GitHubUpdate::check;
    void attach(Listener listener){this.listener=listener;deliver();}
    void detach(){listener=null;}
    void start(String version){if(checking||closed)return;checking=true;task=worker.submit(()->{
        GitHubUpdate.Result answer=null;String failure=null;try{answer=source.check(version);}catch(Exception e){failure=e.getMessage();}
        GitHubUpdate.Result value=answer;String message=failure;main.post(()->{if(closed)return;checking=false;result=value;error=message;ready=true;deliver();});
    });}
    private void deliver(){if(!ready||listener==null)return;ready=false;listener.complete(result,error);result=null;error=null;}
    void close(){closed=true;listener=null;if(task!=null)task.cancel(true);worker.shutdownNow();main.removeCallbacksAndMessages(null);}
}
