package pulsekit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Genre fingerprints that map onto Pulsekit's playable kits. One kit per file set. */
public final class StyleDb {
  private StyleDb() {}

  /** name|kit|bpm|lo|hi|four|back|hats|dkick, and for a style not in 4/4 its time signature (|6/8) */
  private static final String[] HINTS = {
    "House|house|124|118|130|1|0.9|8|0",
    "Deep House|house|122|116|126|1|0.7|8|0",
    "Tech House|house|125|120|130|1|0.8|10|0",
    "Progressive House|house|126|122|132|1|0.25|8|0",
    "Funky House|house|122|118|128|1|0.4|8|0",
    "Disco|house|120|110|126|1|0.55|8|0",
    "Nu Disco|house|118|112|124|1|0.5|8|0",
    "Chicago House|house|124|118|128|1|0.3|8|0",
    "French House|house|122|116|126|1|0.4|8|0",
    "Acid House|house|126|120|132|1|0.2|8|0",
    "Afro House|house|120|114|126|1|0.35|8|0",
    "Organic House|house|122|116|126|1|0.2|6|0",
    "Piano House|house|124|118|128|1|0.4|8|0",
    "Eurodance|house|136|128|142|1|0.5|8|0",
    "Italo Disco|house|128|118|136|1|0.45|8|0",
    "Amapiano|house|112|108|118|1|0.3|6|0",
    "Gqom|house|126|120|132|1|0.15|8|0",
    "EDM|house|128|124|136|1|0.4|8|0",
    "Big Room|house|128|124|132|1|0.35|8|0",
    "Future House|house|126|122|130|1|0.3|10|0",
    "Electro House|house|128|124|132|1|0.25|8|0",
    "Slap House|house|126|122|130|1|0.4|8|0",
    "Techno|techno|132|126|140|1|0.15|16|0",
    "Melodic Techno|techno|130|124|136|1|0.2|12|0",
    "Industrial Techno|techno|138|130|150|1|0.1|16|0",
    "Hard Techno|techno|142|136|155|1|0.1|16|0",
    "Minimal Techno|techno|128|124|134|1|0.1|8|0",
    "Detroit Techno|techno|130|124|136|1|0.2|12|0",
    "Schranz|techno|150|140|160|1|0.05|16|0",
    "Trance|techno|138|130|145|1|0.25|8|0",
    "Psytrance|techno|145|138|150|1|0.2|16|0",
    "Hardstyle|techno|150|140|160|1|0.3|8|0",
    "Gabber|techno|180|165|200|1|0.1|16|0",
    "EBM|techno|130|120|140|1|0.2|8|0",
    "UKG|ukg|132|126|138|0.55|0.7|8|0",
    "2-Step|ukg|132|126|138|0.4|0.75|8|0",
    "Speed Garage|ukg|134|128|140|0.6|0.5|8|0",
    "Bassline|ukg|136|130|142|0.7|0.45|10|0",
    "Broken Beat|ukg|128|118|136|0.35|0.5|8|0",
    "Grime|ukg|140|130|150|0.4|0.6|10|0",
    "Hip-Hop|hiphop|92|80|100|0.25|0.9|6|0",
    "East Coast|hiphop|94|84|102|0.3|0.9|6|0",
    "West Coast|hiphop|98|88|108|0.35|0.85|6|0",
    "G-Funk|hiphop|96|88|104|0.3|0.85|5|0",
    "Alternative Hip-Hop|hiphop|90|80|100|0.25|0.8|6|0",
    "Jazz Rap|hiphop|88|78|98|0.2|0.85|5|0",
    "Crunk|hiphop|80|72|90|0.4|0.9|8|0",
    "Hyphy|hiphop|110|100|118|0.45|0.8|8|0",
    "Boom Bap|boombap|88|80|96|0.2|0.95|5|0",
    "Lo-fi Hip-Hop|boombap|84|72|94|0.2|0.85|4|0",
    "90s Hip-Hop|boombap|90|82|98|0.25|0.95|5|0",
    "Golden Age|boombap|88|80|96|0.2|0.9|5|0",
    "Trap|trap|140|130|160|0.35|0.7|14|0",
    "Drill|trap|142|132|155|0.3|0.75|14|0",
    "UK Drill|trap|140|132|150|0.35|0.7|12|0",
    "Phonk|trap|148|130|165|0.4|0.7|12|0",
    "Cloud Rap|trap|130|118|145|0.25|0.6|10|0",
    "Trap Metal|trap|150|135|170|0.4|0.65|12|0.3",
    "Jersey Club|trap|132|128|140|0.5|0.5|10|0",
    "Dubstep|trap|140|135|150|0.3|0.55|8|0",
    "Riddim|trap|145|138|155|0.35|0.5|8|0",
    "Moombahton|trap|108|102|115|0.7|0.5|8|0",
    "Rock|rock|118|100|130|0.5|1|6|0",
    "Indie Rock|rock|120|105|135|0.45|0.95|6|0",
    "Britpop|rock|118|108|128|0.5|1|6|0",
    "Garage Rock|rock|130|115|145|0.55|1|5|0",
    "Blues Rock|rock|110|90|125|0.4|0.95|5|0",
    "Southern Rock|rock|112|96|124|0.45|0.95|5|0",
    "Classic Rock|rock|116|100|128|0.5|1|6|0",
    "Alternative|rock|122|105|135|0.45|0.95|6|0",
    "Grunge|rock|110|95|125|0.5|1|5|0",
    "Post-Punk|rock|130|115|145|0.4|0.85|8|0",
    "New Wave|rock|128|115|140|0.5|0.8|8|0",
    "Punk|rock|180|160|210|0.55|1|6|0",
    "Pop Punk|rock|175|155|195|0.55|1|8|0",
    "Emo|rock|165|140|185|0.5|0.95|6|0",
    "Country|rock|108|90|122|0.4|0.95|4|0",
    "Americana|rock|102|88|118|0.35|0.9|4|0",
    "Folk|folk|98|78|124|0.4|0.85|4|0",
    "Acoustic Folk|folk|94|76|118|0.25|0.75|3|0",
    "Folk Rock|rock|100|85|115|0.3|0.85|4|0",
    "Hard Rock|hardrock|128|112|145|0.55|1|6|0",
    "Arena Rock|hardrock|124|110|138|0.5|1|6|0",
    "Glam Rock|hardrock|130|115|145|0.5|1|6|0",
    "Stoner Rock|hardrock|70|60|90|0.5|0.9|4|0",
    "Garage Punk|hardrock|160|140|185|0.55|1|5|0",
    "Metal|metal|156|140|180|0.55|1|8|0.4",
    "Thrash|metal|180|160|210|0.6|1|8|0.7",
    "Death Metal|metal|200|170|230|0.5|0.7|8|0.85",
    "Black Metal|metal|180|150|220|0.5|0.5|8|0.8",
    "Doom|metal|70|55|85|0.5|0.9|4|0.2",
    "Sludge|metal|80|60|100|0.5|0.9|4|0.2",
    "Nu Metal|metal|120|100|140|0.5|0.95|8|0.3",
    "Metalcore|metal|160|140|185|0.55|0.95|8|0.6",
    "Power Metal|metal|165|145|185|0.55|1|8|0.5",
    "Folk Metal|metal|150|130|175|0.5|0.9|6|0.4",
    "Industrial Metal|metal|130|115|150|0.55|0.85|10|0.3",
    "Prog Metal|progmetal|148|130|175|0.45|0.8|8|0.85",
    "Djent|progmetal|140|120|165|0.4|0.7|8|0.9",
    "Math Metal|progmetal|150|130|180|0.4|0.75|8|0.8",
    "Progressive Metal|progmetal|144|125|170|0.45|0.8|8|0.8",
    "Rock Ballad|rockballad|72|60|84|0.35|0.9|4|0",
    "Power Ballad|rockballad|74|62|86|0.4|0.95|4|0",
    "Metal Ballad|metalballad|76|64|88|0.4|0.9|4|0.15",
    "Pop Ballad|popballad|70|58|82|0.25|0.8|3|0",
    "Singer-Songwriter|popballad|72|60|88|0.2|0.7|2|0",
    "Acoustic|popballad|76|62|90|0.2|0.65|2|0",
    "Ambient|popballad|70|50|90|0.1|0.2|2|0",
    "Downtempo|popballad|80|70|95|0.3|0.5|4|0",
    "Trip Hop|popballad|85|75|96|0.3|0.7|4|0",
    "Chillout|popballad|82|70|96|0.35|0.5|4|0",
    "Dream Pop|popballad|90|75|105|0.3|0.6|4|0",
    "Shoegaze|rockballad|95|80|115|0.4|0.7|6|0",
    "Post-Rock|rockballad|88|70|110|0.3|0.5|4|0",
    "Funk|funk|108|98|118|0.4|0.7|8|0",
    "P-Funk|funk|104|96|114|0.35|0.65|8|0",
    "Boogie|funk|112|104|120|0.6|0.6|8|0",
    "Neo Soul|funk|90|80|102|0.3|0.8|6|0",
    "R&B|funk|80|70|96|0.3|0.85|6|0",
    "Motown|funk|110|100|122|0.45|0.95|6|0",
    "Go-Go|funk|108|100|118|0.5|0.7|8|0",
    "New Jack Swing|funk|112|104|120|0.4|0.85|8|0",
    "Reggae|funk|78|68|90|0.3|0.6|6|0",
    "Dancehall|funk|100|90|110|0.4|0.55|8|0",
    "Ska|funk|140|120|160|0.5|0.7|8|0",
    "Rocksteady|funk|80|70|90|0.3|0.6|6|0",
    "Dub|funk|76|68|88|0.35|0.5|4|0",
    "Jazz|funk|120|90|160|0.25|0.7|6|0",
    "Swing|funk|140|110|180|0.3|0.8|6|0",
    "Fusion|funk|110|95|130|0.35|0.7|8|0",
    "Gospel|funk|100|85|120|0.4|0.9|6|0",
    "Blues|funk|80|70|100|0.3|0.9|4|0",
    "Latin|latin|100|90|115|0.4|0.6|8|0",
    "Salsa|latin|180|160|200|0.35|0.5|8|0",
    "Samba|latin|100|90|120|0.5|0.4|10|0",
    "Bossa Nova|latin|130|110|150|0.25|0.6|6|0",
    "Mambo|latin|110|100|125|0.4|0.5|8|0",
    "Cumbia|latin|100|90|110|0.5|0.4|6|0",
    "Merengue|latin|140|120|160|0.6|0.4|8|0",
    "Reggaeton|latin|95|88|105|0.5|0.7|8|0",
    "Dembow|latin|100|92|108|0.55|0.65|8|0",
    "Afrobeat|latin|110|100|122|0.5|0.5|8|0",
    "Highlife|latin|120|108|132|0.45|0.5|8|0",
    "Baile Funk|latin|130|120|140|0.5|0.6|8|0",
    "Pop|pop|110|98|122|0.5|0.95|6|0",
    "Synthpop|pop|118|108|128|0.55|0.85|8|0",
    "K-Pop|pop|122|110|132|0.5|0.9|8|0",
    "J-Pop|pop|128|115|138|0.5|0.9|8|0",
    "Dance Pop|pop|120|110|130|0.7|0.8|8|0",
    "Indie Pop|pop|116|100|128|0.4|0.85|6|0",
    "Electropop|pop|120|110|130|0.6|0.8|8|0",
    "City Pop|pop|112|100|122|0.45|0.85|6|0",
    "Breaks|breakbeat|136|120|150|0.3|0.7|8|0",
    "Big Beat|breakbeat|126|115|138|0.4|0.7|8|0",
    "Jungle Breaks|breakbeat|160|150|175|0.3|0.6|10|0",
    "Miami Bass|breakbeat|125|115|135|0.7|0.5|8|0",
    "Ghettotech|breakbeat|145|135|160|0.7|0.4|12|0",
    "DnB|dnb|172|160|180|0.35|0.6|12|0",
    "Jungle|dnb|170|160|180|0.3|0.55|12|0",
    "Liquid DnB|dnb|172|164|178|0.3|0.65|10|0",
    "Neurofunk|dnb|174|166|180|0.35|0.55|14|0",
    "Jump Up|dnb|174|166|180|0.4|0.6|12|0",
    "Drumstep|dnb|160|150|170|0.4|0.6|10|0",
    "Breakcore|dnb|190|170|220|0.3|0.5|14|0",
    "Tropical House|house|118|110|124|1|0.25|8|0",
    "Microhouse|house|126|122|132|1|0.15|8|0",
    "Deep Tech|house|126|122|130|1|0.2|10|0",
    "Jackin House|house|124|118|128|1|0.4|8|0",
    "Tribal House|house|125|118|132|1|0.3|10|0",
    "Melodic House|house|122|116|128|1|0.25|8|0",
    "Fidget House|house|128|124|132|1|0.3|10|0",
    "Dutch House|house|128|124|132|1|0.35|8|0",
    "Complextro|house|128|124|134|1|0.3|8|0",
    "Acid Techno|techno|135|128|145|1|0.1|16|0",
    "Dub Techno|techno|128|122|134|1|0.1|8|0",
    "Peak Time|techno|132|126|140|1|0.15|12|0",
    "Warehouse|techno|130|124|138|1|0.1|12|0",
    "Hardgroove|techno|138|130|148|1|0.15|16|0",
    "UK Funky|ukg|130|124|138|0.5|0.65|8|0",
    "Future Garage|ukg|132|126|140|0.4|0.7|8|0",
    "Night Bass|ukg|128|122|136|0.55|0.6|8|0",
    "Dirty South|hiphop|96|86|108|0.4|0.9|6|0",
    "Memphis Rap|hiphop|90|80|100|0.3|0.9|6|0",
    "Conscious Rap|hiphop|88|78|98|0.2|0.85|5|0",
    "Chopped and Screwed|hiphop|70|60|82|0.3|0.85|4|0",
    "Southern Rap|hiphop|95|85|108|0.35|0.9|6|0",
    "Rage|trap|144|132|160|0.4|0.65|14|0",
    "Plugg|trap|140|128|155|0.3|0.6|12|0",
    "Trap Soul|trap|130|118|142|0.3|0.7|10|0",
    "Twerk|trap|100|92|110|0.5|0.7|10|0",
    "Post-Grunge|rock|118|100|130|0.5|1|6|0",
    "Soft Rock|rock|100|85|115|0.4|0.95|4|0",
    "Heartland|rock|112|96|124|0.45|0.95|5|0",
    "Surf|rock|160|140|180|0.5|1|6|0",
    "Psych Rock|rock|110|90|130|0.4|0.85|6|0",
    "College Rock|rock|120|105|135|0.45|0.95|6|0",
    "Hair Metal|hardrock|128|112|145|0.55|1|6|0",
    "Southern Metal|hardrock|110|95|125|0.5|0.95|5|0",
    "Groove Metal|metal|140|120|160|0.55|1|8|0.5",
    "Deathcore|metal|180|160|210|0.5|0.7|8|0.8",
    "Melodic Death|metal|170|150|195|0.5|0.9|8|0.7",
    "Speed Metal|metal|190|170|220|0.55|1|8|0.75",
    "NWOBHM|metal|150|130|170|0.55|1|6|0.4",
    "Symphonic Metal|metal|155|135|175|0.5|0.9|8|0.45",
    "Technical Death|progmetal|180|160|210|0.45|0.7|8|0.85",
    "Progressive Rock|progmetal|120|90|150|0.4|0.8|6|0.3",
    "Slowcore|popballad|68|55|80|0.15|0.6|2|0",
    "Sadcore|rockballad|70|58|84|0.25|0.75|3|0",
    "Jazz-Funk|funk|108|96|118|0.35|0.7|8|0",
    "Soul|funk|90|78|104|0.3|0.85|6|0",
    "Disco Funk|funk|118|110|126|0.55|0.65|8|0",
    "Acid Jazz|funk|110|98|122|0.3|0.7|6|0",
    "Bachata|latin|130|120|140|0.4|0.55|8|0",
    "Tango|latin|120|110|132|0.35|0.6|6|0",
    "Rumba|latin|100|90|112|0.4|0.5|8|0",
    "Soca|latin|140|128|155|0.55|0.45|8|0",
    "Calypso|latin|116|104|128|0.45|0.5|8|0",
    "Forro|latin|120|108|132|0.5|0.45|8|0",
    "Teen Pop|pop|118|108|128|0.5|0.9|6|0",
    "Adult Contemporary|pop|100|88|112|0.4|0.9|4|0",
    "Hyperpop|pop|140|125|160|0.55|0.75|10|0",
    "Bubblegum|pop|122|112|132|0.55|0.9|8|0",
    "Euro Pop|pop|128|118|138|0.6|0.85|8|0",
    "Nu Skool Breaks|breakbeat|132|120|145|0.35|0.7|8|0",
    "Acid Breaks|breakbeat|136|124|148|0.3|0.65|10|0",
    "Funky Breaks|breakbeat|130|118|142|0.4|0.7|8|0",
    "Darkstep|dnb|174|166|182|0.3|0.5|14|0",
    "Techstep|dnb|174|166|180|0.35|0.55|14|0",
    "Atmospheric DnB|dnb|170|162|178|0.25|0.6|10|0",
    "Sambass|dnb|172|164|180|0.4|0.55|12|0",
    // Styles in another meter: a tenth column, its time signature (MidiDrumGen plays it; others are 4/4).
    "Ballad 6/8|ballad68|60|50|72|0.3|0.9|6|0|6/8",
    "Afro 6/8|latin|110|96|125|0.5|0.5|6|0|6/8",
    "Irish Jig|folk|116|100|130|0.4|0.6|6|0|6/8",
    "Slip Jig|slipjig98|120|100|140|0.4|0.6|9|0|9/8",
    "Slow Blues|blues128|60|48|72|0.3|0.9|12|0|12/8",
    "Doo-Wop|popballad|66|56|78|0.3|0.9|12|0|12/8",
    "Waltz|popballad|96|84|180|0.3|0.6|3|0|3/4",
    "Jazz Waltz|funk|150|120|200|0.3|0.6|6|0|3/4",
  };

