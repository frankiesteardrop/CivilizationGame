package controller;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;

public class AudioManager {
    private static Clip clip;
    private static FloatControl volumeControl;
    private static int currentVolume = 50; // حافظه برای ذخیره آخرین درجه صدا

    // متد پخش آهنگ با استاندارد Classpath
    public static void playMusic(String resourcePath) {
        try {
            InputStream audioSrc = AudioManager.class.getResourceAsStream(resourcePath);
            if (audioSrc != null) {
                InputStream bufferedIn = new BufferedInputStream(audioSrc);
                AudioInputStream audioInput = AudioSystem.getAudioInputStream(bufferedIn);
                clip = AudioSystem.getClip();
                clip.open(audioInput);

                volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);

                // اعمال آخرین ولوم ذخیره شده بلافاصله پس از پخش
                setVolume(currentVolume);

                clip.loop(Clip.LOOP_CONTINUOUSLY);
                clip.start();
            } else {
                System.err.println("Error: Music file not found in resources: " + resourcePath);
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