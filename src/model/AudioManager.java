package controller;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;

public class AudioManager {
    private static Clip clip;
    private static FloatControl volumeControl;
    private static int currentVolume = 50;

    public static void playMusic(String resourcePath) {
        try {
            // استفاده از ClassLoader برای دسترسی ایمن و قطعی به فایل‌ها در محیط Maven/JAR
            InputStream audioSrc = AudioManager.class.getClassLoader().getResourceAsStream(
                    // اگر مسیر با '/' شروع می‌شود، آن را حذف کن تا ClassLoader دچار اشتباه نشود
                    resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath
            );

            if (audioSrc != null) {
                InputStream bufferedIn = new BufferedInputStream(audioSrc);
                AudioInputStream audioInput = AudioSystem.getAudioInputStream(bufferedIn);
                clip = AudioSystem.getClip();
                clip.open(audioInput);

                volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                setVolume(currentVolume);

                clip.loop(Clip.LOOP_CONTINUOUSLY);
                clip.start();
            } else {
                System.err.println("Error: Music file '" + resourcePath + "' not found in classpath!");
            }
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.err.println("Error playing music: " + e.getMessage());
        }
    }

    public static void setVolume(int volumePercent) {
        currentVolume = volumePercent;
        if (volumeControl != null) {
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
        if (clip != null && clip.isRunning()) {
            clip.stop();
        }
    }
}