  public static String familyOf(String kit) {
    if ("house".equals(kit) || "techno".equals(kit)) return "electronic";
    if ("ukg".equals(kit)) return "ukg";
    if ("rock".equals(kit) || "hardrock".equals(kit)) return "rock";
    if ("metal".equals(kit) || "progmetal".equals(kit)) return "metal";
    if ("rockballad".equals(kit) || "metalballad".equals(kit) || "popballad".equals(kit) || "ballad68".equals(kit)) return "ballad";
    if ("hiphop".equals(kit) || "boombap".equals(kit)) return "hiphop";
    if ("trap".equals(kit)) return "trap";
    if ("funk".equals(kit) || "blues128".equals(kit)) return "funk";
    if ("latin".equals(kit)) return "latin";
    if ("folk".equals(kit) || "slipjig98".equals(kit)) return "folk";
    if ("breakbeat".equals(kit)) return "breaks";
    if ("dnb".equals(kit)) return "dnb";
    return "pop";
  }

  public static String suggest(int[][] cells, int bpm) {
    float four = 0.5f;
    float back = 0.5f;
    int hats = 6;
    int kicks = 3;
    float dkick = bpm >= 145 ? 0.35f : 0f;
    if (cells != null) {
      int kickT = Engine.track("kick");
      int snareT = Engine.track("snare");
      int hatT = Engine.track("chh");
      int dkT = Engine.track("dkick");
      four = (hit(cells, kickT, 0) + hit(cells, kickT, 4) + hit(cells, kickT, 8) + hit(cells, kickT, 12)) / 4f;
      back = (hit(cells, snareT, 4) + hit(cells, snareT, 12)) / 2f;
      hats = count(cells, hatT);
      kicks = count(cells, kickT);
      dkick = Math.max(0f, Math.min(1f, count(cells, dkT) / 8f));
    }
    LinkedHashMap<String, Double> kitScore = new LinkedHashMap<String, Double>();
    for (String row : HINTS) {
      String[] p = row.split("\\|");
      if (p.length < 9) continue;
      String kit = p[1];
      // A groove in another meter (Ballad 6/8) is never guessed for a 4/4 file.
      int[] meter = Engine.styleMeter(kit);
      if (meter[0] != 4 || meter[1] != 4) continue;
      int hbpm = Integer.parseInt(p[2]);
      int lo = Integer.parseInt(p[3]);
      int hi = Integer.parseInt(p[4]);
      float hfour = Float.parseFloat(p[5]);
      float hback = Float.parseFloat(p[6]);
      int hhats = Integer.parseInt(p[7]);
      float hdk = Float.parseFloat(p[8]);
      double s = score(bpm, four, back, hats, kicks, dkick, hbpm, lo, hi, hfour, hback, hhats, hdk);
      Double cur = kitScore.get(kit);
      if (cur == null || s > cur) kitScore.put(kit, s);
    }
    String best = "pop";
    double bestS = -1;
    for (Map.Entry<String, Double> e : kitScore.entrySet()) {
      if (e.getValue() > bestS) {
        bestS = e.getValue();
        best = e.getKey();
      }
    }
    if (("pop".equals(best) || "popballad".equals(best) || "rock".equals(best)
            || "funk".equals(best) || "latin".equals(best))
        && bpm >= 80 && bpm <= 128
        && four <= 0.65f && back >= 0.35f
        && hats <= 8 && kicks <= 4 && dkick < 0.2f) {
      best = "folk";
    }
    return best;
  }

