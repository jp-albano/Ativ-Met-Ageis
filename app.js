/** Liga o formulário ao PlaylistLib e mostra as playlists salvas. */
(function () {
  "use strict";
  var Lib = window.PlaylistLib;
  var repo = new Lib.PlaylistRepository(window.localStorage);

  var form = document.getElementById("form-playlist");
  var campo = document.getElementById("nome");
  var erro = document.getElementById("erro");
  var sucesso = document.getElementById("sucesso");
  var lista = document.getElementById("lista");
  var vazio = document.getElementById("vazio");

  function mostrarErro(msg) {
    sucesso.textContent = "";
    erro.textContent = msg;
    campo.setAttribute("aria-invalid", "true");
  }

  function limparMensagens() {
    erro.textContent = "";
    sucesso.textContent = "";
    campo.removeAttribute("aria-invalid");
  }

  function renderizar() {
    var playlists;
    try {
      playlists = repo.listar();
    } catch (e) {
      mostrarErro(e.message);
      return;
    }
    lista.innerHTML = "";
    vazio.hidden = playlists.length > 0;
    playlists.forEach(function (p) {
      var li = document.createElement("li");
      var nome = document.createElement("strong");
      nome.textContent = p.nome; // textContent evita injeção de HTML
      var info = document.createElement("span");
      var qtd = p.musicas.length;
      info.textContent = qtd + (qtd === 1 ? " música" : " músicas") +
        " · criada em " + new Date(p.criadaEm).toLocaleDateString("pt-BR");
      li.appendChild(nome);
      li.appendChild(info);
      lista.appendChild(li);
    });
  }

  form.addEventListener("submit", function (ev) {
    ev.preventDefault();
    limparMensagens();
    try {
      var criada = Lib.criarESalvar(repo, campo.value);
      campo.value = "";
      sucesso.textContent = 'Playlist "' + criada.nome + '" criada.';
      campo.focus();
      renderizar();
    } catch (e) {
      if (e instanceof Lib.ValidationError || e instanceof Lib.StorageError) {
        mostrarErro(e.message);
      } else {
        throw e;
      }
    }
  });

  campo.addEventListener("input", limparMensagens);
  renderizar();
})();
