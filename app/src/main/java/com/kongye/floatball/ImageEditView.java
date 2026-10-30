package com.kongye.floatball;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.FrameLayout;
import java.util.*;

/** Source-space annotations remain individually selectable until the document is flattened. */
final class ImageEditView extends FrameLayout {
    private static final class Mark {
        String tool,text;int color,number;float width,size,x,y,ex,ey;Path path;Bitmap pixels;
        Mark copy(){Mark m=new Mark();m.tool=tool;m.text=text;m.color=color;m.number=number;m.width=width;m.size=size;m.x=x;m.y=y;m.ex=ex;m.ey=ey;m.path=path==null?null:new Path(path);m.pixels=pixels;return m;}
    }
    private final Bitmap original;private Bitmap working;
    private final Paint paint=new Paint(3);
    private final RectF display=new RectF(),crop=new RectF();
    private final ArrayList<Mark> marks=new ArrayList<>();
    private Mark current,selected,gestureOriginal,typingMark;
    private String tool="pen";private int color=0xFFF44336,handle,numberBase;
    private float brush=4,scale,downX,downY,downRawX,downRawY;private float[] cropBefore;
    private boolean moving,hadSelection,hitExisting,resizing,navigating;private float zoom=1,panX,panY,baseScale,lastFocusX,lastFocusY,lastSpan;
    private SpannableStringBuilder inputBuffer;
    private final View deleteControl,resizeControl;

