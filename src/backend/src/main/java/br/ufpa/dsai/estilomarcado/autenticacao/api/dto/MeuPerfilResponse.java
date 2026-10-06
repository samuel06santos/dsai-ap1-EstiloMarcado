package br.ufpa.dsai.estilomarcado.autenticacao.api.dto;

import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;
import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;

public record MeuPerfilResponse(
        Long id, String nome, String email, PerfilUsuario perfil, EstadoConta estado,
        Long unidadeId, Long profissionalId, String telefoneContato,
        FilialResumo filial, EstabelecimentoResumo estabelecimento, String fotoPerfilUrl) {

    public static MeuPerfilResponse from(Usuario usuario, String fotoPerfilUrl) {
        Unidade unidade = usuario.getUnidade();
        return new MeuPerfilResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(),
                usuario.getPerfil(), usuario.getEstado(),
                unidade == null ? null : unidade.getId(),
                usuario.getProfissional() == null ? null : usuario.getProfissional().getId(),
                usuario.getTelefoneContato(),
                unidade == null ? null : new FilialResumo(unidade.getId(), unidade.getNome(), unidade.isAtiva()),
                unidade == null ? null : new EstabelecimentoResumo(
                        unidade.getEstabelecimento().getId(), unidade.getEstabelecimento().getNome()),
                fotoPerfilUrl);
    }

    public record FilialResumo(Long id, String nome, boolean ativa) {}
    public record EstabelecimentoResumo(Long id, String nome) {}
}
