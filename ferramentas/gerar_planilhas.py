"""Gera as planilhas sintéticas de teste da AV03 (T06) e o oráculo calculado.

Uso: python3 ferramentas/gerar_planilhas.py   (a partir da raiz do repositório)
Requer: openpyxl (dependência só desta ferramenta; o projeto Java não usa Python) e,
opcionalmente, LibreOffice (soffice) no PATH. Com ele, cada arquivo é reaberto e salvo pelo
Calc, para ficar com a estrutura de um arquivo real (sharedStrings), e não com as strings
inline que o openpyxl grava.

Todos os dados são fictícios. O oráculo é calculado com decimal.Decimal, de forma
independente da implementação Java, e serve de valor esperado para os testes da fase 2.
Linhas são numeradas como no Excel: cabeçalho = linha 1, primeiro ponto = linha 2.

Não gera inv-R03-strict.xlsx: o formato Strict precisa ser salvo pelo Excel desktop.
R01 (5 MB) e R09 (10 mil linhas) são gerados dentro dos próprios testes.
"""
import shutil
import subprocess
import tempfile
from datetime import datetime
from decimal import Decimal, getcontext
from pathlib import Path

from openpyxl import Workbook

getcontext().prec = 28

SAIDA = Path("src/test/resources/planilhas")
ORACULO = Path("docs/av03/oraculo-planilhas.md")
ABA = "Medicoes"
DATA_FIXA = datetime(2026, 10, 10, 0, 0, 0)

CAB = ["Caracteristica", "ValorReferencia", "Leitura1", "Leitura2",
       "ToleranciaSuperior", "ToleranciaInferior"]
CAB3 = ["Caracteristica", "ValorReferencia", "Leitura1", "Leitura2", "Leitura3",
        "ToleranciaSuperior", "ToleranciaInferior"]

# Base dos inválidos: os mesmos três pontos do ok-aprovado.
BASE = [
    ["Diâmetro eixo", "4.12", "4.13", "4.14", "0.03", "-0.03"],
    ["Comprimento eixo", "10.8", "10.807", "10.811", "0.02", "-0.02"],
    ["Largura base", "0.9288", "0.9286", "0.9290", "0.007", "-0.007"],
]

# (arquivo, cabeçalho, linhas, aba, descrição) — cenários válidos com oráculo
VALIDOS = [
    ("ok-aprovado.xlsx", CAB, BASE, "Todos os pontos dentro, com folga"),
    ("ok-reprovado.xlsx", CAB, [
        ["Diâmetro furo A", "6.00", "6.01", "6.02", "0.03", "-0.03"],
        ["Diâmetro furo B", "6.00", "6.05", "6.07", "0.03", "-0.03"],
        ["Altura flange", "25.4", "25.39", "25.41", "0.05", "-0.05"],
        ["Planicidade face", "0", "0.012", "0.014", "0.05", "0"],
    ], "Um ponto fora entre quatro"),
    ("ok-limite.xlsx", CAB, [
        ["Comprimento pino", "10", "10.07", "10.09", "0.08", "-0.08"],
        ["Diâmetro bucha", "2", "1.94", "1.96", "0.05", "-0.05"],
    ], "|E| = LEA exato, lados positivo e negativo; em double os dois dão FORA"),
    ("ok-erro-negativo.xlsx", CAB, [
        ["Diâmetro alojamento", "20", "19.97", "19.98", "0.03", "-0.03"],
        ["Espessura chapa", "3.5", "3.49", "3.49", "0.02", "-0.02"],
    ], "Média abaixo da referência, ainda dentro (testa o abs)"),
    ("ok-unilateral.xlsx", CAB, [
        ["Planicidade base", "0", "0.02", "0.03", "0.05", "0"],
        ["Posição furo", "0", "0.11", "0.13", "0.1", "0"],
        ["Paralelismo face", "0", "0", "0.004", "0.01", "0"],
    ], "Tol− = 0: um dentro, um fora e uma leitura igual a zero (aceita, R15 é só < 0)"),
    ("ok-arredondamento.xlsx", CAB, [
        ["Diâmetro pino guia", "0.06", "0.0604", "0.0605", "0.0004", "-0.0004"],
        ["Diâmetro furo guia", "0.06", "0.0595", "0.0596", "0.0004", "-0.0004"],
    ], "|E| = 0,00045 > LEA; arredondar a média a 4 casas antes de comparar aprovaria"),
    ("ok-tres-leituras.xlsx", CAB3, [
        ["Comprimento haste", "10", "10.01", "10.02", "10.02", "0.02", "-0.02"],
        ["Diâmetro haste", "1", "1.00", "1.00", "1.01", "0.003", "-0.003"],
    ], "Três leituras com média em dízima; o segundo ponto fica fora por 0,00033…"),
]


