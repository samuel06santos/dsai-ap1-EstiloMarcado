package br.ufpa.dsai.estilomarcado.autenticacao.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailAutenticacaoSmtp implements EmailAutenticacaoGateway {

    private final JavaMailSender mailSender;
    private final String frontendUrl;

    public EmailAutenticacaoSmtp(JavaMailSender mailSender,
                                 @Value("${app.frontend-url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl.replaceAll("/$", "");
    }

    @Override
    public void enviarAtivacao(String destinatario, String nome, String token) {
        enviar(destinatario, "Ative sua conta no Estilo Marcado",
                "Ola, " + nome + "!\n\nAtive sua conta neste link (valido por 24 horas):\n"
                        + link("/ativar", token));
    }

    @Override
    public void enviarConvite(String destinatario, String nome, String token) {
        enviar(destinatario, "Convite para o Estilo Marcado",
                "Ola, " + nome + "!\n\nConclua seu convite e defina sua senha neste link (valido por 24 horas):\n"
                        + link("/convite", token));
    }

    @Override
    public void enviarRecuperacao(String destinatario, String nome, String token) {
        enviar(destinatario, "Recuperacao de conta do Estilo Marcado",
                "Ola, " + nome + "!\n\nRedefina sua senha neste link (valido por 30 minutos):\n"
                        + link("/redefinir-senha", token));
    }

    @Override
    public void enviarSenhaAlterada(String destinatario, String nome) {
        enviar(destinatario, "Sua senha foi alterada",
                "Ola, " + nome + "!\n\nA senha da sua conta no Estilo Marcado foi alterada."
                        + " Se voce nao reconhece esta acao, solicite uma nova recuperacao imediatamente.");
    }

    private String link(String caminho, String token) {
        return frontendUrl + caminho + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private void enviar(String destinatario, String assunto, String texto) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom("nao-responda@estilomarcado.local");
        mensagem.setTo(destinatario);
        mensagem.setSubject(assunto);
        mensagem.setText(texto);
        mailSender.send(mensagem);
    }
}
