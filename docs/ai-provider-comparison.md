# Comparação entre Groq e Gemini

Este projeto utiliza dois provedores de IA com papéis diferentes. A comparação foi implementada no endpoint `POST /transactions/ai/compare` para avaliar como cada modelo interpreta a mesma solicitação e retorna uma resposta estruturada.

## Papel do Groq

O Groq é o provedor principal do assistente conversacional.

Ele é responsável por interpretar mensagens enviadas pelo usuário e classificar a intenção em um contrato JSON padronizado, por exemplo:

```json
{
  "intent": "SUM_TRANSACTIONS_BY_CATEGORY",
  "category": "GROCERIES",
  "period": "CURRENT_MONTH",
  "messageToUser": ""
}
```

Com base nessa interpretação, o backend decide qual ação executar:

- listar transações;
- somar gastos por categoria e período;
- propor a criação de uma transação;
- solicitar esclarecimento;
- confirmar uma transação pendente.

O Groq também é usado no fluxo de transcrição de áudio, permitindo que o usuário envie uma gravação e o sistema converta a fala em uma solicitação financeira estruturada.

## Papel do Gemini

O Gemini atua em duas frentes complementares.

### Análise de recibos

O Gemini é utilizado para analisar imagens de recibos. A aplicação envia a imagem normalizada e solicita a extração de uma proposta de transação, contendo descrição, valor e categoria sugerida.

A transação não é criada automaticamente. O sistema retorna uma proposta que precisa ser confirmada pelo usuário antes da persistência no banco de dados.

### Interpretação de texto

O Gemini também participa da comparação de interpretação de mensagens em linguagem natural. Ele recebe o mesmo prompt e a mesma mensagem do usuário que o Groq, e deve retornar um JSON compatível com o contrato da aplicação.

## Endpoint de comparação

O endpoint abaixo envia a mesma mensagem para os dois provedores:

```bash
POST /transactions/ai/compare
```

Exemplo de requisição:

```json
{
  "userMessage": "Quanto gastei com mercado este mês?"
}
```

## Resultado obtido

| Provedor | Intent | Categoria | Período | Latência |
|---|---|---|---|---|
| Groq | `SUM_TRANSACTIONS_BY_CATEGORY` | `GROCERIES` | `CURRENT_MONTH` | 3.729 ms |
| Gemini | `SUM_TRANSACTIONS_BY_CATEGORY` | `GROCERIES` | `CURRENT_MONTH` | 4.753 ms |

Ambos os provedores identificaram corretamente que a solicitação era uma consulta de gastos por categoria no mês atual.

## Aprendizados do teste

A comparação entre provedores ajudou a identificar pontos importantes:

- Modelos podem inventar valores fora do contrato, como intents inexistentes.
- O prompt precisa definir explicitamente os valores permitidos para `intent`, `category` e `period`.
- Respostas podem vir acompanhadas de Markdown, exigindo tratamento antes do parsing.
- Provedores podem ficar temporariamente indisponíveis, como ocorreu com respostas `503` do Gemini.
- Retry com espera curta é útil para falhas transitórias.
- O backend deve validar a resposta da IA antes de utilizá-la em regras de negócio.

## Conclusão

O Groq é o provedor principal para o assistente conversacional e a transcrição de áudio. O Gemini complementa o projeto com análise visual de recibos e serve como provedor alternativo para interpretação de texto.

A comparação entre os dois provedores tornou o sistema mais robusto, porque expôs diferenças de comportamento e reforçou a necessidade de contratos claros, validação defensiva e tratamento de falhas.