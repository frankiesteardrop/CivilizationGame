package controller;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class AudioManager {
    private static Clip clip;
    private static FloatControl volumeControl;

    // متد پخش آهنگ
    public static void playMusic(String filePath) {
        try {
            File musicFile = new File(filePath);
            if (musicFile.exists()) {
                AudioInputStream audioInput = AudioSystem.getAudioInputStream(musicFile);
                clip = AudioSystem.getClip();
                clip.open(audioInput);

                // دریافت کنترلر صدا
                volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);

                // پخش به صورت تکرار شونده (Loop)
                clip.loop(Clip.LOOP_CONTINUOUSLY);
                clip.start();
            } else {
                System.out.println("Error: Music file not found at " + filePath);
            }
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    // متد تنظیم صدا که به اسلایدر MainMenuPanel متصل است
    public static void setVolume(int volumePercent) {
        if (volumeControl != null) {
            // تبدیل درصد (0 تا 100) به دسی‌بل برای جاوا
            float min = volumeControl.getMinimum();
            float max = volumeControl.getMaximum();
            // جلوگیری از افت شدید صدا در درصدهای پایین
            float range = max - min;
            float gain = (range * volumePercent / 100f) + min;
            volumeControl.setValue(gain);
        }
    }

    // متد توقف آهنگ (در صورت نیاز)
    public static void stopMusic() {
        if (clip != null && clip.isRunning()) {
            clip.stop();
        }
    }
}