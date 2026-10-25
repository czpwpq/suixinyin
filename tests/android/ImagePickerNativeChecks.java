package org.nfcresearch.qpolllab;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import java.util.ArrayList;

/** Native Intent/ClipData policy checks. Does not open a picker, provider or network. */
public final class ImagePickerNativeChecks {
    private ImagePickerNativeChecks() { }
    public static void run() {
        Intent request=ImagePicker.request(true);
        check(Intent.ACTION_OPEN_DOCUMENT.equals(request.getAction()),"Must use document selection");
        check("image/*".equals(request.getType())&&request.hasCategory(Intent.CATEGORY_OPENABLE),"Image/openable filter");
        check(request.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,false),"Multi-select flag missing");
        check(!ImagePicker.request(false).getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,true),"Single picker permits multiple");
        Uri one=Uri.parse("content://native-image-test/photo/1"),two=Uri.parse("content://native-image-test/photo/2"),three=Uri.parse("content://native-image-test/photo/3");
        ClipData clip=ClipData.newRawUri("images",one);clip.addItem(new ClipData.Item(two));clip.addItem(new ClipData.Item(one));
        Intent result=new Intent();result.setClipData(clip);result.setData(three);
        ArrayList<Uri> selected=ImagePicker.collect(result,3);
        check(selected.size()==3&&one.equals(selected.get(0))&&two.equals(selected.get(1))&&three.equals(selected.get(2)),"ClipData/data union or order/deduplication failed");
        result.setData(one);check(ImagePicker.collect(result,2).size()==2,"Duplicate getData consumed capacity");
        rejected(()->ImagePicker.collect(result,1));
        check(ImagePicker.collect(null,0).isEmpty(),"Cancelled result must be empty");
        check(ImagePicker.collect(new Intent(),0).isEmpty(),"Missing URI must be empty");
        rejected(()->ImagePicker.collect(new Intent().setData(Uri.parse("file:///sdcard/secret.jpg")),30));
        rejected(()->ImagePicker.collect(new Intent().setData(Uri.parse("https://example.com/image.png")),30));
        rejected(()->ImagePicker.collect(new Intent(),-1));
    }
    private static void rejected(Runnable action){try{action.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Unsafe or over-capacity selection accepted");}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
