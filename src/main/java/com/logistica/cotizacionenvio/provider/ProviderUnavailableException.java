package com.logistica.cotizacionenvio.provider;

public class ProviderUnavailableException extends RuntimeException {

    private final String provider;

    public ProviderUnavailableException(String provider) {
        super("Proveedor no disponible: " + provider);
        this.provider = provider;
    }

    public String getProvider() {
        return provider;
    }
}