  public static String unify(List<String> kits, List<Double> weights, String fallback) {
    if (kits == null || kits.isEmpty()) return fallback == null || fallback.isEmpty() ? "pop" : fallback;
    LinkedHashMap<String, Double> kitW = new LinkedHashMap<String, Double>();
    LinkedHashMap<String, Double> famW = new LinkedHashMap<String, Double>();
    for (int i = 0; i < kits.size(); i++) {
      String kit = kits.get(i);
      if (kit == null || kit.isEmpty()) kit = fallback;
      double w = i < weights.size() ? Math.max(0, weights.get(i)) : 1;
      Double ck = kitW.get(kit);
      kitW.put(kit, (ck == null ? 0 : ck) + w);
      String fam = familyOf(kit);
      Double cf = famW.get(fam);
      famW.put(fam, (cf == null ? 0 : cf) + w);
    }
    String bestFam = familyOf(fallback);
    double bestFamW = -1;
    for (Map.Entry<String, Double> e : famW.entrySet()) {
      if (e.getValue() > bestFamW) {
        bestFamW = e.getValue();
        bestFam = e.getKey();
      }
    }
    String best = fallback == null || fallback.isEmpty() ? "pop" : fallback;
    double bestW = -1;
    for (Map.Entry<String, Double> e : kitW.entrySet()) {
      if (!bestFam.equals(familyOf(e.getKey()))) continue;
      if (e.getValue() > bestW) {
        bestW = e.getValue();
        best = e.getKey();
      }
    }
    return best;
  }

