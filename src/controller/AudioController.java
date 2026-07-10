package controller;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;

public class AudioController {
    private Clip clip;
    private FloatControl volumeControl;
    private int currentVolume = 50;
    private boolean isAudioAvailable = false;

    public void playMusic(String resourcePath) {
        stopMusic();
        String cleanPath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;

        try {
            InputStream audioSrc = AudioController.class.getClassLoader().getResourceAsStream(cleanPath);

            if (audioSrc == null) {
                System.err.println("⚠️ [Audio Fallback] Music file '" + resourcePath + "' not found. Running game in mute mode.");
                isAudioAvailable = false;
                return;
            }

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
            System.err.println("⚠️ [Audio Fallback] Could not initialize audio system: " + e.getMessage() + ". Running game silently.");
            clip = null;
            volumeControl = null;
            isAudioAvailable = false;
        } catch (Throwable t) {
            System.err.println("⚠️ [Audio Fallback] Critical audio hardware failure. Running game gracefully without audio.");
            clip = null;
            volumeControl = null;
            isAudioAvailable = false;
        }
    }

    public void setVolume(int volumePercent) {
        currentVolume = volumePercent;
        if (isAudioAvailable && volumeControl != null) {
            float min = volumeControl.getMinimum();
            float max = volumeControl.getMaximum();
            float range = max - min;
            float gain = (range * volumePercent / 100f) + min;
            volumeControl.setValue(gain);
        }
    }

    public int getCurrentVolume() {
        return currentVolume;
    }

    public void stopMusic() {
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

    public boolean isAudioSystemWorking() {
        return isAudioAvailable;
    }
}