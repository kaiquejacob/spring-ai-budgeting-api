<div align="center">

<img src="https://readme-typing-svg.demolab.com?font=JetBrains+Mono&weight=600&size=17&duration=2500&pause=700&color=2F81F7&center=true&vCenter=true&width=1000&lines=%5BSYSTEM%5D+BUDGETING+API+ONLINE;%5BSYSTEM%5D+LOADING+SPRING+AI+MODULE...;%5BSYSTEM%5D+GROQ+LLM+%26+WHISPER+CONNECTED;%5BSYSTEM%5D+GEMINI+VISION+%26+TEXT+CONNECTED;%5BSYSTEM%5D+READY+FOR+TRANSACTIONS..." alt="Typing Animation">

<h1>GESTÃO FINANCEIRA IA</h1>

<p>
  <strong>KAIQUE JACOB</strong> · Spring Boot · Spring AI · Groq · Gemini · MySQL
</p>

</div>

---

<div align="center">

![Java](https://img.shields.io/badge/Java%2021%2B-0B0F14?style=for-the-badge&logo=openjdk&logoColor=2F81F7)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-0B0F14?style=for-the-badge&logo=springboot&logoColor=2F81F7)
![Spring AI](https://img.shields.io/badge/Spring%20AI-0B0F14?style=for-the-badge&logo=spring&logoColor=2F81F7)
![Groq](https://img.shields.io/badge/Groq%20API-0B0F14?style=for-the-badge&logo=fastapi&logoColor=2F81F7)
![Gemini](https://img.shields.io/badge/Gemini%20API-0B0F14?style=for-the-badge&logo=google&logoColor=2F81F7)
![MySQL](https://img.shields.io/badge/MySQL-0B0F14?style=for-the-badge&logo=mysql&logoColor=2F81F7)
![Docker](https://img.shields.io/badge/Docker-0B0F14?style=for-the-badge&logo=docker&logoColor=2F81F7)

</div>

## `SOBRE`

API REST de gestão financeira inteligente desenvolvida em **Java** com **Spring Boot** e **Spring AI**.

O sistema oferece automação financeira multimodal:
- 🎙️ **Processamento de Áudio**: Transcrição via **Whisper** (`whisper-large-v3-turbo`) e interpretação com **Groq** (`openai/gpt-oss-20b`) com execução de ferramentas (*Tool Calling*).
- 🧾 **Visão Computacional**: Extração de dados de comprovantes e recibos via **Gemini Vision**.
- 💬 **Chat Conversacional**: Assistente financeiro com memória de sessão e acesso a ferramentas de agregação e listagem.
- ⚖️ **Comparação Multiprovedor**: Módulo de benchmark avaliando respostas e latência entre Groq e Gemini.

Os dados são persistidos em **MySQL 8.4**, estruturados sob princípios de **Domain-Driven Design (DDD)** e arquitetura em camadas limpa.

---

## `FLUXO DA IA`

```text
               ┌────────────────────────────────────────────────────────┐
               │                  ENTRADA DO USUÁRIO                    │
               │         [ Áudio ]   •   [ Recibo ]   •   [ Chat ]      │
               └───────────┬───────────────┬───────────────┬────────────┘
                           │               │               │
        ┌──────────────────▼──┐   ┌────────▼────────┐   ┌──▼──────────────────┐
        │   Groq / Whisper    │   │  Gemini Vision  │   │     Groq LLM        │
        │ Speech-to-Text      │   │ Image OCR/Parse │   │ Conversational Chat │
        └──────────────────┬──┘   └────────┬────────┘   └──┬──────────────────┘
                           │               │               │
                           └───────┬───────┴───────────────┘
                                   │
                                   ▼
                  ┌─────────────────────────────────┐
                  │    Spring AI Tool Calling       │
                  │  • persist-transaction          │
                  │  • list-transactions-by-category│
                  │  • sum-transactions-by-category │
                  │  • list-recent-transactions     │
                  └────────────────┬────────────────┘
                                   │
                                   ▼
                  ┌─────────────────────────────────┐
                  │    Transação Proposta (Token)   │
                  │   -> Confirmação do Usuário     │
                  │   -> Persistência no MySQL 8.4  │
                  └─────────────────────────────────┘
```

---

## `CONCEITOS APLICADOS`

```text
BUDGETING API (SPRING AI + GROQ + GEMINI)

[✓] Integração Spring AI com especificação OpenAI via Groq
[✓] Transcrição de áudio Speech-to-Text com Whisper
[✓] Execução de ferramentas via LLM / Tool Calling (@Tool e @ToolParam)
[✓] System Prompts customizados carregados via Classpath (.st)
[✓] Arquitetura em Camadas e Domain-Driven Design (DDD)
[✓] Value Objects e Imutabilidade com Java Records
[✓] Persistência com Spring Data JPA e MySQL 8.4
[✓] Containerização completa via Docker e Docker Compose
[✓] Sessões conversacionais com contexto e histórico de follow-up
[✓] Extração e normalização de recibos com Gemini Vision
[✓] Comparação e benchmark entre provedores de IA (Groq × Gemini)
[✓] Testes Unitários e Testes de Integração com JUnit 5
```

---

## `ENDPOINTS DA API`

| Operação | Método / Endpoint | Descrição |
|---|---|---|
| 🎙️ **Processar Áudio** | `POST /transactions/ai` | Recebe áudio, transcreve e gera proposta de transação com token |
| 🧾 **Processar Recibo** | `POST /transactions/ai/receipt` | Analisa imagem do comprovante e extrai valor, data e categoria |
| 💬 **Chat Financeiro** | `POST /transactions/ai/chat` | Conversa interativa com histórico e acionamento de ferramentas |
| 🏷️ **Sugerir Categoria** | `POST /transactions/suggest-category` | Classifica a melhor categoria baseada na descrição informada |
| ⚖️ **Comparar Provedores** | `POST /transactions/ai/compare` | Executa a mesma entrada no Groq e Gemini para comparação |
| ✅ **Confirmar Transação** | `POST /transactions/{token}/confirm` | Efetiva a transação pendente gerada pela IA |
| 📝 **Criar Transação** | `POST /transactions` | Cadastro manual de transação financeira |
| 🔍 **Buscar por Categoria** | `GET /transactions/{category}` | Lista transações cadastradas em uma categoria específica |

---

## `FERRAMENTAS DA IA (TOOL CALLING)`

As ferramentas abaixo são registradas no `ChatClient` do Spring AI e são executadas autonomamente pelo modelo:

| Ferramenta | Descrição | Parâmetros |
|---|---|---|
| `persist-transaction` | Registra e persiste uma transação no banco de dados | `description`, `amount`, `category` |
| `list-transactions-by-category` | Consulta transações registradas em uma categoria | `category` |
| `sum-transactions-by-category` | Calcula o total financeiro gasto por categoria e período | `category`, `period` |
| `list-recent-transactions` | Retorna o extrato das transações mais recentes | — |

---

## `COMO EXECUTAR`

### 1. Pré-requisitos
- **Java 21+** instalado
- **Docker** e **Docker Compose**
- Chave de API do **[Groq Cloud](https://console.groq.com/)**
- Chave de API do **[Google AI Studio (Gemini)](https://aistudio.google.com/)**

### 2. Configurar Variáveis de Ambiente
Copie o arquivo de exemplo e preencha suas chaves:

```bash
cp .env.example .env
```

| Variável | Descrição | Exemplo |
|---|---|---|
| `GROQ_API_KEY` | Chave de acesso à API do Groq | `gsk_...` |
| `GEMINI_API_KEY` | Chave de acesso à API do Gemini | `AIza...` |
| `SPRING_DATASOURCE_URL` | URL de conexão com MySQL | `jdbc:mysql://localhost:3306/budgeting` |
| `SPRING_DATASOURCE_USERNAME` | Usuário do banco | `budgeting_user` |
| `SPRING_DATASOURCE_PASSWORD` | Senha do banco | `budgeting_pass` |

### 3. Iniciar o Banco de Dados (MySQL)
Inicie o container MySQL pré-configurado via Docker Compose:

```bash
docker compose up -d
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

---

<details>
<summary><strong>💡 Exemplos Práticos de Requisição (cURL)</strong></summary>

<br>

#### 1. Processar Transação por Áudio (`POST /transactions/ai`)
```bash
curl -X POST http://localhost:8080/transactions/ai \
  -F "file=@/caminho/para/audio.m4a"
```

#### 2. Processar Imagem de Recibo (`POST /transactions/ai/receipt`)
```bash
curl -X POST http://localhost:8080/transactions/ai/receipt \
  -F "file=@/caminho/para/comprovante.jpg"
```

#### 3. Chat Financeiro com Histórico (`POST /transactions/ai/chat`)
```bash
curl -X POST http://localhost:8080/transactions/ai/chat \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "session-123",
    "message": "Quanto eu gastei com alimentação este mês?"
  }'
```

#### 4. Confirmar Transação Pendente (`POST /transactions/{token}/confirm`)
```bash
curl -X POST http://localhost:8080/transactions/d290f1ee-6c54-4b01-90e6-d701748f0851/confirm
```

</details>

---

## `TESTES AUTOMATIZADOS`

O projeto inclui testes de unidade e testes de integração com avaliação de prompts e acionamento de ferramentas:

```bash
# Executar todos os testes de unidade
./gradlew test

# Executar testes de integração (requer credenciais ativas no .env)
./gradlew test --tests "*IT"
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
│   └── TransactionRepository.java
│
├── application/             # Casos de uso e orquestração de negócios
│   ├── ai/                  # Processamento de áudio, recibos e integração multimodal
│   ├── input/ & output/     # DTOs de entrada e saída da camada de aplicação
│   ├── FinancialTools.java  # Definição e registro das ferramentas de Tool Calling
│   └── ConversationSession* # Gerenciamento de histórico e contexto de conversas
│
└── infrastructure/          # Detalhes técnicos e adaptadores externos
    ├── ai/                  # Configurações do Spring AI, Groq e Gemini
    ├── http/                # Controllers REST, validações de payload e Exception Handler
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
│       └── java/dio/budgeting/
│           ├── application/ai/
│           │   ├── ProcessTransactionAudioUseCaseTest.java
│           │   └── ReceiptImageNormalizerTest.java
│           ├── domain/
│           │   └── TransactionPeriodTest.java
│           ├── BudgetingApplicationTests.java
│           ├── OpenAiChatClientIT.java
│           ├── OpenAiChatModelIT.java
│           ├── OpenAiTranscriptionModelIT.java
│           ├── ToolCallingIT.java
│           ├── TransactionAiValidationTest.java
│           └── TransactionPromptEvaluationIT.java
├── docs/
│   └── ai-provider-comparison.md
├── compose.yml
├── Dockerfile
├── build.gradle
├── .env.example
├── .gitignore
├── gradlew
├── gradlew.bat
└── README.md
```

</details>

---

## `ESTUDO COMPARATIVO (GROQ × GEMINI)`

O projeto conta com um estudo comparativo aprofundado documentado em [`docs/ai-provider-comparison.md`](docs/ai-provider-comparison.md), avaliando:
- Latência média por chamada
- Eficiência e aderência na extração de intenções JSON
- Custo por milhão de tokens
- Casos de uso recomendados para cada provedor

---

## `TECNOLOGIAS`

<pre align="center">
[SYSTEM STATUS]

Language  : JAVA 21+
Framework : SPRING BOOT 3 & SPRING AI
AI Chat   : GROQ + GPT-OSS-20B
AI Vision : GEMINI 3.5 FLASH
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
