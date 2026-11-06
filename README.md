# Cobranças Pix

[![CI](https://github.com/caiobento7/pix-cobrancas/actions/workflows/ci.yml/badge.svg)](https://github.com/caiobento7/pix-cobrancas/actions/workflows/ci.yml)

API em **Java 21 + Spring Boot** para criar cobranças Pix e dar baixa nelas quando o pagamento cai, sem nunca contar o mesmo Pix duas vezes.

O difícil de receber Pix não é criar o QR Code: é confiar na baixa. O webhook do banco pode chegar **repetido**, **fora de ordem**, **duas vezes ao mesmo tempo** ou **não chegar**. Este projeto trata os quatro casos.

## O que faz

| | |
|---|---|
| `POST /cobrancas` | Cria a cobrança no PSP e devolve o txid e o "Pix copia e cola" |
| `GET /cobrancas/{txid}` | Status, pagamento e devoluções |
| `POST /webhooks/pix` | Recebe a notificação do PSP (formato da API Pix do Banco Central) |
| `PUT /cobrancas/{txid}/devolucoes/{id}` | Devolução parcial ou total, segura para repetir |
| Conciliação (a cada minuto) | Consulta no PSP as cobranças ainda abertas: acha o pagamento cujo webhook se perdeu e expira as vencidas |

## Como a idempotência funciona

```text
webhook do PSP ──┐
                 ├──> ProcessadorDePagamentos ──> RegistroDePagamento (uma transação)
conciliação ─────┘          │                        1. endToEndId já gravado? -> DUPLICADO
                            │                        2. aplica na cobrança (valor confere?)
                            │                        3. INSERT em pagamento_recebido (PK = endToEndId)
                            │
                            └── se o banco recusar (PK repetida ou versão da cobrança mudou):
                                o Pix foi gravado por outra requisição -> DUPLICADO, sem erro 500
```

- **Chave primária no `endToEndId`**: o identificador único da transação no SPI. Duas requisições com o mesmo Pix, mesmo em paralelo, não conseguem gravar duas vezes. É o banco que garante, não um `if`.
- **Trava otimista (`@Version`) na cobrança**: se o webhook e a conciliação mexem na mesma cobrança ao mesmo tempo, um deles falha e é tratado em vez de sobrescrever o outro.
- **Um caminho só**: webhook e conciliação passam pelo mesmo serviço, então um Pix achado pela conciliação e depois notificado pelo webhook atrasado aparece como `DUPLICADO`.
- **Responde 200 para casos esperados** (repetido, txid desconhecido, valor divergente): erro faria o PSP reenviar o mesmo webhook sem parar. Cada caso fica gravado com o resultado, para auditoria.
- **Devolução com id do cliente** (`PUT .../devolucoes/{id}`), como na API Pix: repetir o pedido devolve a mesma devolução, e o mesmo id com outro valor dá 409.
- **Dinheiro em `BigDecimal`** e `NUMERIC(12,2)`. Os valores trafegam como texto com duas casas (`"25.90"`), no formato da API Pix.

Há um teste que dispara **8 webhooks iguais ao mesmo tempo** e confere que só um baixa a cobrança.

## Rodando

Precisa do Java 21. Não precisa instalar Maven (o projeto traz o `mvnw`).

**Sem Docker** (banco H2 em memória e PSP simulado):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

**Com PostgreSQL** via Docker Compose:

```bash
docker compose up --build
```

### Exemplo

```bash
# 1. Cria a cobrança
curl -X POST localhost:8080/cobrancas -H "Content-Type: application/json" \
  -d '{"valor": "25.90", "descricao": "Camisa"}'
# {"txid": "4bdec5a5...", "status": "ATIVA", "pixCopiaECola": "000201...", ...}

# 2. O PSP avisa que o Pix caiu (no perfil local o token é "token-local")
curl -X POST localhost:8080/webhooks/pix -H "Content-Type: application/json" \
  -H "X-Webhook-Token: token-local" \
  -d '{"pix": [{"endToEndId": "E00000000202611061230abcdefghijk", "txid": "4bdec5a5...",
               "valor": "25.90", "horario": "2026-11-06T12:30:00Z"}]}'
# {"processados": [{"endToEndId": "E000...", "resultado": "APLICADO"}]}

# 3. O mesmo webhook de novo
# {"processados": [{"endToEndId": "E000...", "resultado": "DUPLICADO"}]}

# 4. Devolve parte do valor
curl -X PUT localhost:8080/cobrancas/4bdec5a5.../devolucoes/dev001 \
  -H "Content-Type: application/json" -d '{"valor": "5.90", "motivo": "Desconto"}'
```

Erros seguem o padrão Problem Details (RFC 9457) e dizem qual campo está errado:

```json
{"status": 400, "detail": "Há campos inválidos no pedido.",
 "campos": {"valor": "deve ter duas casas decimais, como 25.90"}}
```

## Testes

```bash
./mvnw verify
```

41 testes com JUnit 5:

- **Regras da cobrança**, sem Spring: pagamento, valor divergente, Pix repetido, expiração e devoluções parciais.
- **API de ponta a ponta** com MockMvc, banco e migrações do Flyway: criação, validação, webhook, lote com vários Pix, token, devoluções e conciliação.
- **Concorrência**: 8 webhooks simultâneos do mesmo Pix resultam em 1 `APLICADO` e 7 `DUPLICADO`.

O tempo é controlado por um `Clock` injetado, então o teste de expiração "adianta o relógio" em vez de esperar.

O GitHub Actions roda tudo duas vezes, em **H2** e em **PostgreSQL 17** de verdade, e confere que a imagem Docker compila.

## Estrutura

```text
src/main/java/dev/caiobento/pix
├── cobranca/      Cobranca (entidade com as regras), Devolucao, serviço e controller
├── pagamento/     PixRecebido, registro idempotente e o processador que trata concorrência
├── webhook/       Endpoint do PSP e o interceptor que confere o token
├── conciliacao/   Rotina agendada que consulta o PSP
├── psp/           Interface do PSP e a implementação simulada
└── config/        Propriedades, relógio e agendamento
src/main/resources/db/migration   Migrações do Flyway
```

## Próximos passos

- Cliente HTTP de um PSP real (mTLS e OAuth2), implementando `PspClient`.
- Publicar um evento "cobrança paga" com o padrão *outbox* para outros sistemas (Kafka ou RabbitMQ).
- Métricas de webhooks recebidos, duplicados e divergentes, com Micrometer.

---

Feito por [Caio Bento](https://caiobento.vercel.app).
