/**
 * Regras de apresentação compartilhadas pelos estados de interface descritos em
 * SPEC/2026-10-05-atalhos-contextuais-e-estados-da-interface.md.
 *
 * Funções puras para que carregamento, vazio, "sem resultado" e falha sejam
 * classificados do mesmo modo em todas as telas, e para que filtros mantenham
 * nomes legíveis, persistam na URL e recusem identificadores adulterados.
 */

export type EstadoLista = 'carregando' | 'erro' | 'sem-dados' | 'sem-resultado' | 'conteudo';

export interface SnapshotLista {
  /** Uma consulta está em andamento. */
  carregando: boolean;
  /** A última consulta falhou. Tem precedência sobre o estado de carregamento. */
  erro: boolean;
  /** Itens recebidos na última resposta bem-sucedida. */
  total: number;
  /** Quantidade de filtros aplicados; distingue "sem dados" de "sem resultado". */
  filtrosAtivos: number;
}

/**
 * "Falha de carregamento" nunca pode ser tratada como lista vazia: o erro tem
 * precedência. Em seguida, o carregamento. Só com a consulta concluída é que a
 * ausência de itens vira "sem dados" (nenhum filtro) ou "sem resultado" (com
 * filtro aplicado).
 */
export function classificarLista(snapshot: SnapshotLista): EstadoLista {
  if (snapshot.erro) { return 'erro'; }
  if (snapshot.carregando) { return 'carregando'; }
  if (snapshot.total > 0) { return 'conteudo'; }
  return snapshot.filtrosAtivos > 0 ? 'sem-resultado' : 'sem-dados';
}

export interface FiltroAtivo {
  /** Chave estável usada em testes e na URL. */
  chave: string;
  /** Rótulo legível exibido ao usuário (ex.: "Filial"). */
  rotulo: string;
  /** Valor legível selecionado (ex.: "Centro"), nunca um identificador cru. */
  valor: string;
}

/** Resumo textual e acessível dos filtros ativos, no formato "Filial: Centro · Estado: Confirmado". */
export function resumoFiltros(filtros: readonly FiltroAtivo[]): string {
  return filtros.map(filtro => `${filtro.rotulo}: ${filtro.valor}`).join(' · ');
}

/** Aceita apenas identificadores inteiros positivos. Devolve `null` para o resto. */
export function idUrlValido(valor: string | null | undefined): number | null {
  if (!valor || !/^[1-9]\d*$/.test(valor)) { return null; }
  const id = Number(valor);
  return Number.isSafeInteger(id) ? id : null;
}

/**
 * Um identificador vindo da URL (ou de um seletor) só é aceito se corresponder a
 * uma opção que a sessão pode enxergar. IDs adulterados são descartados no
 * frontend; o backend continua revalidando escopo e perfil.
 */
export function selecaoValida(valor: string | null | undefined,
                               opcoes: readonly { id: number }[]): number | null {
  const id = idUrlValido(valor);
  return id !== null && opcoes.some(opcao => opcao.id === id) ? id : null;
}

/**
 * Monta os parâmetros de URL de uma consulta navegável, descartando valores
 * nulos, indefinidos e vazios para não poluir o endereço com seleções ausentes.
 */
export function parametrosFiltrosUrl(
  filtros: Readonly<Record<string, string | number | null | undefined>>
): Record<string, string> {
  const parametros: Record<string, string> = {};
  for (const [chave, valor] of Object.entries(filtros)) {
    if (valor === null || valor === undefined || valor === '') { continue; }
    parametros[chave] = String(valor);
  }
  return parametros;
}

export type DestinoVazio =
  | 'filiais' | 'unidades' | 'disponibilidade' | 'agendar' | 'periodo'
  | 'preferencias' | 'cadastro-profissional' | 'cadastro-servico';

export interface AcaoVazia {
  rotulo: string;
  /** O que a ação representa; a tela resolve o link ou a rolagem correspondente. */
  destino: DestinoVazio;
}

export interface OrientacaoVazia {
  titulo: string;
  descricao: string;
  acoes: readonly AcaoVazia[];
}

export type ContextoVazio =
  | 'cliente-sem-agendamento'
  | 'recepcao-sem-atendimentos'
  | 'profissional-sem-agenda'
  | 'lista-espera-cliente'
  | 'lista-espera-equipe'
  | 'notificacoes'
  | 'admin-sem-profissional'
  | 'admin-sem-servico';

const ORIENTACOES: Record<ContextoVazio, OrientacaoVazia> = {
  'cliente-sem-agendamento': {
    titulo: 'Nenhum horário agendado',
    descricao: 'Você ainda não tem reservas. Encontre um horário e marque seu atendimento.',
    acoes: [{ rotulo: 'Encontrar horário', destino: 'filiais' }]
  },
  'recepcao-sem-atendimentos': {
    titulo: 'Sem atendimentos nesta data',
    descricao: 'Nenhum atendimento na filial e no período escolhidos. Crie uma reserva ou mude a data.',
    acoes: [
      { rotulo: 'Agendar atendimento', destino: 'agendar' },
      { rotulo: 'Alterar período', destino: 'periodo' }
    ]
  },
  'profissional-sem-agenda': {
    titulo: 'Nenhum atendimento neste dia',
    descricao: 'Não há atendimentos no dia selecionado. Escolha outra data ou revise sua disponibilidade.',
    acoes: [
      { rotulo: 'Mudar de dia', destino: 'periodo' },
      { rotulo: 'Minha disponibilidade', destino: 'disponibilidade' }
    ]
  },
  'lista-espera-cliente': {
    titulo: 'Você ainda não entrou em nenhuma lista',
    descricao: 'Escolha a filial e o serviço desejados para que o sistema avise quando surgir uma vaga.',
    acoes: [{ rotulo: 'Escolher filial e serviço', destino: 'filiais' }]
  },
  'lista-espera-equipe': {
    titulo: 'Fila vazia para este filtro',
    descricao: 'Não há solicitações neste estado. Ajuste ou limpe o filtro para ver outras solicitações.',
    acoes: []
  },
  'notificacoes': {
    titulo: 'Nenhuma notificação por enquanto',
    descricao: 'Avisos sobre agendamentos e ofertas de vaga aparecem aqui quando surgirem.',
    acoes: [{ rotulo: 'Ajustar preferências', destino: 'preferencias' }]
  },
  'admin-sem-profissional': {
    titulo: 'Nenhum profissional cadastrado',
    descricao: 'Sem profissionais, os serviços ficam sem horários para oferecer.',
    acoes: [{ rotulo: 'Adicionar profissional', destino: 'cadastro-profissional' }]
  },
  'admin-sem-servico': {
    titulo: 'Nenhum serviço cadastrado',
    descricao: 'Sem serviços, a recepção não consegue abrir horários para os clientes.',
    acoes: [{ rotulo: 'Adicionar serviço', destino: 'cadastro-servico' }]
  }
};

/** Orientação e ações de um estado vazio, conforme a tabela de ações por contexto da SPEC. */
export function orientacaoVazia(contexto: ContextoVazio): OrientacaoVazia {
  return ORIENTACOES[contexto];
}
