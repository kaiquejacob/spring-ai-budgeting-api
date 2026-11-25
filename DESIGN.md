# Design system

## Tokens

| Token | Valor | Uso |
|---|---|---|
| `--background` | `#08090a` | Fundo principal |
| `--sidebar` | `#0b0c0e` | Barra lateral |
| `--surface` | `#101113` | Superfícies e cartões |
| `--input` | `#131418` | Campos e bolhas |
| `--surface-hover` | `#181a1e` | Realce ao passar o mouse |
| `--border` | `#23252a` | Divisórias |
| `--border-hover` | `#34373d` | Borda interativa |
| `--border-input` | `#62666d` | Borda do compositor e campos |
| `--text` | `#f7f8f8` | Texto principal |
| `--text-secondary` | `#8a8f98` | Texto de apoio |
| `--accent` | `#e4f222` | Foco, estado ativo e indicadores |
| `--positive` | `#4ade80` | Valores positivos e status |
| `--negative` | `#f87171` | Valores negativos e status |
| `--success-text` | `#86efac` | Texto de sucesso |
| `--success-background` | `rgba(74,222,128,0.08)` | Fundo de sucesso |
| `--success-border` | `rgba(74,222,128,0.30)` | Borda de sucesso |
| `--error-text` | `#fca5a5` | Texto de erro |
| `--error-background` | `rgba(248,113,113,0.08)` | Fundo de erro |
| `--error-border` | `rgba(248,113,113,0.30)` | Borda de erro |
| `--primary` | `#f7f8f8` | Fundo do botão primário |
| `--primary-text` | `#08090a` | Texto do botão primário |
| `--primary-hover` | `#e3e4e6` | Hover do botão primário |
| `--primary-disabled-background` | `#23252a` | Fundo primário desabilitado |
| `--primary-disabled-text` | `#8a8f98` | Texto primário desabilitado |
| `--radius-small` / `--radius-sm` | `6px` | Controles |
| `--radius-medium` / `--radius-md` / `--radius-lg` | `12px` | Cartões e painéis |
| `--font-sans` | Inter e stack do sistema | Texto geral |
| `--font-mono` | JetBrains Mono, ui-monospace, monospace | Valores numéricos |
| `--duration-fast` | `150ms` | Interações rápidas |
| `--duration-base` | `200ms` | Interações comuns |
| `--duration-slow` | `280ms` | Interações mais lentas |
| `--ease-out` | `cubic-bezier(0.16, 1, 0.3, 1)` | Desaceleração |
| `--focus-ring` | `var(--accent)` | Indicador de foco |

## Regras

- Usar o accent apenas em foco, item ativo e indicadores.
- O botão primário é uma pílula branca com texto escuro.
- Positivo e negativo são exclusivos de valores e status.
- Exibir números em fonte mono com algarismos tabulares.
- Animações de UI frequente duram de 150 a 280 ms; mensagens, até 200 ms.
- Respeitar sempre `prefers-reduced-motion`.

## Decisões de design

- Layout de chat inspirado no Perplexity: sidebar estreita e coluna única.
- Tela inicial inspirada no Revolut, com cartão escuro em CSS.
- Fase 7: cena de scroll inspirada no Mercury, com alternativa estática.

## Evitar

- Cartões para todo conteúdo.
- Gradientes decorativos.
- Animações em toda interação.
