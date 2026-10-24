package br.senai_pa3.persistencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.senai_pa3.dominio.PontoEnsaio;
import br.senai_pa3.dominio.ResultadoCalibracao;
import br.senai_pa3.dominio.ResultadoPonto;
import br.senai_pa3.ingestao.ErroValidacao;

class RepositorioCalibracoesTest {

    private static final Instant T0 = Instant.parse("2026-10-24T12:00:00Z");
    private static final String SUB = "sub-opaco-1";
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    private RepositorioEmMemoria repo;

    @BeforeEach
    void criaRepositorio() {
        repo = new RepositorioEmMemoria();
    }

    private static ResultadoCalibracao resultado() {
        PontoEnsaio ponto = new PontoEnsaio("P1", new BigDecimal("10.000"), new BigDecimal("0.010"),
                new BigDecimal("-0.010"), List.of(new BigDecimal("10.001"), new BigDecimal("10.003")));
        return new ResultadoCalibracao(List.of(new ResultadoPonto(ponto, new BigDecimal("10.002"))));
    }

    private static List<ErroValidacao> erros() {
        return List.of(new ErroValidacao(3, "Leitura 1", "R10", "Valor não numérico."));
    }

    @Test
    void hashNovoGravaProcessadaEASentinela() {
        repo.iniciar("e1", SUB, T0);

        ResultadoRegistro r = repo.registrarProcessada("e1", HASH_A, 1234, resultado(), T0);

        assertThat(r).isInstanceOf(ResultadoRegistro.Registrada.class);
        RegistroCalibracao reg = repo.buscar("e1").orElseThrow();
        assertThat(reg.estado()).isEqualTo(EstadoCalibracao.PROCESSADA);
        assertThat(reg.sub()).isEqualTo(SUB);
        assertThat(reg.resultado()).isNotNull();
        assertThat(repo.idDoHash(HASH_A)).contains("e1");
    }

    @Test
    void mesmoHashEmOutroEnvioViraDuplicadaApontandoParaOOriginal() {
        repo.iniciar("e1", SUB, T0);
        repo.iniciar("e2", SUB, T0);
        repo.registrarProcessada("e1", HASH_A, 1234, resultado(), T0);

        ResultadoRegistro r = repo.registrarProcessada("e2", HASH_A, 1234, resultado(), T0);

        assertThat(r).isEqualTo(new ResultadoRegistro.Duplicada("e1"));
        RegistroCalibracao dup = repo.buscar("e2").orElseThrow();
        assertThat(dup.estado()).isEqualTo(EstadoCalibracao.DUPLICADA);
        assertThat(dup.idOriginal()).isEqualTo("e1");
        assertThat(dup.resultado()).isNull();
        assertThat(repo.buscar("e1").orElseThrow().estado()).isEqualTo(EstadoCalibracao.PROCESSADA);
    }

    @Test
    void eventoRepetidoDoMesmoEnvioEhRetomadaENaoDuplicata() {
        repo.iniciar("e1", SUB, T0);
        repo.registrarProcessada("e1", HASH_A, 1234, resultado(), T0);

        ResultadoRegistro r = repo.registrarProcessada("e1", HASH_A, 1234, resultado(), T0.plusSeconds(5));

        assertThat(r).isInstanceOf(ResultadoRegistro.Retomada.class);
        RegistroCalibracao reg = repo.buscar("e1").orElseThrow();
        assertThat(reg.estado()).isEqualTo(EstadoCalibracao.PROCESSADA);
        assertThat(reg.atualizadoEm()).isEqualTo(T0);
    }

    @Test
    void hashDiferenteNaoColide() {
        repo.iniciar("e1", SUB, T0);
        repo.iniciar("e2", SUB, T0);

        repo.registrarProcessada("e1", HASH_A, 1234, resultado(), T0);
        ResultadoRegistro r = repo.registrarProcessada("e2", HASH_B, 1234, resultado(), T0);

        assertThat(r).isInstanceOf(ResultadoRegistro.Registrada.class);
        assertThat(repo.buscar("e2").orElseThrow().estado()).isEqualTo(EstadoCalibracao.PROCESSADA);
    }

