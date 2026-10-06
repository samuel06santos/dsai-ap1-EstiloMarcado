package br.ufpa.dsai.estilomarcado.autenticacao;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;

import br.ufpa.dsai.estilomarcado.autenticacao.service.AdministradorBootstrap;
import br.ufpa.dsai.estilomarcado.autenticacao.service.UsuarioInternoService;
import br.ufpa.dsai.estilomarcado.catalogo.repository.UnidadeRepository;

class AdministradorBootstrapTest {
    @Test
    void aplicaAdministradorInicialEAdicionalNaMesmaUnidade() {
        UsuarioInternoService usuarios = mock(UsuarioInternoService.class);
        UnidadeRepository unidades = mock(UnidadeRepository.class);
        AdministradorBootstrap bootstrap = new AdministradorBootstrap(usuarios, unidades,
                1L, "Unidade Administrativa", "Admin inicial", "inicial@example.com",
                "Novo admin", "novo@example.com");

        bootstrap.run(null);

        verify(usuarios).garantirAdministradorConfigurado(1L, "Admin inicial", "inicial@example.com");
        verify(usuarios).garantirAdministradorConfigurado(1L, "Novo admin", "novo@example.com");
        verifyNoMoreInteractions(usuarios);
    }

    @Test
    void falhaNoAdministradorAdicionalNaoDerrubaAplicacao() {
        UsuarioInternoService usuarios = mock(UsuarioInternoService.class);
        UnidadeRepository unidades = mock(UnidadeRepository.class);
        AdministradorBootstrap bootstrap = new AdministradorBootstrap(usuarios, unidades,
                1L, "Unidade Administrativa", "Admin inicial", "inicial@example.com",
                "Novo admin", "novo@example.com");
        doThrow(new IllegalStateException("identidade indisponivel"))
                .when(usuarios).garantirAdministradorConfigurado(1L, "Novo admin", "novo@example.com");

        bootstrap.run(null);

        verify(usuarios).garantirAdministradorConfigurado(1L, "Admin inicial", "inicial@example.com");
        verify(usuarios).garantirAdministradorConfigurado(1L, "Novo admin", "novo@example.com");
    }

    @Test
    void falhaNoAdministradorInicialNaoImpedeOutroAdministradorNemLogin() {
        UsuarioInternoService usuarios = mock(UsuarioInternoService.class);
        UnidadeRepository unidades = mock(UnidadeRepository.class);
        AdministradorBootstrap bootstrap = new AdministradorBootstrap(usuarios, unidades,
                1L, "Unidade Administrativa", "Admin inicial", "inicial@example.com",
                "Novo admin", "novo@example.com");
        doThrow(new IllegalStateException("identidade indisponivel"))
                .when(usuarios).garantirAdministradorConfigurado(1L, "Admin inicial", "inicial@example.com");

        bootstrap.run(null);

        verify(usuarios).garantirAdministradorConfigurado(1L, "Admin inicial", "inicial@example.com");
        verify(usuarios).garantirAdministradorConfigurado(1L, "Novo admin", "novo@example.com");
    }
}
