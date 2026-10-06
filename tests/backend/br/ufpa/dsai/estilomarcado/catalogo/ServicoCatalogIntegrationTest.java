package br.ufpa.dsai.estilomarcado.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.ufpa.dsai.estilomarcado.catalogo.api.dto.ServicoRequest;
import br.ufpa.dsai.estilomarcado.catalogo.api.dto.ServicoResponse;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.ConflitoException;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.RecursoNaoEncontradoException;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.RegraDeNegocioException;
import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import br.ufpa.dsai.estilomarcado.catalogo.model.Servico;
import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ProfissionalRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ServicoRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.UnidadeRepository;
import br.ufpa.dsai.estilomarcado.catalogo.service.ServicoService;
import br.ufpa.dsai.estilomarcado.autenticacao.model.EstadoConta;
import br.ufpa.dsai.estilomarcado.autenticacao.model.PerfilUsuario;
import br.ufpa.dsai.estilomarcado.autenticacao.model.Usuario;
import br.ufpa.dsai.estilomarcado.autenticacao.repository.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import jakarta.validation.Validator;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ServicoCatalogIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    ServicoService servicoService;

    @Autowired
    Validator validator;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UnidadeRepository unidadeRepository;

    @Autowired
    ProfissionalRepository profissionalRepository;

    @Autowired
    ServicoRepository servicoRepository;

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;

    private Unidade unidade;
    private Unidade outraUnidade;
    private Profissional profA;
    private Profissional profB;
    private Profissional profOutraUnidade;

    @BeforeEach
    void prepararBanco() {
        jdbcTemplate.execute("DELETE FROM spring_session_attributes");
        jdbcTemplate.execute("DELETE FROM spring_session");
        jdbcTemplate.execute("DELETE FROM evento_seguranca");
        jdbcTemplate.execute("DELETE FROM token_usuario");
        jdbcTemplate.execute("DELETE FROM usuario");
        jdbcTemplate.execute("DELETE FROM servico_profissional");
        jdbcTemplate.execute("DELETE FROM servico");
        jdbcTemplate.execute("DELETE FROM profissional");
        jdbcTemplate.execute("DELETE FROM unidade");
        jdbcTemplate.execute("DELETE FROM estabelecimento");

        unidade = unidadeRepository.save(new Unidade("Unidade Centro"));
        outraUnidade = unidadeRepository.save(new Unidade("Unidade Norte"));
        profA = profissionalRepository.save(new Profissional("Ana", unidade));
        profB = profissionalRepository.save(new Profissional("Bruno", unidade));
        profOutraUnidade = profissionalRepository.save(new Profissional("Carlos", outraUnidade));
    }

    @Test
    void deveCriarServicoComDadosValidos() {
        ServicoResponse resposta = servicoService.criar(unidade.getId(), requestValida());

        assertNotNull(resposta.getId());
        assertEquals(unidade.getId(), resposta.getUnidadeId());
        assertEquals("Corte de cabelo", resposta.getNome());
        assertEquals(30, resposta.getDuracaoMinutos());
        assertEquals(0, new BigDecimal("50.00").compareTo(resposta.getPreco()));
        assertTrue(resposta.isAtivo());
    }

    @Test
    void deveRejeitarServicoSemNome() {
        ServicoRequest request = requestValida();
        request.setNome(null);
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void deveRejeitarServicoSemDuracao() {
        ServicoRequest request = requestValida();
        request.setDuracaoMinutos(null);
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void deveRejeitarDuracaoZeroOuNegativa() {
        ServicoRequest zero = requestValida();
        zero.setDuracaoMinutos(0);
        assertFalse(validator.validate(zero).isEmpty());

        ServicoRequest negativa = requestValida();
        negativa.setDuracaoMinutos(-5);
        assertFalse(validator.validate(negativa).isEmpty());
    }

    @Test
    void deveRejeitarPrecoNegativo() {
        ServicoRequest request = requestValida();
        request.setPreco(new BigDecimal("-1.00"));
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void deveRejeitarPrecoComMaisDeDuasCasasDecimais() {
        ServicoRequest request = requestValida();
        request.setPreco(new BigDecimal("10.999"));
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void deveAceitarIntervaloNulo() {
        ServicoRequest request = requestValida();
        request.setIntervaloMinutos(null);

        ServicoResponse resposta = servicoService.criar(unidade.getId(), request);

        assertNotNull(resposta.getId());
    }

    @Test
    void deveRejeitarIntervaloNegativo() {
        ServicoRequest request = requestValida();
        request.setIntervaloMinutos(-1);
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void deveAssociarProfissionaisDaMesmaUnidade() {
        ServicoRequest request = requestValida();
        request.setProfissionalIds(Set.of(profA.getId(), profB.getId()));

        ServicoResponse resposta = servicoService.criar(unidade.getId(), request);

        assertEquals(2, resposta.getProfissionais().size());
    }

    @Test
    void deveRejeitarProfissionalDeOutraUnidade() {
        ServicoRequest request = requestValida();
        request.setProfissionalIds(Set.of(profOutraUnidade.getId()));

        assertThrows(RegraDeNegocioException.class,
                () -> servicoService.criar(unidade.getId(), request));
    }

    @Test
    void deveRejeitarNomeDuplicadoNaMesmaUnidade() {
        servicoService.criar(unidade.getId(), requestValida());

        assertThrows(ConflitoException.class,
                () -> servicoService.criar(unidade.getId(), requestValida()));
    }

    @Test
    void devePermitirMesmoNomeEmUnidadesDiferentes() {
        servicoService.criar(unidade.getId(), requestValida());
        ServicoResponse resposta = servicoService.criar(outraUnidade.getId(), requestValida());

        assertNotNull(resposta.getId());
    }

    @Test
    void servicoSemProfissionalNaoApareceComoDisponivel() {
        servicoService.criar(unidade.getId(), requestValida());

        assertTrue(servicoService.listarDisponiveis(unidade.getId()).isEmpty());
    }

    @Test
    void normalizaNomeERejeitaVariacaoDeCaixaEWhitespace() {
        ServicoRequest primeiro = requestValida();
        primeiro.setNome("  Corte de cabelo  ");
        assertEquals("Corte de cabelo", servicoService.criar(unidade.getId(), primeiro).getNome());

        ServicoRequest segundo = requestValida();
        segundo.setNome("cOrTe De CaBeLo");
        assertThrows(ConflitoException.class, () -> servicoService.criar(unidade.getId(), segundo));

        segundo.setNome("  a  ");
        assertThrows(IllegalArgumentException.class, () -> servicoService.criar(unidade.getId(), segundo));
    }

    @Test
    void indiceImpedeDuplicidadeMesmoForaDoServico() {
        servicoService.criar(unidade.getId(), requestValida());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                jdbcTemplate.update("""
                    INSERT INTO servico(unidade_id, nome, duracao_minutos, preco)
                    VALUES (?, '  CORTE DE CABELO  ', 30, 50)
                    """, unidade.getId()));
    }

    @Test
    void publicoNaoVeRascunhoOuInativoEAdminVeListaCompleta() throws Exception {
        ServicoRequest habilitado = requestValida();
        habilitado.setProfissionalIds(Set.of(profA.getId()));
        ServicoResponse publico = servicoService.criar(unidade.getId(), habilitado);
        ServicoRequest rascunho = requestValida();
        rascunho.setNome("Coloração");
        ServicoResponse semProfissional = servicoService.criar(unidade.getId(), rascunho);

        mvc.perform(get("/api/unidades/{id}/servicos", unidade.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/servicos/{id}", semProfissional.getId()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/unidades/{id}/servicos", unidade.getId())
                        .param("somenteDisponiveis", "false"))
                .andExpect(status().isUnauthorized());

        usuarios.save(new Usuario("Admin", "admin.catalogo@example.com", "admin.catalogo@example.com",
                encoder.encode("Senha123"), PerfilUsuario.ADMINISTRADOR, EstadoConta.ATIVA, unidade, null));
        Cookie admin = login("admin.catalogo@example.com", "Senha123");
        mvc.perform(get("/api/unidades/{id}/servicos", unidade.getId())
                        .param("somenteDisponiveis", "false").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/servicos/{id}", semProfissional.getId()).cookie(admin))
                .andExpect(status().isOk());
        mvc.perform(get("/api/unidades/{id}/servicos", outraUnidade.getId())
                        .param("somenteDisponiveis", "false").cookie(admin))
                .andExpect(status().isForbidden());

        servicoService.desativar(publico.getId());
        mvc.perform(get("/api/servicos/{id}", publico.getId())).andExpect(status().isNotFound());
    }

    @Test
    void profissionalInativadoRemoveServicoDaVisaoPublica() {
        ServicoRequest pedido = requestValida();
        pedido.setProfissionalIds(Set.of(profA.getId()));
        servicoService.criar(unidade.getId(), pedido);
        assertEquals(1, servicoService.listarDisponiveis(unidade.getId()).size());
        profA.setAtivo(false);
        profissionalRepository.save(profA);
        assertTrue(servicoService.listarDisponiveis(unidade.getId()).isEmpty());
    }

    @Test
    void administradorCriaEditaEDesativaServicoPelaApi() throws Exception {
        usuarios.save(new Usuario("Admin", "catalogo@example.com", "catalogo@example.com",
                encoder.encode("Senha123"), PerfilUsuario.ADMINISTRADOR, EstadoConta.ATIVA, unidade, null));
        Cookie admin = login("catalogo@example.com", "Senha123");
        String cadastro = "{\"nome\":\"  Corte social  \",\"duracaoMinutos\":30,\"preco\":45.00} ";
        mvc.perform(post("/api/unidades/{id}/servicos", unidade.getId()).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(cadastro))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nome").value("Corte social"));
        long servicoId = jdbcTemplate.queryForObject("SELECT id FROM servico WHERE nome = 'Corte social'", Long.class);

        mvc.perform(get("/api/unidades/{id}/servicos", unidade.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        String edicao = "{\"nome\":\"Corte social\",\"duracaoMinutos\":40,\"preco\":55.50,"
                + "\"profissionalIds\":[" + profA.getId() + "]}";
        mvc.perform(put("/api/servicos/{id}", servicoId).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(edicao))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duracaoMinutos").value(40));
        mvc.perform(get("/api/unidades/{id}/servicos", unidade.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        mvc.perform(post("/api/unidades/{id}/servicos", unidade.getId()).cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"  CORTE SOCIAL \",\"duracaoMinutos\":30,\"preco\":10}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/servicos/{id}/desativar", servicoId).cookie(admin).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
        mvc.perform(get("/api/unidades/{id}/servicos", unidade.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/unidades/{id}/servicos", unidade.getId())
                        .param("somenteDisponiveis", "false").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].ativo").value(false));
    }

    @Test
    void profissionalInativoNaoApareceNoCatalogoPublico() {
        ServicoRequest request = requestValida();
        request.setProfissionalIds(Set.of(profA.getId(), profB.getId()));
        servicoService.criar(unidade.getId(), request);

        profA.setAtivo(false);
        profissionalRepository.save(profA);

        ServicoResponse publico = servicoService.listarDisponiveis(unidade.getId()).get(0);
        assertEquals(1, publico.getProfissionais().size());
        assertEquals(profB.getId(), publico.getProfissionais().get(0).id());
    }

    @Test
    void servicoDesativadoNaoApareceComoDisponivelMasPermaneceNaListagem() {
        ServicoRequest request = requestValida();
        request.setProfissionalIds(Set.of(profA.getId()));
        ServicoResponse criado = servicoService.criar(unidade.getId(), request);

        servicoService.desativar(criado.getId());

        assertTrue(servicoService.listarDisponiveis(unidade.getId()).isEmpty());
        assertEquals(1, servicoService.listar(unidade.getId()).size());
        assertFalse(servicoService.listar(unidade.getId()).get(0).isAtivo());
    }

    @Test
    void desativarNaoRemoveFisicamenteOservico() {
        ServicoResponse criado = servicoService.criar(unidade.getId(), requestValida());
        Long id = criado.getId();

        servicoService.desativar(id);

        Servico servico = servicoRepository.findById(id).orElseThrow();
        assertFalse(servico.isAtivo());
    }

    @Test
    void buscarServicoInexistenteLancaExcecao() {
        assertThrows(RecursoNaoEncontradoException.class, () -> servicoService.buscar(999999L));
    }

    private ServicoRequest requestValida() {
        ServicoRequest request = new ServicoRequest();
        request.setNome("Corte de cabelo");
        request.setDescricao("Corte com tesoura e maquina");
        request.setDuracaoMinutos(30);
        request.setPreco(new BigDecimal("50.00"));
        return request;
    }

    private Cookie login(String email, String senha) throws Exception {
        Cookie cookie = mvc.perform(post("/api/autenticacao/sessoes").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        assertNotNull(cookie);
        return cookie;
    }
}
