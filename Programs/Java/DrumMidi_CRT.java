import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * DrumMidi for Java 8 and Android ART.
 * Detects kick, snare, closed hi-hat, open hi-hat, ride, crash, rack tom, mid tom, and floor tom.
 * GM drum channel 10: kick 36, snare 38, closed hat 42, open hat 46, ride 51, crash 49,
 * rack tom 50, mid tom 47, floor tom 41.
 *
 * One file. WAV needs nothing else, so `javac DrumMidi.java` works on Android.
 * The jar also bundles JLayer 1.0.1 for MP3. A lone .java file tells you to use the jar for MP3.
 *
 *   java -jar DrumMidi.jar song.wav drums.mid [--sens 1.0] [--hat 1.0] [--tom 1.0] [--ride 1.0] [--crash 1.0] [--bpm 120] [--quantize 16] [--no-hpss] [--no-cymbals] [--no-toms] [--snare-leak] [--faded-opens] [--log [logfile]]
 *
 * --log writes the input and output names, the settings and the hits found to a text file
 * (DrumMidi_CRT_log.txt when no name is given). The settings also go into the MIDI itself as a text
 * event ("pulsekit-drummidi; ..."), so CompareHits can suggest changes to them.
 *
 * --dir <folder> runs on every WAV in the folder (--recursive: its subfolders too), each written as
 * <wav>-dm.mid beside it with the same switches: a Midi2Wav or vst_render.py batch for CompareHits --batch.
 *
 * --zip puts the MIDI file(s) made (and the --log file) in a zip too: <output>.zip beside the MIDI, or
 * <folder>-dm.zip in --dir's folder. --zip_mb N (--zip implied) makes zips of at most N MB each
 * (<name>-part1of3.zip...; a file is never split). --zip_outdir <folder> writes the zips there instead
 * (made when missing), with % codes: after a %, dd, d, mm, m, yyyy, yy and n follow each other
 * (%ddmmyy is 071126; %n the first number from 1 whose folder is not there yet; %% is a %).
 *
 * --no-toms leaves toms out of the MIDI (they are still told apart from kicks and snares); CompareHits
 * then leaves them out of its comparison too.
 *
 * --faded-opens writes as closed the open hats whose ring fades like a closed hat's (heard alone, or
 * on a snare; see shortOpens). Many more closed hats right, some real open hats lost too (tested on
 * four kits: hats right +297 of 11361, open hats lost 126 of 1283); off unless asked.
 *
 * --snare-leak drops the hats a bright snare's wires make in the hat band: what the song's snare puts
 * over 10 kHz is learned from its snares without a hat, and a hat on a snare that rises no more than
 * that is the snare. Fewer false hats on the snare, some real ones lost too (tested: hat precision
 * 76% to 81-85%, recall 80-87% to 71-82%); off unless asked, as songs with few hat-free snares gain
 * nothing.
 *
 * --sens sets kick and snare, and every other part that is not given its own value.
 * --hat, --tom, --ride and --crash replace --sens for that part (they do not multiply it).
 *
 * Bass guitar shares the kick and tom bands, so a low-band onset alone is not a drum.
 * A kick needs a beater click (2-6 kHz) with it and a jump in the kick band's level (linear:
 * a hat leaking into the band rises out of near silence, which only looks big in log terms).
 * A snare needs its body (150-350 Hz) to jump more than the kick band, as a tone above the
 * 400-1000 Hz band (plain noise, as a synthesized hat, is flat), with wires (2-6 kHz) that ring
 * on after the hit. Under guitar and piano the body hardly shows, so there a noise onset whose
 * wires ring is a snare too. A tom jumps in the low-mid level without wires; over a bass line it
 * also needs a stick attack (1-4 kHz). Without a bass line, a "kick" well above the song's kick
 * pitch is a floor tom. Cutting the bass out with a filter would cut the kick too.
 *
 * Hats, rides and crashes ring when their band's quietest point 160-500 ms on stays up, so the
 * next hit does not make a hat look like it rings. A snare or kick leaks into the hat band, so a
 * hat struck with one is kept where it keeps the time of the hats around it.
 */
public class DrumMidi_CRT{

    static final int FFT_SIZE = 2048;
    static final int HOP = 512;
    static final double MAX_HZ = 14000;
    static final double LOG_GAMMA = 100;
    static final int HPSS_KERNEL = 17;

    static final double KICK_LO = 40, KICK_HI = 130;
    static final double SNARE_BODY_LO = 150, SNARE_BODY_HI = 350;
    static final double SNARE_NOISE_LO = 2000, SNARE_NOISE_HI = 6000;
    static final double HAT_LO = 6000, HAT_HI = 14000;
    static final double RIDE_LO = 3000, RIDE_HI = 7000;
    static final double FLOOR_LO = 70, FLOOR_HI = 120;
    static final double MID_LO = 130, MID_HI = 190;
    static final double RACK_LO = 200, RACK_HI = 320;

    static final double ATTACK_LO = 1000, ATTACK_HI = 4000;
    static final double KICK_CLICK = 0.5;
    static final double TOM_ATTACK_GATE = 0.5, TOM_ATTACK = 0.6, TOM_RISE = 0.5;
    /**
     * A tom raises its band's level (70-320 Hz, linear) by TOM_JUMP, and its stick noise dies at
     * once (wiresLeft under TOM_WIRES; a snare's wires ring on). Where the band was quiet before
     * (under QUIET_BEFORE of its loud level) no stick attack is asked for: synthesized toms have
     * none, and no bass note is there to mistake. A kick whose low pitch is TOM_OVER_KICK times the
     * song's kick pitch (and at least FLOOR_TOM_HZ), where no bass line plays, is a floor tom.
     */
    /**
     * A "tom" whose snare body band jumps TOM_SNARE_BODY or more is a snare whose wires do not ring
     * on (1.00-1.22; toms raise it 0.22-0.73).
     */
    static final double TOM_SNARE_BODY = 0.9;
    /**
     * A ghost note is the snare played softly: its body (120-450 Hz) peaks within GHOST_PITCH_LO-HI of
     * the song's loud snares' (ghosts 0.97-1.16, toms on sampled kits 0.59-0.89) and its spectrum
     * there matches theirs (GHOST_LIKE; toms 0.75-0.91). Only where GHOST_SNARES loud snares make the
     * song's snare (an 8-bar fill has a few, and a drum machine's toms are tuned to its snare).
     * Hydrogen's GMRockKit toms match its snare (0.91-0.96), but that kit is not what songs use.
     */
    static final double GHOST_PITCH_LO = 0.95, GHOST_PITCH_HI = 1.25, GHOST_LIKE = 0.9;
    static final int GHOST_SNARES = 16;
    /**
     * The softest ghost notes (a body rise under GHOST_JUMP on the song's scale): picked at
     * SOFT_GHOST_DELTA of the peak threshold, they need the kick band to stay under SOFT_GHOST_KICK,
     * their body to reach SOFT_GHOST_LEVEL of the loud snares' and to rise SOFT_GHOST_RISE times
     * over 3 frames, and the loud snares' pitch and spectrum (SOFT_GHOST_LIKE). Only for a dry snare:
     * where the loud snares still ring SOFT_GHOST_RING of their body RING_FRAMES (~175 ms) on (a
     * room, a plate, a sampled kit's snare buzzing with the other drums: 0.13-1.3; dry kits
     * 0.00-0.09), their ring matches the snare too.
     */
    static final double SOFT_GHOST_DELTA = 0.07, SOFT_GHOST_KICK = 0.1,
        SOFT_GHOST_LEVEL = 0.05, SOFT_GHOST_LIKE = 0.85, SOFT_GHOST_RISE = 2.0, SOFT_GHOST_RING = 0.1;
    static final int RING_FRAMES = 15;
    /**
     * A roll or a fast fill: a snare ROLL_FRAMES (~140 ms) or less from a snare found rises out of
     * that one's ring, so its wires seem not to ring on, and a fill has too few loud snares for the
     * ghost notes' check. Its body rising as a snare's does, with the next snare's spectrum (ROLL_LIKE),
     * it is one too. Not its pitch: over the last hit's ring the body's loudest partial jumps an octave.
     */
    static final int ROLL_FRAMES = 12;
    static final double ROLL_LIKE = 0.9;
    static final double GHOST_DELTA = 0.5;
    static final double TOM_JUMP = 0.3, TOM_WIRES = 0.15, QUIET_BEFORE = 0.25, TOM_OVER_KICK = 1.2;
    /** A pitch read 3 and 2 frames before a kick within this share was held from before (a guitar's note). */
    static final double PITCH_HELD = 0.04, HELD_SONG = 0.5, GUITAR_FLOOR = 0.3, TOM_RISE_PITCH = 1.7;
    static final int RISE_KICKS = 16;
    /**
     * In a fill (under RISE_KICKS kicks) many "kicks" are toms, and toms can read lower than the kick
     * as well as higher, so the song's kick is taken from the half of them with the most low end
     * (lowEnd at or over their median): the kicks. Signs of a tom: a pitch under FILL_TOM_UNDER or over
     * FILL_TOM_OVER of theirs (their medians), a new pitch FILL_TOM_RISE times theirs, a low end under
     * FILL_TOM_LOW of theirs (KVLT's and Riot's toms 0.2-0.4 of their kicks'). Each alone a real kick
     * shows now and then (Riot's reads low or an octave up, Anarchy's low end varies); a "kick" with
     * FILL_TOM_SIGNS of them is a tom.
     */
    static final double FILL_TOM_UNDER = 0.85, FILL_TOM_OVER = 1.4, FILL_TOM_RISE = 1.6, FILL_TOM_LOW = 0.4;
    static final int FILL_TOM_SIGNS = 2;
    /**
     * A "snare" whose body reads under SNARE_TOM_PITCH of the song's snares' (their median; the toms
     * of these kits read 0.75-0.93 of it, snares 0.98 and over), at SNARE_TOM_HZ or more and with the
     * kick band hardly rising (under SNARE_TOM_KICK: a kick under the snare lowers its reading), is a
     * tom. At least SNARE_TOM_SNARES snares make the song's.
     */
    static final double SNARE_TOM_PITCH = 0.93, SNARE_TOM_HZ = 120, SNARE_TOM_KICK = 0.3;
    static final int SNARE_TOM_SNARES = 3;
    /** A tom's stick click reaches the hat band: a hat within TOM_CLICK of a tom found is its click (0.07 real hats to 1). */
    static final double TOM_CLICK = 0.03;
    /**
     * A kick whose third partial rings louder than its fundamental reads OVERTONE_LO-OVERTONE_HI times
     * the song's kick pitch (3.06-3.18 seen) and keeps its low end (under 70 Hz, OVERTONE_SUB of the
     * level at its pitch or more: 0.77-1.22); toms there read 1.3-2.2 times, or 3.39 with 0.32-0.55.
     */
    static final double OVERTONE_LO = 2.8, OVERTONE_HI = 3.2, OVERTONE_SUB = 0.6;
    /**
     * A kick sound repeats: a pitch read by KICK_SHARE of the song's kicks (KICK_SHARE_MIN at least),
     * within KICK_SAME of each other, with its low end there (KICK_SHARE_SUB), is the kick's own (a kit
     * heard through room mics reads 2.28 times its lower kicks). Toms in a song's fills share one pitch
     * by 7 at most. Only over a song's worth of kicks and toms (KICK_SHARE_HITS): an 8-bar tom fill
     * has more toms than kicks, at one pitch.
     */
    static final double KICK_SHARE = 0.15, KICK_SAME = 0.04, KICK_SHARE_SUB = 0.5;
    static final int KICK_SHARE_MIN = 8, KICK_SHARE_HITS = 64;
    /** A pitch KICK_MOST of the song's kicks read is the kick's whatever its low end (toms share one pitch by 7 at most). */
    static final double KICK_MOST = 0.4, KICK_MOST_SUB = 0.26;
    /** A tom's level under 70 Hz stays under TOM_LOW_END of its level at its pitch (0.09-0.45 seen). */
    static final double TOM_LOW_END = 0.5;
    /** A shuffle's beat repeats at least this share as well as its dotted beat (estimateBpm). */
    static final double SHUFFLE_BEAT = 0.5;
    /**
     * Dotted figures (3 sixteenths) can repeat best, reading 4/3 of the tempo; where the beat
     * (4/3 of that lag) repeats DOTTED_BEAT as well, it is the beat (0.90-1.02 seen; straight time
     * up to 0.08, a shuffle 0.6).
     */
    static final double DOTTED_BEAT = 0.8;
    /**
     * A "kick" reading under LOW_TOM of the song's usual kick pitch (the median of LOW_TOM_KICKS kicks
     * at least) is a floor tom tuned under the kick (Anarchy Drums' toms ring at 78-82 Hz under its
     * 113 Hz kick; other kits' kicks read that low 1 time in 600). Not at a pitch KICK_MOST of the
     * kicks share, nor where wires ring (a snare with it) or under LOW_TOM_HZ (no pitch read there).
     * A tom's tone rings on: 8 frames on it keeps LOW_TOM_RING of its level at its pitch (0.06-0.24;
     * a kick heard through room mics reads that low too, but dies at once: 0.00-0.04). A pitch that
     * already sounded just before is a guitar's note held over the kick; over guitars no tom is
     * looked for under the kick (a chord's low note rings there).
     */
    static final double LOW_TOM = 0.8, LOW_TOM_HZ = 40, LOW_TOM_RING = 0.05;
    static final int LOW_TOM_KICKS = 8;
    /** Such a tom with no other drum found at it still raises the tom bands' level by LOW_TOM_JUMP. */
    static final double LOW_TOM_JUMP = 0.1;
    /** A kick read at a higher pitch is still a kick under FLOOR_TOM_HZ: some kicks have no one clear pitch and read 50-85 Hz. */
    static final double FLOOR_TOM_HZ = 86;
    static final double OPEN_HAT_TAIL = 0.45, OPEN_HAT_LATE = 0.33;
    /**
     * openRing's ring after a closed hat: OPEN_TOP and OPEN_BAND were 0.3, and real kits' closed hats
     * (KVLT, Anarchy: a room keeps them ringing) were turned open by the hundred; at 0.45 KVLT's
     * closed hats taken for open fell 538 -> 409, Anarchy's 346 -> 264, five DrumGizmo kits' 478 -> 368,
     * and every open hat these found stayed found.
     */
    static final double OPEN_TOP = 0.45, OPEN_MID = 0.2, OPEN_SWELL = 1.0, OPEN_BAND = 0.45;
    /** At a kick or snare, a hat raises the hat band's level by HAT_OVER_DRUM (0.13 and more); the drum's own leak raises it about 0.06. */
    static final double HAT_OVER_DRUM = 0.1;
    /**
     * A kick louder than the hat band whose hat level rises less than KICK_HAT_JUMP (its click
     * leaking, 0.03-0.13), and a snare whose rise stays under SNARE_HAT_JUMP (its wires, 0.27-0.64;
     * a hat struck with it 0.68 and more), are hats only where they keep the hats' time.
     */
    static final double KICK_HAT_JUMP = 0.15, SNARE_HAT_JUMP = 0.66, SNARE_LIKE = 0.65, SNARE_LIKE_BODY = 0.5;
    /**
     * A hat peak within ECHO_FRAMES (~70 ms) of the one before that raises the hat band less than
     * ECHO_RISE and stays under ECHO_LEVEL of it is that hit's second peak (a kick's, an open hat's or a
     * ride's: Anarchy Drums' samples have one 40-120 ms on), not a hat of its own. (10 frames, 115 ms,
     * dropped 16th-note hats at 120 BPM and more, 125 ms apart: the four VST kits' songs lost 26-34% of
     * such hats; at 6, 148 more closed hats were found and 3 more hats written that were not played.)
     */
    static final int ECHO_FRAMES = 6;
    static final double ECHO_RISE = 0.2, ECHO_LEVEL = 0.8;
    /**
     * The hat band's onset threshold, as a share of BASE_DELTA (0.85 before: at 0.7 the four VST kits'
     * songs gave 273 more closed hats right and 92 more hats written that were not played; at 0.6 the
     * next 86 hats cost 83 more).
     */
    static final double HAT_DELTA = 0.7;
    /** Hats keep time at spacings up to HAT_TIME_MAX frames (0.56 s: quarter notes down to 107 BPM). */
    static final int HAT_TIME_MAX = 48;
    /** Seconds within which a ride or crash and a hat peak are the same hit. */
    static final double SAME_CYMBAL = 0.035;
    static final double RIDE_STABLE = 0.89, RIDE_LOW = 1.0, RIDE_HIGH = 0.7, RIDE_WINDOW = 2.0, RIDE_SHARE = 0.4;
    static final double RIDE_STABLE_RINGING = 0.84, RIDE_RING = 0.45, RIDE_TOP_RING = 0.3;
    /**
     * A sampled ride played dry (Anarchy Drums') holds its partials less: its wash keeps RIDE_WASH of
     * the hit 6 frames on (0.64-0.86) at RIDE_WASH_STABLE (0.76-0.86) while its 7-14 kHz dies to
     * RIDE_WASH_TOP 6 frames on (0.42-0.62) and RIDE_WASH_TOP_LATE 12 frames on (0.05-0.49). Closed
     * hats in a room and open hats ring like that too, but their top keeps on (0.6-1.0 and 0.5-0.8
     * 12 frames on).
     */
    static final double RIDE_WASH = 0.6, RIDE_WASH_STABLE = 0.74, RIDE_WASH_TOP = 0.65, RIDE_WASH_TOP_LATE = 0.4;
    static final int RIDE_ALONE = 2;
    static final double RIDE_NEAR = 1.0;
    static final int RIDE_SEEN = 3;
    /**
     * Rock and metal ride on the kick and snare, so 2 s can hold fewer than RIDE_SEEN clean hits:
     * then the clean hits within RIDE_WINDOW_WIDE seconds decide.
     */
    static final double RIDE_WINDOW_WIDE = 6.0;
    static final double PEDAL_BODY = 0.2, LOUD_BODY = 0.4, PEDAL_ALONE = 1.0, PEDAL_RUN = 0.7;
    /**
     * Rises in band level (linear, in units of the band's loud hits): a kick raises the kick band by
     * KICK_JUMP or more (soft kicks rise 0.29-0.32; a hat's leak into it, or a snare's thump without a
     * kick, rises 0.15 at most: from near silence it only looks big in log terms);
     * a snare raises its body band by SNARE_JUMP and by SNARE_OVER_KICK times the kick band's rise
     * (a kick's own body rises 0.6-0.9 times its kick band); a kick on a snare raises the kick band by
     * KICK_WITH_SNARE (a snare's thump alone reaches 0.6 in some kits).
     */
    static final double KICK_JUMP_BAND = 0.15, KICK_CLICK_JUMP = 0.55, KICK_JUMP_STRONG = 0.6;
    static final double KICK_JUMP = 0.2, SNARE_JUMP = 0.08, SNARE_OVER_KICK = 1.25, KICK_WITH_SNARE = 0.85;
    /**
     * Wires ringing less than WIRES_BAND: a snare's body still rises WEAK_WIRES_OVER_KICK times the kick
     * band's rise or more (95% of them 4.1 and more); a tom's low end raises the kick band too (84% under 3).
     * Not where the hat band jumps BRIGHT_SNARE (a bright snare's noise, or a hat with it: 0.64-1.33;
     * toms mostly up to 0.34).
     */
    static final double WEAK_WIRES_OVER_KICK = 3.0, BRIGHT_SNARE = 0.6;
    /** A ghost note whose wires ring on (WIRES_BAND) needs its body to rise only GHOST_JUMP (0.071-0.079 seen). */
    static final double GHOST_JUMP = 0.065;
    /**
     * Snare wires: the noise band (2-6 kHz) still holds this much of its onset's rise 60-90 ms on
     * (wiresLeft). A snare whose body shows holds WIRES or more; a tom's stick noise dies to
     * 0.00-0.05. Under guitar and piano (the body band already at MASKED_BODY of its loud level
     * before the onset) a snare's body hardly shows: a noise onset of WIRES_NOISE_JUMP whose rise
     * rings WIRES_BAND (snares keep 0.24-0.40, kicks and their bass note 0.04-0.21) up to WIRES_MAX
     * (a crash washes longer) is a snare too. Without a band, a ride ringing over a kick would look
     * like wires, so the body has to show.
     */
    /**
     * A snare's body (150-350 Hz) rises BODY_TONE times the band above it (400-1000 Hz), even a ghost
     * note under a hat (1.0-1.2; a roomy kit's snare 0.73 and more); plain noise, as Pulsekit's own
     * hats, rises 0.3-0.66 times. Over a bass line BODY_TONE_BAND, as guitar and piano add tones. A hat
     * struck with the snare (its band jumping HAT_WITH) hides the wires, so they are not asked for then.
     */
    static final double GAP_LO = 400, GAP_HI = 1000, BODY_TONE = 0.7, BODY_TONE_BAND = 0.9, HAT_WITH = 0.4;
    static final double WIRES = 0.08, WIRES_BAND = 0.23, WIRES_MAX = 0.6, WIRES_NOISE_JUMP = 0.5, MASKED_BODY = 0.25;
    /**
     * A ride washing under the band keeps the noise band at RINGING_NOISE or more before the
     * onsets (0.33-0.39; hats and guitar leave it at 0.19-0.31), and makes a kick's noise ring like
     * wires. There a snare also needs its wires to reach the hat band (6-14 kHz), which jumps
     * MASKED_HAT or more (0.93-1.02); a kick with the ride on it reaches about 0.6.
     */
    static final double MASKED_HAT = 0.9, RINGING_NOISE = 0.32;
    /** Snare wires jumping BRIGHT_WIRES_HAT in the hat band (1.37-1.44; kicks with a hat reach 0.94) need ring only BRIGHT_WIRES. */
    static final double BRIGHT_WIRES_HAT = 1.2, BRIGHT_WIRES = 0.15;
    /** Wires keep at most WIRES_LATE of their rise 150-230 ms on (0.00-0.13); a crash keeps 0.33-0.42. */
    static final double WIRES_LATE = 0.25;
    static final int MIN_GAP_FRAMES = 5;
    static final double BASE_DELTA = 0.30;

    static final int PPQ = 480;
    static final int KICK_NOTE = 36, SNARE_NOTE = 38, CLOSED_HAT = 42, PEDAL_HAT = 44, OPEN_HAT = 46, RIDE_NOTE = 51, CRASH_NOTE = 49;
    static final int FLOOR_TOM = 41, MID_TOM = 47, RACK_TOM = 50;

    static final double[] COS = new double[FFT_SIZE / 2];
    static final double[] SIN = new double[FFT_SIZE / 2];
    static {
        for (int i = 0; i < FFT_SIZE / 2; i++) {
            COS[i] = Math.cos(2 * Math.PI * i / FFT_SIZE);
            SIN[i] = -Math.sin(2 * Math.PI * i / FFT_SIZE);
        }
    }

    static class Audio {
        double[] samples;
        double sampleRate;
    }

    static class Hit {
        final double time;
        final int note;
        final double strength;
        Hit(double time, int note, double strength) {
            this.time = time;
            this.note = note;
            this.strength = strength;
        }
    }

    /**
     * pulsekit.work, user.dir and java.io.tmpdir as they were when the run started: in PyJav they
     * are the whole app's settings, and a run started meanwhile would change them.
     */
    static String[] startDirs;

    public static void main(String[] args) throws Exception {
        startDirs = new String[] {System.getProperty("pulsekit.work"), System.getProperty("user.dir"), System.getProperty("java.io.tmpdir")};
        zip = false;
        zipMb = 0;
        zipOutdir = null;
        made.clear();
        zipBase = null;
        zipDir = null;
        try {
            run(args);
            if (zip && !made.isEmpty()) zipMade();
        } catch (Throwable ex) {
            String m = ex.getMessage();
            finish("Failed: " + (m == null || m.length() == 0 ? ex.toString() : m));
        }
    }

    /** --zip, --zip_mb, --zip_outdir; the files made (the MIDI, the log) and the zip's name and folder. */
    static boolean zip;
    static double zipMb;
    static String zipOutdir;
    static final List<File> made = new ArrayList<File>();
    static String zipBase;
    static File zipDir;

