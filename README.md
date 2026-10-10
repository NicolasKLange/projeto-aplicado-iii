# Projeto Aplicado III — Grupo 39

**Automação da Emissão de Certificados de Calibração — Schulz S.A.**
UniSENAI/SC · 2026.2 · Prof. MSc. Hugo Menezes Barra

Adrian David Castillo Marcano · Nícolas Kretzschmar Lange · Rodrigo Mainardes Ribeiro

## O problema

A Schulz Metrologia leva ~13 dias entre medir um dispositivo na MMC e emitir o certificado
de calibração. O caminho hoje é uma cadeia de planilhas Excel acopladas por cópia manual:
28 valores transportados à mão por certificado, cálculo não validado, sem trilha de auditoria.
O diagnóstico completo, com evidência por arquivo/aba/célula, está em `docs/av01/`.

## Entregas

| # | Tema | Data | Status |
|---|------|------|--------|
| AV01 | Identificação do problema e levantamento de requisitos | 15/09/2026 | Entregue |
| AV02 | Arquitetura em nuvem | 02/10/2026 | Em execução |
| AV03 | MVP parte 1 — microsserviços e APIs | 15/10/2026 | — |
| AV04 | MVP parte 2 — web/mobile e validação | 29/10/2026 | — |
| AV05 | Documentação técnica do MVP e ROI | 19/11/2026 | — |
| AV06 | Pitch técnico-comercial | 03/12/2026 | — |

## Estrutura

```
docs/         documentos de cada entrega (av01 … av06)
docs/decisoes.md   registro de decisões do projeto
diagramas/    fontes .drawio e exportações
src/          código do MVP
evidencias/   prints, gravações e saídas de teste
```

## Dados da empresa

Os arquivos operacionais cedidos pela Schulz (planilhas de cálculo, relatório da MMC e
certificado emitido) **não são versionados aqui**. Contêm dados de cliente e dados pessoais
de técnico (nome e matrícula), e o próprio diagnóstico da AV01 aponta isso como risco
(RA-11, RA-12, RNF-07). Ficam na pasta local do grupo, fora do Git.
