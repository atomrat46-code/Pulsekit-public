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
    /**
     * A kit whose kick has no beater click (an 808's sine, DigiDrum's, Beat DRMR's: its 2-6 kHz energy
     * over its 40-150 Hz energy in its first 46 ms reads 0.00001-0.00002; sampled kicks 0.00025 (the
     * darkest, Artillery Fat Metal) to 0.53) has its kicks found without one, when its kit profile
     * (--kit) says so: under KICK_CLICKLESS.
     */
    static final double KICK_CLICKLESS = 0.0001;
    /**
     * A kit's toms over the ring of the hit before (its profile's "tomjump": the tom band's level jump at
     * the test beat's 2nd and 3rd hit of each tom, half a second after the one before): a ringy, roomy
     * kit's toms jump less, and the tom's level jump asked for (TOM_JUMP) is lowered to match. Under
     * TOMJUMP_LOW (a boomy room: much in the tom band moves) 0.2, under TOMJUMP_MID 0.15, under TOMJUMP_HIGH 0.25.
     */
    static final double TOMJUMP_LOW = 0.63, TOMJUMP_MID = 0.85, TOMJUMP_HIGH = 0.95;
    static final double TOMJUMP_WINDOW_BEFORE = 0.02, TOMJUMP_WINDOW_AFTER = 0.08;

    /** The tom level jump asked for with a kit profile's "tomjump" (TOM_JUMP without one). */
    static double tomJumpFor(double tomJump) {
        if (tomJump < 0) return TOM_JUMP;
        if (tomJump < TOMJUMP_LOW) return 0.2;
        if (tomJump < TOMJUMP_MID) return 0.15;
        if (tomJump < TOMJUMP_HIGH) return 0.25;
        return TOM_JUMP;
    }
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

    /**
     * A choked cymbal: cut off by hand. Its highs (3-16 kHz) peak within CHOKE_SWELL of the hit (a sampled
     * crash swells: AVL Drumkits' peak 110-180 ms on), and CHOKE_AFTER to CHOKE_END after that peak they
     * are CHOKE_LEVEL of it or less. AVL's Black Pearl there: crashes left to ring 0.55-0.79, choked
     * 0.007-0.034; its ride 0.13, choked 0.037. No other hit may come from 30 ms after it to CHOKE_END after
     * its peak (another drum would hold the level up, or the next cymbal end it); the same cymbal found
     * again within CHOKE_SWELL is the same hit (a swelling crash is).
     */
    static final double CHOKE_LEVEL = 0.07, CHOKE_SWELL = 0.3, CHOKE_AFTER = 0.3, CHOKE_END = 0.4;

    /** A --chokes value: the choke as aftertouch on the cymbal's own note (Pulsekit's chokes, e-drums, drum plugins). */
    static final int CHOKE_AT = -1;
    /** The cymbals `--chokes at` alone covers: crash, crash 2, china, splash, ride, ride bell. */
    static final int[] CHOKE_CYMBALS = {49, 57, 52, 55, 51, 53};

    /**
     * --chokes' notes: "49=50,51=52" (the cymbal found = the note a choked one is written as), "49=at" (the
     * cymbal's hit kept and an aftertouch on its note where it was cut: how Pulsekit's Crash choke and Ride
     * choke tracks read), or "at" alone (every crash and ride as that).
     */
    static java.util.Map<Integer, Integer> chokeNotes(String text) {
        java.util.Map<Integer, Integer> out = new java.util.LinkedHashMap<Integer, Integer>();
        if ("at".equalsIgnoreCase(text.trim())) {
            for (int n : CHOKE_CYMBALS) out.put(Integer.valueOf(n), Integer.valueOf(CHOKE_AT));
            return out;
        }
        for (String pair : text.split("[,;\\s]+")) {
            if (pair.trim().length() == 0) continue;
            String[] ab = pair.split("=");
            if (ab.length != 2) throw new IllegalArgumentException("--chokes takes note=note pairs (49=50,51=52), note=at or at: " + pair);
            if ("at".equalsIgnoreCase(ab[1].trim())) {
                int a = Integer.parseInt(ab[0].trim());
                if (a < 0 || a > 127) throw new IllegalArgumentException("--chokes: MIDI notes are 0 to 127: " + pair);
                out.put(Integer.valueOf(a), Integer.valueOf(CHOKE_AT));
                continue;
            }
            int a = Integer.parseInt(ab[0].trim()), b = Integer.parseInt(ab[1].trim());
            if (a < 0 || a > 127 || b < 0 || b > 127) throw new IllegalArgumentException("--chokes: MIDI notes are 0 to 127: " + pair);
            out.put(Integer.valueOf(a), Integer.valueOf(b));
        }
        return out;
    }

    static String chokeText(java.util.Map<Integer, Integer> m) {
        StringBuilder b = new StringBuilder();
        for (java.util.Map.Entry<Integer, Integer> e : m.entrySet()) b.append(b.length() == 0 ? "" : ",").append(e.getKey()).append('=').append(e.getValue().intValue() == CHOKE_AT ? "at" : e.getValue().toString());
        return b.toString();
    }

    /** The level (RMS) of the signal's highs (its second difference: 3-16 kHz mostly) from a to b seconds. */
    static double highLevel(double[] x, double sr, double a, double b, boolean peak) {
        int i0 = Math.max(2, (int) (a * sr)), i1 = Math.min(x.length, (int) (b * sr));
        int win = Math.max(1, (int) (0.01 * sr));
        double best = 0, sum = 0;
        int n = 0;
        for (int i = i0; i + win <= i1; i += win) {
            double e = 0;
            for (int j = i; j < i + win; j++) {
                double d = x[j] - 2 * x[j - 1] + x[j - 2];
                e += d * d;
            }
            double r = Math.sqrt(e / win);
            best = Math.max(best, r);
            sum += r;
            n++;
        }
        return peak ? best : n == 0 ? 0 : sum / n;
    }

    /** Crashes and rides cut off by hand (CHOKE_LEVEL) written as --chokes' notes for them. */
    static void chokes(List<Hit> hits, double[] x, double sr, java.util.Map<Integer, Integer> notes) {
        List<Hit> sorted = new ArrayList<Hit>(hits);
        sorted.sort(Comparator.comparingDouble(new java.util.function.ToDoubleFunction<Hit>() {
            public double applyAsDouble(Hit h) { return h.time; }
        }));
        java.util.Map<Integer, Integer> count = new java.util.TreeMap<Integer, Integer>();
        java.util.Set<Hit> same = new java.util.HashSet<Hit>();
        java.util.Map<Hit, Integer> choked = new java.util.HashMap<Hit, Integer>();
        int unknown = 0;
        List<Hit> cuts = new ArrayList<Hit>();
        for (Hit h : sorted) {
            Integer to = notes.get(Integer.valueOf(h.note));
            if (to == null || same.contains(h)) continue;
            // Its peak: the loudest 10 ms of its highs within CHOKE_SWELL.
            double peak = 0, peakAt = h.time;
            for (double t = Math.max(0, h.time - 0.02); t < h.time + CHOKE_SWELL; t += 0.01) {
                double l = highLevel(x, sr, t, t + 0.01, true);
                if (l > peak) {
                    peak = l;
                    peakAt = t;
                }
            }
            List<Hit> again = new ArrayList<Hit>();
            boolean clear = true;
            for (Hit o : sorted) {
                if (o == h || o.time <= h.time + 0.03) continue;
                if (o.time >= peakAt + CHOKE_END) break;
                if (o.note == h.note && o.time < h.time + CHOKE_SWELL) {
                    again.add(o);
                    continue;
                }
                clear = false;
                break;
            }
            same.addAll(again);
            if (!clear) {
                unknown++;
                continue;
            }
            double later = highLevel(x, sr, peakAt + CHOKE_AFTER, peakAt + CHOKE_END, false);
            if (peak <= 0 || later > CHOKE_LEVEL * peak) continue;
            if (to.intValue() == CHOKE_AT) {
                // Where it was cut: its highs first under a quarter of the peak (after the peak).
                double cut = peakAt + CHOKE_AFTER;
                for (double t = peakAt + 0.01; t < peakAt + CHOKE_AFTER; t += 0.01) {
                    if (highLevel(x, sr, t, t + 0.01, true) < peak * 0.25) {
                        cut = t;
                        break;
                    }
                }
                cuts.add(new Hit(cut, -h.note, 1.0));
            } else {
                choked.put(h, to);
                for (Hit o : again) choked.put(o, to);
            }
            count.put(Integer.valueOf(h.note), Integer.valueOf((count.containsKey(Integer.valueOf(h.note)) ? count.get(Integer.valueOf(h.note)).intValue() : 0) + 1));
        }
        for (int i = 0; i < hits.size(); i++) {
            Integer to = choked.get(hits.get(i));
            if (to != null) hits.set(i, new Hit(hits.get(i).time, to.intValue(), hits.get(i).strength));
        }
        // An aftertouch choke: a hit of the negative note (written as aftertouch 127 on the note).
        hits.addAll(cuts);
        StringBuilder b = new StringBuilder();
        for (java.util.Map.Entry<Integer, Integer> e : count.entrySet()) b.append(b.length() == 0 ? "" : ", ").append(e.getValue()).append(" of ").append(e.getKey())
            .append(" as ").append(notes.get(e.getKey()).intValue() == CHOKE_AT ? "aftertouch" : notes.get(e.getKey()).toString());
        note("Chokes (--chokes " + chokeText(notes) + "): " + (b.length() == 0 ? "none found" : b.toString()) + (unknown > 0 ? "; " + unknown + " not told (another hit within "
            + num(CHOKE_END * 1000) + " ms of its peak)" : ""));
    }

    static class Hit {
        final double time;
        final int note;
        final double strength;
        /** A closed hat rideOrHats made a ride (by the hats and rides around it). */
        boolean fromHat;
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
        inDir = false;
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
    /** Running on a folder's files (--dir). */
    static boolean inDir;

    /**
     * --drums-only: only songs of drums alone (for training data: a vst_render run of their MIDI). A song
     * where another instrument is heard (DRUMS_ONLY_BASS, DRUMS_ONLY_TONE: stricter than the rules' bass and
     * guitar, SogniMusic's drum sessions 77 of 99 kept) or named (drums_bass, drums_guitar) is skipped.
     */
    static boolean drumsOnly;
    static final double DRUMS_ONLY_BASS = 0.15, DRUMS_ONLY_TONE = 0.5;

    /** A song --drums-only leaves out, and why. */
    static final class Skipped extends RuntimeException {
        Skipped(String why) {
            super(why);
        }
    }

    /** Its name says another instrument plays with the drums ("...-drums_guitar-sft.mp3"). */
    static boolean namedWithOthers(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).matches(".*drums_[a-z].*");
    }
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
        // Zips in the folder (a SogniMusic run's, say): their WAV and MP3 files, unpacked into a folder
        // of the zip's name beside it, are done too (their MIDI beside them there).
        List<File> zips = new ArrayList<File>();
        zipsIn(dir, recursive, zips);
        for (File z : zips) {
            checkStop();
            File to = new File(z.getParentFile(), z.getName().replaceAll("(?i)\\.zip$", ""));
            int n = unzipAudio(z, to, wavs);
            System.out.println(z.getName() + ": " + n + " audio file" + (n == 1 ? "" : "s") + " unpacked into " + to.getName());
        }
        if (wavs.isEmpty()) {
            finish("Failed: no WAV or MP3 files (or zips of them) in " + dir.getName());
            return;
        }
        String keepLog = logPath;
        logPath = null;
        int done = 0, skipped = 0;
        inDir = true;
        for (int k = 0; k < wavs.size(); k++) {
            checkStop();
            File w = wavs.get(k);
            File mid = new File(w.getParentFile(), w.getName().replaceAll("(?i)\\.(wave?|mp3)$", "") + "-dm.mid");
            List<String> one = new ArrayList<String>();
            one.add(w.getPath());
            one.add(mid.getPath());
            one.addAll(opts);
            System.out.println("== " + (k + 1) + "/" + wavs.size() + ": " + w.getName());
            long before = mid.isFile() ? mid.lastModified() : -1;
            mid.delete();
            if (opts.contains("--drums-only") && namedWithOthers(w.getName())) {
                System.out.println("Skipped " + w.getName() + ": its name says another instrument (--drums-only)");
                skipped++;
                continue;
            }
            try {
                run(one.toArray(new String[0]));
            } catch (Skipped ex) {
                System.out.println("Skipped " + w.getName() + ": " + ex.getMessage());
                skipped++;
            } catch (IllegalStateException ex) {
                if ("Aborted".equals(ex.getMessage())) throw ex;
                System.out.println("Failed " + w.getName() + ": " + ex.getMessage());
            } catch (Exception ex) {
                System.out.println("Failed " + w.getName() + ": " + ex.getMessage());
            }
            if (mid.isFile() && mid.lastModified() != before) done++;
        }
        logPath = keepLog;
        inDir = false;
        // --make-set on a folder: the songs' sets also made one (each drum from the song where it is clearest),
        // said last so Pulsekit makes that one its drum set.
        String combined = "";
        if (opts.contains("--make-set")) {
            File out = new File(dir, dir.getName() + " combined-set");
            List<File> sets = SetMaker.setsIn(dir, out);
            if (sets.size() > 1) combined = "; " + SetMaker.combine(sets, out, dir.getName());
        }
        // --zip: the folder's MIDI files (and the log) as <folder>-dm.zip in the folder (or --zip_outdir).
        zipBase = dir.getName() + "-dm";
        zipDir = dir;
        finish("Succeeded: DrumMidi on " + done + " of " + wavs.size() + " audio files in " + dir.getName() + " (<name>-dm.mid beside each)"
                + (skipped > 0 ? "; " + skipped + " skipped (--drums-only: another instrument heard or named)" : "") + combined);
    }

    static void wavsIn(File dir, boolean recursive, List<File> into) {
        File[] in = dir.listFiles();
        if (in == null) return;
        Arrays.sort(in);
        for (File f : in) {
            String low = f.getName().toLowerCase(java.util.Locale.ROOT);
            if (f.isFile() && (low.endsWith(".wav") || low.endsWith(".wave") || low.endsWith(".mp3"))) into.add(f);
        }
        if (recursive) for (File f : in) if (f.isDirectory() && !f.getName().startsWith(".")) wavsIn(f, true, into);
    }

    static void zipsIn(File dir, boolean recursive, List<File> into) {
        File[] in = dir.listFiles();
        if (in == null) return;
        Arrays.sort(in);
        for (File f : in) if (f.isFile() && f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".zip") && !f.getName().endsWith("-dm.zip")) into.add(f);
        if (recursive) for (File f : in) if (f.isDirectory() && !f.getName().startsWith(".")) zipsIn(f, true, into);
    }

    /** The zip's WAV and MP3 files into `to` (their folders inside the zip left out); each added to `into`. How many. */
    static int unzipAudio(File zip, File to, List<File> into) {
        int n = 0;
        try {
            java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(zip));
            try {
                for (java.util.zip.ZipEntry e; (e = zis.getNextEntry()) != null; ) {
                    if (e.isDirectory()) continue;
                    String name = e.getName().replaceAll(".*[/\\\\]", "");
                    String low = name.toLowerCase(java.util.Locale.ROOT);
                    if (name.startsWith(".") || !(low.endsWith(".wav") || low.endsWith(".wave") || low.endsWith(".mp3"))) continue;
                    to.mkdirs();
                    File out = new File(to, name);
                    FileOutputStream fos = new FileOutputStream(out);
                    try {
                        byte[] b = new byte[65536];
                        for (int r; (r = zis.read(b)) > 0; ) fos.write(b, 0, r);
                    } finally {
                        fos.close();
                    }
                    if (!into.contains(out)) into.add(out);
                    n++;
                }
            } finally {
                zis.close();
            }
        } catch (Exception ex) {
            System.out.println("Could not unpack " + zip.getName() + ": " + ex.getMessage());
        }
        return n;
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
        // --combine-sets <folder> [<out folder>]: the song drum sets (--make-set's <song>-set folders) in the
        // folder made one: each drum from the set where it is clearest (SetMaker.combine).
        if (args.length > 0 && "--combine-sets".equals(args[0])) {
            if (args.length < 2 || !new File(args[1]).isDirectory()) {
                finish("Failed: --combine-sets needs a folder with drum sets from songs (DrumMidi --make-set's <song>-set folders)");
                return;
            }
            File dir = new File(args[1]).getAbsoluteFile();
            File out = args.length > 2 ? new File(args[2]).getAbsoluteFile() : new File(dir, dir.getName() + " combined-set");
            // Named after the folder it is written to ("Doom combined-set" is Doom), else the songs' folder.
            String named = out.getName().replaceAll("(?i)[ _-]*(combined)?[ _-]*set$", "").trim();
            String said = SetMaker.combine(SetMaker.setsIn(dir, out), out, named.isEmpty() ? dir.getName() : named);
            finish(said.startsWith("Drum set") ? "Succeeded: " + said : "Failed: " + said);
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
                    + "[--sens N] [--hat N] [--tom N] [--ride N] [--crash N] [--bpm N] [--quantize N] [--no-hpss] [--no-cymbals] [--no-toms] [--snare-leak] [--faded-opens] [--no-cymbal-model] [--no-hit-model] [--hit-model <file>] [--drums-only] [--separate] [--keep-stems] [--drum-stem] [--make-set] [--set-hits N] [--chokes <notes>] [--kit <file.kit>] [--library <file.kit>] [--log <logfile>] [--dir <folder>] [--recursive] [--zip] [--zip_mb N] [--zip_outdir <folder>]");
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
        boolean hitModel = !"0".equals(System.getProperty("dm.hitModel", "1"));
        drumsOnly = false;
        separate = false;
        keepStems = false;
        keptStems = null;
        drumStem = false;
        makeSet = false;
        setHits = 1;
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
        // Choked cymbals (--chokes 49=50,51=52,57=58: a crash or ride cut off by hand written as that note,
        // as a kit with choke notes has them: x42 AVL Drumkits; --chokes at: the hit kept and an aftertouch
        // on its note where it was cut, as Pulsekit's choke tracks read it). Empty: written as any other hit.
        java.util.Map<Integer, Integer> chokeNotes = new java.util.LinkedHashMap<Integer, Integer>();
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
            else if ("--no-hit-model".equals(opt)) hitModel = false;
            else if ("--drums-only".equals(opt)) drumsOnly = true;
            // The drums separated from the song first (Demucs, on a computer: setup_demucs.py), then told apart.
            else if ("--separate".equals(opt)) separate = true;
            // --separate's stems kept as WAVs beside the MIDI (drums, bass, vocals, other): an old song's parts.
            else if ("--keep-stems".equals(opt)) keepStems = true;
            // A drums stem already (Demucs's drums.wav, or another separation): the drum-stem mode below.
            else if ("--drum-stem".equals(opt)) drumStem = true;
            // A drum set from the song's own drums: a clean hit of each drum cut out (SetMaker), beside the MIDI.
            else if ("--make-set".equals(opt)) makeSet = true;
            // --set-hits N: that many hits of each drum kept (the best first): a render plays them as velocity layers.
            else if ("--set-hits".equals(opt)) setHits = Math.max(1, Math.min(8, Integer.parseInt(args[++i])));
            // A hit model learned elsewhere (vst_render's Hit model tab: hit-model.txt) in place of the built-in one;
            // "ugritone": the built-in one tuned on Ugritone kits' renders (HitModel.UGRITONE).
            else if ("--hit-model".equals(opt)) {
                String v = args[++i];
                if (v.equalsIgnoreCase("ugritone") && !new File(v).isFile()) System.setProperty("dm.hitModelName", "ugritone");
                else System.setProperty("dm.hitModelFile", new File(v).getAbsolutePath());
            }
            else if ("--snare-leak".equals(opt)) snareLeak = true;
            else if ("--faded-opens".equals(opt)) fadedOpens = true;
            else if ("--no-cymbal-model".equals(opt)) cymModel = false;
            else if ("--chokes".equals(opt)) chokeNotes = chokeNotes(args[++i]);
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
                + (hitModel ? "" : " --no-hit-model") + (System.getProperty("dm.hitModelFile") != null ? " --hit-model " + new File(System.getProperty("dm.hitModelFile")).getName()
                    : System.getProperty("dm.hitModelName") != null ? " --hit-model " + System.getProperty("dm.hitModelName") : "")
                + (chokeNotes.isEmpty() ? "" : " --chokes " + chokeText(chokeNotes))
                + (kit != null ? " --kit " + kit.name : "") + (library != null ? " --library " + library.name : ""));
        if (library != null && kit != null) note("Library " + library.name + " not used: the kit profile " + kit.name + " is this kit's own");
        else if (library != null) note("Library " + library.name + ": kick and toms from " + library.said + " (its cymbals are not used)");
        File heard = inFile;
        if (separate && keepStems) {
            // All four stems (kept beside the MIDI once it is written); DrumMidi listens to a copy of the drums.
            java.util.Map<String, File> st = Demucs.stems(inFile, false, "Separate the stems", "the drums, bass, vocals and the rest");
            File stem = null;
            if (st != null && st.get("drums") != null) {
                try {
                    stem = File.createTempFile("dm-heard", ".wav");
                    java.nio.file.Files.copy(st.get("drums").toPath(), stem.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ex) {
                    stem = null;
                }
            }
            if (stem != null) {
                keptStems = st;
                note("Separated the song with Demucs (drums, bass, vocals, other): DrumMidi listens to the drums alone");
                heard = stem;
                drumStem = true;
            } else {
                if (st != null) for (File f : st.values()) f.delete();
                note("Without the separation: the whole song, its other instruments heard as they are (fewer and less sure hits)");
            }
        } else if (separate) {
            File stem = Demucs.drums(inFile);
            if (stem != null) {
                heard = stem;
                drumStem = true;
            } else note("Without the separation: the whole song, its other instruments heard as they are (fewer and less sure hits)");
        } else if (keepStems) note("--keep-stems keeps --separate's stems: without --separate there are none");
        if (drumStem) note("Drum-stem mode: the drums alone (no bass, guitar or keys guards)");
        Audio audio = readAudio(heard);
        if (heard != inFile) heard.delete();
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
        final boolean guitarLine0 = guitar;
        // 2-8 kHz before HPSS: how a snare's wires rise and die over a guitar (GuitarSnare).
        double[] upper = bandMean(mag, bin(2000, sr, maxBin), bin(8000, sr, maxBin));
        // 1-4 kHz and 150-600 Hz before HPSS: a guitar's chord against a hat (GuitarHat).
        double[] midRaw = bandMean(mag, bin(1000, sr, maxBin), bin(4000, sr, maxBin));
        double[] lowMidRaw = bandMean(mag, bin(150, sr, maxBin), bin(600, sr, maxBin));
        double[] hatLevel = hatRaw.clone();
        normalize(hatLevel);
        normalize(kickLevel);
        normalize(bodyLevel);
        normalize(tomLevel);
        Levels lv = new Levels(kickLevel, bodyLevel, noiseLevel, bodyRaw, gapRaw, hatLevel);
        // A kit whose kick has no beater click (its profile says so: an 808's long sine): its kicks are
        // found by the kick band alone, and before HPSS, which takes much of such a kick away as a held tone.
        final boolean clickless = kit != null && kit.kickClick >= 0 && kit.kickClick < KICK_CLICKLESS;
        double[] kickBeforeHpss = clickless ? flux(mag, bin(KICK_LO, sr, maxBin), bin(KICK_HI, sr, maxBin)) : null;
        double midTone = -1;
        if (hpss) {
            System.out.println("Separating percussive content (a few seconds)...");
            float[][] raw = mag.clone();
            applyHpss(mag);
            midTone = toneShare(raw, mag, bin(GUITAR_TONE_LO, sr, maxBin), bin(GUITAR_TONE_HI, sr, maxBin));
            progress(70);
        }
        // Guitars or keys held over the drums: HPSS takes GUITAR_TONE of the 250-2000 Hz band away as held
        // tones (SogniMusic's guitar sessions: 0.71 median, nine in ten over 0.47; its drum sessions 0.36,
        // three in four under 0.47; a bench of kits alone 0.30). guitarLine alone heard 16% of the guitar sessions.
        if (midTone >= GUITAR_TONE) guitar = true;
        if (drumStem) guitar = false;

        double[] kick = clickless ? kickBeforeHpss : flux(mag, bin(KICK_LO, sr, maxBin), bin(KICK_HI, sr, maxBin));
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

        // Kick onsets only count where the beater clicks. Bass notes have no click. A kit whose kick has
        // none (its profile says so: an 808's) has its kicks found by the kick band alone.
        // A ringy, roomy kit's toms jump less over the ring before them (its profile's tomjump).
        final double tomJumpAsked = tomJumpFor(kit == null ? -1 : kit.tomJump);
        if (tomJumpAsked != TOM_JUMP) note("Kit " + kit.name + ": its toms jump less over the ring before them (" + String.format(java.util.Locale.ROOT, "%.2f", kit.tomJump)
            + "): a tom's level jump of " + num(tomJumpAsked) + " is enough");
        if (clickless) note("Kit " + kit.name + ": its kick has no beater click (" + String.format(java.util.Locale.ROOT, "%.5f", kit.kickClick)
            + "): kicks are found without one");
        double[] kickOnset = new double[kick.length];
        for (int t = 0; t < kick.length; t++) {
            double g = clickless ? 1.0 : Math.min(1.0, near(snareNoise, t) / KICK_CLICK);
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
        // Over a guitar its chords' attacks look like snares and hide the snares' body: the snares
        // are told by GuitarSnare's weighing of what each onset does instead (a bench of kits with a
        // guitar over them, kits it was not fitted on: snares 54% right and 54% found -> 76% and 77%).
        // (The kicks keep asking the first pass's snares: GuitarSnare takes some toms' onsets for snares'
        // that the kick pass would have found, then written as floor toms.)
        List<Integer> kickSnares = new ArrayList<Integer>(snarePeaks);
        if (guitar) {
            // The first pass's snares at a tom's pitch (snareToms) stay toms.
            List<Hit> first = new ArrayList<Hit>();
            for (Hit h : hits) if (h.note == SNARE_NOTE) first.add(h);
            if (writeToms) snareToms(first, lowPitch, lv.kick, sr);
            for (int h = hits.size() - 1; h >= 0; h--) if (hits.get(h).note == SNARE_NOTE) hits.remove(h);
            snarePeaks.clear();
            snareBodies.clear();
            for (Integer p : pickPeaks(snare, delta * GHOST_DELTA, 3)) {
                int t = p.intValue();
                if (GuitarSnare.chance(lv, mag, loudSnare, upper, snare, t) < GuitarSnare.TAKE) continue;
                snarePeaks.add(p);
                snareBodies.add(Double.valueOf(jump(bodyLevel, t)));
                hits.add(new Hit(t * (double) HOP / sr, SNARE_NOTE, snare[t]));
            }
            for (Hit h : first) {
                if (h.note != MID_TOM) continue;
                boolean taken = false;
                for (Integer p : snarePeaks) taken |= Math.abs(p.intValue() * (double) HOP / sr - h.time) < SAME_CYMBAL;
                if (!taken) hits.add(h);
            }
        }
        if (writeToms) snareToms(hits, lowPitch, lv.kick, sr);
        List<Integer> kickFrames = new ArrayList<Integer>();
        boolean bass = bassLine(kickLevel);
        boolean bassTones = bassNotes(kickLevel);
        {
            double[] ks = kickLevel.clone();
            Arrays.sort(ks);
            note("Heard: " + (bass ? "a bass line" : "no bass line") + " (kick band median " + String.format(java.util.Locale.ROOT, "%.2f", ks[ks.length / 2])
                    + "), " + (guitar ? "guitar or keys" : "no guitar") + " (held tones " + (midTone < 0 ? "-" : String.format(java.util.Locale.ROOT, "%.2f", midTone))
                    + ", 300-1000 Hz " + (guitarLine0 ? "up" : "down") + " between hits)");
        }
        if (drumsOnly) {
            double[] ks = kickLevel.clone();
            Arrays.sort(ks);
            if (bass || guitar || guitarLine0 || ks[ks.length / 2] >= DRUMS_ONLY_BASS || midTone >= DRUMS_ONLY_TONE)
                throw new Skipped("another instrument heard (--drums-only)");
        }
        // The hit model's toms held back only on a surer sign of a bass or guitar than the rules take (TOM_BASS,
        // TOM_TONE): SogniMusic's drum sessions heard as with one (15 of 99) lost their model toms.
        final boolean accSure;
        {
            double[] ks = kickLevel.clone();
            Arrays.sort(ks);
            accSure = !drumStem && (ks[ks.length / 2] >= HitModel.TOM_BASS || midTone >= HitModel.TOM_TONE || guitarLine0);
        }
        boolean[] snareAt = new boolean[kick.length];
        for (Integer p : kickSnares) mark(snareAt, p.intValue(), 1);
        for (Integer p : pickPeaks(kickOnset, delta)) {
            int t = p.intValue();
            double jk = jump(kickLevel, t);
            if (bass && !clickless ? !(bandKick(jk, jump(noiseLevel, t)) || shortLow(kickLevel, t, jk)) || jk < KICK_WITH_SNARE && maskedSnare(lv, t, sens, rideRings(rideEnergy, t)) : jk < KICK_JUMP) continue;
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
        // The kicks found (a frame either side): a cymbal struck with one has the kick's click in its bands.
        boolean[] onKick = new boolean[kick.length];
        for (Integer p : kickFrames) mark(onKick, p.intValue(), 1);
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
            if (isCrash(ride, top, rideEnergy, topEnergy, t, onKick[t]) || ride[t] >= hat[t] * 0.85 && rideRings(rideEnergy, t) && snareBody[t] < ride[t]) {
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
            if (isCrash(ride, top, rideEnergy, topEnergy, t, onKick[t])) continue;
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
            if (!isCrash(ride, top, rideEnergy, topEnergy, t, onKick[t])) continue;
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
            if (jump(tomLevel, t) < tomJumpAsked / Math.max(0.05, tomSens) || wiresLeft(noiseLevel, t) >= TOM_WIRES) continue;
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
        // Not over a bass line either: its notes ring in the tom bands (a bench of kits with a bass
        // over them: ringing toms took kicks and hats for toms, kicks 37% found, hats 65%).
        if (writeToms && !bass && !guitar) ringingToms(hits, lowRaw, ringRaw, lowPitch, fps);
        // The hit model (HitModel): kicks, snares and toms told again from every onset that could be one.
        {
            List<Integer> cand = new ArrayList<Integer>();
            for (Integer p : pickPeaks(kickOnset, delta * HitModel.CAND_DELTA, 3)) cand.add(p);
            for (Integer p : pickPeaks(snare, delta * HitModel.CAND_DELTA, 3)) cand.add(p);
            for (Integer p : pickPeaks(toms, delta * HitModel.CAND_DELTA, 3)) cand.add(p);
            for (Hit h : hits) if (h.note == KICK_NOTE || h.note == SNARE_NOTE || isTomNote(h.note)) cand.add(Integer.valueOf((int) Math.round(h.time * sr / HOP)));
            String dump = System.getProperty("dm.hitFeatures");
            if (dump != null) HitModel.dump(hits, cand, audio.samples, sr, bass, guitar, dump);
            // Also for a kit whose kick has no beater click (Tight and Total Studio Drums' kits): the bench's leave-one-out
            // had them better with it. (An 808's long sine is not: on it the model takes snares where there are none.)
            if (hitModel && HitModel.ready() && writeToms) HitModel.apply(hits, cand, audio.samples, sr, bass, guitar, floor, mid, rack, accSure);
        }
        if (kit != null) kit.sort(hits, audio.samples, sr);
        else if (library != null) library.sort(hits, audio.samples, sr);
        if (writeToms) tomClicks(hits);
        if (snareLeak && cymbals) snareLeaks(hits, mag, sr, fps);
        if (!bass) openRings(hits, shape, hatRaw, hatPeaks, sr);
        if (cymbals && fadedOpens) shortOpens(hits, audio.samples, sr, kit);
        // The kit's own hats, ride and crash (its profile's cymbals) tell the cymbals found apart.
        if (kit != null && cymbals) kit.sortCymbals(hits, audio.samples, sr);
        if (kit != null && cymbals) kit.rideVote(hits, audio.samples, sr);
        if (cymbals && System.getProperty("dm.cymFeatures") != null) CymModel.dump(hits, audio.samples, sr, System.getProperty("dm.cymFeatures"), kit);
        if (cymbals && cymModel) CymModel.apply(hits, audio.samples, sr, kit);
        if (cymbals && guitar) GuitarHat.drop(hits, hatRaw, midRaw, lowMidRaw, sr);
        if (cymbals && !chokeNotes.isEmpty()) chokes(hits, audio.samples, sr, chokeNotes);
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
        int kicks = 0, snares = 0, closed = 0, pedals = 0, open = 0, rides = 0, crashes = 0, floors = 0, mids = 0, racks = 0, chokesWritten = 0;
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            long tick = Math.round(h.time * tickPerSec);
            if (gridTicks > 0) {
                long steps = Math.round((tick - phaseTick) / gridTicks);
                tick = Math.max(0, Math.round(phaseTick + steps * gridTicks));
            }
            if (!seen.add(h.note + ":" + tick)) continue;
            if (!writeToms && (h.note == FLOOR_TOM || h.note == MID_TOM || h.note == RACK_TOM)) continue;
            if (h.note < 0) {
                // A choke (--chokes at): aftertouch 127 on the cymbal's note, let go a tick later.
                events.add(new MidiEv(tick, shortMsg(0xA0, -h.note, 127)));
                events.add(new MidiEv(tick + 1, shortMsg(0xA0, -h.note, 0)));
                chokesWritten++;
                continue;
            }
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
        keepStemsBeside(written, inFile);
        note(String.format(java.util.Locale.ROOT, "Wrote %s: %d kicks, %d snares, %d closed hats, %d pedal hats, %d open hats, %d rides, %d crashes, %d rack toms, %d mid toms, %d floor toms",
                written.getName(), kicks, snares, closed, pedals, open, rides, crashes, racks, mids, floors) + (chokesWritten > 0 ? ", " + chokesWritten + " chokes (aftertouch)" : "") + (writeToms ? "" : " (--no-toms: toms not written)"));
        // (A folder's run zipped: "MIDI written", so PyJav on the phone does not keep each one beside the zip.)
        note((zip && inDir ? "MIDI written: " : "MIDI Extracted to: ") + written.getAbsolutePath());
        if (makeSet) {
            String base = written.getName().replaceAll("(?i)\\.midi?$", "");
            File setDir = new File(written.getAbsoluteFile().getParentFile(), base + "-set");
            note(SetMaker.make(audio.samples, audio.sampleRate, hits, setDir, inFile.getName().replaceAll("\\.[^.]+$", ""), setHits));
        }
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

    /** --separate: the drums told apart from a song's other instruments and vocals before DrumMidi listens. */
    static boolean separate;

    /** --keep-stems (with --separate): Demucs's four stems kept beside the MIDI; the temporary ones until then. */
    static boolean keepStems;
    static java.util.Map<String, File> keptStems;

    /** The stems kept beside the MIDI: <song>-drums.wav, -bass.wav, -vocals.wav, -other.wav; the temporary ones deleted. */
    static void keepStemsBeside(File written, File song) {
        java.util.Map<String, File> st = keptStems;
        keptStems = null;
        if (st == null) return;
        String base = song.getName().replaceAll("\\.[^.]+$", "");
        File dir = written.getAbsoluteFile().getParentFile();
        StringBuilder names = new StringBuilder();
        for (String n : new String[] {"drums", "bass", "vocals", "other"}) {
            File f = st.get(n);
            if (f == null || !f.isFile()) continue;
            File to = new File(dir, base + "-" + n + ".wav");
            try {
                java.nio.file.Files.copy(f.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                made.add(to.getAbsoluteFile());
                names.append(names.length() == 0 ? "" : ", ").append(to.getName());
            } catch (Exception ex) {
                note("Stem " + n + " not kept: " + ex.getMessage());
            }
        }
        for (File f : st.values()) f.delete();
        if (names.length() > 0) note("Kept the stems: " + names);
    }

    /** --make-set: a drum set from the song's own drums (SetMaker), beside the MIDI. */
    static boolean makeSet;
    /** --set-hits: how many hits of each drum --make-set keeps (1 to 8). */
    static int setHits = 1;

    /**
     * Drum-stem mode (--separate's drums, or --drum-stem): only drums are there, so nothing is taken for a bass
     * line, a guitar or keys (their guards, which leave out hits a band could have made, are off), as on a
     * kit's render alone. Without it, a song with its band is told apart as well as can be, less completely.
     */
    static boolean drumStem;

    /**
     * Demucs (htdemucs, its drums stem) run in the Python that setup_demucs.py set up (the one
     * ~/.pulsekit/demucs.txt names: its --dir; else ~/.pulsekit/demucs), else the one -Ddm.demucsPython or
     * PULSEKIT_DEMUCS names, else python3 or python
     * with demucs installed. On 80 human-played grooves under real piano and synth (Ugritone and Riot kits):
     * kick P63 R41 -> P83 R80 and snare P69 -> P84 under synth; under piano as with the drums alone.
     */
    static final class Demucs {
        static File python() {
            String given = System.getProperty("dm.demucsPython", System.getenv("PULSEKIT_DEMUCS"));
            if (given != null && given.length() > 0 && new File(given).isFile()) return new File(given);
            File pulsekit = new File(System.getProperty("user.home", "."), ".pulsekit");
            try {
                File named = new File(pulsekit, "demucs.txt");
                if (named.isFile()) {
                    File f = new File(new String(java.nio.file.Files.readAllBytes(named.toPath()), "UTF-8").trim());
                    if (f.isFile()) return f;
                }
            } catch (Exception ignored) {
                // the usual place below
            }
            File home = new File(pulsekit, "demucs");
            for (String p : new String[] {"bin/python", "Scripts/python.exe"}) {
                File f = new File(home, p);
                if (f.isFile()) return f;
            }
            return null;
        }

        /** The drums stem as a WAV in a temporary folder (the caller deletes it), or null (a note says why). */
        static File drums(File song) {
            java.util.Map<String, File> st = stems(song, true, "Separate drums", "the drums");
            if (st == null) return null;
            note("Separated the drums with Demucs: DrumMidi listens to them alone");
            return st.get("drums");
        }

        /**
         * Demucs's stems of a song as WAVs in temporary files (the caller deletes them): drums and no_drums
         * (`twoStems`), or drums, bass, other (guitars, keys...) and vocals. Null when Demucs is not there or
         * failed: a note says why (`what` names the step, `parts` what it separates).
         */
        static java.util.Map<String, File> stems(File song, boolean twoStems, String what, String parts) {
            List<String> cmds = new ArrayList<String>();
            File py = python();
            if (py != null) cmds.add(py.getAbsolutePath());
            else {
                cmds.add("python3");
                cmds.add("python");
            }
            if (System.getProperty("java.vendor", "").toLowerCase(java.util.Locale.ROOT).contains("android")
                    || System.getProperty("java.vm.name", "").toLowerCase(java.util.Locale.ROOT).contains("dalvik")) {
                note(what + ": Demucs runs on a computer only; the whole song is used");
                return null;
            }
            note("Separating " + parts + " with Demucs (a minute or more on a long song)...");
            for (String cmd : cmds) {
                File out = null;
                try {
                    out = java.nio.file.Files.createTempDirectory("dm-demucs").toFile();
                    List<String> c = new ArrayList<String>(java.util.Arrays.asList(cmd, "-m", "demucs"));
                    if (twoStems) c.add("--two-stems=drums");
                    c.addAll(java.util.Arrays.asList("-n", "htdemucs", "-o", out.getAbsolutePath(), song.getAbsolutePath()));
                    ProcessBuilder pb = new ProcessBuilder(c);
                    pb.redirectErrorStream(true);
                    // Its model where setup_demucs.py put it (in its own folder, not on the system drive).
                    File root = py == null ? null : py.getAbsoluteFile().getParentFile().getParentFile();
                    if (root != null && new File(root, "torch-cache").isDirectory()) {
                        pb.environment().put("TORCH_HOME", new File(root, "torch-cache").getAbsolutePath());
                        pb.environment().put("HF_HOME", new File(root, "hf-cache").getAbsolutePath());
                    }
                    Process p = pb.start();
                    StringBuilder log = new StringBuilder();
                    try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), "UTF-8"))) {
                        for (String line; (line = r.readLine()) != null; ) if (log.length() < 4000) log.append(line).append('\n');
                    }
                    int code = p.waitFor();
                    String stem = song.getName().replaceAll("\\.[^.]+$", "");
                    File dir = new File(out, "htdemucs" + File.separator + stem);
                    String[] names = twoStems ? new String[] {"drums", "no_drums"} : new String[] {"drums", "bass", "other", "vocals"};
                    java.util.Map<String, File> got = new java.util.LinkedHashMap<String, File>();
                    for (String n : names) {
                        File f = new File(dir, n + ".wav");
                        if (!f.isFile()) continue;
                        File keep = File.createTempFile("dm-" + n, ".wav");
                        java.nio.file.Files.move(f.toPath(), keep.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        got.put(n, keep);
                    }
                    if (code == 0 && got.size() == names.length) return got;
                    for (File f : got.values()) f.delete();
                    String tail = log.toString().trim();
                    if (tail.contains("No module named demucs")) continue;
                    note(what + ": Demucs failed (" + (tail.length() > 300 ? tail.substring(tail.length() - 300) : tail) + "); the whole song is used");
                    return null;
                } catch (java.io.IOException ex) {
                    // That Python is not there: the next one.
                } catch (Exception ex) {
                    note(what + ": " + ex.getMessage() + "; the whole song is used");
                    return null;
                } finally {
                    if (out != null) deleteTree(out);
                }
            }
            note(what + ": Demucs is not set up (on a computer: setup_demucs.py in PyJav's Python menu); the whole song is used");
            return null;
        }

        /**
         * Beat This! (the beat tracker of Foscarin, Schlüter and Widmer, 2024, in the Python setup_demucs.py
         * made) on mono samples: {beats, downbeats} in seconds, or null when it is not there or failed (a note
         * says why). It hears whole songs as well as instruments alone; the first run fetches its model (77 MB).
         */
        static double[][] beatThis(double[] x, double sr) {
            if (System.getProperty("java.vendor", "").toLowerCase(java.util.Locale.ROOT).contains("android")
                    || System.getProperty("java.vm.name", "").toLowerCase(java.util.Locale.ROOT).contains("dalvik")) return null;
            File py = python();
            if (py == null) {
                note("Beats: Beat This! is not set up (on a computer: setup_demucs.py in PyJav's Python menu); DrumAlong's own beat finder is used");
                return null;
            }
            File wav = null;
            try {
                wav = File.createTempFile("dm-beats", ".wav");
                Sounds.writeWav16(wav, x, (int) Math.round(sr));
                String script = "import sys, torch\n"
                        + "from beat_this.inference import File2Beats\n"
                        + "dev = 'cuda' if torch.cuda.is_available() else 'cpu'\n"
                        + "b, d = File2Beats(checkpoint_path='final0', device=dev, dbn=False)(sys.argv[1])\n"
                        + "print('BEATS ' + ' '.join('%.4f' % v for v in b))\n"
                        + "print('DOWNBEATS ' + ' '.join('%.4f' % v for v in d))\n";
                ProcessBuilder pb = new ProcessBuilder(py.getAbsolutePath(), "-c", script, wav.getAbsolutePath());
                pb.redirectErrorStream(true);
                File root = py.getAbsoluteFile().getParentFile().getParentFile();
                if (root != null && new File(root, "torch-cache").isDirectory()) {
                    pb.environment().put("TORCH_HOME", new File(root, "torch-cache").getAbsolutePath());
                    pb.environment().put("HF_HOME", new File(root, "hf-cache").getAbsolutePath());
                }
                note("Beats: listening with Beat This!...");
                Process p = pb.start();
                double[] beats = null, downs = null;
                StringBuilder log = new StringBuilder();
                try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), "UTF-8"))) {
                    for (String line; (line = r.readLine()) != null; ) {
                        if (line.startsWith("BEATS")) beats = numbers(line.substring(5));
                        else if (line.startsWith("DOWNBEATS")) downs = numbers(line.substring(9));
                        else if (log.length() < 4000) log.append(line).append('\n');
                    }
                }
                int code = p.waitFor();
                if (code == 0 && beats != null && downs != null && beats.length >= 4) return new double[][] {beats, downs};
                String tail = log.toString().trim();
                if (tail.contains("No module named 'beat_this'") || tail.contains("No module named beat_this"))
                    note("Beats: Beat This! is not installed (setup_demucs.py in PyJav's Python menu adds it); DrumAlong's own beat finder is used");
                else note("Beats: Beat This! failed (" + (tail.length() > 300 ? tail.substring(tail.length() - 300) : tail) + "); DrumAlong's own beat finder is used");
                return null;
            } catch (Exception ex) {
                note("Beats: Beat This! " + ex.getMessage() + "; DrumAlong's own beat finder is used");
                return null;
            } finally {
                if (wav != null) wav.delete();
            }
        }

        static double[] numbers(String s) {
            String[] w = s.trim().split("\\s+");
            if (w.length == 1 && w[0].isEmpty()) return new double[0];
            double[] v = new double[w.length];
            for (int i = 0; i < w.length; i++) v[i] = Double.parseDouble(w[i]);
            return v;
        }

        static void deleteTree(File f) {
            File[] in = f.listFiles();
            if (in != null) for (File c : in) deleteTree(c);
            f.delete();
        }
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
    /**
     * --make-set: a drum set from the song's own drums (best with --separate: Demucs's drums stem). For each
     * drum DrumMidi found, its cleanest hit: nothing else struck in the CLEAR_BEFORE before it, and a ring of
     * at least the drum's MIN_TAIL before the next hit; the loudest and longest of those (none clear: the one
     * with the longest ring). Cut from just before its attack to the next hit (at most the drum's MAX_LEN),
     * with a short fade at the end; the hits scaled together (the drums keep their balance), written as
     * <drum>.wav in the folder with set.txt (a line a drum: its note, file, hits, clear hits, length).
     * Pulsekit on a computer makes it a drum set on the Pads page (its SET_SAID line).
     */
    static final class SetMaker {
        static final String SET_SAID = "Drum set from the song: ";
        static final String INDEX = "set.txt";
        static final double CLEAR_BEFORE = 0.08;
        /** The cymbals (hats, ride, crash): a drum hit with only these on it is the next best to one alone. */
        static final java.util.Set<Integer> CYMBAL_NOTES = new java.util.HashSet<Integer>(java.util.Arrays.asList(CLOSED_HAT, PEDAL_HAT, OPEN_HAT, RIDE_NOTE, CRASH_NOTE));
        /** Each drum: its note, file name, the ring it needs (MIN_TAIL, s) and the longest cut (MAX_LEN, s). */
        static final Object[][] DRUMS = {
            {KICK_NOTE, "kick", 0.20, 0.8}, {SNARE_NOTE, "snare", 0.20, 0.8}, {CLOSED_HAT, "closed hat", 0.10, 0.35},
            {PEDAL_HAT, "pedal hat", 0.10, 0.35}, {OPEN_HAT, "open hat", 0.30, 1.2}, {RIDE_NOTE, "ride", 0.35, 2.0},
            {CRASH_NOTE, "crash", 0.50, 3.0}, {RACK_TOM, "rack tom", 0.25, 1.2}, {MID_TOM, "mid tom", 0.25, 1.2}, {FLOOR_TOM, "floor tom", 0.25, 1.5}};

        static String make(double[] x, double sr, List<Hit> hits, File dir, String song) throws Exception {
            return make(x, sr, hits, dir, song, 1);
        }

        /** `keep` hits of each drum (the best first: <drum>.wav, then <drum> 2.wav...), each a line of set.txt. */
        static String make(double[] x, double sr, List<Hit> hits, File dir, String song, int keep) throws Exception {
            StringBuilder index = new StringBuilder("# A drum set from " + song + " (DrumMidi --make-set): note, file, hits, clear hits, length (s)\n");
            List<String> got = new ArrayList<String>(), none = new ArrayList<String>();
            double[] times = new double[hits.size()];
            for (int i = 0; i < times.length; i++) times[i] = hits.get(i).time;
            double top = 0;
            List<Object[]> chosen = new ArrayList<Object[]>();
            for (Object[] d : DRUMS) {
                int note = (Integer) d[0];
                double minTail = (Double) d[2], maxLen = (Double) d[3];
                int all = 0, clear = 0;
                boolean cymbal = CYMBAL_NOTES.contains(note);
                // Each usable hit: {tier, score, start, length}; tier 0 alone, 1 a drum with only cymbals on it or a
                // cymbal with only the kick under it (a drummer's usual way), 3 short (the ring it needs not there).
                List<double[]> cands = new ArrayList<double[]>();
                for (int i = 0; i < hits.size(); i++) {
                    Hit h = hits.get(i);
                    if (h.note != note) continue;
                    all++;
                    int tier = 0;
                    double next = x.length / sr;
                    for (int j = 0; j < times.length; j++) {
                        if (j == i) continue;
                        double dt = times[j] - h.time;
                        if (dt > -CLEAR_BEFORE && dt <= 0.015) {
                            int other = hits.get(j).note;
                            boolean mild = cymbal ? other == KICK_NOTE : CYMBAL_NOTES.contains(other);
                            tier = Math.max(tier, mild ? 1 : 2);
                        }
                        if (dt > 0.015 && times[j] < next) next = times[j];
                    }
                    if (tier == 2) continue;
                    double start = attack(x, sr, h.time);
                    double tail = next - 0.005 - start;
                    double len = Math.min(maxLen, tail);
                    if (len < 0.05) continue;
                    if (tail < minTail) tier = 3;
                    else if (tier == 0) clear++;
                    // Loud and long (the ring counts up to the longest cut); a short one by its ring alone.
                    double score = tier == 3 ? tail : peak(x, sr, start, Math.min(len, 0.05)) * (0.5 + 0.5 * Math.min(1.0, tail / maxLen));
                    cands.add(new double[] {tier, score, start, len});
                }
                if (cands.isEmpty()) {
                    if (all > 0) none.add(d[1] + " (" + all + " hits, none apart from other drums)");
                    continue;
                }
                java.util.Collections.sort(cands, new Comparator<double[]>() {
                    public int compare(double[] p, double[] q) {
                        return p[0] != q[0] ? Double.compare(p[0], q[0]) : Double.compare(q[1], p[1]);
                    }
                });
                int taken = 0;
                for (double[] c : cands) {
                    if (taken >= keep) break;
                    // Short ones only when nothing better was there (the best one aside).
                    if (taken > 0 && c[0] == 3) break;
                    double[] cut = cut(x, sr, c[2], c[3]);
                    for (double v : cut) top = Math.max(top, Math.abs(v));
                    chosen.add(new Object[] {d, cut, all, clear, c[3], (int) c[0], taken});
                    taken++;
                }
            }
            if (chosen.isEmpty()) return "Drum set: no drum had a hit clear enough to cut" + (none.isEmpty() ? "" : " (" + String.join(", ", none) + ")");
            dir.mkdirs();
            // Together to 0.9 at the loudest: the drums keep the song's balance.
            double gain = top > 0 ? 0.9 / top : 1;
            java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<String, Integer>();
            java.util.Map<String, Integer> tiers = new java.util.HashMap<String, Integer>();
            for (Object[] c : chosen) {
                Object[] d = (Object[]) c[0];
                double[] y = (double[]) c[1];
                for (int k = 0; k < y.length; k++) y[k] *= gain;
                int nth = (Integer) c[6];
                String file = d[1] + (nth == 0 ? "" : " " + (nth + 1)) + ".wav";
                Sounds.writeWav16(new File(dir, file), y, (int) Math.round(sr));
                index.append(d[0]).append('\t').append(file).append('\t').append(c[2]).append('\t').append(c[3])
                    .append('\t').append(String.format(java.util.Locale.ROOT, "%.2f", (Double) c[4])).append('\n');
                String drum = (String) d[1];
                counts.put(drum, (counts.containsKey(drum) ? counts.get(drum) : 0) + 1);
                if (nth == 0) tiers.put(drum, (Integer) c[5]);
            }
            for (java.util.Map.Entry<String, Integer> e : counts.entrySet()) {
                int tier = tiers.get(e.getKey());
                boolean cym = false;
                for (Object[] d : DRUMS) if (d[1].equals(e.getKey())) cym = CYMBAL_NOTES.contains((Integer) d[0]);
                got.add(e.getKey() + (e.getValue() > 1 ? " x" + e.getValue() : "")
                    + (tier == 1 ? (cym ? " (with a kick under it)" : " (with a cymbal on it)") : tier == 3 ? " (short: the longest ring there was)" : ""));
            }
            java.io.Writer w = new java.io.OutputStreamWriter(new FileOutputStream(new File(dir, INDEX)), "UTF-8");
            try {
                w.write(index.toString());
            } finally {
                w.close();
            }
            System.out.println(SET_SAID + dir.getAbsolutePath());
            return "Drum set: " + String.join(", ", got) + (none.isEmpty() ? "" : "; left out: " + String.join(", ", none));
        }

        /** The song drum sets (folders with a --make-set set.txt) in `dir` and its folders, two levels down; not `out`. */
        static List<File> setsIn(File dir, File out) {
            List<File> sets = new ArrayList<File>();
            setsIn(dir, out == null ? null : out.getAbsoluteFile(), 0, sets);
            return sets;
        }

        static void setsIn(File dir, File out, int depth, List<File> into) {
            File[] in = dir.listFiles();
            if (in == null) return;
            Arrays.sort(in);
            for (File f : in) {
                if (!f.isDirectory() || f.getAbsoluteFile().equals(out)) continue;
                File idx = new File(f, INDEX);
                if (idx.isFile() && firstLine(idx).contains("(DrumMidi --make-set)")) into.add(f);
                else if (depth < 2) setsIn(f, out, depth + 1, into);
            }
        }

        static String firstLine(File f) {
            try {
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
                try {
                    String s = r.readLine();
                    return s == null ? "" : s;
                } finally {
                    r.close();
                }
            } catch (java.io.IOException ex) {
                return "";
            }
        }

        /**
         * Several songs' drum sets made one (a run's variations, its models): each drum taken whole (all its
         * hits, its velocity layers) from the set where it is clearest: not cut short (its ring there, the
         * drum's MIN_TAIL), then the most hits heard alone, the most hits kept, the longest. Written to `out`
         * as a --make-set set (set.txt says which song each drum is from), SET_SAID last.
         */
        static String combine(List<File> sets, File out, String name) throws Exception {
            if (sets.isEmpty()) return "no drum sets from songs (--make-set's <song>-set folders) found";
            // Each drum's best: {score..., the set, its rows}.
            java.util.Map<Integer, Object[]> best = new java.util.LinkedHashMap<Integer, Object[]>();
            for (File set : sets) {
                java.util.Map<Integer, List<String[]>> rows = new java.util.LinkedHashMap<Integer, List<String[]>>();
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(new File(set, INDEX)), "UTF-8"));
                try {
                    for (String line = r.readLine(); line != null; line = r.readLine()) {
                        String[] w = line.split("\t");
                        if (line.startsWith("#") || w.length < 5 || !new File(set, w[1]).isFile()) continue;
                        int note = Integer.parseInt(w[0].trim());
                        if (!rows.containsKey(note)) rows.put(note, new ArrayList<String[]>());
                        rows.get(note).add(w);
                    }
                } finally {
                    r.close();
                }
                for (java.util.Map.Entry<Integer, List<String[]>> e : rows.entrySet()) {
                    String[] first = e.getValue().get(0);
                    double minTail = 0.2;
                    for (Object[] d : DRUMS) if (((Integer) d[0]).intValue() == e.getKey().intValue()) minTail = (Double) d[2];
                    double len = Double.parseDouble(first[4]);
                    double[] score = {len + 0.005 >= minTail ? 1 : 0, Integer.parseInt(first[3].trim()), e.getValue().size(), len};
                    Object[] had = best.get(e.getKey());
                    if (had == null || better(score, (double[]) had[0])) best.put(e.getKey(), new Object[] {score, set, e.getValue()});
                }
            }
            if (best.isEmpty()) return "no drum in the sets had a hit";
            out.mkdirs();
            File[] old = out.listFiles();
            if (old != null) for (File f : old) if (f.isFile()) f.delete();
            StringBuilder index = new StringBuilder("# A drum set from " + name + " (DrumMidi --make-set): note, file, hits, clear hits, length (s); combined from "
                + sets.size() + " songs' sets (--combine-sets), each drum from the one where it is clearest\n");
            List<String> said = new ArrayList<String>();
            for (Object[] d : DRUMS) {
                Object[] b = best.get(d[0]);
                if (b == null) continue;
                File set = (File) b[1];
                @SuppressWarnings("unchecked")
                List<String[]> rows = (List<String[]>) b[2];
                for (String[] w : rows) {
                    java.nio.file.Files.copy(new File(set, w[1]).toPath(), new File(out, w[1]).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    index.append(String.join("\t", w)).append('\n');
                }
                double[] sc = (double[]) b[0];
                said.add(d[1] + (rows.size() > 1 ? " x" + rows.size() : "") + " (" + set.getName().replaceAll("(?i)(-sep)?(-dm)?-set$", "")
                    + (sc[0] == 0 ? ", short" : "") + ")");
                index.append("# ").append(d[1]).append(": from ").append(set.getName()).append('\n');
            }
            java.io.Writer w = new java.io.OutputStreamWriter(new FileOutputStream(new File(out, INDEX)), "UTF-8");
            try {
                w.write(index.toString());
            } finally {
                w.close();
            }
            System.out.println(SET_SAID + out.getAbsolutePath());
            return "Drum set combined from " + sets.size() + " songs: " + String.join(", ", said);
        }

        /** A drum's set `a` before `b`: not short, more hits alone, more hits kept, longer. */
        static boolean better(double[] a, double[] b) {
            for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return a[i] > b[i];
            return false;
        }

        /** Where the hit starts: back from its peak near `t` (DrumMidi's time can be a little off) to where it rises, 2 ms before. */
        static double attack(double[] x, double sr, double t) {
            int a = Math.max(0, (int) ((t - 0.02) * sr)), b = Math.min(x.length, (int) ((t + 0.03) * sr));
            if (b <= a) return Math.max(0, t);
            int at = a;
            double best = 0;
            for (int i = a; i < b; i++) {
                if (Math.abs(x[i]) > best) {
                    best = Math.abs(x[i]);
                    at = i;
                }
            }
            int s0 = at;
            while (s0 > a && Math.abs(x[s0]) > best * 0.1) s0--;
            return Math.max(0, (s0 - sr * 0.002) / sr);
        }

        static double peak(double[] x, double sr, double from, double len) {
            int a = Math.max(0, (int) (from * sr)), b = Math.min(x.length, (int) ((from + len) * sr));
            double p = 0;
            for (int i = a; i < b; i++) p = Math.max(p, Math.abs(x[i]));
            return p;
        }

        /** The hit: from `start`, `len` seconds, a 1 ms fade in and a fade out over its last fifth (at least 20 ms). */
        static double[] cut(double[] x, double sr, double start, double len) {
            int a = Math.max(0, (int) (start * sr)), n = Math.max(1, Math.min(x.length - a, (int) (len * sr)));
            double[] y = new double[n];
            System.arraycopy(x, a, y, 0, n);
            int in = Math.min(n, (int) (sr * 0.001)), out = Math.min(n, Math.max((int) (sr * 0.02), n / 5));
            for (int i = 0; i < in; i++) y[i] *= i / (double) in;
            for (int i = 0; i < out; i++) y[n - 1 - i] *= i / (double) out;
            return y;
        }
    }


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

    /** Guitars (or keys) hold their notes: the share of the 250-2000 Hz band HPSS takes away as held tones. */
    static final double GUITAR_TONE_LO = 250, GUITAR_TONE_HI = 2000, GUITAR_TONE = 0.6;

    static double toneShare(float[][] raw, float[][] perc, int lo, int hi) {
        double all = 0, kept = 0;
        for (int t = 0; t < raw.length; t++) {
            for (int f = lo; f <= hi; f++) {
                all += (double) raw[t][f] * raw[t][f];
                kept += (double) perc[t][f] * perc[t][f];
            }
        }
        return all > 0 ? 1 - kept / all : 0;
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

    /**
     * A crash struck with a kick: the kick's beater click lifts the ride band's onset (Ugritone's studio
     * kits: the top band 0.42-0.50 of it, a crash alone 0.7-1), so the top band needs only this much of it.
     * Its wash still decides (a kick alone has none).
     */
    static final double CRASH_ON_KICK = 0.35;

    /** A crash hits the ride band and the top band together, and both keep washing. */
    static boolean isCrash(double[] ride, double[] top, double[] rideEnergy, double[] topEnergy, int t, boolean onKick) {
        double r = ride[t], h = top[t];
        if (r < 0.58 || h < 0.58) return false;
        double hi = Math.max(r, h), lo = Math.min(r, h);
        // On a kick its beater click lifts the ride band's onset: the top band is then CRASH_ON_KICK of it.
        if (lo < hi * (onKick && r > h ? CRASH_ON_KICK : 0.62)) return false;
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
     * A kick over a bass line without a click to show (a soft beater, the bass's own attack over it):
     * its band rises by KICK_SHORT_JUMP and dies to KICK_SHORT of its peak 8 frames (about 90 ms) on.
     * A kick's thump dies (bench kicks over a bass: median 0.39), a bass note holds (0.66, toms 0.67).
     */
    static final double KICK_SHORT = 0.5, KICK_SHORT_JUMP = 0.2;

    static boolean shortLow(double[] level, int t, double jk) {
        if (jk < KICK_SHORT_JUMP || t + 8 >= level.length) return false;
        double peak = 0;
        for (int k = Math.max(0, t - 1); k <= Math.min(level.length - 1, t + 2); k++) peak = Math.max(peak, level[k]);
        return peak > 0 && level[t + 8] <= KICK_SHORT * peak;
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
    /** A snare written within this of a kick or tom sounding as the kit's snare: the same hit. */
    static final double SNARE_SAME = 0.03;
    /**
     * A kick or tom found whose sound reads as like the learned snare as its nearest tom, less KIT_SNARE_LIKE,
     * is the snare. Tight Studio Drums (95 songs): snares found as toms read -0.17 (median) under their
     * nearest tom, real toms -0.67 and kicks -0.87; at 0.3, 409 of 489 such snares and 7 of 177 toms.
     * Toms right of those written, without the snare learned and with it (about 100 songs each): Tight
     * Studio 25% -> 68%, KVLT Vegan Tough Guy 68 -> 99, AVL Black Pearl 89 -> 98, Artillery 90 -> 96.
     */
    static final double KIT_SNARE_LIKE = 0.3;
    /**
     * A snare found whose sound reads as its nearest learned tom KIT_TOM_LIKE or more, and by more than
     * KIT_TOM_OVER over the learned snare, is that tom. Toms found as snares read 0.85-0.96 (medians) as a
     * tom; real snares 0.38-0.68. The margin alone took real snares in AVL Drumkits and Artillery (snares
     * right 95 -> 86% and 98 -> 87%); with tom-likeness 0.9 too, of the snares found (about 100 songs a
     * kit): Tight Studio 71 toms back for 9 snares, AVL 25 for 0, Artillery 22 for 13, KVLT Vegan 19 for 0.
     */
    static final double KIT_TOM_OVER = 0.4, KIT_TOM_LIKE = 0.9;
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
        /** How much the kick clicks ("kickclick v": kickClick's ratio, the median of its hits); -1 when not learned. */
        double kickClick = -1;
        /** The toms' level jump over the hit before ("tomjump v", see tomJumpFor); -1 when not learned. */
        double tomJump = -1;

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
        /**
         * How much a kick clicks: its 2-6 kHz energy over its 40-150 Hz energy in its first 2048 samples
         * (46 ms at 44.1 kHz), from where it reaches 30% of its peak after `onset`. See KICK_CLICKLESS.
         */
        static double kickClick(double[] x, double sr, double onset) {
            int a = (int) Math.round(onset * sr), n = 2048;
            if (a < 0 || a + n > x.length) return -1;
            // From where it reaches 30% of its peak (in its first 150 ms): not the quiet before its attack
            // (an MP3's pre-echo there is bright, and would make a sine kick look as if it clicked).
            int end = Math.min(x.length - n, a + (int) (0.15 * sr));
            double top = 0;
            for (int i = a; i < end; i++) top = Math.max(top, Math.abs(x[i]));
            for (int i = a; i < end; i++) if (Math.abs(x[i]) >= 0.3 * top) {
                a = i;
                break;
            }
            double[] re = new double[n], im = new double[n];
            for (int i = 0; i < n; i++) re[i] = x[a + i] * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1)));
            fft(re, im);
            double hi = 0, lo = 0;
            for (int k = 1; k < n / 2; k++) {
                double hz = k * sr / n, p = re[k] * re[k] + im[k] * im[k];
                if (hz >= 2000 && hz <= 6000) hi += p;
                if (hz >= 40 && hz <= 150) lo += p;
            }
            return hi / Math.max(lo, 1e-12);
        }

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

        /**
         * The tom band's level jump (as the song's detection reads it: the whole render's level, normalized)
         * at each tom hit with the same tom half a second before, the median; -1 without such hits.
         */
        static double tomJump(Audio audio, List<double[]> toms) {
            double sr = audio.sampleRate;
            if (toms.isEmpty() || audio.samples.length < FFT_SIZE * 4) return -1;
            float[][] mag = spectrogram(audio.samples, sr);
            int maxBin = mag[0].length - 1;
            double[] level = bandMean(mag, bin(FLOOR_LO, sr, maxBin), bin(RACK_HI, sr, maxBin));
            normalize(level);
            List<Double> jumps = new ArrayList<Double>();
            for (double[] h : toms) {
                boolean after = false;
                for (double[] o : toms) if (o[1] == h[1] && h[0] - o[0] > 0.3 && h[0] - o[0] < 0.7) after = true;
                if (!after) continue;
                int a = (int) Math.max(0, Math.round((h[0] - TOMJUMP_WINDOW_BEFORE) * sr / HOP));
                int b = (int) Math.min(level.length - 1, Math.round((h[0] + TOMJUMP_WINDOW_AFTER) * sr / HOP));
                double j = 0;
                for (int t = a; t <= b; t++) j = Math.max(j, jump(level, t));
                jumps.add(Double.valueOf(j));
            }
            if (jumps.isEmpty()) return -1;
            Collections.sort(jumps);
            return jumps.get(jumps.size() / 2).doubleValue();
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
            // How much the kick clicks, each kick alone (kickClick): an 808's does not.
            List<Double> clicks = new ArrayList<Double>();
            // The toms' times and notes, for the tom level jump over the hit before (tomjump).
            List<double[]> tomTimes = new ArrayList<double[]>();
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
                // The snare too (struck with a hat in the test beat; the hat hardly reaches 40-800 Hz): a fat,
                // low snare (Tight Studio Drums') is found as a kick or tom, and its sound tells it apart.
                if (note != KICK_NOTE && !isTomNote(note) && note != SNARE_NOTE) continue;
                double at = Double.parseDouble(w[0]);
                // The hit as the plugin plays it: where its sound starts, up to 0.45 s after the note
                // (a plugin may play some drums late), not the note's own time.
                double onset = onset(audio.samples, audio.sampleRate, at);
                if (note == KICK_NOTE && onset >= 0) clicks.add(Double.valueOf(kickClick(audio.samples, audio.sampleRate, onset)));
                if (isTomNote(note)) tomTimes.add(new double[] {at, note});
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
            String snareSaid = "";
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
                if (n.intValue() == SNARE_NOTE) snareSaid = "; snare " + n + (c > 1 ? " (" + c + " hits)" : "");
                else kept.add(n + (c > 1 ? " (" + c + " hits)" : ""));
            }
            if (!clicks.isEmpty()) {
                Collections.sort(clicks);
                sb.append(String.format(java.util.Locale.ROOT, "kickclick %.6f\n", clicks.get(clicks.size() / 2).doubleValue()));
            }
            double tomJump = tomJump(audio, tomTimes);
            if (tomJump >= 0) sb.append(String.format(java.util.Locale.ROOT, "tomjump %.4f\n", tomJump));
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
            finish("Wrote " + out.getName() + ": kick and toms " + String.join(", ", kept) + snareSaid + (left.isEmpty() ? "" : "; silent in this kit: " + String.join(", ", left))
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
                if (w.length == 2 && w[0].equals("tomjump")) {
                    k.tomJump = Double.parseDouble(w[1]);
                    continue;
                }
                if (w.length == 2 && w[0].equals("kickclick")) {
                    k.kickClick = Double.parseDouble(w[1]);
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

        /**
         * Each kick or tom found becomes the profile's nearest one: the kick, or a tom (written as DrumMidi's
         * floor, mid or rack tom). With the snare learned (profiles from v425 on), one nearest the snare is
         * the snare: left out where a snare is written within SNARE_SAME, else written as one. Tight Studio
         * Drums' fat snare was found as a floor tom on 505 of its 2043 snares (toms P27).
         */
        void sort(List<Hit> hits, double[] x, double sr) {
            int moved = 0, snares = 0, toms = 0;
            // A snare found whose sound reads as a learned tom by KIT_TOM_OVER more than as the snare: that
            // tom (a bright studio tom is found as a snare; Tight Studio Drums).
            int snareJ0 = notes.indexOf(Integer.valueOf(SNARE_NOTE));
            for (int i = 0; snareJ0 >= 0 && i < hits.size(); i++) {
                Hit h = hits.get(i);
                if (h.note != SNARE_NOTE) continue;
                double[] v = spectrum(x, sr, h.time);
                if (v == null) continue;
                int tom = -1;
                double tomLike = -2;
                for (int j = 0; j < notes.size(); j++) {
                    int n = notes.get(j).intValue();
                    if (n == KICK_NOTE || n == SNARE_NOTE) continue;
                    double l = like(v, spectra.get(j));
                    if (l > tomLike) {
                        tomLike = l;
                        tom = n;
                    }
                }
                if (tom < 0 || tomLike < KIT_TOM_LIKE || tomLike - like(v, spectra.get(snareJ0)) <= KIT_TOM_OVER) continue;
                hits.set(i, new Hit(h.time, tom <= 43 ? FLOOR_TOM : tom <= 47 ? MID_TOM : RACK_TOM, h.strength));
                toms++;
            }
            List<Double> snareAt = new ArrayList<Double>();
            for (Hit h : hits) if (h.note == SNARE_NOTE) snareAt.add(Double.valueOf(h.time));
            List<Hit> drop = new ArrayList<Hit>();
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
                // Near enough to the snare (KIT_SNARE_LIKE of the nearest tom or nearer): the snare. Its sound
                // reads close to a floor tom's; a real tom reads well under it.
                int snareJ = notes.indexOf(Integer.valueOf(SNARE_NOTE));
                if (snareJ >= 0 && best != KICK_NOTE && best != SNARE_NOTE) {
                    double snareLike = like(v, spectra.get(snareJ));
                    if (snareLike > top - KIT_SNARE_LIKE) best = SNARE_NOTE;
                }
                if (best == SNARE_NOTE) {
                    boolean written = false;
                    for (Double t : snareAt) written |= Math.abs(t.doubleValue() - h.time) < SNARE_SAME;
                    if (written) drop.add(h);
                    else {
                        hits.set(i, new Hit(h.time, SNARE_NOTE, h.strength));
                        snareAt.add(Double.valueOf(h.time));
                    }
                    snares++;
                    continue;
                }
                int note = best == KICK_NOTE ? KICK_NOTE : best <= 43 ? FLOOR_TOM : best <= 47 ? MID_TOM : RACK_TOM;
                if (note != h.note) {
                    hits.set(i, new Hit(h.time, note, h.strength));
                    moved++;
                }
            }
            hits.removeAll(drop);
            note("Kit " + name + ": " + moved + " kick" + (moved == 1 ? "" : "s") + " and toms told apart again"
                + (notes.contains(Integer.valueOf(SNARE_NOTE)) ? "; " + snares + " found as a kick or tom sound as its snare, " + toms + " found as a snare as a tom" : ""));
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
     * its sound where it is in the song, learned from 44 kits' renders of 122 songs (their MIDI telling
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
     * lose (-95 with Artie, -64 before); unseen it gains +201. Nice Verby Kit too (the others -144; unseen +272).
     * KVLT No Sleep (10.11.), unseen by the 22-kit model: +1073; learned from all 23, each kit by a model
     * learned from the other 22: +13661, the other 22 kits +12588 (+351 more than without it).
     * The 11.11. studio and AVL kits (Ugritone's Tight Studio and Total Studio Drums, AVL Black Pearl,
     * KVLT Vegan Tough Guy) added: learned from all 27. DrumMidi on 22 kits' songs, the 23-kit model ->
     * the 27-kit one: Total Studio +250, Tight Studio +152, Black Pearl +79 (its open hats 88 -> 142),
     * Vegan +2; the 18 older kits -551 together (-2 to -83 each), all -68. Kept for the studio kits to come.
     * 13.11.: learned again from 44 kits with vst_render's bench (--model_compare, DrumMidi_CRT -Ddm.cymFeatures),
     * DigiDrum, Beat DRMR and Drumatic left out (no real ride or crash). Against the 32-kit model: all 44 kits
     * +658; the 12 kits it never saw +1161 (each left out of learning: +400; Blonde Bop HR +159, Modern Punk +81,
     * Roomy Boomy +78, Nickel Rear +64, Gone Country +55); its own 32 kits -503 (-20 to -50 each).
     * 24.11.: the song-aware model (vst_render's leave-one-out bench on 62 kits): each cymbal's inputs with the
     * song's look after them (its cymbals' inputs on average, the kinds DrumMidi wrote, cymbals a second), so
     * it tells kinds of material apart; a gate learned from the kits' songs (their look against whether the
     * model helped) gives it the songs it suits, the model above the others. Each kit scored by models learned
     * from the other 61: +45467 cymbals right (the model above alone +39212 pooled; song-aware without the
     * gate +45233 but some kits -21%), 34 kits better, 25 worse (the worst -2.6%), per kit +1.7% on average.
     * -Ddm.cymSongModel=off leaves it out (on: every song).
     * On by default; --no-cymbal-model leaves it out.
     */
    /**
     * The hit model: each onset that could be a kick, snare or tom (the rules' hits and the onsets their
     * bands peak at under the rules' threshold), weighed for each drum by a logistic model of how the
     * sound rises and dies in 12 bands (30 Hz to 16 kHz), with the rules' own decision there, whether a
     * bass line or guitar plays, and the onset's level against the song's. Learned by tools/hit_model.py
     * from kits rendered from known MIDI; the lines between its markers are its numbers.
     */
    static final class HitModel {
        static final double CAND_DELTA = 0.5;
        static final double TOM_BASS = Double.parseDouble(System.getProperty("dm.hitTomBass", "0.2")), TOM_TONE = Double.parseDouble(System.getProperty("dm.hitTomTone", "0.6"));
        static final double ACC_TOM_CUT = Double.parseDouble(System.getProperty("dm.hitAccTomCut", "0.95"));
        static final double ACC_HIT_CUT = Double.parseDouble(System.getProperty("dm.hitAccCut", "0"));
        static final double[] EDGES = {30, 60, 100, 150, 250, 400, 700, 1200, 2000, 3500, 6000, 10000, 16000};
        static final double[] OFFS = {-0.03, 0.0, 0.02, 0.05, 0.1, 0.2};
        static final int B = EDGES.length - 1, F = 5 * B + 6;
        static final int N = 2048;
        // hit model: begin
        static double[] HM_MU = {2.62336e-09, -6.28861e-09, 3.57304e-09, 7.73831e-09, -1.49023e-08, 3.89359e-09, -1.15661e-08, 1.38826e-08, 1.24814e-08, 2.4904e-08, -2.40414e-08, -1.07607e-08, 1.69233, 2.13705, 2.54825, 2.27397, 1.85182, 1.63218, 1.59988, 1.67641, 1.83132, 1.86142, 1.83079, 1.9457, -0.602069, -0.957381, -1.32543, -0.832702, -0.790303, -0.642771, -0.626998, -0.708774, -0.816578, -0.834785, -0.785441, -0.842936, -1.02379, -1.37908, -1.70017, -1.27923, -1.03261, -0.871322, -0.852637, -0.917679, -1.02515, -1.04024, -0.981429, -1.01939, -1.32838, -1.61091, -1.87171, -1.50459, -1.21041, -1.08179, -1.04155, -1.10757, -1.22036, -1.24853, -1.20917, -1.26576, 0.196301, 0.161231, 0.0380808, 0.073416, 0.0414268, -3.95486e-09}, HM_SD = {3.41234, 3.56017, 3.63862, 3.46182, 2.99178, 2.94703, 2.98979, 3.09295, 3.26528, 3.47018, 3.70506, 4.00248, 4.38339, 4.73606, 4.77369, 4.41252, 3.86617, 3.73012, 3.61957, 3.66758, 3.79697, 3.87288, 3.97212, 4.274, 1.70364, 1.67626, 1.92727, 1.66377, 1.53825, 1.37982, 1.38696, 1.65492, 1.87905, 2.03523, 2.21996, 2.74645, 2.62315, 2.81295, 3.02903, 2.82956, 2.39204, 2.21903, 2.17611, 2.35519, 2.61358, 2.78959, 3.06213, 3.74169, 3.75575, 3.86063, 3.83945, 3.65896, 3.11862, 2.95555, 2.89495, 2.9878, 3.11955, 3.2541, 3.62934, 4.46435, 0.3972, 0.367745, 0.191392, 0.260819, 0.199276, 2.91426};
        static double[][] HM_W = null;
        static double[] HM_B = null, HM_T = {0.35, 0.3, 0.3};
        static double[][] HM_W1 = {{0.00954272, -0.044451, 0.134919, 0.326902, -0.234026, 0.666805, 0.176962, -0.177563, 0.104114, 0.424669, 0.767882, 0.974316, -0.100438, -0.929451, -0.695552, 0.262815, 0.179903, 0.233027, -0.244623, 0.224985, -0.821216, 0.343279, -0.22801, 0.666808, -0.042853, 0.20886, 0.0955547, -0.300669, -0.773306, -0.308253, -0.50701, -1.60842}, {0.300989, 0.154553, -1.07129, 0.102728, 0.1231, -0.34463, 0.00922872, 0.0574485, 0.489424, -0.277342, 0.0589457, -0.5323, -0.210018, -0.37531, -0.713773, -0.170011, -0.168195, 0.204692, 0.220649, 0.0952263, -0.553114, -0.267804, 0.264096, 0.407883, -0.401754, -0.564773, -0.614453, 0.245574, -0.955582, 0.171919, 0.0922073, 1.71803}, {0.107392, 0.0603994, 1.49582, -0.378382, 0.35998, 0.51622, 0.215009, -0.607114, 0.24132, 0.399501, 0.451474, 0.776275, -0.312956, -0.137017, -0.473817, 0.0148654, 0.160552, 0.188316, 0.0374422, 0.109278, -0.981486, 0.231664, 0.193503, -0.418521, -0.280858, 0.769439, -0.41786, 0.368004, -0.495927, 0.298316, 0.203673, -0.107053}, {-0.467553, 0.00845359, -0.243435, -0.346841, 0.437385, -0.364359, -0.232721, 0.34077, 0.66606, 0.0486375, -0.177414, -0.910817, -1.35302, 0.386669, -0.317826, -0.599121, -0.435038, -0.0511922, -0.194093, -0.204228, -0.0293494, 0.300773, -0.191246, -0.295586, -0.515718, 0.181772, 0.419748, 0.16131, 0.314245, -0.417937, -0.207528, 0.475211}, {0.236745, 0.0190897, 0.966972, 0.651819, 0.0298096, -0.324685, -1.19197, -0.0246535, -0.114504, -0.373835, -0.364216, 0.428036, -0.201871, 0.219145, 0.390456, 0.018992, -0.22432, -0.146877, -0.000112823, 0.392, 0.771223, -0.214226, -0.133789, -0.00159168, 0.391746, -0.51328, 0.325806, -0.312289, 0.438893, -0.0648531, -0.15823, -0.621655}, {0.305609, 0.0872634, -0.644101, 0.255504, -0.476111, -0.445412, -0.187057, 0.105301, 0.0217999, -0.280946, -0.143105, -0.112027, -0.0397381, -0.256315, 0.190268, 0.0920374, -0.3022, 0.0632045, -0.00837587, 0.257375, 0.911084, -0.102839, 0.0602006, 0.367798, 0.203715, -0.309844, 0.215857, -0.779808, 0.179762, 0.133439, 0.0919139, -0.0146527}, {0.354522, 0.188077, -0.239124, -0.0288826, 0.339683, -0.694702, -0.436708, -0.070157, 0.35681, -0.0519144, -0.219149, -0.448731, -0.179941, -0.143888, 0.0458483, -0.161296, -0.480536, 0.470492, -0.372248, 0.0662781, -0.103716, -0.393363, 0.0139811, 0.403281, -0.0481558, -0.32566, 0.13918, 0.38522, -0.272835, 0.173491, 0.290563, 0.650999}, {0.035009, -0.00791943, -0.140217, 0.0680596, -0.180858, -0.0839529, 0.0376259, 0.160632, 0.235204, -0.12081, -0.220876, 0.0703976, 0.0734066, -0.115956, -0.0355708, 0.0233122, 0.0984683, 0.113153, -0.0111189, 0.170521, -0.291378, -0.289313, 0.0958729, 0.307456, -0.248528, -0.0551559, 0.152827, -0.36349, -0.0675747, 0.395014, 0.180459, 0.219618}, {-0.0976892, 0.131588, 0.121556, 0.110913, 0.1013, 0.348256, -0.100247, 0.0131672, 0.0550228, 0.0733347, -0.106244, 0.580075, -0.0219914, 0.248985, -0.215142, -0.00585604, -0.285764, 0.0427987, 0.0958475, 0.0678821, -0.394408, 0.0272491, -0.121156, -0.236022, 0.17556, 0.399405, 0.194238, -0.145557, -0.371223, -0.189503, -0.156973, -0.33074}, {-0.157004, -0.141298, 0.0228553, -0.3525, 0.0631952, 0.313711, 0.255492, -0.286385, 0.0421392, 0.265382, -0.0382637, -0.0352837, 0.350864, -0.113326, -0.224334, -0.0950411, -0.0583532, 0.109731, 0.0249852, -0.116625, -0.0622228, -0.163488, 0.150998, -0.409371, 0.374997, -0.044142, -0.227944, 0.381433, -0.0942654, -0.309655, -0.205723, 0.170895}, {-0.185885, 0.00962777, -0.107184, 0.0109655, -0.273452, 0.185889, 0.350509, -0.0710721, 0.0539274, 0.386236, -0.067567, -0.253951, 0.31066, -0.0144614, 0.184838, 0.0612605, -0.00260116, 0.00555036, 0.174017, -0.0471121, 0.10245, -0.244902, 0.425719, -0.2517, 0.02459, -0.235704, -0.0772337, 0.576606, 0.139424, 0.205754, -0.112329, -0.016064}, {-0.186118, -0.430436, 0.0837243, 0.474623, -0.152006, 0.212356, 1.05079, 0.0359716, -0.248545, -0.0825811, -0.0392235, 0.162369, 0.375636, -0.011677, 0.334239, 0.266514, 0.440477, 0.0937814, 0.0310505, -0.0758814, 0.317649, -0.351643, 0.210369, -0.130841, -0.273351, -0.324404, -0.00464936, 0.358191, 0.146797, 0.377901, 0.0181158, -0.176631}, {0.0132441, 0.0958045, -0.431128, -0.108021, 0.125815, 0.0990049, 0.0215407, 0.0330824, -0.148958, 0.378812, -0.651712, -0.269661, 0.0504971, -0.278689, 0.153712, 0.169662, -0.0193539, 0.0151782, -0.959569, 0.263727, -0.215668, 0.261262, -0.183641, 0.0665157, 0.0480585, 0.0790179, -0.312523, -0.469868, -0.11214, -0.268949, 0.0642166, -0.0641071}, {-0.228179, -0.370232, -0.28383, -0.133048, 0.146783, 0.206577, -0.0726124, -0.202054, 0.125646, -0.205208, -0.369952, 0.0709967, 0.156061, -0.154578, -0.248785, -0.0563104, 0.257139, 0.0195091, -0.657241, 0.383899, -0.123033, -0.0534299, -0.353838, 0.00288118, -0.453226, -0.090835, -0.0753043, 0.0601199, -0.178741, -0.0150658, -0.208821, 0.37844}, {-0.0978811, -0.437535, 0.661064, -0.291566, -0.262989, -0.125385, -0.060664, -0.822321, -0.0578709, -0.151115, -0.413509, 0.383344, -0.150438, 0.0293206, -0.381645, 0.0884015, 0.13071, -0.0766261, -0.375122, 0.106669, -0.541031, -0.200786, -0.558559, -0.176365, -0.315307, 0.242243, 0.116127, 0.109778, -0.0184325, -0.107518, -0.101548, -0.360503}, {-0.59564, -0.353771, -0.199657, -0.117761, 0.309807, -0.030683, -0.0281267, 0.268176, 0.0184131, 0.00100093, -0.683873, -0.335261, -0.421503, 0.0485359, -0.063437, -0.0750968, -0.409936, -0.249226, 0.180322, -0.710407, 0.0892025, 0.0336424, -0.748053, -0.335504, -0.664968, 0.261981, 0.287628, -0.0682721, -0.00288602, -0.346981, -0.193289, -0.00617334}, {-0.277428, -0.321095, 0.423083, 0.133555, -0.0693375, -0.247587, -0.180394, -0.153986, 0.169405, -0.218929, -0.449752, 0.0646773, -0.168199, 0.106966, 0.181487, 0.258463, -0.0696677, 0.0150242, 0.312576, -0.392806, 0.290436, 0.0457523, -0.326698, 0.0832584, -0.455108, -0.292087, -0.164468, 0.107945, 0.210647, -0.258795, 0.033468, -0.0810748}, {-0.466447, -0.701569, -0.36202, 0.324071, -0.59038, -0.0607872, 0.120575, -0.130039, 0.0689849, 0.14405, -0.32572, 0.00695526, 0.376241, -0.0855509, 0.0594442, 0.0374927, 0.228828, 0.011116, 0.0343373, -0.0693988, 0.292172, 0.189707, -0.0358115, 0.108443, -0.319515, -0.333268, 0.065288, -0.0457885, 0.265676, -0.173717, -0.0319437, 0.147904}, {-0.4081, -0.797307, 0.0334147, 0.180585, -0.0250559, -0.133308, -0.0276608, -0.261381, 0.0438291, -0.141777, -0.225418, 0.0525017, -0.183841, 0.266758, 0.0222509, 0.0643074, -0.0744646, 0.100382, -0.198144, -0.124654, -0.173055, 0.118486, -0.254188, 0.302956, -0.0723517, 0.0847356, 0.0891867, 0.151769, -0.0532856, -0.241613, 0.0240197, 0.231706}, {-0.161321, -0.753687, -0.0339553, 0.0626144, -0.0915101, -0.140081, 0.0824956, 0.0059949, -0.0715125, -0.242356, -0.13096, 0.134833, 0.0659654, -0.0246569, 0.06066, -0.0682448, 0.0772519, 0.043879, -0.0922946, -0.150439, 0.0135754, -0.0723032, -0.0726322, 0.327668, -0.0470653, 0.0533028, -0.0366944, -0.1221, 0.0562613, -0.081345, -0.0309435, -0.0585785}, {0.11521, -0.702294, 0.0977185, 0.158688, -0.495714, -0.0572982, -0.0914522, 0.227821, -0.0471034, 0.16923, -0.196008, 0.138379, 0.00329433, 0.153263, -0.0208073, -0.31443, 0.0449847, 0.0366698, 0.0216094, 0.0148661, -0.0382128, 0.168442, -0.0297117, -0.115482, 0.155156, 0.0127599, -0.0553912, 0.105926, -0.169588, -0.152629, -0.0762167, -0.149867}, {0.0991501, -0.899806, -0.0315608, 0.0184645, -0.214, 0.116234, 0.0498404, -0.0383965, -0.246483, 0.0916692, 0.0789303, -0.124931, 0.304418, -0.230778, 0.0426376, -0.278756, 0.214836, 0.206558, -0.019404, 0.0321784, 0.0356987, -0.0310157, 0.145151, -0.16687, 0.210186, -0.0950646, -0.0702873, 0.161256, 0.0877094, -0.0383993, 0.0533249, -0.0308587}, {0.0146521, -0.64473, -0.0929954, -0.136868, -0.199124, 0.110512, -0.0661885, -0.110701, 0.182516, -0.186182, 0.0521468, -0.0911068, -0.0955797, -0.0109275, -0.156595, -0.0321657, -0.00755065, 0.111692, 0.13242, -0.0976642, 0.000849038, -0.0592027, 0.0806228, 0.0292625, -0.0568654, 0.02752, 0.0638843, 0.117945, -0.0147373, 0.187546, 0.0432469, -0.129998}, {-0.143086, -0.393448, 0.0750915, 0.0974702, 0.252477, 0.133091, 0.13451, -0.0293616, 0.0581432, -0.0860399, -0.0689833, -0.0671571, 0.152556, -0.107518, 0.0504152, 0.131941, 0.212222, 0.0107614, 0.118877, 0.0300136, -0.0568558, -0.0274387, -0.0236734, 0.0394955, -0.132578, -0.0786232, -0.120101, 0.0233596, 0.00888865, 0.369202, 0.0237831, 0.00536146}, {0.00221301, 0.07924, 0.101735, -0.0286214, -0.105438, -0.0969365, -0.0639965, 0.0875853, 0.0610693, -1.10701, -0.0393746, 1.16995, -0.0173793, -0.467243, -0.210554, 0.0340226, 0.140624, 0.344911, -0.301845, 0.111101, -0.254787, 0.116441, 0.00291215, -0.265184, -0.201341, 0.211762, -0.248356, 0.026581, -0.153035, -0.0612328, -0.181888, -0.502529}, {-0.0639162, 0.00982107, -0.43709, -0.160095, 0.0648346, 0.389388, 0.103126, 0.00101607, 0.111567, 0.370385, -0.114811, -0.180817, 0.0739004, -0.692895, -0.0759201, 0.702619, 0.248793, 0.278458, 0.157748, 0.31534, -0.250965, -0.420467, 0.0191645, 0.343243, -0.369586, 0.0266049, -0.418217, -0.0257512, -0.410065, 0.133938, -0.376454, 0.27769}, {0.00538702, 0.0833986, 0.477092, 0.0470521, -0.022219, 0.59582, 0.0114023, -0.210579, -0.451451, -0.221772, 0.0101561, -0.340742, -0.0912812, -0.342943, 0.107506, 0.368315, 0.219445, 0.573138, -0.172916, 0.190826, -0.375787, 0.248124, 0.0091015, -0.450113, 0.0227869, 0.171385, -0.221015, 0.207251, -0.253475, -0.0719218, -0.827652, -0.141078}, {-0.191925, 0.139125, -0.0830239, -0.390957, 0.457973, 0.0574583, -0.1069, 0.23806, -0.358702, -0.0125322, -0.0996002, -0.357955, -0.555547, 0.0426304, 0.253532, -0.517239, -0.240134, -0.419465, -0.0571868, 0.260529, 0.113927, 0.495186, -0.0926764, 0.116748, -0.128768, 0.36859, 0.28381, -0.604886, 0.13758, -0.237756, -0.462553, -0.244625}, {-0.200942, 0.0360414, 0.3681, -0.387982, -0.0323093, 0.140312, -0.327178, -0.194956, -0.298439, -0.429063, -0.0129989, 0.10794, -0.0601646, -0.265845, 0.144773, -0.23808, 0.197044, -0.133339, -0.0345399, -0.116471, -0.0415082, -0.0299862, -0.00699059, -0.172199, -0.0379675, 0.040335, -0.0764566, -0.0575609, -0.0540106, -0.120536, -0.215595, -0.181233}, {0.0793472, -0.0220711, -0.197815, -0.405976, -0.137501, 0.028058, -0.163991, 0.147138, -0.262984, -0.154187, -0.144002, 0.020448, -0.0309393, -0.364819, -0.0159676, -0.211277, 0.0586475, 0.189715, -0.108432, -0.115313, -0.103622, -0.382565, -0.212798, -0.17742, -0.0236928, -0.0861076, -0.204335, 0.108789, 0.0593151, -0.148594, 0.039117, -0.0234023}, {-0.122354, -0.077336, -0.133503, -0.0312719, 0.0316947, -0.0216376, -0.268503, 0.147995, -0.151272, 0.0434991, -0.053382, -0.15371, -0.213362, -0.183763, 0.013707, -0.175433, 0.153194, 0.485956, 0.00385583, 0.216037, 0.015479, -0.00696615, -0.0287568, 0.109044, 0.228063, -0.0837468, 0.21593, 0.192971, -0.127052, -0.325637, -0.067444, 0.0840199}, {-0.0233745, 0.00824205, -0.22062, 0.187538, -0.421824, 0.277221, 0.0158885, 0.503191, -0.2178, 0.10047, 0.0372132, -0.32225, 0.130732, 0.0546734, -0.0959555, -0.03991, 0.197868, 0.281095, -0.0774167, -0.299945, 0.117879, -0.0167809, -0.0904328, -0.0859425, 0.0716103, -0.325123, 0.218086, 0.0213958, -0.0158553, -0.250938, 0.006445, 0.0496141}, {-0.106447, 0.100161, -0.218886, 0.0492563, -0.260524, -0.0528719, -0.483736, 0.447573, -0.271943, -0.334951, -0.146585, -0.0615124, 0.0216615, 0.493452, 0.2867, 0.32612, 0.0196322, 0.276906, 0.0325524, -0.140631, 0.352652, -0.23252, 0.159399, 0.111987, 0.659606, -0.28318, 0.155065, 0.227513, 0.170521, -0.587276, 0.0342509, -0.0157925}, {-0.00661622, -0.0858143, -0.0487992, 0.495852, -0.774873, 0.285402, 0.352636, 0.477882, -0.0249618, 0.099357, -0.0182287, 0.0840084, 0.305709, 0.31765, 0.0708933, -0.264554, 0.149964, -0.0220693, 0.118531, -0.498833, 0.213025, 0.0435167, 0.479547, -0.0746905, 0.631061, -0.234184, 0.0825866, 0.210734, 0.0710564, -0.147503, -0.02651, -0.0613086}, {-0.136199, -0.0462519, -0.00206119, 0.876669, -0.82463, 0.304423, 0.855298, 0.319751, -0.162694, 0.392878, 0.035053, -0.0967785, 0.188848, 0.30483, 0.0456913, -0.499675, 0.0102403, 0.177973, 0.183871, -0.0179734, -0.152962, -0.0683055, -0.0649032, -0.0484423, 0.15506, -0.150327, 0.35334, -0.158723, -0.0735264, 0.276401, -0.0465297, -0.0180426}, {0.0291799, -0.0384626, -0.0499082, 0.405069, -0.111787, 0.103305, 0.641611, -0.214189, 0.000908834, -0.228311, 0.0138162, 0.0750015, -0.0401966, 0.129966, 0.164639, 0.0854567, 0.346694, 0.308842, 0.164986, 0.0357755, -0.073807, -0.105318, -0.0694963, 0.0196492, 0.249315, -0.0893244, -0.00374644, 0.0239872, 0.114953, 0.195018, 0.106887, -0.0220901}, {0.0392226, 0.0240838, -0.137021, 0.265325, -0.0823722, 0.230938, 0.0568503, -0.181885, 0.230245, 0.0896705, 0.0983418, -0.245358, 0.0229987, 0.00457319, 0.0218629, 0.368587, 0.219037, 0.0856352, -0.314227, -0.264651, -0.141711, -0.293102, 0.00324504, -0.35877, 0.0587051, 0.00870563, -0.116241, -0.169869, -0.0527833, -0.0356183, -0.356695, -0.26223}, {-0.0114703, -0.0422367, -0.281762, -0.282157, 0.108989, -0.235205, -0.028955, 0.0655393, 0.36422, 0.366818, 0.0987646, -0.0432235, -0.0397121, -0.188297, -0.782998, 0.149578, 0.016739, 0.330523, 0.355306, -0.337556, -0.364744, -0.043848, 0.115792, 0.292013, -0.108758, -0.233838, -0.687376, 0.0241482, -0.122865, 0.339274, 0.450141, 0.714271}, {-0.0820114, -0.0131135, 0.906076, -0.206663, 0.15096, -0.622974, 0.0361332, 0.29417, -0.132535, -0.121468, -0.0865404, -0.0344249, -0.19177, 0.553256, -0.623073, 0.0219554, 0.197483, 0.0608892, 0.0429875, -0.339135, -0.158787, 0.199263, 0.130748, -0.089005, -0.0577956, -0.00486477, 0.0530145, 0.115895, 0.0984115, 0.245267, 0.712971, 0.218033}, {-0.381503, 0.0414175, -0.406052, -0.267327, 0.520645, -0.427803, -0.0321129, -0.0882653, 0.132746, -0.175532, -0.069172, 0.170002, -0.101781, -0.0226655, -0.357143, 0.232872, 0.0964599, -0.447391, -0.334323, 0.120927, -0.195906, 0.179421, 0.1366, 0.0853237, -0.248651, 0.0177462, 0.176463, 0.293688, -0.224969, 0.350662, -0.194833, 0.174116}, {-0.173487, -0.172253, 0.334495, 0.264163, 0.0630467, -0.845011, -0.234866, 0.110599, -0.159129, 0.0909977, -0.208243, -0.155549, 0.363047, 0.0233524, 0.0409708, -0.0453023, 0.132385, -0.173888, -0.147601, -0.00609991, -0.0329851, -0.196337, 0.17467, 0.250644, 0.118302, -0.534003, 0.190351, 0.0635111, 0.0302049, 0.551596, 0.118612, -0.0717753}, {-0.0846592, 0.164021, -0.170522, -0.330756, -0.00329687, -0.549183, -0.26639, 0.157213, 0.167777, 0.198459, 0.0358404, 0.213869, -0.31885, -0.307896, -0.429898, -0.0196224, 0.03183, 0.0462712, -0.373219, -0.376552, -0.0439403, 0.152074, -0.227445, -0.00143988, -0.121802, 0.171111, -0.105832, 0.213187, -0.0402273, 0.0170084, -0.0835974, -0.0276437}, {-0.0874817, 0.0358662, 0.00259796, 0.046819, -0.204276, -0.194024, -0.175783, 0.0269767, 0.00780222, -0.00050977, -0.0467274, -0.0334195, 0.201819, -0.32878, -0.388817, -0.191127, 0.244891, 0.23669, -0.354048, -0.313878, -0.0465172, -0.0261706, -0.146292, -0.109258, -0.113836, -0.0846802, 0.0897872, 0.0516639, 0.0177149, 0.122569, -0.049106, -0.0844356}, {0.325612, -0.0160056, -0.139189, 0.152774, -0.401983, -0.280709, -0.278443, 0.0548383, -0.165122, -0.0496208, 0.00993006, -0.131648, 0.119474, 0.0471524, 0.0268009, -0.0912892, 0.0739533, 0.033388, -0.21621, -0.0250049, 0.189659, -0.0037212, 0.100772, 0.082099, 0.0658626, -0.0334829, 0.133737, -0.251494, 0.0666675, -0.116048, 0.0696505, -0.086674}, {0.296098, -0.114396, -0.0722248, -0.159945, -0.0177769, -0.50903, -0.363222, -0.00697628, -0.187782, -0.252349, -0.0798288, 0.121199, -0.010996, 0.308816, 0.244842, 0.231816, -0.101924, -5.87255e-07, 0.229051, 0.0799987, 0.163407, -0.00181269, -0.0667806, 0.189374, 0.22267, 0.0722268, 0.130039, -0.267699, 0.0736236, -0.178813, 0.151177, -0.0402862}, {0.102988, -0.05934, 0.180642, 0.366187, -0.0440294, -0.0548829, 0.496884, 0.0325026, 0.265369, -0.00183116, 0.115242, 0.0755216, -0.0716918, 0.102061, -0.0861855, -0.268927, 0.0826833, -0.334912, -0.0594997, -0.114534, -0.00686279, 0.141421, -0.0565691, 0.0825094, -0.0953448, 0.144975, 0.140677, -0.388333, -0.19376, -0.12133, 0.0539216, 0.00265472}, {0.0187148, 0.00665385, -0.0383834, -0.109354, 0.0826543, -0.0944522, 0.817668, -0.0436234, -0.0536677, 0.169409, -0.0626625, -0.0938687, 0.0891021, -0.207746, -0.0286093, -0.304335, 0.109316, -0.093809, -0.0539757, 0.102803, -0.0744913, -0.125968, -0.0636468, -0.067268, -0.148388, 0.0160313, -0.0465238, -0.797926, -0.114619, 0.0194288, -0.120193, 0.0563468}, {-0.0445275, 0.0184849, -0.0232985, 0.052354, 0.0414338, 0.00208463, 0.145664, -0.186004, -0.0602188, -0.0422774, 0.062947, 0.0545429, -0.063625, -0.113439, 0.0821402, -0.000124548, 0.0840265, -0.157065, -0.0339822, -0.0370442, 0.0682862, -0.382439, -0.0354016, -0.0600662, -0.00684458, -0.104198, -0.115559, -0.609192, -0.109809, 0.0407129, -0.0910629, -0.0645011}, {0.0418859, -0.118508, -0.307135, 0.243487, 0.0702053, 0.0579121, 0.0460741, -0.121634, -0.0215854, -0.0118938, -0.0224464, 0.0727869, -0.168041, 0.322924, -0.0775168, 0.221609, -0.104417, 0.0548712, -0.338812, -0.0512402, -0.0467106, -0.308835, -0.038415, -0.0422793, 0.0129566, -0.186446, 0.175216, 0.0966806, -0.0752589, -0.106417, 0.511734, 0.52297}, {0.0406053, -0.0181241, 0.0243456, -0.197475, 0.021332, -0.322445, -0.0125462, 0.199581, 0.0442211, -0.0590625, -0.0486746, -0.332777, 0.055882, 0.12487, -0.102373, -0.0977714, 0.00745797, 0.158917, 0.116994, 0.065963, -0.0920027, 0.285984, -0.00979128, 0.431735, 0.129519, -0.201335, -0.374437, 0.0386873, 0.158669, -0.0298501, 1.09309, 0.699731}, {-0.209512, 0.163941, 0.480711, -0.218145, 0.057312, 0.290346, -0.0993221, 0.375478, -0.0623544, -0.167964, 0.107711, -0.224866, 0.056711, 0.0937159, -0.105057, 0.000986195, -0.111736, -0.155521, 0.246904, 0.00635802, 0.0474192, 0.25188, -0.00246624, -0.178092, 0.0175401, 0.0164005, -0.0736646, 0.0530895, 0.100622, -0.288848, -0.0957907, -0.370343}, {-0.201465, -0.107028, -0.116856, -0.416947, 0.0901061, -0.0786676, 0.0811152, -0.265501, 0.0705831, -0.000849886, 0.00729052, 0.0279431, 0.126206, -0.11756, 0.0861287, 0.105033, 0.290924, -0.183083, 0.166105, -0.0181978, -0.160118, -0.165491, 0.0797295, 0.267314, 0.0868585, -0.218299, -0.00838401, 0.261443, -0.0665767, 0.442273, 0.239069, 0.0177988}, {-0.106747, -0.0135933, -0.11361, 0.162571, -0.196082, 0.0641003, 0.142452, 0.0380724, -0.213665, 0.126076, 0.120204, 0.103598, 0.108003, -0.239773, -0.0629988, -0.165895, -0.0569857, 0.158424, -0.178066, 0.0591966, 0.0448696, -0.102764, 0.13104, 0.16662, -0.0520022, -0.178111, 0.155314, -0.0813155, -0.0975206, -0.0448964, 0.0376932, 0.0717775}, {0.159619, 0.0455717, 0.0684193, -0.224068, 0.184702, -0.0204079, -0.0751227, 0.0989513, -0.103924, 0.036269, 0.045615, 0.0525306, -0.338866, 0.0929256, -0.0471557, -0.116401, -0.206024, 0.149835, 0.0500084, -0.0347815, -0.236305, 0.101903, -0.0101915, -0.15158, 0.0618874, 0.13584, -0.0873347, 0.0570031, -0.0911158, -0.0276851, 0.00117487, -0.0387918}, {0.00343278, -0.0802542, 0.0721291, 0.0706219, -0.269117, 0.0651099, -0.0946173, 0.0724841, -0.112771, -0.0871408, -0.0213634, -0.0270223, 0.149837, -0.00846298, 0.00116455, -0.134665, 0.160847, 0.1139, 0.116872, -0.023553, 0.29748, 0.0674618, 0.00929, -0.230615, -0.0457884, 0.0420692, -0.119982, -0.0102174, -0.0571639, 0.0115863, -0.186304, -0.113899}, {0.174174, 0.0372109, -0.043232, 0.0356419, -0.152523, 0.0233193, -0.135708, 0.0180825, 0.0212176, 0.066855, 0.0906255, -0.0571461, -0.0943277, 0.133088, -0.0998476, -0.236131, -0.0479222, 0.0603992, 0.038972, -0.0715291, 0.0384248, 0.132579, -0.0588579, -0.0815392, 0.0653085, 0.0900064, 0.0818559, 0.145559, 0.0326273, 0.0582633, -0.11464, -0.045681}, {0.140425, -0.126961, -0.0579222, -0.205939, 0.25355, 0.224628, -0.0131199, -0.187306, 0.154233, -0.0356227, 0.0487812, -0.104318, -0.068446, 0.073573, -0.0206872, 0.209774, 0.188007, -0.100964, 0.00718415, 0.0682744, -0.0688036, 0.176715, 0.0217587, 0.250529, -0.0359545, -0.0238093, -0.081191, -0.115188, 0.0481631, -0.000173482, 0.0719817, 0.00124765}, {0.0208623, 0.052465, 0.20102, -0.0133536, 0.0678692, 0.0666915, 0.086592, -0.151587, 0.257781, -0.0294099, 0.139078, 0.101566, -0.206675, 0.0639443, 0.0454017, 0.0125166, -0.0255644, -0.172004, 0.0466344, -0.155896, -0.0637426, 0.275787, -0.0475907, 0.110954, 0.0343922, 0.128953, 0.032191, -0.143744, -0.0698257, 0.0514537, -0.0718525, 0.0076416}, {-0.0291658, -0.0833793, 0.098353, -0.178692, 0.0772296, -0.13644, -0.0198111, 0.0479885, -0.158004, -0.115558, -0.0545749, -0.0226026, 0.0788363, -0.0671192, 0.0642843, -0.0253237, -0.109005, -0.158294, -0.00697389, 0.115934, 0.0304883, 0.0245667, 0.0323099, 0.0115635, -0.0210214, 0.0101523, -0.0788293, -0.229274, 0.0288712, -0.00483589, 0.07831, 0.0933023}, {-0.0812348, 0.00906079, 0.0173763, -0.0241495, -0.0739154, -0.0284476, -0.0689933, 0.0503193, -0.123955, 0.0318556, -0.0733117, 0.00580479, 0.078797, -0.0769614, -0.0399296, -0.00284342, -0.0496615, -0.0438993, -0.0869249, 0.0450961, -0.0878703, -0.0243381, 0.0300068, -0.0880399, 0.149982, -0.00972538, 0.145022, 0.0285153, 0.102662, -0.190458, 0.0967934, 0.0950131}, {0.0458373, -0.254856, -0.111685, 0.142793, 0.0320551, 0.1048, 0.146094, -0.361739, -0.0639496, 0.142216, -0.227624, 0.293461, -0.516605, -0.0187586, -0.20281, -0.0747617, -0.0905693, 0.120452, -0.0530586, 0.125679, -0.215656, 0.23311, -0.238128, -0.0630668, -0.000470303, 0.231066, -0.25209, 0.014108, -0.464062, -0.0620446, -0.152936, 0.0695868}, {-0.0119109, -0.223508, -0.0244846, 0.463827, 0.0106723, -0.0898774, -0.246839, 0.100913, -0.0112122, -0.218957, -0.244853, 0.0259909, -0.24901, 0.197129, 0.055934, -0.0822869, -0.500491, 0.0695249, -0.0216165, 0.291121, -0.0307781, -0.191521, -0.462031, -0.0301079, -0.0185239, -0.194172, 0.158872, 0.157881, 0.0119822, -0.22758, 0.0106749, 0.00695253}, {-0.0358267, -0.11856, 0.0340725, 0.0529391, 0.106704, 0.0510218, 0.0419175, 0.0693978, -0.0638103, 0.104999, -0.0861638, 0.106013, -0.186683, 0.0696327, -0.0817619, -0.0746216, -0.284139, 0.0101291, -0.0181849, 0.207704, -0.195355, 0.160465, -0.165697, -0.0130338, -0.0805142, 0.0900331, -0.110783, 0.102532, -0.0902641, 0.0572813, -0.0533948, 0.0850046}, {0.536907, 0.00921108, -0.0657565, 0.0700526, -0.082299, -0.0187881, 0.0460141, -0.175666, 0.312511, 0.0700373, -0.0537428, 0.290213, -0.0722179, -0.192899, 0.0530597, -0.0390361, -0.348963, 0.204199, 0.165424, 0.00367324, -0.265106, -0.224447, -0.0567248, -0.304552, -0.0771632, -0.376385, -0.360406, 0.143925, -0.461148, 0.109162, 0.0838586, 0.0312506}, {0.0954126, 0.0248147, 0.0609125, 0.161603, -0.0233151, 0.308621, 0.0940462, 0.0198342, 0.334115, 0.0264626, -0.0334262, -0.0293754, 0.0464967, 0.040859, 0.161305, 0.0365364, -0.0180081, -0.0102838, 0.144642, 0.0550029, -0.0567127, -0.072165, -0.446339, 0.0481132, 0.0214236, -0.111382, -0.165877, 0.108308, 0.0484747, 0.139224, -0.0850617, -0.169838}, {-0.166197, -0.194064, -0.151463, 0.134904, -0.481822, 0.493718, 0.538873, 0.254361, -0.51176, -0.26151, -0.084429, 0.177571, 0.515823, 0.51063, 0.0761755, -0.0119258, 0.00591751, -0.394725, 0.4179, -0.10752, -0.00221535, 0.759944, 0.174075, -0.64126, 0.617271, 1.24185, 0.184511, -0.546885, 0.603731, -0.337517, 0.0717008, -0.446515}}, HM_W2 = {{0.0286211, -0.904249, -0.676203}, {-0.656389, -1.16818, -1.52884}, {-1.59006, -1.06922, 1.97578}, {0.22397, 0.371549, -0.989147}, {0.0473525, -1.33853, 0.401152}, {0.708637, -0.61993, -1.23215}, {0.291435, -1.50841, 0.240365}, {-0.435938, 0.466465, -0.709412}, {-0.088753, 0.060866, 0.602283}, {0.812735, -0.151196, -0.815903}, {-1.68317, 0.0513056, -0.472237}, {0.861812, -0.169625, -1.04437}, {-1.00204, -1.61351, -0.138631}, {-0.423365, 0.961434, -0.242534}, {-0.786019, 0.523702, -0.564444}, {0.483982, 0.510211, -0.999949}, {-0.140975, -0.497938, -0.792883}, {0.368282, 0.147267, 0.647562}, {-1.04218, 0.769169, 0.0442731}, {0.174371, 0.592163, -0.0617492}, {2.10762, -1.37501, 0.853246}, {0.659996, -0.521005, -0.0716707}, {-0.42003, -0.972288, -1.04886}, {-0.982138, 0.0687433, 0.256636}, {0.0186102, -0.867078, 1.05673}, {-0.10453, -0.557146, -1.3805}, {-1.20654, 0.287456, -0.273473}, {-0.384391, -1.11434, 0.294438}, {-1.26788, -0.037398, -0.553437}, {0.286715, 0.761793, -0.826707}, {1.06255, 0.222357, -0.821302}, {-1.6673, -0.299599, 1.03663}};
        static double[] HM_B1 = {-0.160127, -1.95937, -0.922755, -0.367552, 0.94907, -1.8513, -0.660636, 0.725178, 0.365602, -0.627289, -1.33068, -0.874545, -0.111886, -0.180253, 0.302457, -1.72933, 1.24512, -0.602334, -1.10838, 0.215617, -1.29131, 0.0498471, -1.08868, 0.194632, -0.817074, -1.18509, -0.72331, -0.190125, 0.964133, -0.0953637, 0.250646, 0.306984}, HM_B2 = {-0.00115763, 0.0123504, 0.238138};
        // hit model: end
        /**
         * --hit-model ugritone: the hit model learned also from Ugritone kits' renders of human-played grooves
         * (Ugritone Drums, Riot, Anarchy), with real piano and synth over them; 64 hidden units. Better on Ugritone
         * kits, worse on other makers' (the built-in one is for recordings from anywhere). Made by
         * tools/hit_model.py export --variant ugritone (a text: Android's PyJav reads no files inside the jar).
         */
        // hit model ugritone: begin
        static final String UGRITONE = "mu 0.000146899 0.000269465 0.000356821 0.000487391 0.000413929 0.00033234 0.000271046 0.000258654 0.00019805 0.000236138 0.000274926 0.000254969 1.74767 1.93851 2.12775 1.96955 1.58061 1.52258 1.57456 1.63978 1.62849 1.63193 1.70387 1.90082 -0.577646 -0.820833 -1.00301 -0.589502 -0.551695 -0.54047 -0.488457 -0.505788 -0.502313 -0.489685 -0.509482 -0.654471 -1.04855 -1.22983 -1.32012 -1.06809 -0.833445 -0.807588 -0.815898 -0.848787 -0.855163 -0.856155 -0.864622 -0.998639 -1.22249 -1.33761 -1.52144 -1.26153 -0.983358 -0.980543 -0.980985 -1.02545 -1.03674 -1.042 -1.10364 -1.28262 0.179422 0.179891 0.0563382 0.268061 0.508898 0.000313714\nsd 2.9094 3.15175 3.10299 2.79518 2.39548 2.29739 2.39129 2.41792 2.41766 2.56463 2.91274 3.403 3.78848 4.0797 4.07901 3.73839 3.35279 3.28249 3.27281 3.2615 3.21043 3.23487 3.49173 3.9304 1.80157 1.70418 1.7941 1.56858 1.43828 1.41682 1.36779 1.43955 1.466 1.56376 1.91536 2.53428 2.76694 2.84211 2.85155 2.70764 2.38297 2.32909 2.35998 2.42459 2.40981 2.47692 2.84692 3.5825 3.83549 3.88239 3.88107 3.63542 3.19676 3.0931 3.12362 3.13457 3.0329 3.03842 3.48293 4.28031 0.383706 0.384098 0.230575 0.442951 0.499922 2.26294\nt 0.35 0.4 0.45\nb1 -0.039905 0.992961 -0.79891 -0.58186 0.82206 -1.63909 0.0978318 1.23441 0.595259 -0.421675 0.418359 -0.584564 -0.542307 0.535514 -0.626965 -0.954047 0.295452 -0.674272 0.140898 0.936065 -1.38848 -0.14652 0.622807 0.192903 -1.15427 -0.559478 -0.523795 -0.670259 0.0284988 -0.481803 -0.996995 0.0322157 -1.21945 -0.97015 -2.20141 -1.23035 -0.692624 0.0370246 -0.0781124 -0.56893 0.405464 0.313388 -1.10333 0.0167529 0.234771 -0.450243 -1.03212 -0.901395 -1.35694 0.593082 -0.0337242 -0.0343662 0.131008 0.223954 -1.15492 -0.885013 -0.166986 0.173678 -0.321045 -0.191078 0.156912 -0.0689097 -0.250418 -0.83043\nb2 -0.108113 -0.34219 -0.326432\nw1 0.0727419 -0.344034 0.119884 -0.220276 0.0414353 0.0599194 -0.0474036 -0.175681 -0.298643 -0.021459 0.120755 -0.327984 -0.0653117 0.387411 -0.265576 0.390211 0.062409 0.511535 -0.043321 -0.137307 0.129116 0.104416 -0.473266 -0.0189903 0.143641 0.157023 -0.0373127 0.0837992 -0.109074 0.698072 0.388889 0.186583 0.559188 0.0720364 0.423182 0.0746084 0.000369741 0.415326 0.0603346 0.0728738 0.237829 -0.253678 -0.0418269 0.806917 -0.328508 -0.205728 -0.217761 -0.0201233 0.448184 -0.47168 0.0603765 -0.239764 0.177612 0.0849958 -0.269911 0.014114 0.413361 -0.0484931 0.21929 -0.973865 -0.0803241 -0.391121 0.0967681 -0.272152\nw1 -0.19989 0.233861 0.0664561 -0.204148 -0.380248 0.00940615 0.134271 0.256346 -0.572238 -0.076406 0.155517 0.732084 0.344037 -0.00502236 0.278072 0.729104 0.449825 0.0828319 0.0445004 -0.712228 -0.288595 0.0379568 0.12231 0.146471 -0.0530721 -0.120052 0.10829 0.333382 0.0033424 -0.103892 0.172324 -0.140241 0.348023 -0.787445 -0.194626 -0.414418 -0.287124 -0.468365 0.026248 -0.420335 -0.0732617 -0.0500181 -0.1529 0.14077 0.00250681 0.145961 -0.335194 -0.130641 0.026104 0.692559 -0.062467 0.229716 -0.0633217 -0.0404995 -0.216175 -0.501781 -0.246346 -0.090796 -0.0805903 -0.220487 0.00813071 0.696955 -0.0331328 -0.056949\nw1 -0.530374 0.0209005 0.293579 -0.188513 -0.78859 0.320034 0.220809 -0.617761 -0.427381 0.477236 -0.133278 0.352662 0.0914438 -0.0483834 0.524627 0.706482 -0.371839 0.139436 0.112779 -0.639589 0.322573 -0.0309006 0.368845 0.342228 -0.468073 -0.0477147 -0.451677 -0.078228 0.0967909 -0.126426 0.343922 -0.365994 -0.3944 1.19313 0.0199479 0.763187 0.29746 -0.0938942 0.309121 0.170398 -0.25568 -0.023825 0.301769 0.473399 0.0568026 0.455374 -0.50868 0.396167 0.207648 0.0465987 0.156599 0.440548 -0.167559 -0.154961 0.313016 0.386566 0.293961 0.330638 -0.327324 1.05887 -0.252726 0.187246 -0.283167 0.326778\nw1 -0.123631 -0.174019 0.460003 0.142683 -0.11987 0.204507 0.250696 -0.197623 -0.323722 0.498821 -0.290369 -0.213507 0.557035 -0.123138 0.436041 -0.0194606 0.115286 -0.190961 0.509857 0.222739 0.212621 -0.0909399 0.0862789 0.216419 -0.223689 0.0798074 -0.0560184 0.182252 -0.268139 -0.651493 -0.144568 -0.372251 -0.108426 0.00714854 -0.102862 -0.0497358 -0.0315501 -0.268498 -0.775915 -0.232873 0.536329 0.0908599 0.356606 -0.133606 0.181687 -0.0408024 -0.52206 -0.155767 0.014137 0.184128 0.337264 -0.744648 0.143768 0.299548 -0.526733 0.00026756 -0.201493 0.264707 -0.127888 0.235695 0.314076 -0.199367 0.0429792 -0.114863\nw1 0.0584972 -0.180262 -0.14723 0.452847 0.217144 -0.00953587 0.0505353 0.0800233 0.0649382 0.0483168 -0.0573217 -0.21297 -0.160632 0.182064 -0.0219315 0.357271 -0.00215015 -0.227838 0.00627666 0.0748416 -0.252462 -0.0948897 0.200143 -0.069968 -0.0889441 -0.158553 0.0161787 0.043954 -0.103894 0.0653005 -0.0178102 -0.0923138 -0.0337234 0.426028 0.00731907 0.305519 0.166465 0.280837 -0.436735 0.0665745 0.0634545 0.382134 0.0642246 -0.0998585 0.170512 0.188493 -0.0604743 -0.0314224 -0.404735 0.0669765 0.158061 -0.242292 0.0955337 0.0119611 0.0159534 -0.0654435 -0.0532109 0.341718 0.0336657 0.0275185 -0.00375253 -0.0207296 0.112169 0.328323\nw1 0.225705 0.068104 -0.2813 0.242076 0.227431 -0.260032 -0.151411 0.384382 0.155694 -0.2446 0.138887 -0.105503 0.104834 0.07819 -0.209256 -0.278085 0.265527 -0.151726 0.035859 0.132051 -0.331577 0.0983432 -0.273116 -0.0485443 0.429541 -0.455571 0.319782 -0.032367 -0.109883 -0.0166475 -0.251594 0.318332 -0.0437791 -0.616051 0.0789166 -0.383428 -0.217531 0.27228 0.349577 0.0313732 0.344524 0.0164654 -0.200064 -0.292409 -0.24601 0.169777 0.380528 -0.226145 -0.306827 0.337087 -0.0375675 -0.272422 0.0949022 0.0430442 0.112512 -0.246239 -0.268337 -0.133998 0.37081 0.0653472 0.0698525 0.0239319 -0.113742 -0.0314473\nw1 0.2365 0.357204 0.153117 0.170633 -0.148249 0.150803 0.00994123 0.133722 0.19052 -0.151634 -0.193206 -0.0115924 0.236945 0.00713409 -0.213045 -0.0188074 0.387427 0.118467 0.0421644 0.137794 -0.315952 0.0951852 -0.323803 0.00155169 -0.124625 -0.525414 -0.118963 0.137569 0.0586842 -0.270615 -0.160449 0.00207838 0.0177763 -0.209142 0.323119 -0.354915 -0.00198717 -0.00536176 -0.294592 0.101408 0.175358 0.414106 -0.00694745 0.0468593 -0.00876146 0.0912827 0.455465 -0.128955 -0.391035 0.0948713 0.0281839 0.753212 0.329991 0.0315351 -0.454329 -0.377737 -0.238618 0.101194 -0.105021 0.0866118 0.154962 -0.0655156 -0.0396878 -0.00719878\nw1 0.0691705 0.115714 0.003386 0.426168 0.359834 0.0560757 0.345619 -0.189186 0.185487 0.033564 0.0932331 0.0906421 0.0668229 -0.737058 -0.308769 -0.143611 0.13536 0.0230258 0.0860134 0.229825 0.0376129 -0.0270102 -0.773211 -0.150513 0.394967 -0.0937943 0.254453 0.451197 0.0210706 -0.342277 -0.188603 -0.0102648 0.302263 -0.158303 0.296656 -0.126053 -0.00748502 -0.0817816 0.182569 0.129734 0.237189 0.115206 -0.36172 0.0598396 0.0601169 -0.277146 -0.259682 -0.40333 -0.319862 0.140361 -0.00731795 0.702365 0.304881 -0.145951 -0.139693 0.145383 0.0997098 0.0724348 0.163412 -0.139924 0.0182092 0.0622172 0.300662 -0.188271\nw1 0.0344733 -0.0343363 -0.0667637 0.191661 0.282773 0.0969551 0.234785 -0.285114 0.0535159 -0.0415854 0.149072 0.137682 -0.593072 -0.301281 0.258702 -0.220406 -0.0722946 0.0853457 0.311335 -0.292904 0.0371839 0.108478 -0.484064 -0.195337 -0.201418 1.09277 -0.0656768 0.512107 -0.101107 -0.134007 0.303096 0.117127 -0.0488688 0.12791 -0.290363 0.205107 0.106992 -0.0151262 -0.167164 0.114646 0.205468 0.413343 0.061726 0.119429 -0.104207 -0.773018 -0.522447 0.0257735 -0.0916647 -0.0865372 0.0690778 -0.0376855 0.0825843 -0.0021192 0.317636 0.0513641 0.213961 -0.0289231 0.358411 -0.0408824 0.217733 -0.162063 0.095511 -0.167599\nw1 0.307965 0.0216511 0.153973 -0.743419 -0.151276 0.0749686 0.00766849 -0.471812 -0.34103 0.145116 -0.239631 0.196667 -0.2644 0.117662 0.102154 -0.102879 0.405037 -0.0405229 -0.0253769 -0.349623 0.131382 -0.149843 0.286874 -0.00661405 0.237096 0.688139 -0.234974 0.466414 -0.032276 0.242193 0.0290526 0.0660563 -0.34766 0.114146 -0.243984 0.196206 0.065394 -0.747246 0.309615 0.426271 -0.298726 -0.694245 0.464518 0.439165 0.0118207 -0.501644 0.5588 0.477915 -0.0996537 -0.65737 0.160393 -0.564201 0.0622549 0.199741 -4.82343e-06 -0.103145 0.108349 -0.227189 -0.0695935 -0.120755 0.566799 -0.401017 0.00429418 -0.58115\nw1 0.19364 -0.442679 0.180407 -0.454538 0.0376618 0.0747807 0.102624 0.122859 0.148951 0.143305 -0.296338 0.497395 0.141025 0.133716 0.243479 -0.376177 0.472162 -0.0699374 0.0484393 -0.169054 0.213446 -0.0100979 0.112833 -0.180184 0.798195 -0.640225 0.166616 0.412329 0.0167033 0.27383 -0.0837589 -0.110756 0.0500019 -0.070111 -0.0104646 -0.0198905 -0.276578 -0.209106 0.142954 0.342653 -0.0958883 -1.00778 -0.335035 0.062978 0.0388281 0.2042 -0.0163485 0.0370724 0.125185 -0.172006 0.100895 -0.23147 -0.0304445 -0.0804165 0.0765056 0.172496 -0.0679196 -0.133516 0.448095 0.0151436 0.734581 -0.183856 -0.206612 -0.877429\nw1 0.0777162 -0.0507589 0.259277 -0.951105 -0.0408017 -0.186198 0.0137869 -0.00825395 0.0354763 -0.151717 0.0194529 0.269401 0.336666 0.332388 -0.150351 -0.0410791 0.033424 0.117058 -0.0280859 0.00106586 0.100083 0.0247439 0.258458 0.139928 0.361913 -0.138141 0.471812 0.0832458 0.129386 0.191779 -0.243994 0.0764442 0.0515066 -0.19584 0.0354852 -0.0149928 -0.00636032 0.427962 0.124038 -0.115937 -0.360501 -0.330175 -0.0173038 0.178208 0.26093 -0.028216 -0.00627162 0.305286 0.260544 -0.239305 0.0656983 0.388183 -0.0745374 0.187986 -0.0547353 0.157258 0.0520425 0.226376 0.287324 0.0389686 0.326896 -0.686772 0.0574887 -0.107178\nw1 0.00327211 -0.0117019 -0.0466509 -0.107813 -0.18639 0.0385842 -0.114903 -0.0719996 -0.275301 -0.121862 -0.158681 0.358982 -0.0731529 -0.0736016 -0.0387082 0.385392 -0.0745075 0.383465 0.15199 0.0536046 0.0207428 0.139165 0.138278 -0.158992 -0.143261 0.0483673 -0.094222 0.0482521 0.116749 -0.312597 0.0926874 -0.0295945 -0.687381 -0.17929 0.0381839 0.0569232 0.0614593 -0.0136403 0.0174476 -0.0115569 -0.0272169 0.00887596 -0.370681 0.640142 0.152574 -0.0404216 -0.208784 0.180792 -0.0435269 -0.178025 0.172894 -0.15238 -0.122801 -0.113635 0.102033 0.153549 -0.146954 -0.196772 -0.167525 0.00496619 0.0465179 0.253178 0.147793 -0.209552\nw1 -0.229459 0.133616 0.356175 -0.0790516 0.0567133 -0.264965 -0.11577 0.274243 0.118262 0.0274819 0.189254 0.362694 -0.412233 -0.0236638 0.0282894 0.325236 0.243217 -0.123549 -0.026961 -0.00850485 -0.0300587 -0.407396 -0.117883 0.20075 0.048374 -0.13623 -0.248435 0.155527 -0.041599 -0.22781 0.0324634 -0.114072 -0.231132 -0.180627 -0.176037 0.00705869 -0.0799845 0.215176 -0.299682 0.128439 -0.207342 -0.102143 0.0255095 0.0996726 -0.0410095 0.114179 -0.364561 -0.0451852 -0.0474257 0.231242 -0.00553049 0.00882271 0.269192 0.201455 0.123928 -0.30172 0.0693792 0.11064 -0.267258 -0.119276 -0.158048 0.239096 0.165913 0.0999214\nw1 0.107815 -0.0192312 0.0203874 -0.0267567 -0.0121044 -0.248069 0.0978013 0.00914854 0.00223153 0.190791 0.0390969 -0.2646 -0.345107 0.0684788 0.0443338 0.34411 0.306077 -0.0640908 -0.0206936 -0.0855492 -0.0598663 -0.228654 0.0624691 0.0641619 -0.149764 -0.0815714 -0.29817 -0.00502138 0.0772084 -0.131412 0.00156916 0.0682824 -0.588791 0.158001 -0.0709457 0.0649714 0.178307 0.108544 0.00541273 -0.0334629 -0.0443581 -0.0865598 0.0785844 0.307784 -0.0487054 0.0526007 -0.333934 -0.103948 0.0152791 -0.340109 0.00375741 0.0988538 0.387108 -0.222572 -0.192188 0.1855 0.212882 0.00128579 0.0414013 0.0720094 -0.112185 -0.522692 0.00568941 -0.168891\nw1 -0.0858588 -0.299161 0.354658 -0.187054 0.104277 -0.395183 0.0680954 -0.239461 -0.0832343 0.44647 0.106742 -0.0918207 -0.247897 0.0117597 0.21487 0.179035 0.296177 -0.0446437 0.287909 0.0432386 -0.00468569 -0.0165325 -0.00522106 0.0773543 -0.0751982 0.31938 0.246683 -0.0327179 -0.157211 -0.669646 0.0084382 -0.0778789 -0.257721 -0.198499 -0.216491 0.00950736 0.0088759 -0.121949 -0.1087 0.0649044 0.145659 0.234706 0.21465 0.271196 -0.01701 0.146596 -0.465433 -0.0710222 -0.0572237 0.00155941 0.237287 -0.14105 -0.164625 -0.348158 -0.195537 0.0360414 0.0315494 0.0858952 -0.157738 -0.252135 -0.0389348 -0.170118 -0.802252 -0.152801\nw1 0.158702 -0.158459 0.206052 0.159565 0.306488 -0.158844 -0.0227088 0.00723581 0.121848 0.160649 -0.193924 0.0635302 0.0400405 0.103007 0.0179632 0.174489 0.0933027 -0.0405644 -0.207885 -0.0227675 -0.21786 0.0285274 0.0457808 -0.00943981 0.0917239 0.00272509 0.226852 0.0806498 -0.086756 0.00619416 0.110666 0.0966245 -0.105363 0.1969 -0.136661 0.111128 0.0556768 0.139987 -0.0700872 0.202389 0.0612566 0.107651 0.323656 -0.022753 -0.0484298 0.0414939 0.00642187 0.0821249 -6.06013e-05 0.0487291 -0.0257572 -0.0133115 -0.0360069 0.00410778 -0.166364 -0.177413 0.084315 0.145363 0.147463 0.0167671 0.0182694 0.0960396 -0.620324 -0.171051\nw1 0.167729 0.198168 -0.267416 -0.082562 0.223037 -0.525744 0.0691923 0.0859689 -0.0462266 0.0398839 -0.0648469 -0.0139177 0.04295 0.17281 -0.095795 -0.0415004 0.160956 0.186496 -0.054791 -0.0667777 0.0476126 0.0657401 0.0214824 -0.319644 0.263218 -0.148877 0.224012 -0.213384 0.0309239 0.00598973 0.0966825 0.130636 -0.0455092 -0.126186 0.129679 -0.00483691 0.0475372 -0.226112 0.386082 0.111403 0.0511455 -0.0790949 0.0678263 0.0259685 0.00191143 -0.00328875 0.0422628 -0.284732 0.162437 0.204201 -0.0817792 -0.229088 -0.0601606 -0.0334746 0.0966401 -0.0692375 -0.118704 0.0719941 0.00701297 0.0920066 0.145289 0.294958 -0.549385 -0.217682\nw1 0.0554342 0.108456 0.199435 0.162494 -0.115505 -0.521358 -0.0677899 0.0386219 -0.126357 0.0203059 -0.151817 0.0619919 -0.345295 0.198623 0.0930791 0.012769 0.0135592 0.0137975 0.162363 -0.0562709 0.0374301 0.015587 -0.110536 -0.229141 -0.305119 0.0262282 0.139216 -0.218576 -0.00566968 0.0798552 -0.0627183 0.110128 -0.165226 0.147021 0.0072953 -0.020553 -0.104199 0.0766969 0.0294539 -0.316285 0.0486423 -0.162248 -0.0228845 0.137264 0.0398109 0.141177 0.186988 -0.0209101 -0.00252523 0.0232388 0.0884838 0.247045 0.0978936 -0.163254 -0.144042 0.0502044 0.152545 0.183681 0.0629822 0.0429415 0.168505 0.00473627 -0.72731 -0.207924\nw1 0.0671254 0.216911 0.129459 0.131537 -0.0981734 -0.696832 0.108735 -0.137881 0.126681 0.032858 0.122866 0.00734814 -0.00758087 -0.139801 -0.0427059 0.0494117 0.193055 -0.219222 -0.0544678 0.116745 0.0978621 -0.0659763 0.0428394 -0.284488 0.0157371 -0.211116 0.0152927 0.0613861 -0.058656 -0.0783463 -0.100913 0.107411 0.158554 -0.0450305 -0.0955236 0.0277863 -0.0167978 0.259915 -0.124217 -0.149896 -0.0306274 -0.12469 -0.189981 -0.107253 0.0758273 -0.0532745 -0.257873 -0.0652492 0.0296446 0.0586625 -0.0878751 0.183844 0.00611166 0.0124072 -0.0795048 0.12636 0.158954 -0.0188172 0.252581 -0.0152865 -0.00915167 -0.0734666 -0.293123 -0.175685\nw1 0.0944056 -0.11462 -0.33665 0.109687 0.0222571 -0.319699 0.0711476 0.00904825 -0.00778075 0.0401947 0.367992 0.0242521 -0.057082 -0.237167 0.105755 -0.180349 0.0240974 0.0690945 0.150508 -0.107743 0.0933938 -0.0290977 -0.1772 -0.580274 -0.112337 0.0928623 0.0230721 0.286679 0.0723844 -0.11926 0.142555 0.0132117 0.0633808 0.14251 -0.0351454 -0.0531736 0.0648252 0.198033 0.0164812 0.0317011 0.11381 0.139696 0.163112 0.0659882 -0.0173452 -0.411284 -0.0505658 -0.591697 0.00630885 0.0350718 0.0251082 0.120291 0.0995846 -0.108574 0.0178819 0.0249496 0.129535 -0.00952547 -0.00992305 0.0662118 -0.09239 -0.203379 0.159371 -0.170456\nw1 0.094581 -0.174317 -0.661438 -0.508403 -0.236529 -0.744096 -0.155851 -0.0422322 -0.0235253 0.1381 -0.149863 0.0403422 0.190532 -0.15754 -0.179974 -0.176404 0.114155 0.136452 -0.040982 0.0246476 0.0338364 -0.101238 0.142238 -0.200593 0.0424089 0.133793 -0.0137717 -0.0283758 -0.0654173 0.104177 0.00663875 -0.169261 -0.0305932 -0.314428 0.152825 -0.128391 0.12981 -0.564784 0.292727 0.218599 -0.0908368 -0.158345 0.0662274 0.25204 -0.33423 -0.0860164 0.234318 -0.325448 0.0564768 -0.172886 -0.136433 -0.16991 0.0972735 -0.0671579 -0.103504 -0.0668748 -0.154809 -0.238466 -0.167346 -0.0857691 -0.0241579 -0.07685 0.00182368 -0.615371\nw1 0.123341 -0.114381 -0.495753 0.0536588 0.0141064 -0.539709 0.125793 -0.00145165 -0.0964601 0.120297 -0.0381261 0.0684414 0.144472 0.0285435 0.0826836 -0.0594398 0.109706 -0.225296 -0.0765138 -0.00756901 -0.128205 -0.0383246 -0.154718 -0.286666 0.168393 -0.232515 -0.116691 0.131756 -0.0535676 0.0516666 -0.0464803 -0.216296 0.0671994 -0.047782 -0.0457903 -0.136247 -0.0589379 -0.148664 -0.128148 0.0454207 0.134588 0.0942921 -0.182406 -0.226822 -0.010653 -0.0285881 -0.348622 -0.244818 -0.0284304 -0.128335 -0.0311073 -0.0260368 -0.259501 0.128244 0.230558 0.0824784 0.0163975 -0.239006 -0.103031 -0.0386755 0.101479 0.0916236 0.140213 -0.282412\nw1 -0.148658 0.188033 -0.132069 -0.0664626 -0.0590984 -0.178994 0.0863363 0.191692 0.0483119 -0.240668 0.125383 0.0785391 -0.347991 0.0399063 -0.116474 -0.0149457 -0.220278 -0.0360552 -0.057042 0.185399 0.0386839 -0.0495153 0.102567 0.165697 -0.175858 0.103246 -0.10358 -0.17942 0.0851783 0.0235341 -0.00353671 -0.0468902 0.0305777 0.0782957 -0.0704229 -0.0207863 -0.169325 0.177796 -0.117858 -0.215935 -0.189415 0.27687 -0.0208932 -0.174035 0.0907534 0.146371 0.0559063 -0.0533412 0.132885 0.0109554 -0.0600343 0.0514498 -0.261091 0.0822257 0.180137 0.030422 -0.098372 0.461785 0.261768 0.0833444 -0.112669 0.290734 0.0412033 0.486998\nw1 -0.136989 -0.115197 -0.013317 -0.202338 -0.126059 0.0189923 0.0697348 -0.129868 0.256985 0.409334 -0.0394615 -0.173019 0.0189062 -0.049586 -0.128677 -0.114431 -0.0795491 -1.27805 -0.0745436 -0.112766 0.0958518 0.0177838 0.0285359 -0.0638432 -0.0184946 -0.0469186 -0.212261 0.061893 -0.112179 -0.00315786 -0.0654888 0.154024 -0.141754 -0.0757026 -0.118175 0.040132 0.558731 0.0865237 0.034034 -0.388393 -0.0577647 0.0136883 -0.149624 0.634866 -0.129711 0.0563418 -0.132128 -0.036942 0.235554 -0.474903 0.0679569 -0.00752975 0.572931 0.0740684 0.00312487 0.111289 0.917746 0.0937294 0.0387455 -0.301527 -0.0575108 -0.146189 0.141749 -0.0360863\nw1 -0.012142 0.100918 0.0903291 -0.198704 -0.017402 0.00313808 0.240159 -0.0908105 0.119716 -0.0446909 0.180845 0.439761 -0.0135852 -0.0628521 0.00243973 0.274115 0.0581997 0.00867936 -0.189888 -0.24049 0.0575513 -0.0964398 0.100423 0.0654926 -0.100327 -0.0565349 -0.248657 0.175761 0.0758617 -0.0302788 0.075138 -0.0373368 0.0444508 -0.24471 -0.179321 -0.0620014 -0.284077 -0.00560031 -0.021536 0.361629 -0.136834 -0.0286166 -0.162401 0.166023 -0.0381437 0.0615276 -0.2725 -0.0558305 0.0317 0.185178 -0.166418 -0.0415058 -0.194761 -0.143119 0.246505 -0.035925 -0.389945 0.0363704 -0.228174 -0.165559 -0.0566495 0.0476997 -0.00097226 -0.0190186\nw1 0.131812 -0.0246958 -0.0229946 -0.162368 -0.218746 0.029572 -0.00700505 -0.190494 0.102061 -0.191853 0.108104 -0.289568 -0.0877024 0.12762 -0.112101 0.112684 -0.289126 -0.034866 -0.0210333 -0.0509832 0.0230976 0.129833 0.0857985 0.0447445 -0.0215899 -0.0247104 -0.177636 -0.0798913 0.0588717 0.0167429 0.161939 -0.154486 -0.109293 0.478038 -0.209901 -0.0577326 -0.253546 0.00199301 0.000409973 0.104348 -0.315205 0.000432025 -0.159682 0.0794112 0.0136745 0.0959932 -0.253948 -0.00838325 0.16269 -0.169517 -0.262208 0.10602 -0.212412 -0.350679 0.0981902 0.115226 -0.693648 0.196082 -0.132961 0.205522 -0.13539 -0.122713 0.0562675 0.129408\nw1 -0.423841 -0.447146 0.0407842 -0.284839 -0.0725962 -0.0128267 0.328968 -0.465488 -0.153976 0.252103 -0.0848778 -0.370464 0.148387 -0.177675 -0.211104 0.158936 0.340702 -0.479463 -1.07417 -0.151708 -0.0738743 -0.262517 0.306738 0.371679 -0.223873 0.331211 -0.232807 -0.242642 -0.712445 -0.251812 -0.403686 -0.0405319 -0.233657 -0.148162 0.064801 0.506757 0.450352 -0.268491 -0.311988 0.382806 0.0731474 -0.0479922 0.152593 0.353167 0.11076 0.0584255 -0.549968 0.135619 -0.182049 -0.288521 0.21498 -0.423818 -0.0273271 0.165314 0.0487514 0.166196 0.0116531 0.298523 -0.565199 -0.184802 0.193613 -0.0876306 -0.281719 -0.315981\nw1 0.0837289 -0.143478 0.0596922 0.0838078 0.0777594 0.0203093 -0.0740428 -0.0713218 0.165152 0.0140674 0.132745 -0.175191 -0.00634454 -0.115655 -0.0516875 0.334032 0.0898632 0.114853 -0.0723713 0.0506678 -0.0488372 -0.102469 0.145048 0.00526721 -0.144593 0.0250818 -0.186417 -0.25123 -0.0938169 0.0296681 -0.150814 -0.014631 -0.0749533 0.253993 -0.0854957 0.234325 0.039317 0.107722 -0.0765203 0.0429582 -0.266066 0.185067 -0.165876 0.0597305 0.0518485 0.0668663 -0.031481 0.0132972 -0.47509 -0.00216701 -0.0605213 -0.0918017 -0.0117313 -0.118568 -0.11299 0.0362013 0.0384517 0.0674209 -0.242451 0.022147 -0.107757 -0.0670501 -0.185903 0.175496\nw1 -0.0366933 0.177821 0.0367684 -0.0942042 -0.0584284 -0.00509253 -0.00231796 -0.0230378 0.0515203 -0.110106 -0.12478 0.125094 -0.0465966 0.0221749 -0.0193029 0.0955377 0.114484 0.0240399 -0.395518 0.0501928 0.0247324 -0.184321 -0.00530258 -0.203883 -0.130592 -0.0961932 -0.0579141 -0.21684 -0.124902 -0.0912342 -0.0786069 -0.105277 0.061296 -0.0908741 -0.0314487 0.0965962 0.221061 -0.0218958 0.022193 0.0869421 -0.407059 0.0487712 0.0965636 0.0013294 0.162394 -0.0762714 -0.0789724 0.0532442 0.0636951 0.0579401 0.131221 -0.0562937 -0.00871907 0.165848 0.128297 -0.201789 0.208769 -0.261561 0.0014803 0.082686 0.133639 0.0686595 -0.136606 0.0884054\nw1 0.276615 -0.0887694 -0.226537 -0.043468 0.0702568 -0.0681414 -0.0223577 -0.0977012 0.0509906 -0.108852 -0.00810465 0.0273139 -0.0631325 -0.0903626 -0.06855 0.00777272 0.0293683 -0.0360766 -0.415947 -0.0900388 0.0728634 -0.190642 -0.00175588 -0.00598632 -0.321957 -0.158019 -0.11914 -0.293992 -0.269207 0.18109 -0.185285 -0.127186 -0.197869 -0.209324 0.0516474 -0.0983867 0.19722 -0.135402 -0.108007 -0.0911441 -0.309956 0.0115982 0.095356 -0.177449 0.178335 -0.0942215 -0.0183163 -0.0641618 -0.250647 0.0238122 -0.151824 0.239934 0.0143513 0.200111 -0.0616147 0.00687262 -0.0662388 0.257061 -0.212369 -0.0646702 0.134984 -0.0326545 -0.0718505 0.0344804\nw1 0.491362 0.135503 -0.146284 -0.109721 0.0474128 -0.0532578 -0.258663 0.0679953 -0.171099 -0.0145652 0.544632 -0.0409337 0.05621 -0.576667 -0.0963759 0.0806797 0.0206908 0.0661048 -0.0693889 -0.13963 0.0811652 -0.179979 -0.524591 -0.204118 0.149285 -0.229647 0.440779 -0.0510629 -0.443927 0.013684 -0.14297 0.158107 0.000832623 -0.12587 -0.0376714 -0.322712 0.0425437 -0.212507 -0.212467 0.158902 0.137108 -0.0489066 -0.206709 0.00747346 0.186455 -0.204413 -0.166562 0.109438 0.210436 -0.100594 -0.0813737 0.22671 -0.113581 -0.0317615 -0.0328084 0.0784049 -0.13122 0.191746 -0.110213 -0.195292 0.119267 0.0309994 -0.120835 -0.147923\nw1 0.711747 -0.457564 -0.375533 0.358197 -0.0995037 0.0609903 -0.611275 -0.0144327 -0.0263697 0.0367798 0.0812623 -0.0925752 -0.0742732 -0.419751 -0.110419 -0.15652 0.34998 -0.123923 0.208304 -0.000835274 -0.149456 0.022056 -0.278137 -0.185922 0.101265 0.0628926 0.442188 0.0176511 -0.337517 0.0368501 0.000793238 0.136934 -0.153029 -0.0809775 -0.0159879 -0.630865 0.0337653 0.171105 0.298271 -0.226014 0.441424 -0.118971 -0.120919 -0.0257889 -0.103571 0.290808 0.04153 -0.0723011 -0.039251 0.0809983 -0.551688 -0.168331 0.0106436 -0.213351 -0.144576 0.0794739 0.124704 0.363422 0.229783 0.225191 0.29639 0.209018 0.29281 -0.00355163\nw1 0.562658 -0.412582 -0.294675 0.00711685 0.0225622 0.106534 -0.687335 -0.0967986 -0.114331 -0.0392103 -0.0276367 -0.0307003 0.00979321 -0.303825 -0.671205 -0.308665 0.224423 -0.0820633 0.16299 0.190234 0.241334 0.146653 0.374496 -0.338308 0.768831 0.11329 0.330376 -0.0847345 -0.271614 -0.156932 0.344432 0.395501 0.218087 0.015735 -0.226556 -0.449821 0.0131921 -0.122106 0.136206 -0.496174 0.542959 -0.165267 -0.145882 -0.111371 -1.11161 -0.000672968 0.513415 -0.409622 0.0700973 -0.0450181 -0.590961 -0.166564 0.0436466 -0.701944 -0.0492094 0.345442 0.0902416 0.309584 0.152051 0.0262992 0.148517 -0.0623222 -0.147835 -0.345224\nw1 -0.0197845 -0.562961 0.0085206 -0.0972465 0.0376197 0.0811598 -0.181568 -0.0221138 0.050391 0.274363 -0.0312254 -0.0722908 -0.0129896 0.00258104 -0.275103 -0.478468 0.0643335 0.120153 -0.0223142 -0.119488 0.326632 -0.012912 -0.158656 0.0557517 0.278108 0.0594353 0.432704 0.253207 -0.429366 -0.139183 0.0525683 0.26452 0.0419595 -0.0669969 0.151346 -0.367979 -0.0406631 -0.190707 -0.235043 -0.116674 0.941646 -0.00806725 0.0815307 -0.0171128 -0.927061 -0.0976755 -0.0982166 -0.105381 0.0540619 -0.1596 -0.0688032 0.0374146 -0.272939 0.435628 -0.0730379 0.108473 -0.123728 -0.126626 0.310141 -0.045055 0.166561 -0.0280272 -0.0173677 -0.595519\nw1 -0.00507312 0.011896 0.097903 0.196216 -0.00303258 0.0247446 -0.12157 -0.0299964 0.0312574 0.122602 0.0571005 0.102354 -0.161669 0.224332 -0.047947 -0.0467498 -0.147072 0.141018 0.0944292 0.0102321 0.0716832 -0.123135 -0.101631 0.226731 -0.0767435 -0.152494 0.00995903 -9.77492e-05 0.121866 0.0746025 -0.0846717 0.393714 0.0202171 0.0590067 -0.00691445 0.0946415 0.0954291 0.0890905 -0.0487891 -0.168852 0.184377 0.136041 0.126007 -0.0945116 -0.254713 -0.116819 0.0792408 -0.167453 -0.12552 0.0900076 -0.0198192 0.0147992 -0.302799 0.26259 -0.080412 -0.0420747 0.184921 0.0266797 0.437879 0.0718486 -0.158509 0.133858 -0.0437844 0.163337\nw1 0.19901 -0.185799 -0.0678354 -0.0330818 -0.305573 -0.0538891 -0.0375275 0.160215 -0.162475 0.0588366 0.230308 0.0217259 -0.0850857 -0.0893559 -0.0532062 -0.0205116 -0.363944 -0.179998 0.0958767 0.196033 0.0235816 -0.904377 -0.0667381 0.13886 -0.042851 0.0147696 0.0801259 0.251037 0.0807492 0.0849059 -0.0371752 -0.256091 -0.0702718 -0.154892 -0.42711 0.120336 -0.042539 0.19104 -0.0436726 0.38737 -0.107421 -0.0395674 -0.0439862 -0.359109 -0.0276402 -0.103961 -0.154041 0.0226742 0.0697614 -0.236104 -0.401173 -0.0392615 -0.0997721 -0.3099 0.172976 -0.152959 -0.0805509 -0.130185 0.116952 -0.353282 0.000897297 -0.00731592 -0.138187 -0.0237034\nw1 -0.0021418 0.23022 0.0628418 -0.0608762 -0.19547 0.0403549 0.0907322 0.178548 0.0446306 0.147337 0.276997 0.373582 0.3211 0.13611 -0.176765 0.259809 0.281148 0.181147 -0.0377727 -0.0701568 -0.0108175 -0.0561727 -0.00709998 0.0508354 0.0476037 -0.014493 -0.0246498 -0.0761915 0.0449195 -0.0973719 0.132251 -0.33979 -0.00184036 -0.148482 -0.315003 -0.277596 -0.834027 -0.0560588 -0.0360506 -0.11559 0.0658387 0.0288175 -0.0987334 -0.0898842 -0.0652745 -0.0647247 -0.118531 0.0303831 -0.029815 0.049126 -0.0726075 -0.0315869 0.0116369 -0.0586327 -0.10445 -0.219018 -0.292744 -0.597573 0.00972936 -0.0283822 -0.221615 0.452532 -0.0789565 0.0886273\nw1 -0.0768219 -0.01018 0.242593 0.11572 -0.44521 0.0552625 -0.0330451 -0.0469812 0.102505 0.0254194 0.0972723 -0.561543 -0.0449561 0.0163997 0.237526 -0.00758541 0.0998743 0.0188808 0.211288 -0.213591 -0.145846 -0.14039 0.0102986 0.0471893 -0.174893 0.0560126 0.217785 -0.0181802 0.0754354 -0.134838 -0.385772 -0.142611 -0.0561305 0.522946 -0.421643 -0.239247 -0.528611 -0.0478125 0.223143 -0.0566377 0.00269651 -0.0316227 0.228735 -0.0896166 0.0284089 0.0897845 -0.0475688 0.00262386 -0.294677 -0.0947613 0.0645552 0.0570806 -0.187416 0.0652496 -0.168171 -0.153311 -0.0524933 -0.537802 -0.209735 0.294668 -0.0119799 -0.0944211 -0.0721507 0.0812868\nw1 0.242511 0.377804 0.140135 0.259347 -0.0228214 0.0603191 0.13726 0.199392 0.280762 -0.145438 0.235932 -0.0643696 -0.0635136 0.259287 0.174931 0.123669 0.39746 -0.103129 -0.194109 -0.17903 0.0639917 -0.414599 0.296414 0.0593767 -0.322233 -0.0447141 -0.555654 -0.297282 0.875238 -0.143162 0.143253 -0.34935 -0.116097 -0.359499 -0.321892 -0.391162 -0.0262647 0.218978 8.23261e-05 -0.0945106 -0.380106 0.307567 -0.11055 -0.153655 0.00654951 0.111662 -0.188914 0.201861 -0.319252 0.0029691 -0.315858 -0.032874 0.323174 -0.395613 0.0545186 -0.124053 -0.111288 -0.0584795 0.0883332 0.228091 -0.161981 0.00970182 -0.147089 0.224653\nw1 0.105327 -0.0238454 -0.170266 0.227963 -0.0667352 -0.0127831 0.0203106 0.0407117 0.0604802 0.00787059 -0.00454969 0.00425158 -0.05232 0.202733 -0.179362 -0.124737 0.0251917 -0.0121596 0.0230856 0.215534 -0.19388 0.125336 0.140225 -0.161323 -0.0572817 -0.270487 -0.0140507 0.113194 0.264596 -0.122529 -0.296116 0.150053 -0.0107357 -0.111227 -0.187505 -0.154128 -0.0348987 0.0301623 0.203607 -0.122358 0.137451 -0.0599472 -0.0960491 -0.0291284 -0.0796521 -0.000270786 0.0482231 -0.242661 -0.416666 0.103843 -0.0738087 0.122165 -0.242975 -0.0593126 -0.27365 -0.0740172 -0.0691685 -0.0655558 0.172351 0.037161 0.0355344 0.0927183 0.0901057 0.171408\nw1 -0.122668 -0.0281778 -0.081171 -0.0578335 0.0571961 0.122446 -0.00254616 -0.0877582 0.271412 0.224096 -0.0635988 0.248905 0.0612359 0.420176 -0.0311092 0.175553 -0.258258 0.0152011 0.132662 -0.12439 -0.504395 0.139684 0.0965573 -0.150633 0.0255766 0.0240972 -0.165147 0.0451681 0.256074 0.0754695 -0.253591 0.256826 0.0626882 -0.101189 0.0976541 0.0932998 -0.00424235 0.001112 -0.068692 -0.0457126 0.0310601 0.0374906 -0.289957 0.105528 0.138367 -0.0371397 -0.00346958 -0.0814414 -0.068906 -0.102014 0.169203 0.00198325 -0.245746 -0.0764164 -0.230628 -0.126467 0.32952 -0.130874 0.0470656 0.0315085 0.032469 0.110856 -0.241127 -0.10331\nw1 0.0796983 -0.033461 -0.0144922 -0.0497944 0.198543 0.0377439 0.032652 -0.11655 0.0227532 0.0127932 0.0984814 0.065662 -0.00171825 0.451864 -0.096994 -0.0604349 -0.141911 0.067898 -0.0268306 -0.0242043 0.0778953 0.0238016 -0.0058705 -0.225148 0.0191374 0.0474681 -0.271005 0.00169053 0.431568 0.109836 -0.0628083 0.0889378 -0.0150292 0.0523398 0.261589 -0.022507 -0.0248958 0.0964644 0.127398 0.0479741 -0.0762613 0.248491 -0.048417 -0.0863374 -0.0929527 -0.131501 0.163397 -0.176648 -0.251007 -0.0904182 -0.010915 0.144205 -0.0191013 0.0750476 -0.00359907 -0.160381 -0.0508761 0.0361123 -0.0360733 0.0674814 -0.056591 -0.0299676 -0.295446 -0.114681\nw1 -0.0789043 0.130738 -0.106041 -0.0800126 0.456642 -0.145708 -0.0461084 0.00321306 -0.195668 -0.229115 0.415942 0.160253 0.23316 0.075773 0.0408428 0.00146995 -0.0661927 0.101389 0.0519703 0.0039984 -0.311722 -0.16894 -0.380889 0.0399493 -0.0209345 -0.01818 0.18924 -0.149928 0.43342 -0.116094 -0.0316989 0.122701 -0.00500139 0.0655551 -0.00149476 0.0327671 0.0970156 0.186509 0.201678 0.0803757 -0.0857561 0.169323 0.0708682 -0.0448278 0.160068 -0.0100379 -0.00978212 -0.0279732 0.153105 -0.0961629 -0.142222 -0.0982455 0.105107 0.252548 0.109592 0.0582847 -0.134332 0.0901319 -0.166799 0.0248821 0.108323 0.0281887 0.00825565 -0.00056474\nw1 0.0836218 0.148897 -0.184966 0.100801 0.00488247 -0.142155 -0.209968 0.0884095 0.0592395 -0.0837833 0.201154 0.0417333 -0.064115 0.275237 -0.040591 -0.000410104 0.0964277 -0.0918236 -0.0988759 -0.0125018 -0.676044 -0.00169963 -0.0956662 -0.162986 -0.124431 -0.137846 -0.0866049 -0.060042 0.40105 -0.0593594 -0.110478 0.281027 -0.0457254 0.124251 -0.124559 -0.0133301 0.0673742 -0.108749 0.126199 -0.0986141 -0.064065 -0.222812 0.0134605 0.04916 0.178232 0.294261 -0.0689596 0.127719 0.0995385 -0.0516695 -0.0990002 -0.139349 0.0267102 0.050983 0.204015 -0.0176167 -0.0113786 -0.0748254 0.0954246 0.0263219 -0.0394369 -0.114283 0.352602 -0.0268894\nw1 0.0551698 0.265881 -0.0820547 -0.189656 0.202808 0.0877393 -0.111147 -0.030599 -0.00716624 -0.115194 0.0805963 0.0284482 0.0330818 0.359723 -0.0765579 -0.0560775 -0.0197976 -0.105707 -0.2891 -0.0519809 -0.0633635 -0.099758 0.13688 0.0880565 0.147917 0.0654295 -0.121144 -0.00902173 0.382008 -0.0330009 0.0174505 0.022335 0.119957 0.100409 0.153612 -0.0295429 0.355285 0.117151 -0.0833722 -0.236804 0.100169 -0.131671 -0.0282992 -0.055381 -0.233347 -0.0992115 0.280425 -0.146851 0.328374 0.0224378 0.0825355 0.0446851 0.253914 -0.296014 0.0814467 0.0267814 0.113154 0.0598667 0.186003 -0.0381874 -0.159415 -0.181851 -0.0338855 -0.0732149\nw1 -0.249802 0.237278 0.0920631 -0.112226 -0.0190349 0.0249491 -0.0544027 0.00169056 0.0287615 -0.0265208 -0.0388672 0.0430303 0.0302875 0.0922102 -0.0655624 0.05861 -0.100988 -0.0809215 -0.240078 -0.0871738 -0.312284 -0.0623046 0.28288 0.526848 0.392704 0.200596 -0.152944 -0.0917999 0.552729 0.0833875 0.151045 0.405519 -0.0927077 0.000118708 -0.134899 0.0472096 0.10369 -0.273618 0.0091094 -0.0728516 0.02108 -0.195365 0.248591 0.105223 -0.295581 -0.0409813 0.138401 0.133354 0.0889642 -0.00517699 0.157318 0.111062 0.233649 0.017698 -0.21042 -0.0890712 -0.0433045 0.289987 -0.442923 -0.0745162 -0.0815676 -0.290945 -0.0449609 -0.127772\nw1 -0.056223 0.0962624 0.0881281 0.0780989 -0.120566 0.00224811 0.351273 0.187875 -0.089366 -0.0527282 0.0163225 -0.0244479 -0.0532257 -0.242335 0.00315191 -0.0455752 0.0807832 0.0305603 0.0726951 0.080458 -0.0360949 0.104028 0.181564 0.16289 0.186436 0.0686975 0.160185 0.0939657 -0.0703775 0.0524635 -0.00872955 0.10868 -0.00198156 -0.0869607 0.192797 -0.146597 -0.0333073 -0.202186 0.0233316 0.466238 0.282665 -0.129462 -0.0927926 -0.0794942 -0.0968451 -0.0996219 -0.12985 0.183565 0.277363 -0.0259409 0.0636742 0.0444679 -0.37215 0.0838058 0.130433 -0.0872919 -0.0522521 0.24291 -0.11213 -0.0692956 -0.15019 -0.132947 -0.0692985 -0.092559\nw1 0.075499 -0.0839894 -0.0127041 0.106028 0.0271634 0.131279 0.0492036 0.486716 -0.0354319 -0.00713446 -0.0600999 0.178043 0.0313131 -0.0185595 -0.0912191 -0.259527 -0.262271 -0.0866245 0.0998464 -0.168701 -0.0326424 -0.100535 -0.147641 0.0698939 0.122161 -0.0378994 0.137477 0.123707 -0.206219 0.0127175 0.471536 -0.173284 0.0649815 -0.364826 -0.228887 -0.0278642 0.110245 0.149777 0.0633547 0.00332001 0.0599537 0.0264017 0.133578 -0.37065 0.0448908 0.220732 0.0456917 -0.223219 0.0973812 0.51964 0.112017 -0.0533278 -0.135246 0.179341 -0.203419 -0.170801 0.0722481 0.0716898 0.0819142 -0.0923261 0.150662 0.142074 -0.0155039 0.195501\nw1 -0.0627479 0.0175043 0.0300136 0.0461847 0.00737198 -0.0643177 0.210509 0.725803 -0.27193 -0.0951868 -0.263568 0.297615 0.107261 0.0295576 0.0490208 -0.181442 0.12069 -0.100997 0.00474574 -0.443293 -0.0440555 0.273062 0.0154393 0.162143 -0.122205 0.0517808 0.00983631 -0.116043 -0.000385192 0.00629087 -0.123201 0.0514347 -0.0368426 0.261673 0.189266 0.0045177 -0.201946 -0.118288 0.115497 -0.0378015 -0.12339 -0.0426905 -0.12985 0.0803743 -0.0312562 -0.0824801 -0.220086 0.0176439 -0.0161805 0.0685961 0.0394687 -0.0885927 0.00344971 0.0969701 -0.0085224 -0.0178825 -0.164241 -0.0700663 -0.172738 -0.226532 -0.158702 0.340372 0.0606101 0.00666042\nw1 -0.030464 0.00529457 -0.0746059 0.136794 0.0895463 0.0866826 -0.286447 0.134956 -0.187816 -0.142498 -0.136939 -0.418498 0.214567 -0.264539 0.14421 0.0507351 0.0295928 0.266922 0.0158742 -0.194564 0.179587 0.0835795 0.146219 -0.19215 0.129485 -0.0516279 0.150294 -0.044955 0.244002 0.0593697 0.421683 0.0132008 -0.0496956 0.346397 0.0937419 -0.428143 0.110903 -0.12677 0.192371 -0.10194 0.224166 0.103481 0.28887 -0.152851 0.0172136 0.0865082 0.408862 0.0286439 0.0654856 0.263981 -0.0590969 -0.0794615 0.0795904 -0.00395822 -0.0267476 0.0270404 -0.154114 -0.187809 -0.0628698 0.200582 -0.20796 -0.566819 -0.0708238 -0.12147\nw1 0.307608 0.0604149 -0.0233124 0.0452766 0.0781072 -0.00720956 -0.014087 0.265261 0.273451 -0.223132 -0.211947 -0.00504825 -0.26827 0.047246 -0.010075 -0.209328 0.0189036 8.23579e-05 -0.103778 -0.125581 0.364014 0.223041 0.0714695 0.0558365 -0.206069 -0.256093 -0.193564 -0.107562 9.56174e-05 0.00682698 0.193314 0.205849 0.0502982 0.11875 -0.236142 -0.205506 -0.122105 0.280845 -0.134541 -0.262538 -0.119487 -0.0823347 0.104133 -0.126201 -0.00291478 -0.173012 0.187738 0.0570054 -0.0352918 0.037459 -0.159354 0.32323 0.0789118 0.010746 -0.108774 0.111552 -0.0871266 -0.0492178 -0.0769029 0.140792 -0.102706 0.010865 -0.0861898 0.142727\nw1 -0.0612968 0.0924346 -0.154206 0.0579591 -0.0366629 -0.13309 -0.0812652 -0.132919 0.102532 0.13548 -0.0530102 -0.17495 0.00762999 -0.0253997 0.0179637 0.10063 0.0714235 0.00374022 -0.0305854 -0.102345 -0.0439596 0.0545906 -0.0554014 0.0530517 0.207546 -0.0582771 0.180774 -0.103369 0.0271697 0.0691604 -0.147066 0.0334959 0.110916 0.0671815 0.181817 0.0675277 -0.00911574 0.0257505 0.0144033 -0.188532 0.0722855 -0.104497 -0.289743 0.0616925 0.112101 -0.0603216 -0.0182621 0.082534 -0.18942 -0.0616961 -0.0740638 0.0742489 0.084952 -0.14268 -0.0733599 0.0282709 -0.000565062 0.00433154 0.0414215 0.0173494 -0.0339957 -0.0632056 0.023703 -0.0651667\nw1 0.0615623 0.266993 0.241153 -0.0776931 -0.00683298 -0.0272705 0.0441044 -0.0961796 0.158587 0.156819 -0.277744 0.17594 0.12427 0.070656 0.000846975 0.172497 -0.0760818 -0.00729056 0.104985 -0.0369648 -0.121491 -0.226991 -0.0245318 -0.114437 -0.1982 0.176256 -0.196382 -0.0293745 -0.142859 0.0376812 -0.0911818 -0.0872755 -0.0257755 -0.0857919 0.029382 0.0932619 -2.40051e-05 0.0817473 -0.24039 0.00187614 -0.0436519 0.0672886 0.0418737 0.0973694 -0.0283434 -0.186753 -0.182158 0.112428 0.000511173 -0.174197 0.0330214 0.0662989 -0.0344444 0.0550924 -0.0501721 -0.168119 0.168356 0.0259158 -0.0308338 -0.0865925 -0.0261304 -0.0666461 0.0167891 -0.0654667\nw1 -0.0336718 -0.0923899 -0.0269137 -0.0661678 0.191829 0.0663377 0.0799851 -0.135962 0.0730103 0.0295842 0.183711 -0.0264892 0.0681661 -0.0613374 -0.0198723 0.0392103 0.0834401 -0.0500144 0.0646094 0.0996278 -0.135805 -0.141484 -0.0536643 -0.00235662 0.051315 0.065995 -0.0748329 0.0175615 -0.0265627 -0.0724266 0.0382715 -0.0383426 0.0817151 -0.125595 -0.181507 0.237099 0.00498046 0.183935 -0.0584014 0.262981 -0.0568667 0.0651182 -0.00780822 -0.0721022 -0.0345166 -0.0625676 -0.0919862 -0.0833519 -0.118844 0.0517564 -0.00758483 -0.266244 -0.0516685 0.201192 0.0457918 -0.0431131 -0.0419122 0.0172117 -0.176162 -0.0486012 -0.0157755 0.0108638 0.0720168 0.0207267\nw1 -0.151909 -0.0360788 -0.0361209 -0.0718215 -0.145566 0.0142883 -0.0372744 0.234896 -0.0826756 -0.0856044 0.123042 0.0449069 -0.262027 0.0746623 0.122095 0.0992191 -0.1188 -0.0152543 0.0540763 -0.0243574 -0.122802 0.0227406 0.0155628 -0.00077526 -0.0432539 -0.0217299 0.0160339 -0.0987337 0.131027 -0.0419128 -0.0458332 0.0575932 -0.0674311 0.0970793 0.033822 0.0768982 -0.00241683 -0.0992509 0.0521859 0.300716 -0.173973 -0.00535539 0.118125 0.0434428 -0.0399931 -0.0907788 -0.041912 -0.195165 -0.0320743 -0.0124216 0.0773493 -0.0441724 -0.0703405 0.1842 0.0561047 -0.0875853 -0.17653 0.0926947 -0.21056 0.0165222 0.0745779 0.0329337 0.0576878 0.0356279\nw1 -0.0168776 0.373176 -0.0578195 -0.0667882 -0.103977 0.0755836 -0.0687011 -0.00238536 -0.0534139 0.0362813 -0.189217 -0.0508566 -0.00142162 -0.152261 -0.0632155 0.0982399 0.0122279 0.0104088 -0.0185021 0.194809 -0.162722 0.0558819 0.0837156 -0.0650764 0.0595466 -0.160736 -0.101384 -0.106253 0.107816 -0.00249946 -0.0736074 -0.184918 0.038052 0.0359182 0.159219 0.00101275 0.0328849 -0.057677 0.257181 0.105409 -0.154717 -0.0631485 -0.0702177 0.128793 0.271353 0.0933691 -0.042766 0.145605 -0.0337673 -0.0813216 0.0520373 0.000473297 0.0572586 -0.0254032 0.0138575 0.181259 -0.05961 -0.00659553 0.115082 -0.0949688 0.0430652 0.17937 -0.216056 0.102978\nw1 -0.0816699 0.35825 -0.0372706 0.16156 0.147122 -0.167342 -0.231906 -0.195843 0.168078 -0.24721 -0.197506 0.118502 -0.0940348 -0.0346248 0.164744 -0.134178 -0.0529847 -0.163141 -0.00238515 -0.150711 0.0563152 0.09601 -0.0108993 0.0305495 0.0931072 0.0637921 -0.167763 0.166249 -0.0882786 -0.0516461 0.157334 -0.231833 -0.00273245 -0.0359269 -0.273705 -0.127001 0.0565595 0.131654 -0.223693 -0.184645 0.0450558 0.0196671 -0.198581 -0.0884202 -0.00851818 -0.0107405 0.0598842 0.16444 0.185616 -0.0417199 -0.127781 0.014588 0.0992866 -0.105766 -0.0305747 0.147659 0.166352 0.190447 0.225948 0.0425829 0.0399999 0.0828773 0.00135976 0.085767\nw1 -0.220819 0.0746514 0.153824 -0.0638614 -0.0611606 0.0102013 -0.155621 0.0257335 0.100835 -0.230995 -0.0898786 0.0788513 -0.0914714 0.098129 -0.0904253 -0.159702 -0.129365 0.171373 -0.0236589 -0.00371199 0.0044788 0.0248927 0.0780041 0.0563455 -0.0311187 0.112237 -0.0129042 -0.0393456 0.184032 -0.111755 0.213278 -0.017306 0.0251818 -0.069505 0.134579 0.063416 -0.0908216 -0.17098 0.074763 0.158581 -0.0463225 0.126908 0.0866265 0.145204 -0.00357978 -0.0750747 0.18405 0.321817 -0.0286326 0.294269 0.00685986 0.0439989 0.00346272 -0.0982615 -0.0345138 -0.0538838 0.0563404 0.0165135 -0.128578 0.0315587 -0.133144 -0.196117 -0.0130191 -0.000535184\nw1 -0.0836665 -0.0261646 0.0920918 0.0818238 0.056161 0.106238 -0.0922037 -0.104533 -0.159756 0.0366875 -0.0712059 -0.122468 0.123167 0.0754965 -0.0475928 -0.0108732 0.156733 -0.0140958 0.170268 0.0404651 0.094222 0.214181 0.0587345 -0.0254783 0.127404 -0.0568428 0.389885 0.113188 -0.081711 0.0355509 -0.0608109 -0.141837 -0.0439657 0.00785429 0.0344617 0.0464304 0.0252627 0.139727 0.14575 -0.0356726 0.172627 -0.215872 0.177963 0.209854 -0.0580184 -0.073612 0.0579489 0.036534 -0.0270604 0.0664575 -0.00344563 -0.105526 -0.0237466 0.00322836 -0.131101 0.00490152 0.0265031 -0.236388 -0.150348 0.0636431 0.0695016 -0.162536 0.117718 0.0427017\nw1 0.148841 -0.247324 -0.0492979 -0.131415 -0.169117 -0.126813 -0.0178813 -0.131039 -0.16262 -0.00275837 -0.0372872 0.0987162 0.0265271 0.0548674 0.320095 0.237466 -0.0109741 0.0862554 0.058117 -0.368989 0.0204729 0.0137719 0.140793 0.0181329 -0.140853 0.0492775 -0.112418 0.264011 0.0171465 -0.268836 -0.0250502 0.00940453 -0.0131235 -0.0757594 0.217772 0.102017 -0.0508353 0.129532 -0.0811334 -0.104574 -0.0153785 -0.00949105 -0.407548 0.211756 0.188619 0.0270149 -0.0181436 0.0295863 0.244172 -0.0826605 0.0744973 -0.0754346 0.255674 0.0579012 0.0729137 -0.242342 0.121001 0.0469975 0.0622598 0.0263666 0.00766058 0.00152639 0.0702366 -0.094101\nw1 -0.0419339 -0.166072 -0.0169005 0.222904 0.0919356 -0.050859 -0.0581427 0.0752624 0.0528502 0.0706548 -0.196803 0.0794227 0.053084 -0.102178 -0.0915293 0.00163878 -0.180854 0.003741 -0.00657931 -0.00878911 -0.144188 -0.0476138 -0.0119686 0.00664542 0.0536582 -0.098807 0.154531 0.0493844 -0.0543047 -0.214236 0.159601 0.0303189 0.102641 0.0457869 0.0347139 0.0269325 0.113297 0.0180713 -0.301059 -0.0635017 0.332791 -0.0351378 0.205111 -0.067349 0.0761085 -0.0466242 -0.0817846 0.0225034 -0.153531 0.00138742 0.174298 -0.126093 -0.0201288 -0.0313901 -0.0451077 -0.109904 0.0341766 0.148436 0.107715 0.0595092 -0.0447012 0.0611184 -0.170854 -0.056522\nw1 -0.0726544 0.0947104 -0.0777486 0.019358 -0.0499221 -0.156648 0.0342678 -0.0155444 -0.0524212 0.0223524 -0.000557449 0.0593043 0.00127079 -0.000386232 0.047986 0.0821915 -0.0303624 0.0107057 -0.0177299 -0.0991291 0.0459264 0.0297735 0.0532486 0.00587606 -0.112896 0.0286625 -0.0586534 0.0444875 0.0236679 -0.147421 -0.0267503 -0.00906641 -0.0378659 0.0554366 -0.0152797 0.0489993 -0.111023 0.0221531 -0.0893352 -0.0847963 0.0796589 0.026055 0.00285002 0.0525103 0.0674514 0.050861 -0.108195 -0.018168 0.0397487 0.0766504 0.0906467 -0.0123806 0.0455998 -0.0247804 -0.00778993 -0.254771 -0.00907941 0.0183845 0.0637458 0.0211925 -0.037305 0.00989107 -0.0436439 0.00719158\nw1 -0.193294 -0.0941478 -0.0528064 -0.101314 -0.281368 -0.0236286 0.181078 0.0461927 -0.290497 0.337212 -0.176827 0.0668672 0.0194538 0.0971253 0.114427 0.426434 0.118189 0.2321 0.0807624 -0.29827 -0.121914 0.289021 0.120322 0.0226583 -0.0600613 0.0616939 0.039427 -0.100425 -0.0109427 0.00756966 -0.119672 0.104005 0.0125277 0.162717 0.212836 0.0574338 -0.157005 -0.203778 0.0591137 -0.120181 0.150967 0.0318407 -0.506229 0.319046 0.0868692 0.258041 -0.26496 0.122595 0.282326 -0.0562709 0.171573 -0.00673588 0.208753 -0.217497 -0.599135 -0.623575 0.128082 -0.0909415 0.141944 0.00107295 -0.00745375 0.0307411 -0.0353276 -0.117979\nw1 0.00176585 -0.133104 -0.162521 0.0235724 0.188389 -0.240545 0.0938332 -0.253729 0.668601 -0.0529735 0.0715293 0.102243 -0.0748204 0.00499764 -0.195822 0.0620833 -0.183253 0.015931 0.233226 -0.0128461 -0.431138 0.0418769 -0.138529 0.111868 0.000919842 0.154867 0.0898731 0.525977 -0.131925 -0.0458632 -0.0493918 0.623851 0.0785302 -0.0107397 0.289424 -0.0817677 -0.00593725 0.401351 -0.574695 0.39039 0.046748 -0.0613299 -0.473845 0.0899901 0.0663606 -0.049546 0.0346546 -0.0260889 -0.0869135 -0.115882 -0.0255191 0.0934219 -0.281363 0.0265998 -0.659425 -0.445588 -0.137647 0.0813589 0.0536075 0.199415 -0.0783531 -0.140668 -0.17761 -0.43029\nw1 -0.502753 -0.108022 -0.616325 0.190071 -0.0394991 -0.0763074 0.215402 0.0460867 -0.0362696 0.570274 -0.10918 0.157387 -0.0710475 -0.343578 0.377307 -0.109261 -0.653236 0.466612 -0.136534 -0.035421 0.0977989 -0.419461 0.584438 -0.124355 0.0786817 0.36099 -0.022354 0.10331 0.0731486 -0.104606 0.252853 0.678684 0.260767 0.24864 -0.0804857 0.104886 0.238173 0.158021 -0.0981083 -0.236736 -0.183667 -0.254596 -0.0658274 0.150592 0.195909 0.011549 -0.472789 0.059218 0.521004 -0.132072 0.52293 -0.293779 0.146595 -0.607542 0.760864 0.538662 0.251597 0.177269 0.0744059 0.0946026 -0.57531 0.569094 -0.44494 0.0539832\nw2 0.746284 -0.239996 0.537986\nw2 -0.270535 -0.992275 0.167401\nw2 -0.315326 -0.575856 1.36557\nw2 0.80658 -0.652745 -1.02912\nw2 -0.687247 0.0473391 -0.75112\nw2 -0.720895 -0.828561 -1.7182\nw2 0.0563614 0.202279 0.719901\nw2 -0.729038 0.0731891 -0.720208\nw2 -0.336928 0.466465 -0.807272\nw2 0.254573 -0.661801 0.190312\nw2 -0.288276 -0.310154 -0.720518\nw2 -0.490963 -0.163461 1.00474\nw2 -0.443171 -0.102352 -1.13972\nw2 0.0385559 -0.539819 -1.06799\nw2 -0.518472 -0.625088 0.14454\nw2 -0.393836 0.60711 -0.965392\nw2 -0.674761 -0.0257931 0.48898\nw2 1.06081 -0.152201 -0.36221\nw2 -0.106537 0.660817 0.309154\nw2 -0.825784 0.0186225 -0.613168\nw2 0.313433 -1.10437 -0.725033\nw2 0.532986 0.411117 -0.506489\nw2 -0.134543 -1.01001 0.243026\nw2 0.42922 -0.600018 0.229875\nw2 0.608905 -1.18313 1.04928\nw2 -0.366605 -1.0462 -1.29741\nw2 -0.483106 0.176453 -1.33712\nw2 -0.380069 -0.0212061 -0.752996\nw2 0.175407 1.09638 0.804549\nw2 -1.0804 0.288385 -0.378658\nw2 0.951232 0.0554135 -0.0236215\nw2 -0.307971 -0.640575 0.675065\nw2 -1.70471 -0.124723 -0.412462\nw2 -1.26197 -0.75979 2.134\nw2 -2.08595 -0.235033 1.23606\nw2 -0.0303374 0.506279 -1.38328\nw2 -0.725728 0.304308 -0.520388\nw2 0.409974 -0.65028 -0.916925\nw2 -0.437992 -1.05045 -0.513901\nw2 0.681911 -0.0101321 -0.657029\nw2 -0.136305 0.891159 -0.103071\nw2 -0.381529 -0.717132 0.556126\nw2 -0.980581 1.00124 0.41698\nw2 0.619562 -0.00678687 -0.454887\nw2 0.0888274 -0.630814 0.711053\nw2 0.0410387 -0.431942 -1.12389\nw2 0.791118 -0.462053 2.05277\nw2 -0.643067 0.852896 -0.964561\nw2 0.184998 -1.47419 -0.521688\nw2 -0.710906 0.430201 0.493204\nw2 0.250145 -0.468885 0.200595\nw2 -0.35783 -0.59614 -1.39493\nw2 0.295126 0.392802 0.499372\nw2 0.528892 0.0550373 -1.09692\nw2 0.654425 0.655227 -1.44649\nw2 -0.86343 -0.289127 -0.620457\nw2 0.614527 -0.223761 -0.538311\nw2 0.443012 0.304084 -0.555465\nw2 0.529178 0.355668 -0.711581\nw2 -1.32899 0.0919229 0.00456442\nw2 -0.54662 0.237171 -0.00261511\nw2 -0.496148 -0.209219 1.14187\nw2 -0.00960801 -1.22798 -0.412858\nw2 0.715667 1.19716 -0.371535\n";
        // hit model ugritone: end
        static double[] mu = HM_MU, sd = HM_SD, b = HM_B, th = HM_T, b1 = HM_B1, b2 = HM_B2;
        static double[][] w = HM_W, w1 = HM_W1, w2 = HM_W2;

        static boolean ready() {
            String f = System.getProperty("dm.hitModelFile");
            if (f == null && "ugritone".equals(System.getProperty("dm.hitModelName")) && UGRITONE.length() > 0) {
                try {
                    loadText(UGRITONE);
                } catch (Exception ex) {
                    System.out.println("Ugritone hit model not read: " + ex.getMessage());
                }
            }
            if (f != null) {
                try {
                    load(f);
                } catch (Exception ex) {
                    System.out.println("Hit model file not read: " + ex.getMessage());
                }
            }
            return mu != null && (w1 != null && w2 != null && b1 != null && b2 != null || w != null && b != null);
        }

        /** The three drums' likelihoods (kick, snare, tom) for one onset's features: a small network, or a linear model. */
        static double[] prob(double[] f) {
            double[] z = new double[f.length];
            for (int j = 0; j < z.length && j < mu.length; j++) z[j] = (f[j] - mu[j]) / sd[j];
            double[] out = new double[3];
            if (w1 != null) {
                double[] h = new double[b1.length];
                for (int u = 0; u < h.length; u++) {
                    double a = b1[u];
                    for (int j = 0; j < z.length && j < w1.length; j++) a += w1[j][u] * z[j];
                    h[u] = Math.max(0, a);
                }
                for (int k = 0; k < 3; k++) {
                    double a = b2[k];
                    for (int u = 0; u < h.length; u++) a += w2[u][k] * h[u];
                    out[k] = 1 / (1 + Math.exp(-a));
                }
            } else {
                for (int k = 0; k < 3; k++) {
                    double a = b[k];
                    for (int j = 0; j < z.length && j < w.length; j++) a += w[j][k] * z[j];
                    out[k] = 1 / (1 + Math.exp(-a));
                }
            }
            return out;
        }

        static void load(String path) throws Exception {
            loadText(new String(java.nio.file.Files.readAllBytes(new File(path).toPath()), "UTF-8"));
        }

        /** A model in hit-model.txt's form (lines of a name and its numbers: mu, sd, t, b1, b2, w1, w2). */
        static void loadText(String text) {
            List<double[]> rows = new ArrayList<double[]>(), r1 = new ArrayList<double[]>(), r2 = new ArrayList<double[]>();
            b1 = b2 = null;
            for (String line : text.split("\n")) {
                String[] p = line.trim().split("\\s+");
                if (p.length < 2 || p[0].startsWith("#")) continue;
                double[] v = new double[p.length - 1];
                for (int i = 1; i < p.length; i++) v[i - 1] = Double.parseDouble(p[i]);
                if (p[0].equals("mu")) mu = v;
                else if (p[0].equals("sd")) sd = v;
                else if (p[0].equals("b")) b = v;
                else if (p[0].equals("t")) th = v;
                else if (p[0].equals("b1")) b1 = v;
                else if (p[0].equals("b2")) b2 = v;
                else if (p[0].equals("w1")) r1.add(v);
                else if (p[0].equals("w2")) r2.add(v);
                else rows.add(v);
            }
            w = rows.isEmpty() ? null : rows.toArray(new double[0][]);
            w1 = r1.isEmpty() ? null : r1.toArray(new double[0][]);
            w2 = r2.isEmpty() ? null : r2.toArray(new double[0][]);
        }

        /** Log energy in each band of an N-sample window starting at t (seconds). */
        static double[] bands(double[] x, double sr, double t) {
            double[] re = new double[N], im = new double[N];
            int a = (int) Math.round(t * sr);
            for (int i = 0; i < N; i++) {
                int k = a + i;
                double win = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / N);
                re[i] = k >= 0 && k < x.length ? x[k] * win : 0;
            }
            fft(re, im);
            double[] out = new double[B];
            for (int j = 0; j < B; j++) {
                int lo = Math.max(1, (int) Math.round(EDGES[j] * N / sr)), hi = Math.min(N / 2, (int) Math.round(EDGES[j + 1] * N / sr));
                double e = 0;
                for (int k = lo; k < hi; k++) e += re[k] * re[k] + im[k] * im[k];
                out[j] = Math.log(e / Math.max(1, hi - lo) + 1e-10);
            }
            return out;
        }

        /** The onsets as frames, one within 3 frames (the first kept), in time order. */
        static List<Integer> unique(List<Integer> cand) {
            List<Integer> s = new ArrayList<Integer>(cand);
            Collections.sort(s);
            List<Integer> out = new ArrayList<Integer>();
            for (Integer c : s) if (out.isEmpty() || c.intValue() - out.get(out.size() - 1).intValue() > 3) out.add(c);
            return out;
        }

        /**
         * Each onset's features: the bands' level at the peak (against the song's onsets' mean), their rise
         * from before, and how much is left 50, 100 and 200 ms on; then the rules' kick, snare, tom there,
         * bass, guitar, and the onset's whole level against the song's.
         */
        static double[][] feats(List<Hit> hits, List<Integer> frames, double[] x, double sr, boolean bass, boolean guitar) {
            int n = frames.size();
            double[][] out = new double[n][F];
            double[][] peak = new double[n][];
            double[] whole = new double[n];
            double[] mean = new double[B];
            double wm = 0;
            for (int i = 0; i < n; i++) {
                double t = frames.get(i).intValue() * (double) HOP / sr - N / 2.0 / sr;
                double[][] e = new double[OFFS.length][];
                for (int o = 0; o < OFFS.length; o++) e[o] = bands(x, sr, t + OFFS[o]);
                double[] pk = new double[B];
                for (int j = 0; j < B; j++) pk[j] = Math.max(e[1][j], e[2][j]);
                peak[i] = pk;
                double sum = 0;
                for (int j = 0; j < B; j++) {
                    out[i][B + j] = pk[j] - e[0][j];
                    out[i][2 * B + j] = e[3][j] - pk[j];
                    out[i][3 * B + j] = e[4][j] - pk[j];
                    out[i][4 * B + j] = e[5][j] - pk[j];
                    mean[j] += pk[j] / n;
                    sum += Math.exp(pk[j]);
                }
                whole[i] = Math.log(sum + 1e-10);
                wm += whole[i] / n;
                double time = frames.get(i).intValue() * (double) HOP / sr;
                for (Hit h : hits) {
                    if (Math.abs(h.time - time) > 0.035) continue;
                    if (h.note == KICK_NOTE) out[i][5 * B] = 1;
                    else if (h.note == SNARE_NOTE) out[i][5 * B + 1] = 1;
                    else if (isTomNote(h.note)) out[i][5 * B + 2] = 1;
                }
                out[i][5 * B + 3] = bass ? 1 : 0;
                out[i][5 * B + 4] = guitar ? 1 : 0;
            }
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < B; j++) out[i][j] = peak[i][j] - mean[j];
                out[i][5 * B + 5] = whole[i] - wm;
            }
            return out;
        }

        static void dump(List<Hit> hits, List<Integer> cand, double[] x, double sr, boolean bass, boolean guitar, String file) {
            List<Integer> frames = unique(cand);
            double[][] f = feats(hits, frames, x, sr, bass, guitar);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < frames.size(); i++) {
                sb.append(String.format(java.util.Locale.ROOT, "%.4f", frames.get(i).intValue() * (double) HOP / sr));
                for (double v : f[i]) sb.append(' ').append(String.format(java.util.Locale.ROOT, "%.4f", v));
                sb.append('\n');
            }
            try {
                java.nio.file.Files.write(new File(file).toPath(), sb.toString().getBytes("UTF-8"));
            } catch (Exception ex) {
                System.out.println("Hit features not written: " + ex.getMessage());
            }
        }

        /** The kicks, snares and toms as the model has them (a tom's note as the rules had it there, else by its band). */
        static void apply(List<Hit> hits, List<Integer> cand, double[] x, double sr, boolean bass, boolean guitar, double[] floor, double[] mid, double[] rack, boolean accSure) {
            List<Integer> frames = unique(cand);
            double[][] f = feats(hits, frames, x, sr, bass, guitar);
            List<Hit> keep = new ArrayList<Hit>();
            List<Hit> old = new ArrayList<Hit>();
            for (Hit h : hits) {
                if (h.note == KICK_NOTE || h.note == SNARE_NOTE || isTomNote(h.note)) old.add(h);
                else keep.add(h);
            }
            int[] notes = {KICK_NOTE, SNARE_NOTE, -1};
            int added = 0, dropped = 0;
            for (int i = 0; i < frames.size(); i++) {
                int t = frames.get(i).intValue();
                double time = t * (double) HOP / sr;
                double[] pr = prob(f[i]);
                for (int k = 0; k < 3; k++) {
                    double p = pr[k];
                    if (p < (th != null && k < th.length ? th[k] : 0.5)) continue;
                    int note = notes[k];
                    Hit was = null;
                    for (Hit h : old) if (Math.abs(h.time - time) <= 0.035 && (k == 2 ? isTomNote(h.note) : h.note == note)) was = h;
                    if (k == 2) {
                        if (was != null) note = was.note;
                        else {
                            int c = Math.max(0, Math.min(floor.length - 1, t));
                            note = floor[c] >= mid[c] && floor[c] >= rack[c] ? FLOOR_TOM : mid[c] >= rack[c] ? MID_TOM : RACK_TOM;
                        }
                    }
                    // Over a bass or guitar a tom of its own only when it is sure (ACC_TOM_CUT): real songs' guitars
                    // (SogniMusic's 50 styles) gave it 3x the toms of the same styles' drums alone (1093 against
                    // ~400); at 0.95, 469. On the bench's mixes toms then P 97-98, R 58-70 (the rules: R 24-36).
                    if (k == 2 && was == null && accSure && p < ACC_TOM_CUT) continue;
                    // Kicks and snares the rules did not find, over a band: as sure as ACC_HIT_CUT (0: no cut).
                    if (k < 2 && was == null && accSure && p < ACC_HIT_CUT) continue;
                    if (was == null) added++;
                    keep.add(was != null ? was : new Hit(time, note, p));
                }
            }
            for (Hit h : old) {
                boolean kept = false;
                for (Hit k : keep) kept |= k == h;
                if (!kept) dropped++;
            }
            hits.clear();
            hits.addAll(keep);
            note("Kicks, snares and toms by the hit model: " + added + " added, " + dropped + " left out");
        }
    }

    static final class CymModel {
        static final int N = 1024, F = 29, X = 63;
        /**
         * The kit profile's inputs after the X above: the hit's likeness (cymNear) to the kit's learned closed
         * hat, open hat, ride and crash (0 for one not learned), and 1 when the kit profile has any (0 without
         * a profile). A model of X inputs reads only the first X; one learned with these, all XK.
         */
        static final int XK = X + 5;
        static final double CM_CONTEXT = 2.0, CM_MARGIN = 0.4;
        static final double[] OFFS = {-0.03, 0, 0.03, 0.07, 0.12, 0.2};
        static final int[] NOTE = {CLOSED_HAT, OPEN_HAT, RIDE_NOTE, CRASH_NOTE};
        // The model's numbers: each feature's mean and spread, the kinds' biases and weights. Made by
        // tools/cymbal_model.py export (it rewrites the lines between these two markers).
        // cymbal model: begin
    static final double[] CM_MU = {-0.766593, -0.494744, -0.257299, -0.126502, 0.0361741, 0.368563, 0.369625, 0.365061, 0.460993, 0.327006, 0.151263, -0.433547, -0.483862, -1.32611, -1.53187, -1.44184, 3.99012, 0.402301, 0.39955, 3.04377, 3.18396, 7.38881, 0.622201, -1.94999, 4.39423, 0.0920345, 0.121753, 0.448062, 0.688562, -0.619487, -0.364505, -0.135466, -0.0131364, 0.146017, 0.439593, 0.433674, 0.398916, 0.432982, 0.232354, -0.0802518, -0.87069, -0.589865, -1.46887, -1.62227, -1.45476, 3.97417, 0.387638, 0.383592, 3.09894, 3.31228, 7.27955, 0.639666, -1.96623, 4.38141, 0.0919916, 0.125406, 0.454849, 0.695513, 2.82929, 0.611213, 0.102924, 0.276411, 0.00945092};
    static final double[] CM_SD = {1.572, 1.35371, 1.17047, 1.10204, 0.885407, 0.778475, 0.737208, 0.716657, 0.904853, 1.07242, 1.43256, 2.23798, 1.16885, 1.3747, 1.97048, 2.61983, 3.1128, 0.0995007, 0.097468, 0.619287, 1.02699, 1.20556, 0.373427, 0.894116, 1.3481, 0.289075, 0.327001, 0.497295, 0.463081, 0.799162, 0.652037, 0.567342, 0.549739, 0.412622, 0.385739, 0.35966, 0.318511, 0.475898, 0.586055, 0.867183, 1.51868, 0.496569, 0.725868, 1.13681, 1.57936, 1.79347, 0.0490158, 0.0566433, 0.331779, 0.512878, 0.679153, 0.168771, 0.360924, 0.971511, 0.102844, 0.12324, 0.269219, 0.260249, 0.372084, 0.487475, 0.30386, 0.447223, 0.0967553};
    static final double[] CM_B = {2.83493, -0.231314, -0.364616, -2.239};
    static final double[][] CM_W = {{0.0573536, -0.0202513, -0.139127, 0.102025}, {-0.0713917, -0.0778611, 0.13607, 0.0131827}, {-0.0489904, -0.0478216, 0.0341802, 0.0626318}, {-0.0459879, 0.0158162, 0.0240438, 0.00612784}, {0.0290419, 0.0233282, -0.119929, 0.0675591}, {-0.0238392, 0.0197914, -0.00986389, 0.0139117}, {0.0475069, -0.128433, -0.00841655, 0.0893426}, {-0.0163812, 0.0067733, -0.0539905, 0.0635984}, {0.0488522, 0.0215632, -0.0726275, 0.00221208}, {0.0224244, 0.0075476, 0.0103003, -0.0402724}, {-0.230586, 0.0753161, 0.289744, -0.134474}, {0.154667, 0.0420223, -0.104396, -0.0922935}, {0.144259, -0.103987, -0.108481, 0.0682096}, {-0.233376, 0.127914, -0.0877927, 0.193255}, {-0.398291, 0.277206, -0.0234756, 0.144561}, {-0.547917, 0.332075, 0.153085, 0.0627572}, {0.117578, 0.0961079, -0.131475, -0.0822113}, {0.108068, -0.079369, 0.0173935, -0.0460926}, {0.344911, 0.0226478, -0.223979, -0.14358}, {0.00189026, 0.000781825, -0.0383701, 0.035698}, {-0.00620841, 0.028303, -0.0607903, 0.0386956}, {0.0959013, -0.106408, 0.0602446, -0.0497382}, {-0.0202514, 0.0366468, -0.172649, 0.156254}, {-0.0762739, 0.0754534, -0.052038, 0.0528584}, {-0.171776, 0.399333, -0.128185, -0.0993721}, {-0.227646, 0.197756, 0.0473471, -0.0174574}, {0.111274, 0.0362655, -0.116016, -0.0315239}, {0.3449, -0.374218, -0.0392679, 0.068586}, {0.321748, -0.299859, -0.03118, 0.00929059}, {-0.000722341, 0.14034, -0.155496, 0.0158782}, {-0.201383, -0.0655553, 0.305876, -0.0389374}, {-0.139365, -0.00654612, 0.14939, -0.00347865}, {0.0618321, -0.0487191, 0.0183633, -0.0314764}, {0.175742, 0.0899631, -0.28613, 0.020425}, {-0.060735, -0.0205423, 0.13849, -0.0572122}, {0.0356254, 0.0140955, -0.0505842, 0.000863293}, {-0.109947, 0.100829, 0.0021383, 0.00697992}, {0.311122, -0.0939451, -0.188236, -0.0289403}, {-0.0809343, -0.00540576, 0.0532349, 0.0331052}, {-0.267333, -0.039793, 0.301485, 0.00564074}, {0.185213, -0.0150858, -0.191569, 0.0214422}, {0.237966, 0.0433131, -0.289043, 0.00776361}, {-0.128209, -0.0730474, 0.165183, 0.0360736}, {0.00789303, -0.0883363, 0.0772508, 0.00319247}, {-0.0414769, -0.124678, 0.152638, 0.0135173}, {0.57235, 0.0918733, -0.60153, -0.0626933}, {-0.0480109, -0.0628669, 0.125615, -0.0147369}, {0.277005, 0.150349, -0.431788, 0.004434}, {-0.0307052, -0.0469113, 0.0626379, 0.0149786}, {0.0205863, -0.0676866, 0.0436176, 0.00348265}, {0.154648, -0.0292505, -0.0772697, -0.0481282}, {0.153174, 0.0314227, -0.186145, 0.00154778}, {-0.057055, 0.0463165, -0.0197554, 0.0304939}, {0.168832, -0.092819, -0.166594, 0.0905809}, {-0.198298, -0.10784, 0.309494, -0.00335578}, {0.0194101, 0.227124, -0.199896, -0.0466384}, {0.156431, 0.0659036, -0.193685, -0.0286494}, {-0.0176089, 0.0638011, -0.0183255, -0.0278668}, {0.136938, -0.0582302, -0.0333164, -0.0453909}, {0.121329, -0.0124541, -0.116688, 0.00781286}, {-0.071506, 0.292088, -0.246755, 0.0261729}, {-0.0663272, -0.184775, 0.301312, -0.0502098}, {-0.0801393, -0.00049115, -0.0298904, 0.110521}};
        // cymbal model: end
        // The song-aware model (vst_render's leave-one-out bench, 24.11., 62 kits) and its gate: each cymbal's
        // inputs with the song's look after them (songLook), and when the gate says the song suits it. Made by
        // vst_render.py --leave_one_out (model-62-kits-song-aware.txt, gate-62-kits-song-aware.txt).
        // song-aware cymbal model: begin
    static final double[] SA_MU = {-0.717819, -0.438414, -0.220249, -0.0192248, 0.110616, 0.448389, 0.395293, 0.36218, 0.396621, 0.249687, 0.0112747, -0.578355, -0.596475, -1.3538, -1.60381, -1.52571, 3.99469, 0.384541, 0.385224, 3.12218, 3.27864, 7.35306, 0.62298, -1.9198, 4.39393, 0.0920843, 0.12384, 0.415456, 0.65835, -0.566991, -0.307197, -0.0883176, 0.100517, 0.229111, 0.523016, 0.464827, 0.403812, 0.37505, 0.157805, -0.231605, -1.06003, -0.697025, -1.48915, -1.65956, -1.49053, 3.93804, 0.369017, 0.369038, 3.18596, 3.39649, 7.23215, 0.644484, -1.94694, 4.3678, 0.0926528, 0.130891, 0.43405, 0.676842, 2.81727, 0.646572, 0.103463, 0.23723, 0.012734, -0.11719, -0.220937, -0.390669, -0.252659, 0.903226, -0.564895, -0.305343, -0.0854783, 0.103231, 0.231555, 0.524498, 0.466455, 0.404127, 0.373077, 0.154237, -0.237898, -1.06357, -0.691887, -1.48571, -1.6609, -1.50521, 3.95551, 0.369355, 0.369584, 3.18764, 3.39245, 7.24108, 0.647181, -1.93272, 4.37237, 0.0919887, 0.129978, 0.432476, 0.674274, -0.564763, -0.30566, -0.0866954, 0.101731, 0.230311, 0.523997, 0.465494, 0.403655, 0.373719, 0.155462, -0.235508, -1.06174, -0.695539, -1.48738, -1.65894, -1.49284, 3.9502, 0.369032, 0.369153, 3.18628, 3.39552, 7.23318, 0.645112, -1.94706, 4.36949, 0.0929656, 0.131145, 0.43426, 0.676858, 2.81493, 0.651293, 0.0922191, 0.242767, 0.0137211, -0.139308, -0.238582, -0.414898, -0.226781, 0.903226, 0.651293, 0.0922191, 0.242767, 0.0137211, 4.93898};
    static final double[] SA_SD = {1.58941, 1.38139, 1.25044, 1.14586, 1.00528, 0.828629, 0.778795, 0.762557, 0.985702, 1.21027, 1.63242, 2.56481, 1.34759, 1.35003, 1.91758, 2.58178, 3.11955, 0.115775, 0.11501, 0.73477, 1.25647, 1.21224, 0.368525, 0.90376, 1.44981, 0.289145, 0.329399, 0.4928, 0.474263, 0.817526, 0.698665, 0.649568, 0.617809, 0.558348, 0.453758, 0.391348, 0.361561, 0.55802, 0.69992, 1.02956, 1.8129, 0.653155, 0.803425, 1.1645, 1.5766, 1.86188, 0.0706103, 0.0748566, 0.454391, 0.632156, 0.705776, 0.166045, 0.374647, 0.993146, 0.102733, 0.130664, 0.271716, 0.269851, 0.374781, 0.478034, 0.304563, 0.425385, 0.112124, 0.564287, 0.505653, 0.544884, 0.545367, 0.29565, 0.755459, 0.64168, 0.598543, 0.571917, 0.516094, 0.416804, 0.353145, 0.322868, 0.52636, 0.652247, 0.946337, 1.7008, 0.589969, 0.731356, 1.08223, 1.47271, 1.70316, 0.0664053, 0.0701448, 0.418212, 0.543522, 0.64933, 0.149218, 0.326342, 0.958906, 0.0848267, 0.113027, 0.256301, 0.25281, 0.755994, 0.64339, 0.60051, 0.573543, 0.51789, 0.418707, 0.355039, 0.324504, 0.527012, 0.653651, 0.950579, 1.71013, 0.594772, 0.736996, 1.09023, 1.48048, 1.71365, 0.0663364, 0.0700436, 0.420236, 0.550191, 0.653718, 0.150617, 0.327821, 0.963286, 0.0860161, 0.114394, 0.257861, 0.254669, 0.302937, 0.32915, 0.0993908, 0.325395, 0.0489748, 0.343773, 0.290712, 0.330261, 0.24838, 0.29565, 0.32915, 0.0993908, 0.325395, 0.0489748, 1.37861};
    static final double[] SA_B = {2.5596, -0.0974223, -0.221746, -2.24038};
    static final double[][] SA_W = {{0.0868538, -0.0392657, -0.133149, 0.0855609}, {-0.0615203, 0.0075469, 0.0584273, -0.00445387}, {-0.00977866, -0.0656987, 0.0420514, 0.033426}, {-0.0602852, 0.00633126, 0.00258671, 0.0513672}, {0.0133149, -0.0251014, -0.106235, 0.118021}, {-0.00215642, 0.0156044, -0.0143028, 0.000854835}, {0.0347799, -0.0469381, -0.033547, 0.0457052}, {-0.00901994, 0.00495816, -0.0427579, 0.0468197}, {0.0405889, 0.0220415, -0.0570139, -0.00561648}, {-0.00645525, 0.0325868, 0.0073276, -0.0334591}, {-0.178393, 0.0576081, 0.213278, -0.0924932}, {0.0995997, 0.00653221, -0.018746, -0.0873859}, {0.160295, -0.0823685, -0.0700863, -0.00784041}, {-0.188569, 0.119322, -0.0896746, 0.158921}, {-0.410888, 0.277168, -0.0055468, 0.139267}, {-0.495824, 0.28678, 0.118178, 0.0908649}, {0.113003, 0.0674093, -0.117149, -0.0632626}, {0.112654, -0.0700969, 0.0234127, -0.0659696}, {0.279386, 0.0251738, -0.141881, -0.162679}, {-0.00574917, 0.010236, -0.00695274, 0.00246591}, {-0.0188923, 0.011087, -0.0191483, 0.0269536}, {0.0375934, -0.0809192, 0.0969277, -0.0536019}, {-0.047386, -0.0314314, -0.155086, 0.233903}, {-0.109532, 0.0458434, -0.0365583, 0.100247}, {-0.243607, 0.302392, -0.0222257, -0.0365589}, {-0.214709, 0.142547, 0.0323758, 0.0397853}, {0.100027, -0.00926307, -0.0783864, -0.0123772}, {0.313115, -0.303489, -0.0254054, 0.0157794}, {0.344621, -0.248755, -0.0442618, -0.0516038}, {0.0839216, 0.0651284, -0.182034, 0.0329839}, {-0.0890535, -0.0121733, 0.116605, -0.0153784}, {-0.0472444, -0.0201249, 0.0893048, -0.0219352}, {-0.0132609, -0.0267174, 0.00651825, 0.0334602}, {0.0833278, 0.018504, -0.19152, 0.0896884}, {0.0278963, 0.00561337, 0.024639, -0.0581486}, {0.0613057, 0.0102851, -0.10345, 0.0318595}, {-0.0336249, 0.0419846, -0.042798, 0.0344383}, {0.160717, -0.0433939, -0.0653275, -0.0519956}, {-0.0516385, 0.0157352, 0.0479869, -0.0120837}, {-0.171614, 0.00564913, 0.200009, -0.0340439}, {0.0466758, -0.0219862, -0.025388, 0.00069845}, {0.196415, 0.0246356, -0.179537, -0.0415131}, {-0.146119, -0.0431841, 0.15817, 0.0311335}, {-0.128687, -0.0373622, 0.141105, 0.024945}, {-0.0385955, -0.170036, 0.173773, 0.0348587}, {0.496381, 0.0936346, -0.500909, -0.089107}, {0.0491864, -0.0831754, 0.0423653, -0.00837638}, {0.23642, 0.105689, -0.323248, -0.018861}, {-0.0591568, -0.0363413, 0.0686471, 0.0268509}, {-0.0499684, 0.00322186, 0.0338066, 0.0129399}, {-0.0470911, 0.0428175, 0.0503819, -0.0461082}, {0.0278063, 0.00640843, -0.135227, 0.101012}, {-0.0432938, 0.0254685, 0.0112021, 0.00662306}, {-0.0304516, -0.0683364, 0.0344445, 0.0643434}, {-0.123286, -0.0181995, 0.107229, 0.0342561}, {0.0206123, 0.0830716, -0.111507, 0.00782324}, {0.0832746, 0.0273533, -0.112785, 0.00215698}, {0.0494609, 0.0249976, -0.0465737, -0.0278849}, {0.017265, 0.0219308, 0.046446, -0.0856418}, {0.114241, -0.0228378, -0.0594392, -0.0319639}, {-0.093791, 0.206711, -0.160443, 0.0475231}, {-0.0419993, -0.119731, 0.188763, -0.0270332}, {-0.0729533, -0.00987898, -0.0269168, 0.109749}, {0.096403, -0.0519598, -0.0309631, -0.0134802}, {-0.0705663, 0.184778, -0.0958044, -0.0184073}, {-0.0796785, -0.00456546, 0.119278, -0.0350342}, {-0.109764, 0.0186518, -0.0218313, 0.112944}, {-0.110073, -0.0116535, 0.0154198, 0.106307}, {0.00322809, 0.0136784, -0.0228852, 0.0059787}, {-0.0242703, -0.0179635, 0.0827675, -0.0405336}, {-0.0648694, -0.0343841, 0.150462, -0.051208}, {0.0559667, -0.039139, -0.0264893, 0.00966171}, {0.0175804, -0.00714621, -0.0941951, 0.0837608}, {0.0245557, -0.00220056, 0.0594239, -0.0817791}, {-0.00883021, -0.0556616, 0.0199435, 0.0445483}, {-0.182209, 0.0703177, 0.0518647, 0.060027}, {0.209284, -0.0737234, -0.0778796, -0.0576809}, {-0.0340953, 0.027847, 0.0133658, -0.00711776}, {-0.122977, 0.0746205, 0.064623, -0.0162668}, {0.0535344, -0.00250334, -0.0820513, 0.0310203}, {0.240885, -0.0252823, -0.150683, -0.0649201}, {-0.00500731, -0.00930076, -0.018809, 0.0331171}, {-0.0196051, 0.0221524, -0.0207764, 0.0182292}, {-0.049774, -0.0196695, 0.0484532, 0.0209903}, {0.27764, -0.094168, -0.108958, -0.0745132}, {0.0526326, -0.159087, 0.0911941, 0.0152602}, {0.000776378, 0.0630461, -0.0595455, -0.00427691}, {0.0571475, -0.0315431, -0.0352709, 0.00966644}, {0.0506216, -0.0189012, -0.0607353, 0.0290149}, {0.0972795, -0.0236886, -0.0481146, -0.0254763}, {-0.0122889, -0.0309159, -0.0666659, 0.109871}, {-0.12044, 0.0625825, 0.0130452, 0.0448123}, {0.0870205, -0.0443136, -0.0806538, 0.037947}, {-0.127703, -0.0329045, 0.123369, 0.0372377}, {-0.0531746, 0.0937752, -0.0505413, 0.00994083}, {0.042736, 0.010924, -0.049139, -0.00452121}, {0.000198011, 0.0953434, -0.0338743, -0.0616672}, {-0.0142616, 0.0325508, -0.0216065, 0.0033173}, {-0.0339012, -0.0139233, 0.0882792, -0.0404546}, {-0.072209, -0.0311991, 0.156834, -0.0534262}, {0.05913, -0.0277257, -0.0363248, 0.00492052}, {0.0139246, -0.00464589, -0.0878606, 0.0785821}, {0.0231587, 0.00481675, 0.0541283, -0.0821038}, {0.0186242, -0.062013, 0.000301182, 0.0430877}, {-0.148257, 0.0651768, 0.0242709, 0.0588095}, {0.213523, -0.0739004, -0.0867646, -0.052858}, {-0.0364175, 0.0276575, 0.0129165, -0.00415651}, {-0.123139, 0.0656691, 0.0696769, -0.0122071}, {0.055528, -0.0124624, -0.0747928, 0.031727}, {0.208783, -0.0160325, -0.12638, -0.0663703}, {-0.0163636, -0.00909902, -0.00497406, 0.0304366}, {-0.0238084, 0.0196382, -0.0143161, 0.0184862}, {-0.0354022, -0.0386779, 0.0541087, 0.0199714}, {0.27839, -0.0756523, -0.11987, -0.0828679}, {0.0401862, -0.147439, 0.0923891, 0.0148638}, {0.0119316, 0.0628428, -0.0676195, -0.00715489}, {0.0426339, -0.0223501, -0.0322975, 0.0120139}, {0.0323446, -0.0126836, -0.0501858, 0.0305249}, {0.065427, -0.00494222, -0.0356678, -0.0248169}, {0.012038, -0.0277901, -0.0923982, 0.10815}, {-0.118577, 0.0828979, -0.0165764, 0.0522549}, {0.074133, -0.0420452, -0.068875, 0.0367872}, {-0.117369, -0.0386866, 0.131527, 0.0245288}, {-0.0286987, 0.0818911, -0.0506565, -0.00253598}, {0.0461501, 0.000908458, -0.0451394, -0.0019192}, {-8.68134e-05, 0.0774173, -0.0168843, -0.0604461}, {0.14945, 0.0167562, -0.0976576, -0.0685483}, {0.000516743, 0.0244468, -0.0332102, 0.00824662}, {0.0885849, 0.134599, -0.222399, -0.000784294}, {-0.0239681, -0.0655886, 0.106019, -0.0164619}, {-0.0240025, -0.00168076, -0.0298593, 0.0555426}, {-0.0559902, 0.00485358, 0.0765911, -0.0254545}, {0.140471, -0.0194833, -0.0242583, -0.0967292}, {0.114704, -0.0166983, -0.0297548, -0.0682505}, {-0.0456603, 0.0867058, 0.00010476, -0.0411503}, {-0.110073, -0.0116535, 0.0154198, 0.106307}, {0.000516743, 0.0244468, -0.0332102, 0.00824662}, {0.0885849, 0.134599, -0.222399, -0.000784294}, {-0.0239681, -0.0655886, 0.106019, -0.0164619}, {-0.0240025, -0.00168076, -0.0298593, 0.0555426}, {0.220457, -0.0268111, -0.133949, -0.0596976}};
    static final double[] GT_MU = {-0.42485255, -0.1192909, 0.05426552, 0.22305888, 0.30056915, 0.54232311, 0.46997332, 0.35493199, 0.2775735, 0.031557084, -0.3862559, -1.3238532, -0.74181119, -1.3663902, -1.4852393, -1.3284293, 3.5576881, 0.3546558, 0.3519248, 3.2807899, 3.508208, 7.051281, 0.62916599, -1.9538712, 4.5082277, 0.11106994, 0.15329274, 0.43221586, 0.66487227, -0.42079339, -0.11580752, 0.055678562, 0.22312628, 0.2995401, 0.54053917, 0.46788803, 0.35264418, 0.27614663, 0.030490187, -0.38569632, -1.3237559, -0.74479557, -1.3644369, -1.4786091, -1.312359, 3.5322653, 0.35373264, 0.3508672, 3.2757264, 3.5076458, 7.0306125, 0.62577002, -1.9686768, 4.5012283, 0.11234649, 0.15476484, 0.43439824, 0.66748934, 2.7548602, 0.50898717, 0.11177817, 0.35635351, 0.022881153, -0.19442906, -0.27812729, -0.42154667, -0.15975581, 0.90322581, 0.50898717, 0.11177817, 0.35635351, 0.022881153, 4.6973018};
    static final double[] GT_SD = {0.78565612, 0.6550016, 0.61683045, 0.61842664, 0.56679263, 0.43011353, 0.37397589, 0.34062133, 0.52771796, 0.70818569, 1.0218069, 1.7573301, 0.60706433, 0.68804568, 0.98723413, 1.2943919, 1.6180631, 0.073237847, 0.076598578, 0.48484121, 0.61775843, 0.63882498, 0.15986956, 0.36109456, 0.9223953, 0.093756365, 0.12647648, 0.25131888, 0.25013002, 0.78252569, 0.6534994, 0.61713378, 0.61788846, 0.56875735, 0.43242668, 0.37548205, 0.34230966, 0.52705542, 0.7086723, 1.0250341, 1.7665775, 0.61217301, 0.69321796, 0.99410482, 1.2995186, 1.605218, 0.073179007, 0.076696622, 0.49454068, 0.63226398, 0.64167875, 0.16146097, 0.36382733, 0.93274665, 0.095045638, 0.12762214, 0.25299665, 0.25208198, 0.35319721, 0.31998529, 0.1141414, 0.34497951, 0.076394244, 0.39140113, 0.336947, 0.36802312, 0.22275002, 0.29565005, 0.31998529, 0.1141414, 0.34497951, 0.076394244, 1.4022548};
    static final double[] GT_V = {0.09954559, 0.01898784, -0.037824249, -0.19168652, 0.02688937, 0.048758217, -0.048278835, 0.2387032, -0.28565554, -0.024377086, 0.082345751, 0.020276561, -0.14911385, 0.0059442207, 0.043040783, 0.07917906, -0.16917827, -0.11426235, 0.04067505, 0.05723803, -0.013886985, -0.019506504, 0.11219228, 0.10708994, 0.023531839, 0.25104952, -0.084102014, -0.084393863, -0.086410452, 0.067173205, 0.0003990151, -0.057791526, -0.13488316, 0.050847448, 0.17242331, -0.083253155, 0.21019164, -0.14247777, -0.0024579043, 0.062150196, -0.036714541, -0.10095078, -0.013184232, 0.075266867, 0.1440874, -0.17369986, -0.13309524, -0.032292932, -0.0025447191, -0.054506421, -0.067663539, 0.00064077474, 0.12802144, -0.11211902, 0.18225047, -0.11306845, -0.09135146, -0.073715909, 0.080244422, 0.052935747, -0.043078587, -0.035449052, 0.0027173114, 0.34027867, -0.37449593, 0.012529929, 0.071532568, 0.017562987, 0.052935747, -0.043078587, -0.035449052, 0.0027173114, -0.30845729};
    static final double GT_B = 0.0092832358;
        // song-aware cymbal model: end

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

        /** A run of hats and rides: the next more than RUN_GAP s on ends it; RUN_MIN hits at least. */
        static final double RUN_GAP = 0.6;
        static final int RUN_MIN = 4;
        /** The run's mean ride share that makes it a ride run (1 - this: a hat run), and how unsure a hit must be to follow it. */
        static final double RUN_SURE = 0.7, RUN_UNSURE = 0.4;

        /**
         * A groove's ride (or hat) pattern is one cymbal: in each run of closed hats and rides (as the model
         * left them) whose mean ride share (the model's) is RUN_SURE or more, the hits the model was unsure of
         * (ride share RUN_UNSURE or more) become rides; at 1 - RUN_SURE or less, the rides it was unsure of
         * become closed hats. On the songs of 32 kits: +363 cymbals right (the studio kits +35 to +70).
         */
        static void runs(List<Hit> cym, double[] rideShare, java.util.Map<Hit, Hit> to, java.util.Map<String, Integer> moves) {
            List<Integer> at = new ArrayList<Integer>();
            for (int i = 0; i < cym.size(); i++) {
                Hit h = to.containsKey(cym.get(i)) ? to.get(cym.get(i)) : cym.get(i);
                int k = kind(h.note);
                if (k == 0 || k == 2) at.add(Integer.valueOf(i));
            }
            int start = 0;
            for (int j = 1; j <= at.size(); j++) {
                if (j < at.size() && cym.get(at.get(j)).time - cym.get(at.get(j - 1)).time <= RUN_GAP) continue;
                List<Integer> run = at.subList(start, j);
                start = j;
                if (run.size() < RUN_MIN) continue;
                double mean = 0;
                for (int i : run) mean += rideShare[i];
                mean /= run.size();
                for (int i : run) {
                    Hit h = cym.get(i);
                    Hit now = to.containsKey(h) ? to.get(h) : h;
                    int want;
                    if (mean >= RUN_SURE && rideShare[i] >= RUN_UNSURE) want = RIDE_NOTE;
                    else if (mean <= 1 - RUN_SURE && rideShare[i] <= 1 - RUN_UNSURE && now.note == RIDE_NOTE) want = CLOSED_HAT;
                    else continue;
                    if (now.note == want || want == CLOSED_HAT && now.note == PEDAL_HAT) continue;
                    to.put(h, new Hit(h.time, want, h.strength));
                    String key = cymName(now.note) + " to " + cymName(want) + " (its run)";
                    moves.put(key, Integer.valueOf(moves.containsKey(key) ? moves.get(key).intValue() + 1 : 1));
                }
            }
        }

        /**
         * The model's inputs (X) for each cymbal of `hits` (in time order, into `cym`): its features, the
         * mean of the cymbals' within CM_CONTEXT s, log(1 + how many), the kind written (one-hot).
         * `byTime`, when given, gets every hit in time order.
         */
        static List<double[]> inputs(List<Hit> hits, double[] x, double sr, List<Hit> cym, List<Hit> byTimeOut, KitProfile kit) {
            List<Hit> byTime = new ArrayList<Hit>(hits);
            Collections.sort(byTime, new java.util.Comparator<Hit>() {
                public int compare(Hit p, Hit q) {
                    return Double.compare(p.time, q.time);
                }
            });
            if (byTimeOut != null) byTimeOut.addAll(byTime);
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
            List<double[]> ins = new ArrayList<double[]>();
            for (int i = 0; i < cym.size(); i++) {
                Hit h = cym.get(i);
                double[] in = new double[XK];
                System.arraycopy(fs.get(i), 0, in, 0, F);
                int n = 0;
                for (int j = 0; j < cym.size(); j++) {
                    if (j == i || Math.abs(cym.get(j).time - h.time) > CM_CONTEXT) continue;
                    for (int a = 0; a < F; a++) in[F + a] += fs.get(j)[a];
                    n++;
                }
                if (n > 0) for (int a = 0; a < F; a++) in[F + a] /= n;
                in[2 * F] = Math.log1p(n);
                in[2 * F + 1 + kind(h.note)] = 1;
                kitInputs(in, kit, x, sr, h.time);
                ins.add(in);
            }
            return ins;
        }

        /** The kit profile's inputs (XK above) of a hit at `time`. */
        static void kitInputs(double[] in, KitProfile kit, double[] x, double sr, double time) {
            if (kit == null) return;
            double[] f = null;
            boolean any = false;
            for (int k = 0; k < 4; k++) {
                double[] look = kit.look(NOTE[k]);
                if (look == null) continue;
                if (f == null && (f = cymFeature(x, sr, time)) == null) return;
                in[X + k] = cymNear(f, look);
                any = true;
            }
            in[X + 4] = any ? 1 : 0;
        }

        /**
         * -Ddm.cymFeatures=<file> (cymbal_bench.py): the hits as they are before the model (every one:
         * "h <time> <note>"), each cymbal's model inputs ("c <hit's index> <X numbers>") and the built-in
         * model ("mu", "sd", "b", "w" lines, as a model file, and "margin"), so the bench scores any model
         * on the song without running DrumMidi again.
         */
        static void dump(List<Hit> hits, double[] x, double sr, String file, KitProfile kit) {
            List<Hit> cym = new ArrayList<Hit>(), byTime = new ArrayList<Hit>();
            List<double[]> ins = inputs(hits, x, sr, cym, byTime, kit);
            StringBuilder out = new StringBuilder("# DrumMidi_CRT cymbal model inputs 1\nmargin " + CM_MARGIN + "\n");
            row(out, "mu", CM_MU);
            row(out, "sd", CM_SD);
            row(out, "b", CM_B);
            for (double[] r : CM_W) row(out, "w", r);
            java.util.Map<Hit, Integer> at = new java.util.IdentityHashMap<Hit, Integer>();
            for (int i = 0; i < byTime.size(); i++) {
                Hit h = byTime.get(i);
                at.put(h, Integer.valueOf(i));
                out.append("h ").append(String.format(java.util.Locale.ROOT, "%.6f", h.time)).append(' ').append(h.note).append('\n');
            }
            for (int i = 0; i < cym.size(); i++) row(out, "c " + at.get(cym.get(i)), ins.get(i));
            try {
                java.nio.file.Files.write(new File(file).toPath(), out.toString().getBytes("UTF-8"));
            } catch (Exception ex) {
                note("Cymbal model inputs not written: " + ex.getMessage());
            }
        }

        static void row(StringBuilder out, String key, double[] v) {
            out.append(key);
            for (double a : v) out.append(' ').append(String.format(java.util.Locale.ROOT, "%.8g", a));
            out.append('\n');
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
            apply(hits, x, sr, null);
        }

        static void apply(List<Hit> hits, double[] x, double sr, KitProfile kit) {
            String file = System.getProperty("dm.cymModelFile");
            if (file != null) {
                try {
                    load(file);
                } catch (Exception ex) {
                    note("Cymbal model file not read: " + ex.getMessage());
                }
            }
            double margin = Double.parseDouble(System.getProperty("dm.cymMargin", String.valueOf(CM_MARGIN)));
            List<Hit> cym = new ArrayList<Hit>();
            List<double[]> ins = inputs(hits, x, sr, cym, null, kit);
            // The song-aware model where its gate says the song suits it (not with a model file given, or -Ddm.cymSongModel=off;
            // on: every song).
            double[] mu = CymModel.mu, sd = CymModel.sd, b = CymModel.b;
            double[][] w = CymModel.w;
            String which = "the cymbal model";
            if (file == null && !"off".equals(System.getProperty("dm.cymSongModel")) && !cym.isEmpty()) {
                double[] look = songLook(hits, cym, ins);
                double g = GT_B;
                for (int a = 0; a < GT_MU.length && a < look.length; a++) g += (look[a] - GT_MU[a]) / GT_SD[a] * GT_V[a];
                // (-Ddm.cymSongModel=on: the song-aware model on every song, for checking it against the bench.)
                if (g > 0 || "on".equals(System.getProperty("dm.cymSongModel"))) {
                    List<double[]> with = new ArrayList<double[]>();
                    for (double[] in : ins) {
                        double[] both = java.util.Arrays.copyOf(in, in.length + look.length);
                        System.arraycopy(look, 0, both, in.length, look.length);
                        with.add(both);
                    }
                    ins = with;
                    mu = SA_MU;
                    sd = SA_SD;
                    b = SA_B;
                    w = SA_W;
                    which = "the song-aware cymbal model: the song suits it";
                } else {
                    which = "the cymbal model: the song-aware one's gate left it out";
                }
            }
            java.util.Map<String, Integer> moves = new java.util.TreeMap<String, Integer>();
            java.util.Map<Hit, Hit> to = new java.util.HashMap<Hit, Hit>();
            // Each cymbal's ride share against the closed hat (the model's p(ride) / (p(ride) + p(hat))), for the runs below.
            double[] rideShare = new double[cym.size()];
            for (int i = 0; i < cym.size(); i++) {
                Hit h = cym.get(i);
                double[] in = ins.get(i);
                int mine = kind(h.note);
                double[] z = new double[4];
                double top = -1e300;
                for (int c = 0; c < 4; c++) {
                    z[c] = b[c];
                    for (int a = 0; a < mu.length && a < in.length; a++) z[c] += (in[a] - mu[a]) / sd[a] * w[a][c];
                    top = Math.max(top, z[c]);
                }
                double sum = 0;
                for (int c = 0; c < 4; c++) sum += (z[c] = Math.exp(z[c] - top));
                rideShare[i] = z[2] / (z[2] + z[0] + 1e-300);
                int best = 0;
                for (int c = 0; c < 4; c++) if (z[c] > z[best]) best = c;
                if (best == mine || (z[best] - z[mine]) / sum <= margin) continue;
                to.put(h, new Hit(h.time, NOTE[best], h.strength));
                String key = cymName(h.note) + " to " + cymName(NOTE[best]);
                moves.put(key, Integer.valueOf(moves.containsKey(key) ? moves.get(key).intValue() + 1 : 1));
            }
            runs(cym, rideShare, to, moves);
            for (int i = 0; i < hits.size(); i++) if (to.containsKey(hits.get(i))) hits.set(i, to.get(hits.get(i)));
            StringBuilder said = new StringBuilder();
            for (java.util.Map.Entry<String, Integer> e : moves.entrySet()) said.append(said.length() == 0 ? "" : ", ").append(e.getValue()).append(' ').append(e.getKey());
            note("Cymbals by their sound in the song (" + which + "): " + (said.length() == 0 ? "none changed" : said));
        }

        /**
         * The song's look, as the song-aware model and its gate read it (vst_render's BenchKit.song_inputs):
         * its cymbals' inputs on average, the share of each kind DrumMidi wrote (closed hat, open hat, ride,
         * crash), and cymbals a second over the song's hits (at least 1 s). Not a number when an input is not.
         */
        static double[] songLook(List<Hit> hits, List<Hit> cym, List<double[]> ins) {
            int d = ins.get(0).length;
            double[] look = new double[d + 5];
            for (double[] in : ins) for (int a = 0; a < d; a++) look[a] += in[a] / ins.size();
            for (Hit h : cym) look[d + benchKind(h.note)] += 1.0 / cym.size();
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (Hit h : hits) {
                lo = Math.min(lo, h.time);
                hi = Math.max(hi, h.time);
            }
            look[d + 4] = cym.size() / Math.max(1.0, hits.isEmpty() ? 1.0 : hi - lo);
            return look;
        }

        /** A cymbal's kind as the bench counts what DrumMidi wrote: 0 closed hat (and any other), 1 open hat, 2 ride, 3 crash. */
        static int benchKind(int note) {
            if (note == 46) return 1;
            if (note == 51 || note == 53 || note == 59) return 2;
            if (note == 49 || note == 52 || note == 55 || note == 57) return 3;
            return 0;
        }
    }

    static boolean isTomNote(int n) {
        return n == 41 || n == 43 || n == 45 || n == 47 || n == 48 || n == 50;
    }

    /** The song's loud snares' body spectrum (120-450 Hz), to tell its ghost notes from toms. */
    /**
     * A snare over a guitar: a logistic weighing of the onset's measures, fitted on kits rendered
     * from known MIDI with a distorted or clean guitar over them (tools/accomp_mix.py): the song's
     * snare body (ghost), the body, kick and hat bands' jumps, the wires left, the snare onset, and
     * how the 2-8 kHz level rises and dies (a snare's wires die, a chord rings on).
     */
    static final class GuitarSnare {
        static final double TAKE = 0.4;
        static final double[] MEAN = {0.19685, 0.39879, 0.31473, 0.42606, 0.33621, 0.86352, 0.6313, 1.01344};
        static final double[] SD = {0.39762, 0.28568, 0.35183, 1.28533, 0.28105, 0.51521, 0.42008, 0.97584};
        static final double[] W = {0.40241, 1.43963, -0.4319, -0.40747, 0.93743, -0.90283, -0.50328, 0.2083};
        static final double BIAS = -1.34753;

        static double chance(Levels lv, float[][] mag, SnareBody loud, double[] upper, double[] snare, int t) {
            // (The fit's frames were not centred: its frame t is this one's t + 2.)
            double peak = 0;
            for (int k = t + 1; k <= t + 4; k++) if (k < upper.length) peak = Math.max(peak, upper[k]);
            double dec = t + 10 < upper.length && peak > 0 ? upper[t + 10] / peak : 1;
            double rise = t >= 1 && upper[t - 1] > 0 ? peak / upper[t - 1] : 1;
            double[] x = {loud.ghost(mag, t) ? 1 : 0, jump(lv.body, t), jump(lv.kick, t), wiresLeft(lv.noise, t), jump(lv.hat, t), snare[t], dec,
                Math.log(Math.max(rise, 1e-3))};
            double z = BIAS;
            for (int i = 0; i < x.length; i++) z += W[i] * (x[i] - MEAN[i]) / SD[i];
            return 1 / (1 + Math.exp(-z));
        }
    }

    /**
     * Hats over a guitar: its chords' attacks reach the hat band, and ring on as open hats. A logistic
     * weighing (fitted as GuitarSnare's) of each hat's rise at 6-14 kHz, at 1-4 kHz (a chord's) and
     * their ratio, how both fade, the 150-600 Hz rise, a drum written with it and whether it is open;
     * a hat under DROP is left out (kits it was not fitted on: 485 hats dropped, 410 of them wrong).
     */
    static final class GuitarHat {
        static final double DROP = 0.3;
        static final double[] MEAN = {1.62401, 0.92565, -1.03661, 0.58309, 0.68695, 0.71184, 0.48872, 0.10768};
        static final double[] SD = {1.34905, 0.99806, 0.60491, 0.2816, 0.26778, 0.96183, 0.49987, 0.30998};
        static final double[] W = {-0.31649, 0.67598, 2.93073, -0.16522, 0.73709, -0.21965, 0.35954, 0.03176};
        static final double BIAS = 1.70403;

        static void drop(List<Hit> hits, double[] hat, double[] mid, double[] low, double sr) {
            List<Hit> out = new ArrayList<Hit>();
            for (Hit h : hits) {
                if ((h.note != CLOSED_HAT && h.note != OPEN_HAT) || chance(h, hits, hat, mid, low, sr) >= DROP && !(h.note == OPEN_HAT && chordOpen(h, hat, mid, sr))) out.add(h);
            }
            hits.clear();
            hits.addAll(out);
        }

        static double chance(Hit h, List<Hit> hits, double[] hat, double[] mid, double[] low, double sr) {
            int k = (int) Math.round(h.time * sr / HOP);
            if (k < 3 || k + 6 >= hat.length) return 1;
            double hp = peak(hat, k), mp = peak(mid, k), lp = peak(low, k);
            boolean drum = false;
            for (Hit o : hits) drum |= Math.abs(o.time - h.time) < 0.03 && (o.note == KICK_NOTE || o.note == SNARE_NOTE || isTomNote(o.note));
            double[] x = {Math.log(hp / (hat[k - 3] + 1e-9)), Math.log(mp / (mid[k - 3] + 1e-9)), Math.log(hp / (mp + 1e-9)), hat[k + 6] / (hp + 1e-12),
                mid[k + 6] / (mp + 1e-9), Math.log(lp / (low[k - 3] + 1e-9)), drum ? 1 : 0, h.note == OPEN_HAT ? 1 : 0};
            double z = BIAS;
            for (int i = 0; i < x.length; i++) z += W[i] * (x[i] - MEAN[i]) / SD[i];
            return 1 / (1 + Math.exp(-z));
        }

        /**
         * An open hat whose 6-14 kHz rise is under OPEN_CHORD (log) of its 1-4 kHz rise: a chord ringing
         * on (the fit's wrong open hats: -1.46 and under for nine in ten; real ones' median -0.95).
         */
        static final double OPEN_CHORD = -1.4;

        static boolean chordOpen(Hit h, double[] hat, double[] mid, double sr) {
            int k = (int) Math.round(h.time * sr / HOP);
            if (k < 3 || k + 2 >= hat.length) return false;
            return Math.log(peak(hat, k) / (peak(mid, k) + 1e-9)) < OPEN_CHORD;
        }

        static double peak(double[] v, int k) {
            double p = 0;
            for (int i = k - 1; i <= Math.min(v.length - 1, k + 2); i++) p = Math.max(p, v[i]);
            return p;
        }
    }

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
        if (drumStem) return false;
        double[] g = bandMean(mag, bin(300, sr, maxBin), bin(1000, sr, maxBin));
        Arrays.sort(g);
        double loud = g[(int) (g.length * 0.95)];
        return loud > 1e-9 && g[g.length / 4] >= GUITAR_FLOOR * loud;
    }

    /** Something keeps the kick band up half the time: a bass line (drums alone leave it quiet between hits). */
    static boolean bassLine(double[] kickLevel) {
        if (drumStem) return false;
        double[] s = kickLevel.clone();
        Arrays.sort(s);
        return s[s.length / 2] >= BASS_LINE;
    }

    /**
     * The kick band's median level at which a bass line is taken to play. Was QUIET_BEFORE (0.25): a
     * bass mixed under the drums (a bench of kits with one at 40% of their level: 0.18-0.28; drums
     * alone up to 0.13, SogniMusic's drum sessions 0.14 for nine in ten) went unheard, kicks 81% right.
     */
    static final double BASS_LINE = 0.2;

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
            if (note == h.note) continue;
            Hit made = new Hit(h.time, note, h.strength);
            made.fromHat = h.note == CLOSED_HAT;
            hits.set(hits.indexOf(h), made);
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
     * One that was a hat before rideOrHats is the hat again. Not where a bass line plays: there a ride
     * is often heard only on the beat.
     */
    /**
     * A ride lonely on the kicks that was a hat before rideOrHats is the hat again only when its hat
     * onset is at least this strong (a kick's click in the hat band is weaker): on 40 songs of KVLT,
     * Anarchy, Noob Metal and Riot, +28 cymbals right (+45 with any, but 28 hats more written wrong).
     */
    static final double RESTORE_HAT = 1.0;

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
            if (near >= RIDE_ALONE) continue;
            // A hat found as one, made a ride by the cymbals around it: the hat it was (hats struck with a
            // kick on every eighth have no clean hats of their own to vote).
            if (h.fromHat && h.strength >= RESTORE_HAT) hits.set(hits.indexOf(h), new Hit(h.time, CLOSED_HAT, h.strength));
            else hits.remove(h);
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