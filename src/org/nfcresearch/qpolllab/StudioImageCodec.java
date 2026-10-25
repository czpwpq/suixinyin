/*
 * Photo preparation adapted from 3dhaoxinren/miaomo ImageCodec.prepareEdited
 * (PolyForm Noncommercial 1.0.0). InkSight: Copyright (c) 2026 datascale-ai,
 * MIT; Pillow-compatible fitting/enhancement: Pillow 10.1.0, HPND.
 * Modified to retain this app's existing A0/A1 encoder and NFC transport.
 * miaomo source commit: 499268b34637a5c9e5199ef06e2eda4f9789870a.
 * See third-party/miaomo/NOTICE and
 * third-party/miaomo/THIRD-PARTY-LICENSES.txt for attribution and license terms.
 */
package org.nfcresearch.qpolllab;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;

/** The reference photo pipeline only: edit composition, Lanczos fit, enhancement, then Atkinson. */
public final class StudioImageCodec {
    public static final class Result {
        public final Bitmap preview,originalPreview;
        public final PanelImage.Prepared prepared;
        private Result(PanelImage.Prepared prepared,Bitmap fitted){this.prepared=prepared;preview=prepared.preview;originalPreview=fitted;}
    }

    /** Takes ownership of the prepared preview and supplies an independent comparison image. */
    public static Result fromPrepared(PanelImage.Prepared prepared) {
        if(prepared==null||prepared.preview==null||prepared.preview.isRecycled()||prepared.preview.getWidth()!=PanelProtocol.WIDTH||prepared.preview.getHeight()!=PanelProtocol.HEIGHT)throw new IllegalArgumentException("无效预览快照");
        Bitmap original=prepared.preview.copy(Bitmap.Config.ARGB_8888,false);
        if(original==null)throw new IllegalStateException("无法建立预览快照");
        return new Result(prepared,original);
    }

    public static Result prepareEdited(Bitmap source,PhotoEdit requested,String panelType) {
        if(source==null||source.isRecycled()||source.getWidth()<1||source.getHeight()<1||requested==null)throw new IllegalArgumentException("无效图片或编辑参数");
        PhotoEdit edit=requested.copy();edit.normalize();
        Bitmap oriented=source;
        if(edit.rotation!=0||edit.mirror) {
            Matrix m=new Matrix();if(edit.mirror)m.postScale(-1,1);m.postRotate(edit.rotation);
            oriented=Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),m,false);
        }
        int[] logical;
        try {
            int width=oriented.getWidth(),height=oriented.getHeight();
            int[] original=new int[width*height];
            oriented.getPixels(original,0,width,0,0,width,height);
            logical=InkSightFitter.fit(original,width,height,edit.width(),edit.height(),edit.cover,edit.zoom,edit.offsetX,edit.offsetY);
        } finally {if(oriented!=source)oriented.recycle();}
        if(edit.frameStyle!=0||!edit.texts.isEmpty()) {
            // The int[] Bitmap factory returns an immutable bitmap, which Canvas rejects.
            // Draw only into our own mutable software canvas; never alter the caller's original.
            Bitmap composed=Bitmap.createBitmap(edit.width(),edit.height(),Bitmap.Config.ARGB_8888);
            try {
                composed.setPixels(logical,0,edit.width(),0,0,edit.width(),edit.height());
                PhotoDecorations.draw(new Canvas(composed),edit,edit.width(),edit.height(),-1,false);
                composed.getPixels(logical,0,edit.width(),0,0,edit.width(),edit.height());
            } finally {composed.recycle();}
        }
        int width=PanelProtocol.WIDTH,height=PanelProtocol.HEIGHT;
        int[] fitted=logical;
        if(edit.landscape) {
            fitted=new int[width*height];
            for(int y=0;y<width;y++)for(int x=0;x<height;x++)fitted[x*width+(width-1-y)]=logical[y*height+x];
        }
        byte[] codes=InkSightQuantizer.quantize(fitted,width,height,true);
        PanelImage.Prepared prepared=PanelImage.fromIndexed(codes,panelType,InkSightQuantizer.COLORS,
            "InkSight · Lanczos 裁切 · 自动增强 · Atkinson 抖动 · "+(edit.landscape?"横向构图":"纵向构图"));
        Bitmap original=Bitmap.createBitmap(fitted,width,height,Bitmap.Config.ARGB_8888);
        return new Result(prepared,original);
    }
    private StudioImageCodec(){}
}

