package br.ufpa.dsai.estilomarcado.autenticacao.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.ufpa.dsai.estilomarcado.catalogo.repository.UnidadeRepository;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.ConflitoException;
import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;

@Component
@ConditionalOnProperty(prefix = "app.auth.bootstrap", name = "enabled", havingValue = "true")
public class AdministradorBootstrap implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdministradorBootstrap.class);

    private final UsuarioInternoService service;
    private final UnidadeRepository unidadeRepository;
    private final Long unidadeId;
    private final String nomeUnidade;
    private final String nome;
    private final String email;
    private final String nomeAdicional;
    private final String emailAdicional;

    public AdministradorBootstrap(UsuarioInternoService service,
                                  UnidadeRepository unidadeRepository,
                                  @Value("${app.auth.bootstrap.unidade-id}") Long unidadeId,
                                  @Value("${app.auth.bootstrap.unidade-nome}") String nomeUnidade,
                                  @Value("${app.auth.bootstrap.nome}") String nome,
                                  @Value("${app.auth.bootstrap.email}") String email,
                                  @Value("${app.auth.bootstrap.nome-adicional}") String nomeAdicional,
                                  @Value("${app.auth.bootstrap.email-adicional}") String emailAdicional) {
        this.service = service;
        this.unidadeRepository = unidadeRepository;
        this.unidadeId = unidadeId;
        this.nomeUnidade = nomeUnidade;
        this.nome = nome;
        this.email = email;
        this.nomeAdicional = nomeAdicional;
        this.emailAdicional = emailAdicional;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long id;
        try {
            id = resolverUnidadeId();
        } catch (RuntimeException ex) {
            LOGGER.error("Unidade do bootstrap administrativo nao resolvida: {}", ex.getMessage());
            return;
        }
        try {
            service.garantirAdministradorConfigurado(id, nome, email);
        } catch (RuntimeException ex) {
            LOGGER.error("Administrador inicial nao provisionado: {}", ex.getMessage());
        }
        if (emailAdicional != null && !emailAdicional.isBlank()) {
            try {
                service.garantirAdministradorConfigurado(id, nomeAdicional, emailAdicional);
            } catch (RuntimeException ex) {
                LOGGER.error("Administrador adicional nao provisionado: {}", ex.getMessage());
            }
        }
    }

    private Long resolverUnidadeId() {
        if (unidadeId != null && unidadeId > 0) {
            return unidadeId;
        }
        if (nomeUnidade == null || nomeUnidade.isBlank()) {
            throw new IllegalArgumentException("bootstrap administrativo exige unidade valida");
        }
        var candidatas = unidadeRepository.findByNomeAndPrincipalTrue(nomeUnidade.trim());
        if (candidatas.size() > 1) {
            throw new ConflitoException("bootstrap ambiguo: informe AUTH_BOOTSTRAP_ADMIN_UNIT_ID");
        }
        return candidatas.isEmpty()
                ? unidadeRepository.save(new Unidade(nomeUnidade.trim())).getId()
                : candidatas.getFirst().getId();
    }
}
