/*
 * Copyright (C) 2019 Eugen Rădulescu <synapticwebb@gmail.com> - All rights reserved.
 *
 * You may use, distribute and modify this code only under the conditions
 * stated in the Synaptic Call Recorder license. You should have received a copy of the
 * Synaptic Call Recorder license along with this file. If not, please write to <synapticwebb@gmail.com>.
 */

package net.synapticweb.callrecorder.recorder;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.preference.PreferenceManager;
import android.util.Log;

import net.synapticweb.callrecorder.CrApp;
import net.synapticweb.callrecorder.CrLog;
import net.synapticweb.callrecorder.settings.SettingsFragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION;

abstract class RecordingThread {
    protected static final String TAG = "CallRecorder";
    static final int SAMPLE_RATE = 44100;
    final int channels;
    final int bufferSize;
    final AudioRecord audioRecord;
    private final Recorder recorder;

    RecordingThread(String mode, Recorder recorder) throws RecordingException {
        channels = (mode.equals(Recorder.MONO) ? 1 : 2);
        this.recorder = recorder;
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, channels == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);
        audioRecord = createAudioRecord();
        audioRecord.startRecording();
    }

    private AudioRecord createAudioRecord() throws RecordingException {
        AudioRecord audioRecord;
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(CrApp.getInstance());
        int source = Integer.valueOf(settings.getString(SettingsFragment.SOURCE,
                String.valueOf(VOICE_RECOGNITION)));
            try {
                audioRecord = new AudioRecord(source, SAMPLE_RATE,
                        channels == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT, bufferSize * 10);
            } catch (Exception e) { //La VOICE_CALL dă IllegalArgumentException. Aplicația nu se oprește, rămîne
                //hanging, nu înregistrează nimic.
                throw new RecordingException(e.getMessage());
            }

        if(audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            CrLog.log(CrLog.DEBUG, "createAudioRecord(): Audio source chosen: " + source);
            recorder.setSource(audioRecord.getAudioSource());
        }

        if(audioRecord.getState() != AudioRecord.STATE_INITIALIZED)
            throw new RecordingException("Unable to initialize AudioRecord");

        return audioRecord;
    }

    void disposeAudioRecord() {
        audioRecord.stop();
        audioRecord.release();
    }
}
