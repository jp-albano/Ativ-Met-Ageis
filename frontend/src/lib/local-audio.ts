/**
 * Áudio escolhido pelo usuário, guardado só neste navegador.
 *
 * A API Java não recebe arquivo nem devolve stream (o backend está congelado).
 * O player web só consegue tocar o que a pessoa importa aqui. O IndexedDB
 * persiste o blob entre recarregamentos sem enviar o arquivo ao servidor.
 */

const DB_NAME = "sonora-local-audio";
const STORE = "tracks";
const MAX_BYTES = 200 * 1024 * 1024;
const AUDIO_EXTENSIONS = new Set(["mp3", "wav", "ogg", "oga", "m4a", "aac", "flac", "webm", "mp4", "aiff", "aif", "au"]);

export type LocalAudio = {
  id: string;
  title: string;
  artist: string;
  durationSeconds: number;
  fileName: string;
  addedAt: number;
  catalogId?: string;
};

type StoredTrack = LocalAudio & { blob: Blob };

function requestToPromise<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("Falha no armazenamento local."));
  });
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE, { keyPath: "id" });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("Não foi possível abrir o armazenamento local."));
  });
}

function withoutBlob(track: StoredTrack): LocalAudio {
  return {
    id: track.id,
    title: track.title,
    artist: track.artist,
    durationSeconds: track.durationSeconds,
    fileName: track.fileName,
    addedAt: track.addedAt,
    catalogId: track.catalogId,
  };
}

export function assertPlayableFile(file: File): void {
  const extension = file.name.split(".").pop()?.toLowerCase() ?? "";
  const looksLikeAudio = AUDIO_EXTENSIONS.has(extension) || file.type.startsWith("audio/");
  if (!looksLikeAudio) throw new Error(`${file.name} não parece um arquivo de áudio.`);
  if (file.size <= 0) throw new Error(`${file.name} está vazio.`);
  if (file.size > MAX_BYTES) throw new Error(`${file.name} passa de 200 MB.`);
}

/** Lê a duração declarada pelo arquivo. Zero quando o navegador não consegue ler os metadados. */
export function readDuration(file: File): Promise<number> {
  return new Promise(resolve => {
    const url = URL.createObjectURL(file);
    const audio = document.createElement("audio");
    const finish = (value: number) => {
      window.clearTimeout(timer);
      URL.revokeObjectURL(url);
      resolve(value);
    };
    const timer = window.setTimeout(() => finish(0), 4000);
    audio.preload = "metadata";
    audio.onloadedmetadata = () => finish(Number.isFinite(audio.duration) ? audio.duration : 0);
    audio.onerror = () => finish(0);
    audio.src = url;
  });
}

export async function listLocalTracks(): Promise<LocalAudio[]> {
  const db = await openDb();
  try {
    const rows = await requestToPromise(db.transaction(STORE, "readonly").objectStore(STORE).getAll() as IDBRequest<StoredTrack[]>);
    return rows.map(withoutBlob).sort((a, b) => b.addedAt - a.addedAt);
  } finally {
    db.close();
  }
}

export async function getAudioBlob(id: string): Promise<Blob | null> {
  const db = await openDb();
  try {
    const row = await requestToPromise(db.transaction(STORE, "readonly").objectStore(STORE).get(id) as IDBRequest<StoredTrack | undefined>);
    return row?.blob ?? null;
  } finally {
    db.close();
  }
}

export async function saveLocalAudio(input: {
  file: File;
  title: string;
  artist: string;
  durationSeconds: number;
  catalogId?: string;
}): Promise<LocalAudio> {
  assertPlayableFile(input.file);
  const record: StoredTrack = {
    id: crypto.randomUUID(),
    title: input.title.trim() || "Faixa sem título",
    artist: input.artist.trim() || "Artista desconhecido",
    durationSeconds: input.durationSeconds,
    fileName: input.file.name,
    addedAt: Date.now(),
    blob: input.file,
  };
  if (input.catalogId) record.catalogId = input.catalogId;
  const db = await openDb();
  try {
    await requestToPromise(db.transaction(STORE, "readwrite").objectStore(STORE).put(record));
  } finally {
    db.close();
  }
  return withoutBlob(record);
}

export async function removeLocalTrack(id: string): Promise<void> {
  const db = await openDb();
  try {
    await requestToPromise(db.transaction(STORE, "readwrite").objectStore(STORE).delete(id));
  } finally {
    db.close();
  }
}

/** Apaga arquivos locais ligados a uma faixa do catálogo antes de substituir o áudio. */
export async function removeByCatalogId(catalogId: string): Promise<void> {
  const tracks = await listLocalTracks();
  for (const track of tracks) {
    if (track.catalogId === catalogId) await removeLocalTrack(track.id);
  }
}

/**
 * Mantém o arquivo tocável quando a faixa some do catálogo da API.
 * Cada gravação abre a própria transação: o IndexedDB encerra a transação
 * se houver uma espera entre um pedido e o próximo.
 */
export async function clearCatalogLink(catalogId: string): Promise<void> {
  const db = await openDb();
  let rows: StoredTrack[] = [];
  try {
    rows = await requestToPromise(db.transaction(STORE, "readonly").objectStore(STORE).getAll() as IDBRequest<StoredTrack[]>);
  } finally {
    db.close();
  }
  for (const row of rows) {
    if (row.catalogId !== catalogId) continue;
    const next: StoredTrack = { ...row };
    delete next.catalogId;
    const write = await openDb();
    try {
      await requestToPromise(write.transaction(STORE, "readwrite").objectStore(STORE).put(next));
    } finally {
      write.close();
    }
  }
}
