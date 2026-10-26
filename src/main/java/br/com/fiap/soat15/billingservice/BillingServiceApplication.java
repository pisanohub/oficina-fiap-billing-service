package br.com.fiap.soat15.billingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Billing Service - Fase 4 - Tech Challenge.
 *
 * Responsabilidade: geração e envio de orçamentos para aprovação, registro e
 * verificação de pagamentos integrados ao Mercado Pago, e aviso ao OS Service
 * após o pagamento ser processado (ver documentacao/divisao-tarefas-fase4.md
 * e documentacao/event-storming-saga-v1.md no repositório principal).
 *
 * TODO: aplicar Pessimistic View ou Reread Value na etapa de orçamento/pagamento
 * antes de confirmar o evento de pagamento, para evitar condição de corrida caso
 * o cliente aprove o orçamento enquanto outro processo o esteja reprocessando
 * (reforço da P7 anotado no event storming da Saga).
 */
@SpringBootApplication
public class BillingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BillingServiceApplication.class, args);
	}

}