    ImageEditView(Context c,Bitmap source){
        super(c);original=working=source;crop.set(0,0,source.getWidth(),source.getHeight());
        setWillNotDraw(false);setLayerType(LAYER_TYPE_SOFTWARE,null);setFocusableInTouchMode(true);setContentDescription("图片编辑画布");
        deleteControl=control("close","删除选中标注",0xFFC2185B);deleteControl.setOnClickListener(v->deleteSelected());
        resizeControl=control("resize","缩放选中标注",Ui.GREEN);
        resizeControl.setOnTouchListener(new OnTouchListener(){
            float startX,startY;RectF before;
            public boolean onTouch(View view,MotionEvent event){if(selected==null)return false;switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN->{finishInput();resizing=true;gestureOriginal=selected.copy();before=markBounds(gestureOriginal);startX=event.getRawX();startY=event.getRawY();return true;}
                case MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP->{if(!resizing)return false;float w=Math.max(1,before.width()),h=Math.max(1,before.height());float dx=(event.getRawX()-startX)/scale,dy=(event.getRawY()-startY)/scale;float factor=Math.max(.15f,Math.min(8f,1+(dx*w+dy*h)/(w*w+h*h)));resizeMark(selected,gestureOriginal,before.left,before.top,factor);refresh();if(event.getActionMasked()==MotionEvent.ACTION_UP){resizing=false;view.performClick();}return true;}
                case MotionEvent.ACTION_CANCEL->{if(gestureOriginal!=null)resizeMark(selected,gestureOriginal,before.left,before.top,1);resizing=false;refresh();return true;}
            }return true;}
        });
    }
    private View control(String icon,String label,int color){FrameLayout button=new FrameLayout(getContext());button.setContentDescription(label);button.setClickable(true);button.setBackground(Ui.bg(color,100,getContext()));button.addView(Ui.icon(getContext(),icon,Color.WHITE,20),new FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER));addView(button,new FrameLayout.LayoutParams(dp(30),dp(30)));button.setVisibility(GONE);return button;}
    private int dp(float value){return Ui.dp(getContext(),value);}
    void tool(String value){finishInput();discardEmptyText();tool=value;current=null;selected=null;refresh();}
    String tool(){return tool;}
    void color(int value){color=value;}void width(float value){brush=value;}
    void textColor(int value){color=value;if(selected!=null&&selected.tool.equals("text"))selected.color=value;refresh();}
    private Runnable textDone;
    void onTextDone(Runnable action){textDone=action;}
    private void fit(){if(getWidth()==0||getHeight()==0)return;baseScale=Math.min(getWidth()/(float)working.getWidth(),getHeight()/(float)working.getHeight());viewport();}
    private void viewport(){scale=baseScale*zoom;float w=working.getWidth()*scale,h=working.getHeight()*scale;float limitX=Math.max(0,(w-getWidth())/2),limitY=Math.max(0,(h-getHeight())/2);panX=Math.max(-limitX,Math.min(limitX,panX));panY=Math.max(-limitY,Math.min(limitY,panY));display.set((getWidth()-w)/2+panX,(getHeight()-h)/2+panY,(getWidth()+w)/2+panX,(getHeight()+h)/2+panY);refresh();}
    float zoomFactor(){return zoom;}
    RectF viewportBounds(){return new RectF(display);}
    private void cancelDrawingForNavigation(){finishText();if(current!=null){marks.remove(current);recycle(current);current=null;}if(hitExisting&&selected!=null&&gestureOriginal!=null)moveMark(selected,gestureOriginal,0,0);hitExisting=false;resizing=false;}
    @Override public boolean onInterceptTouchEvent(MotionEvent e){return e.getPointerCount()>1||navigating||super.onInterceptTouchEvent(e);}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);fit();}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){super.onLayout(changed,l,t,r,b);positionControls();}
    private Mark mark(String type,float x,float y){Mark m=new Mark();m.tool=type;m.color=color;m.width=dp(brush)/Math.max(.01f,scale);if(type.equals("highlight"))m.width*=5;m.size=dp(20+brush)/Math.max(.01f,scale);m.x=m.ex=x;m.y=m.ey=y;return m;}
    void text(float x,float y,String value){if(value.isBlank())return;Mark m=mark("text",x,y);m.text=value;marks.add(m);refresh();}
    String selectedText(){return selected!=null&&selected.tool.equals("text")?selected.text:"";}
    void loadWatermark(String value){clearMarks();org.json.JSONArray entries=RecordingWatermark.parse(value);for(int i=0;i<entries.length();i++){org.json.JSONObject item=entries.optJSONObject(i);if(item==null)continue;Mark m=mark("text",(float)item.optDouble("x",.5)*working.getWidth(),(float)item.optDouble("y",.5)*working.getHeight());m.text=item.optString("text","");m.color=item.optInt("color",Color.WHITE);m.size=(float)item.optDouble("size",.025)*working.getHeight();marks.add(m);}refresh();}
    String watermark(){finishText();org.json.JSONArray entries=new org.json.JSONArray();for(Mark m:marks)if(m.tool.equals("text")&&!m.text.isBlank())try{entries.put(new org.json.JSONObject().put("text",m.text).put("x",m.x/working.getWidth()).put("y",m.y/working.getHeight()).put("size",m.size/working.getHeight()).put("color",m.color));}catch(org.json.JSONException ignored){}return entries.length()==0?"":entries.toString();}
    void updateSelectedText(String value){if(selected!=null&&selected.tool.equals("text")){selected.text=value;refresh();}}
    void deleteSelectedText(){deleteSelected();}
    private void deleteSelected(){finishInput();if(selected!=null){marks.remove(selected);recycle(selected);selected=null;}refresh();}
    private void discardEmptyText(){if(selected!=null&&selected.tool.equals("text")&&selected.text.isBlank()){marks.remove(selected);selected=null;}}
    void finishText(){finishInput();discardEmptyText();refresh();}
    boolean isTyping(){return typingMark!=null;}
    private void finishInput(){if(typingMark==null)return;typingMark=null;inputBuffer=null;getContext().getSystemService(InputMethodManager.class).hideSoftInputFromWindow(getWindowToken(),0);}
    private void startInput(){if(selected==null||!selected.tool.equals("text"))return;typingMark=selected;inputBuffer=new SpannableStringBuilder(selected.text);inputBuffer.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5000)});Selection.setSelection(inputBuffer,inputBuffer.length());requestFocus();InputMethodManager ime=getContext().getSystemService(InputMethodManager.class);ime.restartInput(this);postDelayed(()->{if(typingMark!=null&&isAttachedToWindow())ime.showSoftInput(this,InputMethodManager.SHOW_IMPLICIT);},100);}
    @Override public boolean onCheckIsTextEditor(){return typingMark!=null;}
    @Override public InputConnection onCreateInputConnection(EditorInfo info){
        if(typingMark==null)return null;final Mark target=typingMark;final SpannableStringBuilder buffer=inputBuffer;
        info.inputType=InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE;info.imeOptions=EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_FULLSCREEN;info.initialSelStart=Selection.getSelectionStart(buffer);info.initialSelEnd=Selection.getSelectionEnd(buffer);
        return new BaseInputConnection(this,true){
            @Override public Editable getEditable(){return buffer;}
            private void sync(){if(typingMark==target&&marks.contains(target)){target.text=buffer.toString();refresh();}}
            @Override public boolean commitText(CharSequence value,int cursor){boolean ok=super.commitText(value,cursor);sync();return ok;}
            @Override public boolean setComposingText(CharSequence value,int cursor){boolean ok=super.setComposingText(value,cursor);sync();return ok;}
            @Override public boolean deleteSurroundingText(int before,int after){boolean ok=super.deleteSurroundingText(before,after);sync();return ok;}
            @Override public boolean deleteSurroundingTextInCodePoints(int before,int after){boolean ok=super.deleteSurroundingTextInCodePoints(before,after);sync();return ok;}
            @Override public boolean finishComposingText(){boolean ok=super.finishComposingText();sync();return ok;}
            @Override public boolean performEditorAction(int action){finishText();if(textDone!=null)textDone.run();return true;}
            @Override public boolean sendKeyEvent(KeyEvent event){return event.getAction()==KeyEvent.ACTION_DOWN?onKeyDown(event.getKeyCode(),event):true;}
        };
    }
    @Override public boolean onKeyDown(int code,KeyEvent event){if(typingMark!=null&&inputBuffer!=null){int start=Math.max(0,Selection.getSelectionStart(inputBuffer)),end=Math.max(start,Selection.getSelectionEnd(inputBuffer));if(code==KeyEvent.KEYCODE_DEL){if(start==end&&start>0)start=Character.offsetByCodePoints(inputBuffer,start,-1);inputBuffer.delete(start,end);Selection.setSelection(inputBuffer,start);}else{int unicode=code==KeyEvent.KEYCODE_ENTER?'\n':event.getUnicodeChar();if(unicode==0)return super.onKeyDown(code,event);String value=new String(Character.toChars(unicode));inputBuffer.replace(start,end,value);Selection.setSelection(inputBuffer,start+value.length());}typingMark.text=inputBuffer.toString();refresh();return true;}return super.onKeyDown(code,event);}
    private RectF textBounds(Mark m){paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(m.size);float width=m.size;String[] lines=m.text.split("\\n",-1);for(String line:lines)width=Math.max(width,paint.measureText(line));return new RectF(m.x,m.y+paint.ascent(),m.x+width,m.y+paint.descent()+(lines.length-1)*paint.getFontSpacing());}
    private RectF markBounds(Mark m){RectF r;if(m.tool.equals("text"))return textBounds(m);if(m.tool.equals("number")){float radius=m.size*.65f;return new RectF(m.ex-radius,m.ey-radius,m.ex+radius,m.ey+radius);}if(m.path!=null){r=new RectF();m.path.computeBounds(r,true);}else r=rect(m);if(m.tool.equals("arrow")){double angle=Math.atan2(m.ey-m.y,m.ex-m.x);float size=Math.max(m.width*4,m.size*.65f);for(double side:new double[]{-.48,.48})r.union(m.ex-size*(float)Math.cos(angle+side),m.ey-size*(float)Math.sin(angle+side));}r.inset(-m.width/2,-m.width/2);return r;}
    private boolean selectable(Mark m){return !m.tool.equals("mosaic");}
    private Mark hit(float x,float y){float pad=dp(9)/scale;for(int i=marks.size()-1;i>=0;i--){Mark m=marks.get(i);if(!selectable(m))continue;RectF box=markBounds(m);box.inset(-pad,-pad);if(!box.contains(x,y))continue;if(m.path!=null){PathMeasure measure=new PathMeasure(m.path,false);float[] point=new float[2];boolean found=false;for(float d=0;d<=measure.getLength();d+=Math.max(1,pad/2)){measure.getPosTan(d,point,null);if(Math.hypot(point[0]-x,point[1]-y)<pad+m.width/2){found=true;break;}}if(!found)continue;}else if(m.tool.equals("line")||m.tool.equals("arrow")){float vx=m.ex-m.x,vy=m.ey-m.y,den=vx*vx+vy*vy;float t=den==0?0:Math.max(0,Math.min(1,((x-m.x)*vx+(y-m.y)*vy)/den));boolean near=Math.hypot(x-m.x-t*vx,y-m.y-t*vy)<=pad+m.width/2;if(!near&&m.tool.equals("arrow")){double angle=Math.atan2(m.ey-m.y,m.ex-m.x);float size=Math.max(m.width*4,m.size*.65f);for(double side:new double[]{-.48,.48})near|=segmentDistance(x,y,m.ex,m.ey,m.ex-size*(float)Math.cos(angle+side),m.ey-size*(float)Math.sin(angle+side))<=pad+m.width/2;}if(!near)continue;}return m;}return null;}
    private float segmentDistance(float x,float y,float ax,float ay,float bx,float by){float vx=bx-ax,vy=by-ay,den=vx*vx+vy*vy,t=den==0?0:Math.max(0,Math.min(1,((x-ax)*vx+(y-ay)*vy)/den));return (float)Math.hypot(x-ax-t*vx,y-ay-t*vy);}
    private RectF selectionBox(){RectF box=markBounds(selected);float min=dp(36)/scale;if(box.width()<min){float center=box.centerX();box.left=center-min/2;box.right=center+min/2;}if(box.height()<min){float center=box.centerY();box.top=center-min/2;box.bottom=center+min/2;}return box;}
    private void refresh(){positionControls();invalidate();}
    private void positionControls(){boolean visible=selected!=null&&selectable(selected)&&current==null;deleteControl.setVisibility(visible?VISIBLE:GONE);resizeControl.setVisibility(visible?VISIBLE:GONE);if(!visible||scale<=0)return;RectF box=selectionBox();place(deleteControl,display.left+box.right*scale,display.top+box.top*scale);place(resizeControl,display.left+box.right*scale,display.top+box.bottom*scale);if(resizeControl.getY()-deleteControl.getY()<dp(32)){deleteControl.setY(Math.max(0,resizeControl.getY()-dp(34)));resizeControl.setY(Math.min(getHeight()-dp(30),deleteControl.getY()+dp(34)));}}
    private void place(View control,float x,float y){float size=dp(30);control.setX(Math.max(0,Math.min(getWidth()-size,x+dp(3))));control.setY(Math.max(0,Math.min(getHeight()-size,y-size/2)));}
    RectF selectedBounds(){if(selected==null)return null;RectF box=markBounds(selected);return new RectF(display.left+box.left*scale,display.top+box.top*scale,display.left+box.right*scale,display.top+box.bottom*scale);}
    boolean selectionVisible(){return selected!=null;}
    private void resizeMark(Mark m,Mark before,float x,float y,float factor){m.x=x+(before.x-x)*factor;m.y=y+(before.y-y)*factor;m.ex=x+(before.ex-x)*factor;m.ey=y+(before.ey-y)*factor;m.width=before.width*factor;m.size=before.size*factor;if(before.path!=null){m.path=new Path(before.path);Matrix matrix=new Matrix();matrix.setScale(factor,factor,x,y);m.path.transform(matrix);}}
    private void moveMark(Mark m,Mark before,float dx,float dy){m.x=before.x+dx;m.y=before.y+dy;m.ex=before.ex+dx;m.ey=before.ey+dy;if(before.path!=null){m.path=new Path(before.path);m.path.offset(dx,dy);}}
    private void drawMark(Canvas canvas,Mark m){paint.setColor(m.color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(m.width);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setPathEffect(null);paint.setShader(null);paint.setFilterBitmap(false);
        switch(m.tool){case "pen"->canvas.drawPath(m.path,paint);case "highlight"->{paint.setAlpha(88);canvas.drawPath(m.path,paint);paint.setAlpha(255);}case "rect"->canvas.drawRect(rect(m),paint);case "line"->canvas.drawLine(m.x,m.y,m.ex,m.ey,paint);case "arrow"->{canvas.drawLine(m.x,m.y,m.ex,m.ey,paint);double angle=Math.atan2(m.ey-m.y,m.ex-m.x);float size=Math.max(m.width*4,m.size*.65f);for(double side:new double[]{-.48,.48})canvas.drawLine(m.ex,m.ey,m.ex-size*(float)Math.cos(angle+side),m.ey-size*(float)Math.sin(angle+side),paint);}case "number"->{paint.setStyle(Paint.Style.FILL);canvas.drawCircle(m.ex,m.ey,m.size*.65f,paint);paint.setColor(Color.WHITE);paint.setTextSize(m.size*.8f);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);Paint.FontMetrics fm=paint.getFontMetrics();canvas.drawText(String.valueOf(m.number),m.ex,m.ey-(fm.ascent+fm.descent)/2,paint);paint.setTextAlign(Paint.Align.LEFT);}case "text"->{paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(m.size);paint.setTextAlign(Paint.Align.LEFT);float baseline=m.y;for(String line:m.text.split("\\n",-1)){canvas.drawText(line,m.x,baseline,paint);baseline+=paint.getFontSpacing();}}case "mosaic"->{RectF r=rect(m);if(m.pixels!=null)canvas.drawBitmap(m.pixels,null,r,paint);else{paint.setColor(0x8847C8F4);canvas.drawRect(r,paint);}}}
    }
    private RectF rect(Mark m){return new RectF(Math.min(m.x,m.ex),Math.min(m.y,m.ey),Math.max(m.x,m.ex),Math.max(m.y,m.ey));}
    private void drawDocument(Canvas c,Mark omit){c.drawBitmap(working,0,0,null);for(Mark mark:marks)if(mark!=omit)drawMark(c,mark);}
    private void pixelate(Mark m){RectF r=rect(m);if(r.width()<1||r.height()<1){marks.remove(m);return;}Bitmap tile=Bitmap.createBitmap(Math.max(1,(int)r.width()/18),Math.max(1,(int)r.height()/18),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(tile);c.scale(tile.getWidth()/r.width(),tile.getHeight()/r.height());c.translate(-r.left,-r.top);drawDocument(c,m);m.pixels=tile;}
    @Override protected void onDraw(Canvas c){super.onDraw(c);if(working.isRecycled()||scale<=0)return;c.save();c.translate(display.left,display.top);c.scale(scale,scale);drawDocument(c,null);if(selected!=null&&current==null){paint.setStyle(Paint.Style.STROKE);paint.setColor(Ui.GREEN);paint.setStrokeWidth(1/scale);RectF box=selectionBox();box.inset(-3/scale,-3/scale);c.drawRect(box,paint);}if(tool.equals("crop")){paint.setStyle(Paint.Style.FILL);paint.setColor(0x99000000);c.drawRect(0,0,working.getWidth(),crop.top,paint);c.drawRect(0,crop.bottom,working.getWidth(),working.getHeight(),paint);c.drawRect(0,crop.top,crop.left,crop.bottom,paint);c.drawRect(crop.right,crop.top,working.getWidth(),crop.bottom,paint);paint.setStyle(Paint.Style.STROKE);paint.setColor(Ui.GREEN);paint.setStrokeWidth(2/scale);c.drawRect(crop,paint);}c.restore();}
    @Override public boolean onTouchEvent(MotionEvent e){if(scale<=0)return false;if(e.getPointerCount()>1||navigating){if(!navigating){navigating=true;cancelDrawingForNavigation();lastFocusX=(e.getX(0)+e.getX(1))/2;lastFocusY=(e.getY(0)+e.getY(1))/2;lastSpan=(float)Math.hypot(e.getX(1)-e.getX(0),e.getY(1)-e.getY(0));}if(e.getPointerCount()>1&&e.getActionMasked()==MotionEvent.ACTION_MOVE){float x=(e.getX(0)+e.getX(1))/2,y=(e.getY(0)+e.getY(1))/2,span=(float)Math.hypot(e.getX(1)-e.getX(0),e.getY(1)-e.getY(0));float docX=(lastFocusX-display.left)/scale,docY=(lastFocusY-display.top)/scale;if(lastSpan>dp(8))zoom=Math.max(1,Math.min(8,zoom*span/lastSpan));float next=baseScale*zoom;panX=x-docX*next-(getWidth()-working.getWidth()*next)/2;panY=y-docY*next-(getHeight()-working.getHeight()*next)/2;lastFocusX=x;lastFocusY=y;lastSpan=span;viewport();}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){navigating=false;refresh();}return true;}float x=Math.max(0,Math.min(working.getWidth(),(e.getX()-display.left)/scale)),y=Math.max(0,Math.min(working.getHeight(),(e.getY()-display.top)/scale));switch(e.getActionMasked()){
        case MotionEvent.ACTION_DOWN->{downX=x;downY=y;downRawX=e.getRawX();downRawY=e.getRawY();moving=false;hadSelection=selected!=null;current=null;hitExisting=false;if(tool.equals("crop")){finishText();selected=null;float radius=dp(28)/scale;handle=0;if(Math.abs(x-crop.left)<radius)handle|=CropGeometry.LEFT;else if(Math.abs(x-crop.right)<radius)handle|=CropGeometry.RIGHT;if(Math.abs(y-crop.top)<radius)handle|=CropGeometry.TOP;else if(Math.abs(y-crop.bottom)<radius)handle|=CropGeometry.BOTTOM;cropBefore=new float[]{crop.left,crop.top,crop.right,crop.bottom};}else{Mark found=hit(x,y);if(found!=selected)finishInput();discardEmptyText();selected=found;hitExisting=found!=null;gestureOriginal=found==null?null:found.copy();}refresh();return true;}
        case MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP->{x=Math.max(0,Math.min(working.getWidth(),downX+(e.getRawX()-downRawX)/scale));y=Math.max(0,Math.min(working.getHeight(),downY+(e.getRawY()-downRawY)/scale));moving|=Math.hypot(x-downX,y-downY)>dp(3)/scale;if(tool.equals("crop")){float[] r=CropGeometry.adjust(cropBefore,handle,x-downX,y-downY,working.getWidth(),working.getHeight(),Math.min(32,Math.min(working.getWidth(),working.getHeight())));crop.set(r[0],r[1],r[2],r[3]);}else if(hitExisting){if(moving){finishInput();moveMark(selected,gestureOriginal,x-downX,y-downY);}}else if(!tool.equals("text")&&(moving||(!hadSelection&&e.getActionMasked()==MotionEvent.ACTION_UP))){if(current==null){current=mark(tool,downX,downY);if(tool.equals("pen")||tool.equals("highlight")){current.path=new Path();current.path.moveTo(downX,downY);current.path.lineTo(downX+.1f,downY+.1f);}if(tool.equals("number")){int n=numberBase+1;for(Mark m:marks)if(m.tool.equals("number"))n=Math.max(n,m.number+1);current.number=n;}marks.add(current);}current.ex=x;current.ey=y;if(current.path!=null)current.path.lineTo(x,y);}
            if(e.getActionMasked()==MotionEvent.ACTION_UP){if(current!=null){if(current.tool.equals("mosaic"))pixelate(current);selected=selectable(current)?current:null;current=null;}else if(tool.equals("text")&&!moving&&!hitExisting&&!hadSelection){selected=mark("text",x,y);selected.text="";marks.add(selected);}if(!moving&&selected!=null&&selected.tool.equals("text")){startInput();}performClick();}refresh();return true;}
        case MotionEvent.ACTION_CANCEL->{if(current!=null){marks.remove(current);recycle(current);current=null;}if(hitExisting&&selected!=null&&gestureOriginal!=null)moveMark(selected,gestureOriginal,0,0);refresh();return true;}
    }return false;}
    @Override public boolean performClick(){super.performClick();return true;}
    private void recycle(Mark m){if(m.pixels!=null)m.pixels.recycle();}
    private void clearMarks(){finishInput();for(Mark m:marks)recycle(m);marks.clear();current=null;selected=null;}
    void undo(){finishInput();selected=null;if(!marks.isEmpty())recycle(marks.remove(marks.size()-1));else crop.set(0,0,working.getWidth(),working.getHeight());refresh();}
    void reset(){zoom=1;panX=panY=0;numberBase=0;clearMarks();if(working!=original)working.recycle();working=original;crop.set(0,0,working.getWidth(),working.getHeight());fit();}
    void rotate(){transform(true,false);}void mirror(boolean vertical){transform(false,vertical);}
    private void transform(boolean rotate,boolean vertical){finishText();Bitmap flat=render();Matrix matrix=new Matrix();if(rotate)matrix.setRotate(90);else matrix.setScale(vertical?1:-1,vertical?-1:1);Bitmap next;try{next=Bitmap.createBitmap(flat,0,0,flat.getWidth(),flat.getHeight(),matrix,true);}catch(RuntimeException|OutOfMemoryError error){flat.recycle();throw error;}if(next!=flat)flat.recycle();for(Mark m:marks)if(m.tool.equals("number"))numberBase=Math.max(numberBase,m.number);clearMarks();if(working!=original)working.recycle();working=next;zoom=1;panX=panY=0;crop.set(0,0,next.getWidth(),next.getHeight());fit();}
    Bitmap render(){int left=Math.max(0,Math.round(crop.left)),top=Math.max(0,Math.round(crop.top)),w=Math.max(1,Math.min(working.getWidth()-left,Math.round(crop.width()))),h=Math.max(1,Math.min(working.getHeight()-top,Math.round(crop.height())));Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(result);c.translate(-left,-top);drawDocument(c,null);return result;}
    String marks(){StringBuilder b=new StringBuilder();for(Mark m:marks)b.append(m.tool).append(',');return b.toString();}
    void dispose(){textDone=null;clearMarks();if(working!=original&&!working.isRecycled())working.recycle();}
}
