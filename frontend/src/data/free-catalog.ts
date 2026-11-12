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