  public static String fromLabel(String label) {
    if (label == null) return null;
    String want = label.trim().toLowerCase();
    if (want.isEmpty()) return null;
    for (Engine.Style st : Engine.styles().values()) {
      if (want.equals(st.id) || (st.label != null && want.equals(st.label.toLowerCase()))) return st.id;
    }
    for (Row row : rows()) {
      if (row.name != null && want.equals(row.name.trim().toLowerCase())) return row.kit;
    }
    return null;
  }

  /** Index in the A–Z list. -1 when the name is not a database style. */
  public static int indexOf(String label) {
    if (label == null) return -1;
    String want = label.trim();
    if (want.isEmpty()) return -1;
    java.util.List<Row> list = rows();
    for (int i = 0; i < list.size(); i++) {
      Row row = list.get(i);
      if (row.name != null && row.name.equalsIgnoreCase(want)) return i;
    }
    return -1;
  }

  public static int swingOf(String kit) {
    if ("house".equals(kit)) return 12;
    if ("techno".equals(kit) || "hardrock".equals(kit) || "metalballad".equals(kit)) return 6;
    if ("hiphop".equals(kit)) return 22;
    if ("trap".equals(kit) || "ukg".equals(kit)) return 18;
    if ("rock".equals(kit) || "dnb".equals(kit) || "popballad".equals(kit)) return 8;
    if ("metal".equals(kit)) return 4;
    if ("progmetal".equals(kit)) return 2;
    if ("rockballad".equals(kit) || "pop".equals(kit)) return 10;
    if ("funk".equals(kit)) return 28;
    if ("breakbeat".equals(kit)) return 16;
    if ("latin".equals(kit)) return 20;
    if ("boombap".equals(kit)) return 24;
    return 10;
  }

