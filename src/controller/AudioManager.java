package controller;

import javax.sound.sampled.*;
import java.io.File;

public class AudioManager {
    private static Clip clip;
    private static FloatControl volumeControl;

    public static void playMusic(String filePath) {
        try {
            File musicFile = new File(filePath);
            if (musicFile.exists()) {
                AudioInputStream audioInput = AudioSystem.getAudioInputStream(musicFile);
                clip = AudioSystem.getClip();
                clip.open(audioInput);

                // دریافت کنترلر صدا
                volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);

                clip.loop(Clip.LOOP_CONTINUOUSLY); // پخش تکرارشونده
                clip.start();
            } else {
                System.out.println("No background music found. Please place a 'music.wav' file in the project root folder.");
            }
        } catch (Exception e) {
            System.out.println("Audio Error: " + e.getMessage());
        }
    }

    public static void setVolume(int percentage) {
        if (volumeControl != null) {
            if (percentage <= 0) {
                volumeControl.setValue(volumeControl.getMinimum()); // Mute
            } else {
                // تبدیل درصد خطی به لگاریتم دسی‌بل (استاندارد صوتی جاوا)
                float dB = (float) (Math.log(percentage / 100.0) / Math.log(10.0) * 20.0);
                volumeControl.setValue(dB);
            }
        }
    }
}