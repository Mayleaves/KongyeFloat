package com.kongye.floatball;

import android.content.Context;
import android.graphics.*;
import android.view.animation.PathInterpolator;
import org.json.*;
import java.util.*;

/** Small offline scene renderer; uses the supplied SVG paths and CSS keyframes. */
final class PlantScene {
    private static JSONArray source;
    final Node root;
    PlantScene(Context c,int stage)throws Exception{
        if(source==null)try(java.io.InputStream in=c.getAssets().open("branding/plant-motion/scenes.json")){java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);source=new JSONArray(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));}
        JSONObject data=source.getJSONObject(stage),rules=data.getJSONObject("motions");Map<String,Motion> motions=new HashMap<>();
        for(Iterator<String> keys=rules.keys();keys.hasNext();){String key=keys.next();motions.put(key,new Motion(rules.getJSONObject(key)));}
        root=new Node(c,data.getJSONObject("tree"),motions);
    }
    void draw(Canvas c,long time){root.draw(c,time,1);}
    private static float[] floats(JSONArray a)throws JSONException{float[] out=new float[a.length()];for(int i=0;i<out.length;i++)out[i]=(float)a.getDouble(i);return out;}
    static final class Motion {
        final float ox,oy,duration,delay;final float[][] frames;final float[] value=new float[6];final PathInterpolator ease;
        Motion(JSONObject j)throws JSONException{float[] origin=floats(j.getJSONArray("origin")),curve=floats(j.getJSONArray("ease"));ox=origin[0];oy=origin[1];duration=(float)j.getDouble("duration");delay=(float)j.getDouble("delay");ease=new PathInterpolator(curve[0],curve[1],curve[2],curve[3]);JSONArray f=j.getJSONArray("frames");frames=new float[f.length()][];for(int i=0;i<f.length();i++)frames[i]=floats(f.getJSONArray(i));}
        float[] sample(long time){float p=((time-delay)%duration+duration)%duration/duration;int next=1;while(next<frames.length-1&&p>frames[next][0])next++;float[] a=frames[next-1],b=frames[next];float t=ease.getInterpolation(Math.max(0,Math.min(1,(p-a[0])/Math.max(.0001f,b[0]-a[0]))));for(int i=0;i<6;i++)value[i]=a[i+1]+(b[i+1]-a[i+1])*t;return value;}
    }
    /** Draw paths directly at the Canvas device scale, avoiding VectorDrawable bitmap caches on rotation. */
    static final class Shape {
        final Path path=new Path();final Paint fill,stroke;
        Shape(JSONObject j)throws JSONException{
            if(j.has("circle")){float[] v=floats(j.getJSONArray("circle"));path.addCircle(v[0],v[1],v[2],Path.Direction.CW);}else{JSONArray ops=j.getJSONArray("commands");float x=0,y=0,startX=0,startY=0;for(int i=0;i<ops.length();i++){JSONArray op=ops.getJSONArray(i);switch(op.getString(0)){
                case "M":x=(float)op.getDouble(1);y=(float)op.getDouble(2);startX=x;startY=y;path.moveTo(x,y);break;
                case "L":x=(float)op.getDouble(1);y=(float)op.getDouble(2);path.lineTo(x,y);break;
                case "H":x=(float)op.getDouble(1);path.lineTo(x,y);break;
                case "V":y=(float)op.getDouble(1);path.lineTo(x,y);break;
                case "C":x=(float)op.getDouble(5);y=(float)op.getDouble(6);path.cubicTo((float)op.getDouble(1),(float)op.getDouble(2),(float)op.getDouble(3),(float)op.getDouble(4),x,y);break;
                case "Z":path.close();x=startX;y=startY;break;
                default:throw new JSONException("Unsupported SVG command");
            }}}
            int color=Color.parseColor(j.getString("fill"));fill=Color.alpha(color)==0?null:new Paint(3);if(fill!=null)fill.setColor(color);
            if(!j.isNull("stroke")){stroke=new Paint(3);stroke.setColor(Color.parseColor(j.getString("stroke")));stroke.setStyle(Paint.Style.STROKE);stroke.setStrokeWidth((float)j.getDouble("width"));stroke.setStrokeCap(j.optString("cap").equals("round")?Paint.Cap.ROUND:Paint.Cap.BUTT);stroke.setStrokeJoin(j.optString("join").equals("round")?Paint.Join.ROUND:Paint.Join.MITER);}else stroke=null;
        }
        void draw(Canvas c,int alpha){if(fill!=null){fill.setAlpha(alpha);c.drawPath(path,fill);}if(stroke!=null){stroke.setAlpha(alpha);c.drawPath(path,stroke);}}
    }
    static final class Node {
        final Shape art;final Motion motion;final Node[] children;final float opacity;
        Node(Context c,JSONObject j,Map<String,Motion> rules)throws JSONException{art=j.has("shape")?new Shape(j.getJSONObject("shape")):null;motion=rules.get(j.optString("motion"));opacity=(float)j.optDouble("opacity",1);JSONArray list=j.optJSONArray("children");children=new Node[list==null?0:list.length()];for(int i=0;i<children.length;i++)children[i]=new Node(c,list.getJSONObject(i),rules);}
        void draw(Canvas c,long time,float parentAlpha){c.save();float alpha=parentAlpha*opacity;if(motion!=null){float[] v=motion.sample(time);c.translate(motion.ox+v[0],motion.oy+v[1]);c.rotate(v[4]);c.scale(v[2],v[3]);c.translate(-motion.ox,-motion.oy);alpha*=v[5];}if(alpha>.001f){if(art!=null){art.draw(c,Math.round(255*Math.min(1,alpha)));}for(Node child:children)child.draw(c,time,alpha);}c.restore();}
    }
}
