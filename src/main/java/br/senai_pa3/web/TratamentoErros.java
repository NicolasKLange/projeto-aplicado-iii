package br.senai_pa3.web;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import br.senai_pa3.ingestao.PlanilhaInvalidaException;

/**
 * Todo erro da API sai como {@link ProblemDetail} ({@code application/problem+json}, RFC 9457).
 * <p>
 * A classe base já trata o 400 sem a parte {@code arquivo}, o 415 e o 413; aqui só se troca a
 * mensagem do 415 e do 413. Detalhe técnico (exceção, stack trace, caminho) fica só no log.
 */
@RestControllerAdvice
public class TratamentoErros extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TratamentoErros.class);

    /** 400: a lista vai inteira para a tela (T16). O record já é um contrato de erro, não o domínio. */
    @ExceptionHandler(PlanilhaInvalidaException.class)
    ProblemDetail planilhaInvalida(PlanilhaInvalidaException ex) {
        ProblemDetail corpo = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "A planilha não cumpre o contrato. Corrija os erros listados e envie de novo.");
        corpo.setProperty("erros", ex.erros());
        return corpo;
    }

    /** 500: o id liga a resposta ao log, onde fica o stack trace. */
    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception ex) {
        UUID id = UUID.randomUUID();
        log.error("id={}", id, ex);
        ProblemDetail corpo = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro inesperado no processamento. Informe o id ao suporte.");
        corpo.setProperty("id", id);
        return corpo;
    }

    /**
     * 413. Cita o limite do contrato (R01), não o do multipart (6 MB): acima de 5 MB o leitor
     * recusa de qualquer jeito, e o operador vê o mesmo número nos dois caminhos.
     */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ex.getBody().setDetail("O arquivo excede o limite de 5 MB.");
        return super.handleMaxUploadSizeExceededException(ex, headers, status, request);
    }

    /** 415: a base já devolve o cabeçalho Accept; o detail diz como enviar. */
    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ex.getBody().setDetail("Envie a planilha .xlsx como multipart/form-data, no campo 'arquivo'.");
        return super.handleHttpMediaTypeNotSupported(ex, headers, status, request);
    }
}