  public static final class Row {
    public final String name;
    public final String kit;
    public final int bpm;
    public final float four;
    public final float back;
    public final int hats;
    public final float dkick;

    Row(String name, String kit, int bpm, float four, float back, int hats, float dkick) {
      this.name = name;
      this.kit = kit;
      this.bpm = bpm;
      this.four = four;
      this.back = back;
      this.hats = hats;
      this.dkick = dkick;
    }
  }

  /**
   * Positions in `items` whose name (the text before "  ·  ") holds `query`, case and spaces and
   * hyphens aside, so "rock" finds Rock, Hard Rock, Blues Rock... and "hiphop" finds Hip-Hop.
   * The name itself first, then names starting with the query, then the rest, each in list order.
   * An empty query keeps every item.
   */
  public static int[] search(String[] items, String query) {
    String q = query == null ? "" : query.trim().toLowerCase();
    String qc = compact(q);
    java.util.List<Integer> exact = new java.util.ArrayList<Integer>();
    java.util.List<Integer> starts = new java.util.ArrayList<Integer>();
    java.util.List<Integer> inside = new java.util.ArrayList<Integer>();
    for (int i = 0; i < items.length; i++) {
      String name = items[i] == null ? "" : items[i];
      int dot = name.indexOf("  \u00b7  ");
      if (dot >= 0) name = name.substring(0, dot);
      String n = name.trim().toLowerCase();
      String nc = compact(n);
      if (q.length() == 0) inside.add(i);
      else if (n.equals(q) || nc.equals(qc)) exact.add(i);
      else if (n.startsWith(q) || nc.startsWith(qc)) starts.add(i);
      else if (n.contains(q) || (qc.length() > 0 && nc.contains(qc))) inside.add(i);
    }
    int[] out = new int[exact.size() + starts.size() + inside.size()];
    int k = 0;
    for (int i : exact) out[k++] = i;
    for (int i : starts) out[k++] = i;
    for (int i : inside) out[k++] = i;
    return out;
  }

