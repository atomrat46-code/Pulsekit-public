import javax.sound.midi.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * MidiDrumGen: a drum track as a MIDI file, from a style (Pulsekit's style names: Hard Rock,
 * Deep House, Boom Bap...), with fills, crashes and a half-time last quarter.
 *
 * The output file is given alone or with --output (default drum_track_full_db.mid).
 * The tempo stays in the style's range from the style database (Hard Rock 112-145): a tempo given
 * outside it is moved to the nearest end, unless --any-tempo.
 * --timesig sets the meter (default: the style's, 4/4 for most; PyJav passes the app's when it is not
 * 4/4; styles such as Ballad 6/8, Slow Blues (12/8) and Waltz (3/4) have their own): a simple meter
 * plays the style's bar cut or extended to its length, 6/8, 9/8 and 12/8 a dotted-quarter feel,
 * their fills taking the bar's second half in eighths (as Pulsekit's 6/8 and 12/8 fills do).
 * In PyJav --bpm (the app's tempo) stands for --tempo, --swing may be a percent (PyJav passes the
 * app's 12 for 0.12), and the MIDI goes into PyJav's work folder, so it is imported.
 */
public class MidiDrumGen {

    static final String USAGE = "Usage: java MidiDrumGen [output.mid] [--style name] [--tempo N] [--bars N] [--swing N] [--intensity 1-10] "
            + "[--hats auto|8ths|16ths|offbeat] [--humanize 0-12] [--no-fills] [--no-crashes] [--no-half-time] [--any-tempo] [--timesig 3/4|6/8|...]";

    public static final int KICK = 36, SNARE = 38, SIDESTICK = 37, CLAP = 39;
    public static final int CLOSED_HH = 42, PEDAL_HH = 44, OPEN_HH = 46;
    public static final int CRASH = 49, RIDE = 51, CHINA = 52, RIDE_BELL = 53, CRASH2 = 57;
    public static final int FLOOR_TOM = 41, LOW_TOM = 45, MID_TOM = 47, HIGH_TOM = 50;

    public static final List<String> STYLES = new ArrayList<>();
    public static final Map<String, DefaultArgs> DEFAULTS = new HashMap<>();
    public static final Map<String, String> BASE_STYLES = new HashMap<>();

    public static class DefaultArgs {
        int tempo, bars, intensity;
        /** The style's tempo range in the style database (0 when it has none). */
        int minTempo, maxTempo;
        /** The style's time signature (the database's tenth column; 4/4 when it has none). */
        int tsNum = 4, tsDen = 4;
        double swing;
        String hats;

        DefaultArgs(int tempo, int bars, double swing, int intensity, String hats) {
            this.tempo = tempo;
            this.bars = bars;
            this.swing = swing;
            this.intensity = intensity;
            this.hats = hats;
        }
    }

    private static final String RAW_STYLES = 
            "House|house|124|118|130|1|0.9|8|0\n" +
            "Deep House|house|122|116|126|1|0.7|8|0\n" +
            "Tech House|house|125|120|130|1|0.8|10|0\n" +
            "Progressive House|house|126|122|132|1|0.25|8|0\n" +
            "Funky House|house|122|118|128|1|0.4|8|0\n" +
            "Disco|house|120|110|126|1|0.55|8|0\n" +
            "Nu Disco|house|118|112|124|1|0.5|8|0\n" +
            "Chicago House|house|124|118|128|1|0.3|8|0\n" +
            "French House|house|122|116|126|1|0.4|8|0\n" +
            "Acid House|house|126|120|132|1|0.2|8|0\n" +
            "Afro House|house|120|114|126|1|0.35|8|0\n" +
            "Organic House|house|122|116|126|1|0.2|6|0\n" +
            "Piano House|house|124|118|128|1|0.4|8|0\n" +
            "Eurodance|house|136|128|142|1|0.5|8|0\n" +
            "Italo Disco|house|128|118|136|1|0.45|8|0\n" +
            "Amapiano|house|112|108|118|1|0.3|6|0\n" +
            "Gqom|house|126|120|132|1|0.15|8|0\n" +
            "EDM|house|128|124|136|1|0.4|8|0\n" +
            "Big Room|house|128|124|132|1|0.35|8|0\n" +
            "Future House|house|126|122|130|1|0.3|10|0\n" +
            "Electro House|house|128|124|132|1|0.25|8|0\n" +
            "Slap House|house|126|122|130|1|0.4|8|0\n" +
            "Techno|techno|132|126|140|1|0.15|16|0\n" +
            "Melodic Techno|techno|130|124|136|1|0.2|12|0\n" +
            "Industrial Techno|techno|138|130|150|1|0.1|16|0\n" +
            "Hard Techno|techno|142|136|155|1|0.1|16|0\n" +
            "Minimal Techno|techno|128|124|134|1|0.1|8|0\n" +
            "Detroit Techno|techno|130|124|136|1|0.2|12|0\n" +
            "Schranz|techno|150|140|160|1|0.05|16|0\n" +
            "Trance|techno|138|130|145|1|0.25|8|0\n" +
            "Psytrance|techno|145|138|150|1|0.2|16|0\n" +
            "Hardstyle|techno|150|140|160|1|0.3|8|0\n" +
            "Gabber|techno|180|165|200|1|0.1|16|0\n" +
            "EBM|techno|130|120|140|1|0.2|8|0\n" +
            "UKG|ukg|132|126|138|0.55|0.7|8|0\n" +
            "2-Step|ukg|132|126|138|0.4|0.75|8|0\n" +
            "Speed Garage|ukg|134|128|140|0.6|0.5|8|0\n" +
            "Bassline|ukg|136|130|142|0.7|0.45|10|0\n" +
            "Broken Beat|ukg|128|118|136|0.35|0.5|8|0\n" +
            "Grime|ukg|140|130|150|0.4|0.6|10|0\n" +
            "Hip-Hop|hiphop|92|80|100|0.25|0.9|6|0\n" +
            "East Coast|hiphop|94|84|102|0.3|0.9|6|0\n" +
            "West Coast|hiphop|98|88|108|0.35|0.85|6|0\n" +
            "G-Funk|hiphop|96|88|104|0.3|0.85|5|0\n" +
            "Alternative Hip-Hop|hiphop|90|80|100|0.25|0.8|6|0\n" +
            "Jazz Rap|hiphop|88|78|98|0.2|0.85|5|0\n" +
            "Crunk|hiphop|80|72|90|0.4|0.9|8|0\n" +
            "Hyphy|hiphop|110|100|118|0.45|0.8|8|0\n" +
            "Boom Bap|boombap|88|80|96|0.2|0.95|5|0\n" +
            "Lo-fi Hip-Hop|boombap|84|72|94|0.2|0.85|4|0\n" +
            "90s Hip-Hop|boombap|90|82|98|0.25|0.95|5|0\n" +
            "Golden Age|boombap|88|80|96|0.2|0.9|5|0\n" +
            "Trap|trap|140|130|160|0.35|0.7|14|0\n" +
            "Drill|trap|142|132|155|0.3|0.75|14|0\n" +
            "UK Drill|trap|140|132|150|0.35|0.7|12|0\n" +
            "Phonk|trap|148|130|165|0.4|0.7|12|0\n" +
            "Cloud Rap|trap|130|118|145|0.25|0.6|10|0\n" +
            "Trap Metal|trap|150|135|170|0.4|0.65|12|0.3\n" +
            "Jersey Club|trap|132|128|140|0.5|0.5|10|0\n" +
            "Dubstep|trap|140|135|150|0.3|0.55|8|0\n" +
            "Riddim|trap|145|138|155|0.35|0.5|8|0\n" +
            "Moombahton|trap|108|102|115|0.7|0.5|8|0\n" +
            "Rock|rock|118|100|130|0.5|1|6|0\n" +
            "Indie Rock|rock|120|105|135|0.45|0.95|6|0\n" +
            "Britpop|rock|118|108|128|0.5|1|6|0\n" +
            "Garage Rock|rock|130|115|145|0.55|1|5|0\n" +
            "Blues Rock|rock|110|90|125|0.4|0.95|5|0\n" +
            "Southern Rock|rock|112|96|124|0.45|0.95|5|0\n" +
            "Classic Rock|rock|116|100|128|0.5|1|6|0\n" +
            "Alternative|rock|122|105|135|0.45|0.95|6|0\n" +
            "Grunge|rock|110|95|125|0.5|1|5|0\n" +
            "Post-Punk|rock|130|115|145|0.4|0.85|8|0\n" +
            "New Wave|rock|128|115|140|0.5|0.8|8|0\n" +
            "Punk|rock|180|160|210|0.55|1|6|0\n" +
            "Pop Punk|rock|175|155|195|0.55|1|8|0\n" +
            "Emo|rock|165|140|185|0.5|0.95|6|0\n" +
            "Country|rock|108|90|122|0.4|0.95|4|0\n" +
            "Americana|rock|102|88|118|0.35|0.9|4|0\n" +
            "Folk|folk|98|78|124|0.4|0.85|4|0\n" +
            "Acoustic Folk|folk|94|76|118|0.25|0.75|3|0\n" +
            "Folk Rock|rock|100|85|115|0.3|0.85|4|0\n" +
            "Hard Rock|hardrock|128|112|145|0.55|1|6|0\n" +
            "Arena Rock|hardrock|124|110|138|0.5|1|6|0\n" +
            "Glam Rock|hardrock|130|115|145|0.5|1|6|0\n" +
            "Stoner Rock|hardrock|70|60|90|0.5|0.9|4|0\n" +
            "Garage Punk|hardrock|160|140|185|0.55|1|5|0\n" +
            "Metal|metal|156|140|180|0.55|1|8|0.4\n" +
            "Thrash|metal|180|160|210|0.6|1|8|0.7\n" +
            "Death Metal|metal|200|170|230|0.5|0.7|8|0.85\n" +
            "Black Metal|metal|180|150|220|0.5|0.5|8|0.8\n" +
            "Doom|metal|70|55|85|0.5|0.9|4|0.2\n" +
            "Sludge|metal|80|60|100|0.5|0.9|4|0.2\n" +
            "Nu Metal|metal|120|100|140|0.5|0.95|8|0.3\n" +
            "Metalcore|metal|160|140|185|0.55|0.95|8|0.6\n" +
            "Power Metal|metal|165|145|185|0.55|1|8|0.5\n" +
            "Folk Metal|metal|150|130|175|0.5|0.9|6|0.4\n" +
            "Industrial Metal|metal|130|115|150|0.55|0.85|10|0.3\n" +
            "Prog Metal|progmetal|148|130|175|0.45|0.8|8|0.85\n" +
            "Djent|progmetal|140|120|165|0.4|0.7|8|0.9\n" +
            "Math Metal|progmetal|150|130|180|0.4|0.75|8|0.8\n" +
            "Progressive Metal|progmetal|144|125|170|0.45|0.8|8|0.8\n" +
            "Rock Ballad|rockballad|72|60|84|0.35|0.9|4|0\n" +
            "Power Ballad|rockballad|74|62|86|0.4|0.95|4|0\n" +
            "Metal Ballad|metalballad|76|64|88|0.4|0.9|4|0.15\n" +
            "Pop Ballad|popballad|70|58|82|0.25|0.8|3|0\n" +
            "Singer-Songwriter|popballad|72|60|88|0.2|0.7|2|0\n" +
            "Acoustic|popballad|76|62|90|0.2|0.65|2|0\n" +
            "Ambient|popballad|70|50|90|0.1|0.2|2|0\n" +
            "Downtempo|popballad|80|70|95|0.3|0.5|4|0\n" +
            "Trip Hop|popballad|85|75|96|0.3|0.7|4|0\n" +
            "Chillout|popballad|82|70|96|0.35|0.5|4|0\n" +
            "Dream Pop|popballad|90|75|105|0.3|0.6|4|0\n" +
            "Shoegaze|rockballad|95|80|115|0.4|0.7|6|0\n" +
            "Post-Rock|rockballad|88|70|110|0.3|0.5|4|0\n" +
            "Funk|funk|108|98|118|0.4|0.7|8|0\n" +
            "P-Funk|funk|104|96|114|0.35|0.65|8|0\n" +
            "Boogie|funk|112|104|120|0.6|0.6|8|0\n" +
            "Neo Soul|funk|90|80|102|0.3|0.8|6|0\n" +
            "R&B|funk|80|70|96|0.3|0.85|6|0\n" +
            "Motown|funk|110|100|122|0.45|0.95|6|0\n" +
            "Go-Go|funk|108|100|118|0.5|0.7|8|0\n" +
            "New Jack Swing|funk|112|104|120|0.4|0.85|8|0\n" +
            "Reggae|funk|78|68|90|0.3|0.6|6|0\n" +
            "Dancehall|funk|100|90|110|0.4|0.55|8|0\n" +
            "Ska|funk|140|120|160|0.5|0.7|8|0\n" +
            "Rocksteady|funk|80|70|90|0.3|0.6|6|0\n" +
            "Dub|funk|76|68|88|0.35|0.5|4|0\n" +
            "Jazz|funk|120|90|160|0.25|0.7|6|0\n" +
            "Swing|funk|140|110|180|0.3|0.8|6|0\n" +
            "Fusion|funk|110|95|130|0.35|0.7|8|0\n" +
            "Gospel|funk|100|85|120|0.4|0.9|6|0\n" +
            "Blues|funk|80|70|100|0.3|0.9|4|0\n" +
            "Latin|latin|100|90|115|0.4|0.6|8|0\n" +
            "Salsa|latin|180|160|200|0.35|0.5|8|0\n" +
            "Samba|latin|100|90|120|0.5|0.4|10|0\n" +
            "Bossa Nova|latin|130|110|150|0.25|0.6|6|0\n" +
            "Mambo|latin|110|100|125|0.4|0.5|8|0\n" +
            "Cumbia|latin|100|90|110|0.5|0.4|6|0\n" +
            "Merengue|latin|140|120|160|0.6|0.4|8|0\n" +
            "Reggaeton|latin|95|88|105|0.5|0.7|8|0\n" +
            "Dembow|latin|100|92|108|0.55|0.65|8|0\n" +
            "Afrobeat|latin|110|100|122|0.5|0.5|8|0\n" +
            "Highlife|latin|120|108|132|0.45|0.5|8|0\n" +
            "Baile Funk|latin|130|120|140|0.5|0.6|8|0\n" +
            "Pop|pop|110|98|122|0.5|0.95|6|0\n" +
            "Synthpop|pop|118|108|128|0.55|0.85|8|0\n" +
            "K-Pop|pop|122|110|132|0.5|0.9|8|0\n" +
            "J-Pop|pop|128|115|138|0.5|0.9|8|0\n" +
            "Dance Pop|pop|120|110|130|0.7|0.8|8|0\n" +
            "Indie Pop|pop|116|100|128|0.4|0.85|6|0\n" +
            "Electropop|pop|120|110|130|0.6|0.8|8|0\n" +
            "City Pop|pop|112|100|122|0.45|0.85|6|0\n" +
            "Breaks|breakbeat|136|120|150|0.3|0.7|8|0\n" +
            "Big Beat|breakbeat|126|115|138|0.4|0.7|8|0\n" +
            "Jungle Breaks|breakbeat|160|150|175|0.3|0.6|10|0\n" +
            "Miami Bass|breakbeat|125|115|135|0.7|0.5|8|0\n" +
            "Ghettotech|breakbeat|145|135|160|0.7|0.4|12|0\n" +
            "DnB|dnb|172|160|180|0.35|0.6|12|0\n" +
            "Jungle|dnb|170|160|180|0.3|0.55|12|0\n" +
            "Liquid DnB|dnb|172|164|178|0.3|0.65|10|0\n" +
            "Neurofunk|dnb|174|166|180|0.35|0.55|14|0\n" +
            "Jump Up|dnb|174|166|180|0.4|0.6|12|0\n" +
            "Drumstep|dnb|160|150|170|0.4|0.6|10|0\n" +
            "Breakcore|dnb|190|170|220|0.3|0.5|14|0\n" +
            "Tropical House|house|118|110|124|1|0.25|8|0\n" +
            "Microhouse|house|126|122|132|1|0.15|8|0\n" +
            "Deep Tech|house|126|122|130|1|0.2|10|0\n" +
            "Jackin House|house|124|118|128|1|0.4|8|0\n" +
            "Tribal House|house|125|118|132|1|0.3|10|0\n" +
            "Melodic House|house|122|116|128|1|0.25|8|0\n" +
            "Fidget House|house|128|124|132|1|0.3|10|0\n" +
            "Dutch House|house|128|124|132|1|0.35|8|0\n" +
            "Complextro|house|128|124|134|1|0.3|8|0\n" +
            "Acid Techno|techno|135|128|145|1|0.1|16|0\n" +
            "Dub Techno|techno|128|122|134|1|0.1|8|0\n" +
            "Peak Time|techno|132|126|140|1|0.15|12|0\n" +
            "Warehouse|techno|130|124|138|1|0.1|12|0\n" +
            "Hardgroove|techno|138|130|148|1|0.15|16|0\n" +
            "UK Funky|ukg|130|124|138|0.5|0.65|8|0\n" +
            "Future Garage|ukg|132|126|140|0.4|0.7|8|0\n" +
            "Night Bass|ukg|128|122|136|0.55|0.6|8|0\n" +
            "Dirty South|hiphop|96|86|108|0.4|0.9|6|0\n" +
            "Memphis Rap|hiphop|90|80|100|0.3|0.9|6|0\n" +
            "Conscious Rap|hiphop|88|78|98|0.2|0.85|5|0\n" +
            "Chopped and Screwed|hiphop|70|60|82|0.3|0.85|4|0\n" +
            "Southern Rap|hiphop|95|85|108|0.35|0.9|6|0\n" +
            "Rage|trap|144|132|160|0.4|0.65|14|0\n" +
            "Plugg|trap|140|128|155|0.3|0.6|12|0\n" +
            "Trap Soul|trap|130|118|142|0.3|0.7|10|0\n" +
            "Twerk|trap|100|92|110|0.5|0.7|10|0\n" +
            "Post-Grunge|rock|118|100|130|0.5|1|6|0\n" +
            "Soft Rock|rock|100|85|115|0.4|0.95|4|0\n" +
            "Heartland|rock|112|96|124|0.45|0.95|5|0\n" +
            "Surf|rock|160|140|180|0.5|1|6|0\n" +
            "Psych Rock|rock|110|90|130|0.4|0.85|6|0\n" +
            "College Rock|rock|120|105|135|0.45|0.95|6|0\n" +
            "Hair Metal|hardrock|128|112|145|0.55|1|6|0\n" +
            "Southern Metal|hardrock|110|95|125|0.5|0.95|5|0\n" +
            "Groove Metal|metal|140|120|160|0.55|1|8|0.5\n" +
            "Deathcore|metal|180|160|210|0.5|0.7|8|0.8\n" +
            "Melodic Death|metal|170|150|195|0.5|0.9|8|0.7\n" +
            "Speed Metal|metal|190|170|220|0.55|1|8|0.75\n" +
            "NWOBHM|metal|150|130|170|0.55|1|6|0.4\n" +
            "Symphonic Metal|metal|155|135|175|0.5|0.9|8|0.45\n" +
            "Technical Death|progmetal|180|160|210|0.45|0.7|8|0.85\n" +
            "Progressive Rock|progmetal|120|90|150|0.4|0.8|6|0.3\n" +
            "Slowcore|popballad|68|55|80|0.15|0.6|2|0\n" +
            "Sadcore|rockballad|70|58|84|0.25|0.75|3|0\n" +
            "Jazz-Funk|funk|108|96|118|0.35|0.7|8|0\n" +
            "Soul|funk|90|78|104|0.3|0.85|6|0\n" +
            "Disco Funk|funk|118|110|126|0.55|0.65|8|0\n" +
            "Acid Jazz|funk|110|98|122|0.3|0.7|6|0\n" +
            "Bachata|latin|130|120|140|0.4|0.55|8|0\n" +
            "Tango|latin|120|110|132|0.35|0.6|6|0\n" +
            "Rumba|latin|100|90|112|0.4|0.5|8|0\n" +
            "Soca|latin|140|128|155|0.55|0.45|8|0\n" +
            "Calypso|latin|116|104|128|0.45|0.5|8|0\n" +
            "Forro|latin|120|108|132|0.5|0.45|8|0\n" +
            "Teen Pop|pop|118|108|128|0.5|0.9|6|0\n" +
            "Adult Contemporary|pop|100|88|112|0.4|0.9|4|0\n" +
            "Hyperpop|pop|140|125|160|0.55|0.75|10|0\n" +
            "Bubblegum|pop|122|112|132|0.55|0.9|8|0\n" +
            "Euro Pop|pop|128|118|138|0.6|0.85|8|0\n" +
            "Nu Skool Breaks|breakbeat|132|120|145|0.35|0.7|8|0\n" +
            "Acid Breaks|breakbeat|136|124|148|0.3|0.65|10|0\n" +
            "Funky Breaks|breakbeat|130|118|142|0.4|0.7|8|0\n" +
            "Darkstep|dnb|174|166|182|0.3|0.5|14|0\n" +
            "Techstep|dnb|174|166|180|0.35|0.55|14|0\n" +
            "Atmospheric DnB|dnb|170|162|178|0.25|0.6|10|0\n" +
            "Sambass|dnb|172|164|180|0.4|0.55|12|0\n" +
            "Ballad 6/8|ballad68|60|50|72|0.3|0.9|6|0|6/8\n" +
            "Afro 6/8|latin|110|96|125|0.5|0.5|6|0|6/8\n" +
            "Irish Jig|folk|116|100|130|0.4|0.6|6|0|6/8\n" +
            "Slow Blues|blues128|60|48|72|0.3|0.9|12|0|12/8\n" +
            "Doo-Wop|popballad|66|56|78|0.3|0.9|12|0|12/8\n" +
            "Waltz|popballad|96|84|180|0.3|0.6|3|0|3/4\n" +
            "Jazz Waltz|funk|150|120|200|0.3|0.6|6|0|3/4";

    static {
        List<String> EXACT = Arrays.asList(
                "doom_metal", "thrash_metal", "pop_punk", "indie_rock", "techno", "trance",
                "dubstep", "uk_garage", "synthwave", "breakbeat", "gabber", "boom_bap",
                "drill", "neo_soul", "reggaeton", "samba", "afrobeat", "ska", "blues_shuffle",
                "hard_rock", "rock", "metal", "punk", "funk", "hiphop", "house", "pop",
                "jazz", "reggae", "bossa_nova", "disco", "dnb", "trap", "lofi", "country"
        );
        STYLES.addAll(EXACT);

        for (String line : RAW_STYLES.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("Tyylit")) continue;
            String[] p = line.split("\\|");
            if (p.length >= 3) {
                String id = p[0].toLowerCase().replace(" ", "_").replace("-", "_");
                if (!STYLES.contains(id)) STYLES.add(id);

                String base = p[1];
                int tempo = Integer.parseInt(p[2]);
                int minTempo = p.length >= 5 ? Integer.parseInt(p[3]) : 0;
                int maxTempo = p.length >= 5 ? Integer.parseInt(p[4]) : 0;

                double swing = 0.0;
                int intensity = 6;
                String hats = "8ths";

                switch (base) {
                    case "house": case "techno": hats = "offbeat"; intensity = 7; break;
                    case "ukg": hats = "16ths"; swing = 0.3; break;
                    case "hiphop": hats = "16ths"; swing = 0.12; break;
                    case "boombap": hats = "8ths"; swing = 0.22; break;
                    case "trap": hats = "16ths"; intensity = 8; break;
                    case "rock": case "hardrock": intensity = 7; break;
                    case "metal": case "progmetal": intensity = 9; break;
                    case "rockballad": case "popballad": case "metalballad": case "ballad68": intensity = 4; break;
                    case "blues128": swing = 0.0; intensity = 4; break;
                    case "funk": hats = "16ths"; swing = 0.18; break;
                    case "latin": hats = "16ths"; intensity = 7; break;
                    case "pop": intensity = 5; break;
                    case "breakbeat": hats = "16ths"; swing = 0.15; intensity = 7; break;
                    case "dnb": hats = "16ths"; intensity = 8; break;
                }

                DefaultArgs styleArgs = new DefaultArgs(tempo, 16, swing, intensity, hats);
                styleArgs.minTempo = minTempo;
                styleArgs.maxTempo = maxTempo;
                int[] meter = p.length >= 10 ? timesig(p[9]) : null;
                if (meter != null) {
                    styleArgs.tsNum = meter[0];
                    styleArgs.tsDen = meter[1];
                }
                DEFAULTS.put(id, styleArgs);

                String internalBase = base;
                if (base.equals("ukg")) internalBase = "uk_garage";
                else if (base.equals("boombap")) internalBase = "boom_bap";
                else if (base.equals("hardrock")) internalBase = "hard_rock";
                else if (base.equals("progmetal")) internalBase = "metal";
                else if (base.equals("rockballad") || base.equals("popballad") || base.equals("metalballad") || base.equals("ballad68")) internalBase = "rock";
                else if (base.equals("blues128")) internalBase = "blues_shuffle";
                else if (base.equals("latin")) internalBase = "reggaeton";

                BASE_STYLES.put(id, internalBase);
            }
        }
    }

    public static String resolveStyle(String style) {
        if (DEFAULTS.containsKey(style) && !BASE_STYLES.containsKey(style)) return style;
        return BASE_STYLES.getOrDefault(style, style);
    }

    public static int clamp(int n, int lo, int hi) {
        return Math.max(lo, Math.min(hi, n));
    }

    public static double clampDouble(double n, double lo, double hi) {
        return Math.max(lo, Math.min(hi, n));
    }

    public static int jitter(String seedS, int base, int humanize) {
        if (humanize <= 0) return clamp(base, 1, 127);
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(seedS.getBytes(StandardCharsets.UTF_8));
            int hexVal = digest[0] & 0xFF;
            int delta = (hexVal % (humanize * 2 + 1)) - humanize;
            return clamp(base + delta, 1, 127);
        } catch (Exception e) {
            return clamp(base, 1, 127);
        }
    }

    public static class DrumWriter {
        Sequence sequence;
        Track track;
        int channel = 9;
        int humanize;
        int ppq = 480;
        Set<String> used = new HashSet<>();
        /** Only hits from winStart up to winEnd (the bar being written) are kept: a pattern longer than the bar is cut. */
        double winStart = -1e9, winEnd = 1e9;

        public DrumWriter(int tempo, int humanize) {
            this(tempo, humanize, 4, 4);
        }

        public DrumWriter(int tempo, int humanize, int tsNum, int tsDen) {
            this.humanize = humanize;
            try {
                sequence = new Sequence(Sequence.PPQ, ppq);
                track = sequence.createTrack();

                int mpq = 60_000_000 / tempo;
                byte[] tempoBytes = new byte[]{
                        (byte) ((mpq >> 16) & 0xFF),
                        (byte) ((mpq >> 8) & 0xFF),
                        (byte) (mpq & 0xFF)
                };
                MetaMessage tempoMsg = new MetaMessage();
                tempoMsg.setMessage(0x51, tempoBytes, 3);
                track.add(new MidiEvent(tempoMsg, 0));

                byte[] tsBytes = new byte[]{(byte) tsNum, (byte) (31 - Integer.numberOfLeadingZeros(tsDen)), 24, 8};
                MetaMessage tsMsg = new MetaMessage();
                tsMsg.setMessage(0x58, tsBytes, 4);
                track.add(new MidiEvent(tsMsg, 0));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void window(double start, double end) {
            winStart = start;
            winEnd = end;
        }

        public void add(int pitch, double t, double dur, int vel, String tag) {
            if (t < winStart - 1e-6 || t >= winEnd - 1e-6) return;
            String key = pitch + ":" + String.format(Locale.US, "%.4f", t);
            if (used.contains(key)) return;
            used.add(key);

            String seed = String.format(Locale.US, "%d:%.4f:%s", pitch, t, tag);
            int v = jitter(seed, vel, humanize);

            long tickOn = (long) (t * ppq);
            long tickOff = (long) ((t + dur) * ppq);

            try {
                ShortMessage onMsg = new ShortMessage();
                onMsg.setMessage(ShortMessage.NOTE_ON, channel, pitch, v);
                track.add(new MidiEvent(onMsg, tickOn));

                ShortMessage offMsg = new ShortMessage();
                offMsg.setMessage(ShortMessage.NOTE_OFF, channel, pitch, 0);
                track.add(new MidiEvent(offMsg, tickOff));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void write(String path) {
            try {
                MidiSystem.write(sequence, 1, new File(path));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static double swingTime(double beatPos, double swing) {
        if (swing <= 0) return beatPos;
        double sixteenths = beatPos * 4.0;
        if (Math.abs(sixteenths - Math.round(sixteenths)) < 1e-6 && Math.round(sixteenths) % 2 == 1) {
            return beatPos + (swing * 0.25);
        }
        return beatPos;
    }

    public static void hats8ths(DrumWriter w, double t0, double swing, boolean heavy, boolean openAnds) {
        for (int i = 0; i < 8; i++) {
            double raw = t0 + i * 0.5;
            double t = swingTime(raw, swing);
            if (openAnds && (i == 3 || i == 7)) {
                w.add(OPEN_HH, t, 0.32, heavy ? 94 : 80, "oh");
                continue;
            }
            boolean on = (i % 2 == 0);
            int v = heavy ? (on ? 100 : 70) : (on ? 84 : 58);
            w.add(CLOSED_HH, t, 0.36, v, "hh");
        }
    }

    public static void hats16ths(DrumWriter w, double t0, double swing, Integer openOn) {
        for (int i = 0; i < 16; i++) {
            double raw = t0 + i * 0.25;
            if (openOn != null && i == openOn) {
                w.add(OPEN_HH, swingTime(raw, swing), 0.28, 88, "oh");
                continue;
            }
            if (openOn != null && i == openOn + 1) continue;
            double t = swingTime(raw, swing);
            int v = (i % 4 == 0) ? 86 : ((i % 2 == 0) ? 68 : 50);
            w.add(CLOSED_HH, t, 0.16, v, "hh16");
        }
    }

    public static void hatsOffbeat(DrumWriter w, double t0) {
        for (int i = 0; i < 4; i++) {
            w.add(CLOSED_HH, t0 + i, 0.2, 40, "tick");
            w.add(OPEN_HH, t0 + i + 0.5, 0.4, 92, "oh");
        }
    }

    public static void hatsQuarters(DrumWriter w, double t0, int v) {
        for (int i = 0; i < 4; i++) {
            w.add(CLOSED_HH, t0 + i, 0.65, i % 2 == 0 ? v : v - 12, "hq");
        }
    }

    public static void ride8ths(DrumWriter w, double t0, double swing) {
        for (int i = 0; i < 8; i++) {
            double t = swingTime(t0 + i * 0.5, swing);
            w.add(RIDE, t, 0.4, i % 2 == 0 ? 96 : 68, "rd");
        }
        w.add(RIDE_BELL, t0, 0.25, 84, "bell");
    }

    public static void grooveKickSnare(DrumWriter w, double t0, String rawStyle, String section, int intensity) {
        String s = resolveStyle(rawStyle);
        int loud = 110 + intensity;
        int sn = Math.min(127, 108 + intensity);
        int kickV = Math.min(127, loud + 4);

        if (Arrays.asList("hard_rock", "rock").contains(s)) {
            w.add(KICK, t0 + 0.0, 0.42, kickV, "k");
            w.add(KICK, t0 + 2.0, 0.38, kickV - 6, "k");
            if (section.equals("verse")) w.add(KICK, t0 + 2.5, 0.26, kickV - 16, "k");
            else if (section.equals("drive")) { w.add(KICK, t0 + 0.5, 0.24, kickV - 20, "k"); w.add(KICK, t0 + 2.5, 0.24, kickV - 14, "k"); }
            else w.add(KICK, t0 + 3.5, 0.22, kickV - 12, "k");
            w.add(SNARE, t0 + 1.0, 0.38, sn, "s"); w.add(SNARE, t0 + 3.0, 0.38, sn, "s");

        } else if (s.equals("metal")) {
            double[] offsets = {0.0, 0.5, 1.5, 2.0, 2.5, 3.5};
            for (double off : offsets) w.add(KICK, t0 + off, 0.2, (off == 0.0 || off == 2.0) ? kickV : kickV - 10, "k");
            w.add(SNARE, t0 + 1.0, 0.32, sn, "s"); w.add(SNARE, t0 + 3.0, 0.32, sn, "s");

        } else if (s.equals("doom_metal")) {
            w.add(KICK, t0 + 0.0, 0.5, kickV, "k");
            if (section.equals("chorus")) w.add(KICK, t0 + 1.5, 0.3, kickV - 10, "k");
            w.add(SNARE, t0 + 2.0, 0.5, sn, "s");

        } else if (s.equals("thrash_metal")) {
            double[] offsets = {0.0, 1.5, 2.0, 3.5};
            for (double off : offsets) w.add(KICK, t0 + off, 0.15, kickV, "k");
            w.add(SNARE, t0 + 1.0, 0.2, sn, "s"); w.add(SNARE, t0 + 3.0, 0.2, sn, "s");

        } else if (s.equals("pop_punk") || s.equals("punk")) {
            w.add(KICK, t0 + 0.0, 0.3, kickV, "k");
            w.add(KICK, t0 + 2.0, 0.3, kickV, "k");
            if (!section.equals("verse") || s.equals("pop_punk")) {
                w.add(KICK, t0 + 1.5, 0.2, kickV - 18, "k");
                w.add(KICK, t0 + 2.5, 0.2, kickV - 14, "k");
            }
            w.add(SNARE, t0 + 1.0, 0.28, sn, "s"); w.add(SNARE, t0 + 3.0, 0.28, sn, "s");

        } else if (s.equals("indie_rock")) {
            w.add(KICK, t0 + 0.5, 0.3, kickV - 10, "k");
            w.add(KICK, t0 + 1.5, 0.3, kickV - 10, "k");
            w.add(KICK, t0 + 2.5, 0.3, kickV - 10, "k");
            w.add(SNARE, t0 + 1.0, 0.3, sn - 10, "s"); w.add(SNARE, t0 + 3.0, 0.3, sn - 10, "s");

        } else if (Arrays.asList("house", "disco", "techno", "trance", "synthwave", "gabber").contains(s)) {
            int localKickV = s.equals("gabber") ? 127 : kickV;
            for (int i = 0; i < 4; i++) w.add(KICK, t0 + i, 0.4, localKickV, "k");

            if (s.equals("disco") || s.equals("synthwave")) {
                w.add(SNARE, t0 + 1.0, 0.35, sn, "s"); w.add(SNARE, t0 + 3.0, 0.35, sn, "s");
            } else if (!s.equals("gabber")) {
                w.add(CLAP, t0 + 1.0, 0.35, sn, "cl"); w.add(CLAP, t0 + 3.0, 0.35, sn, "cl");
                if (!s.equals("trance")) {
                    w.add(SNARE, t0 + 1.0, 0.2, sn - 40, "s"); w.add(SNARE, t0 + 3.0, 0.2, sn - 40, "s");
                }
            }

        } else if (s.equals("dubstep")) {
            w.add(KICK, t0 + 0.0, 0.5, kickV, "k");
            w.add(SNARE, t0 + 2.0, 0.5, sn, "s");
            if (section.equals("chorus")) w.add(KICK, t0 + 3.5, 0.2, kickV - 20, "k");

        } else if (s.equals("uk_garage")) {
            w.add(KICK, t0 + 0.0, 0.3, kickV, "k");
            w.add(KICK, t0 + 1.75, 0.2, kickV - 15, "k");
            w.add(KICK, t0 + 2.5, 0.2, kickV - 10, "k");
            w.add(SNARE, t0 + 1.0, 0.3, sn, "s"); w.add(SNARE, t0 + 3.0, 0.3, sn, "s");

        } else if (s.equals("breakbeat")) {
            w.add(KICK, t0 + 0.0, 0.3, kickV, "k"); w.add(KICK, t0 + 2.0, 0.3, kickV, "k");
            w.add(KICK, t0 + 2.5, 0.3, kickV - 10, "k");
            w.add(SNARE, t0 + 1.0, 0.3, sn, "s"); w.add(SNARE, t0 + 3.0, 0.3, sn, "s");
            w.add(SNARE, t0 + 1.75, 0.1, sn - 40, "g"); w.add(SNARE, t0 + 3.25, 0.1, sn - 30, "g");

        } else if (Arrays.asList("hiphop", "boom_bap").contains(s)) {
            w.add(KICK, t0 + 0.0, 0.4, kickV, "k");
            w.add(KICK, t0 + 1.5, 0.3, kickV - 10, "k");
            w.add(KICK, t0 + 2.5, 0.28, kickV - 8, "k");
            w.add(SNARE, t0 + 1.0, 0.35, sn, "s"); w.add(SNARE, t0 + 3.0, 0.35, sn, "s");

        } else if (s.equals("drill")) {
            w.add(KICK, t0 + 0.0, 0.4, kickV, "k");
            w.add(KICK, t0 + 1.5, 0.3, kickV - 5, "k");
            w.add(SNARE, t0 + 2.5, 0.4, sn, "s");

        } else if (s.equals("trap")) {
            w.add(KICK, t0 + 0.0, 0.4, kickV, "k");
            w.add(KICK, t0 + 1.5, 0.3, kickV - 5, "k");
            w.add(KICK, t0 + 2.5, 0.3, kickV - 5, "k");
            w.add(CLAP, t0 + 1.0, 0.3, sn, "cl"); w.add(CLAP, t0 + 3.0, 0.3, sn, "cl");

        } else if (s.equals("reggaeton")) {
            for (int i = 0; i < 4; i++) w.add(KICK, t0 + i, 0.4, kickV, "k");
            double[] dembow = {0.75, 1.5, 2.75, 3.5};
            for (double d : dembow) w.add(SNARE, t0 + d, 0.2, sn, "s");

        } else if (s.equals("samba")) {
            double[] surdo = {0.0, 1.5, 2.0, 3.5};
            for (double off : surdo) w.add(KICK, t0 + off, 0.3, kickV - 10, "k");
            w.add(SIDESTICK, t0 + 1.0, 0.2, sn - 15, "ss"); w.add(SIDESTICK, t0 + 3.0, 0.2, sn - 15, "ss");
            w.add(SNARE, t0 + 1.25, 0.1, sn - 50, "g"); w.add(SNARE, t0 + 3.25, 0.1, sn - 50, "g");

        } else if (s.equals("afrobeat")) {
            w.add(KICK, t0 + 0.0, 0.3, kickV, "k");
            w.add(KICK, t0 + 1.5, 0.2, kickV - 10, "k");
            w.add(KICK, t0 + 2.5, 0.2, kickV - 10, "k");
            w.add(SIDESTICK, t0 + 1.0, 0.2, sn, "ss");
            w.add(SIDESTICK, t0 + 2.0, 0.2, sn, "ss");
            w.add(SIDESTICK, t0 + 3.75, 0.2, sn, "ss");

        } else if (s.equals("ska")) {
            w.add(KICK, t0 + 0.0, 0.3, kickV, "k");
            w.add(KICK, t0 + 2.0, 0.3, kickV, "k");
            w.add(SNARE, t0 + 1.0, 0.3, sn, "s"); w.add(SNARE, t0 + 3.0, 0.3, sn, "s");

        } else if (s.equals("blues_shuffle")) {
            w.add(KICK, t0 + 0.0, 0.3, kickV, "k");
            w.add(KICK, t0 + 2.0, 0.3, kickV, "k");
            w.add(SNARE, t0 + 1.0, 0.3, sn, "s"); w.add(SNARE, t0 + 3.0, 0.3, sn, "s");

        } else if (s.equals("funk")) {
            w.add(KICK, t0 + 0.0, 0.28, kickV, "k");
            w.add(KICK, t0 + 1.75, 0.18, kickV - 12, "k");
            if (!section.equals("verse")) w.add(KICK, t0 + 2.5, 0.18, kickV - 20, "k");
            w.add(SNARE, t0 + 1.0, 0.28, sn, "s"); w.add(SNARE, t0 + 3.0, 0.28, sn, "s");
            w.add(SNARE, t0 + 2.75, 0.1, 42, "g"); w.add(SNARE, t0 + 3.75, 0.1, 38, "g");

        } else if (s.equals("pop") || s.equals("country")) {
            w.add(KICK, t0 + 0.0, 0.4, kickV, "k");
            w.add(KICK, t0 + 2.0, 0.35, kickV - 8, "k");
            if (!section.equals("verse")) w.add(KICK, t0 + 2.5, 0.22, kickV - 18, "k");
            w.add(SNARE, t0 + 1.0, 0.35, sn, "s"); w.add(SNARE, t0 + 3.0, 0.35, sn, "s");

        } else if (s.equals("jazz") || s.equals("neo_soul") || s.equals("lofi")) {
            w.add(KICK, t0 + 0.0, 0.2, kickV - 20, "k");
            if (!section.equals("verse")) w.add(KICK, t0 + 2.5, 0.2, kickV - 25, "k");
            w.add(SIDESTICK, t0 + 1.0, 0.2, sn - 15, "ss"); w.add(SIDESTICK, t0 + 3.0, 0.2, sn - 15, "ss");

        } else if (s.equals("reggae")) {
            w.add(KICK, t0 + 2.0, 0.4, kickV, "k");
            w.add(SIDESTICK, t0 + 2.0, 0.4, sn, "ss");
            if (section.equals("chorus")) w.add(KICK, t0 + 0.0, 0.2, kickV - 20, "k");

        } else if (s.equals("bossa_nova")) {
            double[] kickOffs = {0.0, 1.5, 2.0, 3.5};
            for (double off : kickOffs) w.add(KICK, t0 + off, 0.3, kickV - 10, "k");
            double[] stickOffs = {0.0, 1.5, 2.5};
            for (double off : stickOffs) w.add(SIDESTICK, t0 + off, 0.2, sn - 5, "ss");

        } else if (s.equals("dnb")) {
            w.add(KICK, t0 + 0.0, 0.2, kickV, "k"); w.add(KICK, t0 + 1.5, 0.2, kickV, "k");
            if (section.equals("chorus")) w.add(KICK, t0 + 2.5, 0.15, kickV - 10, "k");
            w.add(SNARE, t0 + 1.0, 0.2, sn, "s"); w.add(SNARE, t0 + 3.0, 0.2, sn, "s");
        }
    }

    public static void fill(DrumWriter w, double t0, String size, int intensity) {
        int sn = Math.min(127, 108 + intensity);
        if (size.equals("small")) {
            w.add(KICK, t0 + 0.0, 0.35, 120, "k");
            w.add(SNARE, t0 + 1.0, 0.3, sn, "s");
            hats8ths(w, t0, 0.0, true, false);
            double[] offs = {2.0, 2.25, 2.5, 2.75, 3.0, 3.25, 3.5, 3.75};
            int[] pitches = {SNARE, SNARE, HIGH_TOM, MID_TOM, MID_TOM, LOW_TOM, FLOOR_TOM, SNARE};
            for (int i = 0; i < offs.length; i++) w.add(pitches[i], t0 + offs[i], 0.18, sn - 12 + i, "f");
            w.add(KICK, t0 + 3.75, 0.18, 110, "k");
        } else if (size.equals("med")) {
            w.add(KICK, t0 + 0.0, 0.3, 120, "k"); w.add(SNARE, t0 + 1.0, 0.25, sn, "s");
            w.add(CLOSED_HH, t0 + 0.0, 0.28, 88, "hh"); w.add(CLOSED_HH, t0 + 0.5, 0.28, 66, "hh"); w.add(CLOSED_HH, t0 + 1.0, 0.28, 88, "hh");
            double[] offs = {1.5, 1.75, 2.0, 2.25, 2.5, 2.75, 3.0, 3.25, 3.5, 3.75};
            int[] pitches = {HIGH_TOM, HIGH_TOM, MID_TOM, MID_TOM, LOW_TOM, FLOOR_TOM, SNARE, SNARE, SNARE, KICK};
            for (int i = 0; i < offs.length; i++) w.add(pitches[i], t0 + offs[i], 0.18, sn - 10 + i, "f");
        } else {
            w.add(CRASH, t0 + 0.0, 1.4, 78, "cr");
            w.add(KICK, t0 + 0.0, 0.28, 124, "k"); w.add(KICK, t0 + 2.0, 0.28, 118, "k");
            double[] offs = {0.00, 0.25, 0.50, 0.75, 1.00, 1.25, 1.50, 1.75, 2.00, 2.25, 2.50, 2.625, 2.75, 3.00, 3.25, 3.50, 3.75};
            int[] pitches = {SNARE, SNARE, HIGH_TOM, HIGH_TOM, MID_TOM, MID_TOM, LOW_TOM, FLOOR_TOM, SNARE, HIGH_TOM, MID_TOM, MID_TOM, LOW_TOM, FLOOR_TOM, SNARE, SNARE, SNARE};
            for (int i = 0; i < offs.length; i++) w.add(pitches[i], t0 + offs[i], 0.14, Math.min(127, sn - 8 + i), "f");
            w.add(KICK, t0 + 3.75, 0.18, 120, "k");
        }
    }

    public static void arrange(DrumWriter w, String rawStyle, int bars, double swing, int intensity,
                               String hatsMode, boolean crashes, boolean fills, boolean halfTime) {
        arrange(w, rawStyle, bars, swing, intensity, hatsMode, crashes, fills, halfTime, 4, 4);
    }

    /**
     * The bars in a time signature. A simple meter (3/4, 5/4, 7/8) plays the style's 4/4 bar cut to
     * the bar's length, a longer bar going on from the pattern's start (5/4: one more beat); a fill
     * ends on the bar's last beat. A compound meter (6/8, 9/8, 12/8) has its own dotted-quarter feel.
     */
    public static void arrange(DrumWriter w, String rawStyle, int bars, double swing, int intensity,
                               String hatsMode, boolean crashes, boolean fills, boolean halfTime, int tsNum, int tsDen) {
        String s = resolveStyle(rawStyle);
        double barQ = tsNum * 4.0 / tsDen;
        boolean compound = tsDen == 8 && tsNum % 3 == 0 && tsNum >= 6;

        for (int bar = 0; bar < bars; bar++) {
            double t0 = bar * barQ;
            double phase = (double) bar / Math.max(1, bars);

            boolean isFill = false;
            String size = "small";
            if (fills && bars >= 4) {
                Set<Integer> marks = new HashSet<>(Arrays.asList(bars / 4 - 1, bars / 2 - 1, (3 * bars) / 4 - 1, bars - 1));
                if (marks.contains(bar) && bar >= 0) {
                    isFill = true;
                    if (bar == bars - 1 || bar == (3 * bars) / 4 - 1) size = "big";
                    else if (bar == bars / 2 - 1) size = "med";
                }
            }

            String section = "verse";
            if (phase >= 0.75 && halfTime) section = "half";
            else if (phase >= 0.5) section = "chorus";
            else if (phase >= 0.25) section = "drive";

            if (isFill && !s.equals("gabber") && compound) {
                // A compound bar keeps its groove for the first half and fills the second, in its own pulse
                // (as Pulsekit's 6/8 and 12/8 fills do).
                int pulses = tsNum / 3;
                double from = t0 + (pulses - Math.max(1, pulses / 2)) * 1.5;
                w.window(t0, from);
                compoundBar(w, t0, tsNum, bar, s, section, swing, intensity, crashes);
                w.window(from, t0 + barQ);
                compoundFill(w, from, t0 + barQ, size, intensity);
                if (bar == bars - 1) {
                    w.add(CRASH, t0 + barQ - 0.01, 2.2, 124, "out");
                    w.add(CRASH2, t0 + barQ - 0.01, 2.2, 108, "out2");
                    w.add(KICK, t0 + barQ - 0.01, 0.7, 127, "outk");
                }
                w.window(-1e9, 1e9);
                continue;
            }

            if (isFill && !s.equals("gabber")) {
                // A bar longer than 4/4 plays its groove first; the fill takes the last four beats (or the whole bar).
                if (barQ > 4) {
                    w.window(t0, t0 + barQ - 4);
                    if (compound) compoundBar(w, t0, tsNum, bar, s, section, swing, intensity, crashes);
                    else playBar(w, t0, bar, s, rawStyle, section, swing, intensity, hatsMode, crashes, true);
                }
                w.window(t0, t0 + barQ);
                fill(w, t0 + barQ - 4, size, intensity);
                if (bar == bars - 1) {
                    w.add(CRASH, t0 + barQ - 0.01, 2.2, 124, "out");
                    w.add(CRASH2, t0 + barQ - 0.01, 2.2, 108, "out2");
                    w.add(KICK, t0 + barQ - 0.01, 0.7, 127, "outk");
                }
                w.window(-1e9, 1e9);
                continue;
            }

            w.window(t0, t0 + barQ);
            if (compound) {
                compoundBar(w, t0, tsNum, bar, s, section, swing, intensity, crashes);
            } else {
                for (double start = t0; start < t0 + barQ - 1e-6; start += 4.0) {
                    playBar(w, start, bar, s, rawStyle, section, swing, intensity, hatsMode, crashes, start == t0);
                }
            }
            w.window(-1e9, 1e9);
        }
    }

    /** One 4/4 bar of the style from t0: kick and snare, the bar's crash (when `first`), and the hats or ride. */
    static void playBar(DrumWriter w, double t0, int bar, String s, String rawStyle, String section, double swing,
                        int intensity, String hatsMode, boolean crashes, boolean first) {
            grooveKickSnare(w, t0, rawStyle, section, intensity);

            if (first && crashes && (bar == 0 || (section.equals("chorus") && Math.abs((bar * 4) % 8) < 1e-6))) {
                w.add(CRASH, t0, 1.7, (bar == 0 || section.equals("chorus")) ? 118 : 96, "cr");
            }

            List<String> heavyStyles = Arrays.asList("hard_rock", "metal", "punk", "thrash_metal", "pop_punk");
            if (crashes && section.equals("chorus") && heavyStyles.contains(s) && bar % 4 == 2) {
                w.add(CHINA, t0 + 3.0, 0.9, 96, "ch");
            }

            if (s.equals("jazz") || s.equals("blues_shuffle")) {
                ride8ths(w, t0, swing);
                w.add(PEDAL_HH, t0 + 1.0, 0.2, 80, "phh"); w.add(PEDAL_HH, t0 + 3.0, 0.2, 80, "phh");
            } else if (s.equals("gabber")) {
                w.add(OPEN_HH, t0 + 0.5, 0.4, 100, "oh"); w.add(OPEN_HH, t0 + 2.5, 0.4, 100, "oh");
            } else if (section.equals("chorus") && (heavyStyles.contains(s) || s.equals("rock") || s.equals("doom_metal"))) {
                ride8ths(w, t0, swing);
            } else if (hatsMode.equals("16ths") || (Arrays.asList("funk", "hiphop", "dnb", "trap", "uk_garage", "synthwave", "breakbeat", "drill", "neo_soul", "samba", "afrobeat", "boom_bap").contains(s) && !section.equals("verse"))) {
                hats16ths(w, t0, swing, bar % 2 != 0 ? 14 : null);
            } else if (hatsMode.equals("offbeat") || Arrays.asList("house", "disco", "techno", "trance", "reggaeton").contains(s)) {
                hatsOffbeat(w, t0);
            } else {
                hats8ths(w, t0, swing, intensity >= 6, section.equals("drive") && bar % 2 == 1);
            }
    }

    /**
     * A fill over the second half of a compound bar, from `start` to `end` (beats), in eighths:
     * small, rack, mid and floor toms down in eighths with a 16th floor tom into the next bar (Pulsekit's
     * Toms fill); med, a snare roll in 16ths, louder to the end (its Snare roll); big, the toms with a
     * crash and a kick on each pulse.
     */
    static void compoundFill(DrumWriter w, double start, double end, String size, int intensity) {
        int sn = Math.min(127, 108 + intensity);
        int eighths = (int) Math.round((end - start) / 0.5);
        if (size.equals("med")) {
            int n = eighths * 2;
            for (int i = 0; i < n; i++) w.add(SNARE, start + i * 0.25, 0.12, 64 + (63 * i) / Math.max(1, n - 1), "rl");
            w.add(KICK, start, 0.3, 120, "k");
            w.add(KICK, end - 0.25, 0.2, 124, "k");
            return;
        }
        int[] toms = {HIGH_TOM, MID_TOM, FLOOR_TOM};
        for (int k = 0; k < eighths; k++) {
            w.add(toms[Math.min(2, (k * 3) / Math.max(1, eighths))], start + k * 0.5, 0.2, Math.min(127, sn - 16 + (24 * k) / Math.max(1, eighths - 1)), "f");
        }
        w.add(FLOOR_TOM, end - 0.25, 0.18, 120, "f");
        w.add(KICK, start, 0.3, 110, "k");
        if (size.equals("big")) {
            w.add(CRASH, start, 1.4, 118, "cr");
            for (double pt = start; pt < end - 1e-6; pt += 1.5) w.add(KICK, pt, 0.3, 120, "k");
        }
    }

    /**
     * A compound-meter bar (6/8, 9/8, 12/8): pulses of a dotted quarter (three eighths). The kick on
     * the odd pulses and the snare on the even ones (four-on-the-floor styles: a kick on every pulse
     * and a clap on the even ones), eighths on the hats (the ride in a heavy style's chorus) with the
     * pulses accented, and a pickup kick before the snare outside the verse.
     */
    static void compoundBar(DrumWriter w, double t0, int tsNum, int bar, String s, String section, double swing,
                            int intensity, boolean crashes) {
        int pulses = tsNum / 3;
        int kickV = Math.min(127, 114 + intensity);
        int sn = Math.min(127, 108 + intensity);
        boolean floor = Arrays.asList("house", "disco", "techno", "trance", "synthwave", "gabber", "reggaeton").contains(s);
        boolean heavy = Arrays.asList("hard_rock", "metal", "punk", "thrash_metal", "pop_punk", "rock", "doom_metal").contains(s);
        for (int p = 0; p < pulses; p++) {
            double pt = t0 + p * 1.5;
            if (floor) {
                w.add(KICK, pt, 0.4, p == 0 ? kickV : kickV - 6, "k");
                if (p % 2 == 1) w.add(CLAP, pt, 0.35, sn, "cl");
            } else if (p % 2 == 0) {
                w.add(KICK, pt, 0.4, p == 0 ? kickV : kickV - 8, "k");
                if (!section.equals("verse")) w.add(KICK, pt + 1.0, 0.22, kickV - 18, "k");
            } else {
                w.add(SNARE, pt, 0.35, sn, "s");
            }
        }
        boolean ride = heavy && section.equals("chorus");
        for (int i = 0; i < tsNum; i++) {
            double t = t0 + i * 0.5;
            int v = i % 3 == 0 ? (ride ? 96 : 90) : (ride ? 66 : 60);
            w.add(ride ? RIDE : CLOSED_HH, swingTime(t, swing), ride ? 0.4 : 0.3, v, ride ? "rd" : "hh");
        }
        if (crashes && (bar == 0 || (section.equals("chorus") && bar % 2 == 0))) w.add(CRASH, t0, 1.7, 116, "cr");
    }

    /**
     * A time signature: "3/4", "6/8", "7/8"... (1 to 16 beats of a half, quarter, eighth or sixteenth),
     * or a plain number as SogniMusic takes it: 6, 9 or 12 for /8, else /4. Null when it is neither.
     */
    static int[] timesig(String value) {
        String v = value == null ? "" : value.trim();
        try {
            int slash = v.indexOf('/');
            int num = Integer.parseInt(slash < 0 ? v : v.substring(0, slash).trim());
            int den = slash < 0 ? (num == 6 || num == 9 || num == 12 ? 8 : 4) : Integer.parseInt(v.substring(slash + 1).trim());
            if (num < 1 || num > 16 || (den != 2 && den != 4 && den != 8 && den != 16)) return null;
            return new int[] {num, den};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static void main(String[] args) {
        String style = "hard_rock";
        Integer tempo = null, bars = null, intensity = null;
        Double swing = null;
        String hats = "auto";
        int humanize = 4;
        boolean fills = true, crashes = true, halfTime = true;
        boolean anyTempo = false;
        int tsNum = 4, tsDen = 4;
        boolean tsGiven = false;
        String output = "drum_track_full_db.mid";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--style": style = args[++i].toLowerCase().replace(" ", "_").replace("-", "_"); break;
                case "--tempo": case "--bpm": tempo = (int) Math.round(Double.parseDouble(args[++i])); break;
                case "--bars": bars = Integer.parseInt(args[++i]); break;
                case "--swing": swing = Double.parseDouble(args[++i]); break;
                case "--intensity": intensity = Integer.parseInt(args[++i]); break;
                case "--hats": hats = args[++i]; break;
                case "--humanize": humanize = Integer.parseInt(args[++i]); break;
                case "--fills": fills = true; break;
                case "--no-fills": fills = false; break;
                case "--crashes": crashes = true; break;
                case "--no-crashes": crashes = false; break;
                case "--half-time": halfTime = true; break;
                case "--no-half-time": halfTime = false; break;
                case "--any-tempo": anyTempo = true; break;
                case "--timesig": {
                    int[] ts = timesig(args[++i]);
                    if (ts == null) {
                        System.out.println("Failed: --timesig is beats over a note value, such as 3/4, 6/8 or 7/8 (also 3 for 3/4, 6 for 6/8)");
                        return;
                    }
                    tsNum = ts[0];
                    tsDen = ts[1];
                    tsGiven = true;
                    break;
                }
                case "--output": case "-o": output = args[++i]; break;
                case "-h": case "--help": System.out.println(USAGE); return;
                default:
                    // The output file can also be given alone (PyJav names it after the run).
                    if (!args[i].startsWith("-")) { output = args[i]; break; }
                    System.out.println("Unknown argument: " + args[i] + "\n" + USAGE);
                    return;
            }
        }

        DefaultArgs d = DEFAULTS.getOrDefault(style, DEFAULTS.get("hard_rock"));
        int fTempo = clamp(tempo != null ? tempo : d.tempo, 40, 300);
        // A tempo given (--tempo, or PyJav's --bpm) stays in the style's range from the style database,
        // unless --any-tempo. A style the database does not have keeps any tempo.
        DefaultArgs known = DEFAULTS.get(style);
        if (known != null && known.minTempo > 0 && known.maxTempo >= known.minTempo && !anyTempo
                && (fTempo < known.minTempo || fTempo > known.maxTempo)) {
            int inRange = clamp(fTempo, known.minTempo, known.maxTempo);
            System.out.printf(Locale.US, "Tempo %d is outside %s's range %d-%d: using %d (--any-tempo keeps %d)\n",
                    fTempo, style, known.minTempo, known.maxTempo, inRange, fTempo);
            fTempo = inRange;
        }
        int fBars = clamp(bars != null ? bars : d.bars, 1, 64);
        // A swing above 1 is a percent (PyJav passes the app's swing, e.g. 12): 12 means 0.12.
        double fSwing = clampDouble(swing != null ? (swing > 1 ? swing / 100.0 : swing) : d.swing, 0.0, 0.5);
        int fIntensity = clamp(intensity != null ? intensity : d.intensity, 1, 10);
        String fHats = hats.equals("auto") ? d.hats : hats;
        int fHumanize = clamp(humanize, 0, 12);

        // A style in another meter (Ballad 6/8, Waltz) plays in it unless --timesig says otherwise.
        if (!tsGiven && known != null) {
            tsNum = known.tsNum;
            tsDen = known.tsDen;
        }
        DrumWriter w = new DrumWriter(fTempo, fHumanize, tsNum, tsDen);
        arrange(w, style, fBars, fSwing, fIntensity, fHats, crashes, fills, halfTime, tsNum, tsDen);
        // In PyJav (pulsekit.work set) a bare name goes in its work folder, so the MIDI is imported.
        String work = System.getProperty("pulsekit.work");
        if (work != null && work.length() > 0 && !new File(output).isAbsolute()) output = new File(work, output).getPath();
        w.write(output);

        System.out.printf(Locale.US, "Wrote %s (style=%s base=%s tempo=%d timesig=%d/%d bars=%d swing=%.2f intensity=%d hats=%s)\n",
                new File(output).getName(), style, resolveStyle(style), fTempo, tsNum, tsDen, fBars, fSwing, fIntensity, fHats);
    }
}
