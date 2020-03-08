package top.jasongzy.spectrograph;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.os.EnvironmentCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    // 拍照的requestCode
    private static final int CAMERA_REQUEST_CODE = 0x00000010;
    // 申请相机权限的requestCode
    private static final int PERMISSION_CAMERA_REQUEST_CODE = 0x00000012;
    private ImageView ivPhoto;
    private ImageView plot;
    private TextView finalResult;
    private FloatingActionButton fab;
    private FloatingActionButton fabPlot;

    /**
     * 用于保存拍照图片的uri
     */
    private Uri mCameraUri;

    /**
     * 用于保存图片的文件路径，Android 10以下使用图片路径访问图片
     */
    private String mCameraImagePath;

    /**
     * 是否是Android 10以上手机
     */
    private boolean isAndroidQ = Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q;

    private String savedMCameraImagePath = null;
    private boolean isAbsorbance = false;
    private double[] brightArray = null;
    private double[] savedBrightArray = null;
    private double[] AData = null;
    private Bitmap absorbancePlot = null;
    private Bitmap spectrumPlot = null;

    private void toastMessage(String text, boolean isLengthLong) {
        if (isLengthLong) {
            Toast.makeText(this, text, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //原始图片
        ivPhoto = findViewById(R.id.ivPhoto);
        ivPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCameraImagePath == null) {
                    toastMessage("找个苹果试试看吧！", false);
                } else {
                    toastMessage("图片路径：" + mCameraImagePath, true);
                }
            }
        });
        ivPhoto.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                toastMessage("长按相机按钮可以从相册选择图片哦", true);
                return true;
            }
        });


        // 绘制的曲线
        plot = findViewById(R.id.plot);
        plot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clickPlot();
            }
        });
        plot.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (mCameraImagePath != null) {
                    if (isAbsorbance) {
                        toastMessage("点击悬浮按钮可切换光谱/吸光度曲线", false);
                    } else {
                        toastMessage("点击可设置当前数据为「光源光谱」", false);
                    }
                }
                return true;
            }
        });


        // 相机按钮
        fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                savedMCameraImagePath = mCameraImagePath;
                checkPermissionAndCamera();
            }
        });
        fab.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                savedMCameraImagePath = mCameraImagePath;
                checkPermissionAndAlbum();
                return true;
            }
        });


        // 图像对比按钮
        fabPlot = findViewById(R.id.fabPlot);
        fabPlot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clickFabPlot();
            }
        });
        fabPlot.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (absorbancePlot == null) {
                    toastMessage("绘制吸光度曲线并测量苹果糖度", false);
                } else {
                    toastMessage("切换光谱/吸光度曲线", false);
                }
                return true;
            }
        });

        finalResult = findViewById(R.id.finalResult);
    }

    private void clickFabPlot() {
        if (absorbancePlot == null) {
            Snackbar.make(this.findViewById(android.R.id.content), "是否要绘制「当前光谱」相对于「光源光谱」的吸光度曲线？", Snackbar.LENGTH_LONG)
                    .setAction("是的", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (savedBrightArray.length == brightArray.length) {
                                //画吸光度曲线图
                                absorbancePlot = plotAbsorbance(brightArray, savedBrightArray);
                                plot.setImageBitmap(absorbancePlot);
                                isAbsorbance = true;
                                printResult();
                            } else {
                                toastMessage("图像像素不匹配！", true);
                            }
                        }
                    }).show();
        } else {
            //切换
            if (isAbsorbance) {
                plot.setImageBitmap(spectrumPlot);
                Toast.makeText(this, "光谱曲线", Toast.LENGTH_SHORT).show();
                isAbsorbance = false;
            } else {
                plot.setImageBitmap(absorbancePlot);
                Toast.makeText(this, "吸光度曲线", Toast.LENGTH_SHORT).show();
                isAbsorbance = true;
            }
        }
    }

    private void clickPlot() {
        if (mCameraImagePath != null) {
            if (isAbsorbance) {
                Toast.makeText(this, "点击悬浮按钮可切换光谱/吸光度曲线", Toast.LENGTH_SHORT).show();
            } else {
                Snackbar.make(this.findViewById(android.R.id.content), "是否设置当前数据为光源光谱？", Snackbar.LENGTH_LONG)
                        .setAction("是的", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                savedBrightArray = new double[brightArray.length];
                                System.arraycopy(brightArray, 0, savedBrightArray, 0, brightArray.length);
                                fabPlot.setVisibility(View.VISIBLE);
                                absorbancePlot = null;
                                toastMessage("已设置", false);
                            }
                        }).show();
            }
        }
    }

    /**
     * 获取图片亮度（灰度）值
     */
    public double[] getBright(Bitmap bm) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        int r, g, b;
        double[] brightArray = new double[width];
        for (int i = 0; i < width; i++) {
            int localTemp = bm.getPixel(i, height / 2);
            r = (localTemp | 0xff00ffff) >> 16 & 0x00ff;
            g = (localTemp | 0xffff00ff) >> 8 & 0x0000ff;
            b = (localTemp | 0xffffff00) & 0x0000ff;
            // 灰度值计算公式
            brightArray[i] = 0.299 * r + 0.587 * g + 0.114 * b;
        }
        return brightArray;
    }

    /**
     * 绘制光谱曲线
     */
    private Bitmap plotSpectrum(double[] brightArray) {
        int width = brightArray.length;
        int height = 256;
        Bitmap.Config mConfig = Bitmap.Config.ARGB_8888;
        Bitmap bm = Bitmap.createBitmap(width, height, mConfig);
        Canvas canvas = new Canvas(bm);
        //canvas.drawColor(Color.parseColor("#666666"));
        Paint paint = new Paint();
        //paint.setColor(Color.GRAY);
        paint.setARGB(255, 200, 15, 15);
        //画笔的风格，就是边框（绘制的是空心的）
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        //绘制路径
        Path path = new Path();
        //从哪个点开始绘制
        path.moveTo(0, (float) (height - brightArray[0]));
        for (int i = 1; i < width; i++) {
            //然后绘制到哪个点
            path.lineTo(i, (float) (height - brightArray[i]));
        }
        //按路径绘制
        canvas.drawPath(path, paint);
        return bm;
    }

    /**
     * 绘制吸光度曲线
     */
    private Bitmap plotAbsorbance(double[] brightArray, double[] savedBrightArray) {
        AData = new double[savedBrightArray.length];
        double dataMax = 0;
        for (int i = 0; i < savedBrightArray.length; i++) {
            //吸光度计算
            AData[i] = Math.log10((savedBrightArray[i] != 0 ? savedBrightArray[i] : savedBrightArray[i] + 0.1) / (brightArray[i] != 0 ? brightArray[i] : brightArray[i] + 0.1));
            if (dataMax < AData[i]) {
                dataMax = AData[i];
            }
        }
        int width = savedBrightArray.length;
        int height = (dataMax != 0) ? (int) (Math.ceil(dataMax * 100) + 20) : 350;
        Bitmap.Config mConfig = Bitmap.Config.ARGB_8888;
        Bitmap bm = Bitmap.createBitmap(width, height, mConfig);
        Canvas canvas = new Canvas(bm);
        //canvas.drawColor(Color.parseColor("#666666"));
        Paint paint = new Paint();
        paint.setColor(Color.GRAY);
        //paint.setARGB(255, 200, 15, 15);
        //画笔的风格，就是边框（绘制的是空心的）
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        //绘制路径
        Path path = new Path();
        //从哪个点开始绘制
        path.moveTo(0, (float) (height - 100 * (AData[0] > 0 ? AData[0] : 0)));
        for (int i = 1; i < width; i++) {
            //然后绘制到哪个点
            path.lineTo(i, (float) (height - 100 * (AData[i] > 0 ? AData[i] : 0)));
        }
        //按路径绘制
        canvas.drawPath(path, paint);
        return bm;
    }

    /**
     * 打印糖度检测结果
     */
    private void printResult() {
        Vibrator vibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);
        vibrator.vibrate(50);
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        vibrator.vibrate(50);
        // TODO
        finalResult.setText("检测结果如下\n\n");
        finalResult.append("苹果糖度：");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                if (isAndroidQ) {
                    // Android 10 使用图片uri加载
                    ivPhoto.setImageURI(mCameraUri);
                } else {
                    // 使用图片路径加载
                    try {
                        Cursor cursor = getContentResolver().query(data.getData(), null, null, null, null);
                        if (cursor != null && cursor.moveToFirst()) {
                            mCameraImagePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                        }
                    } catch (Exception e) {
                        //e.printStackTrace();
                    }
                    ivPhoto.setImageBitmap(BitmapFactory.decodeFile(mCameraImagePath));
                }
                finalResult.setText(null);
                // 开始图像处理
                brightArray = getBright(BitmapFactory.decodeFile(mCameraImagePath));
                spectrumPlot = plotSpectrum(brightArray);
                plot.setImageBitmap(spectrumPlot);
                isAbsorbance = false;
                absorbancePlot = null;
            } else {
                mCameraImagePath = savedMCameraImagePath;
                Toast.makeText(this, "取消", Toast.LENGTH_SHORT).show();
            }
        }
    }


    /**
     * 检查权限并拍照。
     * 调用相机前先检查权限。
     */
    private void checkPermissionAndCamera() {
        int hasCameraPermission = ContextCompat.checkSelfPermission(getApplication(),
                Manifest.permission.CAMERA);
        if (hasCameraPermission == PackageManager.PERMISSION_GRANTED) {
            //有权限，调起相机拍照。
            openCamera();
        } else {
            //没有权限，申请权限。
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    PERMISSION_CAMERA_REQUEST_CODE);
        }
    }

    /**
     * 检查权限并打开相册
     */
    private void checkPermissionAndAlbum() {
        int hasCameraPermission = ContextCompat.checkSelfPermission(getApplication(),
                Manifest.permission.READ_EXTERNAL_STORAGE);
        if (hasCameraPermission == PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, CAMERA_REQUEST_CODE);
        } else {
            //没有权限，申请权限。
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_CAMERA_REQUEST_CODE);
        }
    }

    /**
     * 处理权限申请的回调。
     *
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_CAMERA_REQUEST_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //允许权限，有调起相机拍照。
                openCamera();
            } else {
                //拒绝权限，弹出提示框。
                Toast.makeText(this, "权限申请被拒绝", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 调起相机拍照
     */
    private void openCamera() {
        Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // 判断是否有相机
        if (captureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            Uri photoUri = null;

            if (isAndroidQ) {
                // 适配android 10
                photoUri = createImageUri();
            } else {
                try {
                    photoFile = createImageFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (photoFile != null) {
                    mCameraImagePath = photoFile.getAbsolutePath();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        //适配Android 7.0文件权限，通过FileProvider创建一个content类型的Uri
                        photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
                    } else {
                        photoUri = Uri.fromFile(photoFile);
                    }
                }
            }

            mCameraUri = photoUri;
            if (photoUri != null) {
                captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                captureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                startActivityForResult(captureIntent, CAMERA_REQUEST_CODE);
            }
        }
    }

    /**
     * 创建图片地址uri,用于保存拍照后的照片 Android 10以后使用这种方法
     *
     * @return 图片的uri
     */
    private Uri createImageUri() {
        String status = Environment.getExternalStorageState();
        // 判断是否有SD卡,优先使用SD卡存储,当没有SD卡时使用手机存储
        if (status.equals(Environment.MEDIA_MOUNTED)) {
            return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new ContentValues());
        } else {
            return getContentResolver().insert(MediaStore.Images.Media.INTERNAL_CONTENT_URI, new ContentValues());
        }
    }

    /**
     * 创建保存图片的文件
     *
     * @return
     * @throws IOException
     */
    private File createImageFile() throws IOException {
        String imageName = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (!storageDir.exists()) {
            storageDir.mkdir();
        }
        File tempFile = new File(storageDir, imageName);
        if (!Environment.MEDIA_MOUNTED.equals(EnvironmentCompat.getStorageState(tempFile))) {
            return null;
        }
        return tempFile;
    }


    /**
     * toolbar按钮
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.clear:
                //删除app拍摄的照片
                RecursionDeleteFile(getExternalFilesDir(Environment.DIRECTORY_PICTURES));
                //布局还原
                ivPhoto.setImageDrawable(getResources().getDrawable(R.drawable.apple));
                plot.setImageDrawable(getResources().getDrawable(R.drawable.spectrum));
                fabPlot.setVisibility(View.INVISIBLE);
                finalResult.setText(null);
                //清除变量
                mCameraImagePath = null;
                savedMCameraImagePath = null;
                isAbsorbance = false;
                brightArray = null;
                savedBrightArray = null;
                AData = null;
                absorbancePlot = null;
                spectrumPlot = null;
                //Toast.makeText(this, "照片拍摄缓存已清除", Toast.LENGTH_SHORT).show();
                Snackbar.make(this.findViewById(android.R.id.content), "照片拍摄缓存已清除", Snackbar.LENGTH_LONG).show();
                break;
            case R.id.about:
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jasongzy")));
                break;
        }
        return true;
    }

    /**
     * 递归删除文件和文件夹
     *
     * @param file 要删除的根目录
     */
    public void RecursionDeleteFile(File file) {
        if (file.isFile()) {
            file.delete();
            return;
        }
        if (file.isDirectory()) {
            File[] childFile = file.listFiles();
            if (childFile == null || childFile.length == 0) {
                file.delete();
                return;
            }
            for (File f : childFile) {
                RecursionDeleteFile(f);
            }
            file.delete();
        }
    }
}
