package example.shuxiong.zxingdemo;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import static java.lang.Math.min;
import static org.opencv.core.CvType.CV_8UC3;

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
        hints.put(DecodeHintType.TRY_HARDER, true);
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

        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_11, this, null);
        }
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

//                    displayBitmap(bitmap);

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
//            if (width < 100) {
//                break;
//            }
            if (decodeBitmapUsingZXing(bitmap)) {
                break;
            }
            if (tryHarder(bitmap)) {
                break;
            }
//            bitmap = scaleMatrixImage(bitmap, 0.5f, 0.5f);
            break;
        }

    }

    private boolean decodeBitmapUsingZXing(Bitmap bm) {
        count ++;
        saveBitmap(bm, "" + count);
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


    public boolean tryHarder(Bitmap srcBitmap) {
        // scale
//        int height = srcBitmap.getHeight();
//        int width = srcBitmap.getWidth();
//        float rate = 2000.f / (height < width ? height : width);
//        srcBitmap = scaleMatrixImage(srcBitmap, rate, rate);

        Mat src = new Mat();
        Mat gray = new Mat();
        Utils.bitmapToMat(srcBitmap, src);
        if (src.channels() == 3 || src.channels() == 4) {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
        }

        // gaussianblur
        Mat blur = new Mat();
        if (src.width() > 500 && src.height() > 500) {
            Imgproc.GaussianBlur(gray, blur, new Size(5, 5), Imgproc.BORDER_CONSTANT);
        }

        Mat dst = new Mat();


        for (int thres = 270; thres > 100; thres -= 50) {
            int type = thres > 255 ? Imgproc.THRESH_OTSU : Imgproc.THRESH_BINARY;
            Imgproc.threshold(blur, dst, thres, 255, type);

            Bitmap dstBitmap = Bitmap.createBitmap(srcBitmap);
            Utils.matToBitmap(dst, dstBitmap);
            displayBitmap(dstBitmap);

            Log.i("[xxx]", "threshold: " + thres);
            if (decodeBitmapUsingZXing(dstBitmap)) {
                return true;
            }
            if (findSqureAndDecode(blur, gray)) {
                return true;
            }
            break;
        }

        if (true)
            return false;

        int delta = 27;
        int blockSize = 11;
        for (; delta >= 0; delta -=2) {
            Imgproc.adaptiveThreshold(blur, dst, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, blockSize, delta);

            Bitmap dstBitmap = Bitmap.createBitmap(srcBitmap);
            Utils.matToBitmap(dst, dstBitmap);
            displayBitmap(dstBitmap);

            Log.i("[xxx]", "adaptive threshold: " + delta);
            if (decodeBitmapUsingZXing(dstBitmap)) {
                return true;
            }
            if (findSqureAndDecode(dst, gray)) {
                return true;
            }
        }
        return false;
    }

    private void displayBitmap(Bitmap bitmap) {
        imageView.setImageBitmap(bitmap);
    }


    private void displayBitmap(Mat src) {
        Bitmap dstBitmap = Bitmap.createBitmap(src.width(), src.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(src, dstBitmap);
        displayBitmap(dstBitmap);
    }

    int count = 0;
    private boolean findSqureAndDecode(Mat src, Mat origin) {
        Mat out = new Mat();
        Imgproc.Canny(src, out, 50, 200);

//        Imgproc.GaussianBlur(out, out, new Size(5, 5), Imgproc.BORDER_CONSTANT);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(out, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_NONE, new Point(0, 0));

        Mat debug = Mat.zeros(out.size(), CV_8UC3);

        for (int i = 0; i < contours.size(); i ++ ) {
            if (!isSquare(contours.get(i))) {
                continue;
            }

            Rect rect = Imgproc.boundingRect(contours.get(i));
            Mat result = new Mat (origin, rect);

            int minLine = min(result.rows(), result.cols() );
            double scale = 256.f / minLine;
            Size size = new Size(result.cols() * scale, result.rows() * scale);
            Imgproc.resize(result, result, size);

//            Imgproc.drawContours(debug, contours, i, new Scalar(255, 0, 0), 2);

            Mat toCheck = result.clone();
            for (int thres = -1; thres < 255; thres += 50) {
                Bitmap dstBitmap = Bitmap.createBitmap(toCheck.width(), toCheck.height(), Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(toCheck, dstBitmap);
                if (decodeBitmapUsingZXing(dstBitmap)) {
                    displayBitmap(dstBitmap);
                    return true;
                }
                else {
                    int type = thres <= 0 ? Imgproc.THRESH_OTSU : Imgproc.THRESH_BINARY;
                    Imgproc.threshold(result, toCheck, thres, 255, type);
                }
            }
        }

        return false;
    }

    boolean isSquare(MatOfPoint points) {
        MatOfPoint2f curve = new MatOfPoint2f(points.toArray());
        MatOfPoint2f approxCurve = new MatOfPoint2f();
        Imgproc.approxPolyDP(curve, approxCurve, Imgproc.arcLength(new MatOfPoint2f(points.toArray()), true) * 0.25 * 0.3, true);
        MatOfPoint approx = new MatOfPoint(approxCurve.toArray());
        if (approxCurve.toArray().length == 4 && Imgproc.isContourConvex(approx)) {
            double area = Math.abs(Imgproc.contourArea(approx));
            if (area < 50 * 50) {
                return false;
            }
            double maxCosine = 0.0;
            for (int j = 2; j < 5; j ++) {
                double cosine = Math.abs(angle(approx.toArray()[j%4], approx.toArray()[j-2], approx.toArray()[j-1]));
                maxCosine = Math.max(maxCosine, cosine);
            }

            if (maxCosine < 0.1) {
                return true;
            }
        }
        return false;
    }

    private double angle( Point pt1, Point pt2, Point pt0 ) {
            double dx1 = pt1.x - pt0.x;
            double dy1 = pt1.y - pt0.y;
            double dx2 = pt2.x - pt0.x;
            double dy2 = pt2.y - pt0.y;
            return (dx1*dx2 + dy1*dy2)/Math.sqrt((dx1*dx1 + dy1*dy1)*(dx2*dx2 + dy2*dy2) + 1e-10);
    }

    private void saveBitmap(Bitmap bitmap, String name) {
        File PHOTO_DIR = new File("/sdcard/000test");//设置保存路径
        File avaterFile = new File(PHOTO_DIR, name + ".jpg");//设置文件名称
        try {
            avaterFile.createNewFile();
            FileOutputStream fos = new FileOutputStream(avaterFile);
             bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
             fos.flush();
             fos.close();
        } catch (Exception e) {
            Log.e("[xxx]", "" + e);
        }
    }

    private void saveMatImage(Mat src, String name) {
        Bitmap dstBitmap = Bitmap.createBitmap(src.width(), src.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(src, dstBitmap);
        saveBitmap(dstBitmap, name);

    }



}
