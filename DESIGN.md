# Design system

## Direção visual

- Interface desktop de chat inspirada no app do Perplexity: base escura neutra, hierarquia discreta e coluna única.
- Sidebar de 240 px, sem borda vertical, com nova conversa como item de navegação e chip do usuário no rodapé.
- Estado vazio centralizado, deslocado cerca de 5% para cima; símbolo financeiro inline, título, compositor e sugestões.
- Mensagens, propostas e compositor compartilham largura máxima de 672 px.
- Paleta inspirada no app do Perplexity; o accent lime permanece restrito a foco, item ativo e indicadores.

## Tokens

| Grupo | Tokens | Uso |
|---|---|---|
| Superfícies | `--background`, `--sidebar`, `--surface`, `--input`, `--surface-hover` | Fundo e camadas |
| Bordas | `--border`, `--border-input`, `--border-subtle`, `--border-hover` | Contornos e divisórias |
| Texto e ícones | `--text`, `--text-secondary`, `--text-muted`, `--icon` | Hierarquia e ícones |
| Destaque | `--accent`, `--focus-ring`, `--focus-halo` | Foco, item ativo e indicadores |
| Ação | `--primary`, `--primary-hover`, `--primary-text`, `--primary-disabled-*` | Ações e estado desabilitado |
| Estado | `--positive`, `--negative`, `--success-*`, `--error-*` | Status em superfícies escuras neutras |
| Sombras | `--composer-highlight`, `--composer-shadow` | Brilho interno e sombra do compositor |
| Forma e tipo | `--radius-*`, `--font-sans`, `--font-mono` | Raios e tipografia |
| Movimento | `--duration-*`, `--ease-out` | Feedback funcional |

## Medidas e regras

- Sidebar: 240 px; itens: 38 px de altura, ícones de 18 px e intervalo de 12 px.
- Símbolo da marca: 20 px na sidebar e 24 px no estado vazio; SVG inline, traço de 1.5 px.
- Título vazio: 26 px, peso 400, line-height 1.25; 32 px após o símbolo e 72 px até o compositor.
- Compositor: largura máxima de 672 px, altura mínima de 120 px, altura total próxima de 120 px, raio de 18 px e padding `14px 12px 8px 20px`.
- Botões do compositor: 32 px; ícones: 18 px. Sugestões: 34 px de altura e texto de 14 px.
- Usar `currentColor` nos SVGs e centralizar cores em `tokens.css`.
- Valores monetários usam fonte mono com algarismos tabulares; rótulos usam caixa normal.
- Respeitar foco visível e `prefers-reduced-motion`; evitar cartões e animações decorativas em excesso.

## Movimento

- Curva de entrada: `--ease-out` (`cubic-bezier(0.16, 1, 0.3, 1)`); durações: `--duration-fast` 150 ms, `--duration-base` 200 ms e `--duration-slow` 280 ms.
- Mensagens novas entram com opacity e translateY (200 ms); respostas que substituem o estado de análise aparecem em fade (150 ms).
- Enquanto texto, áudio ou recibo aguarda resposta, o indicador pulsa (1.2 s) e dois esqueletos fazem shimmer (1.4 s); o ponto fica estático quando movimento reduzido está ativo.
- Cartões de proposta entram com opacity, translateY e scale de 0.98 a 1 (200 ms); confirmação usa active scale 0.98 e selo em fade (150 ms).
- Na primeira mensagem, título, símbolo e sugestões desaparecem em fade (150 ms); o compositor usa FLIP em transform (280 ms). Nova conversa retorna com fade de 150 ms.
- Botões, chips, navegação e anel de foco mudam cor/fundo em 150 ms. A rolagem até novas mensagens é suave.
- Não animar dimensões, margens ou coordenadas de layout; shimmer usa apenas translateX, sem mudar tamanho.
- `prefers-reduced-motion: reduce` remove transições, animações e rolagem suave; os estados finais são aplicados imediatamente.
