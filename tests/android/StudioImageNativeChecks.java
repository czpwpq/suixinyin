package org.nfcresearch.qpolllab;

import android.graphics.Bitmap;
import java.util.Arrays;

/** Native Bitmap composition and payload invariants. Runs offline and never invokes NFC. */
public final class StudioImageNativeChecks {
    private static final int WIDTH=552,HEIGHT=768;
    public static void run() throws Exception {
        indexedFactory();
        int[] pixels=new int[WIDTH*HEIGHT];
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++)pixels[y*WIDTH+x]=0xff000000|((x*255/(WIDTH-1))<<16)|((y*255/(HEIGHT-1))<<8)|((x+y)&255);
        Bitmap source=Bitmap.createBitmap(pixels,WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
        PhotoEdit edit=new PhotoEdit();byte[] pristine=edit.encode();
        byte[] expected=InkSightQuantizer.quantize(pixels,WIDTH,HEIGHT,true);
        for(String panel:new String[]{"A0","A1",""}) {
            StudioImageCodec.Result result=StudioImageCodec.prepareEdited(source,edit,panel);
            check(result.preview==result.prepared.preview,"Result must expose the exact prepared preview");
            check(result.prepared.panelType.equals(panel),"Unselected panel state was lost");
            equal(pixels(result.originalPreview),pixels,"Same-size fitted pixels changed before enhancement");
            verify(result.prepared,expected,panel.isEmpty()?PanelType.A1:PanelType.parse(panel));
            recycle(result);
        }
        check(Arrays.equals(pristine,edit.encode()),"Preparing modified requested edit state");
        equal(pixels(source),pixels,"Preparing modified source Bitmap");
        check(!source.isRecycled(),"Preparing recycled caller's Bitmap");
        check(!source.isMutable(),"Immutable-source fixture unexpectedly permits drawing");

        // Mirroring, then rotating a landscape composition twice clockwise is a vertical flip.
        PhotoEdit transformed=new PhotoEdit();transformed.mirror=true;transformed.rotation=90;transformed.landscape=true;
        byte[] transformState=transformed.encode();int[] verticalFlip=new int[pixels.length];
        for(int y=0;y<HEIGHT;y++)System.arraycopy(pixels,(HEIGHT-1-y)*WIDTH,verticalFlip,y*WIDTH,WIDTH);
        StudioImageCodec.Result mirrored=StudioImageCodec.prepareEdited(source,transformed,"A1");
        try{equal(pixels(mirrored.originalPreview),verticalFlip,"Mirror plus landscape rotation changed geometry");verify(mirrored.prepared,InkSightQuantizer.quantize(verticalFlip,WIDTH,HEIGHT,true),PanelType.A1);}
        finally{recycle(mirrored);}
        check(Arrays.equals(transformState,transformed.encode()),"Transform modified requested state");
        equal(pixels(source),pixels,"Transform modified immutable original");

        // Independent clockwise logical-landscape-to-physical-portrait coordinate oracle.
        int[] wide=new int[WIDTH*HEIGHT];for(int y=0;y<WIDTH;y++)for(int x=0;x<HEIGHT;x++)wide[y*HEIGHT+x]=0xff000000|((x&255)<<16)|((y&255)<<8)|((x*3+y*7)&255);
        Bitmap landscape=Bitmap.createBitmap(wide,HEIGHT,WIDTH,Bitmap.Config.ARGB_8888);
        int[] rotated=new int[wide.length];for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++)rotated[y*WIDTH+x]=wide[(WIDTH-1-x)*HEIGHT+y];
        edit.landscape=true;
        StudioImageCodec.Result horizontal=StudioImageCodec.prepareEdited(landscape,edit,"A0");
        equal(pixels(horizontal.originalPreview),rotated,"Horizontal composition orientation");
        verify(horizontal.prepared,InkSightQuantizer.quantize(rotated,WIDTH,HEIGHT,true),PanelType.A0);
        recycle(horizontal);landscape.recycle();

