package br.senai_pa3.persistencia;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import br.senai_pa3.dominio.ResultadoCalibracao;
import br.senai_pa3.ingestao.ErroValidacao;

/**
 * Porta de persistência (RN-20). No DynamoDB não há {@code UNIQUE}: a unicidade
 * do hash é um item-sentinela gravado na mesma transação que o novo estado, com
 * {@code attribute_not_exists}. Os métodos {@code registrar*} são atômicos: ou
 * gravam a sentinela e o estado, ou não gravam nada.
 * <p>
 * Os métodos {@code registrar*} são seguros para repetição: se a sentinela já
 * pertence ao mesmo {@code id}, devolvem {@link ResultadoRegistro.Retomada}.
 * <p>
 * 🔒 Nenhum método recebe nome de arquivo, conteúdo bruto ou nome do usuário.
 */
public interface RepositorioCalibracoes {

    /**
     * {@code fn-api}: cria o envio em {@link EstadoCalibracao#PROCESSANDO}, antes de
     * o arquivo existir.
     *
     * @throws IllegalStateException se o {@code id} já existe
     */
    void iniciar(String id, String sub, Instant agora);

    /**
     * Atalho para a {@code fn-ingestao}: de quem é este hash? Serve para encerrar
     * cedo, sem abrir o Excel; <b>não</b> garante unicidade (a corrida só se
     * resolve em {@code registrar*}).
     */
    Optional<String> idDoHash(String sha256);

    /**
     * {@code fn-ingestao}: planilha válida. Em caso de hash novo, grava a sentinela
     * e passa o envio para {@link EstadoCalibracao#PROCESSADA}.
     *
     * @throws IllegalStateException se o envio não existe ou não está em PROCESSANDO
     */
    ResultadoRegistro registrarProcessada(String id, String sha256, long tamanhoBytes,
            ResultadoCalibracao resultado, Instant agora);

    /**
     * {@code fn-ingestao}: planilha fora do contrato (rejeição de negócio). Grava a
     * sentinela e os erros; falha técnica <b>não</b> passa por aqui.
     *
     * @param erros pelo menos um
     * @throws IllegalStateException se o envio não existe ou não está em PROCESSANDO
     */
    ResultadoRegistro registrarRejeitada(String id, String sha256, long tamanhoBytes,
            List<ErroValidacao> erros, Instant agora);

    /**
     * {@code fn-ingestao}: o atalho de {@link #idDoHash} achou dono. Passa o envio
     * para {@link EstadoCalibracao#DUPLICADA}, apontando para o original.
     *
     * @throws IllegalStateException se o envio não existe, não está em PROCESSANDO
     *                               ou {@code idOriginal} não tem esse hash
     */
    void registrarDuplicada(String id, String idOriginal, String sha256, long tamanhoBytes, Instant agora);

    /**
     * {@code fn-certificado}: PROCESSADA → EMITIDA, com a sentinela do token.
     * Repetir com o mesmo token é inofensivo.
     *
     * @throws IllegalStateException se o envio não está em PROCESSADA (nem já EMITIDA
     *                               com este token) ou o token pertence a outro envio
     */
    void registrarEmitida(String id, String token, String sha256Pdf, Instant agora);

    Optional<RegistroCalibracao> buscar(String id);

    /**
     * {@code fn-api}, rota pública de verificação: o envio {@link EstadoCalibracao#EMITIDA} dono
     * do token, ou vazio se o token não existe.
     */
    Optional<RegistroCalibracao> buscarPorToken(String token);
}