def substituir(linhas, linha_excel, coluna, valor):
    """Copia as linhas e troca uma célula (linha no padrão Excel, coluna pelo cabeçalho)."""
    novas = [list(l) for l in linhas]
    novas[linha_excel - 2][CAB.index(coluna)] = valor
    return novas


def sem_coluna(cab, linhas, coluna):
    i = cab.index(coluna)
    return [c for c in cab if c != coluna], [l[:i] + l[i + 1:] for l in linhas]


cab_r05, lin_r05 = sem_coluna(CAB, BASE, "ToleranciaSuperior")
cab_r06, lin_r06 = sem_coluna(CAB, BASE, "Leitura2")
cab_r07 = CAB + ["Desvio"]
lin_r07 = [l + ["0.015"] for l in BASE]

# (arquivo, regra, cabeçalho, linhas, aba, cenário, erros esperados)
INVALIDOS = [
    ("inv-R04-sem-aba.xlsx", "R04", CAB, BASE, "Medicao",
     "Aba chamada `Medicao` (sem o s)", ["Aba Medicoes ausente"]),
    ("inv-R05-sem-tolsup.xlsx", "R05", cab_r05, lin_r05, ABA,
     "Sem a coluna ToleranciaSuperior", ["Cabeçalho ToleranciaSuperior ausente"]),
    ("inv-R06-uma-leitura.xlsx", "R06", cab_r06, lin_r06, ABA,
     "Só Leitura1", ["Menos de duas colunas Leitura*"]),
    ("inv-R07-desvio.xlsx", "R07", cab_r07, lin_r07, ABA,
     "Coluna Desvio presente", ["Coluna Desvio presente"]),
    ("inv-R08-vazia.xlsx", "R08", CAB, [], ABA,
     "Só o cabeçalho", ["Nenhuma linha de dados"]),
    ("inv-R10-obrigatorio.xlsx", "R10", CAB,
     substituir(BASE, 3, "Leitura2", None), ABA,
     "Leitura2 vazia na linha 3", ["Linha 3, Leitura2: valor obrigatório ausente"]),
    ("inv-R11-texto.xlsx", "R11", CAB,
     substituir(BASE, 2, "Leitura1", "abc"), ABA,
     "Texto 'abc' na Leitura1", ["Linha 2, Leitura1: 'abc' não é número"]),
    ("inv-R12-repetida.xlsx", "R12", CAB,
     BASE + [["Diâmetro eixo", "4.12", "4.12", "4.13", "0.03", "-0.03"]], ABA,
     "Diâmetro eixo nas linhas 2 e 5", ["Característica repetida nas linhas 2 e 5"]),
    ("inv-R13-tolsup-zero.xlsx", "R13", CAB,
     substituir(substituir(BASE, 4, "ToleranciaSuperior", "0"), 4, "ToleranciaInferior", "0"), ABA,
     "Tol+ = 0 e Tol− = 0 na linha 4", ["Linha 4: tolerância superior precisa ser > 0"]),
    ("inv-R14-assimetrica.xlsx", "R14", CAB,
     substituir(substituir(BASE, 2, "ToleranciaSuperior", "0.05"), 2, "ToleranciaInferior", "-0.02"), ABA,
     "+0,05 / −0,02 na linha 2", ["Linha 2: tolerância fora do escopo"]),
    ("inv-R15-negativa-unilateral.xlsx", "R15", CAB,
     BASE + [["Planicidade base", "0", "-0.002", "0.003", "0.05", "0"]], ABA,
     "Leitura1 = −0,002 com Tol− = 0 na linha 5", ["Linha 5: leitura negativa em característica unilateral"]),
    ("inv-multiplos-erros.xlsx", "R10, R11, R14", CAB,
     substituir(substituir(substituir(substituir(BASE,
                2, "Leitura2", None),
                3, "Leitura1", "n/a"),
                4, "ToleranciaSuperior", "0.007"),
                4, "ToleranciaInferior", "-0.002"), ABA,
     "Um erro diferente em cada linha (2, 3 e 4)",
     ["Linha 2, Leitura2: valor obrigatório ausente",
      "Linha 3, Leitura1: 'n/a' não é número",
      "Linha 4: tolerância fora do escopo"]),
]


def celula(valor):
    """Número vira célula numérica; texto que não é número fica como texto (casos R11)."""
    if valor is None:
        return None
    try:
        return float(Decimal(valor))
    except Exception:
        return valor


