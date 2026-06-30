package controller;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;

public class AudioManager {
    private static Clip clip;
    private static FloatControl volumeControl;
    private static int currentVolume = 50; // حافظه برای ذخیره آخرین درجه صدا

    // متد پخش آهنگ با سیستم جستجوی ضدگلوله (Dual-Fallback)
    public static void playMusic(String resourcePath) {
        try {
            // تلاش اول: جستجو از طریق Classpath (استاندارد Maven)
            InputStream audioSrc = AudioManager.class.getResourceAsStream(resourcePath);

            // تلاش دوم (Fallback): اگر محیط توسعه پوشه resources را نشناخت، مستقیماً از فایل سیستم بخواند
            if (audioSrc == null) {
                java.io.File directFile = new java.io.File("src/main/resources" + resourcePath);
                if (!directFile.exists()) directFile = new java.io.File("resources" + resourcePath);
                if (!directFile.exists()) directFile = new java.io.File(resourcePath.replace("/", "")); // حالت قرارگیری در روت

                if (directFile.exists()) {
                    audioSrc = new java.io.FileInputStream(directFile);
                }
            }

            // اگر با هر دو روش فایل پیدا شد، پخش را شروع کن
            if (audioSrc != null) {
                InputStream bufferedIn = new java.io.BufferedInputStream(audioSrc);
                AudioInputStream audioInput = AudioSystem.getAudioInputStream(bufferedIn);
                clip = AudioSystem.getClip();
                clip.open(audioInput);

                volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);

                // اعمال آخرین ولوم ذخیره شده
                setVolume(currentVolume);

                clip.loop(Clip.LOOP_CONTINUOUSLY);
                clip.start();
            } else {
                System.err.println("Error: Music file not found anywhere! Check if music.wav actually exists.");
            }
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.err.println("Error playing music: " + e.getMessage());
        }
    }
    // متد تنظیم صدا با قابلیت ذخیره در حافظه
    public static void setVolume(int volumePercent) {
        currentVolume = volumePercent; // ذخیره وضعیت
        if (volumeControl != null) {
            float min = volumeControl.getMinimum();
            float max = volumeControl.getMaximum();
            float range = max - min;
            float gain = (range * volumePercent / 100f) + min;
            volumeControl.setValue(gain);
        }
    }

    // متد استعلام آخرین ولوم برای مقداردهی اولیه اسلایدر
    public static int getCurrentVolume() {
        return currentVolume;
    }

    public static void stopMusic() {
        if (clip != null && clip.isRunning()) {
            clip.stop();
        }
    }
}