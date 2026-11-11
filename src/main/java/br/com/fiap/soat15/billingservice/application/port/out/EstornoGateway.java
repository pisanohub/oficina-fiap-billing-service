package br.com.fiap.soat15.billingservice.application.port.out;
import java.util.UUID;
public interface EstornoGateway {
    void estornarIntegral(String pagamentoId, UUID chaveIdempotencia);
}
