package br.senai_pa3.certificado;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

/** Cliente Cognito falso por {@link Proxy}. */
class CognitoConsultaUsuariosTest {

    private final List<AdminGetUserRequest> pedidos = new ArrayList<>();

    private CognitoConsultaUsuarios com(Function<AdminGetUserRequest, AdminGetUserResponse> resposta) {
        CognitoIdentityProviderClient cliente = (CognitoIdentityProviderClient) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { CognitoIdentityProviderClient.class },
                (proxy, metodo, args) -> {
                    pedidos.add((AdminGetUserRequest) args[0]);
                    return resposta.apply((AdminGetUserRequest) args[0]);
                });
        return new CognitoConsultaUsuarios(cliente, "pool-1");
    }

    private static AttributeType atributo(String nome, String valor) {
        return AttributeType.builder().name(nome).value(valor).build();
    }

    @Test
    void leOAtributoNamePeloSub() {
        var consulta = com(r -> AdminGetUserResponse.builder()
                .userAttributes(atributo("email", "x@exemplo.com"), atributo("name", "Maria Teste")).build());

        assertThat(consulta.nomeDeExibicao("sub-1")).contains("Maria Teste");
        assertThat(pedidos).singleElement().satisfies(p -> {
            assertThat(p.userPoolId()).isEqualTo("pool-1");
            assertThat(p.username()).isEqualTo("sub-1");
        });
    }

    @Test
    void semAtributoNameOuEmBranco_vazio() {
        assertThat(com(r -> AdminGetUserResponse.builder().userAttributes(atributo("email", "x@y.z")).build())
                .nomeDeExibicao("s")).isEmpty();
        assertThat(com(r -> AdminGetUserResponse.builder().userAttributes(atributo("name", "  ")).build())
                .nomeDeExibicao("s")).isEmpty();
    }

    @Test
    void usuarioInexistente_vazio() {
        var consulta = com(r -> {
            throw UserNotFoundException.builder().message("x").build();
        });

        assertThat(consulta.nomeDeExibicao("s")).isEmpty();
    }
}
