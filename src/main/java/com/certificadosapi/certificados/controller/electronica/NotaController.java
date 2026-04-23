package com.certificadosapi.certificados.controller.electronica;

import com.certificadosapi.certificados.dto.ArchivoDescarga;

import com.certificadosapi.certificados.service.electronica.NotaService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;

import java.sql.SQLException;
import java.util.Map;

@RestController
@RequestMapping("/nota")
public class NotaController {

    private final NotaService notaService;

    @Autowired
    public NotaController(NotaService notaService) {
        this.notaService = notaService;
    }

    /**
     * Genera y transmite una nota electrónica al PAC.
     *
     * @param IdMovDoc Identificador del movimiento documento a facturar.
     * @return ResponseEntity con el statusCode DIAN o error detallado.
     */
    @GetMapping("/GenerarNota")
    public ResponseEntity<?> generarNota(@RequestParam Integer IdMovDoc) {
        try {
            Map<String, String> datos = notaService.obtenerDatosNota(IdMovDoc);
            Map<String, Object> resultado = notaService.enviarAlPac(
                IdMovDoc, datos.get("json"), datos.get("bearerToken"), datos.get("urlPac")
            );

            if ((boolean) resultado.get("fueConsulta")) {
                return ResponseEntity.ok(resultado.get("statusCode"));
            }
            return ResponseEntity.ok(resultado.get("statusCode"));

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error general: " + e.getMessage());
        }
    }

    /**
     * Descarga el PDF de una nota desde SSRS usando autenticación NTLM.
     *
     * @param IdMovDoc Identificador del movimiento documento.
     * @param Url      URL base del reporte SSRS.
     * @return ResponseEntity con los bytes del PDF o un mensaje de error.
     */
    @SuppressWarnings("null")
    @GetMapping("/DescargarDocumento")
    public ResponseEntity<?> descargarDocumento(@RequestParam String IdMovDoc, @RequestParam String Url) throws SQLException {
        try {
            byte[] pdfBytes = notaService.descargarDocumento(IdMovDoc, Url);
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"Factura.pdf\"")
                .body(pdfBytes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("ERROR INESPERADO: " + e.getMessage());
        }
    }

    /**
     * Genera un ZIP con el XML y el PDF de la factura y lo retorna para descarga.
     *
     * @param IdMovDoc Identificador del movimiento documento.
     * @param Url      URL base del reporte SSRS para obtener el PDF.
     * @return ResponseEntity con los bytes del ZIP o un mensaje de error.
     */
    @GetMapping("/DescargarPaqueteCompleto")
    public ResponseEntity<byte[]> descargarPaqueteCompleto(@RequestParam Integer IdMovDoc, @RequestParam String Url) {
        try {
            ArchivoDescarga archivo = notaService.descargarPaqueteCompleto(IdMovDoc, Url);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(ContentDisposition.attachment().filename(archivo.getNombreArchivo() + ".zip").build());
            return ResponseEntity.ok().headers(headers).body(archivo.getZipBytes());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage().getBytes());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(("Error al generar el paquete ZIP: " + e.getMessage()).getBytes());
        }
    }

    /**
     * Envía la factura electrónica por correo al destinatario indicado.
     *
     * @param IdMovDoc  Identificador del movimiento documento.
     * @param CorreoA   Correo del destinatario principal.
     * @param CorreoCC  Correo en copia (opcional).
     * @param CorreoCCO Correo en copia oculta (opcional).
     * @param Url       URL base del reporte SSRS para generar el adjunto.
     * @return ResponseEntity con confirmación o mensaje de error.
     */
    @GetMapping("/enviarCorreo")
    public ResponseEntity<?> enviarCorreo(
        @RequestParam int IdMovDoc,
        @RequestParam(required = true) String CorreoA,
        @RequestParam(required = false) String CorreoCC,
        @RequestParam(required = false) String CorreoCCO,
        @RequestParam String Url
    ) {
        try {
            notaService.enviarCorreo(IdMovDoc, CorreoA, CorreoCC, CorreoCCO, Url);
            return ResponseEntity.ok("Correo enviado correctamente");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}