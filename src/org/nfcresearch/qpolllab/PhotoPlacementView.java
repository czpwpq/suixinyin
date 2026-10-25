package org.nfcresearch.qpolllab;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Same transform is used for gesture preview and exported photo pixels. */
public final class PhotoPlacementView extends View {
    private Bitmap photo;
    private float zoom=1,offsetX,offsetY,angle,lastX,lastY,viewScale=1;
    private int targetWidth=504,targetHeight=600,background=Color.WHITE;
    private boolean cover=true;
    private Runnable changed;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final ScaleGestureDetector pinch;
    public PhotoPlacementView(Context context){
        super(context);setContentDescription("照片构图，单指移动，双指缩放");
        pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d){zoom=Math.max(.25f,Math.min(8,zoom*d.getScaleFactor()));invalidate();signal();return true;}
        });
    }
    public void setChanged(Runnable callback){changed=callback;}
    public void setPhoto(Bitmap bitmap){photo=bitmap;reset();}
    public void setTarget(int width,int height,int color){targetWidth=width;targetHeight=height;background=color;invalidate();}
    public void setAngle(float degrees){angle=degrees;invalidate();signal();}
    public void setCover(boolean value){cover=value;invalidate();signal();}
    /** Show the complete rotated photo; placement changes do not reset its chosen angle. */
    public void fitInside(){cover=false;zoom=1;offsetX=offsetY=0;invalidate();signal();}
    /** Cover the target rectangle, including corners at non-right-angle rotations. */
    public void fill(){cover=true;zoom=1;offsetX=offsetY=0;invalidate();signal();}
    /** Restore the center while retaining rotation, fit/fill choice and user zoom. */
    public void center(){offsetX=offsetY=0;invalidate();signal();}
    public void reset(){zoom=1;offsetX=offsetY=angle=0;invalidate();signal();}
    private void signal(){if(changed!=null)changed.run();}
    private void drawPhoto(Canvas canvas){
        canvas.drawColor(background);
        if(photo==null)return;
        double radians=Math.toRadians(angle),cos=Math.abs(Math.cos(radians)),sin=Math.abs(Math.sin(radians));
        double base;
        if(cover){
            // Inverse-rotate target corners into photo space. Both source axes must
            // contain that rectangle; using the rotated photo AABB would leave gaps.
            base=Math.max((cos*targetWidth+sin*targetHeight)/photo.getWidth(),
                          (sin*targetWidth+cos*targetHeight)/photo.getHeight());
        }else{
            double rotatedWidth=cos*photo.getWidth()+sin*photo.getHeight();
            double rotatedHeight=sin*photo.getWidth()+cos*photo.getHeight();
            base=Math.min(targetWidth/rotatedWidth,targetHeight/rotatedHeight);
        }
        float scale=(float)base*zoom;
        canvas.save();canvas.translate(targetWidth/2f+offsetX,targetHeight/2f+offsetY);canvas.rotate(angle);canvas.scale(scale,scale);
        canvas.drawBitmap(photo,-photo.getWidth()/2f,-photo.getHeight()/2f,paint);canvas.restore();
    }
    public Bitmap export(){Bitmap b=Bitmap.createBitmap(targetWidth,targetHeight,Bitmap.Config.ARGB_8888);drawPhoto(new Canvas(b));return b;}
    @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(0xffe4e9e6);viewScale=Math.min((getWidth()-8f)/targetWidth,(getHeight()-8f)/targetHeight);if(viewScale<=0)return;
        c.save();c.translate((getWidth()-targetWidth*viewScale)/2,(getHeight()-targetHeight*viewScale)/2);c.scale(viewScale,viewScale);c.clipRect(0,0,targetWidth,targetHeight);drawPhoto(c);c.restore();}
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!isEnabled())return false;
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);pinch.onTouchEvent(event);
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=event.getX();lastY=event.getY();return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE){if(event.getPointerCount()==1&&!pinch.isInProgress()){offsetX=Math.max(-targetWidth*3,Math.min(targetWidth*3,offsetX+(event.getX()-lastX)/Math.max(.01f,viewScale)));offsetY=Math.max(-targetHeight*3,Math.min(targetHeight*3,offsetY+(event.getY()-lastY)/Math.max(.01f,viewScale)));invalidate();signal();}lastX=event.getX();lastY=event.getY();return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_POINTER_UP){int remaining=event.getActionIndex()==0?1:0;if(remaining<event.getPointerCount()){lastX=event.getX(remaining);lastY=event.getY(remaining);}}
        if(event.getActionMasked()==MotionEvent.ACTION_UP){performClick();if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);return true;}
        if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);return true;}
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
