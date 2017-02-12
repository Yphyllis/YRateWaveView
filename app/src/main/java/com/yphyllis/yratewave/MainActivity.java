package com.yphyllis.yratewave;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaPlayer;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import java.io.IOException;

public class MainActivity extends AppCompatActivity implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnBufferingUpdateListener ,MediaPlayer.OnPreparedListener {

    RateWaveView rateWaveView;
    TextView tvTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initView();
    }

    private void initView() {
        tvTime = (TextView) findViewById(R.id.tv_time);
        tvTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                tvTime.setClickable(false);
                readyGo();
            }
        });

        rateWaveView = (RateWaveView) findViewById(R.id.waveView);
        rateWaveView.start(TestValue.WaveData);
        rateWaveView.setBarDuration((int) (TestValue.WaveRate * 1000));
    }

    private void readyGo() {
        initMp3();
        new Thread(runnable).start();
    }

    private void play() {
        rateWaveView.progress(10);
        setPlayTime(tvTime, 0, TestValue.WaveTime);
    }
    private void pause() {
        rateWaveView.pause();
    }
    private void continu() {
        rateWaveView.continu();
    }
    private void progress(final int progress, int max) {
        rateWaveView.progress(progress);
        tvTime.post(new Runnable() {
            @Override
            public void run() {
                setPlayTime(tvTime, progress/1000, TestValue.WaveTime);
            }
        });
    }
    private void complete() {
        isServiceRun = false;
        rateWaveView.stop();
        tvTime.post(new Runnable() {
            @Override
            public void run() {
                tvTime.setClickable(true);
                tvTime.setText("点击重新播放");
            }
        });
    }


    /////////////////////播放本地文件//////////////////////

    private AssetManager assetManager;
    private MediaPlayer mediaPlayer;
    public MediaPlayer getMediaPlayer() {
        if (assetManager == null) {
            assetManager = getResources().getAssets();
        }
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setOnBufferingUpdateListener(this);
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnCompletionListener(this);
        }
        return mediaPlayer;
    }

    private void initMp3() {
        getMediaPlayer();
        try {
            AssetFileDescriptor fileDescriptor = assetManager.openFd("matchbox.mp3");
            getMediaPlayer().setDataSource(fileDescriptor.getFileDescriptor(),fileDescriptor.getStartOffset(),
                    fileDescriptor.getLength());
            getMediaPlayer().prepare();
            getMediaPlayer().start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mediaPlayer, int i) {

    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        getMediaPlayer().reset();
        complete();
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        play();
    }

    public boolean isPlaying() {
        return isServiceRun && getMediaPlayer().isPlaying();
    }


    /////////////////获取播放进度//////////
    boolean isServiceRun = true;
    Runnable runnable = new Runnable() {

        @Override
        public void run() {
            while (isServiceRun) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (isPlaying() ) {
                    int position = getMediaPlayer().getCurrentPosition();
                    int duration = getMediaPlayer().getDuration();
                    if (position != 0 && duration != 0) {
                        if (isLoadingError) {
                            if (isLoadingError == !isLoadingError(position)) {
                                continu();
                            }
                        }else if((isLoadingError = isLoadingError(position))) {
                            pause();
                        }else {
                            progress(position, duration);
                        }
                    }
                }
            }
        }
        private int progressStopCount, lastProgress;
        private boolean isLoadingError;
        /**
         * 是否加载中断
         * @return
         */
        public boolean isLoadingError(int currentPosition) {
            if (currentPosition == lastProgress) {//两次加载的位置相同
                if (progressStopCount == 3) {//相同位置加载到的次数达到4次时，判定为加载中断
                    return true;
                }
                progressStopCount ++;//加载次数+1
                return false;
            }
            progressStopCount = 0;
            lastProgress = currentPosition;
            return false;
        }
    };


    /////////////
    /**
     * @param textView
     * @param progress 进度，单位秒，比如 10，需要转出成 00:10
     * @param time     时长，单位秒，比如 200，需要转换成 03:20
     */
    public static void setPlayTime(TextView textView, int progress, String time) {
        StringBuilder sb = new StringBuilder();

        if (progress == 0) {
            sb.append("00:00");
        } else {
            int minute = progress / 60;
            int second = progress % 60;
            sb.append("0");
            sb.append(minute);
            sb.append(":");
            if (second < 10) {
                sb.append("0");
                sb.append(second);
            } else {
                sb.append(second);
            }
        }
        sb.append("/");

        try {
            int t = Integer.parseInt(time);
            int minute = t / 60;
            int second = t % 60;
            sb.append("0");
            sb.append(minute);
            sb.append(":");
            if (second < 10) {
                sb.append("0");
                sb.append(second);
            } else {
                sb.append(second);
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
            sb.append(time);
        }

        textView.setText(sb.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isServiceRun = false;
        if(getMediaPlayer().isPlaying()) {
            getMediaPlayer().stop();
            getMediaPlayer().release();
        }
        rateWaveView.release();
    }
}
