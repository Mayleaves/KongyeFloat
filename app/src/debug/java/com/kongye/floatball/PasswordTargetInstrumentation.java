package com.kongye.floatball;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;

/** Reads only generated fixture pages, never the user's vault or login pages. */
public final class PasswordTargetInstrumentation extends Instrumentation {
    private static final String FIXTURE="com.kongye.floatball.fillfixture";
    private int passed;
    private UiAutomation automation;
    private boolean notesOnly;
    @Override public void onCreate(Bundle args){super.onCreate(args);notesOnly=args!=null&&Boolean.parseBoolean(args.getString("release_notes_only","false"));start();}
    private void check(boolean value,String message){if(!value)throw new IllegalStateException(message);passed++;}
    private AccessibilityNodeInfo root(String pkg)throws Exception{
        long until=SystemClock.uptimeMillis()+6000;
        while(SystemClock.uptimeMillis()<until){AccessibilityNodeInfo node=automation.getRootInActiveWindow();if(node!=null){if(pkg.contentEquals(node.getPackageName()))return node;node.recycle();}Thread.sleep(100);}
        throw new IllegalStateException("Generated page did not become active");
    }
    private PasswordTarget.Inspection page(String key)throws Exception{
        Intent intent=new Intent().setClassName(FIXTURE,"com.kongye.fixture.FixtureLogin").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if(!key.isEmpty())intent.putExtra(key,true);if(key.equals("address"))intent.putExtra("url","");
        getTargetContext().startActivity(intent);AccessibilityNodeInfo node=root(FIXTURE);node.recycle();Thread.sleep(400);
        return PasswordTarget.inspectRoot(getTargetContext(),root(FIXTURE));
    }
    @Override public void onStart(){
        Bundle report=new Bundle();
        try{
            if(notesOnly){int count=ReleaseNotesChecks.run(getTargetContext());report.putString("result","passed");report.putInt("release_notes_checks",count);finish(Activity.RESULT_OK,report);return;}
            automation=getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            check(PasswordTarget.inspectResult(null).problem()==PasswordTarget.Problem.ACCESSIBILITY,"missing accessibility reason");
            check(PasswordTarget.inspectRoot(getTargetContext(),null).problem()==PasswordTarget.Problem.WINDOW,"unavailable page reason");
            PasswordTarget.Inspection found=page("");check(found.problem()==PasswordTarget.Problem.NONE&&found.target()!=null,"native login detection");
            try(PasswordTarget target=found.target()){
                check(target.snapshot.password()!=null,"native password field");check("login.example.com".equals(target.snapshot.domain()),"known address control");
                check(target.fill("fixture-user","Fixture!2053"),"generated credentials fill");
            }
            Bundle state=getTargetContext().getContentResolver().call(android.net.Uri.parse("content://"+FIXTURE+".report"),"state",null,null);
            check(state!=null&&state.getBoolean("account")&&state.getBoolean("password"),"both generated fields filled");if(state!=null)state.clear();
            found=page("web_password");check(found.target()!=null,"password inputType without password flag");if(found.target()!=null)found.target().close();
            found=page("unsupported");check(found.target()==null&&found.problem()==PasswordTarget.Problem.UNSUPPORTED,"unsupported field rejected before revealing");
            found=page("address");check(found.target()==null&&found.problem()==PasswordTarget.Problem.ADDRESS,"hidden address reason");
            found=page("no_fields");check(found.target()==null&&found.problem()==PasswordTarget.Problem.NO_FIELDS,"no inputs reason");
            getTargetContext().startActivity(PasswordHelpActivity.intent(getTargetContext(),PasswordTarget.Problem.NONE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            found=PasswordTarget.inspectRoot(getTargetContext(),root(getTargetContext().getPackageName()));
            check(found.target()==null&&found.problem()==PasswordTarget.Problem.OWN_APP,"own page leads to help");
            report.putString("result","passed");report.putInt("checks",passed);finish(Activity.RESULT_OK,report);
        }catch(Throwable error){report.putString("result","failed");report.putString("error",error.toString());report.putInt("checks",passed);finish(Activity.RESULT_CANCELED,report);}
    }
}
