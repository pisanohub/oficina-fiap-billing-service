package br.com.fiap.soat15.billingservice.adapter.in.web;

public class AssinaturaWebhookInvalidaException extends RuntimeException {
    public AssinaturaWebhookInvalidaException() { super("Notificação sem assinatura válida ou fora da tolerância de tempo."); }
}
