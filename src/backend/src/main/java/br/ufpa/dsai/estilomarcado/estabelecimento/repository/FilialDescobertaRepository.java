package br.ufpa.dsai.estilomarcado.estabelecimento.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FilialDescobertaRepository {
    private static final String CODIGO_SERVICO =
            "trim(both '-' from regexp_replace(unaccent(lower(s.nome)), '[^a-z0-9]+', '-', 'g'))";
    private static final String SERVICO_AGENDAVEL = """
            exists (select 1 from servico_profissional sp
                     join profissional p on p.id = sp.profissional_id
                    where sp.servico_id = s.id and p.ativo = true
                      and p.unidade_id = s.unidade_id)
            """;
    private static final String FILTRO = ("""
            from unidade u
            join estabelecimento e on e.id = u.estabelecimento_id
            where u.ativa = true
              and exists (select 1 from servico s
                           where s.unidade_id = u.id and s.ativo = true
                             and %s)
              and (:busca = '' or strpos(unaccent(lower(e.nome)), :busca) > 0
                                or strpos(unaccent(lower(u.nome)), :busca) > 0)
              and (:semServicos or exists
                    (select 1 from servico s
                      where s.unidade_id = u.id and s.ativo = true
                        and %s and %s in (:servicos)))
            """).formatted(SERVICO_AGENDAVEL, SERVICO_AGENDAVEL, CODIGO_SERVICO);

    private final NamedParameterJdbcTemplate jdbc;

    public FilialDescobertaRepository(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    public long contar(String busca, List<String> servicos) {
        Long total = jdbc.queryForObject("select count(*) " + FILTRO,
                filtros(busca, servicos), Long.class);
        return total == null ? 0 : total;
    }

    public List<FilialBasica> listar(String busca, List<String> servicos, int pagina, int tamanho) {
        var parametros = filtros(busca, servicos)
                .addValue("limite", tamanho).addValue("deslocamento", (pagina - 1) * tamanho);
        return jdbc.query("""
                select u.id, u.nome, e.nome as estabelecimento, u.endereco
                """ + FILTRO + """
                order by unaccent(lower(e.nome)), unaccent(lower(u.nome)), u.id
                limit :limite offset :deslocamento
                """, parametros, (rs, numero) -> new FilialBasica(
                rs.getLong("id"), rs.getString("nome"), rs.getString("estabelecimento"),
                rs.getString("endereco")));
    }

    public Map<Long, List<ServicoBasico>> servicos(List<Long> unidades) {
        Map<Long, List<ServicoBasico>> porUnidade = new LinkedHashMap<>();
        if (unidades.isEmpty()) { return porUnidade; }
        var parametros = new MapSqlParameterSource("unidades", unidades);
        jdbc.query(("""
                select s.unidade_id, %s as codigo, s.nome, min(s.preco) as preco_minimo
                  from servico s
                 where s.unidade_id in (:unidades) and s.ativo = true and %s
                 group by s.unidade_id, %s, s.nome
                 order by s.unidade_id, %s, s.nome, min(s.id)
                """).formatted(CODIGO_SERVICO, SERVICO_AGENDAVEL, CODIGO_SERVICO, CODIGO_SERVICO),
                parametros, rs -> {
                    long unidadeId = rs.getLong("unidade_id");
                    porUnidade.computeIfAbsent(unidadeId, ignorado -> new ArrayList<>())
                            .add(new ServicoBasico(rs.getString("codigo"), rs.getString("nome"),
                                    rs.getBigDecimal("preco_minimo")));
                });
        return porUnidade;
    }

    public List<ServicoOpcao> opcoes() {
        return jdbc.query(("""
                select %s as codigo, min(s.nome) as nome
                  from servico s
                  join unidade u on u.id = s.unidade_id
                 where u.ativa = true and s.ativo = true and %s
                 group by %s
                 order by %s
                """).formatted(CODIGO_SERVICO, SERVICO_AGENDAVEL, CODIGO_SERVICO, CODIGO_SERVICO),
                new MapSqlParameterSource(), (rs, numero) ->
                        new ServicoOpcao(rs.getString("codigo"), rs.getString("nome")));
    }

    private MapSqlParameterSource filtros(String busca, List<String> servicos) {
        return new MapSqlParameterSource().addValue("busca", busca)
                .addValue("semServicos", servicos.isEmpty())
                .addValue("servicos", servicos.isEmpty() ? List.of("") : servicos);
    }

    public record FilialBasica(Long id, String nome, String estabelecimento, String endereco) {}
    public record ServicoBasico(String codigo, String nome, BigDecimal precoMinimo) {}
    public record ServicoOpcao(String codigo, String nome) {}
}
