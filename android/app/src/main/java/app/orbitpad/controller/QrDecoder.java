package app.orbitpad.controller;

import android.graphics.Bitmap;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.util.Collections;
import java.util.EnumMap;

/** Offline QR decoder shared by the camera and image import. */
public final class QrDecoder {
    private static String decode(LuminanceSource source) {
        MultiFormatReader reader=new MultiFormatReader();
        EnumMap<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS,Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER,Boolean.TRUE);reader.setHints(hints);
        try{return reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(source))).getText();}
        catch(ReaderException first){try{return reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(source.invert()))).getText();}catch(ReaderException ignored){return null;}}
        finally{reader.reset();}
    }
    static String decodeYuv(byte[] bytes,int width,int height) {
        int step=Math.max(1,width/720),w=width/step,h=height/step;
        byte[] luminance=new byte[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)luminance[y*w+x]=bytes[y*step*width+x*step];
        return decode(new PlanarYUVLuminanceSource(luminance,w,h,0,0,w,h,false));
    }
    public static String decodeBitmap(Bitmap bitmap) {
        int width=bitmap.getWidth(),height=bitmap.getHeight();int[] pixels=new int[width*height];bitmap.getPixels(pixels,0,width,0,0,width,height);
        return decode(new RGBLuminanceSource(width,height,pixels));
    }
    private QrDecoder(){}
}
