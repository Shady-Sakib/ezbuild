package ezbuild.dto;

/** Checkout totals: subtotal + shipping + COD fee (COD only). Always computed on the server. */
public record Quote(long subtotal, long savings, long shippingFee, long codFee, long total) { }
