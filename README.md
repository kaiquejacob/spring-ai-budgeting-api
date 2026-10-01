<div align="center">

<img src="https://readme-typing-svg.demolab.com?font=JetBrains+Mono&weight=600&size=17&duration=2500&pause=700&color=2F81F7&center=true&vCenter=true&width=1000&lines=%5BSYSTEM%5D+BUDGETING+API+ONLINE;%5BSYSTEM%5D+LOADING+SPRING+AI+MODULE...;%5BSYSTEM%5D+GROQ+LLM+%26+WHISPER+CONNECTED;%5BSYSTEM%5D+READY+FOR+TRANSACTIONS..." alt="Typing Animation">

<h1>GESTÃO FINANCEIRA IA</h1>

<p>
  <strong>KAIQUE JACOB</strong> · Spring Boot · Spring AI · Groq
</p>

</div>

---

<div align="center">

![Java](https://img.shields.io/badge/Java%2021%2B-0B0F14?style=for-the-badge&logo=openjdk&logoColor=2F81F7)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-0B0F14?style=for-the-badge&logo=springboot&logoColor=2F81F7)
![Spring AI](https://img.shields.io/badge/Spring%20AI-0B0F14?style=for-the-badge&logo=spring&logoColor=2F81F7)
![Groq](https://img.shields.io/badge/Groq%20API-0B0F14?style=for-the-badge&logo=fastapi&logoColor=2F81F7)
![MySQL](https://img.shields.io/badge/MySQL-0B0F14?style=for-the-badge&logo=mysql&logoColor=2F81F7)
![Docker](https://img.shields.io/badge/Docker-0B0F14?style=for-the-badge&logo=docker&logoColor=2F81F7)

</div>

## `SOBRE`

API REST de gestão financeira inteligente desenvolvida em **Java** com **Spring Boot** e **Spring AI**.

O sistema permite o registro manual de transações financeiras e o processamento automatizado via áudio, utilizando **Whisper** (`whisper-large-v3-turbo`) para transcrição e **Groq** com o modelo **`openai/gpt-oss-20b`** para interpretar a intenção do usuário e executar ferramentas (*Tool Calling*).

Os dados são persistidos em **MySQL**, com uma arquitetura organizada em camadas, execução via **Docker** e testes automatizados com **JUnit 5**.

---

## `CONCEITOS APLICADOS`

```text
BUDGETING API (SPRING AI + GROQ)

[✓] Integração Spring AI com especificação OpenAI via Groq
[✓] Transcrição de áudio Speech-to-Text com Whisper
[✓] Execução de ferramentas via LLM / Tool Calling
[✓] @Tool e @ToolParam
[✓] System Prompt customizado via classpath
[✓] Arquitetura em Camadas e DDD
[✓] Domain, Application e Infrastructure
[✓] Value Objects e Imutabilidade
[✓] TransactionId com UUID e Java Records
[✓] Spring Data JPA + MySQL
[✓] Docker Compose
[✓] Controle de retentativas
[✓] Testes de Integração com JUnit 5
[✓] Variáveis de ambiente para credenciais
[✓] API REST para gerenciamento de transações

[→] Próximos módulos e expansão de categorias
```

---

## `FUNCIONALIDADES`

| Operação | Método / Endpoint | Descrição |
|---|---|---|
| 🎙️ **Processar via IA** | `POST /transactions/ai` | Recebe áudio, transcreve com Whisper, interpreta com LLM e executa a Tool de persistência |
| 📝 **Criar transação** | `POST /transactions` | Registra manualmente uma transação financeira através de JSON |
| 🔍 **Consultar por categoria** | `GET /transactions/{category}` | Lista transações cadastradas de uma categoria |

---

## `FERRAMENTAS DA IA`

As ferramentas são registradas no `ChatClient` do Spring AI e podem ser acionadas pelo modelo conforme o contexto da solicitação.

| Ferramenta | Descrição | Parâmetros |
|---|---|---|
| `persist-transaction` | Persiste uma nova transação no banco | `description`, `amount`, `category` |
| `list-transactions-by-category` | Lista transações de uma categoria | `category` |

---


---

## `ESTRUTURA`

```text
budgeting/
├── src/
│   ├── main/
│   │   ├── java/dio/budgeting/
│   │   │   ├── application/
│   │   │   │   ├── input/
│   │   │   │   │   └── PersistTransactionInput.java
│   │   │   │   ├── output/
│   │   │   │   │   └── TransactionOutput.java
│   │   │   │   ├── ListTransactionsByCategoryUseCase.java
│   │   │   │   └── PersistTransactionUseCase.java
│   │   │   ├── domain/
│   │   │   │   ├── Category.java
│   │   │   │   ├── Transaction.java
│   │   │   │   ├── TransactionId.java
│   │   │   │   └── TransactionRepository.java
│   │   │   ├── infrastructure/
│   │   │   │   ├── http/
│   │   │   │   │   ├── request/
│   │   │   │   │   │   └── TransactionRequest.java
│   │   │   │   │   ├── response/
│   │   │   │   │   │   └── TransactionResponse.java
│   │   │   │   │   └── TransactionController.java
│   │   │   │   └── persistence/
│   │   │   └── BudgetingApplication.java
│   │   └── resources/
│   │       ├── prompts/
│   │       │   └── system-message.st
│   │       └── application.properties
│   └── test/
│       └── java/dio/budgeting/
│           ├── BudgetingApplicationTests.java
│           ├── OpenAiChatClientIT.java
│           ├── OpenAiChatModelIT.java
│           ├── OpenAiTranscriptionModelIT.java
│           └── ToolCallingIT.java
├── compose.yml
├── build.gradle
├── gradlew
├── gradlew.bat
├── .gitignore
└── README.md
```

---

## `TECNOLOGIAS`

<pre align="center">
[SYSTEM STATUS]

Language  : JAVA 21+
Framework : SPRING BOOT 3 & SPRING AI
AI        : GROQ + GPT-OSS-20B
Audio     : WHISPER LARGE-V3-TURBO
Database  : MYSQL 9
Runtime   : DOCKER
Testing   : JUNIT 5
Build     : GRADLE

STATUS    : READY_TO_BUILD

&gt; keep_learning();
&gt; keep_building();
</pre>

</div>
---
---

<div align="center">

### `SYSTEM // END`

**Java • Spring Boot • Spring AI • Groq • MySQL • Docker**

</div>
