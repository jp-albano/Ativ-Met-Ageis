/**
 * Playlist: modelo, validação e armazenamento (tarefas 1 a 3 do card
 * "3- Criar e organizar playlists").
 *
 * Funciona no navegador (window.PlaylistLib) e no Node (require), para
 * permitir testes automatizados sem dependências.
 */
(function (root, factory) {
  if (typeof module === "object" && module.exports) {
    module.exports = factory();
  } else {
    root.PlaylistLib = factory();
  }
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  var NOME_MAX = 50;
  var STORAGE_KEY = "playlists.v1";

  /** Erro de validação do nome; a mensagem pode ser mostrada ao usuário. */
  function ValidationError(message) {
    this.name = "ValidationError";
    this.message = message;
  }
  ValidationError.prototype = Object.create(Error.prototype);

  /** Erro de leitura/gravação no armazenamento. */
  function StorageError(message) {
    this.name = "StorageError";
    this.message = message;
  }
  StorageError.prototype = Object.create(Error.prototype);

  /** Normaliza o nome: remove espaços nas pontas. */
  function normalizarNome(nome) {
    return typeof nome === "string" ? nome.trim() : "";
  }

  /** Chave de comparação: sem diferenciar maiúsculas/minúsculas. */
  function chaveNome(nome) {
    return normalizarNome(nome).toLocaleLowerCase("pt-BR");
  }

  /** Gera um id único e imutável para a playlist. */
  function gerarId() {
    if (typeof crypto !== "undefined" && crypto.randomUUID) {
      return "pl_" + crypto.randomUUID();
    }
    return "pl_" + Date.now().toString(36) + Math.random().toString(36).slice(2, 10);
  }

  /**
   * Valida o nome contra as regras e as playlists já existentes.
   * @param {string} nome
   * @param {Array} existentes playlists já salvas
   * @returns {string} nome normalizado
   * @throws {ValidationError}
   */
  function validarNome(nome, existentes) {
    var limpo = normalizarNome(nome);
    if (limpo.length === 0) {
      throw new ValidationError("Informe o nome da playlist.");
    }
    if (limpo.length > NOME_MAX) {
      throw new ValidationError("O nome deve ter no máximo " + NOME_MAX + " caracteres.");
    }
    var chave = chaveNome(limpo);
    var duplicado = (existentes || []).some(function (p) {
      return chaveNome(p.nome) === chave;
    });
    if (duplicado) {
      throw new ValidationError("Já existe uma playlist com esse nome.");
    }
    return limpo;
  }

  /** Cria o objeto playlist (nasce vazia). Não salva. */
  function criarPlaylist(nome) {
    return {
      id: gerarId(),
      nome: nome,
      musicas: [],
      criadaEm: new Date().toISOString(),
    };
  }

  /**
   * Repositório de playlists sobre um armazenamento com a interface
   * getItem/setItem (localStorage no navegador, objeto simples nos testes).
   */
  function PlaylistRepository(storage) {
    this.storage = storage;
  }

  /** @returns {Array} todas as playlists. @throws {StorageError} */
  PlaylistRepository.prototype.listar = function () {
    var bruto;
    try {
      bruto = this.storage.getItem(STORAGE_KEY);
    } catch (e) {
      throw new StorageError("Não foi possível acessar o armazenamento.");
    }
    if (bruto === null || bruto === undefined) return [];
    try {
      var dados = JSON.parse(bruto);
      if (!Array.isArray(dados)) throw new Error("formato inválido");
      return dados;
    } catch (e) {
      // Não sobrescrevemos dados ilegíveis em silêncio.
      throw new StorageError("Os dados salvos estão corrompidos.");
    }
  };

  /** @returns {object|null} playlist com o id ou null. */
  PlaylistRepository.prototype.buscarPorId = function (id) {
    var achada = this.listar().filter(function (p) { return p.id === id; })[0];
    return achada || null;
  };

  /**
   * Salva uma playlist nova. Falha se o nome já existir ou o id repetir.
   * @throws {ValidationError|StorageError}
   */
  PlaylistRepository.prototype.salvar = function (playlist) {
    var todas = this.listar();
    if (todas.some(function (p) { return p.id === playlist.id; })) {
      throw new StorageError("Já existe uma playlist com esse id.");
    }
    validarNome(playlist.nome, todas);
    todas.push(playlist);
    try {
      this.storage.setItem(STORAGE_KEY, JSON.stringify(todas));
    } catch (e) {
      throw new StorageError("Não foi possível salvar a playlist.");
    }
    return playlist;
  };

  /**
   * Fluxo completo usado pelo formulário: valida, cria e salva.
   * @returns {object} playlist criada
   */
  function criarESalvar(repo, nome) {
    var limpo = validarNome(nome, repo.listar());
    return repo.salvar(criarPlaylist(limpo));
  }

  return {
    NOME_MAX: NOME_MAX,
    STORAGE_KEY: STORAGE_KEY,
    ValidationError: ValidationError,
    StorageError: StorageError,
    validarNome: validarNome,
    criarPlaylist: criarPlaylist,
    PlaylistRepository: PlaylistRepository,
    criarESalvar: criarESalvar,
  };
});
