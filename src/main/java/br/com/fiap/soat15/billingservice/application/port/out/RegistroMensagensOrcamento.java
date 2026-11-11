package br.com.fiap.soat15.billingservice.application.port.out;
import java.util.*;
public interface RegistroMensagensOrcamento {
    record Entrada(String corpo, String identidade, String resultado) { }
    record Pendente(UUID comandoId, String resultado) { }
    Optional<Entrada> buscar(UUID comandoId);
    void reservar(UUID comandoId, String corpo, String identidade);
    void concluir(UUID comandoId, String resultado);
    List<Pendente> pendentes();
    void enviado(UUID comandoId);
}
