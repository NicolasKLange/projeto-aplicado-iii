# AV02 — Arquitetura em Nuvem · entrega 02/10/2026

Oito itens obrigatórios em quatro blocos, mínimo de 320 linhas de texto mais um diagrama.
Restrições: custo R$ 0,00 no Free Tier e foco estrito nos três eixos pedidos pela empresa
(diagrama cloud, pipeline serverless por upload, matriz de custo).

Premissas fechadas com a Schulz em 17/09 — ver `../decisoes.md` (D-002 a D-004).

## 1. Especificação da arquitetura e diagrama

- [ ] Diagrama no Draw.io: upload da planilha → download do certificado, com região,
      serviços, filas, buckets, banco e fronteiras de confiança
- [ ] Operador e upload manual como única porta de entrada; MMC e LightLab, se aparecerem,
      ficam fora da fronteira da nuvem
- [ ] Exportar PNG em alta resolução; `.drawio` versionado em `diagramas/`
- [ ] Descrição do diagrama, componente a componente (mín. 30 linhas)
- [ ] Mapeamento e justificativa de serviços por camada (mín. 50 linhas)
- [ ] Cada serviço amarrado a pelo menos um RNF da AV01

## 2. Matriz de viabilidade financeira (Free Tier)

- [ ] Volume: ~12 certificados/mês, ~10 invocações por pipeline ≈ 250 execuções/mês
- [ ] Armazenamento em 5 anos: ~200 MB contra 5 GB de cota
- [ ] Regra de ciclo de vida (D-004) descrita explicitamente
- [ ] Tabela: serviço, cota, unidade, consumo estimado, folga %, fonte da cota (mín. 40 linhas)
- [ ] Cotas conferidas na página oficial da AWS na semana da entrega, com URL e data
- [ ] Plano de monitoramento: AWS Budgets em R$ 0,01, alarme de billing, SNS (mín. 40 linhas)
- [ ] Print do budget configurado, se houver conta real

## 3. Pipeline serverless

- [ ] Fluxo passo a passo do `s3:ObjectCreated` ao PDF no bucket de saída (mín. 50 linhas)
- [ ] Por etapa: gatilho, entrada, validação, saída, tratamento de erro, idempotência
- [ ] Caminho do operador até o upload: autenticação, URL pré-assinada, validação de formato
- [ ] Aceitar Excel e CSV; manter OOXML Strict (RNF-13) para o arquivo real da Schulz
- [ ] Etapas amarradas aos RF-01 a RF-11
- [ ] Idempotência por hash SHA-256 (RN-20)
- [ ] Quarentena e reprocessamento (RF-04), incluindo o que o operador vê na rejeição
- [ ] Limite de tempo/memória da Lambda na geração do PDF; separar em duas funções se apertado
- [ ] Estratégia de persistência mapeando o dicionário de dados da AV01 (mín. 40 linhas)
- [ ] Decisão de banco justificada: só DynamoDB é Always Free; RDS free tier expira em
      12 meses e Aurora Serverless v2 não tem camada gratuita
- [ ] Trilha de auditoria append-only (RNF-04)

## 4. Segurança, autenticação e conformidade

- [ ] Perfis IAM por papel: metrologista, signatário, coordenador, auditor, serviços (mín. 40 linhas)
- [ ] Menor privilégio por papel: recurso, ações, condições
- [ ] Ao menos uma policy JSON real, não genérica
- [ ] Autenticação de usuário (Cognito) como autoria do PDF, conforme D-003
- [ ] ICP-Brasil declarada fora da PoC por definição da empresa, com registro no roadmap
- [ ] RN-12 imposta na camada de autorização: signatário ≠ técnico executante
- [ ] Criptografia em repouso (SSE-KMS) e em trânsito (TLS 1.2+), rotação de chaves (mín. 30 linhas)
- [ ] Retenção de 5 anos cruzada com prazo de guarda de dado pessoal (RNF-07, RN-35)
- [ ] LGPD: nome e matrícula do técnico — base legal, minimização, log de acesso, descarte
- [ ] RA-11: dados de terceiro no modelo distribuído

## Fechamento

- [ ] Seção de premissas e pontos em aberto atualizada
- [ ] Rastreabilidade: toda decisão aponta para um RF, RNF ou AN da AV01
- [ ] Mínimos de extensão conferidos item a item
- [ ] Revisão cruzada: quem escreve não revisa
- [ ] Exportar em PDF e subir no AVA até 02/10
