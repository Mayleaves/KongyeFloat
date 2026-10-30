package com.kongye.floatball;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.widget.Toast;

public class FilePickerActivity extends Activity {
    @Override public void onCreate(Bundle saved){super.onCreate(saved);if(saved==null){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),7);}catch(ActivityNotFoundException e){Feedback.show(this,"没有可用的系统文件选择器");finish();}}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==7&&result==RESULT_OK&&data!=null&&data.getData()!=null){try{Intent open=new Intent(Intent.ACTION_VIEW).setDataAndType(data.getData(),getContentResolver().getType(data.getData())).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(open,"打开文件"));}catch(ActivityNotFoundException e){Feedback.show(this,"没有可打开此文件的应用");}}finish();}
}
