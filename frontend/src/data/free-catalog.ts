/**
 * Catálogo inicial, já embutido na interface.
 * Cada faixa tem licença Creative Commons ou marca de domínio público no Internet Archive.
 * O áudio continua no servidor de origem; o player só aponta o navegador para esse endereço.
 */
export type FreeTrack = {
  id: string;
  title: string;
  artist: string;
  durationSeconds: number;
  license: string;
  sourceUrl: string;
  identifier: string;
  fileName: string;
};

const RAW: FreeTrack[] = [
  { id: "jamendo-129690|indoor-flowers", title: "Indoor Flowers", artist: "Truman", durationSeconds: 216, license: "http://creativecommons.org/licenses/by-sa/3.0/", sourceUrl: "https://archive.org/details/jamendo-129690", identifier: "jamendo-129690", fileName: "01-1087809-Truman-Indoor Flowers.mp3" },
  { id: "jamendo-404041|love-song", title: "Love Song", artist: "The Piano Lady (Lena Orsa)", durationSeconds: 258, license: "http://creativecommons.org/licenses/by-nc-nd/3.0/", sourceUrl: "https://archive.org/details/jamendo-404041", identifier: "jamendo-404041", fileName: "01-1718778-The Piano Lady _Lena Orsa_-Love Song _ Romantic Piano Music.mp3" },
  { id: "jamendo-152535|uplifting-piano", title: "Positively Uplifting Piano", artist: "Kara Square", durationSeconds: 71, license: "http://creativecommons.org/licenses/by-nc/3.0/", sourceUrl: "https://archive.org/details/jamendo-152535", identifier: "jamendo-152535", fileName: "01-1278312-Kara Square-Positively Uplifting Piano.mp3" },
  { id: "walzerdb1896|walzer", title: "Walzer op. 39", artist: "Johannes Brahms, piano de Wilhelm Backhaus", durationSeconds: 216, license: "http://creativecommons.org/publicdomain/mark/1.0/", sourceUrl: "https://archive.org/details/walzerdb1896", identifier: "walzerdb1896", fileName: "Walzer DB 1896.mp3" },
  { id: "talkin-guitar-blues|talkin", title: "Talkin' Guitar Blues", artist: "Derek Piotr, Bill Seneschal", durationSeconds: 232, license: "https://creativecommons.org/licenses/by-nc-sa/4.0/", sourceUrl: "https://archive.org/details/talkin-guitar-blues", identifier: "talkin-guitar-blues", fileName: "Talkin' Guitar Blues.mp3" },
  { id: "jamendo-239626|guitars-wave", title: "Guitar's Wave", artist: "emerald albatross", durationSeconds: 51, license: "http://creativecommons.org/licenses/by-nc-nd/3.0/", sourceUrl: "https://archive.org/details/jamendo-239626", identifier: "jamendo-239626", fileName: "01-1612011-emerald  albatross-guitar_s wave.mp3" },
  { id: "jamendo-243161|mysterious-guitar", title: "Mysterious Acoustic Guitar", artist: "markmusik", durationSeconds: 184, license: "http://creativecommons.org/licenses/by-nc-sa/3.0/", sourceUrl: "https://archive.org/details/jamendo-243161", identifier: "jamendo-243161", fileName: "01-1833037-markmusik-Mysterious Acoustic Guitar Without Drumm _16bit_.mp3" },
  { id: "violin-meditation|thais", title: "Méditation from Thaïs", artist: "Nicola Benedetti, violino; Julien Quentin, piano", durationSeconds: 316, license: "http://creativecommons.org/licenses/by-nc-nd/3.0/us/", sourceUrl: "https://archive.org/details/Nicola_Benedetti__Julien_Quentin_Music_from_the_Isabella_Stewart_Gardn-5720", identifier: "Nicola_Benedetti__Julien_Quentin_Music_from_the_Isabella_Stewart_Gardn-5720", fileName: "Nicola_Benedetti_violin_Julien_Quentin_piano_-_Mditation_from_Thas.mp3" },
  { id: "violin_201707|violin", title: "Violin", artist: "Joseph Kerski", durationSeconds: 76, license: "http://creativecommons.org/licenses/by-nd/3.0/", sourceUrl: "https://archive.org/details/violin_201707", identifier: "violin_201707", fileName: "violin.mp3" },
  { id: "chadillacblues|chadillac", title: "Chadillac Blues", artist: "drew Roberts", durationSeconds: 131, license: "https://creativecommons.org/licenses/by-sa/4.0/", sourceUrl: "https://archive.org/details/chadillacblues", identifier: "chadillacblues", fileName: "chadillacblues.mp3" },
  { id: "blues-in-my-soul|blues", title: "Blues in My Soul", artist: "Gerard de Goede", durationSeconds: 209, license: "https://creativecommons.org/publicdomain/zero/1.0/", sourceUrl: "https://archive.org/details/blues-in-my-soul", identifier: "blues-in-my-soul", fileName: "Blues in My Soul.mp3" },
  { id: "jamendo-139015|intermezzo", title: "Ensamble Intermezzo", artist: "Michał Jałochowski", durationSeconds: 87, license: "http://creativecommons.org/licenses/by-nc-sa/3.0/", sourceUrl: "https://archive.org/details/jamendo-139015", identifier: "jamendo-139015", fileName: "01-1158349-Micha_ Ja_ochowski-Ensamble Intermezzo - Flute and Orchestra.mp3" },
  { id: "jamendo-241479|flute", title: "Flute", artist: "Dreadrock", durationSeconds: 70, license: "http://creativecommons.org/licenses/by-nc-nd/3.0/", sourceUrl: "https://archive.org/details/jamendo-241479", identifier: "jamendo-241479", fileName: "01-1710496-Dreadrock-dreadrock - flute.mp3" },
  { id: "jamendo-351482|renaissance", title: "Renaissance: Hillingdon Garden", artist: "andy blackwood", durationSeconds: 98, license: "http://creativecommons.org/licenses/by-nc-nd/3.0/", sourceUrl: "https://archive.org/details/jamendo-351482", identifier: "jamendo-351482", fileName: "01-1245629-andy blackwood-Renaissance_  Hillingdon Garden w Flute.mp3" },
  { id: "jamendo-570046|rhythmic-flute", title: "The Rhythmic Path of the Flute", artist: "Oleksandr Yatsenko", durationSeconds: 163, license: "http://creativecommons.org/licenses/by-nc-nd/3.0/", sourceUrl: "https://archive.org/details/jamendo-570046", identifier: "jamendo-570046", fileName: "01-2180275-Oleksandr Yatsenko-The Rhythmic Path of the Flute.mp3" },
  { id: "jamendo-134694|cio-che-vedi", title: "Ciò Che Vedi", artist: "Essence", durationSeconds: 279, license: "http://creativecommons.org/licenses/by-sa/2.5/it/", sourceUrl: "https://archive.org/details/jamendo-134694", identifier: "jamendo-134694", fileName: "01-1125762-Essence _Folk-Ambient Music Project_-Cio Che Vedi - Essence.mp3" },
  { id: "jamendo-427695|folk-waltz", title: "Acoustic Folk Waltz", artist: "Marc Corominas Pujadó", durationSeconds: 128, license: "http://creativecommons.org/licenses/by-nc-nd/3.0/", sourceUrl: "https://archive.org/details/jamendo-427695", identifier: "jamendo-427695", fileName: "01-1649995-Marc Corominas Pujado-Acoustic Folk Waltz.mp3" },
  { id: "folk-john-johanna", title: "John Johanna", artist: "Charlie Parr", durationSeconds: 206, license: "http://creativecommons.org/licenses/by-nc-nd/3.0/", sourceUrl: "https://archive.org/details/TraditionalAndOriginalAmericanFolkMusic", identifier: "TraditionalAndOriginalAmericanFolkMusic", fileName: "01-JohnJohanna.mp3" },
  { id: "jamendo-248567|cello-sonate", title: "Sonate for cello and piano", artist: "Katakomba", durationSeconds: 443, license: "http://creativecommons.org/licenses/by-nc-nd/3.0/", sourceUrl: "https://archive.org/details/jamendo-248567", identifier: "jamendo-248567", fileName: "01-1474435-Katakomba-Sonate for cello and piano.mp3" },
  { id: "jamendo-380672|piano-cello", title: "Slow Emotional Piano with Cello", artist: "Sound Gallery by Dmitry Taras", durationSeconds: 172, license: "http://creativecommons.org/licenses/by-nc-nd/3.0/", sourceUrl: "https://archive.org/details/jamendo-380672", identifier: "jamendo-380672", fileName: "01-1643953-Sound Gallery by Dmitry Taras-Slow Emotional Piano with Cello.mp3" },
  { id: "jamendo-510195|border-line", title: "Border Line", artist: "Mickael Huchet", durationSeconds: 128, license: "http://creativecommons.org/licenses/by-nc-nd/3.0/", sourceUrl: "https://archive.org/details/jamendo-510195", identifier: "jamendo-510195", fileName: "01-2003666-Mickael Huchet-Border Line _trumpet version_.mp3" },
  { id: "jamendo-448525|deep-house", title: "Fashion Deep House", artist: "Sound Creator", durationSeconds: 120, license: "http://creativecommons.org/licenses/by-nc-sa/3.0/", sourceUrl: "https://archive.org/details/jamendo-448525", identifier: "jamendo-448525", fileName: "01-1871207-Sound Creator-Fashion Deep House _Main without Trumpet_.mp3" },
  { id: "ambient-wilson", title: "Wilson-Harrington", artist: "The Ambient Visitor", durationSeconds: 302, license: "http://creativecommons.org/licenses/by-nc-sa/3.0/", sourceUrl: "https://archive.org/details/TheAmbientVisitor-Eleven", identifier: "TheAmbientVisitor-Eleven", fileName: "01TheAmbientVisitor-Wilson-Harrington.mp3" },
  { id: "ambient-nightlight", title: "Nightlight", artist: "Lee Rosevere", durationSeconds: 167, license: "http://creativecommons.org/licenses/by-nc-sa/3.0/", sourceUrl: "https://archive.org/details/The_Ambient_Baby-8089", identifier: "The_Ambient_Baby-8089", fileName: "Lee_Rosevere_-_03_-_Nightlight.mp3" },
  { id: "jamendo-122450|i-need-to-know", title: "I Need to Know", artist: "Sweet danger", durationSeconds: 216, license: "http://creativecommons.org/licenses/by/3.0/", sourceUrl: "https://archive.org/details/jamendo-122450", identifier: "jamendo-122450", fileName: "01-1039404-Sweet danger-I need to know - acoustic version.mp3" },
  { id: "jamendo-148838|rubber-dolls", title: "Rubber Dolls", artist: "Acoustic Enlacement", durationSeconds: 250, license: "http://creativecommons.org/licenses/by-nc-sa/3.0/", sourceUrl: "https://archive.org/details/jamendo-148838", identifier: "jamendo-148838", fileName: "02-1241895-Acoustic Enlacement-Rubber dolls.mp3" },
  { id: "jamendo-463847|bossa-lounge", title: "Bossa Nova Lounge", artist: "BeardMusicStock", durationSeconds: 306, license: "http://creativecommons.org/licenses/by-nc-sa/3.0/", sourceUrl: "https://archive.org/details/jamendo-463847", identifier: "jamendo-463847", fileName: "01-1903517-BeardMusicStock-Bossa Nova Lounge.mp3" },
  { id: "jamendo-608143|bossa-cinematic", title: "Bossa Nova Cinematic", artist: "ForestMusic", durationSeconds: 100, license: "http://creativecommons.org/licenses/by-nc-nd/3.0/", sourceUrl: "https://archive.org/details/jamendo-608143", identifier: "jamendo-608143", fileName: "01-2263957-ForestMusic-Bossa Nova Cinematic.mp3" },
  { id: "jamendo-614040|bossa-for-two", title: "Bossa for Two", artist: "Bulbasound", durationSeconds: 248, license: "http://creativecommons.org/licenses/by-nc-nd/3.0/", sourceUrl: "https://archive.org/details/jamendo-614040", identifier: "jamendo-614040", fileName: "01-2276840-Bulbasound-Bossa for Two.mp3" },
  { id: "until-dawn-drums", title: "Until Dawn", artist: "David R Merry", durationSeconds: 232, license: "http://creativecommons.org/licenses/by-sa/2.0/uk/", sourceUrl: "https://archive.org/details/DavidRMerryUntilDawnDrums", identifier: "DavidRMerryUntilDawnDrums", fileName: "The_High_Life_09_Until_Dawn_Drums_64kb.mp3" },
];

export const readyCatalog: FreeTrack[] = RAW;

/** Monta o endereço de stream sem gravar o arquivo no projeto. */
export function streamUrl(track: Pick<FreeTrack, "identifier" | "fileName">): string {
  return `https://archive.org/download/${encodeURIComponent(track.identifier)}/${encodeURIComponent(track.fileName)}`;
}

export function licenseLabel(url: string): string {
  const value = url.toLowerCase();
  if (value.includes("/zero/")) return "CC0";
  if (value.includes("publicdomain")) return "Domínio público";
  const match = value.match(/\/(by(?:-nc)?(?:-nd)?(?:-sa)?)\//);
  return match ? `CC ${match[1].toUpperCase()}` : "Creative Commons";
}
