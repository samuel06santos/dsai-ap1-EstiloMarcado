package br.ufpa.dsai.estilomarcado.estabelecimento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import br.ufpa.dsai.estilomarcado.autenticacao.service.EmailAutenticacaoGateway;
import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;
import br.ufpa.dsai.estilomarcado.catalogo.repository.UnidadeRepository;
import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = "app.auth.password-cost=4")
@AutoConfigureMockMvc
@Testcontainers
class EstabelecimentoIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UnidadeRepository unidades;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @MockitoBean EmailAutenticacaoGateway email;

    private Unidade principal;
    private Cookie admin;

    @BeforeEach
    void preparar() throws Exception {
        jdbc.execute("DELETE FROM spring_session_attributes");
        jdbc.execute("DELETE FROM spring_session");
        jdbc.execute("DELETE FROM evento_seguranca");
        jdbc.execute("DELETE FROM token_usuario");
        jdbc.execute("DELETE FROM usuario");
        jdbc.execute("DELETE FROM servico_profissional");
        jdbc.execute("DELETE FROM servico");
        jdbc.execute("DELETE FROM profissional");
        jdbc.execute("DELETE FROM unidade");
        jdbc.execute("DELETE FROM estabelecimento");
        reset(email);
        principal = unidades.save(new Unidade("Centro"));
        usuarios.save(new Usuario("Admin", "principal@example.com", "principal@example.com",
                encoder.encode("Senha123"), PerfilUsuario.ADMINISTRADOR,
                EstadoConta.ATIVA, principal, null));
        admin = login("principal@example.com", "Senha123");
    }

    @Test
    void preservaVinculoInicialEMantemCriacaoDeFilialComConviteIsolado() throws Exception {
        assertNotNull(principal.getEstabelecimento().getId());
        long estabelecimentoId = principal.getEstabelecimento().getId();

        mvc.perform(post("/api/estabelecimentos/{id}/unidades", estabelecimentoId)
                        .cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Norte\",\"fusoHorario\":\"America/Sao_Paulo\","
                                + "\"primeiroAdministrador\":{\"nome\":\"Admin Norte\","
                                + "\"email\":\"norte@example.com\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ativa").value(false));

        Unidade norte = unidades.findByEstabelecimentoIdOrderByNomeAsc(estabelecimentoId).stream()
                .filter(u -> !u.isPrincipal()).findFirst().orElseThrow();
        assertEquals(EstadoConta.PENDENTE,
                usuarios.findByEmailNormalizado("norte@example.com").orElseThrow().getEstado());
        mvc.perform(get("/api/unidades/{id}/publico", norte.getId()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/unidades/{id}", norte.getId()).cookie(admin))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/estabelecimentos/{id}", estabelecimentoId).cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filiais.length()").value(2));

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(email).enviarConvite(eq("norte@example.com"), eq("Admin Norte"), token.capture());
        mvc.perform(post("/api/autenticacao/convites").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token.getValue() + "\",\"senha\":\"Senha456\","
                                + "\"confirmacaoSenha\":\"Senha456\"}"))
                .andExpect(status().isNoContent());

        Cookie adminNorte = login("norte@example.com", "Senha456");
        mvc.perform(patch("/api/unidades/{id}", norte.getId()).cookie(adminNorte).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Norte\",\"fusoHorario\":\"America/Sao_Paulo\",\"ativa\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativa").value(true));
        mvc.perform(patch("/api/unidades/{id}", principal.getId()).cookie(adminNorte).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Outra\",\"fusoHorario\":\"America/Sao_Paulo\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/estabelecimentos/{id}", estabelecimentoId).cookie(adminNorte))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filiais.length()").value(0));
    }

    @Test
    void profissionalInativoSaiDaConsultaPublicaESemPermissaoNaoPodeSerEditado() throws Exception {
        mvc.perform(post("/api/unidades/{id}/profissionais", principal.getId())
                        .cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ana\",\"apresentacao\":\"Cortes e penteados\"}"))
                .andExpect(status().isCreated());
        long id = jdbc.queryForObject("SELECT id FROM profissional WHERE nome = 'Ana'", Long.class);
        mvc.perform(get("/api/unidades/{id}/profissionais", principal.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(patch("/api/unidades/{unidadeId}/profissionais/{id}", principal.getId(), id)
                        .cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ana\",\"ativo\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/unidades/{id}/profissionais", principal.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/unidades/{id}/profissionais", principal.getId())
                        .param("incluirInativos", "true"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/unidades/{id}/profissionais", principal.getId())
                        .cookie(admin).param("incluirInativos", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void rejeitaNomeDuplicadoFusoInvalidoEIdDeOutroEstabelecimento() throws Exception {
        long estabelecimentoId = principal.getEstabelecimento().getId();
        String dados = "{\"nome\":\"  centro  \",\"fusoHorario\":\"America/Sao_Paulo\","
                + "\"primeiroAdministrador\":{\"nome\":\"Outro\",\"email\":\"outro@example.com\"}}";
        mvc.perform(post("/api/estabelecimentos/{id}/unidades", estabelecimentoId)
                        .cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(dados))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/unidades/{id}", principal.getId()).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Centro\",\"fusoHorario\":\"Fuso/Invalido\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/estabelecimentos/{id}", estabelecimentoId + 100).cookie(admin))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/unidades/{id}/profissionais", principal.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Ana\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void emailJaUsadoNaoDeixaFilialSemAdministrador() throws Exception {
        long estabelecimentoId = principal.getEstabelecimento().getId();
        mvc.perform(post("/api/estabelecimentos/{id}/unidades", estabelecimentoId)
                        .cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Sul\",\"fusoHorario\":\"America/Sao_Paulo\","
                                + "\"primeiroAdministrador\":{\"nome\":\"Outra Pessoa\","
                                + "\"email\":\"principal@example.com\"}}"))
                .andExpect(status().isConflict());
        assertEquals(1, unidades.findByEstabelecimentoIdOrderByNomeAsc(estabelecimentoId).size());
    }

    private Cookie login(String emailUsuario, String senha) throws Exception {
        Cookie cookie = mvc.perform(post("/api/autenticacao/sessoes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + emailUsuario + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        assertNotNull(cookie);
        return cookie;
    }
}
