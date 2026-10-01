package br.ufpa.dsai.estilomarcado.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
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
import jakarta.validation.Validator;

@SpringBootTest
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
}
