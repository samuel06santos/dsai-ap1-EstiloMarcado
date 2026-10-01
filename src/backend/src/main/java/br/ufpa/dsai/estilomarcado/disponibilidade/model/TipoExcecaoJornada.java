package br.ufpa.dsai.estilomarcado.disponibilidade.model;

/**
 * Tipo de excecao de data da jornada de um profissional.
 *
 * <p>{@code FOLGA} fecha o dia inteiro; {@code JORNADA_ESPECIAL} substitui a
 * jornada semanal por intervalos proprios naquela data.</p>
 */
public enum TipoExcecaoJornada {
    FOLGA,
    JORNADA_ESPECIAL
}
