package br.com.fiap.soat15.billingservice.application.port.out;
import br.com.fiap.soat15.billingservice.domain.model.*;
import java.util.List;
public interface CotacaoGateway { Cotacao consultar(List<ItemCotacao> itens); }
