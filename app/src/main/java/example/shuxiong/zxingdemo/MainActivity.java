package example.shuxiong.zxingdemo;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Reader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;

public class MainActivity extends Activity {

    private MultiFormatReader multiFormatReader;
    private final int REQUEST_ALBUM_OK = 0;
    private ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Vector<BarcodeFormat> QR_CODE_FORMATS = new Vector<BarcodeFormat>(1);
        QR_CODE_FORMATS.add(BarcodeFormat.QR_CODE);

        Vector<BarcodeFormat> decodeFormats = new Vector<BarcodeFormat>();
        decodeFormats.addAll(QR_CODE_FORMATS);

        Hashtable<DecodeHintType, Object> hints = new Hashtable<DecodeHintType, Object>(3);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
//        hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, resultPointCallback);
        multiFormatReader = new MultiFormatReader();
        multiFormatReader.setHints(hints);

        Button button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent albumIntent = new Intent(Intent.ACTION_PICK, null);
                albumIntent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
                startActivityForResult(albumIntent, REQUEST_ALBUM_OK);
            }
        });

        imageView = findViewById(R.id.imageView);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_ALBUM_OK:
                ContentResolver resolver = getContentResolver();

                try {
                    InputStream inputStream = resolver.openInputStream(data.getData());

                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                    imageView.setImageBitmap(bitmap);

                    decode(bitmap);

                } catch (Exception e) {
                    Log.e("[xxx]", "choose error " + e);
                }
                break;
        }
    }

    private void decode(Bitmap bitmap) {

        while (true) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width < 100) {
                break;
            }
            if (decodeQRCode(bitmap)) {
                break;
            } else {
                byte[] data = getBitmapYUVBytes(bitmap);
                if (decodeYUVByZxing(data, width, height)) {
                    break;
                }
            }
            bitmap = scaleMatrixImage(bitmap, 0.5f, 0.5f);
        }

    }

    private boolean decodeQRCode(Bitmap bm) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        Log.i("[xxx]", "decode bitmap " + width + " " + height);
        int[] pixels = new int[width * height];
        bm.getPixels(pixels,0,width,0,0,width,height);

        BinaryBitmap bitmap  = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
        Result rawResult = null;
        try {
            rawResult = multiFormatReader.decodeWithState(bitmap);
        } catch (ReaderException re) {
            // continue
        } finally {
            multiFormatReader.reset();
        }

        if (rawResult != null) {
            Log.i("[xxx]", "decode suc " + rawResult.getText());
        } else {
            Log.i("[xxx]", "decode fail");
        }


        return rawResult != null;
    }

    public Bitmap scaleMatrixImage(Bitmap oldbitmap, float scaleWidth, float scaleHeight) {
    Matrix matrix = new Matrix();
    matrix.postScale(scaleWidth,scaleHeight);// 放大缩小比例
    Bitmap ScaleBitmap = Bitmap.createBitmap(oldbitmap, 0, 0, oldbitmap.getWidth(), oldbitmap.getHeight(), matrix, true);
    return ScaleBitmap;
}

    public static byte[] getBitmapYUVBytes(Bitmap sourceBmp) {
        if (null != sourceBmp) {
            int inputWidth = sourceBmp.getWidth();
            int inputHeight = sourceBmp.getHeight();
            int[] argb = new int[inputWidth * inputHeight];
            sourceBmp.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);
            byte[] yuv = new byte[inputWidth
                    * inputHeight
                    + ((inputWidth % 2 == 0 ? inputWidth : (inputWidth + 1)) * (inputHeight % 2 == 0 ? inputHeight
                    : (inputHeight + 1))) / 2];
            encodeYUV420SP(yuv, argb, inputWidth, inputHeight);
//            sourceBmp.recycle();
            return yuv;
        }
        return null;
    }

    /**
     * 将bitmap里得到的argb数据转成yuv420sp格式
     * 这个yuv420sp数据就可以直接传给MediaCodec, 通过AvcEncoder间接进行编码
     *
     * @param yuv420sp 用来存放yuv429sp数据
     * @param argb     传入argb数据
     * @param width    bmpWidth
     * @param height   bmpHeight
     */
    private static void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        // 帧图片的像素大小
        final int frameSize = width * height;
        // Y的index从0开始
        int yIndex = 0;
        // UV的index从frameSize开始
        int uvIndex = frameSize;
        // YUV数据, ARGB数据
        int Y, U, V, a, R, G, B;
        ;
        int argbIndex = 0;
        // ---循环所有像素点，RGB转YUV---
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                // a is not used obviously
                a = (argb[argbIndex] & 0xff000000) >> 24;
                R = (argb[argbIndex] & 0xff0000) >> 16;
                G = (argb[argbIndex] & 0xff00) >> 8;
                B = (argb[argbIndex] & 0xff);
                argbIndex++;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                Y = Math.max(0, Math.min(Y, 255));
                U = Math.max(0, Math.min(U, 255));
                V = Math.max(0, Math.min(V, 255));

                // NV21 has a plane of Y and interleaved planes of VU each
                // sampled by a factor of 2
                // meaning for every 4 Y pixels there are 1 V and 1 U. Note the
                // sampling is every other
                // pixel AND every other scanline.
                // ---Y---
                yuv420sp[yIndex++] = (byte) Y;
                // ---UV---
                if ((j % 2 == 0) && (i % 2 == 0)) {
                    yuv420sp[uvIndex++] = (byte) V;
                    yuv420sp[uvIndex++] = (byte) U;
                }
            }
        }
    }

    private static boolean decodeYUVByZxing(byte[] bmpYUVBytes, int bmpWidth, int bmpHeight) {
        // Both dimensions must be greater than 0
        Result result = null;
        if (null != bmpYUVBytes && bmpWidth > 0 && bmpHeight > 0) {
            try {
                PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(bmpYUVBytes, bmpWidth,
                        bmpHeight, 0, 0, bmpWidth, bmpHeight, true);
                BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
                Reader reader = new QRCodeReader();
                result = reader.decode(binaryBitmap);
            } catch (Exception e) {
                Log.w("[xxx]", "yuv decode error " + e);
                e.printStackTrace();
            }
        }
        if (result != null) {
            Log.i("[xxx]", "yuv decode suc " + result.getText());
        } else {
            Log.i("[xxx]", "yuv decode fail");
        }
        return result != null;
    }

}
