package br.ufpa.dsai.estilomarcado.estabelecimento;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FilialDescobertaIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE TABLE estabelecimento RESTART IDENTITY CASCADE");
    }

    @Test
    void consultaAnonimaNaoExpoeFiliaisOuServicosIndisponiveisNemDadosPrivados() throws Exception {
        long estabelecimento = estabelecimento("Salão São José");
        long centro = filial(estabelecimento, "Centro", true, true);
        long inativa = filial(estabelecimento, "Norte", false, false);
        long semServico = filial(estabelecimento, "Sul", true, false);
        long semProfissional = filial(estabelecimento, "Leste", true, false);
        long corte = servico(centro, "Corte de cabelo", true, true, "35.00");
        servico(inativa, "Manicure", true, true, "40.00");
        servico(semServico, "Escova", false, true, "60.00");
        servico(semProfissional, "Coloração", true, false, "90.00");

        mvc.perform(get("/api/filiais/publicas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].id").value(centro))
                .andExpect(jsonPath("$.itens[0].estabelecimento").value("Salão São José"))
                .andExpect(jsonPath("$.itens[0].endereco").value("Rua Pública, 10"))
                .andExpect(jsonPath("$.itens[0].servicos[0].codigo").value("corte-de-cabelo"))
                .andExpect(jsonPath("$.itens[0].telefone").doesNotExist())
                .andExpect(jsonPath("$.itens[0].profissionais").doesNotExist());
        mvc.perform(get("/api/filiais/publicas/servicos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codigo").value("corte-de-cabelo"));
        mvc.perform(get("/api/unidades/{id}/publico", inativa))
                .andExpect(status().isNotFound());

        jdbc.update("UPDATE servico SET ativo = false WHERE id = ?", corte);
        mvc.perform(get("/api/filiais/publicas"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void buscaSemAcentoFiltroEPaginacaoTemOrdemEstavel() throws Exception {
        long acai = estabelecimento("Açaí Beleza");
        long bela = estabelecimento("Bela Arte");
        long primeira = filial(acai, "Centro", true, true);
        long segunda = filial(bela, "Norte", true, true);
        long terceira = filial(bela, "Sul", true, false);
        servico(primeira, "Corte de cabelo", true, true, "30.00");
        servico(primeira, "Escova", true, true, "75.00");
        servico(segunda, "Corte de cabelo", true, true, "40.00");
        servico(terceira, "Manicure", true, true, "45.00");

        mvc.perform(get("/api/filiais/publicas").param("busca", "Acai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].id").value(primeira))
                .andExpect(jsonPath("$.itens[0].precoMinimo").value(30.0))
                .andExpect(jsonPath("$.itens[0].precoMaximo").value(75.0));
        mvc.perform(get("/api/filiais/publicas").param("servico", "corte-de-cabelo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/filiais/publicas")
                        .param("servico", "escova", "manicure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.itens[0].id").value(primeira))
                .andExpect(jsonPath("$.itens[1].id").value(terceira));
        mvc.perform(get("/api/filiais/publicas").param("tamanho", "1").param("pagina", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaginas").value(3))
                .andExpect(jsonPath("$.itens[0].id").value(primeira));
        mvc.perform(get("/api/filiais/publicas").param("tamanho", "1").param("pagina", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].id").value(segunda));
        mvc.perform(get("/api/filiais/publicas").param("tamanho", "1").param("pagina", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].id").value(terceira));
    }

    @Test
    void rejeitaFiltrosInvalidosComCampoIdentificado() throws Exception {
        mvc.perform(get("/api/filiais/publicas").param("busca", "a"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.busca").exists());
        mvc.perform(get("/api/filiais/publicas").param("servico", "1 OR true"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.servico").exists());
        mvc.perform(get("/api/filiais/publicas").param("pagina", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.pagina").exists());
        mvc.perform(get("/api/filiais/publicas").param("tamanho", "25"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.tamanho").exists());
    }

    private long estabelecimento(String nome) {
        return jdbc.queryForObject("INSERT INTO estabelecimento(nome) VALUES (?) RETURNING id", Long.class, nome);
    }

    private long filial(long estabelecimento, String nome, boolean ativa, boolean principal) {
        return jdbc.queryForObject("""
                INSERT INTO unidade(nome, nome_normalizado, estabelecimento_id, principal,
                                    endereco, ativa, fuso_horario)
                VALUES (?, lower(?), ?, ?, 'Rua Pública, 10', ?, 'America/Sao_Paulo') RETURNING id
                """, Long.class, nome, nome, estabelecimento, principal, ativa);
    }

    private long servico(long unidade, String nome, boolean ativo, boolean profissionalAtivo, String preco) {
        long profissional = jdbc.queryForObject("""
                INSERT INTO profissional(unidade_id, nome, ativo)
                VALUES (?, 'Profissional de teste', ?) RETURNING id
                """, Long.class, unidade, profissionalAtivo);
        long servico = jdbc.queryForObject("""
                INSERT INTO servico(unidade_id, nome, duracao_minutos, preco, ativo)
                VALUES (?, ?, 40, ?, ?) RETURNING id
                """, Long.class, unidade, nome, new java.math.BigDecimal(preco), ativo);
        jdbc.update("INSERT INTO servico_profissional(servico_id, profissional_id) VALUES (?, ?)",
                servico, profissional);
        return servico;
    }
}
