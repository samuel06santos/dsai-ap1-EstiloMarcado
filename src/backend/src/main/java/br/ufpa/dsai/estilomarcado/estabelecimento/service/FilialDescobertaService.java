package br.ufpa.dsai.estilomarcado.estabelecimento.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.FiliaisPublicasResponse;
import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.FiliaisPublicasResponse.FilialResumo;
import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.FiliaisPublicasResponse.ServicoOpcao;
import br.ufpa.dsai.estilomarcado.estabelecimento.api.dto.FiliaisPublicasResponse.ServicoResumo;
import br.ufpa.dsai.estilomarcado.estabelecimento.repository.FilialDescobertaRepository;
import br.ufpa.dsai.estilomarcado.estabelecimento.repository.FilialDescobertaRepository.ServicoBasico;

@Service
public class FilialDescobertaService {
    private final FilialDescobertaRepository repository;

    public FilialDescobertaService(FilialDescobertaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public FiliaisPublicasResponse listar(String busca, List<String> servico, String pagina, String tamanho) {
        String termo = validarBusca(busca);
        List<String> codigos = validarServicos(servico);
        int numeroPagina = inteiro(pagina, "pagina", 1, 10_000);
        int tamanhoPagina = inteiro(tamanho, "tamanho", 1, 24);
        long total = repository.contar(termo, codigos);
        var filiais = repository.listar(termo, codigos, numeroPagina, tamanhoPagina);
        Map<Long, List<ServicoBasico>> servicos = repository.servicos(
                filiais.stream().map(FilialDescobertaRepository.FilialBasica::id).toList());
        List<FilialResumo> itens = filiais.stream().map(filial -> {
            List<ServicoBasico> disponiveis = servicos.getOrDefault(filial.id(), List.of());
            BigDecimal minimo = disponiveis.stream().map(ServicoBasico::precoMinimo)
                    .min(BigDecimal::compareTo).orElse(null);
            BigDecimal maximo = disponiveis.stream().map(ServicoBasico::precoMinimo)
                    .max(BigDecimal::compareTo).orElse(null);
            return new FilialResumo(filial.id(), filial.nome(), filial.estabelecimento(),
                    filial.endereco(), minimo, maximo, disponiveis.stream()
                        .map(s -> new ServicoResumo(s.codigo(), s.nome(), s.precoMinimo())).toList());
        }).toList();
        int totalPaginas = (int) ((total + tamanhoPagina - 1) / tamanhoPagina);
        return new FiliaisPublicasResponse(itens, total, numeroPagina, tamanhoPagina, totalPaginas);
    }

    @Transactional(readOnly = true)
    public List<ServicoOpcao> servicos() {
        return repository.opcoes().stream()
                .map(opcao -> new ServicoOpcao(opcao.codigo(), opcao.nome())).toList();
    }

    private String validarBusca(String busca) {
        String limpa = busca == null ? "" : busca.trim();
        if (!limpa.isEmpty() && (limpa.length() < 2 || limpa.length() > 80
                || limpa.codePoints().anyMatch(Character::isISOControl))) {
            throw new ConsultaFiliaisInvalidaException("busca", "informe de 2 a 80 caracteres");
        }
        return Normalizer.normalize(limpa, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    private List<String> validarServicos(List<String> servicos) {
        if (servicos == null) { return List.of(); }
        if (servicos.size() > 50) {
            throw new ConsultaFiliaisInvalidaException("servico", "selecione ate 50 servicos");
        }
        return servicos.stream().map(String::trim).filter(codigo -> !codigo.isEmpty())
                .peek(codigo -> {
                    if (codigo.length() > 160 || !codigo.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
                        throw new ConsultaFiliaisInvalidaException("servico", "codigo de servico invalido");
                    }
                }).distinct().toList();
    }

    private int inteiro(String valor, String campo, int minimo, int maximo) {
        try {
            int numero = Integer.parseInt(valor);
            if (numero >= minimo && numero <= maximo) { return numero; }
        } catch (NumberFormatException ignored) {
            // Resposta de validacao identica para formatos e faixas invalidas.
        }
        throw new ConsultaFiliaisInvalidaException(campo,
                "informe um numero entre " + minimo + " e " + maximo);
    }
}
