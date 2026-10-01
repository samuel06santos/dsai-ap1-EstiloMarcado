package br.ufpa.dsai.estilomarcado.autenticacao.api.dto;

import java.util.Map;
import java.util.Set;

public record AtualizarPerfilRequest(String nome, String telefoneContato, boolean telefoneInformado) {
    private static final Set<String> CAMPOS_PERMITIDOS = Set.of("nome", "telefoneContato");

    public static AtualizarPerfilRequest from(Map<String, Object> campos) {
        if (!CAMPOS_PERMITIDOS.containsAll(campos.keySet())) {
            throw new IllegalArgumentException("campos de identidade ou vinculo nao podem ser alterados");
        }
        if (!(campos.get("nome") instanceof String nome)
                || nome.trim().length() < 2 || nome.trim().length() > 120) {
            throw new IllegalArgumentException("nome deve ter entre 2 e 120 caracteres");
        }
        Object telefone = campos.get("telefoneContato");
        if (telefone != null && !(telefone instanceof String)) {
            throw new IllegalArgumentException("telefoneContato deve ser texto ou null");
        }
        return new AtualizarPerfilRequest(nome.trim(), (String) telefone,
                campos.containsKey("telefoneContato"));
    }
}
