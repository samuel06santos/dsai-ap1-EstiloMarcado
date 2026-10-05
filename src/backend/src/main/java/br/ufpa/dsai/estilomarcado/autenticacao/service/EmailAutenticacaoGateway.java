package br.ufpa.dsai.estilomarcado.autenticacao.service;

public interface EmailAutenticacaoGateway {
    void enviarAtivacao(String destinatario, String nome, String token);
    void enviarConvite(String destinatario, String nome, String token);
    void enviarRecuperacao(String destinatario, String nome, String token);
    void enviarSenhaAlterada(String destinatario, String nome);
    default void enviarLinkAtivacao(String destinatario, String nome, String link) {
        enviarAtivacao(destinatario, nome, link);
    }
    default void enviarLinkRecuperacao(String destinatario, String nome, String link) {
        enviarRecuperacao(destinatario, nome, link);
    }
}
