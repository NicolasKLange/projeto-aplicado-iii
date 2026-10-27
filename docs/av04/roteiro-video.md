# Roteiro do vídeo da AV04 (6 a 8 minutos)

Gravação de tela com narração (OBS Studio ou a Barra de Jogos do Windows, `Win + Alt + R`), uma tomada
por bloco. Publicar no YouTube como **Não listado** e colar o link no cabeçalho e na §9 do relatório.

## Antes de gravar 🔒

- [ ] Deploy feito (`infra/deploy.sh` e `infra/deploy-web.sh`) e `infra/smoke-web.sh` sem falhas.
- [ ] Usuário de demonstração criado (bloco 0) com e-mail fictício, por exemplo `demo@example.com`.
- [ ] Console da AWS sem número de conta à vista: menu da conta fechado; nada de *Account ID* no canto.
- [ ] Nenhuma aba com e-mail pessoal, JWT, URL pré-assinada, chave de acesso ou dado real da Schulz.
- [ ] Só planilhas sintéticas de `nucleo/src/test/resources/planilhas/`.
- [ ] Depois de gravar, assista uma vez só procurando esses itens.

**Bloco 0 — fora da gravação: usuário de demonstração**

```bash
POOL=$(aws --profile pa3 --region us-east-2 cloudformation describe-stacks --stack-name pa3-av04 \
  --query "Stacks[0].Outputs[?OutputKey=='UserPoolId'].OutputValue" --output text)
aws --profile pa3 --region us-east-2 cognito-idp admin-create-user --user-pool-id "$POOL" \
  --username demo@example.com --message-action SUPPRESS \
  --user-attributes Name=email,Value=demo@example.com Name=email_verified,Value=true Name=name,Value="Engenheiro Demonstração"
aws --profile pa3 --region us-east-2 cognito-idp admin-set-user-password --user-pool-id "$POOL" \
  --username demo@example.com --password '<senha forte com 12+ caracteres>' --permanent
```

## Blocos

| # | Tempo | Na tela | Narração (resumo) |
| --- | --- | --- | --- |
| 1 | 0:00–0:40 | Slide ou README: o problema | "A Schulz leva cerca de 13 dias entre a medição na MMC e o certificado, com 28 valores copiados à mão por certificado. A AV04 integra o diagnóstico (AV01), a arquitetura (AV02) e o motor de cálculo (AV03) numa plataforma web publicada na AWS, dentro da camada gratuita." |
| 2 | 0:40–1:40 | Diagrama da arquitetura (`diagramas/`) | Upload direto ao S3 por URL pré-assinada → evento dispara a `fn-ingestao` → núcleo Java da AV03 → DynamoDB → `fn-certificado` gera o PDF → verificação pública. Interface servida pela `fn-web` no mesmo HTTP API (D-027), tudo em `us-east-2`. |
| 3 | 1:40–2:10 | URL da plataforma numa aba anônima; login | Login direto no Cognito, sem a senha passar pela aplicação; token só em memória. Mostrar a senha errada → mensagem genérica. |
| 4 | 2:10–3:10 | Processar `ok-aprovado.xlsx` e depois `ok-reprovado.xlsx` | Estados "enviando" → "processando" → resultado. Enums literais `DENTRO_DA_TOLERANCIA`/`FORA_DA_TOLERANCIA`, parecer `APROVADO`/`REPROVADO`, 5 casas com `HALF_EVEN`. |
| 5 | 3:10–3:50 | **Baixar certificado** e abrir o PDF | "Gerado por" com o nome do usuário autenticado (DE-03), SHA-256 da planilha, token e QR Code. Rodapé de PoC acadêmica. |
| 6 | 3:50–4:40 | Celular (espelhado ou filmado) lendo o QR, fora do Wi-Fi | Página pública `/verificar/<token>`, sem login: identificador, data, parecer e hash. Escolher o PDF no celular → "Arquivo íntegro". |
| 7 | 4:40–5:20 | `inv-multiplos-erros.xlsx` e reenvio do `ok-reprovado.xlsx` | Rejeição com linha, coluna e regra (R10, R11, R14); reenvio → `DUPLICADA` (RN-20, sentinela de hash na mesma transação). |
| 8 | 5:20–5:50 | Aba **Histórico** | Indicadores de gestão (envios, emitidos, aprovados, reprovados, rejeitados) e lista; abrir um envio antigo. |
| 9 | 5:50–7:20 | Console AWS em `us-east-2` | Lambda (5 funções, `java21` e `nodejs22.x`); S3 (buckets privados, ciclo de vida de 5 anos); DynamoDB (modo provisionado, 10 de 25 RCU/WCU); CloudWatch (log da `fn-ingestao` só com id, hash e tamanho); *Billing → Free Tier* e Budgets: US$ 0,00. |
| 10 | 7:20–7:50 | README / matriz | Repositório público, scripts de deploy, matriz de rastreabilidade; limitações (sem aprovação do signatário, sem Object Lock, sem MFA) e próximos passos. |

## Depois de gravar

- [ ] Publicar como **Não listado**; abrir o link numa aba anônima.
- [ ] Colar o link em `docs/av04/AV4_fonte.md` (cabeçalho e §9) e gerar o PDF de novo.
- [ ] Apagar o usuário de demonstração (`cognito-idp admin-delete-user`), se quiser.
