"""Pós-processa o DOCX gerado pelo pandoc para igualar a formatação da AV2.

Uso (a partir de docs/av03/):
  pandoc AV3_fonte.md --reference-doc=../av02/AV2_Grupo39_Projeto_Aplicado_III.docx -o /tmp/av3.docx
  python3 ../../ferramentas/formatar_docx.py /tmp/av3.docx AV3_Projeto_Aplicado_III.docx
Requer: lxml.
- corpo justificado (BodyText, FirstParagraph e itens de lista sem link);
- tabelas a 100% da largura, layout fixo, recuo 108, fonte 8 pt, cabeçalho à esquerda e
  colunas largas justificadas;
- Figura 1 em seção paisagem, com 9,2 pol de largura (como a Figura 1 da AV2).
"""
import sys, zipfile, shutil, copy
from lxml import etree

W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
A = "http://schemas.openxmlformats.org/drawingml/2006/main"
ns = {"w": W, "wp": WP, "a": A}
q = lambda t: "{%s}%s" % (W, t)

LARGURA_TEXTO = 9359      # 6,5 pol menos o recuo da tabela, como na AV2
COLUNA_LARGA = 3500       # colunas a partir desta largura recebem texto justificado

def filho(el, tag, antes=None):
    e = el.find("w:" + tag, ns)
    if e is None:
        e = etree.Element(q(tag))
        if antes is not None:
            idx = antes(el)
            el.insert(idx, e)
        else:
            el.append(e)
    return e

def set_jc(p, val):
    ppr = p.find("w:pPr", ns)
    if ppr is None:
        ppr = etree.Element(q("pPr")); p.insert(0, ppr)
    jc = ppr.find("w:jc", ns)
    if jc is None:
        jc = etree.SubElement(ppr, q("jc"))
        # jc vem depois de spacing/ind; mover para antes de rPr, se houver
        rpr = ppr.find("w:rPr", ns)
        if rpr is not None:
            ppr.remove(jc); rpr.addprevious(jc)
    jc.set(q("val"), val)

def estilo(p):
    s = p.find("w:pPr/w:pStyle", ns)
    return s.get(q("val")) if s is not None else None

def tamanho_8pt(p):
    for r in p.iter(q("r")):
        rpr = r.find("w:rPr", ns)
        if rpr is None:
            rpr = etree.Element(q("rPr")); r.insert(0, rpr)
        for t in ("sz", "szCs"):
            e = rpr.find("w:" + t, ns)
            if e is None:
                e = etree.SubElement(rpr, q(t))
            e.set(q("val"), "16")

def sect(paisagem):
    s = etree.Element(q("sectPr"))
    etree.SubElement(s, q("type")).set(q("val"), "nextPage")
    pg = etree.SubElement(s, q("pgSz"))
    mar = etree.SubElement(s, q("pgMar"))
    if paisagem:
        pg.set(q("orient"), "landscape"); pg.set(q("w"), "15840"); pg.set(q("h"), "12240")
        for k, v in dict(left="1000", right="1000", gutter="0", header="0", top="720", footer="0", bottom="720").items():
            mar.set(q(k), v)
    else:
        pg.set(q("w"), "12240"); pg.set(q("h"), "15840")
        for k, v in dict(left="1440", right="1440", gutter="0", header="0", top="1440", footer="0", bottom="1440").items():
            mar.set(q(k), v)
    etree.SubElement(s, q("pgNumType")).set(q("fmt"), "decimal")
    return s

def anexar_sect(p, s):
    ppr = p.find("w:pPr", ns)
    if ppr is None:
        ppr = etree.Element(q("pPr")); p.insert(0, ppr)
    ppr.append(s)

