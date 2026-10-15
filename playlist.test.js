// Testes sem dependências. Execute: node playlist.test.js
const assert = require("assert");
const L = require("./playlist.js");

function memoria() {
  const d = {};
  return { getItem: (k) => (k in d ? d[k] : null), setItem: (k, v) => { d[k] = String(v); } };
}
function repoNovo() { return new L.PlaylistRepository(memoria()); }
function lanca(fn, tipo, trecho) {
  assert.throws(fn, (e) => e instanceof tipo && e.message.includes(trecho));
}

const testes = {
  "nome válido cria playlist vazia com id e criadaEm"() {
    const p = L.criarESalvar(repoNovo(), "Treino");
    assert.strictEqual(p.nome, "Treino");
    assert.deepStrictEqual(p.musicas, []);
    assert.ok(p.id && !Number.isNaN(Date.parse(p.criadaEm)));
  },
  "espaços nas pontas são removidos"() {
    assert.strictEqual(L.criarESalvar(repoNovo(), "  Treino  ").nome, "Treino");
  },
  "vazio e só espaços são rejeitados"() {
    lanca(() => L.criarESalvar(repoNovo(), ""), L.ValidationError, "Informe o nome");
    lanca(() => L.criarESalvar(repoNovo(), "   "), L.ValidationError, "Informe o nome");
    lanca(() => L.criarESalvar(repoNovo(), null), L.ValidationError, "Informe o nome");
  },
  "50 caracteres passa e 51 é rejeitado"() {
    L.criarESalvar(repoNovo(), "a".repeat(50));
    lanca(() => L.criarESalvar(repoNovo(), "a".repeat(51)), L.ValidationError, "no máximo 50");
  },
  "nome duplicado é rejeitado sem diferenciar maiúsculas"() {
    const r = repoNovo();
    L.criarESalvar(r, "Treino");
    lanca(() => L.criarESalvar(r, "treino"), L.ValidationError, "Já existe");
    lanca(() => L.criarESalvar(r, " TREINO "), L.ValidationError, "Já existe");
    assert.strictEqual(r.listar().length, 1);
  },
  "persistência: nova instância sobre o mesmo armazenamento vê os dados"() {
    const m = memoria();
    L.criarESalvar(new L.PlaylistRepository(m), "Treino");
    const lista = new L.PlaylistRepository(m).listar();
    assert.strictEqual(lista.length, 1);
    assert.strictEqual(lista[0].nome, "Treino");
  },
  "duas playlists com nomes diferentes mantêm as duas e ids distintos"() {
    const r = repoNovo();
    const a = L.criarESalvar(r, "Treino");
    const b = L.criarESalvar(r, "Estudo");
    assert.notStrictEqual(a.id, b.id);
    assert.deepStrictEqual(r.listar().map((p) => p.nome), ["Treino", "Estudo"]);
  },
  "buscarPorId devolve a playlist ou null"() {
    const r = repoNovo();
    const a = L.criarESalvar(r, "Treino");
    assert.strictEqual(r.buscarPorId(a.id).nome, "Treino");
    assert.strictEqual(r.buscarPorId("nao-existe"), null);
  },
  "dados corrompidos geram erro e não são sobrescritos"() {
    const m = memoria();
    m.setItem(L.STORAGE_KEY, "{quebrado");
    const r = new L.PlaylistRepository(m);
    lanca(() => r.listar(), L.StorageError, "corrompidos");
    lanca(() => L.criarESalvar(r, "Treino"), L.StorageError, "corrompidos");
    assert.strictEqual(m.getItem(L.STORAGE_KEY), "{quebrado");
  },
  "falha ao gravar gera StorageError"() {
    const m = memoria();
    m.setItem = () => { throw new Error("cheio"); };
    lanca(() => L.criarESalvar(new L.PlaylistRepository(m), "Treino"), L.StorageError, "salvar");
  },
};

let falhas = 0;
for (const [nome, fn] of Object.entries(testes)) {
  try { fn(); console.log("OK    " + nome); }
  catch (e) { falhas++; console.log("FALHA " + nome + "\n      " + e.message); }
}
console.log(falhas ? `\n${falhas} falha(s)` : `\n${Object.keys(testes).length} testes passaram`);
process.exit(falhas ? 1 : 0);
