package br.ufpa.dsai.estilomarcado.autenticacao;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

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
import br.ufpa.dsai.estilomarcado.autenticacao.service.UsuarioInternoService;
import br.ufpa.dsai.estilomarcado.catalogo.api.exception.ConflitoException;
import br.ufpa.dsai.estilomarcado.catalogo.model.Profissional;
import br.ufpa.dsai.estilomarcado.catalogo.model.Unidade;
import br.ufpa.dsai.estilomarcado.catalogo.repository.ProfissionalRepository;
import br.ufpa.dsai.estilomarcado.catalogo.repository.UnidadeRepository;
import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = "app.auth.password-cost=4")
@AutoConfigureMockMvc
@Testcontainers
class AutenticacaoIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final AtomicInteger ORIGEM = new AtomicInteger(10);

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired UnidadeRepository unidadeRepository;
    @Autowired ProfissionalRepository profissionalRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UsuarioInternoService usuarioInternoService;

    @MockitoBean EmailAutenticacaoGateway emailGateway;

    private Unidade unidade;
    private Unidade outraUnidade;
    private Profissional profissional;

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
        reset(emailGateway);

        unidade = unidadeRepository.save(new Unidade("Unidade Centro"));
        outraUnidade = unidadeRepository.save(new Unidade("Unidade Norte"));
        profissional = profissionalRepository.save(new Profissional("Ana", unidade));
    }

    @Test
    void cadastroAtivacaoLoginESessaoFuncionamDePontaAPonta() throws Exception {
        cadastrar("  Cliente@Example.com  ");

        Usuario pendente = usuarioRepository.findByEmailNormalizado("cliente@example.com").orElseThrow();
        assertEquals(EstadoConta.PENDENTE, pendente.getEstado());
        assertNotEquals("Senha123", pendente.getSenhaHash());
        assertTrue(passwordEncoder.matches("Senha123", pendente.getSenhaHash()));

        login("cliente@example.com", "Senha123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem", is("e-mail ou senha invalidos")));

        String token = capturarTokenAtivacao();
        mockMvc.perform(post("/api/autenticacao/ativacoes")
                        .with(csrf()).with(this::novaOrigem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/autenticacao/ativacoes")
                        .with(csrf()).with(this::novaOrigem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isUnprocessableEntity());

        MvcResult login = login("CLIENTE@example.com", "Senha123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil", is("CLIENTE")))
                .andExpect(cookie().httpOnly("SESSION", true))
                .andReturn();
        Cookie sessao = login.getResponse().getCookie("SESSION");
        assertNotNull(sessao);

        mockMvc.perform(get("/api/autenticacao/sessao").cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("cliente@example.com")));
    }

    @Test
    void cadastroDuplicadoMantemRespostaGenericaENaoDuplicaConta() throws Exception {
        String primeira = cadastrar("cliente@example.com").andReturn().getResponse().getContentAsString();
        reset(emailGateway);
        String segunda = cadastrar(" CLIENTE@example.com ").andReturn().getResponse().getContentAsString();

        assertEquals(primeira, segunda);
        assertEquals(1, usuarioRepository.count());
        verifyNoInteractions(emailGateway);
    }

    @Test
    void bootstrapPromoveClienteAtivoMesmoComOutroAdministradorEEncerraSessaoAntiga() throws Exception {
        Usuario cliente = clienteAtivo("meu.email@example.com", "Senha123");
        Cookie sessao = login("meu.email@example.com", "Senha123")
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
        assertNotNull(sessao);
        usuarioRepository.save(new Usuario("Outro administrador", "outro@example.com",
                "outro@example.com", passwordEncoder.encode("Senha123"),
                PerfilUsuario.ADMINISTRADOR, EstadoConta.ATIVA, unidade, null));

        usuarioInternoService.garantirAdministradorConfigurado(unidade.getId(),
                "Nome no ambiente", " MEU.EMAIL@example.com ");

        Usuario promovido = usuarioRepository.findById(cliente.getId()).orElseThrow();
        assertEquals(PerfilUsuario.ADMINISTRADOR, promovido.getPerfil());
        assertEquals(unidade.getId(), promovido.getUnidade().getId());
        assertEquals(EstadoConta.ATIVA, promovido.getEstado());
        mockMvc.perform(get("/api/autenticacao/sessao").cookie(sessao))
                .andExpect(status().isUnauthorized());
        login("meu.email@example.com", "Senha123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil", is("ADMINISTRADOR")));
    }

    @Test
    void bootstrapCriaAdministradorAdicionalPendenteERepeticaoNaoDuplicaConvite() {
        usuarioRepository.save(new Usuario("Outro administrador", "outro@example.com",
                "outro@example.com", passwordEncoder.encode("Senha123"),
                PerfilUsuario.ADMINISTRADOR, EstadoConta.ATIVA, unidade, null));

        usuarioInternoService.garantirAdministradorConfigurado(unidade.getId(),
                "Administrador configurado", "novo@example.com");
        Usuario novo = usuarioRepository.findByEmailNormalizado("novo@example.com").orElseThrow();
        assertEquals(PerfilUsuario.ADMINISTRADOR, novo.getPerfil());
        assertEquals(EstadoConta.PENDENTE, novo.getEstado());
        assertEquals(unidade.getId(), novo.getUnidade().getId());
        verify(emailGateway).enviarConvite(eq("novo@example.com"),
                eq("Administrador configurado"), anyString());

        usuarioInternoService.garantirAdministradorConfigurado(unidade.getId(),
                "Administrador configurado", "novo@example.com");
        assertEquals(2, usuarioRepository.count());
        verify(emailGateway).enviarConvite(eq("novo@example.com"),
                eq("Administrador configurado"), anyString());
    }

    @Test
    void bootstrapNaoPromoveContaPendenteOuOutroPerfilInterno() {
        Usuario pendente = usuarioRepository.save(new Usuario("Cliente", "pendente@example.com",
                "pendente@example.com", null, PerfilUsuario.CLIENTE,
                EstadoConta.PENDENTE, null, null));
        Usuario recepcao = usuarioRepository.save(new Usuario("Recepção", "equipe@example.com",
                "equipe@example.com", null, PerfilUsuario.RECEPCAO,
                EstadoConta.ATIVA, unidade, null));

        assertThrows(ConflitoException.class, () -> usuarioInternoService
                .garantirAdministradorConfigurado(unidade.getId(), "Cliente", "pendente@example.com"));
        assertThrows(ConflitoException.class, () -> usuarioInternoService
                .garantirAdministradorConfigurado(unidade.getId(), "Recepção", "equipe@example.com"));
        assertEquals(PerfilUsuario.CLIENTE,
                usuarioRepository.findById(pendente.getId()).orElseThrow().getPerfil());
        assertEquals(PerfilUsuario.RECEPCAO,
                usuarioRepository.findById(recepcao.getId()).orElseThrow().getPerfil());
    }

    @Test
    void respostasDeLoginNaoPermitemEnumerarContaEBloqueiamAposCincoFalhas() throws Exception {
        Usuario usuario = clienteAtivo("cliente@example.com", "Senha123");
        login("ninguem@example.com", "Errada123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem", is("e-mail ou senha invalidos")));
        login("cliente@example.com", "Errada123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensagem", is("e-mail ou senha invalidos")));

        for (int i = 0; i < 4; i++) {
            login("cliente@example.com", "Errada123").andExpect(status().isUnauthorized());
        }
        login("cliente@example.com", "Senha123").andExpect(status().isUnauthorized());
        assertEquals(EstadoConta.BLOQUEADA, usuarioRepository.findById(usuario.getId()).orElseThrow().getEstado());
    }

    @Test
    void recuperacaoTrocaSenhaInvalidaTokenESessaoAnterior() throws Exception {
        clienteAtivo("cliente@example.com", "Senha123");
        Cookie sessao = login("cliente@example.com", "Senha123").andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");
        assertNotNull(sessao);
        reset(emailGateway);

        mockMvc.perform(post("/api/autenticacao/recuperacoes")
                        .with(csrf()).with(this::novaOrigem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"cliente@example.com\"}"))
                .andExpect(status().isAccepted());
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailGateway).enviarRecuperacao(eq("cliente@example.com"), anyString(), tokenCaptor.capture());

        String corpo = "{\"token\":\"" + tokenCaptor.getValue()
                + "\",\"senha\":\"NovaSenha456\",\"confirmacaoSenha\":\"NovaSenha456\"}";
        mockMvc.perform(post("/api/autenticacao/redefinicoes")
                        .with(csrf()).with(this::novaOrigem)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/autenticacao/sessao").cookie(sessao))
                .andExpect(status().isUnauthorized());
        login("cliente@example.com", "Senha123").andExpect(status().isUnauthorized());
        login("cliente@example.com", "NovaSenha456").andExpect(status().isOk());
        mockMvc.perform(post("/api/autenticacao/redefinicoes")
                        .with(csrf()).with(this::novaOrigem)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void recuperacaoTemMesmaRespostaParaEmailExistenteEInexistente() throws Exception {
        clienteAtivo("cliente@example.com", "Senha123");
        String existente = solicitarRecuperacao("cliente@example.com");
        String inexistente = solicitarRecuperacao("ninguem@example.com");
        assertEquals(existente, inexistente);
    }

    @Test
    void conviteCriaContaProfissionalPendenteEApenasTokenValidoAtiva() throws Exception {
        Cookie sessaoAdmin = loginAdmin(unidade);
        reset(emailGateway);
        String corpo = "{\"nome\":\"Ana Silva\",\"email\":\"ana@example.com\","
                + "\"perfil\":\"PROFISSIONAL\",\"profissionalId\":" + profissional.getId() + "}";
        mockMvc.perform(post("/api/unidades/{id}/usuarios-internos", unidade.getId())
                        .cookie(sessaoAdmin).with(csrf()).with(this::novaOrigem)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado", is("PENDENTE")))
                .andExpect(jsonPath("$.perfil", is("PROFISSIONAL")));

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailGateway).enviarConvite(eq("ana@example.com"), anyString(), tokenCaptor.capture());
        login("ana@example.com", "Senha123").andExpect(status().isUnauthorized());

        String convite = "{\"token\":\"" + tokenCaptor.getValue()
                + "\",\"senha\":\"Senha123\",\"confirmacaoSenha\":\"Senha123\"}";
        mockMvc.perform(post("/api/autenticacao/convites")
                        .with(csrf()).with(this::novaOrigem)
                        .contentType(MediaType.APPLICATION_JSON).content(convite))
                .andExpect(status().isNoContent());
        login("ana@example.com", "Senha123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil", is("PROFISSIONAL")));
    }

    @Test
    void autorizacaoDistingueNaoAutenticadoPerfilEUnidade() throws Exception {
        String servico = "{\"nome\":\"Corte\",\"duracaoMinutos\":30,\"preco\":50}";
        mockMvc.perform(get("/api/unidades/{id}/servicos", unidade.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/unidades/{id}/servicos", unidade.getId())
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(servico))
                .andExpect(status().isUnauthorized());

        clienteAtivo("cliente@example.com", "Senha123");
        Cookie cliente = login("cliente@example.com", "Senha123").andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");
        mockMvc.perform(post("/api/unidades/{id}/servicos", unidade.getId())
                        .cookie(cliente).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(servico))
                .andExpect(status().isForbidden());

        Cookie admin = loginAdmin(unidade);
        mockMvc.perform(post("/api/unidades/{id}/usuarios-internos", outraUnidade.getId())
                        .cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Recepcao\",\"email\":\"r@example.com\",\"perfil\":\"RECEPCAO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejeitaMutacaoSemCsrfECamposDesconhecidos() throws Exception {
        mockMvc.perform(post("/api/autenticacao/sessoes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@example.com\",\"senha\":\"Senha123\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/autenticacao/cadastros")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Cliente\",\"email\":\"x@example.com\",\"senha\":\"Senha123\","
                                + "\"confirmacaoSenha\":\"Senha123\",\"perfil\":\"ADMINISTRADOR\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clienteSoAlteraOProprioNome() throws Exception {
        clienteAtivo("cliente@example.com", "Senha123");
        Cookie sessao = login("cliente@example.com", "Senha123").andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");
        mockMvc.perform(patch("/api/usuarios/me").cookie(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Novo Nome\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome", is("Novo Nome")))
                .andExpect(jsonPath("$.perfil", is("CLIENTE")));
    }

    @Test
    void perfilSalvaNormalizaERemoveTelefoneSemAlterarIdentidade() throws Exception {
        Usuario usuario = clienteAtivo("cliente@example.com", "Senha123");
        Cookie sessao = login("cliente@example.com", "Senha123").andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");

        mockMvc.perform(get("/api/usuarios/me").cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefoneContato", nullValue()))
                .andExpect(jsonPath("$.filial", nullValue()))
                .andExpect(jsonPath("$.estabelecimento", nullValue()));

        mockMvc.perform(patch("/api/usuarios/me").cookie(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nome Atualizado\",\"telefoneContato\":\"(91) 99999-1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefoneContato", is("+5591999991234")));
        mockMvc.perform(get("/api/autenticacao/sessao").cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome", is("Nome Atualizado")));
        mockMvc.perform(get("/api/usuarios/me").cookie(sessao))
                .andExpect(jsonPath("$.telefoneContato", is("+5591999991234")));

        mockMvc.perform(patch("/api/usuarios/me").cookie(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nome Atualizado\"}"))
                .andExpect(jsonPath("$.telefoneContato", is("+5591999991234")));
        mockMvc.perform(patch("/api/usuarios/me").cookie(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Nome Atualizado\",\"telefoneContato\":null}"))
                .andExpect(jsonPath("$.telefoneContato", nullValue()));
        assertEquals("cliente@example.com", usuarioRepository.findById(usuario.getId()).orElseThrow().getEmail());
    }

    @Test
    void perfilRejeitaTelefoneInvalidoECamposProtegidos() throws Exception {
        clienteAtivo("cliente@example.com", "Senha123");
        Cookie sessao = login("cliente@example.com", "Senha123").andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");

        mockMvc.perform(patch("/api/usuarios/me").cookie(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Cliente\",\"telefoneContato\":\"99999-1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", containsString("telefoneContato")));
        for (String campo : new String[] {"email", "perfil", "estado", "unidadeId",
                "profissionalId", "estabelecimentoId"}) {
            mockMvc.perform(patch("/api/usuarios/me").cookie(sessao).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nome\":\"Cliente\",\"" + campo + "\":1}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/usuarios/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/usuarios/me").cookie(sessao)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Cliente\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void perfilInternoMostraSomenteOProprioVinculoETelefonePessoalNaoVaza() throws Exception {
        Cookie admin = loginAdmin(unidade);
        mockMvc.perform(get("/api/usuarios/me").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filial.id", is(unidade.getId().intValue())))
                .andExpect(jsonPath("$.filial.nome", is("Unidade Centro")))
                .andExpect(jsonPath("$.estabelecimento.nome", is("Unidade Centro")));
        mockMvc.perform(patch("/api/usuarios/me").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Administrador\",\"telefoneContato\":\"+351912345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefoneContato", is("+351912345678")));
        mockMvc.perform(get("/api/unidades/{id}/usuarios-internos", unidade.getId()).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].telefoneContato").doesNotExist());
    }

    @Test
    void minhaFilialLeDadosDaContaMesmoInativaESemAceitarIdAlheio() throws Exception {
        unidade.setEndereco("Rua das Flores, 10");
        unidade.setTelefone("(91) 3000-1000");
        unidade.setAtiva(false);
        unidadeRepository.save(unidade);
        String email = "profissional@example.com";
        usuarioRepository.save(new Usuario("Ana", email, email,
                passwordEncoder.encode("Senha123"), PerfilUsuario.PROFISSIONAL,
                EstadoConta.ATIVA, unidade, profissional));
        Cookie sessao = login(email, "Senha123").andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");

        mockMvc.perform(get("/api/unidades/me").cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(unidade.getId().intValue())))
                .andExpect(jsonPath("$.endereco", is("Rua das Flores, 10")))
                .andExpect(jsonPath("$.telefone", is("(91) 3000-1000")))
                .andExpect(jsonPath("$.ativa", is(false)));
        mockMvc.perform(get("/api/unidades/{id}", outraUnidade.getId()).cookie(sessao))
                .andExpect(status().isForbidden());

        clienteAtivo("cliente@example.com", "Senha123");
        Cookie cliente = login("cliente@example.com", "Senha123").andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");
        mockMvc.perform(get("/api/unidades/me").cookie(cliente))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutInvalidaSessaoEEhIdempotente() throws Exception {
        clienteAtivo("cliente@example.com", "Senha123");
        Cookie sessao = login("cliente@example.com", "Senha123").andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");
        assertNotNull(sessao);

        mockMvc.perform(delete("/api/autenticacao/sessao").cookie(sessao).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("SESSION", 0));
        mockMvc.perform(get("/api/autenticacao/sessao").cookie(sessao))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/autenticacao/sessao").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void bootstrapCriaSomenteOPrimeiroAdministradorComoPendente() {
        usuarioInternoService.provisionarPrimeiroAdministrador(
                unidade.getId(), "Admin Inicial", "admin.inicial@example.com");
        usuarioInternoService.provisionarPrimeiroAdministrador(
                unidade.getId(), "Outro Admin", "outro@example.com");

        Usuario admin = usuarioRepository.findByEmailNormalizado("admin.inicial@example.com").orElseThrow();
        assertEquals(PerfilUsuario.ADMINISTRADOR, admin.getPerfil());
        assertEquals(EstadoConta.PENDENTE, admin.getEstado());
        assertEquals(1, usuarioRepository.count());
        verify(emailGateway).enviarConvite(eq("admin.inicial@example.com"), eq("Admin Inicial"), anyString());
    }

    private org.springframework.test.web.servlet.ResultActions cadastrar(String email) throws Exception {
        return mockMvc.perform(post("/api/autenticacao/cadastros")
                        .with(csrf()).with(this::novaOrigem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Cliente Teste\",\"email\":\"" + email
                                + "\",\"senha\":\"Senha123\",\"confirmacaoSenha\":\"Senha123\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensagem", containsString("instrucoes de ativacao")));
    }

    private String capturarTokenAtivacao() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(emailGateway).enviarAtivacao(eq("Cliente@Example.com"), eq("Cliente Teste"), captor.capture());
        return captor.getValue();
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String senha) throws Exception {
        return mockMvc.perform(post("/api/autenticacao/sessoes")
                .with(csrf()).with(this::novaOrigem)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"));
    }

    private String solicitarRecuperacao(String email) throws Exception {
        return mockMvc.perform(post("/api/autenticacao/recuperacoes")
                        .with(csrf()).with(this::novaOrigem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
    }

    private Usuario clienteAtivo(String email, String senha) {
        return usuarioRepository.save(new Usuario("Cliente", email, email.toLowerCase(),
                passwordEncoder.encode(senha), PerfilUsuario.CLIENTE, EstadoConta.ATIVA, null, null));
    }

    private Cookie loginAdmin(Unidade unidadeAdmin) throws Exception {
        String email = "admin" + unidadeAdmin.getId() + "@example.com";
        usuarioRepository.save(new Usuario("Administrador", email, email,
                passwordEncoder.encode("Senha123"), PerfilUsuario.ADMINISTRADOR,
                EstadoConta.ATIVA, unidadeAdmin, null));
        Cookie cookie = login(email, "Senha123").andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");
        assertNotNull(cookie);
        return cookie;
    }

    private org.springframework.mock.web.MockHttpServletRequest novaOrigem(
            org.springframework.mock.web.MockHttpServletRequest request) {
        request.setRemoteAddr("10.0.0." + ORIGEM.incrementAndGet());
        return request;
    }
}
