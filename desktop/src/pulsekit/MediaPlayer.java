package pulsekit;

/** What the playlist viewer asks of a player: VLC for videos, the built-in SoundClip for WAV and MP3. */
interface MediaPlayer {
    void play();

    void pause();

    void stop();

    boolean playing();

    /** VlcPlayer.ENDED at the end. */
    int state();

    long timeMs();

    long lengthMs();

    /** 0..100. */
    void volume(int percent);

    void rate(double speed);

    void mute(boolean on);

    void release();
}