def gravar(arquivo, cab, linhas, aba=ABA):
    wb = Workbook()
    ws = wb.active
    ws.title = aba
    ws.append(cab)
    for linha in linhas:
        ws.append([linha[0]] + [celula(v) for v in linha[1:]])
    # LGPD: nenhum nome de pessoa nos metadados
    wb.properties.creator = ""
    wb.properties.lastModifiedBy = ""
    wb.properties.created = DATA_FIXA
    wb.properties.modified = DATA_FIXA
    wb.save(SAIDA / arquivo)


def normalizar_com_libreoffice():
    soffice = shutil.which("soffice")
    if not soffice:
        print("Aviso: soffice não encontrado; arquivos ficam como o openpyxl gravou.")
        return
    arquivos = [str(f) for f in sorted(SAIDA.glob("*.xlsx")) if f.name != "inv-R02-nao-xlsx.xlsx"]
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run([soffice, "--headless", f"-env:UserInstallation=file://{tmp}/perfil",
                        "--convert-to", "xlsx:Calc MS Excel 2007 XML", "--outdir", f"{tmp}/out",
                        *arquivos], check=True, capture_output=True)
        for f in Path(tmp, "out").glob("*.xlsx"):
            shutil.copy(f, SAIDA / f.name)


def fmt(d):
    s = format(d.normalize(), "f")
    return s.replace(".", ",").replace("-", "−")


def oraculo(cab, linhas):
    n_leit = sum(1 for c in cab if c.startswith("Leitura"))
    saida, todos_dentro = [], True
    for l in linhas:
        ref = Decimal(l[1])
        leituras = [Decimal(v) for v in l[2:2 + n_leit]]
        lea = Decimal(l[2 + n_leit])
        media = sum(leituras) / len(leituras)
        erro = media - ref
        dentro = abs(erro) <= lea
        todos_dentro &= dentro
        dizima = len(format(media.normalize(), "f")) > 20
        saida.append((l[0], " · ".join(fmt(x) for x in leituras), fmt(ref), fmt(lea),
                      fmt(media) + (" (dízima)" if dizima else ""),
                      fmt(erro), "DENTRO_DA_TOLERANCIA" if dentro else "FORA_DA_TOLERANCIA"))
    return saida, "APROVADO" if todos_dentro else "REPROVADO"


def main():
    SAIDA.mkdir(parents=True, exist_ok=True)
    ORACULO.parent.mkdir(parents=True, exist_ok=True)
    md = ["# Oráculo das planilhas sintéticas — AV03 (T06)", "",
          "Gerado por `ferramentas/gerar_planilhas.py` com `decimal.Decimal` (precisão 28), "
          "independente da implementação Java. Média e erro em **precisão completa**, sem "
          "arredondamento. LEA = ToleranciaSuperior (D-T04-2); `|E| ≤ LEA` → "
          "`DENTRO_DA_TOLERANCIA` (D-T04-1). Linhas no padrão do Excel (cabeçalho = 1).", "",
          "## Cenários válidos", ""]
    for arquivo, cab, linhas, descricao in VALIDOS:
        gravar(arquivo, cab, linhas)
        pontos, parecer = oraculo(cab, linhas)
        md += [f"### `{arquivo}` → `{parecer}`", "", descricao, "",
               "| Característica | Leituras | Referência | LEA | Média | Erro | Status |",
               "|---|---|---|---|---|---|---|"]
        md += ["| " + " | ".join(p) + " |" for p in pontos]
        md.append("")

    md += ["## Cenários inválidos", "",
           "| Arquivo | Regra | Cenário | Erros esperados |", "|---|---|---|---|"]
    for arquivo, regra, cab, linhas, aba, cenario, erros in INVALIDOS:
        gravar(arquivo, cab, linhas, aba)
        md.append(f"| `{arquivo}` | {regra} | {cenario} | {'<br>'.join(erros)} |")

    (SAIDA / "inv-R02-nao-xlsx.xlsx").write_text(
        ";".join(CAB) + "\n" + "\n".join(";".join(l) for l in BASE) + "\n", encoding="utf-8")
    md += ["| `inv-R02-nao-xlsx.xlsx` | R02 | Arquivo de texto (CSV) com extensão .xlsx "
           "| Formato diferente de xlsx |",
           "| `inv-R03-strict.xlsx` | R03 | `ok-aprovado` salvo como Strict no Excel desktop "
           "(não gerado por este script) | Formato OOXML Strict |", "",
           "R01 (5 MB) e R09 (10 mil linhas) são gerados dentro dos testes, não versionados.", ""]
    normalizar_com_libreoffice()
    ORACULO.write_text("\n".join(md), encoding="utf-8")


if __name__ == "__main__":
    main()
