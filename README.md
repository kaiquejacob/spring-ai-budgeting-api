<div align="center">

<img src="https://readme-typing-svg.demolab.com?font=JetBrains+Mono&weight=600&size=17&duration=2500&pause=700&color=2F81F7&center=true&vCenter=true&width=1000&lines=%5BSYSTEM%5D+BUDGETING+API+ONLINE;%5BSYSTEM%5D+LOADING+SPRING+AI+MODULE...;%5BSYSTEM%5D+GROQ+LLM+%26+WHISPER+CONNECTED;%5BSYSTEM%5D+GEMINI+VISION+%26+TEXT+CONNECTED;%5BSYSTEM%5D+READY+FOR+TRANSACTIONS..." alt="Typing Animation">

<h1>GESTÃO FINANCEIRA IA</h1>

<p>
  <strong>KAIQUE JACOB</strong> · Spring Boot · Spring AI · Groq · Gemini · MySQL
</p>

</div>

---

<div align="center">

![Java](https://img.shields.io/badge/Java%2025-0B0F14?style=for-the-badge&logo=openjdk&logoColor=2F81F7)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot%204-0B0F14?style=for-the-badge&logo=springboot&logoColor=2F81F7)
![Spring AI](https://img.shields.io/badge/Spring%20AI-0B0F14?style=for-the-badge&logo=spring&logoColor=2F81F7)
![Groq](https://img.shields.io/badge/Groq%20API-0B0F14?style=for-the-badge&logo=fastapi&logoColor=2F81F7)
![Gemini](https://img.shields.io/badge/Gemini%20API-0B0F14?style=for-the-badge&logo=google&logoColor=2F81F7)
![MySQL](https://img.shields.io/badge/MySQL-0B0F14?style=for-the-badge&logo=mysql&logoColor=2F81F7)
![Docker](https://img.shields.io/badge/Docker-0B0F14?style=for-the-badge&logo=docker&logoColor=2F81F7)

</div>

## `SOBRE`

API REST de gestão financeira inteligente desenvolvida em **Java 25** com **Spring Boot 4** e **Spring AI**.

O sistema oferece automação financeira multimodal:
- 🎙️ **Processamento de Áudio**: transcrição via **Whisper** (`whisper-large-v3-turbo`) e interpretação da intenção com **Groq** (`openai/gpt-oss-20b`) em um contrato JSON validado pelo backend.
- 🧾 **Visão Computacional**: extração de dados de comprovantes e recibos via **Gemini**.
- 💬 **Chat Financeiro**: assistente em linguagem natural que consulta as transações do usuário por meio de **Tool Calling** (ferramentas somente de leitura).
- 🧠 **Sessões de conversa**: contexto persistido (categoria e período) para perguntas de follow-up, como "e no mês passado?", nos fluxos de áudio e recibo.
- ⚖️ **Comparação Multiprovedor**: módulo que executa a mesma mensagem no Groq e no Gemini e compara resposta e latência.

Toda transação proposta pela IA exige **confirmação explícita do usuário** antes de ser gravada. Os dados são persistidos em **MySQL 8.4**, estruturados sob princípios de **Domain-Driven Design (DDD)** e arquitetura em camadas.

---

## `FLUXO DA IA`

```text
               ┌────────────────────────────────────────────────────────┐
               │                  ENTRADA DO USUÁRIO                    │
               │         [ Áudio ]   •   [ Recibo ]   •   [ Chat ]      │
               └───────────┬───────────────┬───────────────┬────────────┘
                           │               │               │
        ┌──────────────────▼──┐   ┌────────▼────────┐   ┌──▼──────────────────┐
        │   Groq / Whisper    │   │  Gemini Vision  │   │  Groq + Tool Calling│
        │   Speech-to-Text    │   │  Image OCR/Parse│   │  (somente leitura)  │
        └──────────────────┬──┘   └────────┬────────┘   └──┬──────────────────┘
                           │               │               │
                  ┌────────▼────────┐      │      ┌────────▼───────────────┐
                  │   Groq (JSON)   │      │      │ • sum-transactions-... │
                  │ Intenção + dados│      │      │ • list-transactions-...│
                  └────────┬────────┘      │      │ • list-recent-...      │
                           │               │      └────────┬───────────────┘
                           ▼               ▼               ▼
                  ┌─────────────────────────────┐   ┌──────────────────────┐
                  │ Validação + Casos de Uso    │   │ Resposta em texto    │
                  │ (consulta ou proposta)      │   │ (consulta ao MySQL)  │
                  └─────────────┬───────────────┘   └──────────────────────┘
                                │
                                ▼
                  ┌─────────────────────────────────┐
                  │  Transação Proposta (Token)     │
                  │  -> Confirmação do Usuário      │
                  │  -> Persistência no MySQL 8.4   │
                  └─────────────────────────────────┘
```

---

## `CONCEITOS APLICADOS`

```text
BUDGETING API (SPRING AI + GROQ + GEMINI)

[✓] Integração Spring AI com especificação OpenAI via Groq
[✓] Transcrição de áudio Speech-to-Text com Whisper
[✓] Execução de ferramentas via LLM / Tool Calling (@Tool e @ToolParam)
[✓] Saída estruturada: JSON da IA validado antes de qualquer regra de negócio
[✓] System Prompts customizados carregados via Classpath (.st)
[✓] Arquitetura em Camadas e Domain-Driven Design (DDD)
[✓] Value Objects e Imutabilidade com Java Records
[✓] Persistência com Spring Data JPA e MySQL 8.4
[✓] Containerização via Docker e Docker Compose
[✓] Sessões persistidas com contexto de follow-up (categoria e período)
[✓] Extração e normalização de recibos com Gemini
[✓] Comparação e benchmark entre provedores de IA (Groq × Gemini)
[✓] Retry e tratamento de falhas transitórias dos provedores
[✓] Testes Unitários e Testes de Integração com JUnit 5
```

---

## `ENDPOINTS DA API`

Todas as rotas exigem o header `X-User-Id` (UUID). Veja as [limitações conhecidas](#limitações-conhecidas).

| Operação | Método / Endpoint | Descrição |
|---|---|---|
| 🎙️ **Processar Áudio** | `POST /transactions/ai` | Recebe áudio (`file`), transcreve, interpreta a intenção e executa a consulta ou gera uma proposta de transação com token |
| 🧾 **Processar Recibo** | `POST /transactions/ai/receipt` | Analisa a imagem do comprovante (`file`) e propõe uma transação com descrição, valor e categoria |
| 💬 **Chat Financeiro** | `POST /transactions/ai/chat` | Pergunta em linguagem natural; o modelo aciona ferramentas de consulta e responde em texto |
| 🏷️ **Sugerir Categoria** | `POST /transactions/suggest-category` | Classifica a melhor categoria a partir da descrição informada |
| ⚖️ **Comparar Provedores** | `POST /transactions/ai/compare` | Executa a mesma mensagem no Groq e no Gemini e retorna resposta e latência de cada um |
| ✅ **Confirmar Transação** | `POST /transactions/{token}/confirm` | Efetiva a transação pendente gerada pela IA |
| 📝 **Criar Transação** | `POST /transactions` | Cadastro manual de transação financeira |
| 🔍 **Buscar por Categoria** | `GET /transactions/{category}` | Lista transações de uma categoria (`GROCERIES`, `PHARMA` ou `AUTO`) |

As rotas de IA aceitam o parâmetro opcional `sessionId`, que mantém o contexto de follow-up entre chamadas. A sessão expira após 15 minutos sem uso, e o token de confirmação expira em 5 minutos.

---

## `INTENÇÕES INTERPRETADAS`

O modelo classifica cada mensagem em um contrato JSON, e o backend valida antes de agir:

| Intenção | Quando é usada |
|---|---|
| `CREATE_TRANSACTION` | O usuário informa uma despesa com valor (gera proposta e token) |
| `LIST_TRANSACTIONS` | O usuário quer ver as transações de uma categoria |
| `SUM_TRANSACTIONS_BY_CATEGORY` | O usuário quer o total gasto em uma categoria e período |
| `LIST_RECENT_TRANSACTIONS` | O usuário quer as últimas transações |
| `CONFIRM_PENDING` | O usuário confirma a proposta pendente da sessão |
| `CLARIFICATION_NEEDED` | Falta informação essencial |
| `UNSUPPORTED_REQUEST` | O pedido está fora do escopo (transferências, investimentos, exclusões etc.) |

Categorias: `GROCERIES`, `PHARMA`, `AUTO`.
Períodos: `TODAY`, `YESTERDAY`, `THIS_WEEK`, `LAST_WEEK`, `CURRENT_MONTH`, `LAST_MONTH`, `ALL_TIME`.

---

## `FERRAMENTAS DA IA (TOOL CALLING)`

As ferramentas abaixo são registradas no `ChatClient` do Spring AI do **chat financeiro** e executadas pelo modelo. Todas são **somente leitura** e já filtram os dados pelo usuário da requisição:

| Ferramenta | Descrição | Parâmetros |
|---|---|---|
| `sum-transactions-by-category` | Calcula o total gasto em uma categoria e período | `category`, `period` |
| `list-transactions-by-category` | Consulta as transações registradas em uma categoria | `category` |
| `list-recent-transactions` | Retorna o extrato das últimas 5 transações | — |

> A **criação** de transações não é feita por ferramenta: ela passa pelo fluxo de proposta e confirmação do usuário (`CREATE_TRANSACTION` → token → `POST /transactions/{token}/confirm`).

---

## `COMO EXECUTAR`

### 1. Pré-requisitos
- **Java 25** instalado
- **Docker** e **Docker Compose**
- Chave de API do **[Groq Cloud](https://console.groq.com/)**
- Chave de API do **[Google AI Studio (Gemini)](https://aistudio.google.com/)**

### 2. Configurar Variáveis de Ambiente
Copie o arquivo de exemplo e preencha suas chaves:

```bash
cp .env.example .env
```

| Variável | Descrição | Padrão / Exemplo |
|---|---|---|
| `GROQ_API_KEY` | Chave de acesso à API do Groq | `gsk_...` |
| `GROQ_CHAT_MODEL` | Modelo de chat do Groq | `openai/gpt-oss-20b` |
| `GEMINI_API_KEY` | Chave de acesso à API do Gemini | `AIza...` |
| `GEMINI_CHAT_MODEL` | Modelo do Gemini | `gemini-3.5-flash` |
| `SPRING_DATASOURCE_URL` | URL de conexão com o MySQL | `jdbc:mysql://localhost:3307/transaction` |
| `SPRING_DATASOURCE_USERNAME` | Usuário do banco | `app` |
| `SPRING_DATASOURCE_PASSWORD` | Senha do banco | `app` |

> As credenciais `app/app` servem apenas para desenvolvimento local.

### 3. Iniciar o Banco de Dados (MySQL)
Suba somente o container do MySQL (o `compose.yml` também define o serviço `app`, usado para rodar a API em container):

```bash
docker compose up -d mysql
```

### 4. Executar a Aplicação
Execute o projeto utilizando o Gradle Wrapper:

```bash
# Linux/macOS
./gradlew bootRun

# Windows PowerShell
.\gradlew.bat bootRun
```

A API estará disponível em: `http://localhost:8080`

Para rodar a API e o MySQL juntos em containers:

```bash
docker compose --profile docker up -d --build
```

O endpoint de saúde fica em `http://localhost:8080/actuator/health`.

---

<details>
<summary><strong>💡 Exemplos Práticos de Requisição (cURL)</strong></summary>

<br>

Use qualquer UUID como `X-User-Id`; ele identifica o dono dos dados.

#### 1. Processar Transação por Áudio (`POST /transactions/ai`)
```bash
curl -X POST http://localhost:8080/transactions/ai \
  -H "X-User-Id: 3f2b8c1e-5d4a-4e7b-9a10-1c2d3e4f5a6b" \
  -F "file=@/caminho/para/audio.m4a"
```

#### 2. Processar Imagem de Recibo (`POST /transactions/ai/receipt`)
```bash
curl -X POST http://localhost:8080/transactions/ai/receipt \
  -H "X-User-Id: 3f2b8c1e-5d4a-4e7b-9a10-1c2d3e4f5a6b" \
  -F "file=@/caminho/para/comprovante.jpg"
```

#### 3. Chat Financeiro (`POST /transactions/ai/chat`)
```bash
curl -X POST http://localhost:8080/transactions/ai/chat \
  -H "X-User-Id: 3f2b8c1e-5d4a-4e7b-9a10-1c2d3e4f5a6b" \
  -H "Content-Type: application/json" \
  -d '{"message": "Quanto eu gastei com mercado este mês?"}'
```

#### 4. Confirmar Transação Pendente (`POST /transactions/{token}/confirm`)
```bash
curl -X POST http://localhost:8080/transactions/d290f1ee-6c54-4b01-90e6-d701748f0851/confirm \
  -H "X-User-Id: 3f2b8c1e-5d4a-4e7b-9a10-1c2d3e4f5a6b"
```

#### 5. Comparar Groq e Gemini (`POST /transactions/ai/compare`)
```bash
curl -X POST http://localhost:8080/transactions/ai/compare \
  -H "X-User-Id: 3f2b8c1e-5d4a-4e7b-9a10-1c2d3e4f5a6b" \
  -H "Content-Type: application/json" \
  -d '{"message": "Quanto gastei com mercado este mês?"}'
```

</details>

---

## `TESTES AUTOMATIZADOS`

O projeto inclui testes de unidade e testes de integração com avaliação de prompts e acionamento de ferramentas. Os testes de contexto usam o MySQL local (`docker compose up -d mysql`), e os que chamam a IA só rodam quando `GROQ_API_KEY` está definida no ambiente.

```bash
# Testes unitários e de contexto (excluem a avaliação de prompts)
./gradlew test

# Avaliação de prompts contra o Groq (requer GROQ_API_KEY ativa)
./gradlew groqEvaluationTest
```

---

## `LIMITAÇÕES CONHECIDAS`

```text
[!] Sem autenticação: o usuário vem do header X-User-Id informado pelo cliente.
    Serve para isolar dados por usuário na demonstração, mas não impede que
    alguém use o ID de outra pessoa.
[!] Tokens de confirmação ficam em memória e se perdem ao reiniciar a API.
[!] O endpoint de chat não mantém histórico de mensagens entre chamadas;
    o contexto de follow-up existe nos fluxos de áudio e recibo.
[!] POST /transactions recebe o valor em reais inteiros (campo "amount").
[!] O Spring AI usa uma versão milestone (2.0.0-M4).
```

---

## `ESTRUTURA DO PROJETO`

A estrutura foi desenhada sob princípios de responsabilidade única e arquitetura limpa:

```text
src/main/java/dio/budgeting/
├── domain/                  # Núcleo de domínio: entidades, value objects e contratos
│   ├── Category.java
│   ├── Transaction.java
│   ├── TransactionId.java
│   ├── TransactionPeriod.java
│   └── TransactionRepository.java
│
├── application/             # Casos de uso e orquestração de negócios
│   ├── ai/                  # Interpretação de IA, recibos, Gemini e comparação de provedores
│   ├── input/ & output/     # DTOs de entrada e saída da camada de aplicação
│   ├── FinancialTools.java  # Definição das ferramentas de Tool Calling (somente leitura)
│   └── ConversationSession* # Sessões de conversa e contexto de follow-up
│
└── infrastructure/          # Detalhes técnicos e adaptadores externos
    ├── ai/                  # Configurações do Spring AI, Groq e Gemini
    ├── http/                # Controllers REST, DTOs de request/response e Exception Handler
    └── persistence/         # Mapeamento relacional (JPA/Hibernate) e repositórios MySQL
```

<details>
<summary><strong>Visualizar árvore completa de arquivos</strong></summary>

```text
budgeting/
├── src/
│   ├── main/
│   │   ├── java/dio/budgeting/
│   │   │   ├── application/
│   │   │   │   ├── ai/
│   │   │   │   │   ├── AiProcessingException.java
│   │   │   │   │   ├── AiProviderComparisonService.java
│   │   │   │   │   ├── CategorySuggestion.java
│   │   │   │   │   ├── GeminiTextService.java
│   │   │   │   │   ├── ProcessReceiptImageUseCase.java
│   │   │   │   │   ├── ReceiptImageNormalizer.java
│   │   │   │   │   ├── TransactionAiResponse.java
│   │   │   │   │   └── TransactionIntent.java
│   │   │   │   ├── input/
│   │   │   │   │   └── PersistTransactionInput.java
│   │   │   │   ├── output/
│   │   │   │   │   └── TransactionOutput.java
│   │   │   │   ├── ConversationSession.java
│   │   │   │   ├── ConversationSessionService.java
│   │   │   │   ├── FinancialTools.java
│   │   │   │   ├── ListRecentTransactionsUseCase.java
│   │   │   │   ├── ListTransactionsByCategoryUseCase.java
│   │   │   │   ├── PendingTransactionException.java
│   │   │   │   ├── PendingTransactionService.java
│   │   │   │   ├── PersistTransactionUseCase.java
│   │   │   │   ├── ProcessTransactionAudioUseCase.java
│   │   │   │   ├── SuggestCategoryUseCase.java
│   │   │   │   └── SumTransactionsByCategoryUseCase.java
│   │   │   ├── domain/
│   │   │   │   ├── Category.java
│   │   │   │   ├── InvalidTransactionException.java
│   │   │   │   ├── Transaction.java
│   │   │   │   ├── TransactionId.java
│   │   │   │   ├── TransactionPeriod.java
│   │   │   │   ├── TransactionRepository.java
│   │   │   │   └── TransactionSource.java
│   │   │   ├── infrastructure/
│   │   │   │   ├── ai/
│   │   │   │   │   ├── AiConfiguration.java
│   │   │   │   │   ├── AiProperties.java
│   │   │   │   │   ├── FinancialChatClientConfiguration.java
│   │   │   │   │   ├── GeminiProperties.java
│   │   │   │   │   └── GroqChatOptionsConfiguration.java
│   │   │   │   ├── http/
│   │   │   │   │   ├── request/
│   │   │   │   │   │   ├── CategorySuggestionRequest.java
│   │   │   │   │   │   ├── FinancialChatRequest.java
│   │   │   │   │   │   └── TransactionRequest.java
│   │   │   │   │   ├── response/
│   │   │   │   │   │   ├── CategorySuggestionResponse.java
│   │   │   │   │   │   ├── ErrorResponse.java
│   │   │   │   │   │   ├── FinancialChatResponse.java
│   │   │   │   │   │   ├── TransactionAiHttpResponse.java
│   │   │   │   │   │   └── TransactionResponse.java
│   │   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   │   └── TransactionController.java
│   │   │   │   └── persistence/
│   │   │   │       ├── entity/
│   │   │   │       │   ├── ConversationSessionEntity.java
│   │   │   │       │   └── TransactionEntity.java
│   │   │   │       └── repository/
│   │   │   │           ├── ConversationSessionJpaRepository.java
│   │   │   │           ├── JpaTransactionRepository.java
│   │   │   │           └── TransactionEntityRepository.java
│   │   │   └── BudgetingApplication.java
│   │   └── resources/
│   │       ├── prompts/
│   │       │   ├── chat-system-message.st
│   │       │   ├── receipt-system-message.st
│   │       │   └── system-message.st
│   │       └── application.properties
│   └── test/
│       ├── java/dio/budgeting/
│       │   ├── application/ai/
│       │   │   ├── ProcessTransactionAudioUseCaseTest.java
│       │   │   └── ReceiptImageNormalizerTest.java
│       │   ├── domain/
│       │   │   └── TransactionPeriodTest.java
│       │   ├── BudgetingApplicationTests.java
│       │   ├── OpenAiChatClientIT.java
│       │   ├── OpenAiChatModelIT.java
│       │   ├── OpenAiTranscriptionModelIT.java
│       │   ├── ToolCallingIT.java
│       │   ├── TransactionAiValidationTest.java
│       │   └── TransactionPromptEvaluationIT.java
│       └── resources/
│           └── application-test.properties
├── docs/
│   ├── examples/
│   │   ├── chat-request.json
│   │   └── suggestion-request.json
│   └── ai-provider-comparison.md
├── compose.yml
├── Dockerfile
├── build.gradle
├── settings.gradle
├── .env.example
├── .gitignore
├── gradlew
├── gradlew.bat
└── README.md
```

</details>

---

## `ESTUDO COMPARATIVO (GROQ × GEMINI)`

O projeto conta com um estudo comparativo documentado em [`docs/ai-provider-comparison.md`](docs/ai-provider-comparison.md), que apresenta:
- Latência por chamada na mesma mensagem
- Aderência ao contrato JSON de intenções
- Falhas encontradas (intents inventados, Markdown na resposta, indisponibilidade `503` do Gemini)
- Papel de cada provedor no projeto

---

## `TECNOLOGIAS`

<div align="center">

<pre align="center">
[SYSTEM STATUS]

Language  : JAVA 25
Framework : SPRING BOOT 4 & SPRING AI
AI Chat   : GROQ + GPT-OSS-20B
AI Vision : GEMINI FLASH
Audio     : WHISPER LARGE-V3-TURBO
Database  : MYSQL 8.4
Runtime   : DOCKER
Testing   : JUNIT 5
Build     : GRADLE

STATUS    : READY_TO_BUILD

&gt; keep_learning();
&gt; keep_building();
</pre>

</div>

---

<div align="center">

### `SYSTEM // END`

**Java • Spring Boot • Spring AI • Groq • Gemini • MySQL • Docker**

</div>
