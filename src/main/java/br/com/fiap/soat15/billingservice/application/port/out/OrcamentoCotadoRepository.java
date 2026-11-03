package br.com.fiap.soat15.billingservice.application.port.out;
import br.com.fiap.soat15.billingservice.domain.model.OrcamentoCotado;
import java.util.*;
public interface OrcamentoCotadoRepository {
    Optional<OrcamentoCotado> buscarPorComandoId(UUID comandoId);
    OrcamentoCotado salvar(OrcamentoCotado cotado);
}
