package org.nfcresearch.qpolllab;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.MotionEvent;
import java.lang.reflect.Field;

/** Native Canvas geometry checks. No picker, network or NFC operations. */
public final class PhotoPlacementNativeChecks {
    private PhotoPlacementNativeChecks() { }
    public static void run(Activity activity) throws Exception {
        Bitmap source=Bitmap.createBitmap(83,47,Bitmap.Config.ARGB_8888);source.eraseColor(Color.BLACK);
        PhotoPlacementView placement=new PhotoPlacementView(activity);placement.setTarget(140,210,Color.WHITE);placement.setPhoto(source);
        for(float angle:new float[]{-180,-135,-90,-45,0,37,45,90,135,180}){
            placement.setAngle(angle);placement.fill();
            check(number(placement,"angle")==angle&&number(placement,"zoom")==1,"Fill reset the requested angle or zoom");
            Bitmap result=placement.export();
            // Check near each target corner and edge; a photo-AABB cover calculation
            // passes a bounding-box check but leaves these corners white at 45 degrees.
            for(int x:new int[]{2,70,137})for(int y:new int[]{2,105,207})check(result.getPixel(x,y)==Color.BLACK,"Fill left a target gap at angle "+angle);
            result.recycle();
        }
        placement.setAngle(45);placement.fitInside();Bitmap fit=placement.export();
        check(number(placement,"angle")==45,"Fit reset rotation");
        check(fit.getPixel(70,105)==Color.BLACK&&fit.getPixel(2,2)==Color.WHITE,"Fit did not preserve full rotated photo with margins");fit.recycle();
        set(placement,"zoom",2.5f);set(placement,"offsetX",47f);set(placement,"offsetY",-28f);placement.center();
        check(number(placement,"zoom")==2.5f&&number(placement,"angle")==45&&number(placement,"offsetX")==0&&number(placement,"offsetY")==0,"Center changed more than position");
        placement.reset();check(number(placement,"angle")==0&&number(placement,"zoom")==1,"Reset did not clear rotation/zoom");
        placement.setEnabled(false);MotionEvent event=MotionEvent.obtain(0,0,MotionEvent.ACTION_MOVE,90,110,0);
        try{check(!placement.onTouchEvent(event)&&number(placement,"offsetX")==0&&number(placement,"offsetY")==0,"Disabled crop canvas still accepts transfer-time gestures");}finally{event.recycle();}
        check(source.getPixel(0,0)==Color.BLACK&&!source.isRecycled(),"Export modified source ownership");source.recycle();
    }
    private static float number(Object object,String name) throws Exception {Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return ((Number)field.get(object)).floatValue();}
    private static void set(Object object,String name,float value) throws Exception {Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.setFloat(object,value);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