    @Test
    void rejeitadaGuardaErrosESemDadoDeNegocio() {
        repo.iniciar("e1", SUB, T0);

        ResultadoRegistro r = repo.registrarRejeitada("e1", HASH_A, 999, erros(), T0);

        assertThat(r).isInstanceOf(ResultadoRegistro.Registrada.class);
        RegistroCalibracao reg = repo.buscar("e1").orElseThrow();
        assertThat(reg.estado()).isEqualTo(EstadoCalibracao.REJEITADA);
        assertThat(reg.erros()).containsExactlyElementsOf(erros());
        assertThat(reg.resultado()).isNull();
    }

    @Test
    void mesmaPlanilhaRejeitadaReenviadaViraDuplicadaDaRejeitadaOriginal() {
        repo.iniciar("e1", SUB, T0);
        repo.iniciar("e2", SUB, T0);
        repo.registrarRejeitada("e1", HASH_A, 999, erros(), T0);

        // curto-circuito: o hash é conhecido antes de abrir o Excel
        assertThat(repo.idDoHash(HASH_A)).contains("e1");
        repo.registrarDuplicada("e2", "e1", HASH_A, 999, T0);

        assertThat(repo.buscar("e2").orElseThrow().idOriginal()).isEqualTo("e1");
        assertThat(repo.buscar("e1").orElseThrow().erros()).isEqualTo(erros());
    }

    @Test
    void versaoCorrigidaTemOutroHashEPassa() {
        repo.iniciar("e1", SUB, T0);
        repo.iniciar("e2", SUB, T0);
        repo.registrarRejeitada("e1", HASH_A, 999, erros(), T0);

        ResultadoRegistro r = repo.registrarProcessada("e2", HASH_B, 1000, resultado(), T0);

        assertThat(r).isInstanceOf(ResultadoRegistro.Registrada.class);
    }

    @Test
    void registrarSemIniciarOuForaDeProcessandoFalha() {
        assertThatIllegalStateException()
                .isThrownBy(() -> repo.registrarProcessada("nao-existe", HASH_A, 1, resultado(), T0));

        repo.iniciar("e1", SUB, T0);
        repo.registrarProcessada("e1", HASH_A, 1, resultado(), T0);
        assertThatIllegalStateException()
                .isThrownBy(() -> repo.registrarRejeitada("e1", HASH_B, 1, erros(), T0));
    }

    @Test
    void emissaoSoAPartirDeProcessadaERepeticaoEhInofensiva() {
        repo.iniciar("e1", SUB, T0);
        assertThatIllegalStateException().isThrownBy(() -> repo.registrarEmitida("e1", "tok", HASH_B, T0));
        repo.registrarProcessada("e1", HASH_A, 1, resultado(), T0);

        repo.registrarEmitida("e1", "tok", HASH_B, T0);
        repo.registrarEmitida("e1", "tok", HASH_B, T0.plusSeconds(1));

        RegistroCalibracao reg = repo.buscar("e1").orElseThrow();
        assertThat(reg.estado()).isEqualTo(EstadoCalibracao.EMITIDA);
        assertThat(reg.token()).isEqualTo("tok");
        assertThat(reg.atualizadoEm()).isEqualTo(T0);
    }

    @Test
    void tokenNaoPodeSerReusadoPorOutroEnvio() {
        repo.iniciar("e1", SUB, T0);
        repo.iniciar("e2", SUB, T0);
        repo.registrarProcessada("e1", HASH_A, 1, resultado(), T0);
        repo.registrarProcessada("e2", HASH_B, 1, resultado(), T0);
        repo.registrarEmitida("e1", "tok", HASH_A, T0);

        assertThatIllegalStateException().isThrownBy(() -> repo.registrarEmitida("e2", "tok", HASH_B, T0));
    }

    @Test
    void registroIncoerenteEhRecusado() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RegistroCalibracao("e1", SUB,
                EstadoCalibracao.REJEITADA, HASH_A, 1L, resultado(), erros(), null, null, null, T0));
        assertThatIllegalArgumentException().isThrownBy(() -> new RegistroCalibracao("e1", SUB,
                EstadoCalibracao.REJEITADA, HASH_A, 1L, null, List.of(), null, null, null, T0));
        assertThatIllegalArgumentException().isThrownBy(() -> new RegistroCalibracao("e1", SUB,
                EstadoCalibracao.PROCESSADA, "curto", 1L, resultado(), null, null, null, null, T0));
    }
}
