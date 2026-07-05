package controller;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;

public class AudioManager {
    private static Clip clip;
    private static FloatControl volumeControl;
    private static int currentVolume = 50;
    private static boolean isAudioAvailable = false;

    public static void playMusic(String resourcePath) {
        // اگر قبلاً صدایی در حال پخش بوده، آن را متوقف و آزادسازی می‌کنیم
        stopMusic();

        String cleanPath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;

        try {
            InputStream audioSrc = AudioManager.class.getClassLoader().getResourceAsStream(cleanPath);

            if (audioSrc == null) {
                System.err.println("⚠️ [Audio Fallback] Music file '" + resourcePath + "' not found. Running game in mute mode.");
                isAudioAvailable = false;
                return;
            }

            // استفاده از Try-with-Resources برای جلوگیری از نشت حافظه (Memory Leak)
            try (InputStream bufferedIn = new BufferedInputStream(audioSrc);
                 AudioInputStream audioInput = AudioSystem.getAudioInputStream(bufferedIn)) {

                clip = AudioSystem.getClip();
                clip.open(audioInput);

                volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                isAudioAvailable = true;

                setVolume(currentVolume);

                clip.loop(Clip.LOOP_CONTINUOUSLY);
                clip.start();
            }

        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException | IllegalArgumentException e) {
            // مکانیزم Fallback ایمن: جلوگیری از کرش کردن بازی در سیستم‌های بدون درایور صوتی
            System.err.println("⚠️ [Audio Fallback] Could not initialize audio system: " + e.getMessage() + ". Running game silently.");
            clip = null;
            volumeControl = null;
            isAudioAvailable = false;
        } catch (Throwable t) {
            // گرفتن هرگونه خطای سطح پایین JVM (مثل نبود سخت‌افزار صوتی)
            System.err.println("⚠️ [Audio Fallback] Critical audio hardware failure. Running game gracefully without audio.");
            clip = null;
            volumeControl = null;
            isAudioAvailable = false;
        }
    }

    public static void setVolume(int volumePercent) {
        currentVolume = volumePercent;
        if (isAudioAvailable && volumeControl != null) {
            float min = volumeControl.getMinimum();
            float max = volumeControl.getMaximum();
            float range = max - min;
            float gain = (range * volumePercent / 100f) + min;
            volumeControl.setValue(gain);
        }
    }

    public static int getCurrentVolume() {
        return currentVolume;
    }

    public static void stopMusic() {
        if (clip != null) {
            if (clip.isRunning()) {
                clip.stop();
            }
            clip.close();
            clip = null;
            volumeControl = null;
            isAudioAvailable = false;
        }
    }

    public static boolean isAudioSystemWorking() {
        return isAudioAvailable;
    }
}