package com.certificadosapi.certificados.dto;

public class ArchivoDescarga {

    private final String nombreArchivo;

    private final byte[] zipBytes;

    public ArchivoDescarga(String nombreArchivo, byte[] zipBytes){

        this.nombreArchivo = nombreArchivo;
        this.zipBytes = zipBytes;
    }

    public String getNombreArchivo(){

        return nombreArchivo;

    }

    public byte[] getZipBytes(){
        return zipBytes;
    }
    
}