        // Decoration pixels must enter enhancement once, before the exact four-color projection.
        Bitmap white=Bitmap.createBitmap(37,29,Bitmap.Config.ARGB_8888);white.eraseColor(0xffffffff);
        PhotoEdit decorated=new PhotoEdit();decorated.frameStyle=4;decorated.frameColor=InkSightQuantizer.COLORS[3];
        PhotoEdit.TextLayer text=new PhotoEdit.TextLayer();text.text="四 色 InkSight";text.color=0xff000000;text.background=InkSightQuantizer.COLORS[2];text.outline=true;text.rotation=15;decorated.texts.add(text);
        pristine=decorated.encode();StudioImageCodec.Result composed=StudioImageCodec.prepareEdited(white,decorated,"A1");
        int[] composedPixels=pixels(composed.originalPreview);int red=0,black=0;
        for(int color:composedPixels){if(color==InkSightQuantizer.COLORS[3])red++;if(color==0xff000000)black++;}
        check(red>WIDTH*10&&black>10,"Frame or text missing from exported composition");
        verify(composed.prepared,InkSightQuantizer.quantize(composedPixels,WIDTH,HEIGHT,true),PanelType.A1);
        check(Arrays.equals(pristine,decorated.encode()),"Renderer changed decoration parameters");
        recycle(composed);white.recycle();
        decorationCases();
        invalid(()->StudioImageCodec.prepareEdited(null,new PhotoEdit(),"A1"));
        invalid(()->StudioImageCodec.prepareEdited(source,null,"A1"));
        source.recycle();invalid(()->StudioImageCodec.prepareEdited(source,new PhotoEdit(),"A1"));
    }
    /** Exercise every frame and standalone text with an explicitly immutable source. */
    private static void decorationCases() throws Exception {
        int[] whitePixels=new int[37*29];Arrays.fill(whitePixels,0xffffffff);
        Bitmap source=Bitmap.createBitmap(whitePixels,37,29,Bitmap.Config.ARGB_8888);
        check(!source.isMutable(),"Decoration fixture must be immutable");
        try {
            for(boolean landscape:new boolean[]{false,true})for(int style=0;style<PhotoDecorations.FRAMES.length;style++) {
                PhotoEdit state=new PhotoEdit();state.landscape=landscape;state.mirror=landscape;state.rotation=landscape?90:0;
                state.frameStyle=style;state.frameColor=InkSightQuantizer.COLORS[3];
                if(style==0){PhotoEdit.TextLayer layer=new PhotoEdit.TextLayer();layer.text="文字独立测试\nText only";layer.color=0xff000000;layer.outline=true;layer.background=InkSightQuantizer.COLORS[2];state.texts.add(layer);}
                byte[] before=state.encode();String panel=landscape?"A0":"A1";
                StudioImageCodec.Result result=StudioImageCodec.prepareEdited(source,state,panel);
                try {
                    int[] artwork=pixels(result.originalPreview);int ink=0;
                    for(int color:artwork)if(color!=0xffffffff)ink++;
                    check(ink>20,"Missing frame/text in "+PhotoDecorations.FRAMES[style]+" landscape="+landscape);
                    verify(result.prepared,InkSightQuantizer.quantize(artwork,WIDTH,HEIGHT,true),PanelType.parse(panel));
                    check(result.preview!=source&&result.originalPreview!=source&&result.preview!=result.originalPreview,"Result aliases caller's original or comparison snapshot");
                    check(Arrays.equals(before,state.encode()),"Decoration modified caller's editing state");
                } finally {recycle(result);}
                check(!source.isRecycled(),"Decoration recycled caller's original");equal(pixels(source),whitePixels,"Decoration altered immutable original");
            }
        } finally {source.recycle();}
    }
    private static void indexedFactory() {
        byte[] codes=new byte[WIDTH*HEIGHT];for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++)codes[y*WIDTH+x]=(byte)((x/13+y/11)&3);
        byte[] pristine=codes.clone();
        for(PanelType type:PanelType.values()) {
            int[] palette=InkSightQuantizer.COLORS.clone();
            PanelImage.Prepared image=PanelImage.fromIndexed(codes,type.name(),palette,"reference");
            palette[2]=0xff00ff00;verify(image,codes,type);
            check(image.palette[2]==InkSightQuantizer.COLORS[2],"Prepared palette aliases caller input");
            image.preview.recycle();
            PanelImage.Prepared legacy=PanelImage.fromIndexed(codes,type.name());
            equal(pixels(legacy.preview),type.preview(legacy.packed),"Default indexed factory changed legacy palette");legacy.preview.recycle();
        }
        check(Arrays.equals(codes,pristine),"Indexed factory changed input codes");
        invalid(()->PanelImage.fromIndexed(null,"A1"));invalid(()->PanelImage.fromIndexed(new byte[3],"A1"));
        invalid(()->PanelImage.fromIndexed(codes,"A2"));invalid(()->PanelImage.fromIndexed(codes,null));
        byte[] bad=codes.clone();bad[127]=4;invalid(()->PanelImage.fromIndexed(bad,"A1"));bad[127]=-1;invalid(()->PanelImage.fromIndexed(bad,"A1"));
        invalid(()->PanelImage.fromIndexed(codes,"A1",new int[3],""));
        invalid(()->PanelImage.fromIndexed(codes,"A1",new int[]{0,0xffffffff,0xffff0000,0xffffff00},""));
    }
    private static void verify(PanelImage.Prepared prepared,byte[] codes,PanelType type) {
        check(Arrays.equals(prepared.packed,type.pack(codes)),"Photo pipeline changed existing "+type+" packing");
        int[] actual=pixels(prepared.preview);
        check(prepared.preview.getWidth()==WIDTH&&prepared.preview.getHeight()==HEIGHT,"Preview shape");
        check(Arrays.equals(prepared.palette,InkSightQuantizer.COLORS),"Warm reference palette missing");
        for(int i=0;i<actual.length;i++)check(actual[i]==prepared.palette[codes[i]],"Preview/payload palette index mismatch at "+i);
    }
    private static int[] pixels(Bitmap image){int[] out=new int[image.getWidth()*image.getHeight()];image.getPixels(out,0,image.getWidth(),0,0,image.getWidth(),image.getHeight());return out;}
    private static void recycle(StudioImageCodec.Result result){result.preview.recycle();result.originalPreview.recycle();}
    private static void equal(int[] a,int[] b,String label){check(Arrays.equals(a,b),label);}
    private static void invalid(Runnable action){try{action.run();throw new AssertionError("Invalid input accepted");}catch(IllegalArgumentException expected){}}
    private static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);}
    private StudioImageNativeChecks(){}
}