  private static String compact(String s) {
    return s.replaceAll("[^\\p{L}\\p{N}&]+", "");
  }

  /** The database's style names, A–Z (Deep House, Gqom, Schranz...). */
  public static String[] names() {
    java.util.List<Row> list = rows();
    String[] out = new String[list.size()];
    for (int i = 0; i < out.length; i++) out[i] = list.get(i).name;
    return out;
  }

  public static java.util.List<Row> rows() {
    java.util.ArrayList<Row> out = new java.util.ArrayList<Row>();
    for (String line : HINTS) {
      String[] p = line.split("\\|");
      if (p.length < 8) continue;
      int hats = 8;
      float four = 0.5f;
      float back = 0.5f;
      float dkick = 0f;
      int bpm = 120;
      try { bpm = Integer.parseInt(p[2]); } catch (NumberFormatException ignored) {}
      try { four = Float.parseFloat(p[5]); } catch (NumberFormatException ignored) {}
      try { back = Float.parseFloat(p[6]); } catch (NumberFormatException ignored) {}
      try { hats = Integer.parseInt(p[7]); } catch (NumberFormatException ignored) {}
      if (p.length > 8) {
        try { dkick = Float.parseFloat(p[8]); } catch (NumberFormatException ignored) {}
      }
      out.add(new Row(p[0], p[1], bpm, four, back, hats, dkick));
    }
    java.util.Collections.sort(out, new java.util.Comparator<Row>() {
      public int compare(Row a, Row b) {
        String an = a.name == null ? "" : a.name;
        String bn = b.name == null ? "" : b.name;
        return an.compareToIgnoreCase(bn);
      }
    });
    return out;
  }

