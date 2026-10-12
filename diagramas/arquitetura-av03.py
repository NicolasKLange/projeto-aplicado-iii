# Gera diagramas/arquitetura-av03.png (Figura 1 do relatório da AV3). Requer: matplotlib.
# Uso: python3 arquitetura-av03.py   (a partir da pasta diagramas/)
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
plt.rcParams["font.family"]="DejaVu Sans"
W,H=16,10.5
fig,ax=plt.subplots(figsize=(W,H)); ax.set_xlim(0,W); ax.set_ylim(0,H); ax.axis("off")
def area(x,y,w,h,t,fc,ec,ls="-",tc="#222",fs=11):
    ax.add_patch(FancyBboxPatch((x,y),w,h,boxstyle="round,pad=0,rounding_size=0.18",fc=fc,ec=ec,lw=1.3,ls=ls))
    ax.text(x+0.2,y+h-0.22,t,ha="left",va="top",fontsize=fs,color=tc,weight="bold")
def caixa(cx,cy,w,h,t1,t2="",ls="-",cor="#333",fc="white"):
    ax.add_patch(FancyBboxPatch((cx-w/2,cy-h/2),w,h,boxstyle="round,pad=0,rounding_size=0.12",fc=fc,ec=cor,lw=1.2,ls=ls))
    if t2:
        ax.text(cx,cy+0.13,t1,ha="center",va="center",fontsize=10,color=cor,weight="bold")
        ax.text(cx,cy-0.17,t2,ha="center",va="center",fontsize=8.6,color=cor)
    else:
        ax.text(cx,cy,t1,ha="center",va="center",fontsize=10,color=cor,weight="bold")
def seta(p,q,t="",ls="-",cor="#333",head="-|>",off=(0.08,0),rad=0.0,ha="left"):
    ax.add_patch(FancyArrowPatch(p,q,arrowstyle=head,mutation_scale=14,color=cor,lw=1.2,ls=ls,connectionstyle=f"arc3,rad={rad}",shrinkA=2,shrinkB=2))
    if t:
        mx,my=(p[0]+q[0])/2+off[0],(p[1]+q[1])/2+off[1]
        ax.text(mx,my,t,fontsize=8.8,color=cor,ha=ha,va="center",bbox=dict(fc="white",ec="none",pad=1.2))

# Navegador
area(4.0,9.0,5.6,1.25,"Navegador","#EEF3FA","#7A93B8")
caixa(6.8,9.4,4.8,0.62,"index.html · estilo.css · app.js","HTML, CSS e JavaScript puro, sem bibliotecas")
# Spring
area(0.3,0.3,11.3,8.35,"Aplicação Spring Boot 4.1 — MVP da AV03","#FFFFFF","#888888")
area(0.6,6.35,10.7,1.6,"web — adaptador de entrada HTTP","#F4F4F4","#AAAAAA",fs=11.5)
caixa(2.45,6.95,3.4,0.68,"ProcessamentoController","POST /api/calibracoes/processamento")
caixa(6.15,6.95,3.7,0.68,"ResultadoCalibracaoDto · PontoDto","média e erro exibidos com 5 casas")
caixa(9.65,6.95,3.0,0.68,"TratamentoErros","problem+json: 400 · 413 · 415 · 500")
area(0.6,4.45,10.7,1.55,"aplicacao — caso de uso","#F4F4F4","#AAAAAA",fs=11.5)
caixa(7.6,5.0,5.2,0.68,"ProcessamentoService","lê e calcula; SHA-256 e tamanho no log, nunca o conteúdo")
caixa(2.6,5.0,3.2,0.68,"NucleoConfig","cria os beans do núcleo")
area(0.6,0.55,10.7,3.55,"Núcleo — Java puro, sem Spring (D-012)","#EAF5EC","#6FA27A",fs=10.5)
area(0.85,0.8,4.6,2.85,"ingestao","#FFFFFF","#9CC3A4",fs=11)
caixa(3.15,2.75,3.9,0.6,"«porta» LeitorPlanilha","List<PontoEnsaio> ler(InputStream)")
caixa(3.15,1.45,3.9,0.68,"LeitorPlanilhaPoi","regras R01–R15 do contrato · Apache POI")
area(5.75,0.8,5.3,2.85,"dominio","#FFFFFF","#9CC3A4",fs=11)
caixa(8.4,2.75,4.6,0.6,"CalculadoraMetrologica","média com escala 10, HALF_EVEN")
caixa(8.4,1.45,5.0,1.0,"","")
ax.text(8.4,1.72,"PontoEnsaio · ResultadoPonto",ha="center",va="center",fontsize=10,color="#333",weight="bold")
ax.text(8.4,1.42,"ResultadoCalibracao · StatusPonto · Parecer",ha="center",va="center",fontsize=8.6,color="#333")
ax.text(8.4,1.15,"erro, status e parecer derivados, não armazenados",ha="center",va="center",fontsize=8.6,color="#333")
# AV02
area(12.0,2.3,3.7,4.5,"Arquitetura da AV02\n(fora do MVP)","#FFFFFF","#999999",ls="--",tc="#666",fs=11)
caixa(13.85,5.0,3.2,0.75,"fn-ingestao","AWS Lambda, upload no S3",ls="--",cor="#666")
caixa(13.85,3.15,3.2,0.75,"Amazon DynamoDB","persistência na AV04 (D-014)",ls="--",cor="#666")
# setas
seta((6.8,9.09),(2.9,7.29))
ax.text(5.6,8.82,"planilha .xlsx (multipart/form-data)  →\n←  JSON ou problem+json",fontsize=8.8,color="#333",ha="left",va="center",bbox=dict(fc="white",ec="none",pad=1.2))
seta((3.3,6.61),(6.0,5.34),"InputStream",off=(0.15,0.05))
seta((5.6,4.66),(4.4,3.05),"ler()",off=(0.15,-0.35))
seta((8.2,4.66),(8.4,3.05),"calcular(pontos)",off=(0.12,0))
seta((3.15,1.79),(3.15,2.45),"implementa",ls="--",head="-|>",off=(0.1,0))
seta((8.4,2.45),(8.4,1.85),"cria",off=(0.1,0))
seta((12.25,5.0),(10.2,5.0),"mesmo núcleo",ls="--",cor="#777",off=(0.0,0.2),ha="center")
seta((10.2,4.75),(12.25,3.3),"adaptador\nde saída",ls="--",cor="#777",off=(0.05,-0.1),ha="center")
fig.savefig("arquitetura-av03.png",dpi=200,bbox_inches="tight",pad_inches=0.1,facecolor="white")
