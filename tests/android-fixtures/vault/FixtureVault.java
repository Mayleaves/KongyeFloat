package com.kongye.fixture;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import java.util.ArrayList;

/** Installed only as com.kongye.docs.docktest for generated-data integration checks. */
public class FixtureVault extends ContentProvider {
    static boolean enabled,unlocked,denied,missing,empty;
    static int reveals,binds;
    public boolean onCreate(){return true;}
    static Bundle state(String value){Bundle b=new Bundle();b.putString("status",value);return b;}
    @Override public Bundle call(String method,String arg,Bundle input){
        if(!"com.kongye.floatball".equals(getCallingPackage()))return state("denied");
        Bundle args=input==null?Bundle.EMPTY:input;
        if(method.equals("fixture_config")){
            String mode=args.getString("mode","");enabled=!mode.equals("disabled");unlocked=mode.equals("unlocked")||mode.equals("empty");denied=mode.equals("denied");missing=mode.equals("missing");empty=mode.equals("empty");reveals=binds=0;return state("ok");
        }
        if(method.equals("fixture_state")){Bundle b=state("ok");b.putInt("reveals",reveals);b.putInt("binds",binds);return b;}
        if(denied)return state("denied");
        if(!enabled)return state("disabled");
        if(method.equals("status")){Bundle b=state("ok");b.putInt("version",1);b.putBoolean("enabled",true);b.putBoolean("unlocked",unlocked);return b;}
        if(method.equals("match")||method.equals("search")){
            ArrayList<Bundle> rows=new ArrayList<>();
            if((!method.equals("search")&&!empty)||(method.equals("search")&&args.getString("query","").equals("fixture"))){
                Bundle row=new Bundle();row.putString("id","fixture-only");row.putString("name","测试密码（模拟数据）");row.putString("account","fi****er");row.putString("matchedBy",method.equals("search")?"search":"domain");rows.add(row);
            }
            Bundle b=state("ok");b.putParcelableArrayList("entries",rows);return b;
        }
        if(method.equals("reveal")){
            reveals++;
            if(missing)return state("not_found");
            if(!unlocked){Bundle b=state("locked");b.putParcelable("unlock",PendingIntent.getActivity(getContext(),1,new Intent(getContext(),FixtureUnlock.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));return b;}
            Bundle b=state("ok");b.putString("account","fixture-user");b.putString("password","Fixture!2053");return b;
        }
        if(method.equals("bind")){binds++;return state("ok");}
        return state("error");
    }
    public Cursor query(Uri u,String[] p,String s,String[] a,String sort){return null;}
    public String getType(Uri u){return null;}
    public Uri insert(Uri u,ContentValues v){return null;}
    public int delete(Uri u,String s,String[] a){return 0;}
    public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
}
