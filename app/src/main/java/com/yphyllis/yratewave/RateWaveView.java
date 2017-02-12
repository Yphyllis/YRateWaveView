package com.yphyllis.yratewave;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
 * Created by yphyllis on 16/5/13.
 */
public class RateWaveView extends View implements Runnable {

    public RateWaveView(Context context) {
        super(context);
    }

    public RateWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        getColors(attrs);
    }

    public RateWaveView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        getColors(attrs);
    }

    private static final float DEFAULT_BAR_HEIGHT_MIN = 4f;//最低波形高度，防止出现空的情况，单位 px
    private static final int DEFAULT_BAR_WIDTH = 4;//一个波形的默认宽度，单位 dp
    private static final int DEFAULT_BAR_DURATION = 200;//默认的绘制单个波峰所需要的时间，单位毫秒
    private static final int DEFAULT_ONDRAW_DURATION = 20;//默认的绘制一次的时间，单位毫秒
    private static final float DEFAULT_BAR_RATE = 0.7f;
    private static final int COLOR_BAR = Color.parseColor("#D3D3D1");
    private static final int COLOR_COVER_BAR = Color.parseColor("#3BA9FC");
    private static final int COLOR_BACKGROUND = Color.parseColor("#FFFFFF");

    private int barColor = COLOR_BAR;
    private int coverBarColor = COLOR_COVER_BAR;
    private int backgroundColor = COLOR_BACKGROUND;
    private int barDuration = DEFAULT_BAR_DURATION;//实际的绘制单个波峰所需要的时间
    private int barProgressCount = DEFAULT_BAR_DURATION / DEFAULT_ONDRAW_DURATION;//默认的绘制单个波峰所需要的次数

    //绘制未着色的波峰
    private Paint barPaint;
    //绘制着色波峰
    private Paint coverBarPaint;
    //绘制单个波峰平移过程中的未着色部分
    private Paint barProgressPaint;
    //绘制单个波峰平移过程中的着色部分
    private Paint coverBarProgressPaint;
    //波峰+间隔的 宽度
    private float barWidthFull;
    //波峰宽度
    private float barWidth;
    //波峰宽度的一半
    private float barWidthHalf;
    //每个波峰小进度的宽度
    private float barProgressWidth;
    //能绘制的波峰数
    private int barCount;
    //进度，每 DEFAULT_BAR_DURATION 毫秒+1
    private int barOffset = 0;
    //单个进度条的绘制进度，DEFAULT_BAR_PROGRESS 次完成一个进度
    private int barProgress = 0;

    private void getColors(AttributeSet attr) {
        TypedArray typedArray = getContext().obtainStyledAttributes(attr, R.styleable.RateWaveView);
        barColor = typedArray.getColor(R.styleable.RateWaveView_bar_color, COLOR_BAR);
        coverBarColor = typedArray.getColor(R.styleable.RateWaveView_cover_bar_color, COLOR_COVER_BAR);
        backgroundColor = typedArray.getColor(R.styleable.RateWaveView_background_color, COLOR_BACKGROUND);
        typedArray.recycle();
    }

    private void init() {

        if (barCount == 0 && getMeasuredWidth() != 0) {
            // 计算最大波峰数，保证为偶数，不考虑波峰数为奇数的情况
            final float scale = getContext().getResources().getDisplayMetrics().density;
            barWidth = DEFAULT_BAR_WIDTH * scale + 0.5f;
            barCount = (int) ((float) getMeasuredWidth() / barWidth);
            if (barCount % 2 != 0) {
                barCount++;
            }
            barWidthFull = (float) getMeasuredWidth() / barCount;
            barWidth = (float) getMeasuredWidth() / barCount * DEFAULT_BAR_RATE;
            barWidthHalf = barWidth / 2;
            setBarDuration(barDuration == 0 ? DEFAULT_BAR_DURATION : barDuration);
            setPaint(barWidth);
        }
    }

    private void setPaint(float barWidth) {
        barPaint = new Paint();
        barPaint.setAntiAlias(false);
        barPaint.setStyle(Paint.Style.FILL);
        barPaint.setStrokeWidth(barWidth);
        barPaint.setColor(barColor);

        coverBarPaint = new Paint();
        coverBarPaint.setAntiAlias(false);
        coverBarPaint.setStyle(Paint.Style.FILL);
        coverBarPaint.setStrokeWidth(barWidth);
        coverBarPaint.setColor(coverBarColor);

        barProgressPaint = new Paint();
        barProgressPaint.setAntiAlias(false);
        barProgressPaint.setStyle(Paint.Style.FILL);
        barProgressPaint.setColor(barColor);

        coverBarProgressPaint = new Paint();
        coverBarProgressPaint.setAntiAlias(false);
        coverBarProgressPaint.setStyle(Paint.Style.FILL);
        coverBarProgressPaint.setColor(coverBarColor);
    }

    public void setBarDurationDefault() {
        if (this.barDuration != DEFAULT_BAR_DURATION) {
            setBarDuration(DEFAULT_BAR_DURATION);
        }
    }

    public void setBarDuration(int barDuration) {
        if (barDuration <= 0) {
            throw new RuntimeException("barDuration must above 0");
        }
        this.barDuration = barDuration;
        this.barProgressCount = barDuration / DEFAULT_ONDRAW_DURATION;
        this.barProgressWidth = barWidth / barProgressCount;
    }

    private void startThread() {
        if (!isStarted) {
            isStarted = true;
            thread = new Thread(this);
            thread.start();
        }
    }

    public void start(String waveData) {
        this.waveData = waveData;
        isPaused = true;
    }

    public void pause() {
        isPaused = true;
    }

    public void continu() {
        isPaused = false;
    }

    public void stop() {
        if (isStarted) {
            isStarted = false;
            thread = null;
        }
        isPaused = true;
        barProgress = 0;
        barOffset = 0;
        invalidate();
    }

    public void release() {
        synchronized (lock) {
            if (isStarted) {
                isPaused = true;
                barProgress = 0;
                barOffset = 0;
            }
            isStarted = false;
            thread = null;
        }
    }

    private int lastProgress;

    public void progress(int progress) {
        startThread();
        if (progress > 0 && progress != this.lastProgress) {
            isPaused = false;
        } else {
            isPaused = true;
        }
        this.lastProgress = progress;
        // 用改变刷新间隔的方式 修正波形进度不同步的问题
        int barOffsetTemp = progress / barDuration;
        if (barOffsetTemp - barOffset > 1) {
            drawDuration = DEFAULT_ONDRAW_DURATION - 1;
        } else if (barOffset - barOffsetTemp > 1) {
            drawDuration = DEFAULT_ONDRAW_DURATION + 1;
        } else {
            drawDuration = DEFAULT_ONDRAW_DURATION;
        }

        this.limitCount = 0;
        this.limitOffset = 0;
    }


    private Thread thread;
    private boolean isStarted = false;//线程开始 flag
    private boolean isPaused = true;//初始状态
    private byte[] lock = new byte[0];
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            invalidate();
        }
    };

    public boolean isPaused() {
        return isPaused;
    }

    private int drawDuration = DEFAULT_ONDRAW_DURATION;

    @Override
    public void run() {
        while (isStarted) {
            try {
                draw();
                Thread.sleep(isPaused ? DEFAULT_BAR_DURATION : drawDuration);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void draw() {
        if (isPaused) {
            return;
        }
        if (!isPaused) {
            //在绘制前计算进度，可以解决页面不可见无法刷新UI的问题
            barProgress = (barProgress == barProgressCount - 1) ? (++barOffset - barOffset) : barProgress + 1;
        }
        handler.sendEmptyMessage(0);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        synchronized (lock) {
            getWave();
            init();
            if (data == null || data.length == 0) {
                canvas.drawColor(backgroundColor);//清空画布
                return;
            }

            int measuredHeight = getMeasuredHeight();
            int length = data.length;//波峰数
            int dataOffset = 0;//波峰值在 data 中的偏移量
            float progressOffset = 0;//波峰位置偏移宽度
            int coverIndexEnd = 0;//着色层在进度条中的位置

            //文艺写法
            boolean isNeedOffset = (length > barCount) && (barOffset >= barCount / 2) && (length - barOffset > barCount / 2);
            coverIndexEnd = (length <= barCount || barOffset < barCount / 2) ? barOffset
                    : (length - barOffset > barCount / 2 ? barCount / 2 : barCount - length + barOffset);
            dataOffset = (length <= barCount || barOffset < barCount / 2) ? 0
                    : (length - barOffset > barCount / 2 ? barOffset - barCount / 2 : length - barCount);
            progressOffset = isNeedOffset ? (barWidthFull / barProgressCount * barProgress) : 0;
            int size = isNeedOffset ? barCount + 1 : length > barCount ? barCount : length;
            for (int i = 0; i < size; i++) {
                if (i == coverIndexEnd && barProgress != 0) {
                    float coverWidth = barProgressWidth * barProgress;
                    float uncoverWidth = barWidth - coverWidth;
                    float coverX = i * barWidthFull + coverWidth / 2 - progressOffset;
                    float lineX = i * barWidthFull + coverWidth + uncoverWidth / 2 - progressOffset;
                    float Y = measuredHeight * (data[dataOffset + i]);
                    Y = Y < DEFAULT_BAR_HEIGHT_MIN ? DEFAULT_BAR_HEIGHT_MIN : Y;
                    coverBarProgressPaint.setStrokeWidth(coverWidth);
                    barProgressPaint.setStrokeWidth(uncoverWidth);
                    canvas.drawLine(coverX, measuredHeight, coverX, measuredHeight - Y, coverBarProgressPaint);
                    canvas.drawLine(lineX, measuredHeight, lineX, measuredHeight - Y, barProgressPaint);
                } else {
                    float X = i * barWidthFull + barWidthHalf - progressOffset;
                    float Y = measuredHeight * (data[dataOffset + i]);
                    Y = Y < DEFAULT_BAR_HEIGHT_MIN ? DEFAULT_BAR_HEIGHT_MIN : Y;
                    canvas.drawLine(X, measuredHeight, X, measuredHeight - Y,
                            i < coverIndexEnd ? coverBarPaint : barPaint);
                }
            }
        }
    }

    private String waveData;
    private int limitCount = 0;
    private int limitOffset = 0;
    private float[] data;

    public void setData(float[] data) {
        synchronized (lock) {
            this.data = data;
            barOffset = 0;
            barProgress = 0;
        }
    }

    public void setDataLimit(String waveData) {
        this.waveData = waveData;
        isPaused = true;
        barOffset = 0;
        barProgress = 0;
        limitCount = barCount;
        invalidate();
    }

    public void setDataAndProgress(String waveData, int progress) {
        this.waveData = waveData;
        isPaused = true;
        if (barOffset == 0) {
            barOffset = progress / barDuration;
            barProgress = progress % barDuration / DEFAULT_ONDRAW_DURATION;
            limitOffset = barOffset > barCount ? barCount : barOffset; //防止进度太长，波形数据为空
            limitCount = barCount;
            invalidate();
        }
    }

    private float[] getWave(String waveData, int limit, int limitOffset) {
        if (TextUtils.isEmpty(waveData)) {
            return null;
        }
        String str[] = null;
        str = waveData.split(",");
        float[] waves = null;

        if (str.length == 0) {
            waves = new float[1];
            waves[0] = 0f;
            return waves;
        } else {
            waves = new float[str.length];
            int size = limit != 0 && (limit + limitOffset) < str.length ? (limit + limitOffset) : str.length;
            for (int i = 0; i < size; i++) {
                try {
                    waves[i] = Float.parseFloat(str[i]);
                } catch (NumberFormatException e) {
                    waves[i] = 0f;
                }
            }
            return waves;
        }
    }

    private void getWave() {
        if (!TextUtils.isEmpty(waveData)) {
            log("getWave limitCount=" + limitCount);
            this.data = getWave(waveData, limitCount, limitOffset);
            if (limitCount == 0) {
                waveData = null;
            }
            limitCount = 0;
            limitOffset = 0;
        }
    }

    private void log(String msg) {
        if (isStarted) {
            Log.e("MBWaveView", msg);
        }
    }

    //        普通写法
//    if (length > barCount) {
//        // 时长大于能容纳的波峰
//        if (barOffset >= barCount / 2) {
//            if ( length - barOffset > barCount / 2 ) {
//                // 需要计算偏移量
//                dataOffset = barOffset - barCount / 2;
//                progressOffset = barWidthFull / barProgressCount * barProgress;
//                coverIndexEnd = barCount / 2;
//                for(int i = 0; i < barCount + 1; i++) {
//                    if (i < coverIndexEnd) {
//                        canvas.drawLine(i * barWidthFull + barWidthHalf - progressOffset, measuredHeight, i * barWidthFull + barWidthHalf - progressOffset, measuredHeight - (int) (measuredHeight * (data[dataOffset + i])), coverBarPaint);
//                    }
//                    else if (i == coverIndexEnd && barProgress != 0) {
//                        float coverWidth = barProgressWidth * barProgress;
//                        float lineWidth = barWidth - coverWidth;
//                        coverBarProgressPaint.setStrokeWidth(coverWidth);
//                        barProgressPaint.setStrokeWidth(lineWidth);
//                        canvas.drawLine(i * barWidthFull + coverWidth / 2 - progressOffset, measuredHeight, i * barWidthFull + coverWidth / 2 - progressOffset, measuredHeight - (int) (measuredHeight * (data[dataOffset + i])), coverBarProgressPaint);
//                        canvas.drawLine(i * barWidthFull + coverWidth + lineWidth / 2 - progressOffset, measuredHeight, i * barWidthFull + coverWidth + lineWidth / 2 - progressOffset, measuredHeight - (int) (measuredHeight * (data[dataOffset + i])), barProgressPaint);
//                    }
//                    else  {
//                        canvas.drawLine(i * barWidthFull + barWidthHalf - progressOffset, measuredHeight, i * barWidthFull + barWidthHalf - progressOffset, measuredHeight - (int) (measuredHeight * (data[dataOffset + i])), barPaint);
//                    }
//                }
//
//            }else {
//                //  剩余进度不足波峰数一半，停止平移。不需计算偏移量
//                dataOffset = length - barCount;
//                coverIndexEnd = barCount - (length - barOffset);
//                for(int i=0; i<barCount; i++) {
//                    if (i < coverIndexEnd) {
//                        canvas.drawLine(i * barWidthFull + barWidthHalf, measuredHeight, i * barWidthFull + barWidthHalf, measuredHeight - (int) (measuredHeight * (data[dataOffset + i])), coverBarPaint);
//                    }
//                    else if (i == coverIndexEnd && barProgress != 0  ) {
//                        float coverWidth = barProgressWidth * barProgress;
//                        float lineWidth = barWidth - coverWidth;
//                        coverBarProgressPaint.setStrokeWidth(coverWidth);
//                        barProgressPaint.setStrokeWidth(lineWidth);
//                        canvas.drawLine(i * barWidthFull + coverWidth / 2, measuredHeight, i * barWidthFull + coverWidth / 2, measuredHeight - (int) (measuredHeight * (data[dataOffset + i])), coverBarProgressPaint);
//                        canvas.drawLine(i * barWidthFull + coverWidth + lineWidth / 2, measuredHeight, i * barWidthFull + coverWidth + lineWidth / 2, measuredHeight - (int) (measuredHeight * (data[dataOffset + i])), barProgressPaint);
//                    }
//                    else  {
//                        canvas.drawLine(i * barWidthFull + barWidthHalf, measuredHeight, i * barWidthFull + barWidthHalf, measuredHeight - (int) (measuredHeight * (data[dataOffset + i])), barPaint);
//                    }
//                }
//            }
//        }else {
//            //进度不到波峰数的一半，不需要计算偏移
//            coverIndexEnd = barOffset;
//            for(int i=0; i<barCount; i++) {
//                if (i < coverIndexEnd) {
//                    canvas.drawLine(i * barWidthFull + barWidthHalf, measuredHeight, i * barWidthFull + barWidthHalf, measuredHeight - (int) (measuredHeight * (data[dataOffset + i])), coverBarPaint);
//                }
//                else if (i == coverIndexEnd && barProgress != 0  ) {
//                    float coverWidth = barProgressWidth * barProgress;
//                    float lineWidth = barWidth - coverWidth;
//                    coverBarProgressPaint.setStrokeWidth(coverWidth);
//                    barProgressPaint.setStrokeWidth(lineWidth);
//                    canvas.drawLine(i * barWidthFull + coverWidth / 2, measuredHeight, i * barWidthFull + coverWidth / 2, measuredHeight - (int) (measuredHeight * (data[dataOffset + i])), coverBarProgressPaint);
//                    canvas.drawLine(i * barWidthFull + coverWidth + lineWidth / 2, measuredHeight, i * barWidthFull + coverWidth + lineWidth / 2, measuredHeight - (int) (measuredHeight * (data[dataOffset + i])), barProgressPaint);
//                }
//                else  {
//                    canvas.drawLine(i * barWidthFull + barWidthHalf, measuredHeight, i * barWidthFull + barWidthHalf, measuredHeight - (int) (measuredHeight * (data[dataOffset + i])), barPaint);
//                }
//            }
//        }
//    }else {
//        // 时长小于或等于能容纳的波峰
//        coverIndexEnd = barOffset;
//        for (int i=0; i<length; i++) {
//            if (i < coverIndexEnd) {
//                canvas.drawLine(i * barWidthFull + barWidthHalf, measuredHeight, i * barWidthFull + barWidthHalf, measuredHeight - (int) (measuredHeight * (data[dataOffset + i])), coverBarPaint);
//            }
//            else if (i == coverIndexEnd && barProgress != 0  ) {
//                float coverWidth = barProgressWidth * barProgress;
//                float lineWidth = barWidth - coverWidth;
//                coverBarProgressPaint.setStrokeWidth(coverWidth);
//                barProgressPaint.setStrokeWidth(lineWidth);
//                canvas.drawLine(i * barWidthFull + coverWidth / 2, measuredHeight, i * barWidthFull + coverWidth / 2, measuredHeight - (int) (measuredHeight * (data[dataOffset + i])), coverBarProgressPaint);
//                canvas.drawLine(i * barWidthFull + coverWidth + lineWidth / 2, measuredHeight, i * barWidthFull + coverWidth + lineWidth / 2, measuredHeight - (int) (measuredHeight * (data[dataOffset + i])), barProgressPaint);
//            }
//            else  {
//                canvas.drawLine(i * barWidthFull + barWidthHalf, measuredHeight, i * barWidthFull + barWidthHalf, measuredHeight - (int) (measuredHeight * (data[dataOffset + i])), barPaint);
//            }
//        }
//    }
//
//    if (!isPaused) {
//        if (barProgress == barProgressCount - 1) {
//            barProgress = 0;
//            barOffset++;
//        } else {
//            barProgress++;
//        }
//    }
}

