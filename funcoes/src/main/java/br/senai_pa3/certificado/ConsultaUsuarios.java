package br.senai_pa3.certificado;

import java.util.Optional;

/**
 * Porta do diretório de usuários. Em produção é o Cognito ({@link CognitoConsultaUsuarios}).
 * <p>
 * 🔒 O nome de exibição é lido na hora de desenhar o PDF e não é gravado em tabela, evento nem log (RN-35).
 */
public interface ConsultaUsuarios {

    /** Nome de exibição do usuário identificado pelo {@code sub}; vazio se não houver. */
    Optional<String> nomeDeExibicao(String sub);
}