    /** --zip: what the run made into zips (of at most --zip_mb MB each), in --zip_outdir or beside the MIDI. */
    static void zipMade() {
        File dir = zipDir;
        if (zipOutdir != null && zipOutdir.length() > 0) {
            String path = expandFolder(zipOutdir, java.util.Calendar.getInstance());
            File d = new File(path);
            if (!d.isAbsolute()) d = new File(workDir(), path);
            dir = d;
        }
        if (dir == null) dir = made.get(0).getAbsoluteFile().getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            System.out.println("Could not make the zip's folder " + dir.getPath());
            return;
        }
        long limit = zipMb > 0 ? (long) (zipMb * 1024 * 1024) : Long.MAX_VALUE;
        List<List<File>> parts = new ArrayList<List<File>>();
        List<File> now = new ArrayList<File>();
        long size = 0;
        for (File f : made) {
            if (!f.isFile()) continue;
            if (!now.isEmpty() && size + f.length() > limit) {
                parts.add(now);
                now = new ArrayList<File>();
                size = 0;
            }
            now.add(f);
            size += f.length();
        }
        if (!now.isEmpty()) parts.add(now);
        String base = zipBase != null ? zipBase : made.get(0).getName().replaceAll("(?i)\\.midi?$", "");
        for (int k = 0; k < parts.size(); k++) {
            File z = new File(dir, base + (parts.size() > 1 ? "-part" + (k + 1) + "of" + parts.size() : "") + ".zip");
            try {
                java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(z));
                try {
                    Set<String> names = new HashSet<String>();
                    for (File f : parts.get(k)) {
                        String n = f.getName();
                        for (int c = 2; !names.add(n); c++) n = f.getName().replaceAll("(\\.[^.]+)?$", "-" + c + "$1");
                        zos.putNextEntry(new java.util.zip.ZipEntry(n));
                        zos.write(readBytes(f));
                        zos.closeEntry();
                    }
                } finally {
                    zos.close();
                }
                System.out.println(String.format(java.util.Locale.ROOT, "Wrote %s: %d file%s, %.1f KB (%s)", z.getName(), parts.get(k).size(), parts.get(k).size() == 1 ? "" : "s",
                        z.length() / 1024.0, z.getAbsoluteFile().getParent()));
            } catch (Exception ex) {
                System.out.println("Could not write " + z.getName() + " (" + ex.getMessage() + ")");
            }
        }
    }

    static byte[] readBytes(File f) throws java.io.IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    /** The folder a relative --zip_outdir is under: Pulsekit's work folder, else the current one. */
    static File workDir() {
        String w = System.getProperty("pulsekit.work");
        return new File(w != null && w.length() > 0 ? w : System.getProperty("user.dir", "."));
    }

    /**
     * --zip_outdir's % codes filled in for `now`; %n is the first number from 1 whose folder (the path
     * up to the part %n is in) is not there yet. No % codes: the path as it is.
     */
    static String expandFolder(String pattern, java.util.Calendar now) {
        if (pattern.indexOf('%') < 0) return pattern;
        String one = fillCodes(pattern, now, 1), two = fillCodes(pattern, now, 2);
        if (one.equals(two)) return one;
        int at = 0;
        while (at < Math.min(one.length(), two.length()) && one.charAt(at) == two.charAt(at)) at++;
        for (int n = 1; n < 100000; n++) {
            String path = fillCodes(pattern, now, n);
            int end = path.indexOf('/', at);
            int endB = path.indexOf('\\', at);
            if (end < 0 || (endB >= 0 && endB < end)) end = endB;
            File head = new File(end < 0 ? path : path.substring(0, end));
            if (!head.isAbsolute()) head = new File(workDir(), head.getPath());
            if (!head.exists()) return path;
        }
        return one;
    }

    /** The % codes filled in, n as `n` (dd d mm m yyyy yy n; %% is a %; a lone % stays). */
    static String fillCodes(String pattern, java.util.Calendar now, int n) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c != '%') {
                out.append(c);
                continue;
            }
            if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '%') {
                out.append('%');
                i++;
                continue;
            }
            int j = i + 1;
            while (j < pattern.length()) {
                String rest = pattern.substring(j);
                if (rest.startsWith("yyyy")) {
                    out.append(String.format(java.util.Locale.ROOT, "%04d", now.get(java.util.Calendar.YEAR)));
                    j += 4;
                } else if (rest.startsWith("yy")) {
                    out.append(String.format(java.util.Locale.ROOT, "%02d", now.get(java.util.Calendar.YEAR) % 100));
                    j += 2;
                } else if (rest.startsWith("dd")) {
                    out.append(String.format(java.util.Locale.ROOT, "%02d", now.get(java.util.Calendar.DAY_OF_MONTH)));
                    j += 2;
                } else if (rest.startsWith("d")) {
                    out.append(now.get(java.util.Calendar.DAY_OF_MONTH));
                    j += 1;
                } else if (rest.startsWith("mm")) {
                    out.append(String.format(java.util.Locale.ROOT, "%02d", now.get(java.util.Calendar.MONTH) + 1));
                    j += 2;
                } else if (rest.startsWith("m")) {
                    out.append(now.get(java.util.Calendar.MONTH) + 1);
                    j += 1;
                } else if (rest.startsWith("n")) {
                    out.append(n);
                    j += 1;
                } else {
                    break;
                }
            }
            if (j == i + 1) out.append('%');
            i = j - 1;
        }
        return out.toString();
    }

    static final String DEFAULT_LOG = "DrumMidi_CRT_log.txt";
    /** The --log file, or null; and its lines so far. */
    static String logPath;
    /** The folder a relative --log is written in: the input's (or --dir's). */
    static File logDir;
    static final StringBuilder logText = new StringBuilder();

    /**
     * Stops when PyJav aborts the run (it interrupts the program's thread; outside the app the
     * process is stopped). Checked between the steps and in the long loops.
     */
    static void checkStop() {
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Aborted");
    }

    /** How far the analysis is ("Progress: 40%"), for PyJav's Running line; not kept in the log. */
    static void progress(int pct) {
        checkStop();
        System.out.println("Progress: " + pct + "%");
    }

    /** Prints a line and keeps it for the --log file. */
    static void note(String line) {
        System.out.println(line);
        logText.append(line).append('\n');
    }

    /** Writes the --log file, then prints the last line (PyJav reads it as the run's status). */
    static void finish(String last) {
        logText.append(last).append('\n');
        if (logPath != null) {
            try {
                File f = new File(logPath);
                // A bare name (--log results.txt) goes beside the input: the phone's current folder is read-only.
                if (!f.isAbsolute() && logDir != null) f = new File(logDir, logPath);
                File parent = f.getAbsoluteFile().getParentFile();
                if (parent != null && !parent.isDirectory()) parent.mkdirs();
                FileOutputStream fos = new FileOutputStream(f);
                try {
                    fos.write(logText.toString().getBytes("UTF-8"));
                } finally {
                    fos.close();
                }
                System.out.println("Log: " + f.getAbsolutePath());
                if (!made.contains(f.getAbsoluteFile())) made.add(f.getAbsoluteFile());
            } catch (Exception ex) {
                System.out.println("Could not write the log " + logPath + ": " + ex.getMessage());
            }
        }
        System.out.println(last);
    }

    /**
     * --dir <folder> [--recursive]: DrumMidi on every WAV in the folder (not one it made), each
     * written as <wav>-dm.mid beside it, as vst_render.py --test names them, for CompareHits --batch.
     */
    static void runDir(List<String> rest, int dirAt) throws Exception {
        if (dirAt + 1 >= rest.size()) {
            finish("Failed: --dir needs a folder");
            return;
        }
        File dir = new File(rest.get(dirAt + 1)).getAbsoluteFile();
        if (!dir.isDirectory()) {
            finish("Failed: no folder " + dir.getPath());
            return;
        }
        List<String> opts = new ArrayList<String>(rest);
        opts.remove(dirAt + 1);
        opts.remove(dirAt);
        boolean recursive = opts.remove("--recursive");
        List<File> wavs = new ArrayList<File>();
        wavsIn(dir, recursive, wavs);
        if (wavs.isEmpty()) {
            finish("Failed: no WAV files in " + dir.getName());
            return;
        }
        String keepLog = logPath;
        logPath = null;
        int done = 0;
        for (int k = 0; k < wavs.size(); k++) {
            checkStop();
            File w = wavs.get(k);
            File mid = new File(w.getParentFile(), w.getName().replaceAll("(?i)\\.wave?$", "") + "-dm.mid");
            List<String> one = new ArrayList<String>();
            one.add(w.getPath());
            one.add(mid.getPath());
            one.addAll(opts);
            System.out.println("== " + (k + 1) + "/" + wavs.size() + ": " + w.getName());
            long before = mid.isFile() ? mid.lastModified() : -1;
            mid.delete();
            try {
                run(one.toArray(new String[0]));
            } catch (IllegalStateException ex) {
                if ("Aborted".equals(ex.getMessage())) throw ex;
                System.out.println("Failed " + w.getName() + ": " + ex.getMessage());
            } catch (Exception ex) {
                System.out.println("Failed " + w.getName() + ": " + ex.getMessage());
            }
            if (mid.isFile() && mid.lastModified() != before) done++;
        }
        logPath = keepLog;
        // --zip: the folder's MIDI files (and the log) as <folder>-dm.zip in the folder (or --zip_outdir).
        zipBase = dir.getName() + "-dm";
        zipDir = dir;
        finish("Succeeded: DrumMidi on " + done + " of " + wavs.size() + " WAV files in " + dir.getName() + " (<wav>-dm.mid beside each)");
    }

    static void wavsIn(File dir, boolean recursive, List<File> into) {
        File[] in = dir.listFiles();
        if (in == null) return;
        Arrays.sort(in);
        for (File f : in) {
            String low = f.getName().toLowerCase(java.util.Locale.ROOT);
            if (f.isFile() && (low.endsWith(".wav") || low.endsWith(".wave"))) into.add(f);
        }
        if (recursive) for (File f : in) if (f.isDirectory() && !f.getName().startsWith(".")) wavsIn(f, true, into);
    }

    static String num(double v) {
        String t = String.format(java.util.Locale.ROOT, "%.2f", v);
        while (t.endsWith("0")) t = t.substring(0, t.length() - 1);
        if (t.endsWith(".")) t = t.substring(0, t.length() - 1);
        return t;
    }

    static void run(String[] args) throws Exception {
        // --learn-kit <render> <times.txt> <out.kit>: a kit's kick and toms, from vst_render's test beat.
        if (args.length > 0 && "--learn-kit".equals(args[0])) {
            if (args.length < 4) {
                finish("Failed: --learn-kit needs the test beat's render, its notes' times (seconds and note on each line, then k or s for a cymbal struck with a kick or snare, g for one played on in eighths) and the .kit file to write");
                return;
            }
            KitProfile.learn(new File(args[1]), new File(args[2]), new File(args[3]));
            return;
        }
        // --cut-hits <render> <times.txt> <folder>: vst_render's test beat cut into its hits, for the drum sound library.
        if (args.length > 0 && "--cut-hits".equals(args[0])) {
            if (args.length < 4) {
                finish("Failed: --cut-hits needs the test beat's render, its times file and a folder for the hits");
                return;
            }
            Sounds.cut(new File(args[1]), new File(args[2]), new File(args[3]));
            return;
        }
        // --sounds <list.txt> <out.txt>: each drum sound listed measured (Pulsekit's drum sound library).
        if (args.length > 0 && "--sounds".equals(args[0])) {
            if (args.length < 3) {
                finish("Failed: --sounds needs a list of sound files (one a line) and the file to write the measures to");
                return;
            }
            Sounds.measure(new File(args[1]), new File(args[2]));
            return;
        }
        List<String> rest = new ArrayList<String>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i] == null ? "" : args[i].trim();
            if (a.length() == 0) continue;
            // --zip, --zip_mb N, --zip_outdir <folder>: the MIDI made (and the log) also zipped.
            if ("--zip".equals(a)) {
                zip = true;
                continue;
            }
            if ("--zip_mb".equals(a) && i + 1 < args.length) {
                zipMb = Double.parseDouble(args[++i].trim());
                zip = true;
                continue;
            }
            if ("--zip_outdir".equals(a) && i + 1 < args.length) {
                zipOutdir = args[++i].trim();
                zip = true;
                continue;
            }
            if ("--log".equals(a)) {
                // --log alone writes DrumMidi_CRT_log.txt.
                String nx = i + 1 < args.length && args[i + 1] != null ? args[i + 1].trim() : "";
                String low = nx.toLowerCase(java.util.Locale.ROOT);
                boolean named = nx.length() > 0 && !nx.startsWith("--") && !low.endsWith(".wav") && !low.endsWith(".wave")
                        && !low.endsWith(".mp3") && !low.endsWith(".mid") && !low.endsWith(".midi");
                logPath = named ? nx : DEFAULT_LOG;
                if (named) i++;
                continue;
            }
            rest.add(a);
        }
        args = rest.toArray(new String[0]);
        for (String a : rest) {
            if (a.startsWith("--")) continue;
            File in = new File(a).getAbsoluteFile();
            logDir = in.isDirectory() ? in : in.getParentFile();
            break;
        }
        // --dir: every WAV in the folder, each to <wav>-dm.mid beside it, with the same switches.
        int dirAt = rest.indexOf("--dir");
        if (dirAt >= 0) {
            runDir(rest, dirAt);
            return;
        }
        if (args.length < 2) {
            note("Usage: java DrumMidi <input.wav> <output.mid> "
                    + "[--sens N] [--hat N] [--tom N] [--ride N] [--crash N] [--bpm N] [--quantize N] [--no-hpss] [--no-cymbals] [--no-toms] [--snare-leak] [--faded-opens] [--no-cymbal-model] [--kit <file.kit>] [--library <file.kit>] [--log <logfile>] [--dir <folder>] [--recursive] [--zip] [--zip_mb N] [--zip_outdir <folder>]");
            finish("Failed: need an input wav and an output mid");
            return;
        }
        File inFile = new File(args[0]);
        File outFile = new File(args[1]);
        StringBuilder given = new StringBuilder();
        for (int i = 2; i < args.length; i++) given.append(given.length() == 0 ? "" : " ").append(args[i]);
        double sens = 1.0, hatSens = -1, tomSens = -1, rideSens = -1, crashSens = -1, bpmOverride = 0;
        int quant = 0;
        boolean hpss = true;
        // Kick, snare and toms only: hats, rides and crashes can be played in later on the pads.
        boolean cymbals = true;
        // No toms: they are still told apart from kicks and snares, but not written.
        boolean writeToms = true;
        // Snare leak: hats on a snare that rise no more than its own wires do are left out.
        boolean snareLeak = false;
        boolean fadedOpens = false;
        // The cymbal model (CymModel): each cymbal found told apart again by its sound in the song.
        boolean cymModel = true;
        // A kit profile (vst_render's test beat, --learn-kit): its kick and toms tell the hits found apart.
        KitProfile kit = null;
        // The drum sound library's profile (Pulsekit's drum-library.kit, from many sounds): its kick and
        // toms tell the hits found apart where no kit profile is given.
        KitProfile library = null;
        for (int i = 2; i < args.length; i++) {
            String opt = args[i];
            if ("--sens".equals(opt)) sens = Double.parseDouble(args[++i]);
            else if ("--hat".equals(opt)) hatSens = Double.parseDouble(args[++i]);
            else if ("--tom".equals(opt)) tomSens = Double.parseDouble(args[++i]);
            else if ("--ride".equals(opt)) rideSens = Double.parseDouble(args[++i]);
            else if ("--crash".equals(opt)) crashSens = Double.parseDouble(args[++i]);
            else if ("--bpm".equals(opt)) bpmOverride = Double.parseDouble(args[++i]);
            else if ("--quantize".equals(opt)) quant = Integer.parseInt(args[++i]);
            else if ("--no-hpss".equals(opt)) hpss = false;
            else if ("--no-cymbals".equals(opt)) cymbals = false;
            else if ("--no-toms".equals(opt)) writeToms = false;
            else if ("--snare-leak".equals(opt)) snareLeak = true;
            else if ("--faded-opens".equals(opt)) fadedOpens = true;
            else if ("--no-cymbal-model".equals(opt)) cymModel = false;
            else if ("--kit".equals(opt)) kit = KitProfile.read(new File(args[++i]));
            else if ("--library".equals(opt)) library = KitProfile.library(new File(args[++i]));
            else if (opt.startsWith("-")) throw new IllegalArgumentException("Unknown option: " + opt);
        }
        if (hatSens <= 0) hatSens = sens;
        if (tomSens <= 0) tomSens = sens;
        if (rideSens <= 0) rideSens = sens;
        if (crashSens <= 0) crashSens = sens;

        note("DrumMidi_CRT | input " + inFile.getName() + " | output " + outFile.getName());
        note("Settings: " + (given.length() == 0 ? "defaults" : given.toString()) + " | used: --sens " + num(sens)
                + " --hat " + num(hatSens) + " --tom " + num(tomSens) + " --ride " + num(rideSens) + " --crash " + num(crashSens)
                + (bpmOverride > 0 ? " --bpm " + num(bpmOverride) : "") + (quant > 0 ? " --quantize " + quant : "")
                + (hpss ? "" : " --no-hpss") + (cymbals ? "" : " --no-cymbals") + (writeToms ? "" : " --no-toms") + (snareLeak ? " --snare-leak" : "") + (fadedOpens ? " --faded-opens" : "") + (cymModel ? "" : " --no-cymbal-model")
                + (kit != null ? " --kit " + kit.name : "") + (library != null ? " --library " + library.name : ""));
        if (library != null && kit != null) note("Library " + library.name + " not used: the kit profile " + kit.name + " is this kit's own");
        else if (library != null) note("Library " + library.name + ": kick and toms from " + library.said + " (its cymbals are not used)");
        Audio audio = readAudio(inFile);
        double sr = audio.sampleRate;
        double fps = sr / HOP;
        note(String.format(java.util.Locale.ROOT, "Loaded %.1f s at %.0f Hz", audio.samples.length / sr, sr));
        progress(5);
        if (audio.samples.length < FFT_SIZE * 4) {
            finish("Failed: audio is too short");
            return;
        }

        float[][] mag = spectrogram(audio.samples, sr);
        progress(25);
        int maxBin = mag[0].length - 1;
        // Rises in the raw spectrum, before HPSS evens them out. A drum jumps; a bass note mostly does not.
        double[] tomRise = rise(mag, bin(FLOOR_LO, sr, maxBin), bin(RACK_HI, sr, maxBin));
        double[] hatRaw = bandMean(mag, bin(HAT_LO, sr, maxBin), bin(HAT_HI, sr, maxBin));
        double[] kickLevel = bandMean(mag, bin(KICK_LO, sr, maxBin), bin(KICK_HI, sr, maxBin));
        double[] bodyLevel = bandMean(mag, bin(SNARE_BODY_LO, sr, maxBin), bin(SNARE_BODY_HI, sr, maxBin));
        double[] bodyRaw = bodyLevel.clone();
        double[] tomLevel = bandMean(mag, bin(FLOOR_LO, sr, maxBin), bin(RACK_HI, sr, maxBin));
        double[] noiseLevel = bandMean(mag, bin(SNARE_NOISE_LO, sr, maxBin), bin(SNARE_NOISE_HI, sr, maxBin));
        normalize(noiseLevel);
        // Above the body band, unnormalized: a drum's body is a tone over it, plain noise is as loud there.
        double[] gapRaw = bandMean(mag, bin(GAP_LO, sr, maxBin), bin(GAP_HI, sr, maxBin));
        double[] lowPitch = lowPitch(mag, sr, maxBin);
        CymbalShape shape = new CymbalShape(mag, sr, maxBin);
        // Before HPSS, which takes a tom's ring away as a held tone.
        double[] lowRaw = bandMean(mag, bin(RING_TOM_LO, sr, maxBin), bin(RING_TOM_HI, sr, maxBin));
        double[] ringRaw = bandMean(mag, bin(RING_TOM_RING_LO, sr, maxBin), bin(RING_TOM_RING_HI, sr, maxBin));
        // Before HPSS, which takes a guitar's held notes away.
        boolean guitar = guitarLine(mag, sr, maxBin);
        double[] hatLevel = hatRaw.clone();
        normalize(hatLevel);
        normalize(kickLevel);
        normalize(bodyLevel);
        normalize(tomLevel);
        Levels lv = new Levels(kickLevel, bodyLevel, noiseLevel, bodyRaw, gapRaw, hatLevel);
        if (hpss) {
            System.out.println("Separating percussive content (a few seconds)...");
            applyHpss(mag);
            progress(70);
        }

        double[] kick = flux(mag, bin(KICK_LO, sr, maxBin), bin(KICK_HI, sr, maxBin));
        double[] snareBody = flux(mag, bin(SNARE_BODY_LO, sr, maxBin), bin(SNARE_BODY_HI, sr, maxBin));
        double[] snareNoise = flux(mag, bin(SNARE_NOISE_LO, sr, maxBin), bin(SNARE_NOISE_HI, sr, maxBin));
        normalize(kick);
        normalize(snareBody);
        normalize(snareNoise);

        double[] snare = new double[kick.length];
        for (int t = 0; t < snare.length; t++) snare[t] = Math.sqrt(snareBody[t] * snareNoise[t]);
        normalize(snare);

        int hatLo = bin(HAT_LO, sr, maxBin);
        int hatHi = bin(HAT_HI, sr, maxBin);
        double[] hat = flux(mag, hatLo, hatHi);
        normalize(hat);

        int rideLo = bin(RIDE_LO, sr, maxBin);
        int rideHi = bin(RIDE_HI, sr, maxBin);
        double[] ride = flux(mag, rideLo, rideHi);
        double[] rideEnergy = bandMean(mag, rideLo, rideHi);
        normalize(ride);
        normalize(rideEnergy);

        int topLo = bin(8000, sr, maxBin);
        int topHi = bin(14000, sr, maxBin);
        double[] top = flux(mag, topLo, topHi);
        double[] topEnergy = bandMean(mag, topLo, topHi);
        normalize(top);
        normalize(topEnergy);
        double[] crash = new double[kick.length];
        for (int t = 0; t < crash.length; t++) {
            if (ride[t] < 0.35 || top[t] < 0.35) continue;
            crash[t] = Math.sqrt(ride[t] * top[t]);
        }

        // Kick onsets only count where the beater clicks. Bass notes have no click.
        double[] kickOnset = new double[kick.length];
        for (int t = 0; t < kick.length; t++) {
            double g = Math.min(1.0, near(snareNoise, t) / KICK_CLICK);
            kickOnset[t] = kick[t] * g * g;
        }
        normalize(kickOnset);
        // Toms likewise need a stick attack.
        double[] attack = flux(mag, bin(ATTACK_LO, sr, maxBin), bin(ATTACK_HI, sr, maxBin));
        normalize(attack);

        double[] floor = flux(mag, bin(FLOOR_LO, sr, maxBin), bin(FLOOR_HI, sr, maxBin));
        double[] mid = flux(mag, bin(MID_LO, sr, maxBin), bin(MID_HI, sr, maxBin));
        double[] rack = flux(mag, bin(RACK_LO, sr, maxBin), bin(RACK_HI, sr, maxBin));
        normalize(floor);
        normalize(mid);
        normalize(rack);
        double[] toms = new double[kick.length];
        for (int t = 0; t < toms.length; t++) {
            if (hat[t] > 0.75) continue;
            double g = quietBefore(tomLevel, t) ? 1.0 : Math.min(1.0, Math.max(attack[t], t > 0 ? attack[t - 1] : 0) / TOM_ATTACK_GATE);
            toms[t] = Math.max(floor[t], Math.max(mid[t], rack[t])) * g * g;
        }
        normalize(toms);

        double[] drums = new double[kick.length];
        for (int t = 0; t < drums.length; t++) drums[t] = kick[t] + snare[t];

        double delta = BASE_DELTA / sens;
        List<Hit> hits = new ArrayList<Hit>();
        // A snare's body jumps more than the kick band. Kick plus hat looks like a snare otherwise.
        List<Integer> snarePeaks = new ArrayList<Integer>();
        List<Double> snareBodies = new ArrayList<Double>();
        List<Integer> notSnares = new ArrayList<Integer>();
        for (Integer p : pickPeaks(snare, delta)) {
            int t = p.intValue();
            if (!isSnare(lv, t, sens, rideRings(rideEnergy, t))) {
                notSnares.add(p);
                continue;
            }
            snarePeaks.add(p);
            snareBodies.add(Double.valueOf(jump(bodyLevel, t)));
            hits.add(new Hit(t * (double) HOP / sr, SNARE_NOTE, snare[t]));
        }
        // Ghost notes too soft for their wires to show: the loud snares' body, played softly.
        SnareBody loudSnare = new SnareBody(mag, hits, sr);
        // Softer still (under the peak threshold, or between the hits of a drag): picked again at
        // GHOST_DELTA of it, they count only with the song's snare body.
        boolean[] near = new boolean[snare.length];
        for (Integer p : snarePeaks) mark(near, p.intValue(), 2);
        for (Integer p : pickPeaks(snare, delta * GHOST_DELTA, 3)) if (!near[p.intValue()] && !notSnares.contains(p)) notSnares.add(p);
        for (Integer p : notSnares) {
            int t = p.intValue();
            if (near[t] || jump(bodyLevel, t) < GHOST_JUMP / sens || jump(kickLevel, t) >= KICK_JUMP || !loudSnare.ghost(mag, t)) continue;
            mark(near, t, 2);
            snarePeaks.add(p);
            snareBodies.add(Double.valueOf(jump(bodyLevel, t)));
            hits.add(new Hit(t * (double) HOP / sr, SNARE_NOTE, snare[t]));
        }
        // Softest ghost notes: their body hardly rises on the song's scale, but it rises, at the
        // loud snares' pitch and with their spectrum, to SOFT_GHOST_LEVEL of their body or more.
        double loudBody = loudSnare.rawBody(lv.bodyRaw, hits, sr);
        double ring = loudSnare.ring(lv.bodyRaw, hits, sr);
        if (loudBody > 0 && ring < SOFT_GHOST_RING) {
            for (Integer p : pickPeaks(snare, delta * SOFT_GHOST_DELTA, 3)) {
                int t = p.intValue();
                if (near[t] || t < 3 || jump(kickLevel, t) >= SOFT_GHOST_KICK) continue;
                double peakRaw = Math.max(lv.bodyRaw[t], lv.bodyRaw[Math.min(lv.bodyRaw.length - 1, t + 2)]);
                if (peakRaw < SOFT_GHOST_LEVEL * loudBody || peakRaw < SOFT_GHOST_RISE * lv.bodyRaw[t - 3]) continue;
                if (!loudSnare.ghost(mag, t, SOFT_GHOST_LIKE)) continue;
                mark(near, t, 2);
                snarePeaks.add(p);
                snareBodies.add(Double.valueOf(jump(bodyLevel, t)));
                hits.add(new Hit(t * (double) HOP / sr, SNARE_NOTE, snare[t]));
            }
        }
        rollSnares(lv, mag, snare, notSnares, snarePeaks, snareBodies, near, hits, bodyLevel, kickLevel, sens, sr);
        if (writeToms) snareToms(hits, lowPitch, lv.kick, sr);
        List<Integer> kickFrames = new ArrayList<Integer>();
        boolean bass = bassLine(kickLevel);
        boolean bassTones = bassNotes(kickLevel);
        boolean[] snareAt = new boolean[kick.length];
        for (Integer p : snarePeaks) mark(snareAt, p.intValue(), 1);
        for (Integer p : pickPeaks(kickOnset, delta)) {
            int t = p.intValue();
            double jk = jump(kickLevel, t);
            if (bass ? !bandKick(jk, jump(noiseLevel, t)) || jk < KICK_WITH_SNARE && maskedSnare(lv, t, sens, rideRings(rideEnergy, t)) : jk < KICK_JUMP) continue;
            if (snareAt[t] && jk < KICK_WITH_SNARE) continue;
            kickFrames.add(p);
            hits.add(new Hit(t * (double) HOP / sr, KICK_NOTE, kick[t]));
        }
        // A floor tom is heard as a kick, but well above the song's kick pitch.
        if (!bassTones) floorToms(hits, kickFrames, lowPitch, kickLevel, noiseLevel, sr, mag, snareAt, loudSnare, guitar);
        pedalHats(hits, snarePeaks, snareBodies, sr);
        // Toms are not where a kick or snare would be found at full sensitivity,
        // so a lower --sens does not turn quiet kicks and snares into toms.
        boolean[] busy = new boolean[kick.length];
        for (Integer p : pickPeaks(snare, BASE_DELTA)) {
            int t = p.intValue();
            if (isSnare(lv, t, 1, rideRings(rideEnergy, t))) mark(busy, t, 2);
        }
        for (Integer p : pickPeaks(kickOnset, BASE_DELTA)) if (jump(kickLevel, p.intValue()) >= KICK_JUMP) mark(busy, p.intValue(), 2);
        // HAT_DELTA of the onset threshold: soft hats among loud ones (their mean over 16 frames each way)
        // are found too.
        List<Integer> hatPeaks = cymbals ? pickPeaks(hat, (BASE_DELTA * HAT_DELTA) / Math.max(0.05, hatSens), 3) : new ArrayList<Integer>();
        // A snare's noise (or a kick's click) reaches the hat band, so a hat under one is taken only
        // where it keeps the time of the hats around it (hats on both sides at the same spacing, or
        // two before or after it): a lone snare or a tom fill has no hats to keep time with.
        int nHats = hatPeaks.size();
        boolean[] keep = new boolean[nHats];
        boolean[] under = new boolean[nHats];
        // Taken for a ride or crash (their band rings on): a hat after all if neither is found there.
        boolean[] maybeCymbal = new boolean[nHats];
        // Snare-like: a run of them keeping time among themselves are hats (a count-in, hats alone).
        boolean[] snareLike = new boolean[nHats];
        for (int i = 0; i < nHats; i++) {
            int t = hatPeaks.get(i).intValue();
            if (isCrash(ride, top, rideEnergy, topEnergy, t) || ride[t] >= hat[t] * 0.85 && rideRings(rideEnergy, t) && snareBody[t] < ride[t]) {
                maybeCymbal[i] = true;
                continue;
            }
            if (snare[t] > 1.0 && hat[t] < snare[t]) {
                under[i] = jump(hatLevel, t) >= HAT_OVER_DRUM;
                continue;
            }
            double rise = jump(hatLevel, t);
            if (kick[t] > hat[t] && rise < KICK_HAT_JUMP) {
                under[i] = true;
                continue;
            }
            if (rise < SNARE_HAT_JUMP && snare[t] >= hat[t] * SNARE_LIKE && snareBody[t] >= SNARE_LIKE_BODY && kick[t] < snare[t]) {
                under[i] = true;
                snareLike[i] = true;
                continue;
            }
            keep[i] = true;
        }
        boolean[] kept = new boolean[nHats];
        for (int i = 0; i < nHats; i++) kept[i] = keep[i] || snareLike[i];
        for (int i = 0; i < nHats; i++) if (snareLike[i] && keepsTime(hatPeaks, kept, i)) keep[i] = true;
        for (boolean more = true; more; ) {
            more = false;
            for (int i = 0; i < nHats; i++) {
                if (!under[i] || keep[i] || !keepsTime(hatPeaks, keep, i)) continue;
                keep[i] = true;
                more = true;
            }
        }
        for (int i = 0; i < nHats; i++) {
            if (!keep[i]) continue;
            int t = hatPeaks.get(i).intValue();
            if (echo(hatPeaks, hat, hatLevel, i)) continue;
            boolean open = openHat(hatRaw, hatPeaks, i);
            // A pedal hat's chick reaches the hat band: no closed hat on it.
            if (!open && onPedal(hits, t * (double) HOP / sr)) continue;
            hits.add(new Hit(t * (double) HOP / sr, open ? OPEN_HAT : CLOSED_HAT, hat[t]));
        }
        List<Integer> ridePeaks = cymbals ? pickPeaks(ride, (BASE_DELTA * 0.9) / Math.max(0.05, rideSens), 5) : new ArrayList<Integer>();
        for (int i = 0; i < ridePeaks.size(); i++) {
            int t = ridePeaks.get(i).intValue();
            if (!rideRings(rideEnergy, t)) continue;
            if (isCrash(ride, top, rideEnergy, topEnergy, t)) continue;
            if (snareBody[t] > ride[t] && snareBody[t] > 0.8) continue;
            if (hat[t] > ride[t] * 1.35) continue;
            hits.add(new Hit(t * (double) HOP / sr, RIDE_NOTE, ride[t]));
        }
        // A kit whose open hat sounds like its crash (its profile's looks: openLikeCrash) gets its
        // crashes only from the later passes (dark, ridden): here its open hats would be the crashes.
        boolean openLikeCrash = kit != null && kit.openLikeCrash();
        if (openLikeCrash) note("Kit " + kit.name + ": its open hat sounds like its crash: crashes only where they are dark or ridden");
        List<Integer> crashPeaks = cymbals && !openLikeCrash ? pickPeaks(crash, (BASE_DELTA * 1.7) / Math.max(0.05, crashSens), 32) : new ArrayList<Integer>();
        for (int i = 0; i < crashPeaks.size(); i++) {
            int t = crashPeaks.get(i).intValue();
            if (!isCrash(ride, top, rideEnergy, topEnergy, t)) continue;
            if (!crashAttack(ride, top, t)) continue;
            hits.add(new Hit(t * (double) HOP / sr, CRASH_NOTE, crash[t]));
        }
        // A room or reverb keeps the ride band ringing after a hat too: where no ride or crash was
        // found at a hat taken for one, it is the hat.
        for (int i = 0; i < nHats; i++) {
            if (!maybeCymbal[i]) continue;
            int t = hatPeaks.get(i).intValue();
            double time = t * (double) HOP / sr;
            boolean found = false;
            for (Hit h : hits) {
                if ((h.note == RIDE_NOTE || h.note == CRASH_NOTE) && Math.abs(h.time - time) <= SAME_CYMBAL) found = true;
            }
            if (found || echo(hatPeaks, hat, hatLevel, i)) continue;
            boolean open = openHat(hatRaw, hatPeaks, i);
            if (!open && onPedal(hits, time)) continue;
            hits.add(new Hit(time, open ? OPEN_HAT : CLOSED_HAT, hat[t]));
        }
        List<Integer> tomPeaks = pickPeaks(toms, BASE_DELTA / Math.max(0.05, tomSens), 4);
        for (int i = 0; i < tomPeaks.size(); i++) {
            int t = tomPeaks.get(i).intValue();
            if (busy[t]) continue;
            // A ghost note (the song's snare, played softly) is written as a snare, toms or not.
            if (jump(bodyLevel, t) >= GHOST_JUMP && loudSnare.ghost(mag, t)) {
                hits.add(new Hit(t * (double) HOP / sr, SNARE_NOTE, snare[t]));
                continue;
            }
            // A tom is struck as hard as a snare and its low-mid jumps. Bass notes fail one or both.
            // Its band's level jumps (a hat's leak into it rises from near silence in log terms only),
            // and its body is a tone without wires. Over a band, bass notes are told apart by the stick.
            if (jump(tomLevel, t) < TOM_JUMP / Math.max(0.05, tomSens) || wiresLeft(noiseLevel, t) >= TOM_WIRES) continue;
            if (!quietBefore(tomLevel, t) && (near(attack, t) < TOM_ATTACK || near(tomRise, t) < TOM_RISE)) continue;
            if (jump(bodyLevel, t) >= TOM_SNARE_BODY) {
                hits.add(new Hit(t * (double) HOP / sr, SNARE_NOTE, snare[t]));
                continue;
            }
            double f = floor[t], m = mid[t], r = rack[t];
            int note;
            double strength;
            if (f >= m && f >= r) {
                if (kick[t] > f * 1.15) continue;
                note = FLOOR_TOM;
                strength = f;
            } else if (m >= r) {
                note = MID_TOM;
                strength = m;
            } else {
                if (snare[t] > r) continue;
                note = RACK_TOM;
                strength = r;
            }
            hits.add(new Hit(t * (double) HOP / sr, note, strength));
        }
        // Toms tuned under the kick that nothing else was found at (their stick hardly clicks, their
        // level hardly jumps over the song's kicks): onsets in the tom bands with the low tone ringing,
        // where no kick, snare, tom or cymbal was written (a loose kick or snare peak there is the tom's).
        if (!bassTones && !guitar && kickFrames.size() >= LOW_TOM_KICKS) {
            double[] kp = new double[kickFrames.size()];
            for (int i = 0; i < kp.length; i++) kp[i] = lowPitch[kickFrames.get(i).intValue()];
            Arrays.sort(kp);
            double usualKick = kp[kp.length / 2];
            double[] tomBand = new double[kick.length];
            for (int t = 0; t < tomBand.length; t++) tomBand[t] = Math.max(floor[t], Math.max(mid[t], rack[t]));
            normalize(tomBand);
            for (Integer pk : pickPeaks(tomBand, BASE_DELTA / Math.max(0.05, tomSens), 4)) {
                int t = pk.intValue();
                if (t + 8 >= mag.length) continue;
                double time = t * (double) HOP / sr;
                boolean taken = false;
                for (Hit h : hits) if (Math.abs(h.time - time) <= 0.035 && h.note != CLOSED_HAT && h.note != OPEN_HAT && h.note != PEDAL_HAT) taken = true;
                if (taken) continue;
                double pt = lowPitch[t];
                if (pt >= LOW_TOM * usualKick || pt < LOW_TOM_HZ || jump(tomLevel, t) < LOW_TOM_JUMP || wiresLeft(noiseLevel, t) >= TOM_WIRES) continue;
                if (toneRing(mag, t, pt, sr) < LOW_TOM_RING || heldBefore(lowPitch, t, pt)) continue;
                hits.add(new Hit(time, FLOOR_TOM, tomBand[t]));
            }
        }
        rideOrHats(hits, shape, fps, bass, guitar);
        if (!bass) lonelyRides(hits);
        darkCrashes(hits, shape, fps);
        if (cymbals && !bass) riddenCrashes(hits, shape, fps);
        if (cymbals) ringingCrashes(hits, shape, fps);
        if (writeToms && !bassTones && !guitar) ringingToms(hits, lowRaw, ringRaw, lowPitch, fps);
        if (kit != null) kit.sort(hits, audio.samples, sr);
        else if (library != null) library.sort(hits, audio.samples, sr);
        if (writeToms) tomClicks(hits);
        if (snareLeak && cymbals) snareLeaks(hits, mag, sr, fps);
        if (!bass) openRings(hits, shape, hatRaw, hatPeaks, sr);
        if (cymbals && fadedOpens) shortOpens(hits, audio.samples, sr, kit);
        // The kit's own hats, ride and crash (its profile's cymbals) tell the cymbals found apart.
        if (kit != null && cymbals) kit.sortCymbals(hits, audio.samples, sr);
        if (kit != null && cymbals) kit.rideVote(hits, audio.samples, sr);
        if (cymbals && cymModel) CymModel.apply(hits, audio.samples, sr);
        progress(90);
        note("Hits: " + hits.size());
        if (hits.isEmpty()) {
            finish("Failed: no drum hits found in " + inFile.getName() + " (a track without drums, or too quiet: try a lower --sens)");
            return;
        }
        hits.sort(Comparator.comparingDouble(new java.util.function.ToDoubleFunction<Hit>() {
            public double applyAsDouble(Hit h) { return h.time; }
        }));

        double lo, hi;
        if (bpmOverride > 0) {
            lo = hi = bpmOverride;
        } else {
            double coarse = estimateBpm(drums, fps);
            lo = coarse * 0.97;
            hi = coarse * 1.03;
        }
        double[] grid = fitBeatGrid(drums, fps, lo, hi);
        double bpm = grid[0], phaseSec = grid[1];
        note(String.format(java.util.Locale.ROOT, "Tempo: %.2f BPM, first beat near %.3f s", bpm, phaseSec));

        double tickPerSec = bpm / 60.0 * PPQ;
        double gridTicks = quant > 0 ? PPQ * 4.0 / quant : 0;
        double phaseTick = phaseSec * tickPerSec;

        List<MidiEv> events = new ArrayList<MidiEv>();
        int mpq = (int) Math.round(60000000.0 / bpm);
        events.add(new MidiEv(0, metaMsg(0x51, new byte[] {(byte) (mpq >> 16), (byte) (mpq >> 8), (byte) mpq})));
        events.add(new MidiEv(0, metaMsg(0x58, new byte[] {4, 2, 24, 8})));
        events.add(new MidiEv(0, metaMsg(0x03, "Drums (detected)".getBytes("UTF-8"))));
        // The settings travel with the MIDI, so CompareHits can suggest changes to them.
        String stamp = "pulsekit-drummidi; input=" + inFile.getName().replace(';', ',') + "; output=" + outFile.getName().replace(';', ',')
                + "; args=" + given.toString().replace(';', ',') + "; sens=" + num(sens) + "; hat=" + num(hatSens)
                + "; tom=" + num(tomSens) + "; ride=" + num(rideSens) + "; crash=" + num(crashSens)
                + "; bpm=" + (bpmOverride > 0 ? num(bpmOverride) : "auto") + "; quantize=" + (quant > 0 ? Integer.toString(quant) : "off")
                + "; hpss=" + (hpss ? "on" : "off") + "; cymbals=" + (cymbals ? "on" : "off") + (writeToms ? "" : "; toms=off") + (snareLeak ? "; snare-leak=on" : "") + (fadedOpens ? "; faded-opens=on" : "") + "; tempo=" + num(bpm);
        events.add(new MidiEv(0, metaMsg(0x01, stamp.getBytes("UTF-8"))));

        Set<String> seen = new HashSet<String>();
        int kicks = 0, snares = 0, closed = 0, pedals = 0, open = 0, rides = 0, crashes = 0, floors = 0, mids = 0, racks = 0;
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            long tick = Math.round(h.time * tickPerSec);
            if (gridTicks > 0) {
                long steps = Math.round((tick - phaseTick) / gridTicks);
                tick = Math.max(0, Math.round(phaseTick + steps * gridTicks));
            }
            if (!seen.add(h.note + ":" + tick)) continue;
            if (!writeToms && (h.note == FLOOR_TOM || h.note == MID_TOM || h.note == RACK_TOM)) continue;
            int vel = (int) Math.max(40, Math.min(127, 45 + 55 * h.strength));
            int hold = h.note == CRASH_NOTE ? 180 : (h.note == OPEN_HAT || h.note == RIDE_NOTE ? 120 : 24);
            events.add(new MidiEv(tick, shortMsg(0x90, h.note, vel)));
            events.add(new MidiEv(tick + hold, shortMsg(0x80, h.note, 0)));
            if (h.note == KICK_NOTE) kicks++;
            else if (h.note == SNARE_NOTE) snares++;
            else if (h.note == OPEN_HAT) open++;
            else if (h.note == CLOSED_HAT) closed++;
            else if (h.note == PEDAL_HAT) pedals++;
            else if (h.note == RIDE_NOTE) rides++;
            else if (h.note == CRASH_NOTE) crashes++;
            else if (h.note == FLOOR_TOM) floors++;
            else if (h.note == MID_TOM) mids++;
            else if (h.note == RACK_TOM) racks++;
        }
        File written = writeSmf(outFile, events);
        if (written == null || !written.isFile() || written.length() < 14) {
            finish("Failed: MIDI file was not written");
            return;
        }
        made.add(written);
        note(String.format(java.util.Locale.ROOT, "Wrote %s: %d kicks, %d snares, %d closed hats, %d pedal hats, %d open hats, %d rides, %d crashes, %d rack toms, %d mid toms, %d floor toms",
                written.getName(), kicks, snares, closed, pedals, open, rides, crashes, racks, mids, floors) + (writeToms ? "" : " (--no-toms: toms not written)"));
        note("MIDI Extracted to: " + written.getAbsolutePath());
        finish("Succeeded: " + written.getName());
    }

    static final class MidiEv {
        final long tick;
        final byte[] msg;
        MidiEv(long tick, byte[] msg) {
            this.tick = tick;
            this.msg = msg;
        }
    }

    static byte[] shortMsg(int command, int note, int vel) {
        return new byte[] {(byte) ((command & 0xF0) | 9), (byte) (note & 127), (byte) (vel & 127)};
    }

    static byte[] metaMsg(int type, byte[] data) {
        byte[] vlq = vlqBytes(data.length);
        byte[] msg = new byte[2 + vlq.length + data.length];
        msg[0] = (byte) 0xFF;
        msg[1] = (byte) type;
        System.arraycopy(vlq, 0, msg, 2, vlq.length);
        System.arraycopy(data, 0, msg, 2 + vlq.length, data.length);
        return msg;
    }

    static byte[] vlqBytes(int n) {
        byte[] buf = new byte[4];
        int i = 3;
        buf[i] = (byte) (n & 127);
        n >>= 7;
        while (n > 0 && i > 0) {
            i--;
            buf[i] = (byte) ((n & 127) | 128);
            n >>= 7;
        }
        byte[] out = new byte[4 - i];
        System.arraycopy(buf, i, out, 0, out.length);
        return out;
    }

    static void writeVlq(ByteArrayOutputStream out, long value) {
        long v = value < 0 ? 0 : value;
        byte[] buf = new byte[4];
        int i = 3;
        buf[i] = (byte) (v & 127);
        v >>= 7;
        while (v > 0 && i > 0) {
            i--;
            buf[i] = (byte) ((v & 127) | 128);
            v >>= 7;
        }
        out.write(buf, i, 4 - i);
    }

    /** Standard MIDI file, one track, channel 10. No midi.* library. */
    static File writeSmf(File outFile, List<MidiEv> events) throws Exception {
        Collections.sort(events, new Comparator<MidiEv>() {
            public int compare(MidiEv a, MidiEv b) {
                return a.tick < b.tick ? -1 : (a.tick > b.tick ? 1 : 0);
            }
        });
        ByteArrayOutputStream ev = new ByteArrayOutputStream();
        long last = 0;
        for (int i = 0; i < events.size(); i++) {
            MidiEv e = events.get(i);
            long tick = e.tick < last ? last : e.tick;
            writeVlq(ev, tick - last);
            last = tick;
            ev.write(e.msg);
        }
        writeVlq(ev, 0);
        ev.write(new byte[] {(byte) 0xFF, 0x2F, 0});
        byte[] data = ev.toByteArray();
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        all.write(new byte[] {
            'M', 'T', 'h', 'd', 0, 0, 0, 6,
            0, 0, 0, 1,
            (byte) ((PPQ >> 8) & 255), (byte) (PPQ & 255)
        });
        all.write(new byte[] {
            'M', 'T', 'r', 'k',
            (byte) ((data.length >> 24) & 255),
            (byte) ((data.length >> 16) & 255),
            (byte) ((data.length >> 8) & 255),
            (byte) (data.length & 255)
        });
        all.write(data);
        File dest = place(outFile);
        File parent = dest.getParentFile();
        if (parent != null && !parent.isDirectory()) parent.mkdirs();
        FileOutputStream fos = new FileOutputStream(dest);
        try {
            fos.write(all.toByteArray());
        } finally {
            fos.close();
        }
        return dest;
    }

    static File place(File out) throws Exception {
        String path = out.getPath();
        boolean onRoot = path != null && path.startsWith("/") && path.indexOf('/', 1) < 0;
        if (out.isAbsolute() && !onRoot && !"/".equals(path)) return out;
        String dir = null;
        String[] dirs = dirs();
        for (int i = 0; i < dirs.length; i++) {
            String d = dirs[i];
            if (d == null || d.length() < 2 || "/".equals(d) || ".".equals(d)) continue;
            File f = new File(d);
            if (f.isDirectory() && f.canWrite()) { dir = f.getAbsolutePath(); break; }
        }
        if (dir == null) throw new Exception(path + ": open failed: EROFS (Read-only file system)");
        String name = out.getName();
        if (name == null || name.length() == 0 || ".".equals(name) || "..".equals(name)) name = "out.mid";
        return new File(dir, name);
    }

    /** The folders a bare output name may go in, as they were when the run started (now, if run() was called directly). */
    static String[] dirs() {
        if (startDirs != null) return startDirs;
        return new String[] {System.getProperty("pulsekit.work"), System.getProperty("user.dir"), System.getProperty("java.io.tmpdir")};
    }

    static File writtenFile(File outFile) {
        if (outFile.isFile() && outFile.length() > 0) return outFile;
        String[] dirs = dirs();
        for (int i = 0; i < dirs.length; i++) {
            String dir = dirs[i];
            if (dir == null || dir.length() < 2) continue;
            File alt = new File(dir, outFile.getName());
            if (alt.isFile() && alt.length() > 0) return alt;
        }
        return outFile;
    }

    static Audio readAudio(File f) throws Exception {
        byte[] bytes = readFully(new FileInputStream(f));
        String name = f.getName().toLowerCase();
        if (name.endsWith(".mp3") || isId3OrFrame(bytes)) return readMp3(bytes);
        return readWav(bytes);
    }

    static boolean isId3OrFrame(byte[] b) {
        if (b.length >= 3 && b[0] == 'I' && b[1] == 'D' && b[2] == '3') return true;
        return b.length >= 2 && b[0] == (byte) 0xFF && (b[1] & 0xE0) == 0xE0;
    }

    static byte[] readFully(InputStream in) throws Exception {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            in.close();
        }
    }

    static void installJl(Class<?> utils, Class<?> hookType) throws Exception {
        if (utils.getResourceAsStream("/javazoom/jl/decoder/sfd.ser") != null) return;
        Object hook = Proxy.newProxyInstance(hookType.getClassLoader(), new Class<?>[] {hookType}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("getResourceAsStream".equals(method.getName()) && args != null && args.length == 1) {
                    Class<?> tables = Class.forName("JlTables");
                    byte[] body = (byte[]) tables.getMethod("bytes", String.class).invoke(null, args[0]);
                    return body == null ? null : new ByteArrayInputStream(body);
                }
                return null;
            }
        });
        utils.getMethod("setHook", hookType).invoke(null, hook);
    }

    static Audio readMp3(byte[] d) throws Exception {
        Class<?> bitC;
        Class<?> decC;
        try {
            bitC = Class.forName("javazoom.jl.decoder.Bitstream");
            decC = Class.forName("javazoom.jl.decoder.Decoder");
            Class<?> utils = Class.forName("javazoom.jl.decoder.JavaLayerUtils");
            Class<?> hook = Class.forName("javazoom.jl.decoder.JavaLayerHook");
            installJl(utils, hook);
        } catch (ClassNotFoundException missing) {
            throw new Exception("MP3 needs DrumMidi_CRT.jar (PyJav's Java menu). This single file reads WAV.");
        }
        Object bs = bitC.getConstructor(InputStream.class).newInstance(new ByteArrayInputStream(d));
        Object dec = decC.newInstance();
        double[] mono = new double[44100];
        int frames = 0;
        double sr = 44100;
        try {
            Method readFrame = bitC.getMethod("readFrame");
            Method closeFrame = bitC.getMethod("closeFrame");
            Method frequency = Class.forName("javazoom.jl.decoder.Header").getMethod("frequency");
            Method decodeFrame = decC.getMethod("decodeFrame", Class.forName("javazoom.jl.decoder.Header"), bitC);
            while (true) {
                Object h = readFrame.invoke(bs);
                if (h == null) break;
                sr = ((Number) frequency.invoke(h)).doubleValue();
                Object buf = decodeFrame.invoke(dec, h, bs);
                int nch = Math.max(1, ((Number) buf.getClass().getMethod("getChannelCount").invoke(buf)).intValue());
                short[] s = (short[]) buf.getClass().getMethod("getBuffer").invoke(buf);
                int n = ((Number) buf.getClass().getMethod("getBufferLength").invoke(buf)).intValue();
                int got = n / nch;
                if (frames + got > mono.length) {
                    double[] grow = new double[Math.max(frames + got, mono.length * 2)];
                    System.arraycopy(mono, 0, grow, 0, frames);
                    mono = grow;
                }
                for (int i = 0; i < got; i++) {
                    double sum = 0;
                    for (int c = 0; c < nch; c++) sum += s[i * nch + c];
                    mono[frames + i] = sum / (nch * 32768.0);
                }
                frames += got;
                closeFrame.invoke(bs);
            }
        } finally {
            try { bitC.getMethod("close").invoke(bs); } catch (Exception ignored) {}
        }
        if (frames < (int) (sr / 2)) throw new Exception("Could not decode that MP3");
        // Gapless (a LAME / Lavc tag, as Wav2Mp3 and LAME write it): the tag frame's silence, the
        // encoder's delay and the decoder's 529 samples left out, so the hits are where the WAV had them.
        int from = 0, to = frames;
        int[] gap = mp3Gapless(d);
        if (gap != null) {
            int start = gap[0] + gap[1] + 529;
            int length = frames - gap[0] - gap[1] - gap[2];
            if (start < frames && length > 0) {
                from = start;
                to = Math.min(frames, start + length);
            }
        }
        double[] out = new double[to - from];
        System.arraycopy(mono, from, out, 0, to - from);
        Audio a = new Audio();
        a.samples = out;
        a.sampleRate = sr;
        return a;
    }

    /**
     * The first frame's gapless numbers when it is an Info / Xing frame with a LAME (or Lavc) tag:
     * {samples in that frame, encoder delay, padding}; null otherwise. (As Pulsekit's Mp3Decode.)
     */
    static int[] mp3Gapless(byte[] d) {
        int at = 0;
        if (d.length > 10 && d[0] == 'I' && d[1] == 'D' && d[2] == '3') {
            at = 10 + ((d[6] & 0x7f) << 21 | (d[7] & 0x7f) << 14 | (d[8] & 0x7f) << 7 | (d[9] & 0x7f));
            if ((d[5] & 0x10) != 0) at += 10;
        }
        for (int limit = Math.min(d.length - 4, at + 8192); at < limit; at++) {
            if ((d[at] & 0xff) == 0xff && (d[at + 1] & 0xe0) == 0xe0) break;
        }
        if (at + 4 > d.length || (d[at] & 0xff) != 0xff) return null;
        int version = (d[at + 1] >> 3) & 3;
        int layer = (d[at + 1] >> 1) & 3;
        if (layer != 1) return null;
        boolean mono = ((d[at + 3] >> 6) & 3) == 3;
        int side = version == 3 ? (mono ? 17 : 32) : (mono ? 9 : 17);
        int x = at + 4 + side;
        if (x + 8 > d.length) return null;
        String id = new String(d, x, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (!id.equals("Info") && !id.equals("Xing")) return null;
        int flags = (d[x + 7] & 0xff) | (d[x + 6] & 0xff) << 8;
        int lame = x + 8 + ((flags & 1) != 0 ? 4 : 0) + ((flags & 2) != 0 ? 4 : 0) + ((flags & 4) != 0 ? 100 : 0) + ((flags & 8) != 0 ? 4 : 0);
        if (lame + 24 > d.length) return null;
        String enc = new String(d, lame, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (!enc.equals("LAME") && !enc.equals("Lavc") && !enc.equals("Lavf")) return null;
        int delay = (d[lame + 21] & 0xff) << 4 | (d[lame + 22] & 0xff) >> 4;
        int padding = (d[lame + 22] & 0x0f) << 8 | (d[lame + 23] & 0xff);
        return new int[] {version == 3 ? 1152 : 576, delay, padding};
    }

    static Audio readWav(byte[] b) throws Exception {
        if (b.length < 12 || b[0] != 'R' || b[1] != 'I' || b[2] != 'F' || b[3] != 'F') {
            throw new Exception("Could not read audio data");
        }
        int channels = 1, bits = 16, rate = 44100, format = 1;
        byte[] data = null;
        int pos = 12;
        while (pos + 8 <= b.length) {
            String id = new String(b, pos, 4, "ISO-8859-1");
            int size = u32(b, pos + 4);
            int start = pos + 8;
            if (size < 0 || start > b.length) break;
            if (start + size > b.length) size = b.length - start;
            if ("fmt ".equals(id) && size >= 16) {
                format = u16(b, start);
                channels = u16(b, start + 2);
                rate = u32(b, start + 4);
                bits = u16(b, start + 14);
            } else if ("data".equals(id)) {
                data = new byte[size];
                System.arraycopy(b, start, data, 0, size);
            }
            pos = start + size + (size & 1);
        }
        if (data == null || channels < 1 || rate < 1000) throw new Exception("WAV has no PCM data");
        int bytesPer = Math.max(1, bits / 8);
        int frames = data.length / (bytesPer * channels);
        double[] mono = new double[frames];
        for (int i = 0; i < frames; i++) {
            double s = 0;
            for (int c = 0; c < channels; c++) {
                int o = (i * channels + c) * bytesPer;
                s += sample(data, o, format, bits);
            }
            mono[i] = s / channels;
        }
        Audio a = new Audio();
        a.samples = mono;
        a.sampleRate = rate;
        return a;
    }

    static double sample(byte[] d, int o, int format, int bits) {
        if (format == 3 && bits == 32 && o + 4 <= d.length) {
            int bits32 = (d[o] & 255) | ((d[o + 1] & 255) << 8) | ((d[o + 2] & 255) << 16) | (d[o + 3] << 24);
            return Float.intBitsToFloat(bits32);
        }
        if (bits == 8) return ((d[o] & 255) - 128) / 128.0;
        if (bits == 24 && o + 3 <= d.length) {
            int v = (d[o] & 255) | ((d[o + 1] & 255) << 8) | (d[o + 2] << 16);
            return v / 8388608.0;
        }
        if (o + 2 > d.length) return 0;
        int v = (short) ((d[o] & 255) | (d[o + 1] << 8));
        return v / 32768.0;
    }

    static int u16(byte[] b, int i) {
        return (b[i] & 255) | ((b[i + 1] & 255) << 8);
    }

    static int u32(byte[] b, int i) {
        return (b[i] & 255) | ((b[i + 1] & 255) << 8) | ((b[i + 2] & 255) << 16) | ((b[i + 3] & 255) << 24);
    }

    /**
     * --sounds: drum sounds measured one by one (Pulsekit's drum sound library). Each line of the list is
     * a sound file (WAV, or MP3 in the jar); each line written is its path, then "ok" and its measures,
     * tab-separated: length, peak (dBFS), attack (ms to the loudest 5 ms), decay to -20 and -40 dB of
     * that (ms), the strongest pitch at 25-400 Hz in its first 150 ms (a kick's or tom's), the brightness
     * (spectral centre of its first 100 ms, Hz), the kit profile's spectrum (KIT_BANDS at 40-800 Hz, as
     * --learn-kit reads a kick or tom) and the cymbal look (cymFeature: CYM_BANDS at 2.5-16 kHz, its fade
     * and level), each comma-separated; or "fail" and why. All from where the sound starts.
     */
    static final class Sounds {
        private Sounds() {}

        static void measure(File list, File out) throws Exception {
            List<String> lines = new ArrayList<String>();
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new FileInputStream(list), "UTF-8"));
            try {
                for (String line; (line = r.readLine()) != null; ) if (line.trim().length() > 0) lines.add(line.trim());
            } finally {
                r.close();
            }
            StringBuilder sb = new StringBuilder();
            int ok = 0;
            for (String path : lines) {
                String row;
                try {
                    row = path + "\tok\t" + one(new File(path));
                    ok++;
                } catch (Throwable ex) {
                    row = path + "\tfail\t" + String.valueOf(ex.getMessage() == null ? ex.toString() : ex.getMessage()).replace('\t', ' ').replace('\n', ' ');
                }
                sb.append(row).append('\n');
            }
            java.io.Writer w = new java.io.OutputStreamWriter(new FileOutputStream(out), "UTF-8");
            try {
                w.write(sb.toString());
            } finally {
                w.close();
            }
            finish("Measured " + ok + " of " + lines.size() + " sounds: " + out.getName());
        }

        /** The hits' index in the folder --cut-hits writes. */
        static final String INDEX = "hits.txt";
        /** A hit quieter than this share of the render's peak is a drum the kit does not play: not cut. */
        static final double CUT_SILENT = 0.02;

        /** The library's part for a note (as Pulsekit's drum sound library names them). */
        static String part(int note) {
            switch (note) {
                case 35: case 36: return "kick";
                case 38: case 40: return "snare";
                case 37: return "rim";
                case 39: return "clap";
                case 41: case 43: return "floor tom";
                case 45: case 47: return "mid tom";
                case 48: case 50: return "high tom";
                case 42: return "closed hat";
                case 44: return "pedal hat";
                case 46: return "open hat";
                case 51: case 59: return "ride";
                case 53: return "ride bell";
                case 52: return "china";
                case 55: return "splash";
                case 49: case 57: return "crash";
                default: return "other";
            }
        }

        /**
         * vst_render's test beat cut into its hits (--cut-hits): each note of its times file (the kick, toms
         * and cymbals; cymbals alone, with a kick or snare, and played on in eighths) as a WAV of its own in
         * `outdir`, named by its part and how it was played, and INDEX: each one's file, part, how it was
         * played ("alone", "kick", "snare", "groove") and --sounds' measures, its spectrum and cymbal look
         * read where it starts in the whole render (the ring before taken off), as DrumMidi reads a song.
         */
        static void cut(File render, File times, File outdir) throws Exception {
            Audio a = readAudio(render);
            double sr = a.sampleRate;
            double[] x = a.samples;
            double top = 0;
            for (double v : x) top = Math.max(top, Math.abs(v));
            if (top <= 0) {
                finish("Failed: the test beat is silent");
                return;
            }
            List<double[]> ev = new ArrayList<double[]>();
            List<String> tags = new ArrayList<String>();
            for (String line : new String(java.nio.file.Files.readAllBytes(times.toPath()), "UTF-8").split("\n")) {
                String[] w = line.trim().split("\\s+");
                if (w.length < 2 || w[0].startsWith("#")) continue;
                ev.add(new double[] {Double.parseDouble(w[0]), Integer.parseInt(w[1])});
                tags.add(w.length > 2 ? w[2] : "");
            }
            // The render's offset (the cymbals alone tell it): a hit in eighths is read at its note plus it.
            List<Double> shifts = new ArrayList<Double>();
            for (int i = 0; i < ev.size(); i++) {
                if (!tags.get(i).isEmpty() || !isCymbalNote((int) ev.get(i)[1])) continue;
                double on = KitProfile.onset(x, sr, ev.get(i)[0]);
                if (on >= 0) shifts.add(Double.valueOf(on - ev.get(i)[0]));
            }
            Collections.sort(shifts);
            double shift = shifts.isEmpty() ? 0 : shifts.get(shifts.size() / 2).doubleValue();
            if (!outdir.isDirectory() && !outdir.mkdirs()) throw new java.io.IOException("cannot make " + outdir);
            StringBuilder index = new StringBuilder("# vst_render test beat hits (DrumMidi --cut-hits " + render.getName()
                + "): file, part, how it was played, then --sounds' measures (spectrum and look read in the whole render)\n");
            java.util.Map<String, Integer> count = new java.util.HashMap<String, Integer>();
            int cut = 0, quiet = 0;
            for (int i = 0; i < ev.size(); i++) {
                int note = (int) ev.get(i)[1];
                String tag = tags.get(i);
                String how = tag.equals("k") ? "kick" : tag.equals("s") ? "snare" : tag.equals("g") ? "groove" : "alone";
                double on = tag.equals("g") ? ev.get(i)[0] + shift : KitProfile.onset(x, sr, ev.get(i)[0]);
                if (on < 0) {
                    quiet++;
                    continue;
                }
                double len = isCymbalNote(note) ? (tag.equals("g") ? 0.24 : 0.95) : 0.48;
                if (i + 1 < ev.size()) len = Math.min(len, ev.get(i + 1)[0] - ev.get(i)[0] - 0.01);
                int s0 = Math.max(0, (int) Math.round(on * sr)), n = Math.min(x.length - s0, (int) Math.round(len * sr));
                if (n < sr * 0.05) continue;
                double peak = 0;
                for (int k = s0; k < s0 + n; k++) peak = Math.max(peak, Math.abs(x[k]));
                if (peak < CUT_SILENT * top) {
                    quiet++;
                    continue;
                }
                String part = part(note), key = part + " " + how;
                int c = count.containsKey(key) ? count.get(key).intValue() + 1 : 1;
                count.put(key, Integer.valueOf(c));
                File f = new File(outdir, String.format(java.util.Locale.ROOT, "%s %02d.wav", key, c));
                writeWav16(f, java.util.Arrays.copyOfRange(x, s0, s0 + n), (int) sr);
                String[] m = one(f).split("\t");
                double[] spec = KitProfile.spectrum(x, sr, on), look = cymFeature(x, sr, on);
                if (spec != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < KIT_BANDS; k++) sb.append(k == 0 ? "" : ",").append(fmt(spec[k]));
                    m[7] = sb.toString();
                }
                if (look != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < CYM_BANDS + 2; k++) sb.append(k == 0 ? "" : ",").append(fmt(look[k]));
                    m[8] = sb.toString();
                }
                index.append(f.getName()).append('\t').append(part).append('\t').append(how);
                for (String v : m) index.append('\t').append(v);
                index.append('\n');
                cut++;
            }
            java.io.Writer w = new java.io.OutputStreamWriter(new FileOutputStream(new File(outdir, INDEX)), "UTF-8");
            try {
                w.write(index.toString());
            } finally {
                w.close();
            }
            finish("Cut " + cut + " hits into " + outdir.getName() + " (" + INDEX + ")" + (quiet > 0 ? "; " + quiet + " not played by the kit (too quiet)" : ""));
        }

        /** A mono 16-bit WAV. */
        static void writeWav16(File f, double[] y, int rate) throws Exception {
            byte[] b = new byte[44 + y.length * 2];
            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            bb.put("RIFF".getBytes("US-ASCII")).putInt(36 + y.length * 2).put("WAVEfmt ".getBytes("US-ASCII")).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16).put("data".getBytes("US-ASCII")).putInt(y.length * 2);
            for (double v : y) bb.putShort((short) Math.max(-32768, Math.min(32767, Math.round(v * 32767))));
            FileOutputStream o = new FileOutputStream(f);
            try {
                o.write(b);
            } finally {
                o.close();
            }
        }

        static String one(File f) throws Exception {
            Audio a = readAudio(f);
            double sr = a.sampleRate;
            double[] x = a.samples;
            int win = Math.max(1, (int) Math.round(0.005 * sr));
            int nw = x.length / win;
            if (nw < 2) throw new IllegalArgumentException("too short");
            double[] env = new double[nw];
            double top = 0;
            int topAt = 0;
            for (int w = 0; w < nw; w++) {
                double s = 0;
                for (int i = w * win; i < (w + 1) * win; i++) s += x[i] * x[i];
                env[w] = Math.sqrt(s / win);
                if (env[w] > top) {
                    top = env[w];
                    topAt = w;
                }
            }
            if (top < 1e-6) throw new IllegalArgumentException("silent");
            int start = 0;
            while (start < topAt && env[start] < top * 0.01) start++;
            double peak = 0;
            for (double v : x) peak = Math.max(peak, Math.abs(v));
            int d20 = -1, d40 = -1;
            for (int w = nw - 1; w > topAt; w--) {
                if (d40 < 0 && env[w] >= top * 0.01) d40 = w;
                if (d20 < 0 && env[w] >= top * 0.1) d20 = w;
            }
            double ms = 1000.0 * win / sr;
            double t0 = start * win / sr;
            // Room after the sound for the windows the profile reads.
            double[] padded = java.util.Arrays.copyOf(x, x.length + (int) sr);
            double[] spec = KitProfile.spectrum(padded, sr, t0);
            double[] look = cymFeature(padded, sr, t0, false);
            StringBuilder sb = new StringBuilder();
            sb.append(fmt(x.length * 1000.0 / sr)).append('\t').append(fmt(20 * Math.log10(peak))).append('\t').append(fmt((topAt - start) * ms)).append('\t')
                .append(fmt(d20 < 0 ? 0 : (d20 - topAt + 1) * ms)).append('\t').append(fmt(d40 < 0 ? 0 : (d40 - topAt + 1) * ms)).append('\t')
                .append(fmt(pitch(x, sr, start * win))).append('\t').append(fmt(bright(x, sr, start * win))).append('\t');
            for (int k = 0; k < KIT_BANDS; k++) sb.append(k == 0 ? "" : ",").append(spec == null ? "0" : fmt(spec[k]));
            sb.append('\t');
            for (int k = 0; k < CYM_BANDS + 2; k++) sb.append(k == 0 ? "" : ",").append(look == null ? "0" : fmt(look[k]));
            return sb.toString();
        }

        /** The strongest frequency at 25-400 Hz (1 Hz steps) in the first 150 ms (Hann window). */
        static double pitch(double[] x, double sr, int from) {
            int n = Math.min(x.length - from, (int) (0.15 * sr));
            if (n < 64) return 0;
            double best = 0, at = 0;
            for (int hz = 25; hz <= 400; hz++) {
                double w = 2 * Math.PI * hz / sr, c = Math.cos(w), sn = Math.sin(w), re = 0, im = 0, cr = 1, ci = 0;
                for (int i = 0; i < n; i++) {
                    double v = x[from + i] * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1)));
                    re += v * cr;
                    im -= v * ci;
                    double t = cr * c - ci * sn;
                    ci = cr * sn + ci * c;
                    cr = t;
                }
                double m = re * re + im * im;
                if (m > best) {
                    best = m;
                    at = hz;
                }
            }
            return at;
        }

        /** The spectral centre (Hz) of the first 100 ms. */
        static double bright(double[] x, double sr, int from) {
            int n = Math.min(x.length - from, (int) (0.1 * sr));
            if (n < 64) return 0;
            double[] part = java.util.Arrays.copyOfRange(x, from, from + Math.max(n, FFT_SIZE * 2));
            float[][] mag = spectrogram(part, sr);
            double num = 0, den = 0;
            int frames = Math.max(1, Math.min(mag.length, n / HOP + 1));
            for (int f = 0; f < frames; f++) {
                for (int b = 1; b < mag[f].length; b++) {
                    double hz = b * sr / FFT_SIZE;
                    num += hz * mag[f][b];
                    den += mag[f][b];
                }
            }
            return den <= 0 ? 0 : num / den;
        }

        static String fmt(double v) {
            return String.format(java.util.Locale.ROOT, "%.4g", v);
        }
    }

    static float[][] spectrogram(double[] x, double sr) {
        int maxBin = Math.min(FFT_SIZE / 2, (int) Math.round(MAX_HZ * FFT_SIZE / sr));
        int nFrames = x.length / HOP + 1;
        float[][] mag = new float[nFrames][maxBin + 1];
        double[] win = new double[FFT_SIZE];
        for (int i = 0; i < FFT_SIZE; i++) win[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / FFT_SIZE);
        double[] re = new double[FFT_SIZE], im = new double[FFT_SIZE];
        for (int t = 0; t < nFrames; t++) {
            if ((t & 1023) == 0) checkStop();
            int start = t * HOP - FFT_SIZE / 2;
            for (int j = 0; j < FFT_SIZE; j++) {
                int idx = start + j;
                re[j] = (idx >= 0 && idx < x.length) ? x[idx] * win[j] : 0;
                im[j] = 0;
            }
            fft(re, im);
            for (int k = 0; k <= maxBin; k++) mag[t][k] = (float) Math.sqrt(re[k] * re[k] + im[k] * im[k]);
        }
        return mag;
    }

    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double tr = re[i]; re[i] = re[j]; re[j] = tr;
                double ti = im[i]; im[i] = im[j]; im[j] = ti;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            int half = len / 2, step = n / len;
            for (int i = 0; i < n; i += len) {
                for (int k = 0; k < half; k++) {
                    double wr = COS[k * step], wi = SIN[k * step];
                    int a = i + k, b = a + half;
                    double tr = re[b] * wr - im[b] * wi;
                    double ti = re[b] * wi + im[b] * wr;
                    re[b] = re[a] - tr;
                    im[b] = im[a] - ti;
                    re[a] += tr;
                    im[a] += ti;
                }
            }
        }
    }

    static void applyHpss(float[][] mag) {
        int T = mag.length, F = mag[0].length, h = HPSS_KERNEL / 2;
        float[][] out = new float[T][F];
        float[] buf = new float[HPSS_KERNEL];
        for (int t = 0; t < T; t++) {
            if ((t & 255) == 0) checkStop();
            for (int f = 0; f < F; f++) {
                for (int i = -h; i <= h; i++) buf[i + h] = mag[t][clamp(f + i, 0, F - 1)];
                Arrays.sort(buf);
                float p = buf[h];
                for (int i = -h; i <= h; i++) buf[i + h] = mag[clamp(t + i, 0, T - 1)][f];
                Arrays.sort(buf);
                float hm = buf[h];
                float p2 = p * p, h2 = hm * hm;
                out[t][f] = mag[t][f] * p2 / (p2 + h2 + 1e-12f);
            }
        }
        for (int t = 0; t < T; t++) mag[t] = out[t];
    }

    static int bin(double hz, double sr, int maxBin) {
        return Math.max(0, Math.min(maxBin, (int) Math.round(hz * FFT_SIZE / sr)));
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static double[] flux(float[][] mag, int lo, int hi) {
        double[] o = new double[mag.length];
        // Before the first frame is silence, so a hit right at the start rises like any other.
        for (int t = 0; t < mag.length; t++) {
            double s = 0;
            for (int k = lo; k <= hi; k++) {
                double d = Math.log1p(LOG_GAMMA * mag[t][k]) - (t > 0 ? Math.log1p(LOG_GAMMA * mag[t - 1][k]) : 0);
                if (d > 0) s += d;
            }
            o[t] = s / (hi - lo + 1);
        }
        return o;
    }

    /** Mean magnitude in a band. Used to see whether a hi-hat rings (open) or dies (closed). */
    static double[] bandMean(float[][] mag, int lo, int hi) {
        double[] o = new double[mag.length];
        int n = Math.max(1, hi - lo + 1);
        for (int t = 0; t < mag.length; t++) {
            double s = 0;
            for (int k = lo; k <= hi; k++) s += mag[t][k];
            o[t] = s / n;
        }
        return o;
    }

    /**
     * An open hat openHat misses: a short open hat (Pulsekit's, gone by 150 ms) or one under a
     * kick, a snare or guitars. 8 frames (~90 ms) after its peak the 7-14 kHz band (before HPSS)
     * keeps OPEN_TOP of its rise and the 3-7 kHz band OPEN_MID, and the hat band OPEN_BAND at
     * frames 8-10; a closed hat has died by then. The hat band must not swell on after the peak
     * (OPEN_SWELL at frame 4): a room or reverb rising after a closed hat does.
     */
    /**
     * The closed hats left after the ride and hat passages are sorted (openRing): open where they
     * ring, but not among rides (two within RIDE_NEAR seconds: a ride rings like an open hat), nor
     * in the second after a crash (its wash rings on over the hats). Not where a bass line plays.
     */
    static void openRings(List<Hit> hits, CymbalShape sh, double[] energy, List<Integer> peaks, double sr) {
        List<Double> rides = new ArrayList<Double>();
        for (Hit h : hits) if (h.note == RIDE_NOTE) rides.add(Double.valueOf(h.time));
        List<Double> crashes = new ArrayList<Double>();
        for (Hit h : hits) if (h.note == CRASH_NOTE) crashes.add(Double.valueOf(h.time));
        for (int i = 0; i < peaks.size(); i++) {
            double time = peaks.get(i).intValue() * (double) HOP / sr;
            int at = -1;
            for (int h = 0; h < hits.size(); h++) if (hits.get(h).note == CLOSED_HAT && hits.get(h).time == time) at = h;
            if (at < 0) continue;
            int near = 0;
            for (Double r : rides) if (Math.abs(r.doubleValue() - time) <= RIDE_NEAR) near++;
            boolean washed = false;
            for (Double c : crashes) if (time - c.doubleValue() >= -0.02 && time - c.doubleValue() <= RIDE_NEAR) washed = true;
            if (near >= 2 || washed || !openRing(sh, energy, peaks, i)) continue;
            Hit x = hits.get(at);
            hits.set(at, new Hit(x.time, OPEN_HAT, x.strength));
        }
    }

    static boolean openRing(CymbalShape sh, double[] energy, List<Integer> peaks, int index) {
        int t = peaks.get(index).intValue(), n = energy.length;
        int next = index + 1 < peaks.size() ? peaks.get(index + 1).intValue() : n;
        if (next - t < 10 || t + 10 >= n) return false;
        int b = t;
        for (int k = t; k <= Math.min(sh.hi.length - 1, t + 2); k++) if (sh.hi[k] > sh.hi[b]) b = k;
        int q = b + 8, pr = Math.max(0, t - 3);
        if (q >= sh.hi.length) return false;
        double hiRise = sh.hi[b] - sh.hi[pr], midRise = sh.ride[b] - sh.ride[pr];
        if (hiRise < 1e-9 || midRise < 1e-9) return false;
        if (sh.hi[q] - sh.hi[pr] < OPEN_TOP * hiRise || sh.ride[q] - sh.ride[pr] < OPEN_MID * midRise) return false;
        double pre = Math.min(energy[Math.max(0, t - 3)], energy[Math.max(0, t - 2)]);
        double peak = Math.max(energy[t], energy[Math.min(n - 1, t + 1)]) - pre;
        if (peak < 1e-6) return false;
        if (energy[t + 4] - pre > OPEN_SWELL * peak) return false;
        double band = 0;
        int c = 0;
        for (int k = t + 8; k <= Math.min(next - 2, t + 10); k += 2) {
            band += energy[k] - pre;
            c++;
        }
        return c > 0 && band / c >= OPEN_BAND * peak;
    }

    /**
     * Open hats ring in the top band. A hat that is hit again within ~90 ms is closed,
     * because an open hat is left to sound. The ring is measured in the raw band level
     * (HPSS removes it) above the level just before the hit, so cymbal wash does not count.
     */
    static boolean openHat(double[] energy, List<Integer> peaks, int index) {
        int t = peaks.get(index).intValue();
        if (index + 1 < peaks.size()) {
            int next = peaks.get(index + 1).intValue();
            if (next - t < 8) return false;
        }
        int n = energy.length;
        double pre = Math.min(energy[Math.max(0, t - 3)], energy[Math.max(0, t - 2)]);
        double peak = Math.max(energy[t], energy[Math.min(n - 1, t + 1)]) - pre;
        if (peak < 1e-6) return false;
        int a = Math.min(n - 1, t + 5);
        int b = Math.min(n - 1, t + 9);
        double tail = 0;
        int count = 0;
        for (int i = a; i <= b; i++) {
            tail += energy[i];
            count++;
        }
        tail /= Math.max(1, count);
        if (tail - pre <= peak * OPEN_HAT_TAIL) return false;
        // A room or reverb keeps a closed hat up 60-100 ms on; an open hat still rings 115-185 ms on
        // (frames 10-16, up to the next hit). A hat hit again before then is taken as closed.
        int next = index + 1 < peaks.size() ? peaks.get(index + 1).intValue() : n;
        double late = 0;
        int lc = 0;
        for (int i = t + 10; i <= Math.min(n - 1, Math.min(next - 2, t + 16)); i++) {
            late += energy[i];
            lc++;
        }
        return lc > 0 && late / lc - pre > peak * OPEN_HAT_LATE;
    }

    /**
     * Open hats that fade like a closed hat are closed hats (shortOpens): a hat heard alone whose look
     * (cymFeature) fades under OPEN_FADE_ALONE, or, with a kit profile, is nearer its learned closed hat
     * than its learned open hat by more than OPEN_NEAR_ALONE; a hat on one snare that fades under
     * OPEN_FADE_SNARE (the snare's ring keeps the hat band up after a closed hat). On a kick, or with
     * more drums at it, open and closed hats look alike: left as they are. Only with --faded-opens.
     * On the 7.11. kit tests (122 songs each, their MP3s, kit profiles) the closed hats written open
     * fell 303 -> 135 (KVLT), 348 -> 177 (Artillery), 204 -> 147 (Anarchy), Riot's 18 -> 17, for 42,
     * 58, 20 and 6 real open hats lost: hats right +127, +114, +61, -5. Stricter cuts (fade -0.9 and
     * -1.5, nearer -0.4) lost fewer open hats (18, 23, 12, 3) but gained half as much (+158 in all).
     */
    static final double OPEN_FADE_ALONE = -0.6, OPEN_NEAR_ALONE = -0.3, OPEN_FADE_SNARE = -1.2;

    static void shortOpens(List<Hit> hits, double[] x, double sr, KitProfile kit) {
        double[] closedLook = kit == null ? null : kit.look(CLOSED_HAT), openLook = kit == null ? null : kit.look(OPEN_HAT);
        int closed = 0;
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            if (h.note != OPEN_HAT) continue;
            int others = 0;
            boolean snare = false;
            for (Hit o : hits) {
                if (o == h || Math.abs(o.time - h.time) >= CYM_ALONE) continue;
                others++;
                snare = o.note == SNARE_NOTE;
            }
            if (others > 1 || (others == 1 && !snare)) continue;
            double[] f = cymFeature(x, sr, h.time);
            if (f == null) continue;
            double fade = f[CYM_BANDS];
            boolean shut;
            if (others == 0) {
                boolean nearer = closedLook != null && openLook != null && cymNear(f, openLook) - cymNear(f, closedLook) < OPEN_NEAR_ALONE;
                shut = fade < OPEN_FADE_ALONE || nearer;
            } else {
                shut = fade < OPEN_FADE_SNARE;
            }
            if (!shut) continue;
            hits.set(i, new Hit(h.time, CLOSED_HAT, h.strength));
            closed++;
        }
        if (closed > 0) note("Open hats that fade as closed ones: " + closed + " made closed");
    }

    /** A ride keeps ringing in the mid-high band well after an open hat would have died. */
    static boolean rideRings(double[] energy, int t) {
        double now = energy[t];
        if (now < 1e-6) return false;
        int a = Math.min(energy.length - 1, t + 14);
        int b = Math.min(energy.length - 1, t + 32);
        if (b <= a) return false;
        return tailFloor(energy, a, b) > now * 0.22;
    }

    /** A crash hits the ride band and the top band together, and both keep washing. */
    static boolean isCrash(double[] ride, double[] top, double[] rideEnergy, double[] topEnergy, int t) {
        double r = ride[t], h = top[t];
        if (r < 0.58 || h < 0.58) return false;
        double hi = Math.max(r, h), lo = Math.min(r, h);
        if (lo < hi * 0.62) return false;
        return crashWash(rideEnergy, t) && crashWash(topEnergy, t);
    }

    /** The crash has to jump in. A cymbal that is already ringing is not a new hit. */
    static boolean crashAttack(double[] ride, double[] top, int t) {
        if (t < 4) return true;
        double prev = 0;
        for (int i = t - 4; i < t; i++) prev += ride[i] + top[i];
        prev /= 8.0;
        double now = (ride[t] + top[t]) / 2.0;
        return now > prev * 1.45;
    }

    /** Wash still loud from about 180 ms to 500 ms. Hats and snares die before that. */
    static boolean crashWash(double[] energy, int t) {
        double now = energy[t];
        if (now < 1e-6) return false;
        int a = Math.min(energy.length - 1, t + 16);
        int b = Math.min(energy.length - 1, t + 42);
        if (b <= a) return false;
        return tailFloor(energy, a, b) > now * 0.30;
    }

    /**
     * The quietest a band gets over frames a..b (three-frame means). A cymbal's wash stays up the
     * whole time; hats and snares die between their hits, so the next hit in the window does not
     * make them look like they ring.
     */
    static double tailFloor(double[] energy, int a, int b) {
        double floor = Double.MAX_VALUE;
        for (int i = a; i <= b; i++) {
            int c = Math.min(b, i + 2);
            double s = 0;
            for (int k = i; k <= c; k++) s += energy[k];
            floor = Math.min(floor, s / (c - i + 1));
        }
        return floor;
    }

    /** Mean log rise over two frames in a band of the raw spectrum. */
    static double[] rise(float[][] mag, int lo, int hi) {
        double[] o = new double[mag.length];
        for (int t = 2; t < mag.length; t++) {
            double s = 0;
            for (int k = lo; k <= hi; k++) {
                s += Math.log1p(LOG_GAMMA * mag[t][k]) - Math.log1p(LOG_GAMMA * mag[t - 2][k]);
            }
            o[t] = s / (hi - lo + 1);
        }
        return o;
    }

    /**
     * How much a band's level (bandMean, normalized) rose at an onset: its peak over frames t-1..t+2
     * above its level three frames before. A ringing note before it does not count, and a rise out
     * of near silence counts as little as it is loud.
     */
    static double jump(double[] level, int t) {
        double peak = 0;
        for (int k = Math.max(0, t - 1); k <= Math.min(level.length - 1, t + 2); k++) peak = Math.max(peak, level[k]);
        return Math.max(0, peak - (t >= 3 ? level[t - 3] : 0));
    }

    /**
     * A snare: its body band rises, by more than a kick's own body would with the kick band's rise,
     * and its wires ring on (a tom has the body without them). Or, where a band covers the body, a
     * noise onset whose wires ring.
     */
    static boolean isSnare(Levels lv, int t, double sens, boolean rideRinging) {
        return bodySnare(lv, t, sens) || maskedSnare(lv, t, sens, rideRinging);
    }

    /** The body shows: it jumps, more than with a kick, as a tone, and the wires ring (or a hat hides them). */
    static boolean bodySnare(Levels lv, int t, double sens) {
        double jb = jump(lv.body, t);
        double wires = wiresLeft(lv.noise, t), jk = jump(lv.kick, t);
        return (jb >= SNARE_JUMP / sens || jb >= GHOST_JUMP / sens && wires >= WIRES_BAND) && jb >= SNARE_OVER_KICK * jk
                && (wires >= WIRES_BAND || (wires >= WIRES || jump(lv.hat, t) >= HAT_WITH) && !masked(lv, t) && (jb >= WEAK_WIRES_OVER_KICK * jk || jump(lv.hat, t) >= BRIGHT_SNARE))
                && jump(lv.bodyRaw, t) >= (lv.bass ? BODY_TONE_BAND : BODY_TONE) * jump(lv.gapRaw, t);
    }

    /**
     * A band already sounds in the body band before the onset (and a bass line plays: a roomy kit
     * alone also keeps the body band up between its hits).
     */
    static boolean masked(Levels lv, int t) {
        return lv.bass && t >= 3 && lv.body[t - 3] >= MASKED_BODY;
    }

    /**
     * A kick over a bass line: the bass keeps the kick band up, so the kick's own rise there can be
     * small (KICK_JUMP_BAND), but its beater clicks in the 2-6 kHz band (KICK_CLICK_JUMP; a bass
     * note under a hat rises 0.05-0.15 there, a lone ride up to 0.46, kicks 0.67 and more). A kick
     * band jump of KICK_JUMP_STRONG needs no click (a kick without a beater sound).
     */
    static boolean bandKick(double kickJump, double noiseJump) {
        return kickJump >= KICK_JUMP_STRONG || kickJump >= KICK_JUMP_BAND && noiseJump >= KICK_CLICK_JUMP;
    }

    /** A band covers the body: a noise onset whose rise rings like wires. */
    static boolean maskedSnare(Levels lv, int t, double sens, boolean rideRinging) {
        // A ride ringing on keeps the noise band up after a kick as wires would.
        if (!masked(lv, t) || rideRinging) return false;
        double wires = wiresLeft(lv.noise, t);
        // A crash washes on after the wires would have died.
        if (jump(lv.noise, t) < WIRES_NOISE_JUMP / sens || wires > WIRES_MAX || left(lv.noise, t, 13, 20) > WIRES_LATE) return false;
        double hat = jump(lv.hat, t);
        // Wires that reach far into the hat band need ring less.
        if (hat >= BRIGHT_WIRES_HAT / sens) return wires >= BRIGHT_WIRES;
        return wires >= WIRES_BAND && (lv.noise[t - 3] < RINGING_NOISE || hat >= MASKED_HAT / sens);
    }

    /** Band levels before HPSS, for telling the drums apart at an onset. */
    static final class Levels {
        final double[] kick, body, noise, bodyRaw, gapRaw, hat;
        /** The song has a bass line (bassLine). */
        final boolean bass;

        Levels(double[] kick, double[] body, double[] noise, double[] bodyRaw, double[] gapRaw, double[] hat) {
            this.hat = hat;
            this.bass = bassLine(kick);
            this.kick = kick;
            this.body = body;
            this.noise = noise;
            this.bodyRaw = bodyRaw;
            this.gapRaw = gapRaw;
        }
    }

    /**
     * Kicks found whose low pitch is TOM_OVER_KICK times the kick pitch become floor toms. The
     * song's kick pitch is the lower quartile of them (kicks are the lowest, and a fill can have
     * nearly as many floor toms). Not called where a bass line plays: its notes would be read instead.
     */
    static void floorToms(List<Hit> hits, List<Integer> kickFrames, double[] lowPitch, double[] kickLevel, double[] noiseLevel, double sr, float[][] mag, boolean[] snareAt, SnareBody loudSnare, boolean guitar) {
        if (kickFrames.size() < 4) return;
        double[] p = new double[kickFrames.size()];
        for (int i = 0; i < p.length; i++) p[i] = lowPitch[kickFrames.get(i).intValue()];
        double[] sorted = p.clone();
        Arrays.sort(sorted);
        double kickPitch = sorted[sorted.length / 4];
        double usualKick = sorted[sorted.length / 2];
        // A song where most kicks read high carry a note held from before (guitars over the drums,
        // HELD_SONG of them, at least 5): there every kick's tom pitch is checked on what is new at it.
        int high = 0, held = 0;
        for (int i = 0; i < p.length; i++) {
            if (p[i] < TOM_OVER_KICK * kickPitch) continue;
            high++;
            if (heldBefore(lowPitch, kickFrames.get(i).intValue(), p[i])) held++;
        }
        boolean heldSong = held >= 5 && held >= HELD_SONG * high;
        // What is new at each kick: its pitch, and the song's usual one (the median).
        double[] rise = new double[p.length];
        for (int i = 0; i < p.length; i++) rise[i] = risePitch(mag, kickFrames.get(i).intValue(), sr);
        double[] rs = rise.clone();
        Arrays.sort(rs);
        double usualRise = rs[rs.length / 2];
        // A fill's kicks: the half with the most low end (see FILL_TOM_UNDER).
        double[] low = new double[p.length];
        for (int i = 0; i < p.length; i++) low[i] = lowEnd(mag, kickFrames.get(i).intValue(), p[i], sr);
        double[] ls = low.clone();
        Arrays.sort(ls);
        double lowMid = ls[ls.length / 2];
        List<Double> ap = new ArrayList<Double>(), ar = new ArrayList<Double>(), al = new ArrayList<Double>();
        for (int i = 0; i < p.length; i++) {
            if (low[i] < lowMid) continue;
            ap.add(Double.valueOf(p[i]));
            ar.add(Double.valueOf(rise[i]));
            al.add(Double.valueOf(low[i]));
        }
        double fillPitch = median(ap), fillRise = median(ar), fillLow = median(al);
        for (int i = 0; i < p.length; i++) {
            int t = kickFrames.get(i).intValue();
            // A tom whose body reads as low as the kick (some kits tune them so) rises well above the
            // song's kicks in what is new at it (TOM_RISE_PITCH of their median; RISE_KICKS kicks at least).
            // A kick read at its third partial, in its pitch or in what is new at it, stays a kick;
            // so does one at a pitch many of the song's kicks read.
            if (p.length < RISE_KICKS) {
                int signs = (p[i] < FILL_TOM_UNDER * fillPitch ? 1 : 0) + (p[i] > FILL_TOM_OVER * fillPitch ? 1 : 0) + (rise[i] >= FILL_TOM_RISE * fillRise ? 1 : 0) + (low[i] < FILL_TOM_LOW * fillLow ? 1 : 0);
                if (signs >= FILL_TOM_SIGNS)
                    floorTom(hits, t * (double) HOP / sr);
                continue;
            }
            if (overtoneKick(mag, t, p[i], kickPitch, sr) || overtoneKick(mag, t, rise[i], kickPitch, sr)) continue;
            // A tom tuned under the kick.
            if (!guitar && p.length >= LOW_TOM_KICKS && p[i] < LOW_TOM * usualKick && p[i] >= LOW_TOM_HZ && !snareAt[t]
                && wiresLeft(noiseLevel, t) < TOM_WIRES && !sharedPitch(p, p[i], KICK_MOST) && toneRing(mag, t, p[i], sr) >= LOW_TOM_RING
                && !heldBefore(lowPitch, t, p[i])) {
                floorTom(hits, t * (double) HOP / sr);
                continue;
            }
            if (sharedPitch(p, p[i]) && lowEnd(mag, t, p[i], sr) >= KICK_SHARE_SUB) continue;
            // A pitch most of the song's kicks read is the kick's with less of a low end than
            // KICK_SHARE_SUB asks (a kick processed so its second partial rings over the
            // fundamental: 0.28-0.47 there), but more than toms have (KICK_MOST_SUB; 0.06-0.24).
            if (sharedPitch(p, p[i], KICK_MOST) && lowEnd(mag, t, p[i], sr) >= KICK_MOST_SUB) continue;
            // Wires ringing at a kick are a snare struck with it; where no snare was found there and
            // the low end is weak (TOM_LOW_END; a kick keeps it), the snare's wires buzz with a tom.
            boolean wires = wiresLeft(noiseLevel, t) >= TOM_WIRES && (snareAt[t] || lowEnd(mag, t, p[i], sr) >= TOM_LOW_END);
            // Ringing wires and the song's snare body at a kick: a snare struck with the kick that the
            // snare search missed. The kick stays, and the snare is written too.
            if (!wires && wiresLeft(noiseLevel, t) >= TOM_WIRES && loudSnare.ghost(mag, t)) {
                hits.add(new Hit(t * (double) HOP / sr, SNARE_NOTE, 1.0));
                continue;
            }
            if (p.length >= RISE_KICKS && rise[i] >= TOM_RISE_PITCH * usualRise && rise[i] >= FLOOR_TOM_HZ && !wires) {
                floorTom(hits, t * (double) HOP / sr);
                continue;
            }
            if (p[i] < TOM_OVER_KICK * kickPitch || p[i] < FLOOR_TOM_HZ || wires) continue;
            // The same pitch already sounded just before: a guitar's note held over the kick, not a tom's.
            // Then the pitch of what is new at the hit decides.
            if ((heldSong || heldBefore(lowPitch, t, p[i])) && rise[i] < TOM_OVER_KICK * kickPitch) continue;
            floorTom(hits, t * (double) HOP / sr);
        }
    }

    /** How much of its level at `pitch` a hit keeps 8 frames on (a tom's tone rings, a kick's thump dies). */
    static double toneRing(float[][] mag, int t, double pitch, double sr) {
        int maxBin = mag[t].length - 1, pb = bin(pitch, sr, maxBin), a = Math.min(mag.length - 1, t + 1), b = Math.min(mag.length - 1, t + 8);
        double at = 0, later = 0;
        for (int k = Math.max(1, pb - 1); k <= Math.min(maxBin, pb + 1); k++) {
            at = Math.max(at, mag[a][k]);
            later = Math.max(later, mag[b][k]);
        }
        return at > 1e-12 ? later / at : 0;
    }

    /** Whether KICK_SHARE of the pitches (KICK_SHARE_MIN at least) are within KICK_SAME of `pitch`. */
    static boolean sharedPitch(double[] pitches, double pitch) {
        return sharedPitch(pitches, pitch, KICK_SHARE);
    }

    static boolean sharedPitch(double[] pitches, double pitch, double share) {
        int near = 0;
        for (double q : pitches) if (Math.abs(q - pitch) <= KICK_SAME * pitch) near++;
        return pitches.length >= KICK_SHARE_HITS && near >= Math.max(KICK_SHARE_MIN, share * pitches.length);
    }

    /** The level under 70 Hz at a hit, in units of the level at its pitch (a kick keeps it, a tom has little). */
    static double lowEnd(float[][] mag, int t, double pitch, double sr) {
        double sub = 0, at = 0;
        for (int k = t; k <= Math.min(mag.length - 1, t + 3); k++) {
            int maxBin = mag[k].length - 1;
            for (int b = bin(30, sr, maxBin); b <= bin(70, sr, maxBin); b++) sub = Math.max(sub, mag[k][b]);
            for (int b = bin(pitch * 0.85, sr, maxBin); b <= bin(pitch * 1.15, sr, maxBin); b++) at = Math.max(at, mag[k][b]);
        }
        return at > 1e-9 ? sub / at : 0;
    }

    /** Snares in rolls and fast fills (see ROLL_FRAMES), next to the snares found, added until no more are. */
    static void rollSnares(Levels lv, float[][] mag, double[] snare, List<Integer> notSnares, List<Integer> snarePeaks, List<Double> snareBodies, boolean[] near,
            List<Hit> hits, double[] bodyLevel, double[] kickLevel, double sens, double sr) {
        SnareBody one = new SnareBody(mag, new ArrayList<Hit>(), sr);
        for (boolean more = true; more; ) {
            more = false;
            for (Integer p : notSnares) {
                int t = p.intValue();
                if (near[t]) continue;
                double jb = jump(bodyLevel, t);
                if (jb < SNARE_JUMP / sens || jb < SNARE_OVER_KICK * jump(kickLevel, t) || jump(lv.bodyRaw, t) < BODY_TONE * jump(lv.gapRaw, t)) continue;
                boolean like = false;
                for (Integer q : snarePeaks) {
                    int s = q.intValue();
                    if (s != t && Math.abs(s - t) <= ROLL_FRAMES && one.alike(mag, t, s)) {
                        like = true;
                        break;
                    }
                }
                if (!like) continue;
                mark(near, t, 2);
                snarePeaks.add(p);
                snareBodies.add(Double.valueOf(jb));
                hits.add(new Hit(t * (double) HOP / sr, SNARE_NOTE, snare[t]));
                more = true;
            }
        }
    }

    /** Hats at a tom found are its stick's click (see TOM_CLICK). */
    static void tomClicks(List<Hit> hits) {
        List<Double> toms = new ArrayList<Double>();
        for (Hit h : hits) if (h.note == FLOOR_TOM || h.note == MID_TOM || h.note == RACK_TOM) toms.add(Double.valueOf(h.time));
        if (toms.isEmpty()) return;
        for (java.util.Iterator<Hit> it = hits.iterator(); it.hasNext(); ) {
            Hit h = it.next();
            if (h.note != CLOSED_HAT && h.note != OPEN_HAT) continue;
            for (Double t : toms) {
                if (Math.abs(t.doubleValue() - h.time) <= TOM_CLICK) {
                    it.remove();
                    break;
                }
            }
        }
    }

    static double median(List<Double> v) {
        if (v.isEmpty()) return 0;
        List<Double> c = new ArrayList<Double>(v);
        Collections.sort(c);
        return c.get(c.size() / 2).doubleValue();
    }

    /** Snares found whose body reads well under the song's snares' (see SNARE_TOM_PITCH) become toms. */
    static void snareToms(List<Hit> hits, double[] lowPitch, double[] kickLevel, double sr) {
        List<Double> ps = new ArrayList<Double>();
        for (Hit h : hits) if (h.note == SNARE_NOTE) ps.add(Double.valueOf(lowPitch[frame(h.time, sr, lowPitch.length)]));
        if (ps.size() < SNARE_TOM_SNARES) return;
        Collections.sort(ps);
        double usual = ps.get(ps.size() / 2).doubleValue();
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            if (h.note != SNARE_NOTE) continue;
            int t = frame(h.time, sr, lowPitch.length);
            double p = lowPitch[t];
            if (p < SNARE_TOM_PITCH * usual && p >= SNARE_TOM_HZ && jump(kickLevel, t) < SNARE_TOM_KICK) hits.set(i, new Hit(h.time, MID_TOM, h.strength));
        }
    }

    static int frame(double time, double sr, int n) {
        return Math.max(0, Math.min(n - 1, (int) Math.round(time * sr / HOP)));
    }

    /**
     * A kit's own kick and toms (vst_render plays each of them alone in its test beat; --learn-kit
     * keeps their sound): the 40-800 Hz spectrum of each, KIT_BANDS log-spaced bands over the first
     * KIT_FRAMES frames of a KIT_N window (a fine look at the low end), in log level, its mean taken
     * off. A kick or tom found takes the note whose spectrum is nearest its own: in a fill most of
     * the "kicks" can be toms, and the song alone does not say what this kit's kick sounds like.
     */
    static final int KIT_BANDS = 16, KIT_FRAMES = 2, KIT_N = 4096;
    static final double KIT_LO = 40, KIT_HI = 800;
    static final double[] KIT_WINDOW = new double[KIT_N];
    static {
        for (int n = 0; n < KIT_N; n++) KIT_WINDOW[n] = 0.5 - 0.5 * Math.cos(2 * Math.PI * n / (KIT_N - 1));
    }
    /**
     * A tom in the test beat under KIT_SILENT of the kick's level (40-800 Hz) is not played by this kit.
     * 1%: a metal kit's kick is loud below 800 Hz, so toms that ring can stay under 5% of it (KVLT's
     * were all left out at 5%); a note a kit does not play is far quieter (only the other mics' bleed).
     */
    static final double KIT_SILENT = 0.01;

    /**
     * The kit profile's cymbals (the test beat's closed hat, open hat, ride and crash, each alone):
     * the 2.5-16 kHz spectrum at the hit, CYM_BANDS log-spaced bands in log level with their mean
     * taken off, and how fast it fades (the band's level 70-90 ms after the hit over its level at the
     * hit, in log). A cymbal found takes the nearest learned kind, when it is clearly nearer
     * (CYM_MARGIN) than the kind it was written as: how this kit's hats, ride and crash sound, not
     * the fixed ringing rules, tells them apart.
     */
    static final int CYM_BANDS = 12, CYM_N = 1024;
    static final double CYM_LO = 2500, CYM_HI = 16000;
    /** A fade's weight against the spectrum's likeness (-1..1) in telling cymbals apart. */
    static final double CYM_DECAY_WEIGHT = 0.3;
    /**
     * How much nearer another learned cymbal must be for a hit to change to it (0.08 moved too many
     * of Anarchy's closed hats to its ride), and how near it must be at least.
     */
    static final double CYM_MARGIN = 0.25, CYM_LEAST = 0.5;
    /** Two learned cymbals this alike (cymNear) are not told apart in this kit: neither changes to the other. */
    static final double CYM_ALIKE = 0.4;
    /** KitProfile.openLikeCrash: the open hat's and crash's looks this near (Riot 0.78; other kits 0.07-0.43). */
    static final double OPEN_CRASH_ALIKE = 0.6;
    /** Another hit this near (a cymbal, a kick, a snare, a tom): the sound is not the cymbal alone, and it is not changed. */
    static final double CYM_ALONE = 0.04;
    /** KitProfile.rideVote: the passage (seconds each way) and how many cymbals heard alone in it. */
    static final double RIDE_VOTE_WINDOW = 2.0;
    static final int RIDE_VOTE_SEEN = 4;
    /** KitProfile.rideVote: a profile's ride or hats cut is used where both its values are under this. */
    static final double RIDE_CUT_LOWER = -0.1;
    /** A test beat cymbal under this share of the loudest one is not played by the kit. */
    static final double CYM_SILENT = 0.01;
    static final int[] CYM_NOTES = {CLOSED_HAT, OPEN_HAT, RIDE_NOTE, CRASH_NOTE};

    static boolean isCymbalNote(int n) {
        for (int c : CYM_NOTES) if (c == n) return true;
        return false;
    }

    static String cymName(int n) {
        return n == CLOSED_HAT ? "closed hat" : n == OPEN_HAT ? "open hat" : n == RIDE_NOTE ? "ride" : n == CRASH_NOTE ? "crash" : String.valueOf(n);
    }

    /** One CYM_N window's band magnitudes (CYM_BANDS of them, log-spaced in CYM_LO..CYM_HI) at `start`; null past the end. */
    static double[] cymWindow(double[] x, double sr, int start) {
        if (start < 0 || start + CYM_N > x.length) return null;
        double[] re = new double[CYM_N], im = new double[CYM_N];
        for (int n = 0; n < CYM_N; n++) re[n] = x[start + n] * (0.5 - 0.5 * Math.cos(2 * Math.PI * n / (CYM_N - 1)));
        fft(re, im);
        double hi = Math.min(CYM_HI, sr / 2 * 0.95);
        double[] band = new double[CYM_BANDS];
        int[] count = new int[CYM_BANDS];
        for (int b = 1; b < CYM_N / 2; b++) {
            double hz = b * sr / CYM_N;
            if (hz < CYM_LO || hz >= hi) continue;
            int k = (int) Math.floor(CYM_BANDS * Math.log(hz / CYM_LO) / Math.log(hi / CYM_LO));
            k = Math.max(0, Math.min(CYM_BANDS - 1, k));
            band[k] += Math.sqrt(re[b] * re[b] + im[b] * im[b]);
            count[k]++;
        }
        for (int k = 0; k < CYM_BANDS; k++) band[k] = count[k] > 0 ? band[k] / count[k] : 0;
        return band;
    }

    /**
     * A cymbal hit's look at `time`: CYM_BANDS spectrum values (log, mean off, unit length), its fade
     * (index CYM_BANDS) and its level (index CYM_BANDS + 1); null where the audio ends or is silent.
     */
    static double[] cymFeature(double[] x, double sr, double time) {
        return cymFeature(x, sr, time, true);
    }

    /**
     * cymFeature, with what rang before the hit taken off or not: in a song a hat's own ring from
     * the hat before is taken off too, which leaves its look brighter than the test beat's hats
     * (heard a second apart); without that, a song's hats look like the learned closed hat.
     */
    static double[] cymFeature(double[] x, double sr, double time, boolean takeBefore) {
        int at = (int) Math.round(time * sr);
        double[][] w = {cymWindow(x, sr, at), cymWindow(x, sr, at + CYM_N / 4), cymWindow(x, sr, at + (int) (0.07 * sr)), cymWindow(x, sr, at + (int) (0.09 * sr))};
        for (double[] v : w) if (v == null) return null;
        // What rang already just before the hit (a crash's wash, an open hat): taken off, so the look
        // is the hit's own rise and fade, not the cymbals before it.
        double[] before = takeBefore ? cymWindow(x, sr, at - CYM_N - (int) (0.005 * sr)) : null;
        double[] out = new double[CYM_BANDS + 2];
        double e1 = 0, e2 = 0, mean = 0;
        for (int k = 0; k < CYM_BANDS; k++) {
            double pre = before == null ? 0 : before[k];
            double now = (w[0][k] + w[1][k]) / 2;
            double v = Math.max(now - pre, 0.05 * now);
            e1 += v;
            e2 += Math.max((w[2][k] + w[3][k]) / 2 - pre, 0.02 * now);
            out[k] = Math.log(v + 1e-12);
            mean += out[k] / CYM_BANDS;
        }
        if (e1 <= 0) return null;
        double norm = 0;
        for (int k = 0; k < CYM_BANDS; k++) {
            out[k] -= mean;
            norm += out[k] * out[k];
        }
        norm = Math.sqrt(norm);
        if (norm <= 0) return null;
        for (int k = 0; k < CYM_BANDS; k++) out[k] /= norm;
        out[CYM_BANDS] = Math.log((e2 + 1e-12) / e1);
        out[CYM_BANDS + 1] = e1;
        return out;
    }

    /** How near a cymbal hit's look is to a learned one: the spectra's likeness less the fades' difference. */
    static double cymNear(double[] a, double[] b) {
        double s = 0;
        for (int k = 0; k < CYM_BANDS; k++) s += a[k] * b[k];
        return s - CYM_DECAY_WEIGHT * Math.abs(a[CYM_BANDS] - b[CYM_BANDS]);
    }

    static final class KitProfile {
        final String name;
        final List<Integer> notes = new ArrayList<Integer>();
        final List<double[]> spectra = new ArrayList<double[]>();
        /** The learned cymbals (lines "c<note> ..."): their notes and looks (cymFeature, without the level). */
        final List<Integer> cymNotes = new ArrayList<Integer>();
        final List<double[]> cymLooks = new ArrayList<double[]>();
        /**
         * The learned cymbals heard with a kick ("c<note>k") or a snare ("c<note>s"), from the test beat's
         * cymbals struck with them: a cymbal found on a kick or snare is told apart by these. Also the
         * cymbals played on in eighths ("c<note>g"), for rideVote.
         */
        final List<Integer> withNotes = new ArrayList<Integer>();
        final List<Character> withTags = new ArrayList<Character>();
        final List<double[]> withLooks = new ArrayList<double[]>();
        /** The ride or hats cut learned from the runs ("ridecut a b"; null when not learned): see rideVote. */
        double[] rideCut;

        KitProfile(String name) {
            this.name = name;
        }

        /** The spectrum (see KIT_BANDS) of the sound starting at `time`, and its level (index KIT_BANDS); null where the audio ends. */
        static double[] spectrum(double[] x, double sr, double time) {
            int start = (int) Math.round(time * sr);
            if (start < 0 || start + (KIT_FRAMES - 1) * HOP + KIT_N > x.length) return null;
            double[] edges = new double[KIT_BANDS + 1];
            for (int i = 0; i <= KIT_BANDS; i++) edges[i] = KIT_LO * Math.pow(KIT_HI / KIT_LO, i / (double) KIT_BANDS);
            int b0 = (int) Math.floor(KIT_LO * KIT_N / sr), b1 = (int) Math.ceil(KIT_HI * KIT_N / sr);
            double[] band = new double[KIT_BANDS];
            int[] count = new int[KIT_BANDS];
            for (int f = 0; f < KIT_FRAMES; f++) {
                int a = start + f * HOP;
                for (int b = b0; b <= b1; b++) {
                    double hz = b * sr / KIT_N;
                    int k = 0;
                    while (k < KIT_BANDS - 1 && hz >= edges[k + 1]) k++;
                    if (hz < edges[0] || hz >= edges[KIT_BANDS]) continue;
                    // One bin of the window's DFT, its phase turned on step by step.
                    double re = 0, im = 0, w = 2 * Math.PI * b / KIT_N, c = Math.cos(w), sn = Math.sin(w), cr = 1, ci = 0;
                    for (int n = 0; n < KIT_N; n++) {
                        double v = x[a + n] * KIT_WINDOW[n];
                        re += v * cr;
                        im -= v * ci;
                        double t = cr * c - ci * sn;
                        ci = cr * sn + ci * c;
                        cr = t;
                    }
                    band[k] += Math.sqrt(re * re + im * im);
                    count[k]++;
                }
            }
            double[] out = new double[KIT_BANDS + 1];
            double level = 0, mean = 0;
            for (int k = 0; k < KIT_BANDS; k++) {
                double v = count[k] > 0 ? band[k] / count[k] : 0;
                level += v;
                out[k] = Math.log(v + 1e-12);
                mean += out[k] / KIT_BANDS;
            }
            double norm = 0;
            for (int k = 0; k < KIT_BANDS; k++) {
                out[k] -= mean;
                norm += out[k] * out[k];
            }
            norm = Math.sqrt(norm);
            if (norm <= 0) return null;
            for (int k = 0; k < KIT_BANDS; k++) out[k] /= norm;
            out[KIT_BANDS] = level;
            return out;
        }

        /** Where the hit of a note at `time` starts: the first sample within 0.45 s of it (from 20 ms
         * before) at a third of that stretch's peak, less 5 ms; -1 where it is silent or past the end. */
        static double onset(double[] x, double sr, double time) {
            int a = Math.max(0, (int) Math.round((time - 0.02) * sr)), b = Math.min(x.length, (int) Math.round((time + 0.45) * sr));
            double peak = 0;
            for (int i = a; i < b; i++) peak = Math.max(peak, Math.abs(x[i]));
            if (peak <= 0) return -1;
            for (int i = a; i < b; i++) if (Math.abs(x[i]) >= peak / 3) return Math.max(time - 0.02, i / sr - 0.005);
            return time;
        }

        static double like(double[] a, double[] b) {
            double s = 0;
            for (int k = 0; k < KIT_BANDS; k++) s += a[k] * b[k];
            return s;
        }

        /** --learn-kit: the kick's and toms' spectra from the test beat's render, its notes' times in `times`. */
        static void learn(File render, File times, File out) throws Exception {
            Audio audio = readAudio(render);
            List<Integer> order = new ArrayList<Integer>();
            java.util.Map<Integer, double[]> sum = new java.util.HashMap<Integer, double[]>();
            java.util.Map<Integer, Integer> count = new java.util.HashMap<Integer, Integer>();
            // Notes with no sound to read at their hits: past the render's end, or digital silence there.
            java.util.Map<Integer, String> unread = new java.util.LinkedHashMap<Integer, String>();
            double renderSec = audio.samples.length / (double) audio.sampleRate;
            double late = 0;
            // The cymbals (closed hat, open hat, ride, crash) learned alongside.
            java.util.Map<Integer, double[]> cymSum = new java.util.TreeMap<Integer, double[]>();
            java.util.Map<Integer, Integer> cymCount = new java.util.HashMap<Integer, Integer>();
            java.util.Set<Integer> cymUnread = new java.util.LinkedHashSet<Integer>();
            // A cymbal struck with a kick ("k" after its note) or a snare ("s"): its look then, apart.
            java.util.Map<String, double[]> withSum = new java.util.TreeMap<String, double[]>();
            java.util.Map<String, Integer> withCount = new java.util.HashMap<String, Integer>();
            // Cymbals played on in eighths ("g"): each hit over the ring of the one before, as in a song. Its
            // look is read at the note's time plus the render's offset (the cymbals alone tell it): in a
            // run of hits the onset search would find the next one.
            List<double[]> groove = new ArrayList<double[]>();
            // The runs' hits read (all but the first of each): note, and its look with and without the ring
            // before taken off, for the ride or hats cut (rideCut).
            List<Integer> runNotes = new ArrayList<Integer>();
            List<double[][]> runLooks = new ArrayList<double[][]>();
            List<Double> cymShifts = new ArrayList<Double>();
            for (String line : new String(java.nio.file.Files.readAllBytes(times.toPath()), "UTF-8").split("\n")) {
                String[] w = line.trim().split("\\s+");
                if (w.length < 2 || w[0].startsWith("#")) continue;
                int note = Integer.parseInt(w[1]);
                String tag = w.length > 2 ? w[2].trim() : "";
                if (isCymbalNote(note) && (tag.equals("k") || tag.equals("s"))) {
                    // A cymbal struck with a kick or snare: its look at the note (both start there).
                    double wat = Double.parseDouble(w[0]);
                    double won = onset(audio.samples, audio.sampleRate, wat);
                    double[] f = won < 0 ? null : cymFeature(audio.samples, audio.sampleRate, won);
                    if (f == null) continue;
                    String wk = note + tag;
                    if (!withSum.containsKey(wk)) {
                        withSum.put(wk, new double[CYM_BANDS + 2]);
                        withCount.put(wk, Integer.valueOf(0));
                    }
                    double[] ws = withSum.get(wk);
                    for (int k = 0; k < CYM_BANDS + 2; k++) ws[k] += f[k];
                    withCount.put(wk, Integer.valueOf(withCount.get(wk).intValue() + 1));
                    continue;
                }
                if (isCymbalNote(note) && tag.equals("g")) {
                    // A cymbal played on in eighths: read after the loop, where the render's offset is known.
                    groove.add(new double[] {Double.parseDouble(w[0]), note});
                    continue;
                }
                if (tag.length() > 0) continue;
                if (isCymbalNote(note)) {
                    // A cymbal alone: its look where its sound starts.
                    double cat = Double.parseDouble(w[0]);
                    double con = onset(audio.samples, audio.sampleRate, cat);
                    double[] f = con < 0 ? null : cymFeature(audio.samples, audio.sampleRate, con);
                    if (con >= 0) cymShifts.add(Double.valueOf(con - cat));
                    Integer ck = Integer.valueOf(note);
                    if (f == null) {
                        if (!cymSum.containsKey(ck)) cymUnread.add(ck);
                        continue;
                    }
                    cymUnread.remove(ck);
                    if (!cymSum.containsKey(ck)) {
                        cymSum.put(ck, new double[CYM_BANDS + 2]);
                        cymCount.put(ck, Integer.valueOf(0));
                    }
                    double[] cs = cymSum.get(ck);
                    for (int k = 0; k < CYM_BANDS + 2; k++) cs[k] += f[k];
                    cymCount.put(ck, Integer.valueOf(cymCount.get(ck).intValue() + 1));
                    continue;
                }
                if (note != KICK_NOTE && !isTomNote(note)) continue;
                double at = Double.parseDouble(w[0]);
                // The hit as the plugin plays it: where its sound starts, up to 0.45 s after the note
                // (a plugin may play some drums late), not the note's own time.
                double onset = onset(audio.samples, audio.sampleRate, at);
                if (onset - at > late) late = onset - at;
                double[] v = onset < 0 ? null : spectrum(audio.samples, audio.sampleRate, onset);
                if (v == null) {
                    if (!sum.containsKey(Integer.valueOf(note)) && !unread.containsKey(Integer.valueOf(note)))
                        unread.put(Integer.valueOf(note), at + 0.2 > renderSec ? "after the render's end (" + num(renderSec) + " s)" : "no sound at " + num(at) + " s");
                    continue;
                }
                unread.remove(Integer.valueOf(note));
                Integer key = Integer.valueOf(note);
                if (!sum.containsKey(key)) {
                    sum.put(key, new double[KIT_BANDS + 1]);
                    count.put(key, Integer.valueOf(0));
                    order.add(key);
                }
                double[] s = sum.get(key);
                for (int k = 0; k <= KIT_BANDS; k++) s[k] += v[k];
                count.put(key, Integer.valueOf(count.get(key).intValue() + 1));
            }
            if (!groove.isEmpty()) {
                Collections.sort(cymShifts);
                double shift = cymShifts.isEmpty() ? 0 : cymShifts.get(cymShifts.size() / 2).doubleValue();
                for (double[] g : groove) {
                    // The first of a run has no ring before it: not as in a song, left out.
                    boolean after = false;
                    for (double[] o : groove) if (o[1] == g[1] && o[0] < g[0] && g[0] - o[0] < 0.3) after = true;
                    if (!after) continue;
                    double[] f = cymFeature(audio.samples, audio.sampleRate, Math.max(0, g[0] + shift));
                    if (f == null) continue;
                    double[] fn = cymFeature(audio.samples, audio.sampleRate, Math.max(0, g[0] + shift), false);
                    if (fn != null) {
                        runNotes.add(Integer.valueOf((int) g[1]));
                        runLooks.add(new double[][] {f, fn});
                    }
                    String gk = (int) g[1] + "g";
                    if (!withSum.containsKey(gk)) {
                        withSum.put(gk, new double[CYM_BANDS + 2]);
                        withCount.put(gk, Integer.valueOf(0));
                    }
                    double[] gs = withSum.get(gk);
                    for (int k = 0; k < CYM_BANDS + 2; k++) gs[k] += f[k];
                    withCount.put(gk, Integer.valueOf(withCount.get(gk).intValue() + 1));
                }
            }
            Integer kick = Integer.valueOf(KICK_NOTE);
            if (!sum.containsKey(kick)) {
                finish("Failed: the test beat has no kick to learn (36)");
                return;
            }
            double kickLevel = sum.get(kick)[KIT_BANDS] / count.get(kick).intValue();
            StringBuilder sb = new StringBuilder("# DrumMidi kit profile (--learn-kit " + render.getName() + "): note, then its " + KIT_BANDS
                + " band spectrum " + num(KIT_LO) + "-" + num(KIT_HI) + " Hz\n");
            List<String> kept = new ArrayList<String>(), left = new ArrayList<String>();
            for (Integer n : order) {
                double[] s = sum.get(n);
                int c = count.get(n).intValue();
                if (n.intValue() != KICK_NOTE && s[KIT_BANDS] / c < KIT_SILENT * kickLevel) {
                    left.add(n + " (" + String.format(java.util.Locale.ROOT, "%.1f", 100 * s[KIT_BANDS] / c / kickLevel) + "% of the kick)");
                    continue;
                }
                double norm = 0;
                for (int k = 0; k < KIT_BANDS; k++) norm += s[k] * s[k];
                norm = Math.sqrt(norm);
                sb.append(n);
                for (int k = 0; k < KIT_BANDS; k++) sb.append(' ').append(String.format(java.util.Locale.ROOT, "%.4f", s[k] / norm));
                sb.append('\n');
                kept.add(n + (c > 1 ? " (" + c + " hits)" : ""));
            }
            // The cymbals: "c<note>", its spectrum and its fade.
            double loudest = 0;
            for (java.util.Map.Entry<Integer, double[]> e : cymSum.entrySet()) loudest = Math.max(loudest, e.getValue()[CYM_BANDS + 1] / cymCount.get(e.getKey()).intValue());
            List<String> cymKept = new ArrayList<String>(), cymLeft = new ArrayList<String>();
            java.util.Map<Integer, double[]> keptLooks = new java.util.HashMap<Integer, double[]>();
            if (!cymSum.isEmpty()) sb.append("# cymbals: c<note>, its ").append(CYM_BANDS).append(" band spectrum ").append(num(CYM_LO)).append("-").append(num(CYM_HI))
                .append(" Hz, then how fast it fades (log level 70-90 ms after the hit over at it)\n");
            for (java.util.Map.Entry<Integer, double[]> e : cymSum.entrySet()) {
                double[] cs = e.getValue();
                int c = cymCount.get(e.getKey()).intValue();
                if (cs[CYM_BANDS + 1] / c < CYM_SILENT * loudest) {
                    cymLeft.add(e.getKey() + " " + cymName(e.getKey().intValue()) + " (" + String.format(java.util.Locale.ROOT, "%.1f", 100 * cs[CYM_BANDS + 1] / c / loudest)
                        + "% of the loudest cymbal)");
                    continue;
                }
                double norm = 0;
                for (int k = 0; k < CYM_BANDS; k++) norm += cs[k] * cs[k];
                norm = Math.sqrt(norm);
                sb.append('c').append(e.getKey());
                for (int k = 0; k < CYM_BANDS; k++) sb.append(' ').append(String.format(java.util.Locale.ROOT, "%.4f", cs[k] / norm));
                sb.append(' ').append(String.format(java.util.Locale.ROOT, "%.4f", cs[CYM_BANDS] / c)).append('\n');
                cymKept.add(e.getKey() + " " + cymName(e.getKey().intValue()) + (c > 1 ? " (" + c + " hits)" : ""));
                double[] look = new double[CYM_BANDS + 1];
                for (int k = 0; k < CYM_BANDS; k++) look[k] = Double.parseDouble(String.format(java.util.Locale.ROOT, "%.4f", cs[k] / norm));
                look[CYM_BANDS] = Double.parseDouble(String.format(java.util.Locale.ROOT, "%.4f", cs[CYM_BANDS] / c));
                keptLooks.put(e.getKey(), look);
            }
            for (Integer n : cymUnread) if (!cymSum.containsKey(n)) cymLeft.add(n + " " + cymName(n.intValue()) + " (no sound at its hits)");
            // The cymbals with a kick or snare: kept for the cymbals heard alone too (a cymbal the kit does not
            // play would be the kick or snare alone).
            List<String> withKept = new ArrayList<String>(), grooveKept = new ArrayList<String>();
            for (java.util.Map.Entry<String, double[]> e : withSum.entrySet()) {
                String key = e.getKey();
                int wn = Integer.parseInt(key.substring(0, key.length() - 1));
                boolean heard = false;
                for (String k2 : cymKept) if (k2.startsWith(wn + " ")) heard = true;
                if (!heard) continue;
                double[] ws = e.getValue();
                int c = withCount.get(key).intValue();
                double norm = 0;
                for (int k = 0; k < CYM_BANDS; k++) norm += ws[k] * ws[k];
                norm = Math.sqrt(norm);
                if (norm <= 0) continue;
                sb.append('c').append(key);
                for (int k = 0; k < CYM_BANDS; k++) sb.append(' ').append(String.format(java.util.Locale.ROOT, "%.4f", ws[k] / norm));
                sb.append(' ').append(String.format(java.util.Locale.ROOT, "%.4f", ws[CYM_BANDS] / c)).append('\n');
                if (key.endsWith("g")) grooveKept.add(wn + " (" + c + " hits)");
                else withKept.add(wn + (key.endsWith("k") ? " with kick" : " with snare"));
            }
            // The ride or hats cut: where the runs' hats and rides, heard as in a song, part on rideVote's two
            // scores (the middle between their means), from the looks just written.
            String cutSaid = "";
            double[] rideLook = keptLooks.get(Integer.valueOf(RIDE_NOTE)), hatLook = keptLooks.get(Integer.valueOf(CLOSED_HAT));
            if (rideLook != null && hatLook != null) {
                double[] sumH = new double[2], sumR = new double[2];
                int nH = 0, nR = 0;
                for (int i = 0; i < runNotes.size(); i++) {
                    double[][] fl = runLooks.get(i);
                    double a = cymNear(fl[0], rideLook) - cymNear(fl[0], hatLook), b = cymNear(fl[0], rideLook) - cymNear(fl[1], hatLook);
                    if (runNotes.get(i).intValue() == CLOSED_HAT) {
                        sumH[0] += a;
                        sumH[1] += b;
                        nH++;
                    } else if (runNotes.get(i).intValue() == RIDE_NOTE) {
                        sumR[0] += a;
                        sumR[1] += b;
                        nR++;
                    }
                }
                if (nH > 0 && nR > 0) {
                    double c1 = (sumH[0] / nH + sumR[0] / nR) / 2, c2 = (sumH[1] / nH + sumR[1] / nR) / 2;
                    sb.append("# ride or hats cut: where the hat and ride played on in eighths part on the ride vote's two scores\n");
                    sb.append(String.format(java.util.Locale.ROOT, "ridecut %.4f %.4f\n", c1, c2));
                    cutSaid = String.format(java.util.Locale.ROOT, "; ride or hats cut %.2f, %.2f", c1, c2);
                }
            }
            java.nio.file.Files.write(out.toPath(), sb.toString().getBytes("UTF-8"));
            List<String> none = new ArrayList<String>();
            for (java.util.Map.Entry<Integer, String> e : unread.entrySet()) if (!sum.containsKey(e.getKey())) none.add(e.getKey() + " (" + e.getValue() + ")");
            finish("Wrote " + out.getName() + ": kick and toms " + String.join(", ", kept) + (left.isEmpty() ? "" : "; silent in this kit: " + String.join(", ", left))
                + (none.isEmpty() ? "" : "; nothing to read: " + String.join(", ", none))
                + (late > 0.03 ? "; hits up to " + Math.round(late * 1000) + " ms after their notes" : "")
                + (kept.size() > 1 ? "" : "; no tom played: does the plugin's MIDI note map have its toms on 41-48 (or give a --map)?")
                + (cymKept.isEmpty() ? "" : "; cymbals " + String.join(", ", cymKept))
                + (cymLeft.isEmpty() ? "" : "; cymbals not heard: " + String.join(", ", cymLeft))
                + (withKept.isEmpty() ? "" : "; cymbals on a kick or snare: " + String.join(", ", withKept))
                + (grooveKept.isEmpty() ? "" : "; cymbals played on in eighths: " + String.join(", ", grooveKept)) + cutSaid);
        }

        /** What a library profile was made from ("12 kicks, 3 floor toms"), from its first line. */
        String said = "";

        /**
         * The drum sound library's profile (--library): its kick and toms only. Its cymbals (sounds heard
         * alone) are left out: on the 7.11. kit tests, a profile from the other three kits' test-beat
         * sounds told kicks and toms apart as well as each kit's own profile (kicks right 4583 -> 4921 of
         * 5052; own 4919), but its cymbals put Anarchy's closed hats wrong (-176).
         */
        static KitProfile library(File f) throws Exception {
            KitProfile k = new KitProfile(f.getName());
            for (String line : new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8").split("\n")) {
                String t = line.trim();
                if (t.startsWith("# from ")) k.said = t.substring(7);
                String[] w = t.split("\\s+");
                if (w.length < KIT_BANDS + 1 || !w[0].matches("\\d+")) continue;
                double[] v = new double[KIT_BANDS + 1];
                for (int i = 0; i < KIT_BANDS; i++) v[i] = Double.parseDouble(w[i + 1]);
                k.notes.add(Integer.valueOf(w[0]));
                k.spectra.add(v);
            }
            if (!k.notes.contains(Integer.valueOf(KICK_NOTE)) || k.notes.size() < 2) throw new IllegalArgumentException(f.getName() + " has no kick and tom to tell apart (the library needs a ready kick and tom)");
            if (k.said.length() == 0) k.said = k.notes.size() + " drums";
            return k;
        }

        static KitProfile read(File f) throws Exception {
            KitProfile k = new KitProfile(f.getName());
            for (String line : new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8").split("\n")) {
                String[] w = line.trim().split("\\s+");
                if (w.length > CYM_BANDS + 1 && w[0].matches("c\\d+[ksg]?")) {
                    double[] c = new double[CYM_BANDS + 1];
                    for (int i = 0; i <= CYM_BANDS; i++) c[i] = Double.parseDouble(w[i + 1]);
                    char last = w[0].charAt(w[0].length() - 1);
                    if (last == 'k' || last == 's' || last == 'g') {
                        // A cymbal heard with a kick or a snare, or played on in eighths.
                        k.withNotes.add(Integer.valueOf(w[0].substring(1, w[0].length() - 1)));
                        k.withTags.add(Character.valueOf(last));
                        k.withLooks.add(c);
                    } else {
                        k.cymNotes.add(Integer.valueOf(w[0].substring(1)));
                        k.cymLooks.add(c);
                    }
                    continue;
                }
                if (w.length == 3 && w[0].equals("ridecut")) {
                    k.rideCut = new double[] {Double.parseDouble(w[1]), Double.parseDouble(w[2])};
                    continue;
                }
                if (w.length < KIT_BANDS + 1 || w[0].startsWith("#")) continue;
                double[] v = new double[KIT_BANDS + 1];
                for (int i = 0; i < KIT_BANDS; i++) v[i] = Double.parseDouble(w[i + 1]);
                k.notes.add(Integer.valueOf(w[0]));
                k.spectra.add(v);
            }
            if (!k.notes.contains(Integer.valueOf(KICK_NOTE)) || k.notes.size() < 2) throw new IllegalArgumentException(f.getName() + " is not a kit profile with a kick and a tom");
            return k;
        }

        /** Each kick or tom found becomes the profile's nearest one: the kick, or a tom (written as DrumMidi's floor, mid or rack tom). */
        void sort(List<Hit> hits, double[] x, double sr) {
            int moved = 0;
            for (int i = 0; i < hits.size(); i++) {
                Hit h = hits.get(i);
                if (h.note != KICK_NOTE && h.note != FLOOR_TOM && h.note != MID_TOM && h.note != RACK_TOM) continue;
                double[] v = spectrum(x, sr, h.time);
                if (v == null) continue;
                int best = -1;
                double top = -2;
                for (int j = 0; j < notes.size(); j++) {
                    double l = like(v, spectra.get(j));
                    if (l > top) {
                        top = l;
                        best = notes.get(j).intValue();
                    }
                }
                int note = best == KICK_NOTE ? KICK_NOTE : best <= 43 ? FLOOR_TOM : best <= 47 ? MID_TOM : RACK_TOM;
                if (note != h.note) {
                    hits.set(i, new Hit(h.time, note, h.strength));
                    moved++;
                }
            }
            note("Kit " + name + ": " + moved + " kick" + (moved == 1 ? "" : "s") + " and toms told apart again");
        }

        /**
         * Each cymbal found (closed hat, open hat, ride, crash) becomes the learned cymbal its sound is
         * nearest, when that is clearly nearer (CYM_MARGIN) than the kind it was written as; a hit with
         * another cymbal at it (two cymbals at once) is left as it is. Needs two cymbals learned.
         */
        /**
         * The kit's open hat and crash sound alike: their learned looks nearer than OPEN_CRASH_ALIKE.
         * Then the crash peaks are the open hats: on the 7.11. kit tests Riot's (0.78; Anarchy 0.07,
         * Artillery 0.22, KVLT 0.43) crashes written there were 102 open hats, 34 closed hats, 58 nothing
         * and 8 crashes; without them its open hats right went 116 -> 215 of 333, its crashes 9 -> 5 of 38.
         */
        boolean openLikeCrash() {
            double[] o = look(OPEN_HAT), c = look(CRASH_NOTE);
            return o != null && c != null && cymNear(o, c) > OPEN_CRASH_ALIKE;
        }

        /** A learned cymbal's look (null when not learned). */
        double[] look(int note) {
            int j = cymNotes.indexOf(Integer.valueOf(note));
            return j < 0 ? null : cymLooks.get(j);
        }

        void sortCymbals(List<Hit> hits, double[] x, double sr) {
            if (cymNotes.size() < 2) return;
            java.util.Map<String, Integer> moves = new java.util.TreeMap<String, Integer>();
            // Every other hit's time: a kick's, snare's or tom's sound under a cymbal makes it look darker
            // and longer (a crash, an open hat), so only cymbals heard alone are told apart again.
            List<Hit> byTime = new ArrayList<Hit>(hits);
            Collections.sort(byTime, new java.util.Comparator<Hit>() {
                public int compare(Hit p, Hit q) {
                    return Double.compare(p.time, q.time);
                }
            });
            List<Double> cymTimes = new ArrayList<Double>();
            for (Hit h : byTime) cymTimes.add(Double.valueOf(h.time));
            for (int i = 0; i < hits.size(); i++) {
                Hit h = hits.get(i);
                if (!isCymbalNote(h.note)) continue;
                int k = Collections.binarySearch(cymTimes, Double.valueOf(h.time));
                if (k < 0) k = -k - 1;
                // Itself is one of the times; any other within CYM_ALONE (at the very same time too) is another drum.
                int near = 0;
                Hit other = null;
                for (int j = Math.max(0, k - 4); j < Math.min(cymTimes.size(), k + 5); j++) {
                    if (Math.abs(cymTimes.get(j).doubleValue() - h.time) >= CYM_ALONE) continue;
                    near++;
                    if (byTime.get(j) != h) other = byTime.get(j);
                }
                // With one kick or one snare on it: told apart by the kit's cymbals learned with that drum (when learned).
                char with = near == 2 && other != null ? (other.note == KICK_NOTE ? 'k' : other.note == SNARE_NOTE ? 's' : ' ') : near > 1 ? ' ' : 0;
                if (with == ' ') continue;
                List<Integer> kNotes = new ArrayList<Integer>();
                List<double[]> kLooks = new ArrayList<double[]>();
                if (with == 0) {
                    kNotes = cymNotes;
                    kLooks = cymLooks;
                } else {
                    for (int j = 0; j < withNotes.size(); j++) {
                        if (withTags.get(j).charValue() != with) continue;
                        kNotes.add(withNotes.get(j));
                        kLooks.add(withLooks.get(j));
                    }
                    if (kNotes.size() < 2) continue;
                }
                double[] f = cymFeature(x, sr, h.time);
                if (f == null) continue;
                int best = -1;
                double top = -9, mine = -9;
                double[] bestLook = null, mineLook = null;
                for (int j = 0; j < kNotes.size(); j++) {
                    double v = cymNear(f, kLooks.get(j));
                    if (v > top) {
                        top = v;
                        best = kNotes.get(j).intValue();
                        bestLook = kLooks.get(j);
                    }
                    if (kNotes.get(j).intValue() == h.note) {
                        mine = v;
                        mineLook = kLooks.get(j);
                    }
                }
                // A kind the profile has not learned is left as it is (KVLT's ride was not heard: its rides
                // went to its closed hat); so is one too like the nearest in this kit, or not near enough.
                if (best < 0 || best == h.note || mine <= -9 || top < CYM_LEAST || top - mine < CYM_MARGIN) continue;
                if (cymNear(mineLook, bestLook) > CYM_ALIKE) continue;
                hits.set(i, new Hit(h.time, best, h.strength));
                String key = cymName(h.note) + " to " + cymName(best) + (with == 'k' ? " (on a kick)" : with == 's' ? " (on a snare)" : "");
                moves.put(key, Integer.valueOf(moves.containsKey(key) ? moves.get(key).intValue() + 1 : 1));
            }
            StringBuilder said = new StringBuilder();
            for (java.util.Map.Entry<String, Integer> e : moves.entrySet()) said.append(said.length() == 0 ? "" : ", ").append(e.getValue()).append(' ').append(e.getKey());
            note("Kit " + name + ": cymbals by their learned sound: " + (said.length() == 0 ? "none changed" : said));
        }

        /**
         * Hats played as the ride, passage by passage: a ride or hats are told apart better over a passage
         * than hit by hit (a ride on a kick or snare looks like a hat there). The cymbal hits heard alone
         * within RIDE_VOTE_WINDOW seconds (RIDE_VOTE_SEEN at least) vote: each its nearness to the learned
         * ride less that to the learned closed hat, twice (the hat's nearness with and without the ring
         * before the hit taken off: a song's hats look brighter than the test beat's with it). Where both
         * means are over 0, the passage's hats are the ride. Only hats are made the ride: a passage
         * voting for hats is left as it is (its rides were often right). Needs the ride and closed hat learned.
         * (The looks played on in eighths, c42g and c51g, are not used: with them songs' hats voted for the
         * ride, the closed hats right went from 81% to 68% in Riot and from 67% to 35% in Artillery.)
         * The cut is 0, or the profile's ride or hats cut (the runs' hats and rides parting) where both its
         * values are under RIDE_CUT_LOWER: a kit whose ride the spaced-out looks place too near its hats
         * (Riot: -0.33) finds its rides there (Riot's rides right 350 to 431 of 689, closed hats 2892 to
         * 2878); a cut over 0 is not used (Artillery's 0.41 lost most of its rides), nor one near 0
         * (Anarchy's -0.03 made 15 hats rides for 1 ride).
         */
        void rideVote(List<Hit> hits, double[] x, double sr) {
            double[] rl = look(RIDE_NOTE), hl = look(CLOSED_HAT);
            if (rl == null || hl == null) return;
            List<Hit> cym = new ArrayList<Hit>();
            for (Hit h : hits) if (h.note == CLOSED_HAT || h.note == RIDE_NOTE) cym.add(h);
            int n = cym.size();
            boolean[] clean = new boolean[n];
            double[] d = new double[n], d2 = new double[n];
            for (int i = 0; i < n; i++) {
                Hit h = cym.get(i);
                boolean alone = true;
                for (Hit o : hits) if (o != h && Math.abs(o.time - h.time) < CYM_ALONE) alone = false;
                if (!alone) continue;
                double[] f = cymFeature(x, sr, h.time), g = cymFeature(x, sr, h.time, false);
                if (f == null || g == null) continue;
                clean[i] = true;
                d[i] = cymNear(f, rl) - cymNear(f, hl);
                d2[i] = cymNear(f, rl) - cymNear(g, hl);
            }
            boolean lower = rideCut != null && rideCut[0] < RIDE_CUT_LOWER && rideCut[1] < RIDE_CUT_LOWER;
            double cut1 = lower ? rideCut[0] : 0, cut2 = lower ? rideCut[1] : 0;
            List<Hit> toRide = new ArrayList<Hit>();
            for (int i = 0; i < n; i++) {
                Hit h = cym.get(i);
                if (h.note != CLOSED_HAT) continue;
                int seen = 0;
                double sum = 0, sum2 = 0;
                for (int j = 0; j < n; j++) {
                    if (!clean[j] || Math.abs(cym.get(j).time - h.time) > RIDE_VOTE_WINDOW) continue;
                    seen++;
                    sum += d[j];
                    sum2 += d2[j];
                }
                if (seen >= RIDE_VOTE_SEEN && sum / seen > cut1 && sum2 / seen > cut2) toRide.add(h);
            }
            for (Hit h : toRide) hits.set(hits.indexOf(h), new Hit(h.time, RIDE_NOTE, h.strength));
            note("Kit " + name + ": ride by passage: " + toRide.size() + " hat" + (toRide.size() == 1 ? "" : "s") + " made the ride");
        }
    }

    /**
     * The cymbal model: each cymbal DrumMidi found (closed hat, open hat, ride, crash) told apart again by
     * its sound where it is in the song, learned from 22 kits' renders of 122 songs (their MIDI telling
     * which cymbal each hit was). A hit's features: its 12 band shape 2-16 kHz (the ring before taken off),
     * how it fades to 30, 70, 120 and 200 ms (up to the next hit), its rise, how noisy and how peaky its
     * spectrum is at once and after 30 ms, its brightness, how alike its ring is to its hit, the time to the
     * next hit and its level; then the mean of the same over the cymbals within CM_CONTEXT s (a passage
     * on the ride or the hats), and the kind written. A multinomial logistic model gives each kind's
     * chance; a hit becomes the likeliest kind where that is CM_MARGIN surer than the kind written.
     * On the 8.11. kit tests (6 kits, 122 songs each), each kit scored by a model learned from the other
     * five: cymbals right +2087 (KVLT Mexico +503, Riot +66, Artillery Fat Metal +444, Anarchy +148, KVLT
     * Mysterious Satan +365, Artillery Northern Violence 1 +561), mostly closed hats written open or ride;
     * the open hats' and rides' precision up (Artillery's open hats 30% -> 72%), some open hats and rides
     * lost; without a kit profile +1119 on four kits. The 8.11. evening tests' new kits, unseen: KVLT
     * Norvegian Chicken Sack +575, Artillery Northern Violence 2 +685. Learned again from all eight
     * (tools/cymbal_model.py): each kit scored by a model learned from the other seven, +3641 (Anarchy
     * +202, Artillery Fat Metal +360, Chicken Sack +593, KVLT Mexico +545, Northern Violence 1 +828 and 2
     * +685, Riot +5, Mysterious Satan +423). The 9.11. tests' 11 new kits (KVLT Ride the 85, Seattle 1991,
     * St. Ranger, Svea mamma 1990, Tampa 1992, Trash 1989; Artillery Northern Violence 3, Death to the
     * Metal, Metalcore, Netombed, Industrial), unseen by the eight-kit model: +5549, each kit +157 to
     * +1332. Learned again from all 19: each kit scored by a model learned from the other 18, +9808, every
     * kit gaining (Riot +71 the least; margin 0.2 would gain more in all, +10462, but Riot -38 and twice
     * the open hats lost). Artillery Default Kit, unseen by the 19-kit model: +333; learned from all 20,
     * each kit by a model learned from the other 19: +10078, every kit gaining (Riot +56 the least).
     * KVLT 1983 Metal (the Speed Metal collection, 10.11.), unseen by the 20-kit model: +273 (closed hats
     * +310); learned from all 21, each kit by a model learned from the other 20: +10498, the other 20 kits
     * +10225 (+147 more than without it), every kit gaining (Anarchy +118 the least).
     * KVLT Artie (10.11.), unseen by the 21-kit model: +942; learned from all 22, each kit by a model
     * learned from the other 21: +12237; 1983 Metal (its kin) +1139 (was +336), the other 20 -99 together,
     * every kit gaining (Anarchy +96 the least). Aggro Heavy Metal (10.11.) is left out: with it the others
     * lose (-95 with Artie, -64 before); unseen it gains +201.
     * On by default; --no-cymbal-model leaves it out.
     */
    static final class CymModel {
        static final int N = 1024, F = 29, X = 63;
        static final double CM_CONTEXT = 2.0, CM_MARGIN = 0.4;
        static final double[] OFFS = {-0.03, 0, 0.03, 0.07, 0.12, 0.2};
        static final int[] NOTE = {CLOSED_HAT, OPEN_HAT, RIDE_NOTE, CRASH_NOTE};
        // The model's numbers: each feature's mean and spread, the kinds' biases and weights. Made by
        // tools/cymbal_model.py export (it rewrites the lines between these two markers).
        // cymbal model: begin
    static final double[] CM_MU = {-0.478093, -0.295356, -0.0743018, 0.0174638, 0.174333, 0.583661, 0.485287, 0.42252, 0.420035, 0.118654, -0.242488, -1.13171, -0.39024, -1.24994, -1.48377, -1.39699, 3.68141, 0.388127, 0.399191, 3.14473, 3.14082, 7.22199, 0.670507, -1.90127, 4.00616, 0.075431, 0.10051, 0.428922, 0.68253, -0.344214, -0.179843, 0.0364098, 0.121167, 0.270233, 0.63507, 0.533619, 0.438515, 0.374057, 0.0260272, -0.439794, -1.47125, -0.489585, -1.39679, -1.58297, -1.42583, 3.66373, 0.374868, 0.383371, 3.18983, 3.23782, 7.13357, 0.68633, -1.91674, 4.03182, 0.0751686, 0.101882, 0.434112, 0.68805, 2.81553, 0.634734, 0.150189, 0.205379, 0.00969844};
    static final double[] CM_SD = {1.45568, 1.27568, 1.10357, 1.0373, 0.822013, 0.700273, 0.716858, 0.700337, 0.871189, 0.951501, 1.27119, 2.09084, 0.640039, 0.952985, 1.69072, 2.45664, 2.68682, 0.0886228, 0.0746653, 0.550711, 0.55519, 1.07645, 0.296841, 0.850137, 1.2625, 0.264086, 0.300679, 0.494922, 0.465492, 0.693633, 0.592618, 0.537916, 0.533731, 0.364646, 0.284652, 0.333215, 0.312306, 0.450025, 0.531019, 0.739938, 1.33241, 0.335446, 0.551153, 1.0205, 1.52877, 1.56587, 0.0430629, 0.0495247, 0.282923, 0.362296, 0.569218, 0.138788, 0.34609, 0.921062, 0.089154, 0.109511, 0.273869, 0.268362, 0.367588, 0.481505, 0.357256, 0.403978, 0.0980019};
    static final double[] CM_B = {3.00876, -0.317475, -0.53256, -2.15873};
    static final double[][] CM_W = {{0.0528294, -0.00794603, -0.124631, 0.0797481}, {-0.0766073, -0.0667947, 0.0994088, 0.0439931}, {-0.0375084, 0.0376398, -0.0535842, 0.0534528}, {-0.0250749, 0.0983683, -0.121182, 0.047889}, {0.0397929, 0.036701, -0.157582, 0.0810882}, {0.0161636, 0.069231, -0.105955, 0.0205602}, {0.0514458, -0.0785779, -0.0628097, 0.0899419}, {0.0253798, 0.038499, -0.135692, 0.0718127}, {0.0605035, 0.0700231, -0.153336, 0.0228094}, {0.0395667, -0.0107073, 0.0540667, -0.0829261}, {-0.196387, -0.13285, 0.501009, -0.171771}, {0.0711821, 0.0105123, 0.0136303, -0.0953247}, {0.469547, -0.114165, -0.395438, 0.0400558}, {-0.510314, 0.298528, 0.0236482, 0.188138}, {-0.452147, 0.247156, 0.064196, 0.140795}, {-0.518777, 0.26617, 0.203787, 0.0488197}, {0.0865552, 0.00426228, -0.0303198, -0.0604977}, {0.0986259, -0.0829981, 0.0317904, -0.0474183}, {0.336925, 0.0436921, -0.192512, -0.188105}, {0.0597235, 0.0236611, -0.100459, 0.0170746}, {-0.0455882, 0.0217583, -0.0790921, 0.102922}, {0.103572, -0.0952044, 0.0506513, -0.0590193}, {-0.0535371, 0.0781585, -0.171753, 0.147131}, {-0.0914977, 0.064884, -0.0444321, 0.0710458}, {-0.305802, 0.219493, 0.200029, -0.11372}, {-0.222789, 0.210723, 0.0623113, -0.050245}, {0.144939, -0.0370561, -0.0551636, -0.0527191}, {0.37986, -0.346171, -0.0752662, 0.0415772}, {0.296679, -0.258917, -0.0344191, -0.00334209}, {-0.0547837, 0.127047, -0.0871372, 0.0148737}, {-0.255349, -0.0718171, 0.333976, -0.00681062}, {-0.172945, 0.00690494, 0.152428, 0.0136128}, {0.270356, -0.0167625, -0.240553, -0.0130409}, {0.207729, 0.066014, -0.2755, 0.00175701}, {0.00121595, 0.0225405, -0.0212395, -0.00251694}, {-0.0479672, 0.0393976, -0.0695815, 0.078151}, {-0.0370167, 0.040804, -0.0113228, 0.00753555}, {0.24495, -0.0408833, -0.216598, 0.0125307}, {-0.135048, -0.0400081, 0.145073, 0.0299829}, {-0.173927, -0.0829257, 0.316178, -0.059325}, {0.134854, 0.00323665, -0.128615, -0.00947568}, {0.357894, 0.116814, -0.520986, 0.0462786}, {-0.134879, -0.0304966, 0.125167, 0.0402092}, {-0.00670817, -0.0982894, 0.09566, 0.00933757}, {-0.0221082, -0.134596, 0.144692, 0.0120113}, {0.525818, 0.0966624, -0.571117, -0.0513635}, {-0.0133498, 0.000892718, 0.0525854, -0.0401284}, {0.121666, 0.0670562, -0.211923, 0.0232009}, {-0.0992955, 0.0248281, 0.0496235, 0.0248438}, {0.00404744, -0.0446423, 0.0337898, 0.00680503}, {0.196351, -0.00480586, -0.126938, -0.0646069}, {0.0928104, -0.0261602, -0.0398922, -0.026758}, {-0.021558, 0.0159618, -0.00758817, 0.0131845}, {0.150164, -0.169672, 0.0650654, -0.0455579}, {-0.226714, -0.0589005, 0.306006, -0.0203914}, {0.0646785, 0.16133, -0.147151, -0.0788572}, {0.16351, 0.0725658, -0.197415, -0.0386601}, {-0.00759229, 0.0701574, -0.0413879, -0.0211772}, {0.114923, -0.0512758, -0.0135651, -0.0500819}, {0.134617, -0.115226, -0.0163315, -0.00305932}, {-0.0611328, 0.35064, -0.290981, 0.00147318}, {-0.0817986, -0.170287, 0.272883, -0.0207974}, {-0.101362, -0.0101461, 0.0161174, 0.0953908}};
        // cymbal model: end

        /** A model file in place of the built-in one (the bench's, trained without the kit it scores). */
        static double[] mu = CM_MU, sd = CM_SD, b = CM_B;
        static double[][] w = CM_W;

        static void load(String path) throws Exception {
            List<double[]> rows = new ArrayList<double[]>();
            for (String line : new String(java.nio.file.Files.readAllBytes(new File(path).toPath()), "UTF-8").split("\n")) {
                String[] p = line.trim().split("\\s+");
                if (p.length < 2) continue;
                double[] v = new double[p.length - 1];
                for (int i = 1; i < p.length; i++) v[i - 1] = Double.parseDouble(p[i]);
                if (p[0].equals("mu")) mu = v;
                else if (p[0].equals("sd")) sd = v;
                else if (p[0].equals("b")) b = v;
                else rows.add(v);
            }
            w = rows.toArray(new double[0][]);
        }

        static int kind(int note) {
            return note == CLOSED_HAT || note == PEDAL_HAT ? 0 : note == OPEN_HAT ? 1 : note == RIDE_NOTE || note == 53 || note == 59 ? 2 : isCymbalNote(note) ? 3 : -1;
        }

        static double[] power(double[] x, double sr, double at) {
            int a = (int) (at * sr);
            if (at < 0 || a + N > x.length) return null;
            double[] re = new double[N], im = new double[N];
            for (int k = 0; k < N; k++) re[k] = x[a + k] * (0.5 - 0.5 * Math.cos(2 * Math.PI * k / (N - 1)));
            fftN(re, im);
            double[] m = new double[N / 2 + 1];
            for (int k = 0; k <= N / 2; k++) m[k] = re[k] * re[k] + im[k] * im[k];
            return m;
        }

        static double[] feats(double[] x, double sr, double t, double nxt) {
            double[][] ms = new double[6][];
            for (int i = 0; i < 6; i++) if ((ms[i] = power(x, sr, t + OFFS[i])) == null) return null;
            double[][] band = new double[6][12];
            double[] e = new double[6];
            int lo = -1, hi = -1;
            for (int k = 0; k <= N / 2; k++) {
                double f = k * sr / N;
                if (f > 3000 && lo < 0) lo = k;
                if (f < 12000) hi = k;
                for (int j = 0; j < 12; j++) {
                    double a = 2000 * Math.pow(8, j / 12.0), c = 2000 * Math.pow(8, (j + 1) / 12.0);
                    if (f >= a && f < c) for (int i = 0; i < 6; i++) band[i][j] += ms[i][k];
                }
            }
            for (int i = 0; i < 6; i++) for (int j = 0; j < 12; j++) {
                band[i][j] += 1e-12;
                e[i] += band[i][j];
            }
            double[] v = new double[F];
            double mean = 0;
            for (int j = 0; j < 12; j++) {
                v[j] = Math.log(Math.max(band[1][j] - band[0][j], 0.05 * band[1][j]));
                mean += v[j] / 12;
            }
            for (int j = 0; j < 12; j++) v[j] -= mean;
            double gap = nxt - t;
            int[] at = {2, 3, 4, 5};
            double[] after = {0.03, 0.07, 0.12, 0.2};
            for (int j = 0; j < 4; j++) {
                boolean has = gap > after[j] + 0.02;
                v[12 + j] = has ? Math.log(e[at[j]] / e[1]) : 0;
                v[25 + j] = has ? 0 : 1;
            }
            v[16] = Math.log(e[1] / e[0]);
            v[17] = flat(ms[1], lo, hi);
            v[18] = flat(ms[2], lo, hi);
            v[19] = peaky(ms[1], lo, hi);
            v[20] = peaky(ms[2], lo, hi);
            double num = 0, den = 0;
            for (int k = 0; k <= N / 2; k++) {
                num += k * sr / N * ms[1][k];
                den += ms[1][k];
            }
            v[21] = Math.log(num / den);
            double[] l0 = new double[12], l1 = new double[12];
            double m0 = 0, m1 = 0;
            for (int j = 0; j < 12; j++) {
                l0[j] = Math.log(band[1][j]);
                l1[j] = Math.log(band[2][j]);
                m0 += l0[j] / 12;
                m1 += l1[j] / 12;
            }
            double dot = 0, n0 = 0, n1 = 0;
            for (int j = 0; j < 12; j++) {
                dot += (l0[j] - m0) * (l1[j] - m1);
                n0 += (l0[j] - m0) * (l0[j] - m0);
                n1 += (l1[j] - m1) * (l1[j] - m1);
            }
            v[22] = dot / (Math.sqrt(n0) * Math.sqrt(n1) + 1e-9);
            v[23] = Math.log(Math.min(gap, 2.0));
            v[24] = Math.log(e[1]);
            return v;
        }

        /** An FFT of N points (DrumMidi's fft has its tables for FFT_SIZE). */
        static final double[] CS = new double[N / 2], SN = new double[N / 2];
        static {
            for (int k = 0; k < N / 2; k++) {
                CS[k] = Math.cos(-2 * Math.PI * k / N);
                SN[k] = Math.sin(-2 * Math.PI * k / N);
            }
        }

        static void fftN(double[] re, double[] im) {
            for (int i = 1, j = 0; i < N; i++) {
                int bit = N >> 1;
                for (; (j & bit) != 0; bit >>= 1) j ^= bit;
                j ^= bit;
                if (i < j) {
                    double tr = re[i]; re[i] = re[j]; re[j] = tr;
                    double ti = im[i]; im[i] = im[j]; im[j] = ti;
                }
            }
            for (int len = 2; len <= N; len <<= 1) {
                int half = len / 2, step = N / len;
                for (int i = 0; i < N; i += len) {
                    for (int k = 0; k < half; k++) {
                        double wr = CS[k * step], wi = SN[k * step];
                        int a = i + k, b = a + half;
                        double tr = re[b] * wr - im[b] * wi, ti = re[b] * wi + im[b] * wr;
                        re[b] = re[a] - tr;
                        im[b] = im[a] - ti;
                        re[a] += tr;
                        im[a] += ti;
                    }
                }
            }
        }

        static double flat(double[] m, int lo, int hi) {
            double lg = 0, s = 0;
            for (int k = lo; k <= hi; k++) {
                lg += Math.log(m[k] + 1e-12);
                s += m[k] + 1e-12;
            }
            int n = hi - lo + 1;
            return Math.exp(lg / n) / (s / n);
        }

        static double peaky(double[] m, int lo, int hi) {
            double[] v = java.util.Arrays.copyOfRange(m, lo, hi + 1);
            double top = 0;
            for (double a : v) top = Math.max(top, a);
            java.util.Arrays.sort(v);
            double med = v.length % 2 == 1 ? v[v.length / 2] : (v[v.length / 2 - 1] + v[v.length / 2]) / 2;
            return Math.log(top / (med + 1e-12));
        }

        static void apply(List<Hit> hits, double[] x, double sr) {
            String file = System.getProperty("dm.cymModelFile");
            if (file != null) {
                try {
                    load(file);
                } catch (Exception ex) {
                    note("Cymbal model file not read: " + ex.getMessage());
                }
            }
            double margin = Double.parseDouble(System.getProperty("dm.cymMargin", String.valueOf(CM_MARGIN)));
            List<Hit> byTime = new ArrayList<Hit>(hits);
            Collections.sort(byTime, new java.util.Comparator<Hit>() {
                public int compare(Hit p, Hit q) {
                    return Double.compare(p.time, q.time);
                }
            });
            List<Hit> cym = new ArrayList<Hit>();
            List<double[]> fs = new ArrayList<double[]>();
            for (int i = 0; i < byTime.size(); i++) {
                Hit h = byTime.get(i);
                if (kind(h.note) < 0) continue;
                double nxt = h.time + 2.0;
                for (int j = i + 1; j < byTime.size(); j++) if (byTime.get(j).time > h.time + 0.005) {
                    nxt = Math.min(nxt, byTime.get(j).time);
                    break;
                }
                double[] f = feats(x, sr, h.time, nxt);
                if (f == null) continue;
                cym.add(h);
                fs.add(f);
            }
            java.util.Map<String, Integer> moves = new java.util.TreeMap<String, Integer>();
            java.util.Map<Hit, Hit> to = new java.util.HashMap<Hit, Hit>();
            for (int i = 0; i < cym.size(); i++) {
                Hit h = cym.get(i);
                double[] in = new double[X];
                System.arraycopy(fs.get(i), 0, in, 0, F);
                int n = 0;
                for (int j = 0; j < cym.size(); j++) {
                    if (j == i || Math.abs(cym.get(j).time - h.time) > CM_CONTEXT) continue;
                    for (int a = 0; a < F; a++) in[F + a] += fs.get(j)[a];
                    n++;
                }
                if (n > 0) for (int a = 0; a < F; a++) in[F + a] /= n;
                in[2 * F] = Math.log1p(n);
                int mine = kind(h.note);
                in[2 * F + 1 + mine] = 1;
                double[] z = new double[4];
                double top = -1e300;
                for (int c = 0; c < 4; c++) {
                    z[c] = b[c];
                    for (int a = 0; a < X; a++) z[c] += (in[a] - mu[a]) / sd[a] * w[a][c];
                    top = Math.max(top, z[c]);
                }
                double sum = 0;
                for (int c = 0; c < 4; c++) sum += (z[c] = Math.exp(z[c] - top));
                int best = 0;
                for (int c = 0; c < 4; c++) if (z[c] > z[best]) best = c;
                if (best == mine || (z[best] - z[mine]) / sum <= margin) continue;
                to.put(h, new Hit(h.time, NOTE[best], h.strength));
                String key = cymName(h.note) + " to " + cymName(NOTE[best]);
                moves.put(key, Integer.valueOf(moves.containsKey(key) ? moves.get(key).intValue() + 1 : 1));
            }
            for (int i = 0; i < hits.size(); i++) if (to.containsKey(hits.get(i))) hits.set(i, to.get(hits.get(i)));
            StringBuilder said = new StringBuilder();
            for (java.util.Map.Entry<String, Integer> e : moves.entrySet()) said.append(said.length() == 0 ? "" : ", ").append(e.getValue()).append(' ').append(e.getKey());
            note("Cymbals by their sound in the song (the cymbal model): " + (said.length() == 0 ? "none changed" : said));
        }
    }

    static boolean isTomNote(int n) {
        return n == 41 || n == 43 || n == 45 || n == 47 || n == 48 || n == 50;
    }

    /** The song's loud snares' body spectrum (120-450 Hz), to tell its ghost notes from toms. */
    static final class SnareBody {
        final int b0, b1;
        final double[] spec;
        final double peak;
        final int count;

        SnareBody(float[][] mag, List<Hit> hits, double sr) {
            int maxBin = mag.length > 0 ? mag[0].length - 1 : 0;
            b0 = bin(120, sr, maxBin);
            b1 = bin(450, sr, maxBin);
            spec = new double[b1 - b0 + 1];
            List<Double> loud = new ArrayList<Double>();
            for (Hit h : hits) if (h.note == SNARE_NOTE) loud.add(Double.valueOf(h.strength));
            Collections.sort(loud);
            double cut = loud.isEmpty() ? 0 : loud.get(loud.size() / 2).doubleValue();
            int n = 0;
            for (Hit h : hits) {
                if (h.note != SNARE_NOTE || h.strength < cut) continue;
                add(spec, mag, (int) Math.round(h.time * sr / HOP));
                n++;
            }
            count = n;
            peak = peakBin(spec);
        }

        void add(double[] v, float[][] mag, int t) {
            for (int k = Math.max(0, t); k <= Math.min(mag.length - 1, t + 3); k++) for (int b = b0; b <= b1; b++) v[b - b0] += mag[k][b];
        }

        /** The loud snares' body level (median of its peaks at them, unnormalized); 0 with too few. */
        double rawBody(double[] bodyRaw, List<Hit> hits, double sr) {
            if (count < GHOST_SNARES) return 0;
            List<Double> v = new ArrayList<Double>();
            for (Hit h : hits) {
                if (h.note != SNARE_NOTE) continue;
                int t = (int) Math.round(h.time * sr / HOP);
                if (t < 0 || t >= bodyRaw.length) continue;
                v.add(Double.valueOf(Math.max(bodyRaw[t], bodyRaw[Math.min(bodyRaw.length - 1, t + 2)])));
            }
            if (v.isEmpty()) return 0;
            Collections.sort(v);
            return v.get(v.size() / 2).doubleValue();
        }

        /**
         * How long the loud snares ring (a room or a reverb): the median of their body level
         * RING_FRAMES after the peak over the peak. A dry kit's snare has died by then.
         */
        double ring(double[] bodyRaw, List<Hit> hits, double sr) {
            List<Double> v = new ArrayList<Double>();
            for (Hit h : hits) {
                if (h.note != SNARE_NOTE) continue;
                int t = (int) Math.round(h.time * sr / HOP);
                if (t < 0 || t + RING_FRAMES >= bodyRaw.length) continue;
                double pk = Math.max(bodyRaw[t], bodyRaw[t + 2]);
                if (pk > 0) v.add(Double.valueOf(bodyRaw[t + RING_FRAMES] / pk));
            }
            if (v.isEmpty()) return 1;
            Collections.sort(v);
            return v.get(v.size() / 2).doubleValue();
        }

        /** A snare's body pitch and spectrum at frame t: a ghost note. */
        boolean ghost(float[][] mag, int t) {
            return ghost(mag, t, GHOST_LIKE);
        }

        boolean ghost(float[][] mag, int t, double like) {
            if (count < GHOST_SNARES || peak <= 0) return false;
            double[] v = new double[spec.length];
            add(v, mag, t);
            double ratio = peakBin(v) / peak;
            if (ratio < GHOST_PITCH_LO || ratio > GHOST_PITCH_HI) return false;
            double dot = 0, na = 0, nb = 0;
            for (int i = 0; i < v.length; i++) {
                dot += v[i] * spec[i];
                na += v[i] * v[i];
                nb += spec[i] * spec[i];
            }
            return na > 0 && nb > 0 && dot / Math.sqrt(na * nb) >= like;
        }

        /** The body at frame t has frame s's spectrum (a roll's snares: see ROLL_FRAMES). */
        boolean alike(float[][] mag, int t, int s) {
            double[] v = new double[spec.length], w = new double[spec.length];
            add(v, mag, t);
            add(w, mag, s);
            double dot = 0, na = 0, nb = 0;
            for (int i = 0; i < v.length; i++) {
                dot += v[i] * w[i];
                na += v[i] * v[i];
                nb += w[i] * w[i];
            }
            return na > 0 && nb > 0 && dot / Math.sqrt(na * nb) >= ROLL_LIKE;
        }

        /** Where v peaks, in bins from b0's frequency (b0 + index), between bins. */
        double peakBin(double[] v) {
            int best = 0;
            for (int i = 1; i < v.length; i++) if (v[i] > v[best]) best = i;
            double off = 0;
            if (best > 0 && best < v.length - 1) {
                double a = v[best - 1], b = v[best], c = v[best + 1], d = a - 2 * b + c;
                if (d != 0) off = Math.max(-0.5, Math.min(0.5, 0.5 * (a - c) / d));
            }
            return v[best] > 0 ? b0 + best + off : 0;
        }
    }

    /** A kick read at its third partial (OVERTONE_LO-OVERTONE_HI of the kick pitch) whose low end is still there. */
    static boolean overtoneKick(float[][] mag, int t, double pitch, double kickPitch, double sr) {
        if (pitch < OVERTONE_LO * kickPitch || pitch > OVERTONE_HI * kickPitch) return false;
        return lowEnd(mag, t, pitch, sr) >= OVERTONE_SUB;
    }

    /** The kick found at `time` becomes a floor tom. */
    static void floorTom(List<Hit> hits, double time) {
        for (int h = 0; h < hits.size(); h++) {
            Hit x = hits.get(h);
            if (x.note == KICK_NOTE && x.time == time) hits.set(h, new Hit(time, FLOOR_TOM, x.strength));
        }
    }

    /**
     * Guitars (or keys) play: the 300-1000 Hz band stays up between the hits, its lower quartile
     * at GUITAR_FLOOR of its loud level (95th percentile); drums alone, a ride too, leave it far lower.
     */
    static boolean guitarLine(float[][] mag, double sr, int maxBin) {
        double[] g = bandMean(mag, bin(300, sr, maxBin), bin(1000, sr, maxBin));
        Arrays.sort(g);
        double loud = g[(int) (g.length * 0.95)];
        return loud > 1e-9 && g[g.length / 4] >= GUITAR_FLOOR * loud;
    }

    /** Something keeps the kick band up half the time: a bass line (drums alone leave it quiet between hits). */
    static boolean bassLine(double[] kickLevel) {
        double[] s = kickLevel.clone();
        Arrays.sort(s);
        return s[s.length / 2] >= QUIET_BEFORE;
    }

    /**
     * The kick band kept up by bass notes, not by ringing toms: a bass line holds it steady (its
     * lower quartile at BASS_STEADY of its median at least: the bass mixes 0.80-0.86), toms let it
     * die away between their hits (Anarchy Drums' tom grooves 0.43-0.67). The tom passes ask this;
     * the kick and cymbal passes keep asking bassLine, as a band kept up there either way.
     */
    static final double BASS_STEADY = 0.74;

    static boolean bassNotes(double[] kickLevel) {
        double[] s = kickLevel.clone();
        Arrays.sort(s);
        double median = s[s.length / 2];
        return median >= QUIET_BEFORE && s[s.length / 4] >= BASS_STEADY * median;
    }

    /**
     * Hat i keeps the time of the hats kept so far: kept hats on both sides at the same spacing, or
     * two kept hats before it (or after it) spaced as it is from the nearer one; spacings up to
     * HAT_TIME_MAX frames, equal within 40% (hat onsets land a few frames early or late: 16ths at
     * 100 BPM come out 11 and 15 frames apart).
     */
    /** Whether a pedal hat was found within SAME_CYMBAL of the time. */
    static boolean onPedal(List<Hit> hits, double time) {
        for (Hit h : hits) if (h.note == PEDAL_HAT && Math.abs(h.time - time) <= SAME_CYMBAL) return true;
        return false;
    }

    /** The hat peak is the second peak of the one before (ECHO_FRAMES). */
    static boolean echo(List<Integer> peaks, double[] hat, double[] level, int i) {
        if (i == 0) return false;
        int t = peaks.get(i).intValue(), p = peaks.get(i - 1).intValue();
        return t - p <= ECHO_FRAMES && jump(level, t) < ECHO_RISE && hat[t] < ECHO_LEVEL * hat[p];
    }

    static boolean keepsTime(List<Integer> peaks, boolean[] keep, int i) {
        int t = peaks.get(i).intValue();
        int[] before = new int[2], after = new int[2];
        int nb = 0, na = 0;
        for (int j = i - 1; j >= 0 && nb < 2; j--) if (keep[j]) before[nb++] = peaks.get(j).intValue();
        for (int j = i + 1; j < peaks.size() && na < 2; j++) if (keep[j]) after[na++] = peaks.get(j).intValue();
        if (nb >= 1 && na >= 1 && sameGap(t - before[0], after[0] - t)) return true;
        if (nb >= 2 && sameGap(t - before[0], before[0] - before[1])) return true;
        return na >= 2 && sameGap(after[0] - t, after[1] - after[0]);
    }

    /**
     * The shape of the cymbal bands before HPSS, per frame: 1-3 kHz, 3-7 kHz (the ride band) and
     * 7-14 kHz levels, and how alike the ride band's spectrum is to its spectrum 4 frames on.
     */
    static final class CymbalShape {
        final double[] lo, ride, hi, stab;

        CymbalShape(float[][] mag, double sr, int maxBin) {
            int n = mag.length;
            lo = new double[n];
            ride = new double[n];
            hi = new double[n];
            stab = new double[n];
            int l0 = bin(1000, sr, maxBin), r0 = bin(3000, sr, maxBin), r1 = bin(7000, sr, maxBin), h1 = bin(14000, sr, maxBin);
            for (int f = 0; f < n; f++) {
                lo[f] = mean(mag[f], l0, r0);
                ride[f] = mean(mag[f], r0, r1);
                hi[f] = mean(mag[f], r1, h1);
                if (f + 4 < n) {
                    double ab = 0, aa = 0, bb = 0;
                    for (int k = r0; k <= r1; k++) {
                        ab += mag[f][k] * mag[f + 4][k];
                        aa += mag[f][k] * mag[f][k];
                        bb += mag[f + 4][k] * mag[f + 4][k];
                    }
                    stab[f] = ab / Math.sqrt(aa * bb + 1e-18);
                }
            }
        }

        private static double mean(float[] v, int a, int b) {
            double s = 0;
            for (int k = a; k <= b; k++) s += v[k];
            return s / Math.max(1, b - a + 1);
        }
    }

    /**
     * Ride or hats, passage by passage. A ride's ring keeps its partials (the ride band alike
     * from 4 to 8 frames on, RIDE_STABLE) or sits low (1-3 kHz at RIDE_LOW of the ride band,
     * 7-14 kHz under RIDE_HIGH); a closed hat with a room or reverb rings on too, but these only
     * here and there. Where RIDE_SHARE of the clean cymbal hits within RIDE_WINDOW seconds
     * (no kick, snare or crash on them; RIDE_SEEN at least) are ride-like (a ride's clean hits are so
     * 43-67% of the time, a hat's 8-14%), the passage's closed hats
     * are the ride; elsewhere its rides are hats. The levels are taken as rises at the hit, so what
     * already sounds does not count. Over a bass line the partials are not asked about, and hats
     * are not turned into a ride (chords struck with the hats rise low as a ride does).
     */
    static void rideOrHats(List<Hit> hits, CymbalShape sh, double fps, boolean bass, boolean guitar) {
        List<Hit> cym = new ArrayList<Hit>();
        List<Double> all = new ArrayList<Double>();
        for (Hit h : hits) {
            all.add(Double.valueOf(h.time));
            if (h.note == CLOSED_HAT || h.note == RIDE_NOTE) cym.add(h);
        }
        Collections.sort(all);
        int n = cym.size();
        boolean[] clean = new boolean[n], like = new boolean[n];
        for (int i = 0; i < n; i++) {
            Hit h = cym.get(i);
            clean[i] = true;
            for (Hit o : hits) {
                if ((o.note == KICK_NOTE || o.note == SNARE_NOTE || o.note == CRASH_NOTE) && Math.abs(o.time - h.time) < 0.06) clean[i] = false;
            }
            int t = (int) Math.round(h.time * fps), b = t;
            for (int k = t; k <= Math.min(sh.ride.length - 1, t + 3); k++) if (sh.ride[k] + sh.hi[k] > sh.ride[b] + sh.hi[b]) b = k;
            if (b >= sh.ride.length || sh.ride[b] < 1e-9) continue;
            double next = Double.MAX_VALUE;
            for (Double v : all) if (v.doubleValue() > h.time + 2.5 / fps) {
                next = v.doubleValue();
                break;
            }
            // Over a band, a guitar or keys hold the ride band's spectrum steady between hits too.
            // A little less steady counts too when the ride band still rings 6 frames on (RIDE_RING of the
            // hit): a ride played on and on over guitars (no bass) keeps its wash; a closed hat has died.
            boolean rings = b + 6 < sh.ride.length && sh.ride[b + 6] >= RIDE_RING * sh.ride[b];
            // A guitar holds the band steady under closed hats too; their 7-14 kHz has died 6 frames on
            // (a ride's still rings, RIDE_TOP_RING of the hit).
            boolean topRings = b + 6 < sh.hi.length && sh.hi[b + 6] >= RIDE_TOP_RING * sh.hi[b];
            // A dry ride's wash: the ride band rings on while its top dies (closed and open hats keep their top).
            boolean washes = b + 12 < sh.ride.length && sh.ride[b + 6] >= RIDE_WASH * sh.ride[b]
                && sh.hi[b + 6] <= RIDE_WASH_TOP * sh.hi[b] && sh.hi[b + 12] <= RIDE_WASH_TOP_LATE * sh.hi[b];
            boolean stable = !bass && topRings && (next - h.time) * fps > 8 && b + 4 < sh.stab.length
                && (sh.stab[b + 4] >= RIDE_STABLE || rings && sh.stab[b + 4] >= RIDE_STABLE_RINGING || washes && sh.stab[b + 4] >= RIDE_WASH_STABLE);
            // Rises at the hit: what already sounds (a band, a ride's own wash) does not count.
            int pre = Math.max(0, t - 3);
            double dl = Math.max(0, sh.lo[b] - sh.lo[pre]), dr = Math.max(1e-9, sh.ride[b] - sh.ride[pre]), dh = Math.max(0, sh.hi[b] - sh.hi[pre]);
            // A guitar's chord struck with the hat rises low too (1-3 kHz): not a ride's sign there.
            boolean low = !guitar && dl >= RIDE_LOW * dr && dh <= RIDE_HIGH * dr;
            like[i] = stable || low;
        }
        for (int i = 0; i < n; i++) {
            Hit h = cym.get(i);
            int seen = 0, rides = 0;
            for (double window : new double[] {RIDE_WINDOW, RIDE_WINDOW_WIDE}) {
                seen = 0;
                rides = 0;
                for (int j = 0; j < n; j++) {
                    if (!clean[j] || Math.abs(cym.get(j).time - h.time) > window) continue;
                    seen++;
                    if (like[j]) rides++;
                }
                if (seen >= RIDE_SEEN) break;
            }
            if (seen < RIDE_SEEN) continue;
            int note = rides >= RIDE_SHARE * seen ? RIDE_NOTE : CLOSED_HAT;
            // Over a band, guitar chords struck with the hats look low too: hats are not made rides.
            if (bass && note == RIDE_NOTE) continue;
            if (note != h.note) hits.set(hits.indexOf(h), new Hit(h.time, note, h.strength));
        }
    }

    /**
     * A crash played on and on (eighths or quarters, as heavy music rides it) has no time to wash
     * between its hits, so isCrash, which asks for the wash, does not find it: its hits are written
     * as hats or a ride. Such a crash is dark: its 7-14 kHz under CRASH_TILT of its 3-7 kHz (Anarchy
     * Drums' crashes 0.18-0.29; hats and rides 0.34-0.92), and the 3-7 kHz still at CRASH_SWELL of the
     * hit 5 frames on (a crash swells: 0.88-1.71; most hats die under it). Where at least
     * CRASH_SHARE of the cymbal hits within CRASH_WINDOW seconds are so (any number of them: a lone
     * crash too), they are the crash.
     */
    static final double CRASH_TILT = 0.3, CRASH_SWELL = 0.75, CRASH_SHARE = 0.5, CRASH_WINDOW = 1.0;

    /**
     * --snare-leak: a snare's wires reach the hat band, and a bright one (Anarchy Drums') looks there
     * just like a hat struck with it. What is the snare's own is learned from the song's snares with
     * no hat written at them (LEAK_SNARES at least; with fewer, nothing is changed): the share of
     * their 4-14 kHz rise that is over 10 kHz (Anarchy's snare 0.06-0.14, a snare with a cymbal
     * 0.16-0.26, a hat alone 0.33-0.47). A hat on a snare whose share stays under LEAK_OVER times the
     * snare's own is the snare's leak, not a hat.
     */
    static final int LEAK_SNARES = 4;
    static final double LEAK_OVER = 1.2;

    static void snareLeaks(List<Hit> hits, float[][] mag, double sr, double fps) {
        List<Hit> snares = new ArrayList<Hit>(), alone = new ArrayList<Hit>();
        for (Hit h : hits) if (h.note == SNARE_NOTE) snares.add(h);
        for (Hit s : snares) {
            boolean hat = false;
            for (Hit h : hits) if ((h.note == CLOSED_HAT || h.note == OPEN_HAT || h.note == PEDAL_HAT) && Math.abs(h.time - s.time) <= SAME_CYMBAL) hat = true;
            if (!hat) alone.add(s);
        }
        if (alone.size() < LEAK_SNARES) {
            note("Snare leak: " + alone.size() + " snares without a hat, " + LEAK_SNARES + " needed to learn the snare's own sound; no hats left out");
            return;
        }
        double[] own = new double[alone.size()];
        for (int i = 0; i < own.length; i++) own[i] = topShare(mag, (int) Math.round(alone.get(i).time * fps), sr);
        Arrays.sort(own);
        double snareTop = own[own.length / 2];
        List<Hit> leaks = new ArrayList<Hit>();
        for (Hit h : hits) {
            if (h.note != CLOSED_HAT && h.note != OPEN_HAT) continue;
            boolean onSnare = false;
            for (Hit s : snares) if (Math.abs(s.time - h.time) <= SAME_CYMBAL) onSnare = true;
            if (onSnare && topShare(mag, (int) Math.round(h.time * fps), sr) < LEAK_OVER * snareTop) leaks.add(h);
        }
        hits.removeAll(leaks);
        note(String.format(java.util.Locale.ROOT, "Snare leak: learned from %d snares without a hat (%.2f of their rise over 10 kHz); %d hats on snares left out", alone.size(), snareTop, leaks.size()));
    }

    /** Of what rises at the hit in 4-14 kHz (over 3 frames before), the share over 10 kHz. */
    static double topShare(float[][] mag, int t, double sr) {
        if (t < 3 || t + 1 >= mag.length) return 1;
        int maxBin = mag[t].length - 1;
        double low = 0, high = 0;
        for (int b = bin(4000, sr, maxBin); b < bin(14000, sr, maxBin); b++) {
            double r = Math.max(0, Math.max(mag[t][b], mag[t + 1][b]) - mag[t - 3][b]);
            if (b >= bin(10000, sr, maxBin)) high += r;
            else low += r;
        }
        return low + high > 1e-12 ? high / (low + high) : 1;
    }

    static void darkCrashes(List<Hit> hits, CymbalShape sh, double fps) {
        List<Hit> cym = new ArrayList<Hit>();
        for (Hit h : hits) if (h.note == CLOSED_HAT || h.note == OPEN_HAT || h.note == RIDE_NOTE || h.note == CRASH_NOTE) cym.add(h);
        int n = cym.size();
        boolean[] dark = new boolean[n];
        for (int i = 0; i < n; i++) {
            int t = (int) Math.round(cym.get(i).time * fps) + 1;
            if (t + 5 >= sh.ride.length || sh.ride[t] < 1e-12) continue;
            dark[i] = sh.hi[t] < CRASH_TILT * sh.ride[t] && sh.ride[t + 5] >= CRASH_SWELL * sh.ride[t];
        }
        for (int i = 0; i < n; i++) {
            Hit h = cym.get(i);
            if (h.note == CRASH_NOTE) continue;
            int seen = 0, crashes = 0;
            for (int j = 0; j < n; j++) {
                if (Math.abs(cym.get(j).time - h.time) > CRASH_WINDOW) continue;
                seen++;
                if (dark[j]) crashes++;
            }
            if (crashes > 0 && crashes >= CRASH_SHARE * seen && dark[i]) hits.set(hits.indexOf(h), new Hit(h.time, CRASH_NOTE, h.strength));
        }
    }

    /**
     * A crash rings for seconds, and its wash peaks again on its way down (or as the hats and the
     * ride beat under it): such a peak is found as another crash. A crash within RING_WINDOW seconds
     * after another one whose 3-14 kHz level is under RING_JUMP times what it was 2-3 frames before
     * is the first crash still ringing, not a new one (Anarchy Drums: crashes played jump 1.2-9.3
     * times, the ringing's peaks 0.8-1.1; tested: crash precision 47% to 62%,
     * recall 27% to 26%, nothing else changed).
     */
    static final double RING_WINDOW = 1.0, RING_JUMP = 1.1;

    /**
     * A crash ridden on eighths or quarters that darkCrashes misses (its hits too close to swell)
     * stays written as hats or a ride. Its sound around the hit tells it: the cymbal hits within
     * RIDDEN_WINDOW seconds are dark (their median 7-14 kHz under RIDDEN_AROUND of their 3-7 kHz;
     * Anarchy Drums' ridden crashes 0.22, hats and rides 0.42), and its wash stays up between them
     * (the median low between a hit and the next at RIDDEN_FLOOR of the hit at least: crashes 0.52,
     * hats 0.14, rides 0.41). A hat or ride hit that is dark itself (under RIDDEN_TILT) there is the
     * crash. Runs before ringingCrashes, so the wash's own peaks it turns into crashes are then
     * left out. Tested: Anarchy crash recall 26% to 44%, precision 62% to 52%; hat precision 44% to
     * 45%, ride 50% to 55%; nothing else changed.
     */
    static final double RIDDEN_TILT = 0.3, RIDDEN_AROUND = 0.25, RIDDEN_FLOOR = 0.5, RIDDEN_WINDOW = 1.0;

    static void riddenCrashes(List<Hit> hits, CymbalShape sh, double fps) {
        List<Hit> cym = new ArrayList<Hit>();
        for (Hit h : hits) if (h.note == CLOSED_HAT || h.note == OPEN_HAT || h.note == RIDE_NOTE || h.note == CRASH_NOTE) cym.add(h);
        cym.sort(Comparator.comparingDouble(new java.util.function.ToDoubleFunction<Hit>() { public double applyAsDouble(Hit h) { return h.time; } }));
        int n = cym.size();
        double[] tilt = new double[n], floor = new double[n];
        for (int i = 0; i < n; i++) {
            int t = (int) Math.round(cym.get(i).time * fps);
            tilt[i] = 1;
            if (t + 3 >= sh.ride.length || sh.ride[t + 1] < 1e-12) continue;
            tilt[i] = sh.hi[t + 1] / sh.ride[t + 1];
            int next = i + 1 < n ? (int) Math.round(cym.get(i + 1).time * fps) : t + 25;
            next = Math.min(Math.min(Math.max(next, t + 3), t + 25), sh.ride.length);
            double low = Double.MAX_VALUE;
            for (int f = t + 2; f < next; f++) low = Math.min(low, sh.ride[f]);
            floor[i] = low / sh.ride[t + 1];
        }
        for (int i = 0; i < n; i++) {
            Hit h = cym.get(i);
            if (h.note == CRASH_NOTE || tilt[i] >= RIDDEN_TILT) continue;
            List<Double> tilts = new ArrayList<Double>(), floors = new ArrayList<Double>();
            for (int j = 0; j < n; j++) {
                if (Math.abs(cym.get(j).time - h.time) > RIDDEN_WINDOW) continue;
                tilts.add(Double.valueOf(tilt[j]));
                floors.add(Double.valueOf(floor[j]));
            }
            Collections.sort(tilts);
            Collections.sort(floors);
            if (tilts.get(tilts.size() / 2).doubleValue() < RIDDEN_AROUND && floors.get(floors.size() / 2).doubleValue() >= RIDDEN_FLOOR)
                hits.set(hits.indexOf(h), new Hit(h.time, CRASH_NOTE, h.strength));
        }
    }

    /**
     * A tom tuned low lands its stick in the kick band (50-160 Hz) as a kick does, and its level there
     * hardly jumps over the tom rung before it, so the tom bands' tests miss it and it is written as a
     * kick or not at all. What tells it is its ring: in 60-125 Hz a kick has died 6 frames on (Anarchy
     * Drums' kicks keep 0.12-0.23 of the hit), a tom still holds it (0.37-1.0). An onset adding at least
     * RING_TOM_ADD of the song's usual kick in 50-160 Hz, over a level under RING_TOM_BEFORE of the hit,
     * still at RING_TOM_HOLD of it 6 frames on and RING_TOM_HOLD_LATE 12 frames on, and pitched under
     * RING_TOM_HZ (kicks' beater 110-120 Hz, these toms 70-90), is a tom: a kick written there is
     * taken for it. Fixed limits, not the song's own kicks: in a tom groove most "kicks" are toms.
     * Tested: Anarchy tom recall 7% to 26%, precision 42% to 58%; kick recall 86% to 84%, precision
     * 95% to 96%; toms on the other kits a little better, nothing else changed.
     */
    static final double RING_TOM_LO = 50, RING_TOM_HI = 160, RING_TOM_RING_LO = 60, RING_TOM_RING_HI = 125;
    static final double RING_TOM_ADD = 0.35, RING_TOM_BEFORE = 0.5, RING_TOM_HOLD = 0.35, RING_TOM_HOLD_LATE = 0.3, RING_TOM_HZ = 100;

    static void ringingToms(List<Hit> hits, double[] low, double[] ring, double[] pitch, double fps) {
        int n = low.length;
        double[] add = new double[n];
        for (int t = 3; t + 1 < n; t++) add[t] = Math.max(0, Math.max(low[t], low[t + 1]) - Math.min(low[t - 2], low[t - 3]));
        List<Double> kAdd = new ArrayList<Double>();
        for (Hit h : hits) {
            if (h.note != KICK_NOTE) continue;
            int t = (int) Math.round(h.time * fps);
            if (t < 3 || t + 3 >= n) continue;
            double m = 0;
            for (int i = t - 2; i <= t + 2; i++) m = Math.max(m, add[i]);
            kAdd.add(Double.valueOf(m));
        }
        if (kAdd.size() < LOW_TOM_KICKS) return;
        Collections.sort(kAdd);
        double usualAdd = kAdd.get(kAdd.size() / 2).doubleValue();
        if (usualAdd < 1e-12) return;
        int found = 0, fromKicks = 0;
        for (int t = 4; t + 13 < n; t++) {
            if (add[t] < RING_TOM_ADD * usualAdd) continue;
            boolean peak = true;
            for (int i = t - 3; i <= t + 3; i++) if (i != t && add[i] >= add[t]) peak = false;
            if (!peak) continue;
            double top = Math.max(low[t], low[t + 1]), now = Math.max(ring[t], ring[t + 1]);
            if (Math.min(low[t - 2], low[t - 3]) > RING_TOM_BEFORE * top || now < 1e-12) continue;
            if (ring[t + 6] < RING_TOM_HOLD * now || ring[t + 12] < RING_TOM_HOLD_LATE * now || pitch[t] >= RING_TOM_HZ) continue;
            double time = t / fps;
            Hit kick = null;
            boolean tom = false;
            for (Hit h : hits) {
                if (Math.abs(h.time - time) > SAME_CYMBAL) continue;
                if (h.note == KICK_NOTE) kick = h;
                if (h.note == FLOOR_TOM || h.note == MID_TOM || h.note == RACK_TOM) tom = true;
            }
            if (tom) continue;
            int note = pitch[t] < FLOOR_TOM_HZ ? FLOOR_TOM : MID_TOM;
            if (kick != null) {
                hits.set(hits.indexOf(kick), new Hit(kick.time, note, kick.strength));
                fromKicks++;
            } else hits.add(new Hit(time, note, Math.min(1.5, add[t] / usualAdd)));
            found++;
        }
        if (found > 0) note("Ringing toms: " + found + " (" + fromKicks + " of them written as kicks before)");
    }

    static void ringingCrashes(List<Hit> hits, CymbalShape sh, double fps) {
        List<Hit> crashes = new ArrayList<Hit>();
        for (Hit h : hits) if (h.note == CRASH_NOTE) crashes.add(h);
        crashes.sort(Comparator.comparingDouble(new java.util.function.ToDoubleFunction<Hit>() { public double applyAsDouble(Hit h) { return h.time; } }));
        List<Hit> ringing = new ArrayList<Hit>();
        for (int i = 1; i < crashes.size(); i++) {
            Hit h = crashes.get(i);
            if (h.time - crashes.get(i - 1).time > RING_WINDOW) continue;
            int t = (int) Math.round(h.time * fps);
            if (t < 3 || t + 1 >= sh.ride.length) continue;
            double level = sh.ride[t + 1] + sh.hi[t + 1];
            double before = Math.min(sh.ride[t - 2] + sh.hi[t - 2], sh.ride[t - 3] + sh.hi[t - 3]);
            if (level < RING_JUMP * before) ringing.add(h);
        }
        hits.removeAll(ringing);
    }

    /**
     * A ride found on a kick or a snare (within 60 ms) with fewer than RIDE_ALONE rides off the kicks
     * and snares within RIDE_WINDOW seconds is the kick's click or the snare's rattle in the ride
     * band (over guitars more so), not a ride: a ride played keeps on between the kicks and snares.
     * Not where a bass line plays: there a ride is often heard only on the beat.
     */
    static void lonelyRides(List<Hit> hits) {
        List<Double> beat = new ArrayList<Double>();
        for (Hit h : hits) if (h.note == KICK_NOTE || h.note == SNARE_NOTE) beat.add(Double.valueOf(h.time));
        List<Double> clean = new ArrayList<Double>();
        List<Hit> onBeat = new ArrayList<Hit>();
        for (Hit h : hits) {
            if (h.note != RIDE_NOTE) continue;
            boolean on = false;
            for (Double b : beat) if (Math.abs(b.doubleValue() - h.time) < 0.06) on = true;
            if (on) onBeat.add(h);
            else clean.add(Double.valueOf(h.time));
        }
        for (Hit h : onBeat) {
            int near = 0;
            for (Double c : clean) if (Math.abs(c.doubleValue() - h.time) <= RIDE_WINDOW) near++;
            if (near < RIDE_ALONE) hits.remove(h);
        }
    }

    static boolean sameGap(int a, int b) {
        return a > 0 && b > 0 && a <= HAT_TIME_MAX && b <= HAT_TIME_MAX && Math.abs(a - b) <= Math.max(2, 0.4 * Math.max(a, b));
    }

    /**
     * A quiet "snare" (its body rising under PEDAL_BODY) with no kick and no loud snare (body
     * LOUD_BODY) within PEDAL_ALONE seconds is a hi-hat pedal's chick, as in a count-in (its last
     * chicks, just before the downbeat, too: within PEDAL_RUN of the one before): it has a
     * little body and some noise, as a ghost note does, but ghost notes come among the beat's kicks
     * and snares.
     */
    static void pedalHats(List<Hit> hits, List<Integer> snareFrames, List<Double> bodies, double sr) {
        List<Double> beat = new ArrayList<Double>();
        for (Hit h : hits) if (h.note == KICK_NOTE) beat.add(Double.valueOf(h.time));
        for (int i = 0; i < snareFrames.size(); i++) {
            if (bodies.get(i).doubleValue() >= LOUD_BODY) beat.add(Double.valueOf(snareFrames.get(i) * (double) HOP / sr));
        }
        // In time order, so a count-in's last chicks (just before the downbeat) follow the run.
        double lastPedal = -1e9;
        for (int i = 0; i < snareFrames.size(); i++) {
            if (bodies.get(i).doubleValue() >= PEDAL_BODY) continue;
            double time = snareFrames.get(i) * (double) HOP / sr;
            boolean before = false, after = false;
            for (Double b : beat) {
                double d = b.doubleValue() - time;
                if (d < 0 && d >= -PEDAL_ALONE) before = true;
                if (d >= 0 && d <= PEDAL_ALONE) after = true;
            }
            if (before || after && time - lastPedal > PEDAL_RUN) continue;
            lastPedal = time;
            for (int h = 0; h < hits.size(); h++) {
                Hit x = hits.get(h);
                if (x.note == SNARE_NOTE && x.time == time) hits.set(h, new Hit(time, PEDAL_HAT, x.strength));
            }
        }
    }

    /** The band's level 3 frames before the onset is under QUIET_BEFORE of its loud level. */
    static boolean quietBefore(double[] level, int t) {
        return t < 3 || level[t - 3] < QUIET_BEFORE;
    }

    /**
     * The loudest frequency (40-400 Hz) over each frame and the next three, interpolated between
     * bins: a kick's, a tom's or a snare's body pitch at an onset. Not its rise: a kick ringing on
     * under the next one would leave only the new kick's overtones.
     */
    static double[] lowPitch(float[][] mag, double sr, int maxBin) {
        int lo = Math.max(1, bin(40, sr, maxBin)), hi = Math.min(maxBin - 1, bin(400, sr, maxBin));
        double[] out = new double[mag.length];
        double[] r = new double[hi + 2];
        for (int t = 0; t < mag.length; t++) {
            for (int k = lo - 1; k <= hi + 1; k++) {
                double v = 0;
                for (int f = t; f <= Math.min(mag.length - 1, t + 3); f++) v += mag[f][k];
                r[k] = v;
            }
            int best = lo;
            for (int k = lo; k <= hi; k++) if (r[k] > r[best]) best = k;
            double den = r[best - 1] - 2 * r[best] + r[best + 1];
            double off = Math.abs(den) < 1e-12 ? 0 : Math.max(-0.5, Math.min(0.5, 0.5 * (r[best - 1] - r[best + 1]) / den));
            out[t] = (best + off) * sr / FFT_SIZE;
        }
        return out;
    }

    /** Whether pitch `p` already sounded 3 and 2 frames before frame t (within PITCH_HELD). */
    static boolean heldBefore(double[] lowPitch, int t, double p) {
        if (t < 3) return false;
        return Math.abs(lowPitch[t - 3] - p) <= PITCH_HELD * p && Math.abs(lowPitch[t - 2] - p) <= PITCH_HELD * p;
    }

    /**
     * The loudest frequency (40-400 Hz) of what is new at frame t: the spectrum over it and the next
     * three frames above the spectrum 3 frames before. A note held from before cancels out.
     */
    static double risePitch(float[][] mag, int t, double sr) {
        int maxBin = mag[0].length - 1;
        int lo = Math.max(1, bin(40, sr, maxBin)), hi = Math.min(maxBin - 1, bin(400, sr, maxBin));
        float[] pre = mag[Math.max(0, t - 3)];
        double[] r = new double[hi + 2];
        for (int k = lo - 1; k <= hi + 1; k++) {
            double v = 0;
            for (int f = t; f <= Math.min(mag.length - 1, t + 3); f++) v += Math.max(0, mag[f][k] - pre[k]);
            r[k] = v;
        }
        int best = lo;
        for (int k = lo; k <= hi; k++) if (r[k] > r[best]) best = k;
        double den = r[best - 1] - 2 * r[best] + r[best + 1];
        double off = Math.abs(den) < 1e-12 ? 0 : Math.max(-0.5, Math.min(0.5, 0.5 * (r[best - 1] - r[best + 1]) / den));
        return (best + off) * sr / FFT_SIZE;
    }

    /** How much of the onset's rise a band keeps 5-8 frames (60-90 ms) on. */
    static double wiresLeft(double[] level, int t) {
        return left(level, t, 5, 8);
    }

    /**
     * How much of the onset's rise (above the level 3 frames before) a band keeps frames a..b on.
     * What already sounded before, a guitar or a ringing cymbal, does not count as ringing.
     */
    static double left(double[] level, int t, int a, int b) {
        double peak = 0;
        for (int k = Math.max(0, t - 1); k <= Math.min(level.length - 1, t + 2); k++) peak = Math.max(peak, level[k]);
        double pre = t >= 3 ? level[t - 3] : 0;
        if (peak - pre < 1e-9) return 0;
        double s = 0;
        int c = 0;
        for (int k = t + a; k <= Math.min(level.length - 1, t + b); k++) {
            s += level[k];
            c++;
        }
        return c == 0 ? 0 : Math.max(0, s / c - pre) / (peak - pre);
    }

    /** Largest value within one frame of t. Onsets in different bands can land a frame apart. */
    static double near(double[] o, int t) {
        double v = o[t];
        if (t > 0) v = Math.max(v, o[t - 1]);
        if (t + 1 < o.length) v = Math.max(v, o[t + 1]);
        return v;
    }

    static void mark(boolean[] at, int t, int r) {
        for (int i = Math.max(0, t - r); i <= Math.min(at.length - 1, t + r); i++) at[i] = true;
    }

    static void normalize(double[] o) {
        double[] s = o.clone();
        Arrays.sort(s);
        double p = s[(int) (0.98 * (s.length - 1))];
        if (p < 1e-9) p = 1e-9;
        for (int i = 0; i < o.length; i++) o[i] /= p;
    }

    static List<Integer> pickPeaks(double[] o, double delta) {
        return pickPeaks(o, delta, MIN_GAP_FRAMES);
    }

    static List<Integer> pickPeaks(double[] o, double delta, int minGap) {
        final int W = 3, MEAN_W = 16;
        List<Integer> peaks = new ArrayList<Integer>();
        int n = o.length, last = -1000;
        for (int t = 0; t < n - 1; t++) {
            double v = o[t];
            if (v < delta) continue;
            boolean isMax = true;
            for (int i = Math.max(0, t - W); i <= Math.min(n - 1, t + W); i++) {
                if (o[i] > v) { isMax = false; break; }
            }
            if (!isMax) continue;
            int a = Math.max(0, t - MEAN_W), b = Math.min(n - 1, t + MEAN_W);
            double mean = 0;
            for (int i = a; i <= b; i++) mean += o[i];
            mean /= (b - a + 1);
            if (v <= mean + delta) continue;
            if (!peaks.isEmpty() && t - last < minGap) {
                if (v > o[last]) {
                    peaks.set(peaks.size() - 1, Integer.valueOf(t));
                    last = t;
                }
                continue;
            }
            peaks.add(Integer.valueOf(t));
            last = t;
        }
        return peaks;
    }

    static double estimateBpm(double[] env, double fps) {
        int n = env.length;
        int minLag = Math.max(2, (int) Math.floor(fps * 60 / 180.0));
        int maxLag = (int) Math.ceil(fps * 60 / 60.0);
        if (maxLag + 2 >= n) return 120;
        double mean = 0;
        for (int i = 0; i < env.length; i++) mean += env[i];
        mean /= n;
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = env[i] - mean;

        int acLen = Math.min(n, maxLag * 4 / 3 + 4);
        double[] ac = new double[acLen];
        for (int lag = minLag - 1; lag < acLen; lag++) {
            double s = 0;
            for (int i = 0; i + lag < n; i++) s += x[i] * x[i + lag];
            ac[lag] = s / (n - lag);
        }
        int bestLag = minLag;
        double best = -Double.MAX_VALUE;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double bpm = 60 * fps / lag;
            double oct = Math.log(bpm / 120.0) / Math.log(2);
            double score = ac[lag] * Math.exp(-0.5 * oct * oct / (0.8 * 0.8));
            if (score > best) { best = score; bestLag = lag; }
        }
        // A shuffle (triplet swing): the dotted beat (3 triplets) can repeat best, reading 2/3 of the
        // tempo; where the beat (2/3 of that lag) repeats nearly as well (SHUFFLE_BEAT), it is the beat.
        // In straight time that lag falls between the notes.
        int beat = (int) Math.round(bestLag * 2.0 / 3.0);
        if (beat >= minLag && beat - 1 >= 0 && 60 * fps / beat <= 180) {
            double here = Math.max(ac[beat], Math.max(ac[beat - 1], ac[beat + 1]));
            if (here >= SHUFFLE_BEAT * ac[bestLag]) bestLag = ac[beat - 1] > ac[beat] && ac[beat - 1] >= ac[beat + 1] ? beat - 1 : ac[beat + 1] > ac[beat] ? beat + 1 : beat;
            else bestLag = dotted(ac, bestLag, maxLag);
        } else {
            bestLag = dotted(ac, bestLag, maxLag);
        }
        double a = ac[bestLag - 1], b = ac[bestLag], c = ac[bestLag + 1];
        double denom = a - 2 * b + c;
        double lagRef = bestLag + (denom != 0 ? clampD(0.5 * (a - c) / denom, -1, 1) : 0);
        return 60 * fps / lagRef;
    }

    /** The beat 4/3 of the lag, where it repeats DOTTED_BEAT as well as the lag (dotted figures). */
    static int dotted(double[] ac, int bestLag, int maxLag) {
        int slow = (int) Math.round(bestLag * 4.0 / 3.0);
        if (slow > maxLag || slow + 1 >= ac.length || ac[bestLag] <= 0) return bestLag;
        int at = slow;
        if (ac[slow - 1] > ac[at]) at = slow - 1;
        if (ac[slow + 1] > ac[at]) at = slow + 1;
        return ac[at] >= DOTTED_BEAT * ac[bestLag] ? at : bestLag;
    }

    static double[] fitBeatGrid(double[] env, double fps, double lo, double hi) {
        double bestScore = -Double.MAX_VALUE, bestBpm = lo, bestPhase = 0;
        for (double bpm = lo; bpm <= hi + 1e-9; bpm += 0.02) {
            double period = 60.0 * fps / bpm;
            for (double ph = 0; ph < period; ph += 0.5) {
                double s = 0;
                int count = 0;
                for (double pos = ph; pos < env.length - 1; pos += period) {
                    int i = (int) pos;
                    double fr = pos - i;
                    s += env[i] * (1 - fr) + env[i + 1] * fr;
                    count++;
                }
                s /= Math.max(count, 1);
                if (s > bestScore) { bestScore = s; bestBpm = bpm; bestPhase = ph; }
            }
        }
        return new double[] {bestBpm, bestPhase / fps};
    }

    static double clampD(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}