def main(ent, sai):
    shutil.copy(ent, sai)
    with zipfile.ZipFile(ent) as z:
        xml = z.read("word/document.xml")
        ordem = z.namelist()
        outros = {n: z.read(n) for n in ordem if n != "word/document.xml"}
    raiz = etree.fromstring(xml)
    corpo = raiz.find("w:body", ns)

    # 1. Corpo justificado
    for p in corpo.findall("w:p", ns):
        st = estilo(p)
        lista = p.find("w:pPr/w:numPr", ns) is not None
        link = p.find(".//w:hyperlink", ns) is not None
        if st in ("BodyText", "FirstParagraph") or (st == "Compact" and lista and not link):
            set_jc(p, "both")

    # 2. Tabelas
    for tbl in corpo.iter(q("tbl")):
        tpr = tbl.find("w:tblPr", ns)
        for t in ("tblW", "jc", "tblInd", "tblLayout"):
            e = tpr.find("w:" + t, ns)
            if e is not None:
                tpr.remove(e)
        estilo_t = tpr.find("w:tblStyle", ns)
        pos = list(tpr).index(estilo_t) + 1 if estilo_t is not None else 0
        novos = []
        e = etree.Element(q("tblW")); e.set(q("w"), "5000"); e.set(q("type"), "pct"); novos.append(e)
        e = etree.Element(q("jc")); e.set(q("val"), "left"); novos.append(e)
        e = etree.Element(q("tblInd")); e.set(q("w"), "108"); e.set(q("type"), "dxa"); novos.append(e)
        e = etree.Element(q("tblLayout")); e.set(q("type"), "fixed"); novos.append(e)
        for k, e in enumerate(novos):
            tpr.insert(pos + k, e)
        grid = tbl.find("w:tblGrid", ns)
        cols = grid.findall("w:gridCol", ns)
        soma = sum(int(c.get(q("w"))) for c in cols)
        larguras = [round(int(c.get(q("w"))) * LARGURA_TEXTO / soma) for c in cols]
        larguras[-1] += LARGURA_TEXTO - sum(larguras)
        # Nenhuma palavra do cabeçalho quebra no meio: largura mínima por coluna
        linhas0 = tbl.findall("w:tr", ns)
        if linhas0:
            # menor largura que não quebra nenhuma palavra da coluna (limite: 40% da tabela)
            minimos = [0] * len(larguras)
            for tr in linhas0:
                for j, tc in enumerate(tr.findall("w:tc", ns)[:len(larguras)]):
                    for p in tc.findall("w:p", ns):
                        texto = "".join(p.itertext())
                        maior = max((len(x) for x in texto.split()), default=0)
                        minimos[j] = max(minimos[j], min(maior * 100 + 300, int(LARGURA_TEXTO * 0.4)))
            for j, m in enumerate(minimos):
                falta = m - larguras[j]
                if falta > 0:
                    larguras[j] += falta
                    while falta > 0:
                        k = max(range(len(larguras)), key=lambda x: larguras[x] - minimos[x])
                        tira = min(falta, larguras[k] - minimos[k])
                        if tira <= 0:
                            break
                        larguras[k] -= tira; falta -= tira
        for c, w in zip(cols, larguras):
            c.set(q("w"), str(w))
        linhas = tbl.findall("w:tr", ns)
        cabecalho = tbl.find("w:tr/w:trPr/w:tblHeader", ns) is not None
        for i, tr in enumerate(linhas):
            for j, tc in enumerate(tr.findall("w:tc", ns)):
                tcpr = tc.find("w:tcPr", ns)
                if tcpr is None:
                    tcpr = etree.Element(q("tcPr")); tc.insert(0, tcpr)
                tcw = tcpr.find("w:tcW", ns)
                if tcw is None:
                    tcw = etree.Element(q("tcW")); tcpr.insert(0, tcw)
                tcw.set(q("w"), str(larguras[j] if j < len(larguras) else larguras[-1])); tcw.set(q("type"), "dxa")
                larga = j < len(larguras) and larguras[j] >= COLUNA_LARGA
                for p in tc.findall("w:p", ns):
                    tamanho_8pt(p)
                    eh_cab = i == 0 and cabecalho
                    codigo = any(r.find("w:rPr/w:rStyle", ns) is not None and
                                 r.find("w:rPr/w:rStyle", ns).get(q("val")) == "VerbatimChar"
                                 for r in p.iter(q("r")))
                    set_jc(p, "both" if (larga and not eh_cab and not codigo) else "left")

    # 3. Figura 1 em seção paisagem
    paras = corpo.findall("w:p", ns)
    for k, p in enumerate(paras):
        if estilo(p) == "CaptionedFigure":
            ext = p.find(".//wp:extent", ns)
            cx, cy = int(ext.get("cx")), int(ext.get("cy"))
            novo_cx = int(9.2 * 914400); novo_cy = round(cy * novo_cx / cx)
            ext.set("cx", str(novo_cx)); ext.set("cy", str(novo_cy))
            for a in p.iter("{%s}ext" % A):
                if a.get("cx") == str(cx):
                    a.set("cx", str(novo_cx)); a.set("cy", str(novo_cy))
            anterior = p.getprevious()
            anexar_sect(anterior, sect(paisagem=False))
            legenda = paras[k + 1]
            assert estilo(legenda) == "ImageCaption"
            anexar_sect(legenda, sect(paisagem=True))
            break  # só a Figura 1

    # 4. Ordem dos filhos de pPr exigida pelo schema (o pandoc põe numPr antes de pStyle)
    ordem_ppr = ("pStyle keepNext keepLines pageBreakBefore framePr widowControl numPr "
                 "suppressLineNumbers pBdr shd tabs suppressAutoHyphens kinsoku wordWrap "
                 "overflowPunct topLinePunct autoSpaceDE autoSpaceDN bidi adjustRightInd "
                 "snapToGrid spacing ind contextualSpacing mirrorIndents suppressOverlap jc "
                 "textDirection textAlignment textboxTightWrap outlineLvl divId cnfStyle rPr "
                 "sectPr pPrChange").split()
    for ppr in raiz.iter(q("pPr")):
        filhos = list(ppr)
        filhos.sort(key=lambda c: ordem_ppr.index(etree.QName(c).localname)
                    if etree.QName(c).localname in ordem_ppr else len(ordem_ppr))
        for c in filhos:
            ppr.append(c)

    xml = etree.tostring(raiz, xml_declaration=True, encoding="UTF-8", standalone=True)

    # 5. nsid da numeração com 8 dígitos hexadecimais (o pandoc grava menos)
    import re as _re
    num = outros["word/numbering.xml"].decode("utf-8")
    num = _re.sub(r'(<w:nsid w:val=")([0-9A-Fa-f]{1,7})(")', lambda m: m.group(1) + m.group(2).rjust(8, "0") + m.group(3), num)
    outros["word/numbering.xml"] = num.encode("utf-8")
    # settings.xml do pandoc: doNotTrackMoves fora da ordem do schema
    cfg = outros["word/settings.xml"].decode("utf-8")
    m = _re.search(r"<w:doNotTrackMoves ?/>", cfg)
    f = _re.search(r"<w:stylePaneFormatFilter [^>]*/>", cfg)
    if m and f and m.start() > f.end():
        cfg = cfg[:m.start()] + cfg[m.end():]
        cfg = cfg[:f.end()] + m.group(0) + cfg[f.end():]
        outros["word/settings.xml"] = cfg.encode("utf-8")
    # O pandoc copia a mídia do documento de referência (a figura da AV2) sem referenciá-la:
    # remove o que o document.xml.rels não usa e declara o tipo PNG.
    rels = outros.get("word/_rels/document.xml.rels", b"").decode("utf-8")
    ordem = [n for n in ordem
             if not (n.startswith("word/media/") and n[len("word/"):] not in rels)]
    ct = outros["[Content_Types].xml"].decode("utf-8")
    if 'Extension="png"' not in ct:
        ct = ct.replace("<Default ", '<Default Extension="png" ContentType="image/png"/><Default ', 1)
        outros["[Content_Types].xml"] = ct.encode("utf-8")
    with zipfile.ZipFile(sai, "w", zipfile.ZIP_DEFLATED) as z:
        for n in ordem:
            z.writestr(n, xml if n == "word/document.xml" else outros[n])

if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
