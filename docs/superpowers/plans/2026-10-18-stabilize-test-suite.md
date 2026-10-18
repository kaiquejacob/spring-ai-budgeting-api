# Stabilize Test Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `./gradlew clean test` execute and pass deterministic tests without depending on Groq rate limits or probabilistic model responses.

**Architecture:** Mark every test that calls Groq as an external evaluation test and exclude that tag from the default `test` task. Keep a separate `groqEvaluationTest` task for explicit live-model checks. Add local tests around application behavior so the default suite still validates the transaction interpretation contract without network calls.

**Tech Stack:** Java 25, Gradle 9.4.1, Spring Boot 4.0.5, JUnit Jupiter 6.0.3, Spring AI OpenAI/Groq integration.

**Spec:** User request: all tests should pass before continuing development.

## Global Constraints

- `./gradlew clean test` must not call the Groq API.
- Live Groq tests must remain available through `groqEvaluationTest`.
- Do not weaken production validation or hide test failures with broad exception handling.
- Preserve existing test assertions and repository behavior unless a test requires deterministic dependency injection.

## Review Focus

- A default test run with no API key must still discover and pass local tests.
- Every test that creates a live Spring AI/OpenAI request must be excluded from the default task.
- The explicit Groq task must discover the tagged tests rather than report zero tests.
- Local interpretation tests must verify creation, category listing, category sums, recent transactions, clarification, and unsupported requests without network access.
- A live Groq failure must remain visible when `groqEvaluationTest` is run explicitly.

---

### Task 1: Classify external integration tests

**Files:**
- Modify: `src/test/java/dio/budgeting/OpenAiChatClientIT.java`
- Modify: `src/test/java/dio/budgeting/OpenAiChatModelIT.java`
- Modify: `src/test/java/dio/budgeting/OpenAiTranscriptionModelIT.java`
- Modify: `src/test/java/dio/budgeting/ToolCallingIT.java`
- Modify: `src/test/java/dio/budgeting/TransactionPromptEvaluationIT.java`
- Modify: `build.gradle:39-47`

**Interfaces:**
- Produces: the `groq-evaluation` JUnit tag and a default test task that excludes it.

- [ ] **Step 1: Add `@Tag("groq-evaluation")` to every test class that performs live Groq/OpenAI calls.**
- [ ] **Step 2: Configure `tasks.named('test')` with `useJUnitPlatform { excludeTags 'groq-evaluation' }`.**
- [ ] **Step 3: Configure `groqEvaluationTest` with `includeTags 'groq-evaluation'` and make it depend on `testClasses`.**
- [ ] **Step 4: Run `./gradlew clean test` and verify local tests are discovered and no external API call appears in the output.**
- [ ] **Step 5: Run `./gradlew groqEvaluationTest --dry-run` and verify the dedicated task is selectable without changing the default task.**

### Task 2: Add deterministic interpretation coverage

**Files:**
- Modify: `src/test/java/dio/budgeting/TransactionAiValidationTest.java`
- Modify: `src/main/java/dio/budgeting/application/ProcessTransactionAudioUseCase.java` only if testable behavior requires a narrowly scoped seam.

**Interfaces:**
- Consumes: `ProcessTransactionAudioUseCase.interpret(String)`.
- Produces: local tests that do not contact Groq and verify the application validation contract.

- [ ] **Step 1: Write failing tests for validation of AI responses supplied by a mocked `ChatClient`, covering creation, listing, sum with missing period, recent transactions, clarification, and unsupported requests.**
- [ ] **Step 2: Run `./gradlew test --tests "dio.budgeting.TransactionAiValidationTest"` and verify the new tests fail for the intended missing behavior or setup issue.**
- [ ] **Step 3: Implement only the smallest test seam needed to provide deterministic `ChatClient` responses; do not add keyword routing or hard-code the integration test phrases in production.**
- [ ] **Step 4: Re-run the targeted test and verify all deterministic validation assertions pass without network access.**

### Task 3: Verify both test modes

**Files:**
- No production files.

- [ ] **Step 1: Run `./gradlew clean test` and verify the complete default suite passes.**
- [ ] **Step 2: Confirm the default report contains no `TransactionPromptEvaluationIT` or other `*IT` executions tagged `groq-evaluation`.**
- [ ] **Step 3: Run `./gradlew groqEvaluationTest` only when `GROQ_API_KEY` is available and quota is sufficient; treat provider responses and HTTP 429 as external evaluation failures, not deterministic suite failures.**
- [ ] **Step 4: Run `git diff --check` and inspect the final diff for unrelated changes.**
