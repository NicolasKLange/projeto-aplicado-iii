package br.senai_pa3.persistencia;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.senai_pa3.dominio.ResultadoCalibracao;
import br.senai_pa3.ingestao.ErroValidacao;

/**
 * Adaptador de teste da porta. {@code synchronized} faz o papel da transação do
 * DynamoDB: a checagem da sentinela e a gravação não se intercalam.
 */
public class RepositorioEmMemoria implements RepositorioCalibracoes {

    private final Map<String, RegistroCalibracao> calibracoes = new HashMap<>();
    /** {@code HASH#<sha256>} → id do dono. */
    private final Map<String, String> sentinelasHash = new HashMap<>();
    /** {@code TOKEN#<token>} → id do dono. */
    private final Map<String, String> sentinelasToken = new HashMap<>();

    @Override
    public synchronized void iniciar(String id, String sub, Instant agora) {
        if (calibracoes.containsKey(id)) {
            throw new IllegalStateException("Envio já existe.");
        }
        calibracoes.put(id, new RegistroCalibracao(id, sub, EstadoCalibracao.PROCESSANDO,
                null, null, null, null, null, null, null, agora));
    }

    @Override
    public synchronized Optional<String> idDoHash(String sha256) {
        return Optional.ofNullable(sentinelasHash.get(sha256));
    }

    @Override
    public synchronized ResultadoRegistro registrarProcessada(String id, String sha256, long tamanhoBytes,
            ResultadoCalibracao resultado, Instant agora) {
        return registrar(id, sha256, atual -> new RegistroCalibracao(id, atual.sub(), EstadoCalibracao.PROCESSADA,
                sha256, tamanhoBytes, resultado, null, null, null, null, agora), tamanhoBytes, agora);
    }

    @Override
    public synchronized ResultadoRegistro registrarRejeitada(String id, String sha256, long tamanhoBytes,
            List<ErroValidacao> erros, Instant agora) {
        return registrar(id, sha256, atual -> new RegistroCalibracao(id, atual.sub(), EstadoCalibracao.REJEITADA,
                sha256, tamanhoBytes, null, erros, null, null, null, agora), tamanhoBytes, agora);
    }

    @Override
    public synchronized void registrarDuplicada(String id, String idOriginal, String sha256, long tamanhoBytes,
            Instant agora) {
        if (!id.equals(idOriginal) && !idOriginal.equals(sentinelasHash.get(sha256))) {
            throw new IllegalStateException("O original informado não é dono desse hash.");
        }
        marcarDuplicada(id, idOriginal, sha256, tamanhoBytes, agora);
    }

    @Override
    public synchronized void registrarEmitida(String id, String token, String sha256Pdf, Instant agora) {
        RegistroCalibracao atual = obrigatorio(id);
        if (atual.estado() == EstadoCalibracao.EMITIDA && token.equals(atual.token())) {
            return;
        }
        if (atual.estado() != EstadoCalibracao.PROCESSADA) {
            throw new IllegalStateException("Só PROCESSADA pode ser emitida.");
        }
        if (sentinelasToken.containsKey(token)) {
            throw new IllegalStateException("Token já pertence a outro envio.");
        }
        sentinelasToken.put(token, id);
        calibracoes.put(id, new RegistroCalibracao(id, atual.sub(), EstadoCalibracao.EMITIDA, atual.sha256(),
                atual.tamanhoBytes(), atual.resultado(), null, null, token, sha256Pdf, agora));
    }

    @Override
    public synchronized Optional<RegistroCalibracao> buscar(String id) {
        return Optional.ofNullable(calibracoes.get(id));
    }

    private ResultadoRegistro registrar(String id, String sha256, java.util.function.Function<RegistroCalibracao,
            RegistroCalibracao> novo, long tamanhoBytes, Instant agora) {
        RegistroCalibracao atual = obrigatorio(id);
        String dono = sentinelasHash.get(sha256);
        if (id.equals(dono)) {
            return new ResultadoRegistro.Retomada();
        }
        if (dono != null) {
            marcarDuplicada(id, dono, sha256, tamanhoBytes, agora);
            return new ResultadoRegistro.Duplicada(dono);
        }
        exigirProcessando(atual);
        sentinelasHash.put(sha256, id);
        calibracoes.put(id, novo.apply(atual));
        return new ResultadoRegistro.Registrada();
    }

    private void marcarDuplicada(String id, String idOriginal, String sha256, long tamanhoBytes, Instant agora) {
        RegistroCalibracao atual = obrigatorio(id);
        if (atual.estado() == EstadoCalibracao.DUPLICADA && idOriginal.equals(atual.idOriginal())) {
            return;
        }
        exigirProcessando(atual);
        calibracoes.put(id, new RegistroCalibracao(id, atual.sub(), EstadoCalibracao.DUPLICADA, sha256,
                tamanhoBytes, null, null, idOriginal, null, null, agora));
    }

    private RegistroCalibracao obrigatorio(String id) {
        RegistroCalibracao r = calibracoes.get(id);
        if (r == null) {
            throw new IllegalStateException("Envio não existe.");
        }
        return r;
    }

    private static void exigirProcessando(RegistroCalibracao r) {
        if (r.estado() != EstadoCalibracao.PROCESSANDO) {
            throw new IllegalStateException("Só PROCESSANDO aceita essa transição.");
        }
    }
}
