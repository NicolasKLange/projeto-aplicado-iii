package br.senai_pa3.certificado;

import java.util.Optional;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

/** Adaptador da {@link ConsultaUsuarios} com {@code AdminGetUser}; o {@code sub} serve de username. */
public class CognitoConsultaUsuarios implements ConsultaUsuarios {

    private final CognitoIdentityProviderClient cognito;
    private final String userPoolId;

    public CognitoConsultaUsuarios(CognitoIdentityProviderClient cognito, String userPoolId) {
        this.cognito = cognito;
        this.userPoolId = userPoolId;
    }

    @Override
    public Optional<String> nomeDeExibicao(String sub) {
        try {
            return cognito.adminGetUser(AdminGetUserRequest.builder().userPoolId(userPoolId).username(sub).build())
                    .userAttributes().stream()
                    .filter(a -> "name".equals(a.name()))
                    .map(AttributeType::value)
                    .filter(v -> v != null && !v.isBlank())
                    .findFirst();
        } catch (UserNotFoundException e) {
            return Optional.empty();
        }
    }
}
