/** Formata datas locais da API sem convertê-las de fuso horário. */
export function formatarData(valor: string): string {
  const partes = /^(\d{4})-(\d{2})-(\d{2})$/.exec(valor);
  return partes ? `${partes[3]}/${partes[2]}/${partes[1]}` : valor;
}

/** O início de um agendamento já está no horário local da filial. */
export function formatarDataHora(valor: string): string {
  const partes = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(valor);
  return partes ? `${partes[3]}/${partes[2]}/${partes[1]} às ${partes[4]}:${partes[5]}` : valor;
}
