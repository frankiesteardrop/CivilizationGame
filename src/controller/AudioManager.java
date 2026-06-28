package controller;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;

public class AudioManager {
    private static Clip clip;
    private static FloatControl volumeControl;

    // متد پخش آهنگ با استاندارد Classpath
    public static void playMusic(String resourcePath) {
        try {
            // خواندن فایل از پوشه resources به صورت Stream
            InputStream audioSrc = AudioManager.class.getResourceAsStream(resourcePath);
            if (audioSrc != null) {
                InputStream bufferedIn = new BufferedInputStream(audioSrc);
                AudioInputStream audioInput = AudioSystem.getAudioInputStream(bufferedIn);
                clip = AudioSystem.getClip();
                clip.open(audioInput);

                // دریافت کنترلر صدا
                volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);

                // پخش به صورت تکرار شونده (Loop)
                clip.loop(Clip.LOOP_CONTINUOUSLY);
                clip.start();
            } else {
                System.err.println("Error: Music file not found in resources: " + resourcePath);
            }
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.err.println("Error playing music: " + e.getMessage());
        }
    }

    // متد تنظیم صدا که به اسلایدر MainMenuPanel متصل است
    public static void setVolume(int volumePercent) {
        if (volumeControl != null) {
            float min = volumeControl.getMinimum();
            float max = volumeControl.getMaximum();
            float range = max - min;
            float gain = (range * volumePercent / 100f) + min;
            volumeControl.setValue(gain);
        }
    }

    // متد توقف آهنگ
    public static void stopMusic() {
        if (clip != null && clip.isRunning()) {
            clip.stop();
        }
    }
}