  /** Half, straight, or double the track tempo — whichever sits nearest the style. */
  public static int syncBpm(int trackBpm, int styleBpm) {
    int g = Math.max(40, Math.min(240, trackBpm > 0 ? trackBpm : 120));
    int s = Math.max(40, Math.min(240, styleBpm > 0 ? styleBpm : g));
    int best = g;
    double bestD = Double.POSITIVE_INFINITY;
    double[] ratios = new double[] { 0.5, 1, 2 };
    for (int i = 0; i < ratios.length; i++) {
      int bpm = Math.max(40, Math.min(240, (int) Math.round(g * ratios[i])));
      double d = Math.abs(Math.log(bpm / (double) s));
      if (d < bestD - 1e-9) {
        bestD = d;
        best = bpm;
      }
    }
    return best;
  }

  private static double score(
      int bpm, float four, float back, int hats, int kicks, float dkick,
      int hbpm, int lo, int hi, float hfour, float hback, int hhats, float hdk) {
    double span = Math.max(8, (hi - lo) / 2.0);
    double bpmFit = 1 - Math.min(1.2, Math.abs(bpm - hbpm) / (span + 6));
    if (bpm < lo - 14 || bpm > hi + 14) bpmFit *= 0.25;
    else if (bpm < lo || bpm > hi) bpmFit *= 0.7;
    double fourFit = 1 - Math.abs(four - hfour);
    double backFit = 1 - Math.abs(back - hback);
    double hatFit = 1 - Math.min(1, Math.abs(hats - hhats) / 10.0);
    int expectKicks = hfour > 0.7 ? 4 : hfour > 0.3 ? 3 : 2;
    double kickFit = 1 - Math.min(1, Math.abs(kicks - expectKicks) / 6.0);
    double dkFit = 1 - Math.abs(dkick - hdk);
    double s = bpmFit * 2.4 + fourFit * 1.5 + backFit * 1.35 + hatFit * 0.7 + kickFit * 0.35 + dkFit * 0.8;
    if (four >= 0.7f) {
      if (hfour >= 0.85f) s += 1.6;
      else if (hfour < 0.6f) s *= 0.62;
    }
    return s;
  }

  private static int hit(int[][] cells, int track, int step) {
    if (track < 0 || cells == null || track >= cells.length) return 0;
    int[] row = cells[track];
    if (row == null || row.length == 0) return 0;
    return row[step % row.length] > 0 ? 1 : 0;
  }

  private static int count(int[][] cells, int track) {
    int n = 0;
    for (int i = 0; i < Engine.STEPS; i++) n += hit(cells, track, i);
    return n;
  }
}
