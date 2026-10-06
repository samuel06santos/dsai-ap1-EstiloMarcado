# Descoberta pública de filiais e serviços (2026-10-05)

## O que é e por que existe

Hoje o cliente precisa receber um link com o ID de uma filial para conhecer
serviços e horários. A página inicial não oferece um caminho natural para
descobrir onde agendar. Esta SPEC cria uma listagem pública enxuta de filiais
ativas, com busca por nome e filtros úteis, para ligar a primeira visita à
página de agendamento sem exigir conhecimento de identificadores.

A funcionalidade amplia deliberadamente o limite “sem diretório global” de
`2026-09-30-estabelecimentos-filiais-profissionais-e-permissoes.md` e
`2026-10-01-navegacao-visual-e-meu-perfil.md`. Não altera o escopo interno de
gestão entre filiais nem concede novas permissões.

## Escopo

- Rota pública `/filiais`, acessível pela home, pelo rodapé e pela navegação
  do cliente autenticado.
- Busca por nome de estabelecimento ou filial e filtro por serviço oferecido.
- Cartões com nome, localização textual, serviços em destaque, faixa de preço
  quando houver dados reais e ação “Ver serviços e horários”.
- Paginação, estados vazios e tratamento de filial ou serviço desativado.
- Ligação com a página pública existente `/unidades/:id` sem alterar reservas.

## Consulta pública e privacidade

`GET /api/filiais/publicas?busca=&servico=&pagina=&tamanho=` devolve somente
filiais ativas de estabelecimentos aptos à exposição pública. Busca ignora
acentos e diferença entre maiúsculas/minúsculas, aceita 2 a 80 caracteres e
limita o tamanho da página a 24 resultados. Ordenação padrão é por nome de
estabelecimento, filial e ID para desempate. A resposta inclui ID, nome da
filial e do estabelecimento, cidade/bairro quando cadastrados, endereço público
aprovado, serviços ativos resumidos e preço mínimo real por serviço. Não inclui
e-mail, telefone pessoal, dados de cliente, agenda interna ou profissionais
inativos. Filial que desativar deixa de aparecer na próxima consulta.

Filtro de serviço usa nome legível e código de serviço público; admite vários
parâmetros `servico`, combinados por "qualquer um" (OU), e o backend confere
vínculo e estado na filial. Busca e paginação são realizadas no
servidor. A API aplica limite de requisições e não permite enumeração de dados
internos via parâmetros. Horários disponíveis não são pré-calculados para toda
a listagem; são consultados na página da filial pelo motor existente.

## Experiência de navegação

O campo “Buscar filial ou estabelecimento” mostra ajuda curta, botão de limpar
e resultado após envio ou pausa breve na digitação. O filtro de serviço usa
tags selecionáveis abaixo do campo de busca, com "Todos" como padrão exclusivo
e seleção múltipla das demais opções; nunca IDs digitáveis. O título da página
é compacto para manter a busca visível. Estado vazio distingue “nenhuma filial
encontrada” de erro de conexão e oferece limpar filtros. O cartão inteiro não
precisa ser clicável: há link claro e acessível para a página da filial.

Ao voltar da filial, busca, filtro e página continuam na URL para preservar o
contexto e permitir compartilhar o resultado. O usuário pode abrir uma filial
em nova aba sem perder a pesquisa. A última filial visitada pode continuar
sendo atalho local, mas a descoberta pública não depende de `localStorage`.
Se uma filial sair do ar entre resultado e clique, sua página retorna `404`
com mensagem compreensível e link para `/filiais` com filtros preservados.

## Linguagem visual e acessibilidade

Os cartões seguem a hierarquia de `2026-10-01-navegacao-visual-e-meu-perfil.md`:
nome, contexto, serviços, preço e ação. Não há campos com aparência editável
para texto apenas informativo. Resultados têm cabeçalho com total aproximado e
paginação com rótulos. Estado de carregamento não desloca a página de forma
brusca. Foco, contraste e alvos de toque seguem os critérios já definidos; a
lista não tem rolagem horizontal em 320 px.

## Critérios de aceitação e testes

- Visitante chega à lista pela home, encontra filial por nome e serviço e abre
  sua página pública sem informar ID.
- Apenas filiais e serviços ativos aparecem; a desativação remove a filial da
  listagem e bloqueia acesso público pelo link antigo.
- Paginação tem ordem estável e não mistura resultados entre filtros; entrada
  inválida recebe `400` com campo identificado.
- Busca vazia mostra filiais públicas; busca sem correspondência oferece ação
  de limpar filtros; falha da API mostra opção de tentar novamente.
- URL preserva busca, filtro e página ao voltar. Interface funciona por
  teclado, leitor de tela e em 320 px.
- A resposta não contém dados privados de contas, clientes ou equipe.

## Fora do escopo

- Ordenação por distância sem endereço geocodificado ou permissão de localização.
- Destaque pago, avaliações reais e recomendação personalizada.
- Compartilhar administração ou agenda de filiais entre perfis internos.
- Garantir vaga a partir da listagem; a confirmação revalida disponibilidade.

## Relação com outras SPECs e rastreabilidade

- Amplia a consulta pública de `2026-09-30-estabelecimentos-filiais-profissionais-e-permissoes.md`.
- Usa catálogo de `2026-09-30-catalogo-de-servicos.md` e disponibilidade de
  `2026-10-01-motor-de-disponibilidade.md` somente após abrir uma filial.
- Alimenta a ação principal de `2026-10-05-pagina-inicial-publica.md`.
